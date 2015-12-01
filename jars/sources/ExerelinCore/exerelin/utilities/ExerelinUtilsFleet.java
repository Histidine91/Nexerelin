package exerelin.utilities;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import data.scripts.campaign.SSP_FleetFactory;
import data.scripts.variants.SSP_VariantRandomizer;
import data.scripts.world.SSP_FleetInjector;
import data.scripts.world.SSP_FleetInjector.CommanderType;
import data.scripts.world.SSP_FleetInjector.CrewType;
import data.scripts.world.SSP_FleetInjector.FleetStyle;
import static data.scripts.world.SSP_FleetInjector.getArchetypeWeights;
import static data.scripts.world.SSP_FleetInjector.randomizeVariants;
import org.apache.log4j.Logger;


public class ExerelinUtilsFleet
{
    public enum ExerelinFleetSize
    {
        EXTRA_LARGE,
        LARGE,
        MEDIUM,
        SMALL
    }

    public enum ExerelinFleetType
    {
        ASTEROID_MINING,
        GAS_MINING,
        LOGISTICS,
        BOARDING,
        WAR
    }

    public enum ExerelinVariantType
    {
        MINING,
        FREIGHTER,
        TANKER,
        TROOP_TRANSPORT,
        SUPER_FREIGHTER,
        CARRIER
    }

    public enum ExerelinWarFleetType
    {
        RANDOM,
        CARRIER,
        LARGE_SHIPS,
        FRIGATE,
        MEDIUM_SHIPS
    }
    
    private static int SMALL_FLEET_SUPPLIES_DAY = 10;
    private static int MEDIUM_FLEET_SUPPLIES_DAY = 25;
    private static int LARGE_FLEET_SUPPLIES_DAY = 60;
    private static int EXTRA_LARGE_FLEET_SUPPLIES_DAY = 90;
    
    public static Logger log = Global.getLogger(ExerelinUtilsFleet.class);
   
    /**
     * Used by Starsector Plus to create its custom fleets
     * @param fleet
     * @param market
     * @param stability
     * @param qualityFactor
     * @param type 
     */
    public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {      
        String factionId = fleet.getFaction().getId();
                
        //log.info("Fleet " + fleet.getName() + ": stability " + stability + "; qf " + qualityFactor);
        //float qualityFactorOriginal = qualityFactor;
        //qualityFactor = Math.max(qualityFactor, 0.7f);
        
        SSP_VariantRandomizer.Archetype theme = SSP_FleetInjector.pickTheme(factionId);
        SSP_FleetInjector.setThemeName(fleet, theme);
        
        switch (type)
        {
            case "exerelinInvasionFleet":
            case "exerelinRespawnFleet":
                /*
                if (factionId.equals("luddic_church") && maxFP >= 120)
                    SSP_FleetFactory.createPurificationFleet(fleet, factionId, qualityFactor, maxFP);
                else if (factionId.equals("exigency"))
                    SSP_FleetFactory.createExigencyFleet(fleet, factionId, qualityFactor, maxFP);
                else if (factionId.equals("interstellarimperium"))
                    SSP_FleetFactory.createSiegeFleet(fleet, factionId, qualityFactor, maxFP);
                else if (factionId.equals("templars"))
                    SSP_FleetFactory.createTemplarFleet(fleet, factionId, qualityFactor, maxFP);
                else
                    SSP_FleetFactory.createGenericFleet(fleet, factionId, qualityFactor, maxFP);
                */
                
                SSP_FleetInjector.levelFleet(fleet, CrewType.MILITARY, factionId);
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.ELITE, factionId), CommanderType.ELITE);
                break;
            case "exerelinInvasionSupportFleet":
            case "exerelinDefenceFleet":
                SSP_FleetInjector.levelFleet(fleet, CrewType.MILITARY, factionId);
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.MILITARY, factionId), CommanderType.MILITARY);
                break;
            case "exerelinResponseFleet":
                /*
                if (factionId.equals("luddic_church") && maxFP >= 120)
                    SSP_FleetFactory.createPurificationFleet(fleet, factionId, qualityFactor, maxFP);
                else if (factionId.equals("exigency"))
                    SSP_FleetFactory.createExigencyFleet(fleet, factionId, qualityFactor, maxFP);
                else if (factionId.equals("templars"))
                    SSP_FleetFactory.createTemplarFleet(fleet, factionId, qualityFactor, maxFP);
                else
                    SSP_FleetFactory.createGenericFleet(fleet, factionId, qualityFactor, maxFP);
                */
                
                SSP_FleetInjector.levelFleet(fleet, CrewType.MILITARY, factionId);
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.MILITARY, factionId), CommanderType.MILITARY);
                break;  
            case "exerelinMiningFleet":
                SSP_FleetInjector.levelFleet(fleet, CrewType.CIVILIAN, factionId);
                randomizeVariants(fleet, factionId, qualityFactor, theme, getArchetypeWeights(FleetStyle.CIVILIAN, factionId), CommanderType.CIVILIAN);
                break;
        }
        SSP_FleetFactory.finishFleetNonIntrusive(fleet);
    }
    
    public static FleetMemberAPI addMiningShipToFleet(CampaignFleetAPI fleet)
    {
        String variantId = "mining_pod_wing";
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId());
        if (config != null && config.miningVariantsOrWings != null && !config.miningVariantsOrWings.isEmpty()) 
            variantId = (String) ExerelinUtils.getRandomListElement(config.miningVariantsOrWings);
        FleetMemberType type = FleetMemberType.SHIP;
        if (variantId.contains("_wing")) type = FleetMemberType.FIGHTER_WING;
        FleetMemberAPI miner = Global.getFactory().createFleetMember(type, variantId);
        fleet.getFleetData().addFleetMember(miner);
        return miner;
    }

    public static void sortByFleetCost(CampaignFleetAPI fleet)
    {
        // local reference to be sorted
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        // Remove all members from the fleet
        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        // Sort descending by fleet cost so that more expensive ships are first
        Collections.sort(initialFleetMembers, new Comparator<FleetMemberAPI>() {
            @Override
            public int compare(FleetMemberAPI o1, FleetMemberAPI o2) {
                return Float.compare(o2.getFleetPointCost(), o1.getFleetPointCost());
            }
        });

        // Re-add members to fleet from sorted list
        for (FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().addFleetMember(member);
    }

    public static void sortByHullSize(CampaignFleetAPI fleet)
    {
        // local reference to be sorted
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        // Remove all members from the fleet
        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        // Sort descending by hull size so that larger hulls are first
        Collections.sort(initialFleetMembers, new Comparator<FleetMemberAPI>() {
            @Override
            public int compare(FleetMemberAPI o1, FleetMemberAPI o2) {
                return o2.getHullSpec().getHullSize().compareTo(o1.getHullSpec().getHullSize());
            }
        });

        // Re-add members to fleet from sorted list
        for (FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().addFleetMember(member);
    }
}
