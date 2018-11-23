package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import data.scripts.campaign.SWP_FleetFactory;

public class SWPFleetUtilsProxy {
	
	public static CampaignFleetAPI enhancedCreateFleet(FactionAPI faction, FleetParamsV3 params, int total) {
		final FleetParamsV3 params2 = params;
		return SWP_FleetFactory.enhancedCreateFleet(faction, total, new SWP_FleetFactory.FleetFactoryDelegate() {
			@Override
			public CampaignFleetAPI createFleet() {
				return FleetFactoryV3.createFleet(params2);
			}
		});
	}
}
