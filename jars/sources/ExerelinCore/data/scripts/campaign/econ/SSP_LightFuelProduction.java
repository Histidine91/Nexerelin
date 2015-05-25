package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

@Deprecated
public class SSP_LightFuelProduction extends BaseMarketConditionPlugin {

    public static final float FUEL_PRODUCTION_CREW = 100f;
    public static final float FUEL_PRODUCTION_FUEL = 3000f;
    public static final float FUEL_PRODUCTION_MACHINERY = 10f;
    public static final float FUEL_PRODUCTION_ORGANICS = 2000f;
    public static final float FUEL_PRODUCTION_RARE_METALS = 200f;
    public static final float FUEL_PRODUCTION_VOLATILES = 2000f;

    @Override
    public void apply(String id) {
        market.getDemand(Commodities.REGULAR_CREW).getDemand().modifyFlat(id, FUEL_PRODUCTION_CREW);
        market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().modifyFlat(id, FUEL_PRODUCTION_CREW
                                                                                                  * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);
        float crewDemandMet = getCrewDemandMet(market);

        market.getDemand(Commodities.ORGANICS).getDemand().modifyFlat(id, FUEL_PRODUCTION_ORGANICS * crewDemandMet);
        market.getDemand(Commodities.VOLATILES).getDemand().modifyFlat(id, FUEL_PRODUCTION_VOLATILES * crewDemandMet);
        market.getDemand(Commodities.RARE_METALS).getDemand().modifyFlat(id, FUEL_PRODUCTION_RARE_METALS * crewDemandMet);
        market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, FUEL_PRODUCTION_MACHINERY);

	String[] inputs = {Commodities.ORGANICS, Commodities.VOLATILES, Commodities.RARE_METALS};
        float productionMult = getProductionMult(market, inputs) * crewDemandMet;
        market.getCommodityData(Commodities.FUEL).getSupply().modifyFlat(id, FUEL_PRODUCTION_FUEL * productionMult);
    }

    @Override
    public void unapply(String id) {
        market.getDemand(Commodities.ORGANICS).getDemand().unmodify(id);
        market.getDemand(Commodities.VOLATILES).getDemand().unmodify(id);
        market.getDemand(Commodities.RARE_METALS).getDemand().unmodify(id);
        market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);

        market.getDemand(Commodities.REGULAR_CREW).getDemand().unmodify(id);
        market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().unmodify(id);

        market.getCommodityData(Commodities.FUEL).getSupply().unmodify(id);
    }

}
