package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class Exerelin_Hydroponics extends BaseMarketConditionPlugin {
	
	public static final float HYDROPONICS_CREW = 100f;
	public static final float HYDROPONICS_FOOD = 3000f;
	public static final float HYDROPONICS_HEAVY_MACHINERY = 20f;
	
	public static final float HYDROPONICS_CAPACITY_MULT = 0.3f;	// 1.0 means feeds 100% of typical population demand
	public static final float HYDROPONICS_CREW_POP_MULT = 0.001f * HYDROPONICS_CAPACITY_MULT;
	public static final float HYDROPONICS_FOOD_POP_MULT = 0.1f * HYDROPONICS_CAPACITY_MULT;
	public static final float HYDROPONICS_HEAVY_MACHINERY_POP_MULT = 0.0004f * HYDROPONICS_CAPACITY_MULT;
	// this gives about 2500 food / machinery
	// for reference, arid world is 6000
	
	@Override
	public void apply(String id) {

		// constant
		/*
		market.getDemand(Commodities.REGULAR_CREW).getDemand().modifyFlat(id, HYDROPONICS_CREW);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().modifyFlat(id, HYDROPONICS_CREW * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float crewDemandMet = getCrewDemandMet(market);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, HYDROPONICS_HEAVY_MACHINERY);
		
		market.getCommodityData(Commodities.FOOD).getSupply().modifyFlat(id, HYDROPONICS_FOOD * crewDemandMet);
		*/
		
		// population-based
		float pop = getPopulation(market);
		float crewDemand = HYDROPONICS_CREW_POP_MULT * pop;
		market.getDemand(Commodities.REGULAR_CREW).getDemand().modifyFlat(id, crewDemand);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().modifyFlat(id, crewDemand * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float crewDemandMet = getCrewDemandMet(market);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, HYDROPONICS_HEAVY_MACHINERY_POP_MULT * pop);
		
		market.getCommodityData(Commodities.FOOD).getSupply().modifyFlat(id, HYDROPONICS_FOOD_POP_MULT * pop * crewDemandMet);
	}
	
	@Override
	public void unapply(String id) {
		market.getDemand(Commodities.REGULAR_CREW).getDemand().unmodify(id);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().unmodify(id);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);
		market.getCommodityData(Commodities.FOOD).getSupply().unmodify(id);
	}
}
