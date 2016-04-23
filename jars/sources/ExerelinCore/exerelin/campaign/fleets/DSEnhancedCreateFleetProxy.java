package exerelin.campaign.fleets;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import data.scripts.campaign.DS_FleetFactory;

public class DSEnhancedCreateFleetProxy {
	
	public static CampaignFleetAPI enhancedCreateFleet(FactionAPI faction, FleetParams params, int total) {
		final FleetParams params2 = params;
		return DS_FleetFactory.enhancedCreateFleet(faction, total, new DS_FleetFactory.FleetFactoryDelegate() {
			@Override
			public CampaignFleetAPI createFleet() {
				return DS_FleetFactory.createFleet(params2);
			}
		});
	}
}
