package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.utilities.NexUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

public class GroundBattleSide {
	
	public static final Map<String, Integer> INDUSTRY_DEFEND_PRIORITY = new HashMap<>();
	
	public static Logger log = Global.getLogger(GroundBattleSide.class);
	
	protected GroundBattleIntel intel;
	protected boolean isAttacker;
	protected FactionAPI faction;
	protected List<GroundUnit> units = new LinkedList<>();
	protected PersonAPI commander;
	protected float startingStrength;	// set for defender only
	protected Map<ForceType, Integer> losses = new HashMap<>();
	protected Map<ForceType, Integer> lossesLastTurn = new HashMap<>();
	protected Map<String, Object> data = new HashMap<>();
	protected StatBonus damageDealtMod = new StatBonus();
	protected StatBonus damageTakenMod = new StatBonus();
	protected StatBonus moraleDamTakenMod = new StatBonus();
	protected StatBonus dropCostMod = new StatBonus();
	protected StatBonus bombardmentCostMod = new StatBonus();
	protected MutableStat dropAttrition = new MutableStat(0);
	
	public GroundBattleSide(GroundBattleIntel intel, boolean isAttacker) {
		this.intel = intel;
		this.isAttacker = isAttacker;
		
		if (!isAttacker) {
			commander = intel.getMarket().getAdmin();
		}
	}
	
	public Map<String, Object> getData() {
		return data;
	}	

	public StatBonus getDamageDealtMod() {
		return damageDealtMod;
	}
	
	public StatBonus getDamageTakenMod() {
		return damageTakenMod;
	}
	
	public StatBonus getMoraleDamTakenMod() {
		return moraleDamTakenMod;
	}
	
	public StatBonus getDropCostMod() {
		return dropCostMod;
	}
	
	public StatBonus getBombardmentCostMod() {
		return bombardmentCostMod;
	}
	
	public MutableStat getDropAttrition() {
		return dropAttrition;
	}
	
	public FactionAPI getFaction() {
		return faction;
	}
	
	public List<GroundUnit> getUnits() {
		return units;
	}
	
	public Map<ForceType, Integer> getLosses() {
		return losses;
	}
	
	public Map<ForceType, Integer> getLossesLastTurn() {
		return lossesLastTurn;
	}
	
	public void reportLosses(GroundUnit unit, int num) {
		ForceType type = unit.getType();
		NexUtils.modifyMapEntry(losses, type, num);
		NexUtils.modifyMapEntry(lossesLastTurn, type, num);
		if (unit.isPlayer) {
			NexUtils.modifyMapEntry(intel.playerData.getLosses(), type, num);
			NexUtils.modifyMapEntry(intel.playerData.getLossesLastTurn(), type, num);
		}
	}
	
	public void generateDefenders() {
		float militia = 1, marines = 0, heavies = 0;
		if (intel.market.getSize() >= 5) {
			militia = 0.75f;
			marines = 0.25f;
		}
		if (intel.market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY)) {
			militia -= 0.25f;
			marines += 0.25f;
		}
			
		for (IndustryForBattle ind : intel.industries) {
			militia += ind.getPlugin().getTroopContribution("militia");
			marines += ind.getPlugin().getTroopContribution("marine");
			heavies += ind.getPlugin().getTroopContribution("heavy");
		}
		
		float countForSize = GBUtils.getTroopCountForMarketSize(intel.getMarket());
		countForSize *= 0.5f + (intel.market.getStabilityValue() / 10f) * 0.75f;
		
		militia = Math.round(militia * countForSize * 2.5f);
		marines = Math.round(marines * countForSize);
		heavies = Math.round(heavies * countForSize / GroundUnit.HEAVY_COUNT_DIVISOR);
		
		log.info(String.format("Available troops: %s militia, %s marines, %s heavy", militia, marines, heavies));
		
		// divide these peeps into units of the appropriate size
		createDefenderUnits(ForceType.MILITIA, Math.round(militia));
		createDefenderUnits(ForceType.MARINE, Math.round(marines));
		createDefenderUnits(ForceType.HEAVY, Math.round(heavies));
		
		allocateDefenders();
		
		for (GroundUnit unit : units) {
			startingStrength += unit.getBaseStrength();
		}
	}
	
	public void createDefenderUnits(ForceType type, int numTroops) {
		int sizePerUnit = intel.unitSize.getAverageSizeForType(type);
		int numUnits = (int)((float)numTroops/sizePerUnit);
		if (numUnits == 0 && numTroops > intel.unitSize.getAverageSizeForType(type)/2) {
			numUnits = 1;
		}
		for (int i=0; i<numUnits; i++) {
			int size = Math.round(numTroops/numUnits);
			GroundUnit unit = new GroundUnit(intel, type, size, units.size());
			unit.faction = intel.market.getFaction();
			units.add(unit);
		}
	}
	
	public void allocateDefenders() {
		List<IndustryForBattle> priority = new LinkedList<>(intel.industries);
		Collections.sort(priority, PRIORITY_SORT);
		
		List<IndustryForBattle> pickFrom = new LinkedList<>();
		List<GroundUnit> toAssign = new ArrayList<>();
		
		// assign marine and heavy units to priority industries
		for (GroundUnit unit : units) {
			if (unit.type == ForceType.MARINE || unit.type == ForceType.HEAVY) {
				toAssign.add(unit);
			}
		}
		Collections.shuffle(toAssign);
		for (GroundUnit unit : toAssign) {
			if (pickFrom.isEmpty()) {
				pickFrom.addAll(priority);
			}
			IndustryForBattle loc = pickFrom.get(0);
			unit.setLocation(loc);
			log.info(String.format("Adding %s unit to %s", unit.type, loc.ind.getCurrentName()));
			pickFrom.remove(0);
		}
		
		toAssign.clear();
		pickFrom.clear();
		
		// assign militia to industries
		for (GroundUnit unit : units) {
			if (unit.type == ForceType.MILITIA) {
				toAssign.add(unit);
			}
		}
		for (GroundUnit unit : toAssign) {
			if (pickFrom.isEmpty()) {
				pickFrom.addAll(priority);
			}
			IndustryForBattle loc = pickFrom.get(0);
			unit.setLocation(loc);
			log.info(String.format("Adding %s unit to %s", unit.type, loc.ind.getCurrentName()));
			pickFrom.remove(0);
		}
	}
	
	public static int getDefendPriorityFromTags(Industry ind) {
		Set<String> tags = ind.getSpec().getTags();
		if (tags.contains(Industries.TAG_GROUNDDEFENSES) 
				|| tags.contains(Industries.TAG_MILITARY)
				|| tags.contains(Industries.TAG_COMMAND))
			return 4;
		if (tags.contains(Industries.TAG_SPACEPORT))
			return 3;
		if (tags.contains(Industries.TAG_PATROL))
			return 2;
		
		return 0;
	}
	
	public static int getDefendPriority(Industry ind) {
		String id = ind.getId();
		if (INDUSTRY_DEFEND_PRIORITY.containsKey(id))
			return INDUSTRY_DEFEND_PRIORITY.get(id);
		
		int value = getDefendPriorityFromTags(ind);
		INDUSTRY_DEFEND_PRIORITY.put(id, value);
		return value;
	}	
	
	public static final Comparator<IndustryForBattle> PRIORITY_SORT = new Comparator<IndustryForBattle>() {
		@Override
		public int compare(IndustryForBattle one, IndustryForBattle two) {
			int prio1 = getDefendPriority(one.ind), prio2 = getDefendPriority(two.ind);
			if (prio1 != prio2) return Integer.compare(prio2, prio1);
			
			return Float.compare(two.ind.getBuildCost(), one.ind.getBuildCost());
		}
	};
}
