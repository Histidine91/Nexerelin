package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class GBUtils {
	
	/**
	 * Estimates the strength of the militia, marines and heavy units in the planetary garrison.
	 * @param intel
	 * @param useHealth If true, will take into account garrison damage from recent invasions.
	 * @return
	 */
	public static float[] estimateDefenderStrength(GroundBattleIntel intel, boolean useHealth) {
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
		
		militia = Math.round(militia * countForSize * 2.5f);
		marines = Math.round(marines * countForSize);
		heavies = Math.round(heavies * countForSize / GroundUnit.HEAVY_COUNT_DIVISOR);
		
		return new float[] {militia * GroundUnit.ForceType.MILITIA.strength,
				marines * GroundUnit.ForceType.MARINE.strength,
				heavies * GroundUnit.ForceType.HEAVY.strength};
	}
	
	public static float[] estimatePlayerStrength() {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		int marines = cargo.getMarines();
		int heavyArms = (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS);
		heavyArms = Math.min(heavyArms, marines/GroundUnit.CREW_PER_MECH);
		
		int remainingMarines = marines - heavyArms * GroundUnit.CREW_PER_MECH;
		
		return new float[] {remainingMarines * GroundUnit.ForceType.MARINE.strength,
				heavyArms * GroundUnit.ForceType.HEAVY.strength};
	}
	
	public static float getTroopCountForMarketSize(MarketAPI market) {
		int size = market.getSize();
		float mult = (float)Math.pow(2, size - 1);
		
		return Math.round(mult * GBConstants.BASE_GARRISON_SIZE);
	}
}
