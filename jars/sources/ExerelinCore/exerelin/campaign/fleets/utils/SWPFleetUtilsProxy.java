package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import data.scripts.campaign.SWP_FleetFactory;

public class SWPFleetUtilsProxy {
	
	public static CampaignFleetAPI enhancedCreateFleet(FactionAPI faction, FleetParams params, int total) {
		final FleetParams params2 = params;
		return SWP_FleetFactory.enhancedCreateFleet(faction, total, new SWP_FleetFactory.FleetFactoryDelegate() {
			@Override
			public CampaignFleetAPI createFleet() {
				return SWP_FleetFactory.createFleet(params2);
			}
		});
	}
}
