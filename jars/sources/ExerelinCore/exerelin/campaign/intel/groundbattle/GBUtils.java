package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

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
		return new float[] {
			counts[0] * ForceType.MILITIA.strength,
			counts[1] * ForceType.MARINE.strength, 
			counts[2] * ForceType.HEAVY.strength,
		};
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
			float ratio = demand == 0 ? 0 : avail/demand;
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
}
