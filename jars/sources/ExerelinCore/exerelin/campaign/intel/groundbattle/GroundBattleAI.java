package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

public class GroundBattleAI {
	
	public static Logger log = Global.getLogger(GroundBattleAI.class);
	
	public static boolean PRINT_DEBUG = true;
	public static float MIN_MORALE_TO_REDEPLOY = 0.35f;
	public static float STRENGTH_RATIO_TO_WRITE_OFF = 0.75f;
	
	protected GroundBattleIntel intel;
	protected boolean isAttacker;
	protected boolean isPlayer;
	
	// generated for all industries, so ground units can look up the current strength where they are
	protected transient Map<IndustryForBattle, IFBStrengthRecord> strengthRecords = new HashMap<>();
	protected transient Set<IndustryForBattle> industriesWithEnemy = new HashSet<>();
	protected transient Set<IFBStrengthRecord> writeoffIndustries = new HashSet<>();
	protected transient List<IFBStrengthRecord> industriesWithEnemySorted = new ArrayList<>();
	protected transient List<GroundUnit> availableUnitsSorted = new LinkedList<>();
	protected transient float availableStrength;
	
	/*
		AI concepts:
		- industries that need shoring up
		- available assets to move, sorted
			- strength of each
			- not too low morale
		- industries that are lost (not enough assets to reinforce)
	*/
	
	public GroundBattleAI(GroundBattleIntel intel, boolean isAttacker, boolean isPlayer) {
		this.intel = intel;
		this.isAttacker = isAttacker;
		this.isPlayer = isPlayer;
	}
	
	protected Set<IndustryForBattle> getEnemyIndustries() {
		Set<IndustryForBattle> results = new HashSet<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			if (ifb.heldByAttacker != isAttacker || ifb.containsEnemyOf(isAttacker))
				results.add(ifb);
		}
		return results;
	}
	
	/**
	 * Calculates the deployable strength of the units we can move, taking into 
	 * account movement point costs.
	 * @return
	 */
	protected float recomputeAvailableStrength() {
		int remainingMovePoints = intel.getSide(isAttacker).getMovementPointsPerTurn().getModifiedInt()
				- intel.getSide(isAttacker).getMovementPointsSpent().getModifiedInt();
		
		availableStrength = 0;
		for (GroundUnit unit : availableUnitsSorted) {
			availableStrength += unit.getBaseStrength();
			remainingMovePoints -= unit.getDeployCost();
			if (remainingMovePoints <= 0) break;
		}
		return availableStrength;
	}
	
	/**
	 * Find industries we can't save and mark as such.
	 * @return The current {@code writeoffIndustries} set, for easier method chaining.
	 */
	protected Set<IFBStrengthRecord> writeOffIndustries() {
		printDebug("Updating industries to write off");
		for (IFBStrengthRecord record : industriesWithEnemySorted) {
			// already in writeoff list
			if (writeoffIndustries.contains(record)) {
				continue;
			}
			
			// too important to give up
			if (record.industry.getPriority() >= 3)
				continue;
			
			float strRatio = record.getEffectiveStrengthRatio(false);
			if (strRatio < STRENGTH_RATIO_TO_WRITE_OFF) 
			{
				float hypotheticalOurStr = record.ourStr + availableStrength;
				float hypotheticalRatio = hypotheticalOurStr/record.theirStr;
				if (hypotheticalRatio < STRENGTH_RATIO_TO_WRITE_OFF) 
				{
					printDebug(" - Writing off " + record.industry.getName());
					printDebug(String.format("  - Current strength ratio %s, at most we could get it to %s", 
							strRatio, hypotheticalRatio));
					writeoffIndustries.add(record);
				}
			}
		}
		return writeoffIndustries;
	}
	
	/**
	 * Get units that can move, are not already moving and are in a shape to move.
	 * @return
	 */
	protected List<GroundUnit> getMobileUnits() {
		List<GroundUnit> results = new ArrayList<>();
		boolean allowMilitia = canUnleashMilitia();
		for (GroundUnit unit : intel.getSide(isAttacker).getUnits()) {
			if (isPlayer && unit.getLocation() == null) continue;	// don't drop new units for player AI
			if (unit.getDestination() != null) continue;
			if (unit.isWithdrawing()) continue;
			if (unit.getMorale() < MIN_MORALE_TO_REDEPLOY) continue;
			if (unit.isReorganizing() || unit.isAttackPrevented()) continue;
			if (!allowMilitia && unit.getType() == ForceType.MILITIA) continue;
			results.add(unit);
		}
		return results;
	}
	
	/**
	 * Alternative to {@code IndustryForBattle.getStrength} which accounts for 
	 * the fact that militia fold like tissue paper. Also handles units that are
	 * not yet here, but moving here.
	 * @param ifb
	 * @param attacker
	 * @return
	 */
	public float getStrengthForAI(IndustryForBattle ifb, boolean attacker) {
		float strength = 0;
		for (GroundUnit unit : intel.getAllUnits()) {
			if (unit.isAttacker != attacker) continue;
			boolean isHere = unit.getLocation() == ifb;
			// the check is so we can't see where enemy units are going to move
			if (attacker == this.isAttacker) {
				isHere = isHere || unit.getDestination() == ifb;
			}
			
			if (!isHere)
				continue;
			
			float thisStr = unit.getAttackStrength();
			if (unit.getType() == ForceType.MILITIA)
				thisStr *= 0.5f;
			strength += thisStr;
		}
		return strength;
	}
	
	public void giveOrders() {
		try {
			getInfo();
			
			boolean proceed = true;
			int count = 0;
			while (proceed) {
				count++;
				printDebug("Decision loop iteration " + count);
				proceed = decisionLoop();
			}
		} catch (Exception ex) {
			log.error("AI orders failed", ex);
		}
	}
	
	public void getInfo() {
		printDebug("Preparing information for AI, is attacker: " + isAttacker);
		
		List<GroundUnit> mobile = getMobileUnits();
		
		// ---------------------------------------------------------------------
		// Determine conditions on all industries
		industriesWithEnemy = getEnemyIndustries();
		
		for (IndustryForBattle ifb : intel.getIndustries()) {
			float ourStr = getStrengthForAI(ifb, isAttacker);
			float theirStr = getStrengthForAI(ifb, !isAttacker);
			IFBStrengthRecord record = new IFBStrengthRecord(isAttacker, ifb, ourStr, theirStr);
			if (industriesWithEnemy.contains(ifb))
			{
				industriesWithEnemySorted.add(record);
			}
			strengthRecords.put(ifb, record);
		}
		
		Collections.sort(industriesWithEnemySorted, new Comparator<IFBStrengthRecord>() {
			@Override
			public int compare(IFBStrengthRecord one, IFBStrengthRecord two) {
				return Float.compare(two.getPriorityForReinforcement(false), one.getPriorityForReinforcement(false));
			}
		});
		printDebug("Listed industries with enemy presence");
		for (IFBStrengthRecord record : industriesWithEnemySorted) {
			printDebug(String.format(" - Industry %s has strength ratio %.2f, reinforcement priority %.2f", 
					record.industry.getName(),
					record.getEffectiveStrengthRatio(false),
					record.getPriorityForReinforcement(false)));
		}
		
		// ---------------------------------------------------------------------
		// Get units we can redeploy to threatened areas
		printDebug("Getting units available for move orders");
		for (GroundUnit unit : mobile) {
			if (unit.getLocation() != null) {
				IFBStrengthRecord strAtLoc = strengthRecords.get(unit.getLocation());
				if (strAtLoc == null) {
					log.warn("Missing strength record for location " + unit.getLocation().getName());
					continue;
				}
				float remainingStr = strAtLoc.getEffectiveStrengthRatioAssumingUnitRemoved(unit);
				//log.info("Remaining strRatio: " + remainingStr);
				if (strAtLoc.hasEnemyPresence && remainingStr < 0.9f)
					continue;
			}
			printDebug(String.format(" - Available unit: %s, at %s, strength %s", 
					unit.toString(), 
					unit.getLocation() == null ? "fleet" : unit.getLocation().getName(), 
					unit.getAttackStrength()));
			availableUnitsSorted.add(unit);
		}
		
		Collections.sort(availableUnitsSorted, new Comparator<GroundUnit>() {
			@Override
			public int compare(GroundUnit one, GroundUnit two) {
				return Float.compare(two.getBaseStrength(), one.getBaseStrength());
			}
		});
		
		recomputeAvailableStrength();
		writeOffIndustries();
	}
	
	/**
	 * Move units to where they're needed, while we can.
	 * @return True if further action should be taken (i.e. this method should 
	 * be called again), false otherwise.
	 */
	public boolean decisionLoop() {
		boolean movedAnything = false;
		for (IFBStrengthRecord toReinforce : industriesWithEnemySorted) {
			printDebug("Considering plans for industry " + toReinforce.industry.getName());
			if (writeoffIndustries.contains(toReinforce)) {
				continue;
			}
			for (GroundUnit unit : availableUnitsSorted) {
				IFBStrengthRecord origin = strengthRecords.get(unit.getLocation());
				
				// already at that location
				if (origin != null) {
					if (origin.industry == toReinforce.industry) continue;
				
					// our current location has a higher priority than reinforcement candidate?
					if (!writeoffIndustries.contains(origin) 
							&& origin.getPriorityForReinforcement(false) > toReinforce.getPriorityForReinforcement(false)) {
						//printDebug(String.format(" - %s current location has higher priority", unit.toString()));
						continue;
					}
						
				}				
				
				// issue move order
				printDebug(String.format(" - Moving %s to reinforce %s", unit.toString(), 
						toReinforce.industry.getName()));
				if (unit.getLocation() != null)
					unit.setDestination(toReinforce.industry);
				else
					unit.deploy(toReinforce.industry, null);
				availableUnitsSorted.remove(unit);
				
				// recompute priority given the reinforcements we've just dispatched
				toReinforce.ourStr += unit.getBaseStrength();
				toReinforce.getEffectiveStrengthRatio(true);
				toReinforce.getPriorityForReinforcement(true);
				if (origin != null) {
					origin.ourStr -= unit.getBaseStrength();
					origin.getEffectiveStrengthRatio(true);
					origin.getPriorityForReinforcement(true);
				}			
				
				movedAnything = true;
				break;
			} 
			if (movedAnything) break;
		}
		
		if (movedAnything) {
			recomputeAvailableStrength();
			writeOffIndustries();
		}
		
		boolean canContinue = movedAnything && availableStrength > 0 && intel.getSide(isAttacker).getMovementPointsRemaining() > 0;
		return canContinue;
	}
			
	protected boolean canUnleashMilitia() {
		return intel.turnNum > 15 + intel.getMarket().getSize() * 4;
	}
	
	public static void printDebug(String str) {
		if (!PRINT_DEBUG) return;
		log.info(str);
	}
	
	public static class IFBStrengthRecord {
		public boolean isAttacker;
		public IndustryForBattle industry;
		public float ourStr;
		public float theirStr;
		public boolean hasEnemyPresence;
		
		public transient Float strRatioCache;
		public transient Float reinforcePriorityCache;
		
		public IFBStrengthRecord(boolean isAttacker, IndustryForBattle industry, float ourStr, float theirStr) 
		{
			this.isAttacker = isAttacker;
			this.industry = industry;
			this.ourStr = ourStr;
			this.theirStr = theirStr;
			hasEnemyPresence = theirStr > 0;
		}
		
		public float getEffectiveStrengthRatio(boolean recompute) {
			if (!recompute && strRatioCache != null)
				return strRatioCache;
			
			strRatioCache = getEffectiveStrengthRatio(ourStr, theirStr);
			return strRatioCache;
		}
		
		public float getEffectiveStrengthRatioAssumingUnitRemoved(GroundUnit unit) {
			return getEffectiveStrengthRatio(ourStr - unit.getAttackStrength(), theirStr);
		}
		
		public float getEffectiveStrengthRatio(float ourStr, float theirStr) {
			float strMult = industry.getPlugin().getStrengthMult();
			if (strMult != 1) {
				if (industry.heldByAttacker == isAttacker) {
					ourStr *= strMult;
				} else {
					theirStr *= strMult;
				}
			}
			if (theirStr <= 0) return 9999;
			return ourStr/theirStr;
		}
		
		public float getPriorityForReinforcement(boolean recompute) {
			if (!recompute && reinforcePriorityCache != null) {
				return reinforcePriorityCache;
			}
			
			float strRatio = getEffectiveStrengthRatio(false);
			float prio = 1.25f - strRatio;
			if (prio < 0) prio = 0;
			else if (industry.heldByAttacker != isAttacker) prio /= 2;	// counterattacks have lower priority
			prio += GroundBattleSide.getDefendPriority(industry.getIndustry()) * 0.1f;
			
			reinforcePriorityCache = prio;
			return reinforcePriorityCache;
		}
	}
}