package exerelin.utilities;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import data.scripts.campaign.SSP_FleetFactory;
import data.scripts.campaign.SSP_LevelUpper;
import data.scripts.variants.SSP_FleetRandomizer;
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
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.ELITE, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F));
                
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
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.WAR, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F));
                SSP_FleetFactory.createGenericFleet(fleet, faction, qualityFactor, maxFP);
                injector.levelFleet(fleet, CrewType.MILITARY, FleetStyle.MILITARY, faction);
                break;
            case "exerelinResponseFleet":
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.WAR, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F));
                
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
                injector.levelCommander(fleet.getCommander(), fleet, CommanderType.TRADER, faction, (maxFP + 100.0F) / ((float)Math.random() * 3.0F + 6.0F));
                SSP_FleetFactory.createTradeFleet(fleet, faction, stability, qualityFactorOriginal, maxFP/15, 1, 0, 0);
                
                maxFP *= 1.4;
                float minerFP = maxFP - fleet.getFleetPoints();
                while (minerFP > 0)
                {
                    FleetMemberAPI miner = ExerelinUtilsFleet.addMiningShipToFleet(fleet);
                    //miner.setVariant(SSP_VariantRandomizer.createVariant(miner, faction, fleet.getCommanderStats(), getArchetypeFromRole(null, qualityFactor, null),
                    //                                              qualityFactor, SSP_LevelUpper.getAICommanderOPBonus(fleet.getCommander())), false, true);
                    minerFP = maxFP - fleet.getFleetPoints();
                }
                fleet.updateCounts();
                injector.levelFleet(fleet, CrewType.CIVILIAN, FleetStyle.CIVILIAN, faction);
                break;
        }
    }
    
    public static FleetMemberAPI addMiningShipToFleet(CampaignFleetAPI fleet)
    {
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId());
        String variantId = (String) ExerelinUtils.getRandomListElement(config.miningVariantsOrWings);
        FleetMemberType type = FleetMemberType.SHIP;
        if (variantId.contains("wing")) type = FleetMemberType.FIGHTER_WING;
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

    @Deprecated
    public static String getRandomVariantIdForFactionOfExerelinType(String factionId, ExerelinVariantType variantType)
    {
        return "";
    }

    @Deprecated
    public static String getRandomVariantIdForFactionByHullsize(String factionId, ShipAPI.HullSize hullSize)
    {
        ExerelinFactionConfig exerelinFactionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        switch (hullSize) {
            case FIGHTER:
                //return exerelinFactionConfig.fighterWings.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.fighterWings.size() - 1));
            case FRIGATE:
                //return exerelinFactionConfig.frigateVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.frigateVariants.size() - 1));
            case DESTROYER:
                //return exerelinFactionConfig.destroyerVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.destroyerVariants.size() - 1));
            case CRUISER:
                //return exerelinFactionConfig.cruiserVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.cruiserVariants.size() - 1));
            case CAPITAL_SHIP:
                //return exerelinFactionConfig.capitalVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.capitalVariants.size() - 1));
            default:
                //return exerelinFactionConfig.frigateVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.fighterWings.size() - 1));
        }
        return "";
    }

    public static String getRandomVariantIdForFaction(String factionId, Boolean includeFighters, Boolean includeLargeShips)
    {
        if(includeLargeShips)
            return getRandomVariantIdForFaction(factionId,  includeFighters);
        else
        {
            int rand = 1;

            if(includeFighters)
                rand = ExerelinUtils.getRandomInRange(1, 3);
            else
                rand = ExerelinUtils.getRandomInRange(2, 3);

            ShipAPI.HullSize hullSize = ShipAPI.HullSize.FRIGATE;

            switch(rand)
            {
                case 1:
                    if (ExerelinUtilsFleet.doesFactionHaveVariantOfHullsize(factionId, ShipAPI.HullSize.FIGHTER))
                        hullSize = ShipAPI.HullSize.FIGHTER;
                    break;
                case 2:
                    hullSize = ShipAPI.HullSize.FRIGATE;
                    break;
                case 3:
                    if (ExerelinUtilsFleet.doesFactionHaveVariantOfHullsize(factionId, ShipAPI.HullSize.DESTROYER))
                        hullSize = ShipAPI.HullSize.DESTROYER;
                    break;
            }

            return ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(factionId, hullSize);
        }
    }

    public static String getRandomVariantIdForFaction(String factionId, Boolean includeFighters)
    {
        int rand = 1;

        if(includeFighters)
            rand = ExerelinUtils.getRandomInRange(1, 5);
        else
            rand = ExerelinUtils.getRandomInRange(2, 5);

        ShipAPI.HullSize hullSize = ShipAPI.HullSize.FRIGATE;

        switch(rand){
            case 1:
                if(ExerelinUtilsFleet.doesFactionHaveVariantOfHullsize(factionId, ShipAPI.HullSize.FIGHTER))
                    hullSize = ShipAPI.HullSize.FIGHTER;
                break;
            case 2:
                hullSize = ShipAPI.HullSize.FRIGATE;
                break;
            case 3:
                if(ExerelinUtilsFleet.doesFactionHaveVariantOfHullsize(factionId, ShipAPI.HullSize.DESTROYER))
                    hullSize = ShipAPI.HullSize.DESTROYER;
                break;
            case 4:
                if(ExerelinUtilsFleet.doesFactionHaveVariantOfHullsize(factionId, ShipAPI.HullSize.CRUISER))
                    hullSize = ShipAPI.HullSize.CRUISER;
                break;
            case 5:
                if(ExerelinUtilsFleet.doesFactionHaveVariantOfHullsize(factionId, ShipAPI.HullSize.CAPITAL_SHIP))
                    hullSize = ShipAPI.HullSize.CAPITAL_SHIP;
                break;
        }

        return ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(factionId, hullSize);
    }

    @Deprecated
    public static Boolean doesFactionHaveVariantOfHullsize(String factionId, ShipAPI.HullSize hullSize)
    {
        return true;
    }

    public static CampaignFleetAPI createPirateFleet(String[] factions, int size)
    {
        // Create fleet base
        CampaignFleetAPI fleet = Global.getSector().createFleet("player", "shuttle");

        fleet.setFaction("pirates");

        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();
        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        for(int i = 0; i < size; i++)
        {
            String factionId = (String) ExerelinUtils.getRandomArrayElement(factions);
            String variantId = ExerelinUtilsFleet.getRandomVariantIdForFaction(factionId, false, false);
            FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(newMember);
        }

        return fleet;
    }

    @Deprecated
    public static CampaignFleetAPI createFleetForFaction(String factionId, ExerelinFleetType fleetType, ExerelinFleetSize fleetSize)
    {
        SectorAPI sectorAPI = Global.getSector();
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        // Create fleet base
        CampaignFleetAPI fleet = sectorAPI.createFleet("player", "shuttle");
        fleet.setFaction(factionId);
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        return fleet;
    }

    private static void buildCarrierFleet(CampaignFleetAPI fleet, int maxSuppliesDay)
    {
        float currentSuppliesDay = 0;

        String variantId = "";
        FleetMemberAPI member = null;
        int numFlightDecks = 0;
        int numFighters = 0;

        while(currentSuppliesDay < maxSuppliesDay)
        {
            if(currentSuppliesDay == 0)
            {
                variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.CARRIER);
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                numFlightDecks += member.getNumFlightDecks();
            }
            else
            {
                if(numFlightDecks*3 < numFighters)
                {
                    variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.CARRIER);
                    member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                    numFlightDecks += member.getNumFlightDecks();
                }
                else
                {
                    variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.FIGHTER);
                    member = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, variantId);
                    numFighters++;
                }
            }

            fleet.getFleetData().addFleetMember(member);

            if(fleet.getFleetData().getMembersListCopy().size() == 1)
                fleet.getFleetData().setFlagship(member);

            currentSuppliesDay += member.getStats().getBaseSupplyUsePerDay().getModifiedValue();
        }
    }

    private static void buildLargeShipFleet(CampaignFleetAPI fleet, int maxSuppliesDay)
    {
        float currentSuppliesDay = 0;

        String variantId = "";
        FleetMemberAPI member = null;

        while(currentSuppliesDay < maxSuppliesDay)
        {
            if(ExerelinUtils.getRandomInRange(1, 3) == 1)
                variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.CAPITAL_SHIP);
            else
                variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.CRUISER);

            member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(member);
            currentSuppliesDay += member.getStats().getBaseSupplyUsePerDay().getModifiedValue();
        }
    }

    private static void buildFrigateFleet(CampaignFleetAPI fleet,  int maxSuppliesDay)
    {
        float currentSuppliesDay = 0;

        String variantId = "";
        FleetMemberAPI member = null;

        while(currentSuppliesDay < maxSuppliesDay)
        {
            variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.FRIGATE);

            member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(member);
            currentSuppliesDay += member.getStats().getBaseSupplyUsePerDay().getModifiedValue();
        }
    }

    private static void buildMediumShipFleet(CampaignFleetAPI fleet, int maxSuppliesDay)
    {
        float currentSuppliesDay = 0;

        String variantId = "";
        FleetMemberAPI member = null;

        while(currentSuppliesDay < maxSuppliesDay)
        {
            if(ExerelinUtils.getRandomInRange(1, 3) == 1)
                variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.CRUISER);
            else
                variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.DESTROYER);

            member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(member);
            currentSuppliesDay += member.getStats().getBaseSupplyUsePerDay().getModifiedValue();
        }
    }

    private static void buildRandomFleet(CampaignFleetAPI fleet, int maxSuppliesDay)
    {
        String randomVariantId = "";
        FleetMemberAPI member = null;

        float currentSuppliesDay = 0;

        while(currentSuppliesDay < maxSuppliesDay)
        {
            if(maxSuppliesDay == SMALL_FLEET_SUPPLIES_DAY)
            {
                if(ExerelinUtils.getRandomInRange(1, 3) == 1)
                {
                    randomVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.FIGHTER);
                    member = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, randomVariantId);
                }
                else
                {
                    randomVariantId = ExerelinUtilsFleet.getRandomVariantIdForFaction(fleet.getFaction().getId(), false, false);
                    member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, randomVariantId);
                }
            }
            else
            {
                if(ExerelinUtils.getRandomInRange(1, 5) == 1)
                {
                    randomVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.FIGHTER);
                    member = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, randomVariantId);
                }
                else
                {
                    randomVariantId = ExerelinUtilsFleet.getRandomVariantIdForFaction(fleet.getFaction().getId(), false, true);
                    member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, randomVariantId);
                }
            }

            fleet.getFleetData().addFleetMember(member);

            currentSuppliesDay += member.getStats().getBaseSupplyUsePerDay().getModifiedValue();
        }
    }

    public static void addEscortsToFleet(CampaignFleetAPI fleet,  int numEscorts)
    {
        for(int i = 0; i < numEscorts; i++)
        {
            String randomFrigateVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.FRIGATE);
            FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, randomFrigateVariantId);
            fleet.getFleetData().addFleetMember(newMember);
        }
    }

    public static void addFreightersToFleet(CampaignFleetAPI fleet)
    {
        float targetSupplies = fleet.getLogistics().getLogisticalCost() * 18;
        float maxCapacity = fleet.getCargo().getMaxCapacity();

        // Add super freighter to extra large fleets
        /*if(targetSupplies >= EXTRA_LARGE_FLEET_SUPPLIES_DAY)
        {
            String variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.SUPER_FREIGHTER) ;
            FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(newMember);
        }*/

        // Add extra freighters if needed
        while(maxCapacity < (targetSupplies))
        {
            String variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.FREIGHTER) ;
            FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(newMember);

            maxCapacity += newMember.getCargoCapacity();
        }

    }

    public static void addCapitalShipToFleet(CampaignFleetAPI fleet)
    {
        String randomCapitalVariantId;

        if(ExerelinUtilsFleet.doesFactionHaveVariantOfHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.CAPITAL_SHIP))
            randomCapitalVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.CAPITAL_SHIP);
        else
            randomCapitalVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(fleet.getFaction().getId(), ShipAPI.HullSize.CRUISER);

        FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, randomCapitalVariantId);
        fleet.getFleetData().addFleetMember(newMember);
    }

    public static void resetFleetCargoToDefaults(CampaignFleetAPI fleet, float extraCrewPercent, float marinesPercent, CargoAPI.CrewXPLevel crewXPLevel)
    {
        CargoAPI fleetCargo = fleet.getCargo();
        List members = fleet.getFleetData().getMembersListCopy();
        fleetCargo.clear();
        for(int i = 0; i < members.size(); i = i + 1)
        {
            FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
            fleetCargo.addCrew(crewXPLevel, (int) fmAPI.getMinCrew() + (int) ((fmAPI.getMaxCrew() - fmAPI.getMinCrew()) * extraCrewPercent));
            fleetCargo.addMarines((int) ((fmAPI.getMaxCrew() - fmAPI.getMinCrew()) * marinesPercent));
            fleetCargo.addFuel(fmAPI.getFuelCapacity());
            fleetCargo.addSupplies(fmAPI.getCargoCapacity());
        }
    }
}
