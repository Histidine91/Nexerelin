package exerelin.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class SupplyWorkshop extends BaseMarketConditionPlugin {
	
	// for comparison
	/*
	public static final float AUTOFAC_HEAVY_METALS = 1000;
	public static final float AUTOFAC_HEAVY_RARE_METALS = 100;
	public static final float AUTOFAC_HEAVY_VOLATILES = 1000;
	public static final float AUTOFAC_HEAVY_ORGANICS = 1000;
	//public static final float AUTOFAC_HEAVY_CREW = 1000;
	public static final float AUTOFAC_HEAVY_MACHINERY_DEMAND = 200;
	public static final float AUTOFAC_HEAVY_MACHINERY = 1200;
	public static final float AUTOFAC_HEAVY_SUPPLIES = 5000; // 2000
	public static final float AUTOFAC_HEAVY_HAND_WEAPONS = 2000;
	*/
	
	//public static final float WORKSHOP_CREW = 200f;
	public static final float WORKSHOP_VOLATILES = 250f;		// 25%
	public static final float WORKSHOP_ORGANICS = 250f;		// 25%
	public static final float WORKSHOP_METALS = 250f;		// 25%
	public static final float WORKSHOP_RARE_METALS = 25f;	// 25%
	public static final float WORKSHOP_HEAVY_MACHINERY_DEMAND = 40f;	// 25%
	public static final float WORKSHOP_HEAVY_MACHINERY = 0f;
	public static final float WORKSHOP_SUPPLIES = 1000f;		// 20%
	public static final float WORKSHOP_HAND_WEAPONS = 500f;	// 25%
	
	@Override
	public void apply(String id) {
		//market.getDemand(Commodities.CREW).getDemand().modifyFlat(id, WORKSHOP_CREW);
		//market.getDemand(Commodities.CREW).getNonConsumingDemand().modifyFlat(id, WORKSHOP_CREW * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float sizeMult = getBaseSizeMult();
		
		if (market.getSize() >= 4)
			market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, WORKSHOP_HEAVY_MACHINERY_DEMAND * sizeMult);
		market.getDemand(Commodities.VOLATILES).getDemand().modifyFlat(id, WORKSHOP_VOLATILES * sizeMult);
		market.getDemand(Commodities.ORGANICS).getDemand().modifyFlat(id, WORKSHOP_ORGANICS * sizeMult);
		market.getDemand(Commodities.METALS).getDemand().modifyFlat(id, WORKSHOP_METALS * sizeMult);
		market.getDemand(Commodities.RARE_METALS).getDemand().modifyFlat(id, WORKSHOP_RARE_METALS * sizeMult);
		
		float productionMult = getProductionMult(market, Commodities.ORGANICS, Commodities.VOLATILES, 
				Commodities.METALS, Commodities.RARE_METALS) * sizeMult;
		
		market.getCommodityData(Commodities.SUPPLIES).getSupply().modifyFlat(id, WORKSHOP_SUPPLIES * productionMult);
		//market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().modifyFlat(id, WORKSHOP_HEAVY_MACHINERY * productionMult);
		market.getCommodityData(Commodities.HAND_WEAPONS).getSupply().modifyFlat(id, WORKSHOP_HAND_WEAPONS * productionMult);
	}
	
	@Override
	public void unapply(String id) {
		//market.getDemand(Commodities.CREW).getDemand().unmodify(id);
		//market.getDemand(Commodities.CREW).getNonConsumingDemand().unmodify(id);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);
		market.getDemand(Commodities.VOLATILES).getDemand().unmodify(id);
		market.getDemand(Commodities.ORGANICS).getDemand().unmodify(id);
		market.getDemand(Commodities.METALS).getDemand().unmodify(id);
		market.getDemand(Commodities.RARE_METALS).getDemand().unmodify(id);
		market.getCommodityData(Commodities.SUPPLIES).getSupply().unmodify(id);
		//market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().unmodify(id);
		market.getCommodityData(Commodities.HAND_WEAPONS).getSupply().unmodify(id);
	}
}
