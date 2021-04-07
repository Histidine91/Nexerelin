package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
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
	protected int marinesLost;
	protected int heaviesLost;
	protected Map<String, Object> data = new HashMap<>();
	
	public GroundBattleSide(GroundBattleIntel intel, boolean isAttacker) {
		this.intel = intel;
		this.isAttacker = isAttacker;
	}
	
	public void generateDefenders() {
		float militia = 1, marines = 0, heavies = 0;
		if (intel.market.getSize() >= 5) {
			militia = 0.75f;
			marines = 0.25f;
		}
			
		for (IndustryForBattle ind : intel.industries) {
			militia += ind.getPlugin().getTroopContribution("militia");
			marines += ind.getPlugin().getTroopContribution("marine");
			heavies += ind.getPlugin().getTroopContribution("heavy");
		}
		
		float countForSize = getTroopCountForMarketSize();
		
		militia = Math.round(militia * countForSize * 2.5f);
		marines = Math.round(marines * countForSize);
		heavies = Math.round(heavies * countForSize / GroundUnit.HEAVY_COUNT_DIVISOR);
		
		log.info(String.format("Available troops: %s militia, %s marines, %s heavy", militia, marines, heavies));
		
		// divide these peeps into units of the appropriate size
		createDefenderUnits(ForceType.MILITIA, Math.round(militia));
		createDefenderUnits(ForceType.MARINE, Math.round(marines));
		createDefenderUnits(ForceType.HEAVY, Math.round(heavies));
		
		allocateDefenders();
	}
	
	public void createDefenderUnits(ForceType type, int numTroops) {
		int sizePerUnit = intel.unitSize.avgSize;
		int numUnits = (int)Math.ceil(numTroops/sizePerUnit);
		for (int i=0; i<numUnits; i++) {
			int size = Math.round(numTroops/numUnits);
			GroundUnit unit = new GroundUnit(intel, type, size);
			log.info("Generating " + type + " unit of size " + size);
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
		for (GroundUnit unit : toAssign) {
			if (pickFrom.isEmpty()) {
				pickFrom.addAll(priority);
			}
			unit.location = pickFrom.get(0);
			log.info(String.format("Adding %s unit to %s", unit.type, unit.location.ind.getCurrentName()));
			pickFrom.remove(0);
		}
		
		// assign militia to industries
		pickFrom.clear();
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
			log.info(String.format("Adding %s unit to %s", unit.type, unit.location.ind.getCurrentName()));
			pickFrom.remove(0);
		}
	}
	
	public float getTroopCountForMarketSize() {
		int size = intel.market.getSize();
		float mult = (float)Math.pow(2, size - 1);
		
		return mult * 10;
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
