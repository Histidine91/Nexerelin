package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import data.scripts.campaign.DS_FleetFactory;
import data.scripts.campaign.fleets.DS_FleetInjector;
import static data.scripts.campaign.fleets.DS_FleetInjector.randomizeVariants;
import data.scripts.util.DS_Defs;
import static data.scripts.util.DS_Util.getArchetypeWeights;

public class DSFleetUtilsProxy {
	
	public static CampaignFleetAPI enhancedCreateFleet(FactionAPI faction, FleetParams params, int total) {
		final FleetParams params2 = params;
		return DS_FleetFactory.enhancedCreateFleet(faction, total, new DS_FleetFactory.FleetFactoryDelegate() {
			@Override
			public CampaignFleetAPI createFleet() {
				return FleetFactoryV2.createFleet(params2);
			}
		});
	}
	
	public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {
		String factionId = fleet.getFaction().getId();

		DS_Defs.Archetype theme = DS_FleetInjector.pickTheme(factionId);
		DS_FleetInjector.setThemeName(fleet, theme);

		switch (type)
		{
			case "exerelinInvasionFleet":
			case "exerelinRespawnFleet":
				randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.ELITE, factionId));
				break;
			case "exerelinInvasionSupportFleet":
			case "exerelinDefenceFleet":
				randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId));
				break;
			case "exerelinResponseFleet":
				randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId));
				break;  
			case "exerelinMiningFleet":
				randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.CIVILIAN, factionId));
				break;
			default:    // fallback taken from SS+
				randomizeVariants(fleet, factionId, qualityFactor, null, getArchetypeWeights(DS_Defs.FleetStyle.STANDARD, factionId));
		}
		DS_FleetFactory.finishFleetNonIntrusive(fleet, factionId, false);
	}
}
