package exerelin;

import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;
//import data.scripts.world.OmniFac;

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
        fleet.getCommander().setPersonality("cautious");
		theLocation.spawnFleet(spawnPoint, 0, 0, fleet);
		theFleet = fleet;
		fleet.setPreferredResupplyLocation(theTarget.getOrbit().getFocus());

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

					if (((PlanetAPI)theTarget).isMoon())
						orbitRadius = 200;
					else if(((PlanetAPI)theTarget).isGasGiant())
						orbitRadius = 500;
					//SectorEntityToken storage = theSystem.addOrbitalStation(theTarget, ExerelinUtils.getRandomInRange(1,359), orbitRadius, ExerelinUtils.getRandomInRange(40,60), "Storage Facility", "neutral");
					//storage.getCargo().setFreeTransfer(true);
					//ExerelinUtils.populateStartingStorageFacility(storage);
					System.out.println(theFaction + " constructed station ");
				}
				else if (theAssignment.equalsIgnoreCase("omnifac"))
				{
					int orbitRadius = 300;
					if (((PlanetAPI)theTarget).isMoon())
						orbitRadius = 200;
					else if(((PlanetAPI)theTarget).isGasGiant())
						orbitRadius = 500;

                    //OmniFac factory = new OmniFac(theSystem.addOrbitalStation(theTarget, ExerelinUtils.getRandomInRange(1,359), orbitRadius, ExerelinUtils.getRandomInRange(40,60), "Omnifactory", "independent"), "data/config/omnifac_settings.json");
                    //theTarget.getContainingLocation().addScript(factory);
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






