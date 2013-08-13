package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

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

		CampaignFleetAPI fleet = theSector.createFleet(faction, type);

		theLocation.spawnFleet(spawnPoint, 0, 0, fleet);
        fleet.setName("Command Fleet");
		theFleet = fleet;
		fleet.setPreferredResupplyLocation(target);

		setFleetAssignments(fleet);

		if(theFaction.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			Global.getSector().addMessage(ExerelinData.getInstance().getPlayerFaction() + " command fleet incoming!", Color.magenta);
		else
			Global.getSector().addMessage(theFaction + " command fleet incoming!");

		System.out.println(theFaction + " command fleet created");

		return fleet;
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

					theTarget = newTarget;
					setFleetAssignments(theFleet);
					return;
				}
				else if(!boarding && ExerelinUtils.getStationOwnerFactionId(theTarget).equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				{
					// Warn player of boarding
					boarding = true;
					System.out.println("Player owned " + theTarget.getFullName() + " being boarded by " + theFaction);
					Global.getSector().addMessage(theTarget.getFullName() + " is being boarded by " + theFaction, Color.magenta);
				}

				if(defendLocation)
				{
					if(!boarding || ExerelinUtils.getRandomInRange(0, 6000) > 0)
					{
						// Start boarding
						boarding = true;
						setFleetAssignments(theFleet);
					}
					else
					{
						return; // Finish boarding and run arrived script
					}
				}
				else
				{
					return; // Skip boarding and run arrived script
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
						theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, spawnPoint, 100);
						return;
					}

					theTarget = newTarget;
					setFleetAssignments(theFleet);
					return;
				}

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






