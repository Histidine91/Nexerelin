package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.groundbattle.plugins.GroundBattlePlugin;
import exerelin.utilities.CrewReplacerUtils;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

public class GroundBattleRoundResolve {
	
	public static Logger log = Global.getLogger(GroundBattleRoundResolve.class);
	public static final boolean PRINT_DEBUG = false;
		
	protected GroundBattleIntel intel;
	protected Map<GroundUnitDef, Integer> atkLosses = new HashMap<>();
	protected Map<GroundUnitDef, Integer> defLosses = new HashMap<>();
	protected Map<GroundUnitDef, Integer> playerLosses = new HashMap<>();
	protected Set<IndustryForBattle> hadCombat = new HashSet<>();

	/**
	 * First float in pair is by local attacker, second is by local defender.
	 * These may not be the same as the planetary attacker/defender, although they should remain within a turn.
	 */
	protected Map<IndustryForBattle, Pair<Float, Float>> localDamageDealt = new HashMap<>();
	
	public GroundBattleRoundResolve(GroundBattleIntel intel) {
		this.intel = intel;
	}
	
	public void resolveRound() {
		// attack, resolve moves, attack again
		// then check who holds each industry
		// update morale and such
		for (GroundUnit unit : intel.getAllUnits()) {
			unit.lossesLastTurn = 0;
			unit.moraleDeltaLastTurn = 0;
		}
		intel.getSide(true).getLossesLastTurnV2().clear();
		intel.getSide(false).getLossesLastTurnV2().clear();
		intel.playerData.getLossesLastTurnV2().clear();
		
		boolean anyAction = false;
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.beforeTurnResolve(intel.turnNum);
		}
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.beforeCombatResolve(intel.turnNum, 1);
		}
		
		for (IndustryForBattle ifb : intel.industries) {
			anyAction |= resolveCombatOnIndustry(ifb);
		}
		
		processUnitsRoundIntermission();
		updateIndustryOwners();
		
		// move units to their destinations
		for (GroundUnit unit : intel.getAllUnits()) {
			if (unit.isReorganizing() && !unit.isWithdrawing()) continue;
			boolean isWithdrawal = unit.isWithdrawing();
			unit.executeMove(false);
			
			if (!isWithdrawal) anyAction = true;
		}
		
		resetMovementPointsSpent(false);
		resetMovementPointsSpent(true);
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.beforeCombatResolve(intel.turnNum, 2);
		}
		
		for (IndustryForBattle ifb : intel.industries) {
			anyAction |= resolveCombatOnIndustry(ifb);
		}
		
		processUnitsAfterRound();
		updateIndustryOwners();
		processUnitsAfterRound2();
		updateIndustryOwners();
		disruptIndustriesFromCombat();
		intel.disruptIndustries();
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.afterTurnResolve(intel.turnNum);
		}
		
		if (anyAction)
			intel.resetTurnsSinceLastAction();
		else
			intel.incrementTurnsSinceLastAction();
	}
	
	/**
	 * Clears the movement points spent table, then applies the overdraft modifier if needed.
	 * @param isAttacker
	 */
	public void resetMovementPointsSpent(boolean isAttacker) {
		GroundBattleSide side = intel.getSide(isAttacker);
		int spentThisTurn = side.getMovementPointsSpent().getModifiedInt();
		int pointsPerTurn = side.getMovementPointsPerTurn().getModifiedInt();
		int overdraft = spentThisTurn - pointsPerTurn;
		
		side.getMovementPointsSpent().unmodify();
		if (overdraft > 0) {
			side.getMovementPointsSpent().modifyFlat("overdraft", overdraft);
		}
	}
	
	public void checkReorganize(GroundUnit unit) {
		if (unit.morale < GBConstants.REORGANIZE_AT_MORALE) {
			printDebug(String.format("  Unit %s reorganizing due to low morale: %s", unit.name, unit.morale));
			unit.reorganize(1);
		}
	}
	
	public void processUnitsRoundIntermission() {
		for (GroundUnit unit : intel.getAllUnits()) {
			if (unit.getSize() <= 0) {	// ded
				unit.destroyUnit(0);
			}
			// was in combat, morale too low, not withdrawn
			else if (unit.lossesLastTurn > 0 && unit.morale < GBConstants.BREAK_AT_MORALE && !unit.isWithdrawing()) 
			{
				tryRoutUnit(unit);
			}
		}
	}
	
	public void processUnitsAfterRound() {
		for (GroundUnit unit : intel.getAllUnits()) {
			unit.reorganize(-1);
			unit.preventAttack(-1);
			
			IndustryForBattle lastLocation = unit.location;
			
			if (unit.isPlayer && unit.lossesLastTurn > 0) {
				GroundBattleLog lg = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_LOSSES, intel.turnNum);
				lg.params.put("unit", unit);
				lg.params.put("losses", unit.lossesLastTurn);
				lg.params.put("morale", unit.moraleDeltaLastTurn);
				lg.params.put("location", lastLocation);
				intel.addLogEvent(lg);				
			}
			
			if (unit.getSize() <= 0) {	// ded
				unit.destroyUnit(0);
			}
		}
	}
	
	public void processUnitsAfterRound2() {
		
		for (GroundUnit unit : intel.getAllUnits()) {
			if (unit.location != null && unit.location.isContested()) {
				unit.modifyMorale(-GBConstants.MORALE_LOSS_FROM_COMBAT);
			} else {
				unit.modifyMorale(GBConstants.MORALE_RECOVERY_OUT_OF_COMBAT, 0, 0.65f);
			}
			
			// was in combat, morale too low, not withdrawn
			if (unit.lossesLastTurn > 0 && unit.morale < GBConstants.BREAK_AT_MORALE && unit.location != null) 
			{
				tryRoutUnit(unit);
			}
			else {
				checkReorganize(unit);
			}
		}
	}
	
	public IndustryForBattle tryRoutUnit(GroundUnit unit) {
		if (unit.getSize() < intel.getUnitSize().getMinSizeForType(unit.getUnitDefId())) {
			printDebug(String.format("  %s broken and too many losses, destroying", unit.name));
			unit.destroyUnit(0.5f);
			return null;
		}
		
		printDebug(String.format("  Trying to rout %s due to low morale: %s", unit.name, unit.morale));
		WeightedRandomPicker<IndustryForBattle> picker = new WeightedRandomPicker<>();
		for (IndustryForBattle ifb : intel.getIndustries()) {
			// can only rout to locations that are held by our side and not contested
			if (ifb.heldByAttacker != unit.isAttacker) continue;
			if (ifb.isContested()) continue;
			if (ifb == unit.getLocation()) continue;
			float weight = 1;
			if (unit.getDestination() == ifb)
				weight = 2;
			picker.add(ifb, weight);
		}
		IndustryForBattle selected = picker.pick();
		if (selected != null) {
			printDebug(String.format("  Unit %s retreating to %s", unit.name, selected.ind.getCurrentName()));
			IndustryForBattle previous = unit.getLocation();
			unit.inflictAttrition(0.5f, this, null);
			unit.setDestination(selected);
			unit.executeMove(true);
			unit.reorganize(1);
			GroundBattleLog lg = new GroundBattleLog(intel, GroundBattleLog.TYPE_UNIT_ROUTED, intel.turnNum);
			lg.params.put("unit", unit);
			lg.params.put("location", selected);
			lg.params.put("previous", previous);
			intel.addLogEvent(lg);
			return selected;
		}			
		else {
			unit.destroyUnit(0.5f);
			return null;
		}
	}
	
	public void updateIndustryOwners() {
		for (IndustryForBattle ifb : intel.industries) {
			ifb.updateOwner(hadCombat.contains(ifb));
		}
	}

	protected Pair<Float, Float> getOrGenerateDamageDealtEntry(IndustryForBattle ifb) {
		if (!localDamageDealt.containsKey(ifb)) {
			localDamageDealt.put(ifb, new Pair<Float, Float>(0f, 0f));
		}
		return localDamageDealt.get(ifb);
	}
	
	public boolean resolveCombatOnIndustry(IndustryForBattle ifb) {
		if (!ifb.isContested()) return false;
		printDebug("Resolving combat on " + ifb.ind.getCurrentName());
		hadCombat.add(ifb);
		
		float atkStr = getAttackStrengthOnIndustry(ifb, true) * MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		printDebug(String.format("  Attacker strength: %.2f", atkStr));
		float defStr = getAttackStrengthOnIndustry(ifb, false) * MathUtils.getRandomNumberInRange(0.8f, 1.2f);		
		printDebug(String.format("  Defender strength: %.2f", defStr));

		float localAtkStr = ifb.heldByAttacker ? defStr : atkStr;
		float localDefStr = ifb.heldByAttacker ? atkStr : defStr;

		Pair<Float, Float> ddEntry = getOrGenerateDamageDealtEntry(ifb);
		ddEntry.one += localAtkStr;
		ddEntry.two += localDefStr;
		
		printDebug(String.format("  Applying damage to defender"));
		distributeDamage(ifb, false, atkStr);
		printDebug(String.format("  Applying damage to attacker"));
		distributeDamage(ifb, true, defStr);
		
		return true;
	}
	
	/**
	 * Distributes the specified damage among all units of the specified side on the specified industry. 
	 * Each unit's share of the damage is proportional to its base strength.
	 * @param ifb
	 * @param attacker Is the damage recipient the attacking side?
	 * @param dam
	 */
	public void distributeDamage(IndustryForBattle ifb, boolean attacker, float dam) {
		List<GroundUnit> units = new ArrayList<>();
		for (GroundUnit unit : ifb.units) {
			if (unit.isAttacker == attacker)
				units.add(unit);
		}
		distributeDamage(units, dam);
	}
	
	/**
	 * Distributes the specified damage among all units of the specified side on the specified industry. 
	 * Each unit's share of the damage is proportional to its base strength.
	 * @param units
	 * @param dam
	 */
	public void distributeDamage(List<GroundUnit> units, float dam) {
		float totalStrength = 0;
		Map<GroundUnit, Float> contribs = new HashMap<>();
		for (GroundUnit unit : units) {
			float contrib = unit.getBaseStrength() * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			totalStrength += contrib;
			contribs.put(unit, contrib);
		}
		for (GroundUnit unit : units) {
			float mult = contribs.get(unit)/totalStrength;
			damageUnit(unit, dam * mult);
		}
	}
	
	public void damageUnit(GroundUnit unit, float dmg) {
		dmg = unit.getAdjustedDamageTaken(dmg);
		
		float dmgPerKill = unit.getUnitDef().strength;
		int kills = (int)(dmg/dmgPerKill);
		
		// if any remaining damage after kills, roll to kill one more unit
		float surplusDmg = dmg % dmgPerKill;
		if (Math.random() * dmgPerKill < surplusDmg) {
			kills++;
		}
		damageUnitMorale(unit, kills);
		inflictUnitLosses(unit, kills);
	}
	
	public float damageUnitMorale(GroundUnit unit, int kills) {
		if (kills == 0) return 0;
		float lossProportion = (float)kills/unit.getSize();
		float moraleDmg = lossProportion/GBConstants.MORALE_DAMAGE_FACTOR;
		moraleDmg = unit.getAdjustedMoraleDamageTaken(moraleDmg);
		float moraleDmgClamped = unit.modifyMorale(-moraleDmg);
		
		printDebug(String.format("    Unit %s took %.2f morale damage, now has %.2f", 
				unit.toString(), moraleDmg, unit.morale));
		
		return moraleDmgClamped;
	}
	
	public void inflictUnitLosses(GroundUnit unit, int kills) {
		if (kills == 0) return;
		
		Map<GroundUnitDef, Integer> toModify = unit.isAttacker ? atkLosses : defLosses;
		GroundUnitDef def = unit.getUnitDef();

		boolean hasEquip = unit.unitDef.equipment != null;
		int personnelKills = hasEquip ? Math.round(kills * (float)def.personnel.mult/def.equipment.mult) : kills;
		int equipmentKills = hasEquip ? kills : 0;
		int equipmentKillsTrue = inflictUnitCommodityLosses(unit, equipmentKills, true);
		int personnelKillsTrue = inflictUnitCommodityLosses(unit, personnelKills, false);
		int killsTrue = hasEquip ? equipmentKillsTrue : personnelKillsTrue;

		unit.lossesLastTurn += kills;
		
		NexUtils.modifyMapEntry(toModify, unit.unitDef, kills);

		if (unit.isPlayer) {
			NexUtils.modifyMapEntry(playerLosses, unit.unitDef, kills);
			//intel.playerData.xpTracker.data.remove(prevMarines - currMarines, true);	// do this in inflictUnitCommodityLosses
		}
		
		printDebug(String.format("    Unit %s (%s) took %s losses (%s true losses)", unit.name, unit.getUnitDef().name, kills, killsTrue));
	}

	/**
	 * Reduce the unit's commodity counts due to taking losses.
	 * @param unit
	 * @param kills Kills to inflict on the unit.
	 * @param isEquipment
	 * @return Computed kill count (may differ from {@code kills} due to some commodity types having a non-standard damage resistance.)
	 */
	public int inflictUnitCommodityLosses(GroundUnit unit, int kills, boolean isEquipment)
	{
		if (kills <= 0) return 0;

		GroundUnitDef def = unit.getUnitDef();

		int totalInUnit = isEquipment ? unit.getEquipmentCount() : unit.getPersonnelCount();
		Map<String, Integer> commodities = isEquipment ? unit.getEquipmentMap() : unit.getPersonnelMap();

		int computedKills = 0;
		for (String commodityId : commodities.keySet()) {
			int myCount = commodities.get(commodityId);
			float myDeathShare = (float)myCount/totalInUnit;
			float myDeathsRaw = kills * myDeathShare;

			// damage resistance based on the personnel type's utility
			String jobId = isEquipment ? def.equipment.crewReplacerJobId : def.personnel.crewReplacerJobId;
			float damResist = CrewReplacerUtils.getCommodityPower(jobId, commodityId);
			if (damResist <= 0) damResist = 1;	// safety

			myDeathsRaw /= damResist;

			float remainder = myDeathsRaw % 1;

			int myDeaths = (int)myDeathsRaw;
			if (Math.random() < remainder) myDeathsRaw += 1;

			NexUtils.modifyMapEntry(commodities, commodityId, -myDeaths);
			printDebug(String.format("      Killing %s of commodity type %s for unit %s", myDeaths, commodityId, unit.getName()));
			computedKills += myDeaths;

			intel.getSide(unit.isAttacker).reportLossesByCommodity(unit, commodityId, myDeaths);
			if (unit.isPlayer() && Commodities.MARINES.equals(commodityId)) {
				intel.playerData.xpTracker.data.remove(myDeaths, true);
			}
		}

		return computedKills;
	}

	public void disruptIndustriesFromCombat() {
		for (IndustryForBattle ifb : localDamageDealt.keySet()) {
			disruptIndustryFromCombat(ifb);
		}
	}

	public void disruptIndustryFromCombat(IndustryForBattle ifb) {
		if (ifb.getPlugin().getDef().hasTag("noBombard") || ifb.getPlugin().getDef().hasTag("resistBombard"))
			return;

		Pair<Float, Float> entry = localDamageDealt.get(ifb);
		float localAtkDmg = entry.one;
		float localDefDmg = entry.two;
		if (localDefDmg <= 0.1) localDefDmg = 0.1f;
		float surplus = (localAtkDmg - localDefDmg)/localDefDmg;
		if (surplus < GBConstants.DISRUPT_DAMAGE_MIN_FACTOR) return;
		if (surplus > 1) surplus = 1;

		Industry ind = ifb.getIndustry();
		float disruptTime = NexUtilsMarket.getIndustryDisruptTime(ind) * GBConstants.DISRUPT_DAMAGE_TIME_MULT * surplus;
		ind.setDisrupted(ind.getDisruptedDays() + disruptTime, true);
	}
	
	public float getAttackStrengthOnIndustry(IndustryForBattle ifb, boolean attacker) 
	{
		float str = 0;
		for (GroundUnit unit : ifb.units) {
			if (unit.isAttacker != attacker) continue;
			float contrib = unit.getAttackStrength();
			contrib *= GBConstants.BASE_DAMAGE_MULT;
			contrib *= intel.unitSize.damMult;
			contrib *= NexConfig.groundBattleDamageMult;
			printDebug(String.format("    Unit %s (%s) contributing attack strength: %.2f", 
					unit.name, unit.getUnitDef().name, contrib));
			str += contrib;
		}
		
		return str;
	}
	
	public static float computeShortageMult(MarketAPI market) {
		float totalDemand = 0f;
		float totalShortage = 0f;
		for (CommodityOnMarketAPI com : market.getAllCommodities()) {
			if (com.isPersonnel()) continue;
			if (com.getCommodity().hasTag(Commodities.TAG_META)) continue;
			
			int a = com.getAvailable();			
			float max = com.getMaxDemand();
			totalDemand += max;
			totalShortage += Math.max(0, max - a);
		}
		float mult = 1f;
		if (totalShortage > 0 && totalDemand > 0) {
			mult = Math.max(0, totalDemand - totalShortage) / totalDemand;
		}
		return mult;
	}
	
	public static Map<CommodityOnMarketAPI, Float> computeInvasionValuables(MarketAPI market) 
	{
		Map<CommodityOnMarketAPI, Float> result = new HashMap<>();
		for (CommodityOnMarketAPI com : market.getAllCommodities()) {
			if (com.isPersonnel()) continue;
			if (com.getCommodity().hasTag(Commodities.TAG_META)) continue;
			
			int a = com.getAvailable();
			if (a > 0) {
				float num = BaseIndustry.getSizeMult(a) * com.getCommodity().getEconUnit() * 0.5f;
				result.put(com, num);
			}
		}
		
		return result;
	}
	
	public static float getBaseInvasionValue(MarketAPI market, Map<CommodityOnMarketAPI, Float> valuables) {
		float targetValue = 0f;
		for (CommodityOnMarketAPI com : valuables.keySet()) {
			targetValue += valuables.get(com) * com.getCommodity().getBasePrice();
		}
		targetValue *= 0.1f;
		targetValue *= computeShortageMult(market);
		return targetValue;
	}

	/**
	 * Used to get player loot after winning the ground battle.
	 * @param market
	 * @param mult
	 * @return A {@code CargoAPI} containing the loot obtained.
	 */
	public static CargoAPI lootMarket(MarketAPI market, float mult) {
		Map<CommodityOnMarketAPI, Float> valuables = computeInvasionValuables(market);
		Random random = new Random();
		WeightedRandomPicker<CommodityOnMarketAPI> picker = new WeightedRandomPicker<CommodityOnMarketAPI>(random);
		CargoAPI result = Global.getFactory().createCargo(true);
		
		for (CommodityOnMarketAPI com : valuables.keySet()) {
			picker.add(com, valuables.get(com));
		}
		float targetValue = getBaseInvasionValue(market, valuables);
		targetValue *= mult;
		
		//float chunks = 10f;
		float chunks = valuables.size();
		if (chunks > 6) chunks = 6;
		for (int i = 0; i < chunks; i++) {
			float chunkValue = targetValue * 1f / chunks;
			float randMult = StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f);
			chunkValue *= randMult;

			CommodityOnMarketAPI pick = picker.pick();
			int quantity = (int) (chunkValue / pick.getCommodity().getBasePrice());
			if (quantity <= 0) continue;

			// handled in InvasionRound
			//pick.addTradeModMinus("invasion_" + Misc.genUID(), -quantity, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);

			result.addCommodity(pick.getId(), quantity);
		}

		//raidSpecialItems(result, random, true);

		result.sort();

		float credits = (int)(targetValue * 0.1f * StarSystemGenerator.getNormalRandom(random, 0.5f, 1.5f));
		if (credits < 0) credits = 0;
		result.getCredits().add(credits);

		//result.clear();
		
		Global.getSector().getPlayerFleet().getCargo().getCredits().add(credits);
		if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE) != null) {
			market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addAll(result);
		}

		//NexUtilsMarket.reportInvadeLoot(dialog, market, tempInvasion, tempInvasion.invasionLoot);
		return result;
	}
	
	public static void printDebug(String text) {
		if (!PRINT_DEBUG) return;
		log.info(text);
	}
}
