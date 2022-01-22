package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;

@Deprecated
public class DSFleetUtilsProxy {
	
	@Deprecated
	public static CampaignFleetAPI enhancedCreateFleet(FactionAPI faction, FleetParamsV3 params, int total) {
		/*
		final FleetParamsV3 params2 = params;
		return DS_FleetFactory.enhancedCreateFleet(faction, total, new DS_FleetFactory.FleetFactoryDelegate() {
			@Override
			public CampaignFleetAPI createFleet() {
				return FleetFactoryV3.createFleet(params2);
			}
		});
		*/
		return FleetFactoryV3.createFleet(params);
	}
	
	/*
	@Deprecated
	public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {
		String factionId = fleet.getFaction().getId();
		MemoryAPI memory = fleet.getMemoryWithoutUpdate();
		Random r;
		if (memory.contains(DS_Defs.MEMORY_KEY_RANDOM_SEED)) {
			long seed = memory.getLong(DS_Defs.MEMORY_KEY_RANDOM_SEED);
			r = new Random(seed);
		} else {
			r = new Random();
		}

		DS_Defs.Archetype theme = DS_FleetInjector.pickTheme(factionId, r);
		DS_Util.setThemeName(fleet, theme);
		List<String> extendedTheme = DS_Util.pickExtendedTheme(factionId, market, r);
		DS_Util.setExtendedThemeName(fleet, extendedTheme);

		switch (type)
		{
			case "exerelinInvasionFleet":
			case "exerelinRespawnFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.ELITE, factionId), false, r);
				break;
			case "exerelinInvasionSupportFleet":
			case "exerelinDefenseFleet":
			case "nex_suppressionFleet":
			case "nex_satBombFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId), false, r);
				break;
			case "exerelinResponseFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId), false, r);
				break;  
			case "exerelinMiningFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.CIVILIAN, factionId), true, r);
				break;
			case "vengeanceFleet":
				randomizeVariants(fleet, factionId, extendedTheme, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.ELITE, factionId), false, r);
				break;
				
			default:	// fallback taken from SS+
				randomizeVariants(fleet, factionId, null, qualityFactor, 0f, theme, 
						getArchetypeWeights(DS_Defs.FleetStyle.STANDARD, factionId), false, r);
		}
		DS_FleetFactory.finishFleetNonIntrusive(fleet, factionId, false, r);
	}
	*/
}
