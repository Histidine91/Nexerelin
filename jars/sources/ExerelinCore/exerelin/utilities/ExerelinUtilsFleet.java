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
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import data.scripts.campaign.SSP_FleetFactory;
import data.scripts.campaign.SSP_LevelUpper;
import static data.scripts.variants.SSP_FleetRandomizer.getArchetypeFromRole;
import data.scripts.variants.SSP_VariantRandomizer;
import data.scripts.world.SSP_FleetInjector;
import data.scripts.world.SSP_FleetInjector.CommanderType;
import data.scripts.world.SSP_FleetInjector.CrewType;
import data.scripts.world.SSP_FleetInjector.FleetStyle;
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
        String faction = fleet.getFaction().getId();
        MemoryAPI memory = fleet.getMemoryWithoutUpdate();
        int maxFP = memory.contains("$maxFP") ? ((Integer)memory.get("$maxFP")).intValue() : fleet.getFleetPoints();
        
        SSP_FleetInjector injector = SSP_FleetInjector.getInjector();
        if (injector == null) 
        {
            log.error("Missing SS+ injector");
            return;
        }
        
        log.info("Fleet " + fleet.getName() + ": stability " + stability + "; qf " + qualityFactor);
        float qualityFactorOriginal = qualityFactor;
        qualityFactor = Math.max(qualityFactor, 0.7f);

        switch (type)
        {
            case "exerelinInvasionFleet":
            case "exerelinRespawnFleet":
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.ELITE, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F), true);
                
                if (faction.equals("luddic_church") && maxFP >= 120)
                    SSP_FleetFactory.createPurificationFleet(fleet, faction, qualityFactor, maxFP);
                else if (faction.equals("exigency"))
                    SSP_FleetFactory.createExigencyFleet(fleet, faction, qualityFactor, maxFP);
                else if (faction.equals("interstellarimperium"))
                    SSP_FleetFactory.createSiegeFleet(fleet, faction, qualityFactor, maxFP);
                else if (faction.equals("templars"))
                    SSP_FleetFactory.createTemplarFleet(fleet, faction, qualityFactor, maxFP);
                else
                    SSP_FleetFactory.createGenericFleet(fleet, faction, qualityFactor, maxFP);
                
                int numMarines = fleet.getCargo().getMarines();
                log.info("Invasion fleet " + fleet.getNameWithFaction() + " has " + numMarines + " marines");
                for (int i=0; i<numMarines; i=i+100)
                {
                    fleet.getFaction().pickShipAndAddToFleet(ShipRoles.PERSONNEL_MEDIUM, qualityFactor, fleet);
                    //SSP_FleetRandomizer.addRandomVariantToFleet(fleet, faction, ShipRoles.PERSONNEL_MEDIUM, qualityFactor);
                }
                fleet.updateCounts();
                
                injector.levelFleet(fleet, CrewType.ELITE, FleetStyle.ELITE, faction);
                break;
            case "exerelinInvasionSupportFleet":
            case "exerelinDefenceFleet":
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.WAR, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F), true);
                SSP_FleetFactory.createGenericFleet(fleet, faction, qualityFactor, maxFP);
                injector.levelFleet(fleet, CrewType.MILITARY, FleetStyle.MILITARY, faction);
                break;
            case "exerelinResponseFleet":
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.WAR, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F), true);
                
                if (faction.equals("luddic_church") && maxFP >= 120)
                    SSP_FleetFactory.createPurificationFleet(fleet, faction, qualityFactor, maxFP);
                else if (faction.equals("exigency"))
                    SSP_FleetFactory.createExigencyFleet(fleet, faction, qualityFactor, maxFP);
                else if (faction.equals("templars"))
                    SSP_FleetFactory.createTemplarFleet(fleet, faction, qualityFactor, maxFP);
                else
                    SSP_FleetFactory.createGenericFleet(fleet, faction, qualityFactor, maxFP);
                
                injector.levelFleet(fleet, CrewType.MILITARY, FleetStyle.MILITARY, faction);
                break;  
            case "exerelinMiningFleet":
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.TRADER, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F), true);
                SSP_FleetFactory.createTradeFleet(fleet, faction, stability, qualityFactorOriginal, maxFP/15, 1, 0, 0);
                
                maxFP *= 1.4;
                float minerFP = maxFP - fleet.getFleetPoints();
                while (minerFP > 0)
                {
                    FleetMemberAPI miner = ExerelinUtilsFleet.addMiningShipToFleet(fleet);
                    //miner.setVariant(SSP_VariantRandomizer.createVariant(miner, faction, fleet.getCommanderStats(), getArchetypeFromRole(null, qualityFactor, null),
                    //                                              qualityFactor, SSP_LevelUpper.getAICommanderOPBonus(fleet.getCommander())), false, true);
                    minerFP -= miner.getFleetPointCost();
                }
                fleet.updateCounts();
                injector.levelFleet(fleet, CrewType.CIVILIAN, FleetStyle.CIVILIAN, faction);
                break;
        }
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
