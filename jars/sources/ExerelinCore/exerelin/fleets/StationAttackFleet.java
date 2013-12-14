package exerelin.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import exerelin.*;
import exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import exerelin.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;

@SuppressWarnings("unchecked")
public class StationAttackFleet extends ExerelinFleetBase
{
    SectorEntityToken anchor;
    SectorEntityToken targetStation;
    SectorEntityToken resupplyStation;

	boolean boarding = false;
    long lastTimeCheck;

	public StationAttackFleet(String faction, SectorEntityToken anchor, SectorEntityToken targetStation, SectorEntityToken resupplyStation, Boolean deductResources)
	{
        this.anchor = anchor;
        this.targetStation = targetStation;
        this.resupplyStation = resupplyStation;

        fleet = Global.getSector().createFleet(faction, "exerelinInSystemStationAttackFleet");

        if (ExerelinUtilsFaction.isFactionAtWar(faction, true))
            ExerelinUtils.addRandomEscortShipsToFleet(fleet, 3, 5, faction, Global.getSector());
        else
            ExerelinUtils.addRandomEscortShipsToFleet (fleet, 1, 2, faction, Global.getSector());

        boarding = false;

        if(!deductResources || ExerelinUtils.canStationSpawnFleet(anchor, fleet, 1, 0.8f, false, ExerelinUtils.getCrewXPLevelForFaction(faction)))
        {
            ExerelinUtils.addFreightersToFleet(fleet);
            ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.2f, 0.8f, ExerelinUtils.getCrewXPLevelForFaction(faction));
            ExerelinUtilsFleet.sortByHullSize(fleet);

            fleet.setPreferredResupplyLocation(resupplyStation);
            fleet.setName(ExerelinConfig.getExerelinFactionConfig(faction).boardingFleetName);

            setFleetAssignments();

            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(anchor, 0, 0, fleet));
        }
        else
            this.fleet = null;
	}

	public void setTarget(SectorEntityToken targetStation, SectorEntityToken resupplyStation)
	{
		if(this.targetStation != null && targetStation != null && this.targetStation.getFullName().equalsIgnoreCase(targetStation.getFullName()))
			return;

        this.targetStation = targetStation;
        this.resupplyStation = resupplyStation;
        boarding = false;

		setFleetAssignments();
	}

	public void setFleetAssignments()
	{
		fleet.clearAssignments();
		if(targetStation != null && ExerelinUtils.isValidBoardingFleet(fleet, true))
		{
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, targetStation, 3000, createTestTargetScript());
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, targetStation, 3, createArrivedScript());
            fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, targetStation, 10);
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, resupplyStation, 15);

            fleet.setPreferredResupplyLocation(resupplyStation);
		}
		else
			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, resupplyStation, 10);
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(targetStation != null)
				{
					if(targetStation.getFaction().getId().equalsIgnoreCase(fleet.getFaction().getId()))
					{
						// If we own station then just go there
						return; // Will run arrived script
					}
					else if(targetStation.getFaction().getRelationship(fleet.getFaction().getId()) >= 0)
					{
						// If neutral/ally owns station, despawn (home station may reassign a target)
						fleet.clearAssignments();
                        boarding = false;
						fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, resupplyStation, 10);
						return;
					}
					else if(!boarding && targetStation.getFaction().getId().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFaction().getId()))
					{
						ExerelinUtilsMessaging.addMessage(targetStation.getFullName() + " is being boarded by " + fleet.getFaction().getDisplayName(), Color.magenta);
					}
				}

                if(!boarding)
                {
                    lastTimeCheck = Global.getSector().getClock().getTimestamp();
                    boarding = true;
                }

                if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) >= 1)
                {
                    lastTimeCheck = Global.getSector().getClock().getTimestamp();
                    if(targetStation != null && ExerelinUtils.boardStationAttempt(fleet, targetStation, false, true))
                        boarding = false;
                    else
                        setFleetAssignments();
                }
                else
                    setFleetAssignments();

			}
		};
	}

	private Script createArrivedScript() {
		return new Script() {
			public void run() {
				if(targetStation != null && targetStation.getFaction().getId().equalsIgnoreCase(fleet.getFaction().getId()))
				{
					// If we already own it deliver resources (as if we took it over), defend and despawn
					CargoAPI cargo = targetStation.getCargo();
					cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR,  80);
					cargo.addFuel(80);
					cargo.addMarines(40);
					cargo.addSupplies(320);
                    ExerelinUtils.removeShipsFromFleet(fleet, ExerelinConfig.validBoardingFlagships, true, false);
                    ExerelinUtils.removeShipsFromFleet(fleet, ExerelinConfig.validTroopTransportShips, false, false);
                    ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleet.getFaction().getId()));
				}
				else if(targetStation == null || targetStation.getFaction().getRelationship(fleet.getFaction().getId()) >= 0)
				{
					// If no target assigned or neutral/ally owns station, go home
					fleet.clearAssignments();
                    boarding = false;
					fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, resupplyStation, 10);
				}
				else
				{
					// Else, take over station
                    StationRecord stationRecord = SystemManager.getSystemManagerForAPI((StarSystemAPI)targetStation.getContainingLocation()).getSystemStationManager().getStationRecordForToken(targetStation);
                    stationRecord.setOwner(fleet.getFaction().getId(), true, true);
                    stationRecord.clearCargo();
                    ExerelinUtils.removeShipsFromFleet(fleet, ExerelinConfig.validTroopTransportShips, false, false);
                    ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.5f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleet.getFaction().getId()));
				}
			}
		};
	}
}






