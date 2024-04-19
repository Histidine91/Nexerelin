package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.intel.groundbattle.plugins.AbilityPlugin;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtils;
import org.apache.log4j.Logger;

import java.util.*;

public class GroundBattleAI {
	
	public static Logger log = Global.getLogger(GroundBattleAI.class);
	
	public static boolean PRINT_DEBUG = false;
	public static float MIN_MORALE_TO_REDEPLOY = 0.35f;
	public static float STRENGTH_RATIO_TO_WRITE_OFF = 0.75f;
	public static float MIN_SCORE_FOR_ABILITY = 5;
	
	protected GroundBattleIntel intel;
	protected boolean isAttacker;
	protected boolean isPlayer;
	protected boolean allowDrop = true;	// can the AI drop new units from orbit?
	protected int movePointsAvailable;
	protected int movePointThreshold;
	
	/**
	 * Generated for all industries, so ground units can look up the current strength where they are.
	 */
	protected transient Map<IndustryForBattle, IFBStrengthRecord> strengthRecords = new HashMap<>();
	protected transient Set<IndustryForBattle> industriesWithEnemy = new HashSet<>();
	protected transient Set<IFBStrengthRecord> writeoffIndustries = new HashSet<>();
	protected transient List<IFBStrengthRecord> industriesWithEnemySorted = new LinkedList<>();

	/**
	 * 	Undeployed before deployed units, then strongest to weakest.
	 */
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
		allowDrop = !isPlayer;
	}

	public GroundBattleAI(GroundBattleIntel intel, boolean isAttacker, boolean isPlayer, boolean allowDrop) {
		this(intel, isAttacker, isPlayer);
		this.allowDrop = allowDrop;
	}
	
	public List<IFBStrengthRecord> getIndustriesWithEnemySorted() {
		return industriesWithEnemySorted;
	}
	
	protected Set<IndustryForBattle> getEnemyIndustries() {
		Set<IndustryForBattle> results = new HashSet<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			if (ifb.heldByAttacker != isAttacker || ifb.containsEnemyOf(isAttacker))
				results.add(ifb);
		}
		return results;
	}
	
	protected int getHighestMoveCost(Collection<GroundUnit> units) {
		int highest = 0;
		for (GroundUnit unit : units) {
			int cost = unit.getDeployCost();
			if (highest < cost) highest = cost;
		}
		return highest;
	}
	
	/**
	 * Calculates the deployable strength of the units we can move, taking into 
	 * account movement point costs.
	 * @return
	 */
	protected float recomputeAvailableStrength() {
		int remainingMovePoints = movePointsAvailable - intel.getSide(isAttacker).getMovementPointsSpent().getModifiedInt();
			
		availableStrength = 0;
		for (GroundUnit unit : availableUnitsSorted) {
			if (remainingMovePoints <= movePointThreshold) break;
			availableStrength += unit.getBaseStrength();
			remainingMovePoints -= unit.getDeployCost();
		}
		//printDebug("  Available strength is " + availableStrength);
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
			
			float prio = record.industry.getPriority();
			// too important to give up
			if (!isAttacker && prio >= 3)
				continue;
			
			float strRatio = record.getEffectiveStrengthRatio(false);
			float target = STRENGTH_RATIO_TO_WRITE_OFF;
			if (isAttacker) target *= 1.25f;
			if (strRatio == 0) target *= 1.5f;
			target -= prio * 0.15f;
			
			if (strRatio < target) 
			{
				float hypotheticalOurStr = record.ourStr + availableStrength;
				float hypotheticalRatio = hypotheticalOurStr/record.theirStr;
				if (hypotheticalRatio < target) 
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
			if (unit.isPlayer != this.isPlayer) continue;	// ally AI doesn't move player units (or vice-versa)
			if (!allowDrop && !unit.isDeployed()) continue;
			if (unit.getDestination() != null) continue;
			if (unit.isWithdrawing()) continue;
			if (unit.getMorale() < MIN_MORALE_TO_REDEPLOY) continue;
			if (unit.isReorganizing() || unit.isAttackPrevented()) continue;
			if (!allowMilitia && unit.getUnitDef().hasTag(GroundUnitDef.TAG_MILITIA)) continue;
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
			if (unit.getUnitDef().hasTag(GroundUnitDef.TAG_MILITIA))
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
				proceed = decisionLoop(count);
			}
		} catch (Exception ex) {
			log.error("AI orders failed", ex);
		}
	}
	
	public void checkAbilityUse() {
		// try-catch here as well so an exception in ability use doesn't block normal movement
		try {
		GroundBattleSide side = intel.getSide(isAttacker);
		PersonAPI user = side.getCommander();
		
		List<Pair<AbilityPlugin, Float>> abilitiesSorted = new ArrayList<>();
		
		for (AbilityPlugin ability : side.getAbilities()) {
			Pair<String, Map<String, Object>> disableReason = ability.getDisabledReason(user);
			if (disableReason != null) {
				//printDebug("  Ability disabled: " + disableReason.two.get("desc"));
				continue;
			}
			
			float score = ability.getAIUsePriority(this);
			if (score < MIN_SCORE_FOR_ABILITY) continue;
			printDebug("  Checking ability for use: " + ability.getDef().name + ", " + score);
			abilitiesSorted.add(new Pair<>(ability, score));
		}
		
		if (abilitiesSorted.isEmpty()) return;
		
		Collections.sort(abilitiesSorted, new NexUtils.PairWithFloatComparator(true));
		
		AbilityPlugin best = abilitiesSorted.get(0).one;
		printDebug("AI trying ability: " + best.getDef().name);
		boolean success = best.aiExecute(this, user);
		
		} catch (Exception ex) {
			log.error("Ability use check failed", ex);
		}
	}
	
	public void getInfo() {
		printDebug("Preparing information for AI, is attacker: " + isAttacker);

		movePointsAvailable = intel.getSide(isAttacker).getMovementPointsPerTurn().getModifiedInt();

		// wrong way to do it, since moving units already on ground doesn't use supplies
		/*
		if (this.isPlayer) {
			movePointsAvailable = (int)Math.min(movePointsAvailable, Global.getSector().getPlayerFleet().getCargo().getSupplies());
		}
		*/
		
		// leave some move points for player to use
		movePointThreshold = 0;
		if (intel.getTurnNum() > 1 && !isPlayer && intel.playerIsAttacker != null && intel.playerIsAttacker == this.isAttacker) {
			movePointThreshold = getHighestMoveCost(intel.getPlayerData().getUnits());
			printDebug("Reserving " + movePointThreshold + " move points for player");
		}
		
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
		
		Collections.sort(industriesWithEnemySorted, INDUSTRY_SORT_COMPARATOR);
		
		// concentration of force hax: for attacker, take only the top three industries that we do not already have a presence on
		if (isAttacker) {
			List<IFBStrengthRecord> toRemove = new ArrayList<>();
			int extraCount = 0;
			for (IFBStrengthRecord record : industriesWithEnemySorted) {
				if (record.ourStr > 0) continue;
				extraCount++;
				if (extraCount > 3) {
					toRemove.add(record);
				}
			}
			industriesWithEnemySorted.removeAll(toRemove);
		}
				
		printDebug("Listed industries with enemy presence");
		for (IFBStrengthRecord record : industriesWithEnemySorted) {
			printDebug(String.format(" - Industry %s has strength ratio %.2f, reinforcement priority %.2f", 
					record.industry.getName(),
					record.getEffectiveStrengthRatio(false),
					record.getPriorityForReinforcement(false)));
		}
		
		if (!isPlayer)
			checkAbilityUse();
		
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
				if (strAtLoc.hasEnemyPresence && remainingStr < 1.05f)
					continue;
			} else {
				if (!unit.isFleetInRange()) continue;
			}
			
			printDebug(String.format(" - Available unit: %s, at %s, strength %s", 
					unit.toString(), 
					!unit.isDeployed() ? "fleet" : unit.getLocation().getName(), 
					unit.getAttackStrength()));
			availableUnitsSorted.add(unit);
		}
		
		Collections.sort(availableUnitsSorted, new Comparator<GroundUnit>() {
			// undeployed units before deployed ones, then strong ones before weak ones
			@Override
			public int compare(GroundUnit one, GroundUnit two) {
				if (!one.isDeployed() && two.isDeployed())
					return -1;
				if (one.isDeployed() && !two.isDeployed())
					return 1;
				return Float.compare(two.getBaseStrength(), one.getBaseStrength());
			}
		});

		// for player, remove units that we don't have supplies to deploy
		if (isPlayer) {
			float suppliesAvailable = Global.getSector().getPlayerFleet().getCargo().getSupplies();
			Iterator<GroundUnit> iter = availableUnitsSorted.iterator();
			while (iter.hasNext()) {
				GroundUnit unit = iter.next();
				if (unit.getLocation() != null) continue;
				float cost = unit.getDeployCost();
				if (cost > suppliesAvailable) {
					//log.info("Removing available unit due to lack of supplies: " + unit.getName());
					iter.remove();
					continue;
				}
				suppliesAvailable -= cost;
			}
		}

		/*
		log.info("Printing sorted units");
		for (GroundUnit unit : availableUnitsSorted) {
			log.info(unit + ": " + unit.getBaseStrength());
		}
		*/
		
		recomputeAvailableStrength();
		writeOffIndustries();
	}
	
	/**
	 * Move units to where they're needed, while we can.
	 * @param iter The iteration number of the loop.
	 * @return True if further action should be taken (i.e. this method should 
	 * be called again), false otherwise.
	 */
	public boolean decisionLoop(int iter) {
		// stalemated for too many turns, withdraw attacker
		if (isAttacker && !isPlayer && intel.getTurnsSinceLastAction() >= GBConstants.WITHDRAW_AFTER_NO_COMBAT_TURNS) {
			orderWithdrawal();
			return false;
		}
		
		boolean movedAnything = false;
		// this prevents it from getting stuck if all the target industries are writeoffs
		boolean ignoreWriteoff = writeoffIndustries.size() == industriesWithEnemySorted.size();
		boolean canAct = availableStrength > 0 && intel.getSide(isAttacker).getMovementPointsRemaining() > movePointThreshold;
		if (!canAct) return false;
		
		for (IFBStrengthRecord toReinforce : industriesWithEnemySorted) {
			printDebug(String.format("Considering plans for industry %s (priority %s)", 
					toReinforce.industry.getName(), toReinforce.getPriorityForReinforcement(false)));
			if (writeoffIndustries.contains(toReinforce) && !ignoreWriteoff) 
			{
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
				toReinforce.ourStr += unit.getAttackStrength();
				toReinforce.getEffectiveStrengthRatio(true);
				float newDestPrio = toReinforce.getPriorityForReinforcement(true);
				printDebug(String.format("  Destination %s now has priority %s", toReinforce.industry.getName(), newDestPrio));
				if (origin != null) {
					origin.ourStr -= unit.getAttackStrength();
					origin.getEffectiveStrengthRatio(true);
					// don't recompute origin's priority, to avoid circular unit movements because it fell in priority
					//float newOriginPrio = origin.getPriorityForReinforcement(true);
					//printDebug(String.format("  Origin %s now has priority %s", origin.industry.getName(), newOriginPrio));
				}
				
				movedAnything = true;
				break;
			} 
			if (movedAnything) break;
		}
		
		if (movedAnything) {
			recomputeAvailableStrength();
			// resort industries that need deployment, since priority changed
			Collections.sort(industriesWithEnemySorted, INDUSTRY_SORT_COMPARATOR);
			writeOffIndustries();
		}
		
		boolean canContinue = movedAnything && availableStrength > 0 && intel.getSide(isAttacker).getMovementPointsRemaining() > movePointThreshold;
		return canContinue;
	}
	
	public void orderWithdrawal() {
		int remainingMovePoints = movePointsAvailable - intel.getSide(isAttacker).getMovementPointsSpent().getModifiedInt();
		
		for (GroundUnit unit : availableUnitsSorted) {
			if (remainingMovePoints <= movePointThreshold) break;
			unit.orderWithdrawal();
		}
	}
			
	protected boolean canUnleashMilitia() {
		return intel.turnNum >= intel.getMilitiaUnleashTurn();
	}
	
	public static void printDebug(String str) {
		if (!PRINT_DEBUG && !ExerelinModPlugin.isNexDev) return;
		log.info(str);
	}
	
	public static class IFBStrengthRecord {
		public boolean isAttacker;	// is this the attacking side's copy of the record?
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
		
		/**
		 * Get the ratio of our strength to enemy strength, on this industry.<br/>
		 * "Effective" means apply the industry's strength mult (it's already been applied
		 * in calculating the local strength, but do it again due to factors like square-cube law
		 * and the modifier also offering a damage reduction).
		 * @param recompute
		 * @return
		 */
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
			
			boolean weHoldThis = industry.heldByAttacker == isAttacker;
			float strRatio = getEffectiveStrengthRatio(false);
			if (isAttacker && !weHoldThis)
				strRatio *= 0.5f;	// underestimate our strength on industries we do not hold, to favor concentration of force
			
			float prio = 1.25f - strRatio;
			if (prio < 0) 
				prio = 0;
			else if (!isAttacker && !weHoldThis) 
				prio /= 2;	// counterattacks by defender have lower priority
			prio += GroundBattleSide.getDefendPriority(industry.getIndustry()) * 0.125f;
			
			reinforcePriorityCache = prio;
			return reinforcePriorityCache;
		}
	}
	
	public static final Comparator<IFBStrengthRecord> INDUSTRY_SORT_COMPARATOR = new Comparator<IFBStrengthRecord>() {
		@Override
		public int compare(IFBStrengthRecord one, IFBStrengthRecord two) {
			return Float.compare(two.getPriorityForReinforcement(false), one.getPriorityForReinforcement(false));
		}
	};
}