package exerelin.fleets;

import com.fs.starfarer.api.campaign.*;
import exerelin.*;
import exerelin.campaign.DiplomacyManager;
import exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import exerelin.SectorManager;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.ExerelinUtilsStation;


@SuppressWarnings("unchecked")
public class WarFleet extends ExerelinFleetBase
{
    public static enum FleetStance
    {
        ATTACK,
        DEFENSE,
        PATROL
    }

    SectorEntityToken targetStation;
    SectorEntityToken resupplyStation;
    SectorEntityToken defendStation;
    SectorEntityToken anchor;

    FleetStance currentStance;

    public WarFleet(String faction, SectorEntityToken anchor, SectorEntityToken target, SectorEntityToken defend, SectorEntityToken resupply, FleetStance stance, Boolean deductResources)
    {
        this.anchor = anchor;
        this.targetStation = target;
        this.resupplyStation = resupply;
        this.defendStation = defend;
        this.currentStance = stance;

        ExerelinUtilsFleet.ExerelinFleetSize fleetSize = ExerelinUtilsStation.getSpawnFleetSizeForStation(anchor);

        if(!deductResources)
            fleetSize = ExerelinUtilsFleet.ExerelinFleetSize.LARGE;

        if(fleetSize == null)
        {
            this.fleet = null;
            return;
        }

        this.fleet = ExerelinUtilsFleet.createFleetForFaction(faction, ExerelinUtilsFleet.ExerelinFleetType.WAR, fleetSize);

        float eliteShipChance = 0.01f;

        // If losing, 5% chance to add a elite ship to fleet
        if(SectorManager.getCurrentSectorManager().getLosingFaction().equalsIgnoreCase(faction))
            eliteShipChance = eliteShipChance + 0.05f;

        // Add player chance
        if(faction.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
            eliteShipChance = eliteShipChance + ExerelinUtilsPlayer.getPlayerFactionFleetEliteShipBonusChance();

        if(ExerelinUtils.getRandomInRange(0, (int) (99 / (eliteShipChance * 100))) == 0)
            ExerelinUtilsFleet.addCapitalShipToFleet(fleet);

        ExerelinUtilsFleet.addFreightersToFleet(fleet);
        ExerelinUtilsFleet.resetFleetCargoToDefaults(fleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(faction));
        ExerelinUtilsFleet.sortByHullSize(fleet);

        fleet.setPreferredResupplyLocation(resupply);
        fleet.getCommander().setPersonality("aggressive");

        setFleetAssignments();

        SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(anchor, 0, 0, fleet));

        if(deductResources)
        {
            ExerelinUtilsStation.removeResourcesFromStationForFleetSize(anchor, fleetSize);
        }
    }

    public void setTarget(SectorEntityToken target, SectorEntityToken defend, SectorEntityToken resupply)
    {
        this.targetStation = target;
        this.defendStation = defend;
        this.resupplyStation = resupply;

        this.setFleetAssignments();
    }

    public void setStance(FleetStance stance)
    {
        this.currentStance = stance;

        setFleetAssignments();
    }

    public void setFleetAssignments()
    {
        if(this.fleet == null)
            return;

        fleet.clearAssignments();

        if(this.currentStance == FleetStance.ATTACK)
            setAttackAssignments();
        else if(this.currentStance == FleetStance.PATROL)
            setPatrolAssignments();
        else
            setDefendAssignments();
    }

    private void setAttackAssignments()
    {
        ExerelinUtils.renameFleet(fleet, "attack");

        if(targetStation != null)
        {
            fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, targetStation, 1000);
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);

            fleet.setPreferredResupplyLocation(resupplyStation);
        }
        else
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
    }

    private void setDefendAssignments()
    {
        ExerelinUtils.renameFleet(fleet, "defense");

        fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, this.anchor, 60);
        fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, this.anchor, 60);

        fleet.setPreferredResupplyLocation(anchor);
    }

    private  void setPatrolAssignments()
    {
        ExerelinUtils.renameFleet(fleet, "patrol");

        if(this.defendStation != null)
        {
            // Check if home is under major threat
            Boolean homeUnderThreat = false;
            if(defendStation == anchor)
                homeUnderThreat = true;

            // Only allow raid/patrol choice if home is not under threat
            int action;
            if(homeUnderThreat)
                action = 0;
            else
                action = ExerelinUtils.getRandomInRange(1,2);

            if(action == 0)
            {
                // Defend station
                fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation, 270);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
                fleet.setPreferredResupplyLocation(defendStation);
            }
            else if(action == 1 && ExerelinUtils.doesSystemHaveEntityForFaction((StarSystemAPI)defendStation.getContainingLocation(), fleet.getFaction().getId(), -100000f, -0.01f))
            {
                // Raid system of defend station
                fleet.addAssignment(FleetAssignment.RAID_SYSTEM, defendStation, 270);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
                fleet.setPreferredResupplyLocation(defendStation);
            }
            else if(action == 2 && defendStation.getContainingLocation() == anchor.getContainingLocation())
            {
                // Patrol system of defend station
                fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, defendStation, 270);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
                fleet.setPreferredResupplyLocation(defendStation);
            }
            else
            {
                // Patrol home system
                fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, anchor, 270);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
                fleet.setPreferredResupplyLocation(anchor);
            }
        }
        else if(DiplomacyManager.isFactionAtWar(this.fleet.getFaction().getId(), true))
        {
            // Patrol home system
            fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, anchor, 270);
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
            fleet.setPreferredResupplyLocation(anchor);
        }
        else
        {
            fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
        }
    }
}






