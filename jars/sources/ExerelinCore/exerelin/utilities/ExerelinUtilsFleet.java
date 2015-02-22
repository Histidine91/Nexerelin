package exerelin.utilities;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.*;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;


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

    public static String getRandomVariantIdForFactionOfExerelinType(String factionId, ExerelinVariantType variantType)
    {
        ExerelinFactionConfig exerelinFactionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        if(exerelinFactionConfig == null)
        {
            System.out.println("EXERELIN ERROR: Couldn't get random variant for: " + factionId);
            return "";
        }

        switch (variantType) {
            case MINING:
                return exerelinFactionConfig.miningVariantsOrWings.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.miningVariantsOrWings.size() - 1));
            case FREIGHTER:
                return exerelinFactionConfig.freighterVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.freighterVariants.size() - 1));
            case TANKER:
                return exerelinFactionConfig.tankerVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.tankerVariants.size() - 1));
            case TROOP_TRANSPORT:
                return exerelinFactionConfig.troopTransportVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.troopTransportVariants.size() - 1));
            case SUPER_FREIGHTER:
                return exerelinFactionConfig.superFreighterVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.superFreighterVariants.size() - 1));
            case CARRIER:
                return exerelinFactionConfig.carrierVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.carrierVariants.size() - 1));
            default:
                return exerelinFactionConfig.miningVariantsOrWings.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.miningVariantsOrWings.size() - 1));
        }
    }

    public static String getRandomVariantIdForFactionByHullsize(String factionId, ShipAPI.HullSize hullSize)
    {
        ExerelinFactionConfig exerelinFactionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        switch (hullSize) {
            case FIGHTER:
                return exerelinFactionConfig.fighterWings.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.fighterWings.size() - 1));
            case FRIGATE:
                return exerelinFactionConfig.frigateVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.frigateVariants.size() - 1));
            case DESTROYER:
                return exerelinFactionConfig.destroyerVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.destroyerVariants.size() - 1));
            case CRUISER:
                return exerelinFactionConfig.cruiserVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.cruiserVariants.size() - 1));
            case CAPITAL_SHIP:
                return exerelinFactionConfig.capitalVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.capitalVariants.size() - 1));
            default:
                return exerelinFactionConfig.frigateVariants.get(ExerelinUtils.getRandomInRange(0, exerelinFactionConfig.fighterWings.size() - 1));
        }
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

    // TODO: DOESN'T SEEM TO WORK WHEN LISTS ARE EMPTY
    public static Boolean doesFactionHaveVariantOfHullsize(String factionId, ShipAPI.HullSize hullSize)
    {
        ExerelinFactionConfig exerelinFactionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        switch (hullSize) {
            case FIGHTER:
                return exerelinFactionConfig.fighterWings.size() > 0;
            case FRIGATE:
                return exerelinFactionConfig.frigateVariants.size() > 0;
            case DESTROYER:
                return exerelinFactionConfig.destroyerVariants.size() > 0;
            case CRUISER:
                return exerelinFactionConfig.cruiserVariants.size() > 0;
            case CAPITAL_SHIP:
                return exerelinFactionConfig.capitalVariants.size() > 0;
            default:
                return exerelinFactionConfig.frigateVariants.size() > 0;
        }
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
            String factionId = factions[ExerelinUtils.getRandomInRange(0, factions.length - 1)];
            String variantId = ExerelinUtilsFleet.getRandomVariantIdForFaction(factionId, false, false);
            FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(newMember);
        }

        return fleet;
    }

    public static CampaignFleetAPI createFleetForFaction(String factionId, ExerelinFleetType fleetType, ExerelinFleetSize fleetSize)
    {
        SectorAPI sectorAPI = Global.getSector();
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);

        // Create fleet base
        CampaignFleetAPI fleet = sectorAPI.createFleet("player", "shuttle");
        fleet.setFaction(factionId);
        List<FleetMemberAPI> initialFleetMembers = fleet.getFleetData().getMembersListCopy();

        for(FleetMemberAPI member : initialFleetMembers)
            fleet.getFleetData().removeFleetMember(member);

        switch(fleetType)
        {
            case ASTEROID_MINING:
                ExerelinUtilsFleet.buildAsteroidMiningFleet(fleet, factionConfig);
                fleet.setName(factionConfig.asteroidMiningFleetName);
                break;
            case GAS_MINING:
                ExerelinUtilsFleet.buildGasMiningFleet(fleet, factionConfig);
                fleet.setName(factionConfig.gasMiningFleetName);
                break;
            case LOGISTICS:
                ExerelinUtilsFleet.buildLogisticsFleet(fleet, factionConfig);
                fleet.setName(factionConfig.logisticsFleetName);
                break;
            case BOARDING:
                ExerelinUtilsFleet.buildBoardingFleet(fleet, factionConfig);
                fleet.setName(factionConfig.boardingFleetName);
                break;
            case WAR:
                ExerelinUtilsFleet.buildWarFleet(fleet, factionConfig, fleetSize);
                break;
        }

        return fleet;

    }

    private static void buildAsteroidMiningFleet(CampaignFleetAPI fleet, ExerelinFactionConfig factionConfig)
    {
        String miningVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.MINING);

        FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, miningVariantId);
        fleet.getFleetData().addFleetMember(newMember);
        newMember = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, miningVariantId);
        fleet.getFleetData().addFleetMember(newMember);

        float maxCapacity = fleet.getCargo().getMaxCapacity();

        while(maxCapacity < 400)
        {
            String variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.FREIGHTER) ;
            newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(newMember);

            maxCapacity += newMember.getCargoCapacity();
        }
    }

    private static void buildGasMiningFleet(CampaignFleetAPI fleet, ExerelinFactionConfig factionConfig)
    {
        String miningVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.MINING);

        FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, miningVariantId);
        fleet.getFleetData().addFleetMember(newMember);

        float maxFuel = fleet.getCargo().getMaxFuel();

        while(maxFuel < 700)
        {
            String variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.TANKER) ;
            newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            fleet.getFleetData().addFleetMember(newMember);

            maxFuel += newMember.getFuelCapacity();
        }
    }

    private static void buildLogisticsFleet(CampaignFleetAPI fleet, ExerelinFactionConfig factionConfig)
    {
        String freighterVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.FREIGHTER);
        FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, freighterVariantId);
        fleet.getFleetData().addFleetMember(newMember);
        newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, freighterVariantId);
        fleet.getFleetData().addFleetMember(newMember);
        newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, freighterVariantId);
        fleet.getFleetData().addFleetMember(newMember);
    }

    private static void buildBoardingFleet(CampaignFleetAPI fleet, ExerelinFactionConfig factionConfig)
    {
        String boardingFlagshipVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.SUPER_FREIGHTER);
        String troopTransportVariantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(fleet.getFaction().getId(), ExerelinVariantType.TROOP_TRANSPORT);

        FleetMemberAPI newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, boardingFlagshipVariantId);
        fleet.getFleetData().addFleetMember(newMember);
        newMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, troopTransportVariantId);
        fleet.getFleetData().addFleetMember(newMember);
    }

    private static void buildWarFleet(CampaignFleetAPI fleet, ExerelinFactionConfig factionConfig, ExerelinFleetSize fleetSize)
    {
        int maxSuppliesDay = 0;

        List<ExerelinWarFleetType> possibleTypes = new ArrayList<ExerelinWarFleetType>();

        switch(fleetSize)
        {
            case SMALL:
                maxSuppliesDay = SMALL_FLEET_SUPPLIES_DAY;
                possibleTypes.add(ExerelinWarFleetType.RANDOM);
                possibleTypes.add(ExerelinWarFleetType.FRIGATE);
                break;
            case MEDIUM:
                maxSuppliesDay = MEDIUM_FLEET_SUPPLIES_DAY;
                possibleTypes.add(ExerelinWarFleetType.RANDOM);
                possibleTypes.add(ExerelinWarFleetType.FRIGATE);
                possibleTypes.add(ExerelinWarFleetType.CARRIER);
                possibleTypes.add(ExerelinWarFleetType.MEDIUM_SHIPS);
                break;
            case LARGE:
                maxSuppliesDay = LARGE_FLEET_SUPPLIES_DAY;
                possibleTypes.add(ExerelinWarFleetType.RANDOM);
                possibleTypes.add(ExerelinWarFleetType.CARRIER);
                possibleTypes.add(ExerelinWarFleetType.LARGE_SHIPS);
                possibleTypes.add(ExerelinWarFleetType.MEDIUM_SHIPS);
                break;
            case EXTRA_LARGE:
                maxSuppliesDay = EXTRA_LARGE_FLEET_SUPPLIES_DAY;
                possibleTypes.add(ExerelinWarFleetType.RANDOM);
                possibleTypes.add(ExerelinWarFleetType.CARRIER);
                possibleTypes.add(ExerelinWarFleetType.LARGE_SHIPS);
                break;
        }

        if(fleetSize != ExerelinFleetSize.SMALL && (factionConfig.carrierVariants.size() + factionConfig.fighterWings.size()) > (factionConfig.cruiserVariants.size() + factionConfig.destroyerVariants.size())/2)
        {
            possibleTypes.add(ExerelinWarFleetType.CARRIER);
            possibleTypes.add(ExerelinWarFleetType.CARRIER);
        }

        ExerelinWarFleetType fleetType = possibleTypes.get(ExerelinUtils.getRandomInRange(0, possibleTypes.size() - 1));

        switch(fleetType)
        {
            case RANDOM:
                ExerelinUtilsFleet.buildRandomFleet(fleet, maxSuppliesDay);
                break;
            case FRIGATE:
                ExerelinUtilsFleet.buildFrigateFleet(fleet, maxSuppliesDay) ;
                break;
            case CARRIER:
                ExerelinUtilsFleet.buildCarrierFleet(fleet,  maxSuppliesDay);
                break;
            case LARGE_SHIPS:
                ExerelinUtilsFleet.buildLargeShipFleet(fleet, maxSuppliesDay);
                break;
            case MEDIUM_SHIPS:
                ExerelinUtilsFleet.buildMediumShipFleet(fleet, maxSuppliesDay);
                break;
        }

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
