package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class Exerelin_SupplyWorkshop extends BaseMarketConditionPlugin {
	
	public static final float WORKSHOP_CREW = 400f;
	public static final float WORKSHOP_VOLATILES = 1000f;
	public static final float WORKSHOP_ORGANICS = 5000f;
	public static final float WORKSHOP_METALS = 2000f;
	public static final float WORKSHOP_RARE_METALS = 100f;
	public static final float WORKSHOP_HEAVY_MACHINERY_DEMAND = 25f;
	public static final float WORKSHOP_HEAVY_MACHINERY = 500f;
	public static final float WORKSHOP_SUPPLIES = 4000f;
	
	@Override
	public void apply(String id) {
		market.getDemand(Commodities.REGULAR_CREW).getDemand().modifyFlat(id, WORKSHOP_CREW);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().modifyFlat(id, WORKSHOP_CREW * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float crewDemandMet = getCrewDemandMet(market);
		
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, WORKSHOP_HEAVY_MACHINERY_DEMAND);
		market.getDemand(Commodities.VOLATILES).getDemand().modifyFlat(id, WORKSHOP_VOLATILES);
		market.getDemand(Commodities.ORGANICS).getDemand().modifyFlat(id, WORKSHOP_ORGANICS);
		market.getDemand(Commodities.METALS).getDemand().modifyFlat(id, WORKSHOP_METALS);
		market.getDemand(Commodities.RARE_METALS).getDemand().modifyFlat(id, WORKSHOP_RARE_METALS);
		
		market.getCommodityData(Commodities.SUPPLIES).getSupply().modifyFlat(id, WORKSHOP_SUPPLIES * crewDemandMet);
		market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().modifyFlat(id, WORKSHOP_HEAVY_MACHINERY * crewDemandMet);
	}
	
	@Override
	public void unapply(String id) {
		market.getDemand(Commodities.REGULAR_CREW).getDemand().unmodify(id);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().unmodify(id);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);
		market.getDemand(Commodities.VOLATILES).getDemand().unmodify(id);
		market.getDemand(Commodities.ORGANICS).getDemand().unmodify(id);
		market.getDemand(Commodities.METALS).getDemand().unmodify(id);
		market.getDemand(Commodities.RARE_METALS).getDemand().unmodify(id);
		market.getCommodityData(Commodities.SUPPLIES).getSupply().unmodify(id);
		market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().unmodify(id);
	}
}
