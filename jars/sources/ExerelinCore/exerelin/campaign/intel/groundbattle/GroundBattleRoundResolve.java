package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BaseIndustry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.plugins.GroundBattlePlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

public class GroundBattleRoundResolve {
	
	public static Logger log = Global.getLogger(GroundBattleRoundResolve.class);
	public static final boolean PRINT_DEBUG = false;
		
	protected GroundBattleIntel intel;
	protected Map<ForceType, Integer> atkLosses = new HashMap<>();
	protected Map<ForceType, Integer> defLosses = new HashMap<>();
	protected Map<ForceType, Integer> playerLosses = new HashMap<>();
	protected Set<IndustryForBattle> hadCombat = new HashSet<>();
	
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
		intel.getSide(true).getLossesLastTurn().clear();
		intel.getSide(false).getLossesLastTurn().clear();
		intel.playerData.getLossesLastTurn().clear();
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.beforeTurnResolve(intel.turnNum);
		}
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.beforeCombatResolve(intel.turnNum, 1);
		}
		
		for (IndustryForBattle ifb : intel.industries) {
			resolveCombatOnIndustry(ifb);
		}
		
		processUnitsRoundIntermission();
		updateIndustryOwners();
		
		for (GroundUnit unit : intel.getAllUnits()) {
			if (unit.isReorganizing() && !unit.isWithdrawing()) continue;
			unit.executeMove(false);
		}
		
		resetMovementPointsSpent(false);
		resetMovementPointsSpent(true);
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.beforeCombatResolve(intel.turnNum, 2);
		}
		
		for (IndustryForBattle ifb : intel.industries) {
			resolveCombatOnIndustry(ifb);
		}
		
		processUnitsAfterRound();
		updateIndustryOwners();
		processUnitsAfterRound2();
		updateIndustryOwners();
		intel.disruptIndustries();
		
		for (GroundBattlePlugin plugin : intel.getPlugins()) {
			plugin.afterTurnResolve(intel.turnNum);
		}
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
		if (unit.getSize() < intel.getUnitSize().getMinSizeForType(unit.getType())) {
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
	
	public void resolveCombatOnIndustry(IndustryForBattle ifb) {
		if (!ifb.isContested()) return;
		printDebug("Resolving combat on " + ifb.ind.getCurrentName());
		hadCombat.add(ifb);
		
		float atkStr = getAttackStrengthOnIndustry(ifb, true) * MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		printDebug(String.format("  Attacker strength: %.2f", atkStr));
		float defStr = getAttackStrengthOnIndustry(ifb, false) * MathUtils.getRandomNumberInRange(0.8f, 1.2f);		
		printDebug(String.format("  Defender strength: %.2f", defStr));
		
		printDebug(String.format("  Applying damage to defender"));
		distributeDamage(ifb, false, atkStr);
		printDebug(String.format("  Applying damage to attacker"));
		distributeDamage(ifb, true, defStr);
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
		
		float dmgPerKill = unit.type.strength;
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
		
		Map<ForceType, Integer> toModify = unit.isAttacker ? atkLosses : defLosses;
		
		boolean heavy = unit.type == ForceType.HEAVY;
		if (heavy) {
			unit.heavyArms -= kills;
			unit.personnel -= kills * GroundUnit.CREW_PER_MECH;
			if (unit.heavyArms < 0) unit.heavyArms = 0;
			if (unit.personnel < 0) unit.personnel = 0;
		}
		else {
			unit.personnel -= kills;
			if (unit.personnel < 0) unit.personnel = 0;
		}
		unit.lossesLastTurn += kills;
		
		NexUtils.modifyMapEntry(toModify, unit.type, kills);
		if (unit.isPlayer) {
			NexUtils.modifyMapEntry(playerLosses, unit.type, kills);
			intel.playerData.xpTracker.data.remove(heavy ? kills * 
					GroundUnit.CREW_PER_MECH : kills, true);
		}
		
		printDebug(String.format("    Unit %s (%s) took %s losses", unit.name, unit.type.toString(), kills));
		
		intel.getSide(unit.isAttacker).reportLosses(unit, kills);
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
					unit.name, unit.type.toString(), contrib));
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
