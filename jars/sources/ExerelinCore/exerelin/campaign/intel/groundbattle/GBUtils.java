package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.invasion.CounterInvasionIntel;
import exerelin.utilities.NexConfig;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GBUtils {
	
	public static Logger log = Global.getLogger(GBUtils.class);
	
	/**
	 * Estimates the combat strength of the militia, marines and heavy units in the planetary garrison.
	 * @param intel
	 * @param useHealth If true, will take into account garrison damage from recent invasions.
	 * @return
	 */
	public static float[] estimateDefenderStrength(GroundBattleIntel intel, boolean useHealth) {
		float[] counts = estimateDefenderCounts(intel, useHealth);
		float strengthMult = estimateDefenderStrengthMult(intel);
		log.info("Strength mult estimated at " + strengthMult);
		return new float[] {
			counts[0] * GroundUnitDef.getUnitDef(GroundUnitDef.MILITIA).strength * strengthMult,
			counts[1] * GroundUnitDef.getUnitDef(GroundUnitDef.MARINE).strength * strengthMult,
			counts[2] * GroundUnitDef.getUnitDef(GroundUnitDef.HEAVY).strength * strengthMult,
		};
	}

	/**
	 * Estimates the attack strength multiplier of defender units using a temporarily created unit.
	 * @param intel
	 * @return
	 */
	public static float estimateDefenderStrengthMult(GroundBattleIntel intel) {
		GroundBattleSide defender = intel.getSide(false);
		GroundUnit temp = defender.createUnit(GroundUnitDef.MARINE, defender.getFaction(), 100, null);
		MutableStat str = temp.getAttackStat();
		StatBonus strBonus = temp.getAttackStatBonus();
		//log.info(NexUtils.mutableStatToString(str));
		//log.info(NexUtils.statBonusToString(strBonus, str.getModifiedValue()));
		float base = temp.getBaseStrength();
		float fin = temp.getAttackStrength();

		defender.getUnits().remove(temp);
		return fin/base;
	}
	
	/**
	 * Estimates the numbers of the militia, marines and heavy units in the planetary garrison.
	 * @param intel
	 * @param useHealth If true, will take into account garrison damage from recent invasions.
	 * @return
	 */
	public static float[] estimateDefenderCounts(GroundBattleIntel intel, boolean useHealth) {
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
		
		float countForSize = getTroopCountForMarketSize(intel.getMarket());
		countForSize *= 0.5f + (intel.market.getStabilityValue() / 10f) * 0.75f;
		
		float health = 1;
		if (useHealth) {
			health = 1 - GBUtils.getGarrisonDamageMemory(intel.getMarket());
		}
		
		marines *= 0.5f + 0.5f * getDeficitFactor(intel.market, Commodities.MARINES);
		heavies *= 0.5f + 0.5f * getDeficitFactor(intel.market, Commodities.MARINES, Commodities.HAND_WEAPONS);
		
		float suppliesFactor = getDeficitFactor(intel.market, Commodities.SUPPLIES);
		militia *= 1 + 0.25f * suppliesFactor;
		marines *= 1 + 0.25f * suppliesFactor;
		heavies *= 1 + 0.25f * suppliesFactor;
		
		militia = Math.round(militia * countForSize * 2.5f * health);
		marines = Math.round(marines * countForSize * health);
		heavies = Math.round(heavies * countForSize / GroundUnit.HEAVY_COUNT_DIVISOR * health);
		
		return new float[] {militia, marines, heavies};
	}
	
	public static float getDeficitFactor(MarketAPI market, String... commodities) {
		float lowest = 1;
		for (String commodity : commodities) {
			int avail = market.getCommodityData(commodity).getAvailable();
			int demand = market.getCommodityData(commodity).getMaxDemand();
			//log.info(String.format("Commodity %s has available %s, demand %s", commodity, avail, demand));
			float ratio = demand == 0 ? 1 : avail/demand;
			if (ratio < lowest)
				lowest = ratio;
		}
		return lowest;
	}
	
	public static float estimateTotalDefenderStrength(GroundBattleIntel intel, boolean useHealth) 
	{
		float str = 0;
		float[] strByType = estimateDefenderStrength(intel, useHealth);
		for (float thisStr: strByType) {
			str += thisStr;
		}
		return str;
	}
	
	public static float estimateTotalDefenderStrength(MarketAPI market, FactionAPI attacker, boolean useHealth) 
	{
		GroundBattleIntel temp = new GroundBattleIntel(market, attacker, market.getFaction());
		temp.init();
		return estimateTotalDefenderStrength(temp, useHealth);
	}
	
	public static float[] estimatePlayerStrength(GroundBattleIntel intel) {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		int marines = cargo.getMarines();
		int heavyArms = (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS);
		
		// if cramped, do marines only
		// else, heavy arms then marines
		if (false && intel.isCramped()) {
			return new float[] {marines * ForceType.MARINE.strength};
		}
		else {
			heavyArms = Math.min(heavyArms, marines/GroundUnit.CREW_PER_MECH);

			int remainingMarines = marines - heavyArms * GroundUnit.CREW_PER_MECH;

			return new float[] {remainingMarines * ForceType.MARINE.strength,
					heavyArms * ForceType.HEAVY.strength};
		}
	}
	
	public static float getTroopCountForMarketSize(MarketAPI market) {
		int size = market.getSize();
		float mult = (float)Math.pow(2, size - 1);
		
		return Math.round(mult * GBConstants.BASE_GARRISON_SIZE);
	}
	
	public static float getGarrisonDamageMemory(MarketAPI market) {
		if (!market.getMemoryWithoutUpdate().contains(GBConstants.MEMKEY_GARRISON_DAMAGE)) 
			return 0;
		float damage = market.getMemoryWithoutUpdate().getFloat(GBConstants.MEMKEY_GARRISON_DAMAGE);
		return damage;
	}
	
	public static void setGarrisonDamageMemory(MarketAPI market, float damage) {
		if (damage <= 0) {
			market.getMemoryWithoutUpdate().unset(GBConstants.MEMKEY_GARRISON_DAMAGE);
			log.info("Unsetting garrison damage for " + market.getName());
		}			
		else {
			market.getMemoryWithoutUpdate().set(GBConstants.MEMKEY_GARRISON_DAMAGE, damage);
			log.info("Setting garrison damage for " + market.getName() + ": " + String.format("%.3f", damage));
		}
	}
	
	
	public static List<Industry> convertIFBListToIndustryList(List<IndustryForBattle> ifbs) {
		List<Industry> industries = new ArrayList<>();
		for (IndustryForBattle ifb : ifbs) {
			industries.add(ifb.getIndustry());
		}
		return industries;
	}
	
	public static float getCombinedArmsBonus(float ratio) {
		float level = (float)(Math.PI/2 * ratio);
		return (float)Math.sin(level);
	}
	
	public static float getCombinedArmsRatio(List<GroundUnit> units) {
		float inf = 0;
		float mech = 0;
		for (GroundUnit unit : units) {
			if (unit.getType().isInfantry())
				inf += unit.getBaseStrength();
			else
				mech = unit.getBaseStrength();
		}
		
		if (inf == 0 || mech == 0) return 0;
		if (inf < mech) return inf/mech;
		else return mech/inf;
	}
	
	public static CounterInvasionIntel generateCounterInvasion(GroundBattleIntel gb, MarketAPI origin, MarketAPI target) {
		String factionId = origin.getFactionId();
		float fp = 0;	//InvasionFleetManager.estimatePatrolStrength(target, 0.1f);
		fp += target.getSize() * target.getSize() * 3 * 5f;
		fp *= InvasionFleetManager.getInvasionSizeMult(origin.getFactionId());
		
		// smaller than a normal invasion
		float sizeMult = 1;	//0.5f;
		sizeMult *= Math.min(1, origin.getSize()/(float)target.getSize());
		if (!Misc.isMilitary(origin)) sizeMult *= 0.7f;
		fp *= sizeMult;
		
		log.info("Counter-invasion size before pool: " + fp);
		fp = FleetPoolManager.getManager().drawFromPool(factionId, new FleetPoolManager.RequisitionParams(fp, 0, 0f, 1));
		if (FleetPoolManager.USE_POOL) {
			if (fp < 30) return null;
		}
		else if (InvasionFleetManager.getManager().getSpawnCounter(factionId) < NexConfig.pointsRequiredForInvasionFleet/5) 
		{
			return null;
		}
		log.info("Counter-invasion size after pool: " + fp);
		
		float organizeTime = 0;	//InvasionFleetManager.getOrganizeTime(fp) * 0.2f;
		
		CounterInvasionIntel counter = new CounterInvasionIntel(gb, origin.getFaction(), origin, target, fp, organizeTime);
		counter.init();
		InvasionFleetManager.getManager().modifySpawnCounterV2(factionId, -InvasionFleetManager.getInvasionPointCost(counter));
		return counter;
	}
	
	public static float getCounterInvasionDistMult(MarketAPI market) {
		float mult = 1;
		for (Industry ind : market.getIndustries()) {
			if (ind.isDisrupted()) continue;
			
			if (ind.getSpec().hasTag(Industries.TAG_COMMAND))
				mult = 4;
			else if (ind.getSpec().hasTag(Industries.TAG_MILITARY))
				mult = 2;
		}
		return mult;
	}
	
	public static MarketAPI getMarketForCounterInvasion(MarketAPI target) {
		List<MarketAPI> available = AllianceManager.getAllianceMarkets(target.getFactionId());
		List<CounterInvasionOriginPick> sorted = new ArrayList<>();
		
		for (MarketAPI market : available) {
			if (market == target) continue;
			if (market.isHidden()) continue;
			
			boolean sameSystem = market.getContainingLocation() == target.getContainingLocation();
			boolean military = Misc.isMilitary(market);
			float priority;
			if (sameSystem) priority = military ? 1 : 2;
			else priority = 3;
			
			CounterInvasionOriginPick pick = new CounterInvasionOriginPick();
			pick.market = market;
			pick.priority = priority;
			
			float score = market.getSize() * 10;
			if (military) score *= 3;
			if (!sameSystem) {
				float dist = Misc.getDistanceLY(market.getPrimaryEntity(), target.getPrimaryEntity());
				float distMult = getCounterInvasionDistMult(market);
				float maxDist = GBConstants.MAX_DIST_FOR_COUNTER_INVASION * distMult;
				
				if (dist > maxDist) continue;
				if (dist < 1) dist = 1;
				score /= dist;
			}
			// prefer to use own faction instead of allied
			if (market.getFaction() != target.getFaction()) score /= 2;
			
			pick.score = score;
			
			sorted.add(pick);
		}		
		if (sorted.isEmpty()) return null;
		
		Collections.sort(sorted);
		float bestPrio = sorted.get(0).priority;
		
		WeightedRandomPicker<CounterInvasionOriginPick> picker = new WeightedRandomPicker<>();
		for (CounterInvasionOriginPick pick : sorted) {
			if (pick.priority < bestPrio) break;
			picker.add(pick, pick.score);
		}
		if (picker.isEmpty()) return null;
		return picker.pick().market;
	}
	
	
	// priority 1: same system, military
	// priority 2: same system, non-military
	// priority 3: other system, military or non-military
	public static class CounterInvasionOriginPick implements Comparable<CounterInvasionOriginPick> {
		public MarketAPI market;
		public float priority;
		public float score;

		@Override
		public int compareTo(CounterInvasionOriginPick other) {
			if (priority != other.priority)
				return Float.compare(priority, other.priority);
			return Float.compare(other.score, score);
		}	
	}
}
