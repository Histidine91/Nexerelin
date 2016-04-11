package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class Exerelin_SupplyWorkshop extends BaseMarketConditionPlugin {
	
	// for comparison
	/*
	public static final float AUTOFAC_HEAVY_METALS = 20000;
	public static final float AUTOFAC_HEAVY_RARE_METALS = 1000;
	public static final float AUTOFAC_HEAVY_VOLATILES = 1000;
	public static final float AUTOFAC_HEAVY_ORGANICS = 10000;
	public static final float AUTOFAC_HEAVY_CREW = 1000;
	public static final float AUTOFAC_HEAVY_MACHINERY_DEMAND = 50;
	public static final float AUTOFAC_HEAVY_MACHINERY = 5000;
	public static final float AUTOFAC_HEAVY_SUPPLIES = 12000;
	public static final float AUTOFAC_HEAVY_HAND_WEAPONS = 40000;
	*/
	
	public static final float WORKSHOP_CREW = 200f;
	public static final float WORKSHOP_VOLATILES = 320f;
	public static final float WORKSHOP_ORGANICS = 2500f;
	public static final float WORKSHOP_METALS = 1500f;
	public static final float WORKSHOP_RARE_METALS = 75f;
	public static final float WORKSHOP_HEAVY_MACHINERY_DEMAND = 20f;
	public static final float WORKSHOP_HEAVY_MACHINERY = 0f;
	public static final float WORKSHOP_SUPPLIES = 2500f;
	public static final float WORKSHOP_HAND_WEAPONS = 2000f;	// mostly here just so the system doesn't fail if there aren't enough autofacs
	
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
		
		float productionMult = getProductionMult(market, Commodities.ORGANICS, Commodities.VOLATILES, Commodities.METALS, Commodities.RARE_METALS) * crewDemandMet;
		
		market.getCommodityData(Commodities.SUPPLIES).getSupply().modifyFlat(id, WORKSHOP_SUPPLIES * productionMult);
		market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().modifyFlat(id, WORKSHOP_HEAVY_MACHINERY * productionMult);
		market.getCommodityData(Commodities.HAND_WEAPONS).getSupply().modifyFlat(id, WORKSHOP_HAND_WEAPONS * productionMult);
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
		market.getCommodityData(Commodities.HAND_WEAPONS).getSupply().unmodify(id);
	}
}
