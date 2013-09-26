package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
import data.scripts.world.OmniFac;

import java.awt.*;

@SuppressWarnings("unchecked")
public class OutSystemStationConstructionFleet
{
	SectorEntityToken theTarget;
	public SectorEntityToken spawnPoint;
	String theFaction;
	LocationAPI theLocation;
	CampaignFleetAPI theFleet;
	SectorAPI theSector;
	String theAssignment;
	StarSystemAPI theSystem;

	public OutSystemStationConstructionFleet(SectorAPI sector, StarSystemAPI system, LocationAPI location, String faction, SectorEntityToken target, String assignmnent)
	{
		theFaction = faction;
		theLocation = location;
		theSector = sector;
		theTarget = target;
		theAssignment = assignmnent;
		theSystem = system;
	}

	public CampaignFleetAPI spawnFleet()
	{
		// DEFAULTS
		String type = "exerelinStationConstructionFleet";
		String faction = theFaction;

		// Get spawn location
		this.spawnPoint = ExerelinUtils.getRandomOffMapPoint(theLocation);

		CampaignFleetAPI fleet = theSector.createFleet(faction, type);

		theLocation.spawnFleet(spawnPoint, 0, 0, fleet);
		theFleet = fleet;
		fleet.setPreferredResupplyLocation(theTarget);

		Script arriveScript = createArrivedScript();

		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, theTarget, 3000, arriveScript);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, theTarget, 10);

		System.out.println(theFaction + " station construction fleet created to " + theAssignment);

		return fleet;
	}

	private Script createArrivedScript() {
		return new Script() {
			public void run() {
				if(theAssignment.equalsIgnoreCase("storage"))
				{
					int orbitRadius = 300;
					if (theTarget.getFullName().contains(" I") || theTarget.getFullName().contains(" II") || theTarget.getFullName().contains(" III"))
						orbitRadius = 200;
					else if(theTarget.getFullName().contains("Gaseous"))
						orbitRadius = 500;
					SectorEntityToken storage = theSystem.addOrbitalStation(theTarget, ExerelinUtils.getRandomInRange(1,359), orbitRadius, ExerelinUtils.getRandomInRange(40,60), "Storage Facility", "neutral");
					storage.getCargo().setFreeTransfer(true);
					ExerelinUtils.populateStartingStorageFacility(storage);
					System.out.println(theFaction + " constructed station ");
				}
				else if (theAssignment.equalsIgnoreCase("omnifac"))
				{
					int orbitRadius = 300;
					if (theTarget.getFullName().contains(" I") || theTarget.getFullName().contains(" II") || theTarget.getFullName().contains(" III"))
						orbitRadius = 200;
					else if(theTarget.getFullName().contains("Gaseous"))
						orbitRadius = 500;
					theSystem.addSpawnPoint(new OmniFac(theSystem.addOrbitalStation(theTarget, ExerelinUtils.getRandomInRange(1,359), orbitRadius, ExerelinUtils.getRandomInRange(40,60), "Omnifactory", "neutral")));
					System.out.println(theFaction + " constructed station ");
				}
				else
				{
					System.out.println("EXERELIN ERROR: Assignment for station construction fleet invalid");
				}
			}
		};
	}
}






