package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class Exerelin_Hydroponics extends BaseMarketConditionPlugin {
	
	public static final float HYDROPONICS_CREW = 100f;
	public static final float HYDROPONICS_FOOD = 5000f;
	public static final float HYDROPONICS_HEAVY_MACHINERY = 10f;
	
	@Override
	public void apply(String id) {
		market.getDemand(Commodities.REGULAR_CREW).getDemand().modifyFlat(id, HYDROPONICS_CREW);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().modifyFlat(id, HYDROPONICS_CREW * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION );
		float crewDemandMet = getCrewDemandMet(market);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().modifyFlat(id, HYDROPONICS_HEAVY_MACHINERY);
		
		market.getCommodityData(Commodities.FOOD).getSupply().modifyFlat(id, HYDROPONICS_FOOD * crewDemandMet);
	}
	
	@Override
	public void unapply(String id) {
		market.getDemand(Commodities.REGULAR_CREW).getDemand().unmodify(id);
		market.getDemand(Commodities.REGULAR_CREW).getNonConsumingDemand().unmodify(id);
		market.getDemand(Commodities.HEAVY_MACHINERY).getDemand().unmodify(id);
		market.getCommodityData(Commodities.FOOD).getSupply().unmodify(id);
	}
}
