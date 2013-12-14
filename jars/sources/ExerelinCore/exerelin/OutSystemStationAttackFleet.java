package exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import exerelin.commandQueue.CommandSpawnPrebuiltFleet;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.ExerelinUtilsMessaging;

import java.awt.*;

@SuppressWarnings("unchecked")
public class OutSystemStationAttackFleet
{
	SectorEntityToken theTarget;
	public SectorEntityToken spawnPoint;
	String theFaction;
	LocationAPI theLocation;
	CampaignFleetAPI theFleet;
	SectorAPI theSector;
	Boolean defendLocation;
	Boolean boarding = false;
    long lastTimeCheck;

	public OutSystemStationAttackFleet(SectorAPI sector, LocationAPI location, String faction, Boolean defend)
	{
		theFaction = faction;
		theLocation = location;
		theSector = sector;
		defendLocation = defend;
	}

	public CampaignFleetAPI spawnFleet(SectorEntityToken targetPredetermined, SectorEntityToken spawnPointPredetermined) {

		// DEFAULTS
		String type = "exerelinOutSystemStationAttackFleet";
		String faction = theFaction;

		if(spawnPointPredetermined == null)
			this.spawnPoint = ExerelinUtils.getRandomOffMapPoint(theLocation);
		else
			this.spawnPoint = spawnPointPredetermined;

		// Get a target to attack
		SectorEntityToken target;
		if(targetPredetermined == null)
		{
			target = ExerelinUtils.getClosestEnemyStation(faction, (StarSystemAPI)theLocation, this.theSector, this.spawnPoint);
			if(target == null)
				return null;
		}
		else
			target = targetPredetermined;

		this.theTarget = target;
		this.boarding = false;

		CampaignFleetAPI fleet = theSector.createFleet(faction, "exerelinInSystemStationAttackFleet");
        CampaignFleetAPI extraFleet = theSector.createFleet(faction, "exerelinGenericFleet");
        CampaignFleetAPI extraFleetTwo = theSector.createFleet(faction, "exerelinGenericFleet");
        ExerelinUtils.mergeFleets(fleet, extraFleet);
        ExerelinUtils.mergeFleets(fleet, extraFleetTwo);
        ExerelinUtils.addFreightersToFleet(fleet);
        ExerelinUtils.addFreightersToFleet(fleet);
        ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.3f, 0.3f, CargoAPI.CrewXPLevel.ELITE);
        ExerelinUtilsFleet.sortByHullSize(fleet);


        fleet.setName(ExerelinConfig.getExerelinFactionConfig(faction).commandFleetName);
        if(ExerelinUtils.getRandomInRange(0,1) == 1)
          fleet.getCommander().setPersonality("aggressive");
		theFleet = fleet;
		fleet.setPreferredResupplyLocation(target);

		setFleetAssignments(fleet);

		if(theFaction.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
			ExerelinUtilsMessaging.addMessage(((StarSystemAPI) theLocation).getName() + ": " + Global.getSector().getFaction(theFaction).getDisplayName() + " command fleet incoming!", Color.magenta);
		else
            ExerelinUtilsMessaging.addMessage(((StarSystemAPI)theLocation).getName() + ": " + Global.getSector().getFaction(theFaction).getDisplayName() + " command fleet incoming!");

        //theLocation.spawnFleet(spawnPoint, 0, 0, fleet);
        SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandSpawnPrebuiltFleet(spawnPoint, 0, 0, fleet));

		return null;
	}

	private void setFleetAssignments(CampaignFleetAPI fleet)
	{
		fleet.clearAssignments();

		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, theTarget, 3000, createTestTargetScript());
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, theTarget, 10, createArrivedScript());
		fleet.addAssignment(FleetAssignment.DEFEND_LOCATION, theTarget, 60);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, theTarget, 10);
	}

	private Script createTestTargetScript() {
		return new Script() {
			public void run() {
				if(ExerelinUtils.getStationOwnerFactionId(theTarget).equalsIgnoreCase(theFaction))
					return; // We own it so run arrived script
				else if(!ExerelinUtils.getStationOwnerFactionId(theTarget).equalsIgnoreCase(theFaction) && theSector.getFaction(ExerelinUtils.getStationOwnerFactionId(theTarget)).getRelationship(theFaction) >= 0)
				{
					// Ally or neutral owns it so get a new target to attack
					SectorEntityToken newTarget = ExerelinUtils.getClosestEnemyStation(theFaction, (StarSystemAPI)theLocation, theSector, theTarget);
					if(newTarget == null)
					{
						// No target so leave system
						theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, spawnPoint, 100);
						return;
					}
                    boarding = false;
					theTarget = newTarget;
                    theFleet.setPreferredResupplyLocation(newTarget);
					setFleetAssignments(theFleet);
					return;
				}
				else if(!boarding && ExerelinUtils.getStationOwnerFactionId(theTarget).equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
				{
					// Warn player of boarding
                    ExerelinUtilsMessaging.addMessage(theTarget.getFullName() + " is being boarded by " + Global.getSector().getFaction(theFaction).getDisplayName(), Color.magenta);
				}

				if(defendLocation)
				{
					if(!boarding)
					{
						// Start boarding
                        lastTimeCheck = Global.getSector().getClock().getTimestamp();
						boarding = true;
						setFleetAssignments(theFleet);
					}

                    if(Global.getSector().getClock().getElapsedDaysSince(lastTimeCheck) >= 1)
                    {
                        lastTimeCheck = Global.getSector().getClock().getTimestamp();
                        if(ExerelinUtils.getRandomInRange(0, 15) == 0)
                            boarding = false;
                        else
                            setFleetAssignments(theFleet);
                    }
                    else
                        setFleetAssignments(theFleet);
				}
			}
		};
	}

	private Script createArrivedScript() {
		return new Script() {
			public void run() {
				if(ExerelinUtils.getStationOwnerFactionId(theTarget).equalsIgnoreCase(theFaction))
				{
					// We own station
					if(!defendLocation)
					{
						theTarget.getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 200);
						theTarget.getCargo().addMarines(100);
						theTarget.getCargo().addFuel(200);
						theTarget.getCargo().addSupplies(800);
					}
					else
					{
						theTarget.getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 800);
						theTarget.getCargo().addMarines(400);
						theTarget.getCargo().addFuel(800);
						theTarget.getCargo().addSupplies(3200);
					}
					return; // commence defending or despawn
				}
				else if(!ExerelinUtils.getStationOwnerFactionId(theTarget).equalsIgnoreCase(theFaction) && theSector.getFaction(ExerelinUtils.getStationOwnerFactionId(theTarget)).getRelationship(theFaction) >= 0)
				{
					// Ally or neutral owns it so get new target
					SectorEntityToken newTarget = ExerelinUtils.getClosestEnemyStation(theFaction, (StarSystemAPI)theLocation, theSector, theTarget);
					if(newTarget == null)
					{
						// No target so leave system
                        theFleet.clearAssignments();
						theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, spawnPoint, 100);
						return;
					}

					theTarget = newTarget;
					setFleetAssignments(theFleet);
                    theFleet.setPreferredResupplyLocation(newTarget);
					return;
				}

                ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinConfig.validBoardingFlagships, true, false);
                ExerelinUtils.removeShipsFromFleet(theFleet, ExerelinConfig.validTroopTransportShips, false, false);
				StationRecord stationRecord = SectorManager.getCurrentSectorManager().getSystemManager((StarSystemAPI)theLocation).getSystemStationManager().getStationRecordForToken(theTarget);
				stationRecord.setOwner(theFaction, true, true);
				stationRecord.clearCargo();

				if(!defendLocation)
				{
					theTarget.getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 200);
					theTarget.getCargo().addMarines(100);
					theTarget.getCargo().addFuel(200);
					theTarget.getCargo().addSupplies(800);
				}
				else
				{
					theTarget.getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 800);
					theTarget.getCargo().addMarines(400);
					theTarget.getCargo().addFuel(800);
					theTarget.getCargo().addSupplies(3200);
				}
			}
		};
	}
}






