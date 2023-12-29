package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelData;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelRank;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.plugins.GroundBattlePlugin;
import exerelin.campaign.intel.groundbattle.plugins.GroundUnitPlugin;
import exerelin.utilities.CrewReplacerUtils;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;

@Log4j
public class GroundUnit {
	
	public static final boolean USE_LOCATION_IMAGE = true;
	
	public static float PANEL_WIDTH = USE_LOCATION_IMAGE ? 220 : 200;
	public static float PANEL_HEIGHT = USE_LOCATION_IMAGE ? 120 : 110;
	public static final float TITLE_HEIGHT = 16;
	public static float LOCATION_SECTION_HEIGHT = USE_LOCATION_IMAGE ? 32 : 24;
	public static final float PADDING_X = 4;
	public static final float BUTTON_SECTION_WIDTH = 64;
	//public static final Object BUTTON_NEW_MARINE = new Object();
	//public static final Object BUTTON_NEW_HEAVY = new Object();
	
	public static final float HEAVY_COUNT_DIVISOR = 6f;	// a marine platoon has 6x as many marines as a mech platoon has mechs
	public static final float REBEL_COUNT_MULT = 0.6f;	// rebel units are 40% smaller
	public static final int CREW_PER_MECH = 2;

	public final String id = Misc.genUID();
	@Getter protected String unitDefId;
	protected transient GroundUnitDef unitDef;
	@Getter protected int index;
	@Getter protected GroundBattleIntel intel;
	@Getter @Setter	protected String name;
	@Getter @Setter	protected FactionAPI faction;
	@Getter @Setter protected CampaignFleetAPI fleet;
	protected RouteData route;
	@Getter @Setter protected boolean isPlayer;
	@Getter @Setter protected boolean isAttacker;
	@Deprecated protected ForceType type;
	@Getter protected GroundUnitPlugin plugin;
	
	@Deprecated protected int personnel;
	@Deprecated protected int heavyArms;
	@Getter	protected Map<String, Integer> personnelMap = new HashMap<>();
	@Getter protected Map<String, Integer> equipmentMap = new HashMap<>();

	protected int lossesLastTurn;
	protected float moraleDeltaLastTurn;
	protected float morale = 0.8f;
	
	public Map<String, Object> data = new HashMap<>();

	@Getter protected String currAction;
	@Getter protected IndustryForBattle location;
	@Getter protected IndustryForBattle destination;

	@Deprecated
	public GroundUnit(GroundBattleIntel intel, ForceType type, int num, int index) {
		this.intel = intel;
		this.type = type;
		this.index = index;
		plugin = GroundUnitPlugin.initPlugin(this);
		name = generateName();
		if (num > 0) setSize(num, false);
	}

	public GroundUnit(GroundBattleIntel intel, String unitDefId, int num, int index) {
		this.intel = intel;
		this.unitDefId = unitDefId;
		this.unitDef = getUnitDef();
		this.type = unitDef.type;
		this.index = index;
		plugin = GroundUnitPlugin.initPlugin(this);
		name = generateName();
		if (num > 0) setSize(num, false);
	}

	protected Object readResolve() {
		if (unitDefId == null) {
			switch (type) {
				case MARINE:
					unitDefId = "marine";
					break;
				case HEAVY:
					unitDefId = "heavy";
					break;
				case MILITIA:
					unitDefId = "militia";
					break;
				case REBEL:
					unitDefId = "rebel";
					break;
			}
		}
		unitDef = getUnitDef();
		if (unitDef == null) {
			log.info("Failed to get unitdef for unit " + name + ", has def ID " + unitDefId);
		}
		if (personnelMap == null) {
			personnelMap = new HashMap<>();
			personnelMap.put(Commodities.MARINES, personnel);
		}
		if (equipmentMap == null) {
			equipmentMap = new HashMap<>();
			equipmentMap.put(Commodities.HAND_WEAPONS, heavyArms);
		}
		if (plugin == null) {
			plugin = GroundUnitPlugin.initPlugin(this);
		}

		return this;
	}

	public GroundUnitDef getUnitDef() {
		return GroundUnitDef.getUnitDef(unitDefId);
	}

	public void setUnitDef(String unitDefId) {
		this.unitDefId = unitDefId;
		unitDef = getUnitDef();
	}
	
	protected static CargoAPI getCargo() {
		return Global.getSector().getPlayerFleet().getCargo();
	}
	
	public float setStartingMorale() {
		return plugin.setStartingMorale();
	}

	/**
	 * Returns the total number of personnel (all types) in this unit.
	 * @return
	 */
	public int getPersonnelCount() {
		return NexUtils.getMapSumInteger(personnelMap);
	}

	/**
	 * Returns the number of marines in this unit (and not any other personnel type).
	 * @return
	 */
	public int getMarines() {
		Integer count = personnelMap.get(Commodities.MARINES);
		if (count == null) return 0;
		return count;
	}

	public int getEquipmentCount() {
		return NexUtils.getMapSumInteger(equipmentMap);
	}

	/**
	 * Changes the unit size.
	 * @param num Number of marine equivalents for a marine unit, heavy arms equivalents for a heavy unit, etc.
	 * @param takeFromCargo If true, takes the needed commodities from the fleet's cargo. For compatibility with Crew Replacer,
	 *                      should always be true when resizing a player unit, or else do your own implementation (e.g. current unit split implementation).
	 */
	public void setSize(int num, boolean takeFromCargo) {
		// first return the existing units to cargo
		if (takeFromCargo) {
			returnCommoditiesToCargo();
		}

		int wantedHeavyArms = 0;
		int wantedPersonnel= 0;
		//log.info(String.format("Setting size %s for unit %s", num, name));

		if (unitDef.equipment != null) {
			wantedHeavyArms = num * unitDef.equipment.mult;
		}
		if (unitDef.personnel != null) {
			wantedPersonnel = num * unitDef.personnel.mult;
		}

		// now take the new ones from cargo
		if (takeFromCargo) {
			//log.info(String.format("Want %s marine equivalents for unit %s", wantedPersonnel, name));
			addPersonnelOrEquipmentFromCargo(wantedPersonnel, true);
			//log.info(String.format("Want %s heavy arms equivalents for unit %s", wantedHeavyArms, name));
			addPersonnelOrEquipmentFromCargo(wantedHeavyArms, false);
			
			// move the XP from player cargo to battle player data
			if (isPlayer) {
				PlayerFleetPersonnelTracker.transferPersonnel(
						PlayerFleetPersonnelTracker.getInstance().getMarineData(),
						intel.playerData.xpTracker.data,
						this.getMarines(), null);
			}
		}
		else {
			if (wantedPersonnel > 0) personnelMap.put(unitDef.personnel.commodityId, wantedPersonnel);
			if (wantedHeavyArms > 0) equipmentMap.put(unitDef.equipment.commodityId, wantedHeavyArms);
		}
	}

	/**
	 * Grabs marine equivalents or heavy arms equivalents from cargo using Crew Replacer.
	 * @param wanted
	 * @param isPersonnel
	 */
	public void addPersonnelOrEquipmentFromCargo(int wanted, boolean isPersonnel) {
		if (wanted == 0) return;

		Map<String, Integer> taken = null;

		Map<String, Integer> commodities = isPersonnel ? this.getPersonnelMap() : this.getEquipmentMap();
		if (isPersonnel) {
			String jobId = unitDef.personnel.crewReplacerJobId;
			String commodityId = unitDef.personnel.commodityId;
			taken = CrewReplacerUtils.takeCommodityFromCargo(fleet, commodityId, jobId, wanted);
			for (String thisCommodityId : taken.keySet()) {
				int count = taken.get(thisCommodityId);
				//log.info(String.format("  Adding %s of commodity %s for unit %s", count, commodityId, this.getName()));
				NexUtils.modifyMapEntry(commodities, thisCommodityId, count);
			}
		} else {
			String jobId = unitDef.equipment.crewReplacerJobId;
			String commodityId = unitDef.equipment.commodityId;
			taken = CrewReplacerUtils.takeCommodityFromCargo(fleet, commodityId, jobId, wanted);
			for (String thisCommodityId : taken.keySet()) {
				int count = taken.get(thisCommodityId);
				//log.info(String.format("  Adding %s of commodity %s for unit %s, index %s", count, commodityId, this.getName()));
				NexUtils.modifyMapEntry(commodities, thisCommodityId, count);
			}
		}

		if (taken == null) {
			log.error(String.format("Failed to obtain %s commodities from cargo (is personnel: %s", wanted, isPersonnel));
		}
	}
	
	public String generateName() {
		return plugin.generateName();
	}

	public boolean isDeployed() {
		return location != null;
	}
	
	public boolean isWithdrawing() {
		return GBConstants.ACTION_WITHDRAW.equals(currAction);
	}

	@Deprecated
	public ForceType getType() {
		return type;
	}

	/**
	 * Result is identical to {@code getSize()} for normal units,
	 * or {@code CREW_PER_MECH} times larger for heavy units.
	 * Identical to {@code getPersonnelCount(), use that instead.}
	 * @return
	 */
	@Deprecated
	public int getPersonnel() {
		return getPersonnelCount();
	}

	public float getMorale() {
		return morale;
	}
	
	public boolean isPlayer() {
		return intel.playerData.getUnits().contains(this);
	}
	
	public boolean isFleetInRange() {
		if (fleet != null && fleet.isAlive()) {
			return intel.isFleetInRange(fleet);
		}
		if (route != null) {
			return intel.isRouteInRange(route);
		}
		return true;
	}

	/**
	 * Now named @{code returnCommoditiesToCargo}.
	 */
	@Deprecated
	public void returnUnitsToCargo() {
		returnCommoditiesToCargo(getCargo(), 1);
	}

	public void returnCommoditiesToCargo() {
		returnCommoditiesToCargo(getCargo(), 1);
	}

	/**
	 * Adds the current personnel and equipment in the unit back to player cargo.
	 */
	public void returnCommoditiesToCargo(CargoAPI cargo, float mult) {
		int numMarines = this.getMarines();

		returnCommodityMapToCargo(personnelMap, cargo, mult);
		returnCommodityMapToCargo(equipmentMap, cargo, mult);

		// move the XP from battle player data to player cargo
		PlayerFleetPersonnelTracker.transferPersonnel(
				intel.playerData.xpTracker.data,
				PlayerFleetPersonnelTracker.getInstance().getMarineData(),
				numMarines, null);
	}

	/**
	 * Adds the items in the map (each multiplied by {@code mult} to the specified cargo and CLEARS the map.
	 * @param commodities
	 * @param cargo
	 */
	public static void returnCommodityMapToCargo(Map<String, Integer> commodities, CargoAPI cargo, float mult) {
		for (String commodityId : commodities.keySet()) {
			int count = commodities.get(commodityId);
			cargo.addCommodity(commodityId, Math.round(count * mult));
		}
		commodities.clear();
	}

	public void sendUnitToStorage(SubmarketAPI storage) {
		sendCommodityMapToStorage(personnelMap, storage);
		sendCommodityMapToStorage(equipmentMap, storage);
		removeUnit(false);
	}

	/**
	 * Used to send the contents of a unit to the local storage post-battle. Will clear the unit's personnel and equipment maps.
	 * @param commodities
	 * @param storage
	 */
	public void sendCommodityMapToStorage(Map<String, Integer> commodities, SubmarketAPI storage) {
		CargoAPI cargoLoot = intel.playerData.getLoot();
		CargoAPI cargoStorage = storage.getCargo();
		for (String commodityId : commodities.keySet()) {
			int count = commodities.get(commodityId);
			cargoStorage.addCommodity(commodityId, count);
			if (cargoLoot != null) {
				cargoLoot.addCommodity(commodityId, count);
			}
			NexUtils.modifyMapEntry(intel.playerData.getSentToStorage(), commodityId, count);
		}
		commodities.clear();
	}
	
	public void setLocation(IndustryForBattle newLoc) {
		if (newLoc == location) return;
		if (location != null) location.removeUnit(this);
		location = newLoc;
		if (newLoc != null) newLoc.addUnit(this);
	}
	
	public void setDestination(IndustryForBattle destination) {
		if (destination == location) {
			cancelMove();
			return;
		}
		this.destination = destination;
		intel.getSide(isAttacker).getMovementPointsSpent().modifyFlat(id + "_move", getDeployCost());
		currAction = null;
	}
	
	public void cancelMove() {
		destination = null;
		currAction = null;
		intel.getSide(isAttacker).getMovementPointsSpent().unmodifyFlat(id + "_move");
	}
	
	public void inflictAttrition(float amount, GroundBattleRoundResolve resolve, 
			InteractionDialogAPI dialog) 
	{
		if (resolve == null)
			resolve = new GroundBattleRoundResolve(intel);
		int losses = (int)(getSize() * amount);
		// wipeout
		if (losses > getSize()) {
			losses = getSize();
			destroyUnit(0);
		}
		if (losses > 0) {
			float moraleDam = resolve.damageUnitMorale(this, losses);
			resolve.inflictUnitLosses(this, losses);
			
			if (isPlayer && dialog != null) {
				TextPanelAPI text = dialog.getTextPanel();
				text.setFontSmallInsignia();
				String moraleStr = StringHelper.toPercent(moraleDam);
				String str = String.format(getString("deployAttrition"), losses, moraleStr);
				Color neg = Misc.getNegativeHighlightColor();
				LabelAPI para = text.addPara(str);
				para.setHighlight(losses + "", moraleStr);
				para.setHighlightColors(neg, neg);
				text.setFontInsignia();
			}
		}
	}
	
	public void deploy(IndustryForBattle newLoc, InteractionDialogAPI dialog) {		
		setLocation(newLoc);
		int cost = getDeployCost();
		if (isPlayer) {
			getCargo().removeSupplies(cost);
			intel.getPlayerData().suppliesUsed += cost;
		}
		if (dialog != null) {
			AddRemoveCommodity.addCommodityLossText(Commodities.SUPPLIES, cost, dialog.getTextPanel());
		}
		destination = null;
		float attrition = intel.getSide(isAttacker).dropAttrition.getModifiedValue()/100;
		if (attrition > 0) {
			log.info(String.format(
					"%s receiving %s attrition during deployment", 
					toString(), StringHelper.toPercent(attrition)));
			inflictAttrition(attrition, null, dialog);
		}
		if (true || isPlayer) {
			GroundBattleLog log = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_MOVED, intel.turnNum);
			log.params.put("unit", this);
			log.params.put("location", location);
			intel.addLogEvent(log);
		}
		intel.getSide(isAttacker).getMovementPointsSpent().modifyFlat(id + "_deploy", cost);
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.reportUnitMoved(this, null);
		}
	}
	
	public void orderWithdrawal() {
		currAction = GBConstants.ACTION_WITHDRAW;
		intel.getSide(isAttacker).getMovementPointsSpent().modifyFlat(id + "_move", getDeployCost());
		destination = null;
	}
	
	public void executeMove(boolean silent) {
		IndustryForBattle lastLoc = location;
		if (isWithdrawing()) {
			if (intel.isPlayerInRange()) {
				setLocation(null);
				currAction = null;
			}
		}
		else {
			if (destination == null) return;
			intel.getMovedFromLastTurn().put(this, location);
			setLocation(destination);
			destination = null;
		}
		if (!silent) {
			GroundBattleLog log = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_MOVED, intel.turnNum);
			log.params.put("unit", this);
			log.params.put("previous", lastLoc);
			log.params.put("location", location);
			intel.addLogEvent(log);
		}
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.reportUnitMoved(this, lastLoc);
		}
	}
	
	public void removeUnit(boolean returnToCargo) 
	{
		if (isPlayer && returnToCargo) {
			for (String commodityId : this.equipmentMap.keySet()) {
				NexUtils.modifyMapEntry(intel.playerData.getDisbandedV2(), commodityId, this.equipmentMap.get(commodityId));
			}
			for (String commodityId : this.personnelMap.keySet()) {
				NexUtils.modifyMapEntry(intel.playerData.getDisbandedV2(), commodityId, this.personnelMap.get(commodityId));
			}
			log.info("Disbanding " + name + ": " + getSize());
			returnUnitsToCargo();
		}
		setLocation(null);
		intel.getSide(isAttacker).units.remove(this);
		if (isPlayer)
			intel.playerData.getUnits().remove(this);
		
		log.info(String.format("Removed unit %s (%s)", name, type));
	}
	
	public void destroyUnit(float recoverProportion) {
		cancelMove();
		if (isPlayer && intel.isPlayerInRange()) {
			this.returnCommoditiesToCargo(getCargo(), recoverProportion);
		}
		GroundBattleLog lg = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_DESTROYED, intel.turnNum);
		lg.params.put("unit", this);
		lg.params.put("location", this.location);
		intel.addLogEvent(lg);	
		removeUnit(false);
	}
	
	/**
	 * Spend an additional {@code turns} reorganizing. Negative values to progress reorganization.
	 * @param turns
	 */
	public void reorganize(int turns) {
		Integer curr = (Integer)data.get("reorganizing");
		if (curr == null) curr = 0;
		int newVal = curr + turns;
		if (newVal <= 0) data.remove("reorganizing");
		else data.put("reorganizing", newVal);
	}
	
	public boolean isReorganizing() {
		return data.containsKey("reorganizing");
	}
	
	public void preventAttack(int turns) {
		Integer curr = (Integer)data.get("preventAttack");
		if (curr == null) curr = 0;
		int newVal = curr + turns;
		if (newVal <= 0) data.remove("preventAttack");
		else data.put("preventAttack", newVal);
	}
	
	public boolean isAttackPrevented() {
		return data.containsKey("preventAttack");
	}
	
	public void addActionText(TooltipMakerAPI info) {
		Color color = Misc.getTextColor();
		String strId = "currAction";
		
		if (GBConstants.ACTION_WITHDRAW.equals(currAction)) {
			strId += "Withdrawing";
		}
		else if (isReorganizing() && isAttackPrevented()) {
			strId += "Shocked";
			color = Misc.getNegativeHighlightColor();
		}
		else if (isReorganizing()) {
			strId += "Reorganizing";
			color = Misc.getNegativeHighlightColor();
		}
		else if (destination != null) {
			strId += "Moving";
		}
		else if (location == null) {
			strId += "WaitingFleet";
		}
		else if (location.isContested()) {
			strId += "Engaged";
		}
		else {
			strId += "Waiting";
		}
		String str = getString(strId);
		if (destination != null) {
			str = String.format(str, destination.ind.getCurrentName());
		}
		else if (location != null && location.isContested()) {
			str = String.format(str, location.ind.getCurrentName());
		}
		
		info.addPara(str, color, 0);
	}
	
	public void setMorale(float morale) {
		this.morale = morale;
	}
	
	/**
	 * Clamped to range [0, 1].
	 * @param delta
	 * @return Delta after clamping.
	 */
	public float modifyMorale(float delta) {
		return modifyMorale(delta, 0, 1);
	}
	
	/**
	 * Clamped to range [min, max].
	 * @param delta
	 * @param min
	 * @param max
	 * @return Delta after clamping.
	 */
	public float modifyMorale(float delta, float min, float max) {
		float newMorale = morale + delta;
		if (newMorale > max) newMorale = max;
		if (newMorale < 0) newMorale = 0;
		
		// prevent "going backwards"
		if (delta > 0 && newMorale < morale) {
			newMorale = morale;
		}
		else if (delta < 0 && newMorale > morale) {
			newMorale = morale;
		}
		
		delta = newMorale - morale;
		
		morale = newMorale;
		moraleDeltaLastTurn += delta;
		return delta;
	}
	
	/**
	 * Unit size multiplied by the unitdef's strength multiplier.
	 * @return
	 */
	public float getBaseStrength() {
		return getSizeForStrength() * unitDef.strength;
	}
	
	public static float getBaseStrengthForAverageUnit(UnitSize size, String unitDefId)
	{
		return size.avgSize * GroundUnitDef.getUnitDef(unitDefId).strength;
	}
	
	public int getDeployCost() {
		return getDeployCost(this.getSize(), unitDefId, isAttacker, intel);
	}
	
	public static int getDeployCost(int size, String unitDefId, boolean isAttacker, GroundBattleIntel intel)
	{
		float cost = size * GBConstants.SUPPLIES_TO_DEPLOY_MULT;
		cost *= GroundUnitDef.getUnitDef(unitDefId).dropCostMult;
		cost = intel.getSide(isAttacker).dropCostMod.computeEffective(cost);
		return Math.round(cost);
	}
	
	/**
	 * Partial attack stat, before fleet/market bonuses are applied.
	 * @return
	 */
	public MutableStat getAttackStat() {
		return plugin.getAttackStat();
	}
	
	/**
	 * Hack to replace fleet marine XP bonus with the local XP bonus.
	 * @param stats
	 * @param attackPower True to get modifier to attack power, false to get reduction to casualties.
	 */
	public void substituteLocalXPBonus(StatBonus stats, boolean attackPower) {
		PersonnelData data = intel.playerData.xpTracker.data;
		injectXPBonus(stats, data, attackPower);
	}
	
	/**
	 * Applies the XP bonus from the provided {@code PersonnelData} to the provided stats.
	 * @param stats
	 * @param data
	 * @param attackPower True to get modifier to attack power, false to get reduction to casualties.
	 */
	public void injectXPBonus(StatBonus stats, PersonnelData data, boolean attackPower) {
		String id = "marineXP";
		float effectBonus = PlayerFleetPersonnelTracker.getInstance().getMarineEffectBonus(data);
		float casualtyReduction = PlayerFleetPersonnelTracker.getInstance().getMarineLossesReductionPercent(data);
		//log.info(String.format("XP %s translating to %s effect bonus, %s casualty red.", data.xp, effectBonus, casualtyReduction));
		PersonnelRank rank = data.getRank();
		if (attackPower) {
			if (effectBonus > 0) {
			//stats.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).modifyMult(id, 1f + effectBonus * 0.01f, rank.name + " marines");
				stats.modifyPercent(id, effectBonus, rank.name + " " + StringHelper.getString("marines"));
			} else {
				//stats.getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD).unmodifyMult(id);
				stats.unmodifyPercent(id);
			}
		}
		else {
			//log.info("Reducing casualties by " + casualtyReduction + " from " + data.xp);
			if (casualtyReduction > 0) {
				stats.modifyMult(id, 1f - casualtyReduction * 0.01f, rank.name + " " + StringHelper.getString("marines"));
			} else {
				stats.unmodifyMult(id);
			}
		}
	}

	/**
	 * Attack stat bonus from external factors, like the unit's backing fleet.
	 */
	public StatBonus getAttackStatBonus() {
		return plugin.getAttackStatBonus();
	}
	
	public float getAttackStrength() {
		return plugin.getAttackStrength();
	}
	
	public float getAdjustedMoraleDamageTaken(float dmg) {
		return plugin.getAdjustedMoraleDamageTaken(dmg);
	}
	
	/**
	 * Partial defense stat, before fleet/market bonuses are applied.
	 * @return
	 */
	public MutableStat getDefenseStat() {
		return plugin.getDefenseStat();
	} 
	
	public StatBonus getDefenseStatBonus() {
		return plugin.getDefenseStatBonus();
	}
	
	public float getAdjustedDamageTaken(float dmg) {
		return plugin.getAdjustedDamageTaken(dmg);
	}
	
	public int getSize() {
		int num = unitDef.equipment != null ? getEquipmentCount() : getPersonnelCount();
		return num;
	}
	
	/**
	 * Adjusted for the strength of each commodity type used in this unit.
	 * @return
	 */
	public float getSizeForStrength() {
		float str = 0;
		Map<String, Integer> map = unitDef.equipment != null ? this.equipmentMap : this.personnelMap;
		String jobId = unitDef.equipment != null ? unitDef.equipment.crewReplacerJobId : unitDef.personnel.crewReplacerJobId;
		for (String commodity : map.keySet()) {
			int count = map.get(commodity);
			float power = CrewReplacerUtils.getCommodityPower(jobId, commodity);
			//log.info(String.format("Commodity %s has power %.1f", commodity, power));
			float thisStr = power * count;
			str += thisStr;
		}
		return str;
	}
	
	/**
	 * e.g. two half-companies and one full company will return about the same value.
	 * @return
	 */
	public float getNumUnitEquivalents() {
		float num = getSizeForStrength();
		return num/intel.unitSize.getAverageSizeForType(unitDef);
	}
	
	/**
	 * Creates an empty card with the new marine/heavy unit buttons.
	 * @param parent
	 * @param size
	 * @return
	 */
	public static CustomPanelAPI createBlankCard(CustomPanelAPI parent, UnitSize size) 
	{
		float pad = 3;
		FactionAPI faction = Global.getSector().getPlayerFaction();
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH, PANEL_HEIGHT, 
				new GroundUnitPanelPlugin(faction, null, faction.getCrest()));
		
		float btnWidth = 160;
		
		// add create unit buttons
		TooltipMakerAPI buttonHolder = card.createUIElement(PANEL_WIDTH, PANEL_HEIGHT, false);
		int numButtons = 0;
		for (GroundUnitDef def : GroundUnitDef.UNIT_DEFS) {
			if (!def.playerCanCreate) continue;
			if (!def.shouldShow()) continue;
			ButtonAPI newUnit = buttonHolder.addButton(String.format(getString("btnNewUnit"), def.name, size.getName()),
					def, btnWidth, 24, numButtons > 0 ? pad : 0);
			int minSize = size.getMinSizeForType(def);
			int reqPers = def.personnel != null ? minSize * def.personnel.mult : 0;
			int reqEquip = def.equipment != null ? minSize * def.equipment.mult : 0;
			boolean enough = reqPers <= 0 || CrewReplacerUtils.getAvailableCommodity(player,
					def.personnel.commodityId, def.personnel.crewReplacerJobId) >= reqPers;
			enough &= reqEquip <= 0 || CrewReplacerUtils.getAvailableCommodity(player,
					def.equipment.commodityId, def.equipment.crewReplacerJobId) >= reqEquip;

			if (!enough) newUnit.setEnabled(false);

			numButtons++;
		}
		
		card.addUIElement(buttonHolder).inTL((PANEL_WIDTH-btnWidth)/2, PANEL_HEIGHT/2 - 24 * numButtons/2);
		
		return card;
	}
	
	public CustomPanelAPI createUnitCard(CustomPanelAPI parent, boolean forDialog)
	{
		float sizeMult = 1, pad = 3;
		if (forDialog) {
			sizeMult = 1.5f;
			pad = 4.5f;
		}
		
		String commoditySprite = unitDef.getSprite();
		String crest = faction.getCrest();
		
		CustomPanelAPI card = parent.createCustomPanel(PANEL_WIDTH * sizeMult, 
				PANEL_HEIGHT * sizeMult, 
				new GroundUnitPanelPlugin(faction, this, crest));
		TooltipMakerAPI title = card.createUIElement(PANEL_WIDTH * sizeMult, 
				TITLE_HEIGHT * sizeMult, false);
		if (forDialog) title.setParaSmallInsignia();
		title.addPara(name, faction.getBaseUIColor(), pad);
		card.addUIElement(title).inTL(0, 0);
		
		// begin stats section
		TooltipMakerAPI stats = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH)/2 * sizeMult, 
				(PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT) * sizeMult, false);

		String commodityId;
		TooltipMakerAPI line;

		// number of personnel, e.g. marines
		int personnel = getPersonnelCount();
		if (personnel > 0) {
			commodityId = personnelMap.keySet().iterator().next();
			line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(
					commodityId).getIconName(), 16 * sizeMult);
			line.addPara(getPersonnelCount() + "", 0);
			stats.addImageWithText(pad);
			stats.addTooltipToPrevious(createTooltip("marines"), TooltipLocation.BELOW);
		}

		
		// number of equipment, e.g. heavy arms
		int equipment = getEquipmentCount();
		if (equipment > 0) {
			commodityId = equipmentMap.keySet().iterator().next();
			line = stats.beginImageWithText(Global.getSettings().getCommoditySpec(
					commodityId).getIconName(), 16 * sizeMult);
			line.addPara(equipment + "", 0);
			stats.addImageWithText(pad);
			stats.addTooltipToPrevious(createTooltip("heavyArms"), TooltipLocation.BELOW);
		}
		else {
			stats.addSpacer(19 * sizeMult);
		}
		
		// morale
		line = stats.beginImageWithText("graphics/icons/insignia/16x_star_circle.png", 
				16 * sizeMult);
		Color moraleColor = getMoraleColor(morale);
		String moraleStr = Math.round(this.morale * 100) + "%";
		line.addPara(moraleStr, moraleColor, 0);
		stats.addImageWithText(pad);
		stats.addTooltipToPrevious(createTooltip("morale"), TooltipLocation.BELOW);
		
		card.addUIElement(stats).belowLeft(title, 0);
		
		TooltipMakerAPI stats2 = card.createUIElement((PANEL_WIDTH - BUTTON_SECTION_WIDTH)/2 * sizeMult, 
				(PANEL_HEIGHT - TITLE_HEIGHT - LOCATION_SECTION_HEIGHT) * sizeMult, false);
		
		// attack power
		line = stats2.beginImageWithText(Global.getSettings().getSpriteName("misc", 
				"nex_groundunit_attackpower"), 16 * sizeMult);
		line.addPara(String.format("%.0f", getAttackStrength()), 0);
		stats2.addImageWithText(pad);
		stats2.addTooltipToPrevious(createTooltip("attackPower"), TooltipLocation.BELOW);
		card.addUIElement(stats2).rightOfTop(stats, 0);
		
		// defence power
		line = stats2.beginImageWithText(Global.getSettings().getSpriteName("misc", 
				"nex_groundunit_defensepower"), 16 * sizeMult);
		line.addPara(String.format("%.2f", getAdjustedDamageTaken(1)), 0);
		stats2.addImageWithText(pad);
		stats2.addTooltipToPrevious(createTooltip("defensePower"), TooltipLocation.BELOW);
		card.addUIElement(stats2).rightOfTop(stats, 0);
		
		// deploy cost
		if (true || location == null) {
			line = stats2.beginImageWithText(Global.getSettings().getCommoditySpec(
					Commodities.SUPPLIES).getIconName(), 16 * sizeMult);
			line.addPara(getDeployCost() + "", 0);
			stats2.addImageWithText(pad);
			stats2.addTooltipToPrevious(createTooltip("supplies"), TooltipLocation.BELOW);
		}
		else {
			//stats.addSpacer(19);
		}		
		// end stats section
		
		// location
		TooltipMakerAPI loc = card.createUIElement(PANEL_WIDTH * sizeMult, 
				LOCATION_SECTION_HEIGHT * sizeMult, false);
		
		// with image version
		if (USE_LOCATION_IMAGE) {
			String img = location != null ? location.ind.getCurrentImage() : "graphics/illustrations/free_orbit.jpg";
			line = loc.beginImageWithText(img, 32 * sizeMult);
			addActionText(line);
			loc.addImageWithText(pad);
			
		} else {
			if (location != null) {
				String locStr = String.format(getString("currLocation"), location.getName());
				loc.addPara(locStr, 0);
			}
			addActionText(loc);
		}
		card.addUIElement(loc).inBL(0, 2 * sizeMult);
		
		
		// button holder
		if (!forDialog) {
			TooltipMakerAPI buttonHolder = card.createUIElement(BUTTON_SECTION_WIDTH * sizeMult, 
				PANEL_HEIGHT * sizeMult * 2, false);
			buttonHolder.addButton(StringHelper.getString("action", true), this, 
					(BUTTON_SECTION_WIDTH - 6) * sizeMult, 16 * sizeMult, 0);
			ButtonAPI qm = buttonHolder.addButton(getString("btnQuickMove", true), new UnitQuickMoveHax(this), 
					(BUTTON_SECTION_WIDTH - 6) * sizeMult, 16 * sizeMult, 0);
			boolean deployed = location != null;
			boolean canDeploy = !deployed && getDeployCost() <= Global.getSector().getPlayerFleet().getCargo().getSupplies()
					&& isFleetInRange();
			if (isReorganizing() || intel.getSide(isAttacker).getMovementPointsRemaining() <= 0
					|| (!deployed && !canDeploy)) 
			{
				qm.setEnabled(false);
			}
			card.addUIElement(buttonHolder).inTR(1 * sizeMult, 2 * sizeMult);
		}
		
		return card;
	}
	
	public static Color getMoraleColor(float morale) {
		Color color = Misc.getHighlightColor();
		if (morale > .6) color = Misc.getPositiveHighlightColor();
		else if (morale < .3) color = Misc.getNegativeHighlightColor();
		return color;
	}

	public void addCommodityBreakdown(TooltipMakerAPI tooltip, boolean isEquipment) {
		Color hl = Misc.getHighlightColor();
		Map<String, Integer> commodities = isEquipment ? this.getEquipmentMap() : this.getPersonnelMap();
		for (String commodityId : commodities.keySet()) {
			int count = commodities.get(commodityId);
			TooltipMakerAPI imgWithText = tooltip.beginImageWithText(GroundBattleIntel.getCommoditySprite(commodityId), 24);
			imgWithText.addPara("%s " + GroundBattleIntel.getCommodityName(commodityId), 0, hl, count + "");
			tooltip.addImageWithText(0);
		}
	}

	public String getBackgroundIcon() {
		return plugin.getBackgroundIcon();
	}
	
	public TooltipCreator createTooltip(final String id) {
		final GroundUnit unit = this;
		return new TooltipCreator() {
			@Override
			public boolean isTooltipExpandable(Object tooltipParam) {
				return false;
			}

			@Override
			public float getTooltipWidth(Object tooltipParam) {
				return 280;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				String str = getString("unitCard_tooltip_" + id);
				tooltip.addPara(str, 0);
				switch (id) {
					case "marines":
						addCommodityBreakdown(tooltip, false);
						break;
					case "heavyArms":
						addCommodityBreakdown(tooltip, true);
						break;
					case "morale":
						Color hl = Misc.getHighlightColor();
						//tooltip.setBulletedListMode(BaseIntelPlugin.BULLET);
						str = " - " + getString("unitCard_tooltip_" + id + 2);
						tooltip.addPara(str, 3, hl,
								StringHelper.toPercent(1 - GBConstants.MORALE_ATTACK_MOD),
								StringHelper.toPercent(1 + GBConstants.MORALE_ATTACK_MOD)
						);	str = " - " + getString("unitCard_tooltip_" + id + 3);
						tooltip.addPara(str, 0, hl, StringHelper.toPercent(GBConstants.REORGANIZE_AT_MORALE));
						str = " - " + getString("unitCard_tooltip_" + id + 4);
						tooltip.addPara(str, 0);
						break;
					case "attackPower":
						{
							str = getString("unitCard_tooltip_atkbreakdown_header");
							tooltip.addPara(str, 3);
							tooltip.addStatModGrid(360, 60, 10, 3, getAttackStat(), true, NexUtils.getStatModValueGetter(true, 0));
							StatBonus bonus = unit.getAttackStatBonus();
							if (bonus != null && !bonus.isUnmodified()) {
								tooltip.addStatModGrid(360, 60, 10, 3, bonus, true, NexUtils.getStatModValueGetter(true, 0));
							}
							bonus = intel.getSide(isAttacker).getDamageDealtMod();
							if (bonus != null && !bonus.isUnmodified()) {
								tooltip.addStatModGrid(360, 60, 10, 3, bonus, true, NexUtils.getStatModValueGetter(true, 0));
							}
							break;
						}
					case "defensePower":
						{
							str = getString("unitCard_tooltip_atkbreakdown_header");
							tooltip.addPara(str, 3);
							MutableStat def = getDefenseStat();
							if (def != null && !def.isUnmodified()) {
								tooltip.addStatModGrid(360, 60, 10, 3, getDefenseStat(), true, NexUtils.getStatModValueGetter(true, 2, true));
							}							
							StatBonus bonus = unit.getDefenseStatBonus();
							if (bonus != null && !bonus.isUnmodified()) {
								tooltip.addStatModGrid(360, 60, 10, 3, bonus, true, NexUtils.getStatModValueGetter(true, 2, true));
							}
							bonus = intel.getSide(isAttacker).getDamageTakenMod();
							if (bonus != null && !bonus.isUnmodified()) {
								tooltip.addStatModGrid(360, 60, 10, 3, bonus, true, NexUtils.getStatModValueGetter(true, 2, true));
							}		break;
						}
					default:
						break;
				}
			}
		};
	}
	
	@Override
	public String toString() {
		return String.format("%s (%s)", name, unitDef.name.toLowerCase());
	}

	@Deprecated
	public static enum ForceType {
		MARINE(Commodities.MARINES, "troopNameMarine", 1, 1, 1), 
		HEAVY(Commodities.HAND_WEAPONS, "troopNameMech", 5.5f, 1, GBConstants.HEAVY_DROP_COST_MULT),
		MILITIA(Commodities.CREW, "troopNameMilitia", 0.4f, 0.6f, 1), 
		REBEL(Commodities.CREW, "troopNameRebel", 0.5f, 0.7f, 1);	// note that attack power is halved later
		
		public final String commodityId;
		public final String nameStringId;
		public final float strength;
		public final float moraleMult;
		public final float dropCostMult;
		
		private ForceType(String commodityId, String nameStringId, float strength, 
				float moraleMult, float dropCostMult) 
		{
			this.commodityId = commodityId;
			this.nameStringId = nameStringId;
			this.strength = strength;
			this.moraleMult = moraleMult;
			this.dropCostMult = dropCostMult;
		}
		
		public String getName() {
			return getString(nameStringId);
		}
		
		public String getCommodityName() {
			return StringHelper.getCommodityName(commodityId);
		}
		
		public String getCommoditySprite() {
			return Global.getSettings().getCommoditySpec(commodityId).getIconName();
		}
		
		public boolean isInfantry() {
			return this != ForceType.HEAVY;
		}
	}
	
	public static enum UnitSize {
		PLATOON(40, 65, 1f),
		COMPANY(120, 200, 0.5f),
		BATTALION(500, 800, 0.25f),
		REGIMENT(2000, 3500, 0.1f);
		
		public final int avgSize;
		public final int maxSize;
		public final int minSize;
		public final float damMult;
		
		private UnitSize(int avgSize, int maxSize, float damMult) {
			this.avgSize = avgSize;
			this.maxSize = maxSize;
			minSize = (int)(avgSize * GBConstants.UNIT_MIN_SIZE_MULT);
			this.damMult = damMult;
		}

		@Deprecated
		public static int getSizeForType(int count, ForceType type) {
			if (type == ForceType.HEAVY) count = Math.round(count/GroundUnit.HEAVY_COUNT_DIVISOR);
			// don't do it here since it messes with the icon count in IndustryForBattle.renderTroopPanelNew
			//if (type == ForceType.REBEL) count *= REBEL_COUNT_MULT;
			return count;
		}

		public static int getSizeForType(int count, String unitDefId) {
			return getSizeForType(count, GroundUnitDef.getUnitDef(unitDefId));
		}

		public static int getSizeForType(int count, GroundUnitDef def) {
			return Math.round(count * def.unitSizeMult);
		}

		public int getAverageSizeForType(String unitDefId) {
			return getSizeForType(avgSize, unitDefId);
		}

		public int getAverageSizeForType(GroundUnitDef def) {
			return getSizeForType(avgSize, def);
		}
		
		public int getMinSizeForType(String unitDefId) {
			return getSizeForType(minSize, unitDefId);
		}

		public int getMinSizeForType(GroundUnitDef def) {
			return getSizeForType(minSize, def);
		}
		
		public int getMaxSizeForType(String unitDefId) {
			return getSizeForType(maxSize, unitDefId);
		}

		public int getMaxSizeForType(GroundUnitDef def) {
			return getSizeForType(maxSize, def);
		}
		
		public String getName() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()));
		}
		
		public String getNamePlural() {
			return getString("unit" + Misc.ucFirst(toString().toLowerCase()) + "Plural");
		}
	}
	
	public static class UnitQuickMoveHax {
		public GroundUnit unit;
		
		public UnitQuickMoveHax(GroundUnit unit) {
			this.unit = unit;
		}
	}
}
