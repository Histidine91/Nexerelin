package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.intel.groundbattle.GBDataManager.AbilityDef;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.plugins.AbilityPlugin;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import lombok.Getter;
import org.apache.log4j.Logger;

import java.util.*;

public class GroundBattleSide {
	
	public static final Map<String, Float> INDUSTRY_DEFEND_PRIORITY = new HashMap<>();
	
	public static Logger log = Global.getLogger(GroundBattleSide.class);
	
	protected GroundBattleIntel intel;
	protected boolean isAttacker;
	protected FactionAPI faction;
	protected List<GroundUnit> units = new LinkedList<>();
	protected PersonAPI commander;
	protected float currNormalBaseStrength;	// set for defender only
	protected List<AbilityPlugin> abilities = new ArrayList<>();
	@Deprecated @Getter private Map<ForceType, Integer> losses = new HashMap<>();
	@Deprecated @Getter private Map<ForceType, Integer> lossesLastTurn = new HashMap<>();
	@Getter	protected Map<String, Integer> lossesV2 = new HashMap<>();
	@Getter	protected Map<String, Integer> lossesLastTurnV2 = new HashMap<>();

	protected Map<String, Object> data = new HashMap<>();
	protected StatBonus damageDealtMod = new StatBonus();
	protected StatBonus damageTakenMod = new StatBonus();
	protected StatBonus moraleDamTakenMod = new StatBonus();
	protected StatBonus dropCostMod = new StatBonus();
	protected StatBonus bombardmentCostMod = new StatBonus();
	protected MutableStat dropAttrition = new MutableStat(0);
	protected MutableStat movementPointsPerTurn = new MutableStat(0);
	protected MutableStat movementPointsSpent = new MutableStat(0);
	
	public GroundBattleSide(GroundBattleIntel intel, boolean isAttacker) {
		this.intel = intel;
		this.isAttacker = isAttacker;
		
		if (!isAttacker) {
			commander = intel.getMarket().getAdmin();
		}
		
		for (AbilityDef adef : GBDataManager.getAbilityDefs()) {
			AbilityPlugin plugin = AbilityPlugin.loadPlugin(this, adef.id);
			abilities.add(plugin);
		}
	}

	protected Object readResolve() {
		if (lossesV2 == null) lossesV2 = new HashMap<>();
		if (lossesLastTurnV2 == null) lossesLastTurnV2 = new HashMap<>();
		return this;
	}
	
	public GroundBattleIntel getIntel() {
		return intel;
	}
	
	public boolean isAttacker() {
		return isAttacker;
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
	
	public MutableStat getMovementPointsSpent() {
		return movementPointsSpent;
	}
	
	public MutableStat getMovementPointsPerTurn() {
		return movementPointsPerTurn;
	}
	
	public int getMovementPointsRemaining() {
		return movementPointsPerTurn.getModifiedInt() - movementPointsSpent.getModifiedInt();
	}
	
	public FactionAPI getFaction() {
		return faction;
	}
	
	public List<GroundUnit> getUnits() {
		return units;
	}
	
	public PersonAPI getCommander() {
		return commander;
	}
	
	public List<AbilityPlugin> getAbilities() {
		return abilities;
	}
	
	public int getGlobalAbilityCooldown() {
		String key = "abilityCooldown";
		Integer cooldown = (Integer)data.get(key);
		if (cooldown == null) return 0;
		return cooldown;
	}
	
	public void modifyGlobalAbilityCooldown(int cooldown) {
		String key = "abilityCooldown";
		Integer currCooldown = (Integer)data.get(key);
		if (currCooldown != null) {
			cooldown += currCooldown;
		}
		if (cooldown <= 0)
			data.remove(key);
		else
			data.put(key, cooldown);
	}

	@Deprecated
	public void reportLosses(GroundUnit unit, int num) {
		ForceType type = unit.getType();
		NexUtils.modifyMapEntry(losses, type, num);
		NexUtils.modifyMapEntry(lossesLastTurn, type, num);
		if (unit.isPlayer) {
			NexUtils.modifyMapEntry(intel.playerData.getLosses(), type, num);
			NexUtils.modifyMapEntry(intel.playerData.getLossesLastTurn(), type, num);
		}
		
		// losses inflicted by player contribute towards orphans made
		if (intel.playerIsAttacker != null) {
			if (intel.playerIsAttacker != unit.isAttacker) {
				int casualties = num;
				if (unit.getType() == ForceType.HEAVY) casualties *= 2;
				if (intel.playerIsAttacker) casualties *= 2;	// include estimated civilian fatalities
				StatsTracker.getStatsTracker().modifyOrphansMadeByCrewCount(casualties, unit.faction.getId());
			}
		}
	}

	public void reportLossesByCommodity(GroundUnit unit, String commodityId, int num) {
		NexUtils.modifyMapEntry(lossesV2, commodityId, num);
		NexUtils.modifyMapEntry(lossesLastTurnV2, commodityId, num);
		if (unit.isPlayer) {
			NexUtils.modifyMapEntry(intel.playerData.getLossesV2(), commodityId, num);
			NexUtils.modifyMapEntry(intel.playerData.getLossesLastTurnV2(), commodityId, num);
		}

		incrementOrphansMade(unit, commodityId, num);
	}

	public void incrementOrphansMade(GroundUnit unit, String commodityId, int num) {
		if (intel.playerIsAttacker == null) return;
		if (intel.playerIsAttacker == unit.isAttacker) return;
		CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(commodityId);
		if (!spec.isPersonnel()) return;

		int casualties = num;
		if (intel.playerIsAttacker) casualties *= 2;	// include estimated civilian fatalities
		StatsTracker.getStatsTracker().modifyOrphansMadeByCrewCount(casualties, unit.faction.getId());
	}
	
	public void reportTurn() {
		for (AbilityPlugin ability : abilities) {
			ability.reportTurn();
		}
		modifyGlobalAbilityCooldown(-1);
	}
	
	/**
	 * Level of local defenders' (militia in particular) unwillingness to fight.<br/>
	 * 0: Defending our home, normal strength.<br/>
	 * 1: Defending on behalf of a conqueror, reduced strength.<br/>
	 * 2: Our original faction or their ally comes to liberate us; heavily reduced 
	 * strength and some militia become rebels.
	 * @return
	 */
	protected int getResistanceTier() {
		String origOwner = NexUtilsMarket.getOriginalOwner(intel.getMarket());
		if (origOwner == null) {
			return 0;
		}
		String currOwner = intel.getMarket().getFactionId();
		String attacker = intel.getSide(true).getFaction().getId();
		
		if (AllianceManager.areFactionsAllied(attacker, origOwner)) {
			return 2;
		}
		else if (!currOwner.equals(origOwner)) return 1;
		else return 0;
	}
	
	public void generateDefenders() {
		float[] counts = GBUtils.estimateDefenderCounts(intel, true);
		float militia = counts[0];
		float marines = counts[1];
		float heavies = counts[2];
		
		float rebels = 0;
		int resistance = getResistanceTier();
		if (resistance == 2) {
			rebels += militia * GBConstants.LIBERATION_REBEL_MULT;
			militia *= 1 - GBConstants.LIBERATION_REBEL_MULT;
		}
		
		RebellionIntel rebellion = RebellionIntel.getOngoingEvent(intel.getMarket());
		if (rebellion != null && rebellion.getRebelFaction().isAtWorst(intel.getSide(true).getFaction(), RepLevel.NEUTRAL)) 
		{
			float defStrength = GBUtils.estimateTotalDefenderStrength(intel, true);
			float gs = Math.max(rebellion.getGovtStrength(), 1);
			float mult = rebellion.getRebelStrength()/gs;
			float rebsFromRebellion = defStrength * mult;
			if (!rebellion.isStarted()) rebsFromRebellion *= 0.5f;
			rebels += rebsFromRebellion;
		}
				
		log.info(String.format("Available troops: %s militia, %s marines, %s heavy", militia, marines, heavies));
		
		// divide these peeps into units of the appropriate size
		createUnits(GroundUnitDef.MILITIA, Math.round(militia));
		createUnits(GroundUnitDef.MARINE, Math.round(marines));
		createUnits(GroundUnitDef.HEAVY, Math.round(heavies));
		
		allocateDefenders();
		
		FactionAPI rebFac = Global.getSector().getFaction(NexUtilsMarket.getOriginalOwner(intel.getMarket()));
		if (rebellion != null) {
			rebFac = rebellion.getRebelFaction();
		}
		if (rebFac == null) rebFac = Global.getSector().getPlayerFaction();
		
		if (rebels > 0) {
			createAndAllocateRebels(Math.round(rebels), rebFac);
			intel.rebelsArose = true;
		}
		
		currNormalBaseStrength = GBUtils.estimateTotalDefenderStrength(intel, false);
	}
	
	public float getStrength() {
		float str = 0;
		for (GroundUnit unit : units) {
			str += unit.getAttackStrength();
		}
		return str;
	}
	
	public float getBaseStrength() {
		float str = 0;
		for (GroundUnit unit : units) {
			str += unit.getBaseStrength();
		}
		return str;
	}
	
	public void createUnits(String unitDefId, int numTroops) {
		float moraleMult = 1;
		boolean rebel = GroundUnitDef.REBEL.equals(unitDefId);
		if (!isAttacker && !rebel) {
			int resistance = getResistanceTier();
			if (resistance >= 2)
				moraleMult = 0.6f;
			else if (resistance == 1)
				moraleMult = 0.8f;
		}
		
		int sizePerUnit = intel.unitSize.getAverageSizeForType(unitDefId);
		if (rebel) {
			sizePerUnit = Math.round(sizePerUnit * GroundUnit.REBEL_COUNT_MULT);
		}
		int numUnits = (int)Math.ceil((float)numTroops/sizePerUnit - 0.25f);
		if (numUnits == 0 && numTroops > 1) {	//intel.unitSize.getAverageSizeForType(type)/2) {
			numUnits = 1;
		}
		//log.info(String.format("Can create %s units of %s", numUnits, type));
		for (int i=0; i<numUnits; i++) {
			int size = Math.round(numTroops/numUnits);
			GroundUnit unit = createUnit(unitDefId, faction, size, null);
			unit.morale *= moraleMult;
		}
	}
	
	public GroundUnit createUnit(String unitDefId, FactionAPI faction, int size, CampaignFleetAPI fleet) {
		GroundUnit unit = intel.createUnit(unitDefId, faction, isAttacker, size, fleet, units.size());
		units.add(unit);
		return unit;
	}
	
	public void allocateDefenders() {
		List<IndustryForBattle> priority = new LinkedList<>(intel.industries);
		Collections.sort(priority, PRIORITY_SORT);
		
		List<IndustryForBattle> pickFrom = new LinkedList<>();
		List<GroundUnit> toAssign = new ArrayList<>();
		
		// assign marine and heavy units to priority industries
		for (GroundUnit unit : units) {
			if (unit.isPlayer) continue;
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
			if (unit.isPlayer) continue;
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
	
	public void createAndAllocateRebels(int numTroops, FactionAPI faction) {
		log.info(String.format("Generating %s rebels", numTroops));
		int sizePerUnit = intel.unitSize.getAverageSizeForType(GroundUnitDef.REBEL);
		int numUnits = (int)((float)numTroops/sizePerUnit);
		if (numUnits == 0 && numTroops > 1) {
			numUnits = 1;
		}
		List<GroundUnit> generatedRebels = new ArrayList<>();
		for (int i=0; i<numUnits; i++) {
			int size = Math.round(numTroops/numUnits);
			GroundUnit unit = intel.createUnit(GroundUnitDef.REBEL, faction, true, size, null, generatedRebels.size());
			intel.getSide(true).getUnits().add(unit);
			generatedRebels.add(unit);
		}
		
		List<IndustryForBattle> priority = new LinkedList<>(intel.industries);
		Collections.sort(priority, PRIORITY_SORT);
		Collections.reverse(priority);	// rebels appear on lower priority industries first, since that's where the military isn't
		
		List<IndustryForBattle> pickFrom = new LinkedList<>();
		
		Collections.shuffle(generatedRebels);
		for (GroundUnit unit : generatedRebels) {
			if (pickFrom.isEmpty()) {
				pickFrom.addAll(priority);
			}
			IndustryForBattle loc = pickFrom.get(0);
			unit.setLocation(loc);
			log.info(String.format("Adding %s unit to %s", unit.type, loc.ind.getCurrentName()));
			pickFrom.remove(0);
		}
	}
	
	public static float getDefendPriorityFromTags(Industry ind) {
		Set<String> tags = ind.getSpec().getTags();
		if (tags.contains(Industries.TAG_GROUNDDEFENSES))
			return 4;
		if (tags.contains(Industries.TAG_SPACEPORT) 
				|| tags.contains(Industries.TAG_MILITARY)
				|| tags.contains(Industries.TAG_COMMAND))
			return 3;
		if (tags.contains(Industries.TAG_HEAVYINDUSTRY))
			return 2.5f;
		if (tags.contains(Industries.TAG_PATROL))
			return 1.5f;
		
		return 0.5f;
	}
	
	public static float getDefendPriority(Industry ind) {
		String id = ind.getId();
		if (ind.getDisruptedDays() > 20) {
			return 0.5f;
		}
		
		if (INDUSTRY_DEFEND_PRIORITY.containsKey(id))
			return INDUSTRY_DEFEND_PRIORITY.get(id);
		
		float value = getDefendPriorityFromTags(ind);
		INDUSTRY_DEFEND_PRIORITY.put(id, value);
		return value;
	}
	
	public static final Comparator<IndustryForBattle> PRIORITY_SORT = new Comparator<IndustryForBattle>() {
		@Override
		public int compare(IndustryForBattle one, IndustryForBattle two) {
			float prio1 = getDefendPriority(one.ind), prio2 = getDefendPriority(two.ind);
			if (prio1 != prio2) return Float.compare(prio2, prio1);
			
			return Float.compare(two.ind.getBuildCost(), one.ind.getBuildCost());
		}
	};
}
