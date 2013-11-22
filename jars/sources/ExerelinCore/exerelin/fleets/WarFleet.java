package exerelin.fleets;

import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.*;
import exerelin.*;
import exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import exerelin.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;


@SuppressWarnings("unchecked")
public class WarFleet extends ExerelinFleetBase
{
    public static enum FleetStance
    {
        Attack,
        Defense,
        Patrol
    }

    SectorEntityToken targetStation;
    SectorEntityToken resupplyStation;
    SectorEntityToken defendStation;
    SectorEntityToken anchor;

    FleetStance currentStance;

    public WarFleet(String faction, SectorEntityToken anchor, SectorEntityToken target, SectorEntityToken defend, SectorEntityToken resupply, FleetStance stance)
    {
        this.anchor = anchor;
        this.targetStation = target;
        this.resupplyStation = resupply;
        this.defendStation = defend;
        this.currentStance = stance;

        fleet = Global.getSector().createFleet(faction, "exerelinGenericFleet");

        float eliteShipChance = 0.01f;

        // If losing, 5% chance to add a elite ship to fleet
        if(SectorManager.getCurrentSectorManager().getLosingFaction().equalsIgnoreCase(faction))
            eliteShipChance = eliteShipChance + 0.05f;

        // Add player chance
        if(faction.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
            eliteShipChance = eliteShipChance + ExerelinUtilsPlayer.getPlayerFactionFleetEliteShipBonusChance();

        if(ExerelinUtils.getRandomInRange(0, (int) (99 / (eliteShipChance * 100))) == 0)
            ExerelinUtils.addEliteShipToFleet(fleet);

        if(ExerelinUtils.canStationSpawnFleet(anchor, fleet, 1, 0.1f, true, ExerelinUtils.getCrewXPLevelForFaction(faction)))
        {
            ExerelinUtils.addFreightersToFleet(fleet);
            ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(faction));
            ExerelinUtilsFleet.sortByHullSize(fleet);

            fleet.setPreferredResupplyLocation(resupply);
            fleet.getCommander().setPersonality("aggressive");

            setFleetAssignments();

            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(anchor, 0, 0, fleet));
        }
        else
        {
            this.fleet = null;
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
        fleet.clearAssignments();

        if(this.currentStance == FleetStance.Attack)
            setAttackAssignments();
        else if(this.currentStance == FleetStance.Patrol)
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
            // Check if home is under threat
            Boolean homeUnderThreat = false;
            if(defendStation == anchor)
                homeUnderThreat = true;

            // Only allow raid system choice if home is not under threat
            int action;
            if(homeUnderThreat)
                action = 0;
            else
                action = ExerelinUtils.getRandomInRange(0,2);

            if(action == 0)
            {
                // Defend station
                fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, defendStation, 270);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
                fleet.setPreferredResupplyLocation(defendStation);
            }
            else if(action == 1)
            {
                // Raid system of station
                fleet.addAssignment(FleetAssignment.RAID_SYSTEM, defendStation, 270);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
                fleet.setPreferredResupplyLocation(defendStation);
            }
            else if(action == 2)
            {
                // PATROL system of station
                fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, defendStation, 270);
                fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, anchor, 1000);
                fleet.setPreferredResupplyLocation(defendStation);
            }
        }
        else if(ExerelinUtilsFaction.isFactionAtWar(this.fleet.getFaction().getId(), true))
        {
            // PATROL system
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






