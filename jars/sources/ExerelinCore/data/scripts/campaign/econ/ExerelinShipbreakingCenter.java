package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.econ.ShipbreakingCenter;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class ExerelinShipbreakingCenter extends ShipbreakingCenter {
	public static final float EXTRA_MACHINERY_MULT = 2.5f;
	public static final float EXTRA_METALS_MULT = 2f;
	
	public void apply(String id) {
		super.apply(id);
		market.getCommodityData(Commodities.HEAVY_MACHINERY).getSupply().modifyFlat(id, ConditionData.SHIPBREAKING_MACHINERY * EXTRA_MACHINERY_MULT);
		market.getCommodityData(Commodities.METALS).getSupply().modifyFlat(id, ConditionData.SHIPBREAKING_METALS * EXTRA_METALS_MULT);
		market.getCommodityData(Commodities.RARE_METALS).getSupply().modifyFlat(id, ConditionData.SHIPBREAKING_RARE_METALS * EXTRA_METALS_MULT);
	}
	
}