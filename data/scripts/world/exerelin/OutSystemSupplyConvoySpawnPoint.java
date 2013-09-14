package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.Script;
import com.fs.starfarer.api.campaign.*;

import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import data.characters.skills.scripts.ExerelinSkillData;
import data.scripts.world.BaseSpawnPoint;
import data.scripts.world.exerelin.ExerelinData;
import data.scripts.world.exerelin.ExerelinUtils;

@SuppressWarnings("unchecked")
public class OutSystemSupplyConvoySpawnPoint extends BaseSpawnPoint
{
	String owningFactionId;
	LocationAPI theLocation;
	CampaignFleetAPI theFleet;
	SectorEntityToken theTarget;

	public OutSystemSupplyConvoySpawnPoint(SectorAPI sector, LocationAPI location,
										   float daysInterval, int maxFleets, SectorEntityToken anchor)
	{
		super(sector, location, daysInterval, maxFleets, anchor);
		theLocation = location;
	}

	public void setFaction(String factionId)
	{
		owningFactionId = factionId;
		while(this.getFleets().size() > 0)
			this.getFleets().remove(0);
	}

	@Override
	public CampaignFleetAPI spawnFleet() {
		String type = "exerelinOutSystemSupplyConvoy";

		if(this.getFleets().size() == this.getMaxFleets())
			return null;

		// Check cargo amounts
		int spawnChance = 3;
		CargoAPI cargo = getAnchor().getCargo();
		if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 800 && cargo.getMarines() > 400 && cargo.getSupplies() > 3200 && cargo.getFuel() > 800)
			spawnChance = 2;
		if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) > 1600 && cargo.getMarines() > 800 && cargo.getSupplies() > 6400 && cargo.getFuel() > 1600)
			spawnChance = 1;

		if(spawnChance != 3 && ExerelinUtils.getRandomInRange(1, spawnChance) == 1) // 50% or 0% chance of spawning if lots of resources
			return null;

		// Get fleet
		// If faction is player, get which faction player is playing as
		CampaignFleetAPI fleet = getSector().createFleet(owningFactionId, type);

	    DiplomacyRecord diplomacyRecord = ExerelinData.getInstance().getSectorManager().getDiplomacyManager().getRecordForFaction(owningFactionId);
	    if (diplomacyRecord.hasWarTargetInSystem((StarSystemAPI)getLocation(), false))
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 4, 5, owningFactionId, getSector());
	    else
	      ExerelinUtils.addRandomEscortShipsToFleet (fleet, 1, 2, owningFactionId, getSector());

		theFleet = fleet;
		getLocation().spawnFleet(ExerelinUtils.getRandomOffMapPoint(getLocation()), 0, 0, fleet);
		fleet.setPreferredResupplyLocation(getAnchor());
        fleet.setName("Supply Convoy");
		theTarget = getAnchor();

		Script script2 = createTestTargetScript();
		Script arriveScript = createArrivedTargetScript();

		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, getAnchor(), 1000, script2);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, getAnchor(), 10, arriveScript);
		fleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, getAnchor(), 10);
		this.getFleets().add(fleet);
		return fleet;
	}

	private Script createArrivedTargetScript()
	{
		return new Script() {
			public void run() {
				CargoAPI targetCargo = theTarget.getCargo();

                float cargoMultiplier = 1.0f;

                if(theFleet.getFaction().getId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
                    cargoMultiplier = ExerelinPlayerFunctions.getPlayerStationConvoyResourceMultipler();

				targetCargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, (int)(200 * cargoMultiplier));
                System.out.println("Adding crew (200): " + (200 * cargoMultiplier));
				targetCargo.addCrew(CargoAPI.CrewXPLevel.ELITE, 1);
				targetCargo.addCrew(CargoAPI.CrewXPLevel.VETERAN, 1);
				targetCargo.addCrew(CargoAPI.CrewXPLevel.GREEN, 1);
				targetCargo.addMarines((int)(100 * cargoMultiplier));
				targetCargo.addFuel(100 * cargoMultiplier); // Halved due to mining fleets
				targetCargo.addSupplies(400 * cargoMultiplier); // Halved due to mining fleets

                if(theFleet.getFaction().getId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
                {
                    // Faction is player so add weapons/ships to cargo
                    int numWeapons = 4;
                    int numShips = 2;

                    if(ExerelinUtils.getRandomInRange(0, (int)(99 / cargoMultiplier)) == 0)
                    {
                        System.out.println("Adding extra weapons/ships");
                        numWeapons = numWeapons*2;
                        numShips = numShips*2;
                    }

                    ExerelinUtils.addWeaponsToCargo(targetCargo, numWeapons, theFleet.getFaction().getId(), getSector());
                    ExerelinUtils.addRandomFactionShipsToCargo(targetCargo, numShips, theFleet.getFaction().getId(), getSector());
                }
			}
		};
	}

	private Script createTestTargetScript()
	{
		return new Script() {
			public void run() {
				if(!ExerelinUtils.getStationOwnerFactionId(getAnchor()).equalsIgnoreCase(owningFactionId))
				{
					theFleet.clearAssignments();
					theFleet.addAssignment(FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, ExerelinUtils.getRandomOffMapPoint(theLocation), 100);
				}
			}
		};
	}
}