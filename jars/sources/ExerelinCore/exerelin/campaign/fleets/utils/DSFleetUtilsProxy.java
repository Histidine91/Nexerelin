package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
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
				return DS_FleetFactory.createFleet(params2);
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
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.ELITE, factionId), DS_Defs.CommanderType.ELITE);
                DS_FleetInjector.levelFleet(fleet, DS_Defs.CrewType.MILITARY, factionId, DS_Defs.CommanderType.ELITE);
                break;
            case "exerelinInvasionSupportFleet":
            case "exerelinDefenceFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId), DS_Defs.CommanderType.MILITARY);
                DS_FleetInjector.levelFleet(fleet, DS_Defs.CrewType.MILITARY, factionId, DS_Defs.CommanderType.MILITARY);
                break;
            case "exerelinResponseFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.MILITARY, factionId), DS_Defs.CommanderType.MILITARY);
                DS_FleetInjector.levelFleet(fleet, DS_Defs.CrewType.MILITARY, factionId, DS_Defs.CommanderType.ELITE);
                break;  
            case "exerelinMiningFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(DS_Defs.FleetStyle.CIVILIAN, factionId), DS_Defs.CommanderType.CIVILIAN);
                DS_FleetInjector.levelFleet(fleet, DS_Defs.CrewType.CIVILIAN, factionId, DS_Defs.CommanderType.CIVILIAN);
                break;
            default:    // fallback taken from SS+
                randomizeVariants(fleet, factionId, qualityFactor, null, getArchetypeWeights(DS_Defs.FleetStyle.STANDARD, factionId), DS_Defs.CommanderType.STANDARD);
                DS_FleetInjector.levelFleet(fleet, DS_Defs.CrewType.STANDARD, factionId, DS_Defs.CommanderType.STANDARD);
        }
        DS_FleetFactory.finishFleetNonIntrusive(fleet, factionId);
	}
}
