package exerelin.campaign.fleets;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.loading.FleetCompositionDoctrineAPI;
import data.scripts.campaign.fleets.SSP_FleetInjector;
import static data.scripts.campaign.fleets.SSP_FleetInjector.getArchetypeWeights;
import static data.scripts.campaign.fleets.SSP_FleetInjector.randomizeVariants;
import data.scripts.variants.SSP_VariantRandomizer;

public class SSPFleetUtilsProxy {
	
	public static CampaignFleetAPI enhancedCreateFleet(FactionAPI faction, FleetParams params, int total) 
	{
		FleetCompositionDoctrineAPI doctrine = faction.getCompositionDoctrine();
		float preInterceptors = doctrine.getInterceptors();
		float preFighters = doctrine.getFighters();
		float preBombers = doctrine.getBombers();
		float preSmall = doctrine.getSmall();
		float preFast = doctrine.getFast();
		float preMedium = doctrine.getMedium();
		float preLarge = doctrine.getLarge();
		float preCapital = doctrine.getCapital();
		float preSmallCarrierProbability = doctrine.getSmallCarrierProbability();
		float preMediumCarrierProbability = doctrine.getMediumCarrierProbability();
		float preLargeCarrierProbability = doctrine.getLargeCarrierProbability();

		if (total > 25 && total <= 50) {
			doctrine.setInterceptors(preInterceptors * 0.5f);
			doctrine.setFighters(preFighters * 0.5f);
			doctrine.setBombers(preBombers * 0.5f);
			doctrine.setSmall(preSmall * 0.5f);
			doctrine.setFast(preFast * 0.5f);
			doctrine.setMedium(preMedium);
			doctrine.setLarge(preLarge * 1.25f);
			doctrine.setCapital(preCapital * 1.5f);
			doctrine.setSmallCarrierProbability(preSmallCarrierProbability * 0.8f);
			doctrine.setMediumCarrierProbability(preMediumCarrierProbability * 0.9f);
			doctrine.setLargeCarrierProbability(preLargeCarrierProbability);
		} else if (total > 50) {
			doctrine.setInterceptors(preInterceptors * 0.25f);
			doctrine.setFighters(preFighters * 0.25f);
			doctrine.setBombers(preBombers * 0.25f);
			doctrine.setSmall(preSmall * 0.25f);
			doctrine.setFast(preFast * 0.25f);
			doctrine.setMedium(preMedium * 0.75f);
			doctrine.setLarge(preLarge);
			doctrine.setCapital(preCapital * 1.25f);
			doctrine.setSmallCarrierProbability(preSmallCarrierProbability * 0.5f);
			doctrine.setMediumCarrierProbability(preMediumCarrierProbability * 0.65f);
			doctrine.setLargeCarrierProbability(preLargeCarrierProbability * 0.8f);
		}
		CampaignFleetAPI fleet = FleetFactoryV2.createFleet(params);

		doctrine.setInterceptors(preInterceptors);
		doctrine.setFighters(preFighters);
		doctrine.setBombers(preBombers);
		doctrine.setSmall(preSmall);
		doctrine.setFast(preFast);
		doctrine.setMedium(preMedium);
		doctrine.setLarge(preLarge);
		doctrine.setCapital(preCapital);
		doctrine.setSmallCarrierProbability(preSmallCarrierProbability);
		doctrine.setMediumCarrierProbability(preMediumCarrierProbability);
		doctrine.setLargeCarrierProbability(preLargeCarrierProbability);

		return fleet;
	}
	
	public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {
		String factionId = fleet.getFaction().getId();
        
        SSP_VariantRandomizer.Archetype theme = SSP_FleetInjector.pickTheme(factionId);
        SSP_FleetInjector.setThemeName(fleet, theme);
        
        switch (type)
        {
            case "exerelinInvasionFleet":
            case "exerelinRespawnFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(SSP_FleetInjector.FleetStyle.ELITE, factionId), SSP_FleetInjector.CommanderType.ELITE);
                SSP_FleetInjector.levelFleet(fleet, SSP_FleetInjector.CrewType.MILITARY, factionId, SSP_FleetInjector.CommanderType.ELITE);
                break;
            case "exerelinInvasionSupportFleet":
            case "exerelinDefenceFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(SSP_FleetInjector.FleetStyle.MILITARY, factionId), SSP_FleetInjector.CommanderType.MILITARY);
                SSP_FleetInjector.levelFleet(fleet, SSP_FleetInjector.CrewType.MILITARY, factionId, SSP_FleetInjector.CommanderType.MILITARY);
                break;
            case "exerelinResponseFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(SSP_FleetInjector.FleetStyle.MILITARY, factionId), SSP_FleetInjector.CommanderType.MILITARY);
                SSP_FleetInjector.levelFleet(fleet, SSP_FleetInjector.CrewType.MILITARY, factionId, SSP_FleetInjector.CommanderType.ELITE);
                break;  
            case "exerelinMiningFleet":
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(SSP_FleetInjector.FleetStyle.CIVILIAN, factionId), SSP_FleetInjector.CommanderType.CIVILIAN);
                SSP_FleetInjector.levelFleet(fleet, SSP_FleetInjector.CrewType.CIVILIAN, factionId, SSP_FleetInjector.CommanderType.CIVILIAN);
                break;
            default:    // fallback taken from SS+
                randomizeVariants(fleet, factionId, qualityFactor, null, getArchetypeWeights(SSP_FleetInjector.FleetStyle.STANDARD, factionId), SSP_FleetInjector.CommanderType.STANDARD);
                SSP_FleetInjector.levelFleet(fleet, SSP_FleetInjector.CrewType.STANDARD, factionId, SSP_FleetInjector.CommanderType.STANDARD);
        }
        //SSP_FleetFactory.finishFleetNonIntrusive(fleet, factionId);
	}
}
