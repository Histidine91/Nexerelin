package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class Exerelin_RecyclingPlant extends BaseMarketConditionPlugin {
	
	public static final float RECYCLING_CREW = 200f;
	public static final float RECYCLING_VOLATILES = 1500f;
	public static final float RECYCLING_ORGANICS = 800f;
	public static final float RECYCLING_METALS = 800f;
	public static final float RECYCLING_RARE_METALS = 40f * 2;
	public static final float RECYCLING_SUPPLIES = 500f;
	public static final float RECYCLING_HEAVY_MACHINERY = 50f;	//100f;
	public static final float RECYCLING_HEAVY_MACHINERY_DEMAND = 20f;
	
	public static final float HAX_MULT_07_OV = 25f;
	public static final float HAX_MULT_07_METALS = 4f;
	
	@Override
	public void apply(String id) {
		market.getDemand(Commodities.REGULAR_CREW).getDemand().modifyFlat(id, RECYCLING_CREW);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().modifyFlat(id, RECYCLING_CREW * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float crewDemandMet = getCrewDemandMet(market);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, RECYCLING_HEAVY_MACHINERY_DEMAND);
		
		market.getCommodityData(Commodities.VOLATILES).getSupply().modifyFlat(id, RECYCLING_VOLATILES * HAX_MULT_07_OV * crewDemandMet);
		market.getCommodityData(Commodities.ORGANICS).getSupply().modifyFlat(id, RECYCLING_ORGANICS * HAX_MULT_07_OV * crewDemandMet);
		market.getCommodityData(Commodities.METALS).getSupply().modifyFlat(id, RECYCLING_METALS * HAX_MULT_07_METALS * crewDemandMet);
		market.getCommodityData(Commodities.RARE_METALS).getSupply().modifyFlat(id, RECYCLING_RARE_METALS * HAX_MULT_07_METALS * crewDemandMet);
		market.getCommodityData(Commodities.SUPPLIES).getSupply().modifyFlat(id, RECYCLING_SUPPLIES * crewDemandMet);
		market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().modifyFlat(id, RECYCLING_HEAVY_MACHINERY * crewDemandMet);
	}
	
	@Override
	public void unapply(String id) {
		market.getDemand(Commodities.REGULAR_CREW).getDemand().unmodify(id);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().unmodify(id);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);
		market.getCommodityData(Commodities.VOLATILES).getSupply().unmodify(id);
		market.getCommodityData(Commodities.ORGANICS).getSupply().unmodify(id);
		market.getCommodityData(Commodities.METALS).getSupply().unmodify(id);
		market.getCommodityData(Commodities.RARE_METALS).getSupply().unmodify(id);
		market.getCommodityData(Commodities.SUPPLIES).getSupply().unmodify(id);
		market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().unmodify(id);
	}
}
