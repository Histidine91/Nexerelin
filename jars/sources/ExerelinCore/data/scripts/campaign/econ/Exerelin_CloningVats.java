package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

@Deprecated
public class Exerelin_CloningVats extends BaseMarketConditionPlugin {
    
	public static final float CLONINGVATS_CREW = 200f;	// green
	public static final float CLONINGVATS_CREW_DEMAND = 20f;
	public static final float CLONINGVATS_VOLATILES = 200f;
	public static final float CLONINGVATS_ORGANICS = 100f;
	public static final float CLONINGVATS_ORGANS = 25f;
	//public static final float CLONINGVATS_FOOD = 50f;	// soylent green is made of people
	public static final float CLONINGVATS_FOOD_DEMAND = 50f;
	//public static final float CLONINGVATS_HEAVY_MACHINERY = 5f;
	
    @Override
    public void apply(String id) {
        market.getDemand(Commodities.CREW).getDemand().modifyFlat(id, CLONINGVATS_CREW_DEMAND);
		market.getDemand(Commodities.CREW).getNonConsumingDemand().modifyFlat(id, CLONINGVATS_CREW_DEMAND * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float crewDemandMet = market.getDemand(Commodities.CREW).getClampedFractionMet();
		market.getDemand(Commodities.VOLATILES).getDemand().modifyFlat(id, CLONINGVATS_VOLATILES);
		market.getDemand(Commodities.ORGANICS).getDemand().modifyFlat(id, CLONINGVATS_ORGANICS);
		//market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, CLONINGVATS_HEAVY_MACHINERY);
		market.getDemand(Commodities.FOOD).getDemand().modifyFlat(id, CLONINGVATS_FOOD_DEMAND);
		
        market.getCommodityData(Commodities.CREW).getSupply().modifyFlat(id, CLONINGVATS_CREW * crewDemandMet);
		market.getCommodityData(Commodities.ORGANS).getSupply().modifyFlat(id, CLONINGVATS_ORGANS * crewDemandMet);
		//market.getCommodityData(Commodities.FOOD).getSupply().modifyFlat(id, CLONINGVATS_FOOD * crewDemandMet);
    }
    
    @Override
    public void unapply(String id) {
        market.getDemand(Commodities.CREW).getDemand().unmodify(id);
        market.getDemand(Commodities.CREW).getNonConsumingDemand().unmodify(id);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);
		market.getDemand(Commodities.VOLATILES).getDemand().unmodify(id);
		market.getDemand(Commodities.ORGANICS).getDemand().unmodify(id);
		market.getDemand(Commodities.FOOD).getDemand().unmodify(id);
		
		market.getCommodityData(Commodities.CREW).getSupply().unmodify(id);
		market.getCommodityData(Commodities.ORGANS).getSupply().unmodify(id);
		//market.getCommodityData(Commodities.FOOD).getSupply().unmodify(id);
    }
}
