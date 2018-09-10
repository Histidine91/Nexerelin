package exerelin.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class RecyclingPlant extends BaseMarketConditionPlugin {
	
	//public static final float RECYCLING_CREW = 200f;
	public static final float RECYCLING_VOLATILES = 800f;
	public static final float RECYCLING_ORGANICS = 800f;
	public static final float RECYCLING_METALS = 400f;
	public static final float RECYCLING_RARE_METALS = 20f;
	//public static final float RECYCLING_SUPPLIES = 500f;
	//public static final float RECYCLING_HEAVY_MACHINERY = 20f;
	//public static final float RECYCLING_HEAVY_MACHINERY_DEMAND = 20f;
	
	//public static final float HAX_MULT_07_OV = 25f;
	//public static final float HAX_MULT_07_METALS = 2f;	// not too high, we don't want to obsolete the ore -> metals chain
	
	@Override
	public void apply(String id) {
		//market.getDemand(Commodities.CREW).getDemand().modifyFlat(id, RECYCLING_CREW);
		//market.getDemand(Commodities.CREW).getNonConsumingDemand().modifyFlat(id, RECYCLING_CREW * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float mult = getBaseSizeMult();	// * getCrewDemandMet(market);
		//market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, RECYCLING_HEAVY_MACHINERY_DEMAND);
		
		market.getCommodityData(Commodities.VOLATILES).getSupply().modifyFlat(id, RECYCLING_VOLATILES * mult);
		market.getCommodityData(Commodities.ORGANICS).getSupply().modifyFlat(id, RECYCLING_ORGANICS * mult);
		market.getCommodityData(Commodities.METALS).getSupply().modifyFlat(id, RECYCLING_METALS * mult);
		market.getCommodityData(Commodities.RARE_METALS).getSupply().modifyFlat(id, RECYCLING_RARE_METALS * mult);
		//market.getCommodityData(Commodities.SUPPLIES).getSupply().modifyFlat(id, RECYCLING_SUPPLIES * mult);
		//market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().modifyFlat(id, RECYCLING_HEAVY_MACHINERY * mult);
	}
	
	@Override
	public void unapply(String id) {
		//market.getDemand(Commodities.CREW).getDemand().unmodify(id);
		//market.getDemand(Commodities.CREW).getNonConsumingDemand().unmodify(id);
		//market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);
		market.getCommodityData(Commodities.VOLATILES).getSupply().unmodify(id);
		market.getCommodityData(Commodities.ORGANICS).getSupply().unmodify(id);
		market.getCommodityData(Commodities.METALS).getSupply().unmodify(id);
		market.getCommodityData(Commodities.RARE_METALS).getSupply().unmodify(id);
		//market.getCommodityData(Commodities.SUPPLIES).getSupply().unmodify(id);
		//market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().unmodify(id);
	}
}
