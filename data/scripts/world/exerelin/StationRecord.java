package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.world.BaseSpawnPoint;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;

public class StationRecord
{
	private StationManager stationManager;

	private SectorEntityToken stationToken;
	private CargoAPI stationCargo;
	private String planetType;
	private DiplomacyRecord owningFaction;
	private Float efficiency = 1f;
	private StarSystemAPI system;

	private int numStationsTargeting;
	private StationRecord targetStationRecord;
	private StationRecord assistStationRecord;
	private SectorEntityToken targetAsteroid;
	private SectorEntityToken targetGasGiant;

	// Spawnpoints
	private AttackFleetSpawnPoint attackSpawn;
	private DefenseFleetSpawnPoint defenseSpawn;
	private PatrolFleetSpawnPoint patrolSpawn;
	private InSystemStationAttackShipSpawnPoint stationAttackFleetSpawn;
	private InSystemSupplyConvoySpawnPoint inSystemSupplyConvoySpawn;
	private OutSystemSupplyConvoySpawnPoint outSystemSupplyConvoySpawn;
	private AsteroidMiningFleetSpawnPoint asteroidMiningFleetSpawnPoint;
	private GasMiningFleetSpawnPoint gasMiningFleetSpawnPoint;

	public StationRecord(SectorAPI sector, StarSystemAPI inSystem, StationManager manager, SectorEntityToken token)
	{
		system = inSystem;

		stationToken = token;
		stationCargo = token.getCargo();
		planetType = this.derivePlanetType(token);

		attackSpawn = new AttackFleetSpawnPoint(sector, system, 1000000, 2, token);
		defenseSpawn = new DefenseFleetSpawnPoint(sector, system, 1000000, 2, token);
		patrolSpawn = new PatrolFleetSpawnPoint(sector, system, 1000000, 2, token);
		stationAttackFleetSpawn = new InSystemStationAttackShipSpawnPoint(sector, system, 1000000, 1, token);
		inSystemSupplyConvoySpawn = new InSystemSupplyConvoySpawnPoint(sector, system, 1000000, 1, token);
		outSystemSupplyConvoySpawn = new OutSystemSupplyConvoySpawnPoint(sector, system, 1000000, 1, token);
		asteroidMiningFleetSpawnPoint = new AsteroidMiningFleetSpawnPoint(sector,  system,  1000000, 1, token);
		gasMiningFleetSpawnPoint = new GasMiningFleetSpawnPoint(sector,  system,  1000000, 1, token);

		stationManager = manager;
	}

	public DiplomacyRecord getOwner()
	{
		return owningFaction;
	}

	public void setOwner(String newOwnerFactionId, Boolean displayMessage, Boolean updateRelationship)
	{
		if(newOwnerFactionId == null)
		{
			// Set station back to abandoned
			stationToken.setFaction("abandonded");
			owningFaction = null;
			stationCargo.setFreeTransfer(false);
			return;
		}
		String originalOwnerId = "";
		if(owningFaction != null)
			originalOwnerId = owningFaction.getFactionId();

		if(displayMessage)
		{
			if(newOwnerFactionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				Global.getSector().addMessage(stationToken.getFullName() + " taken over by " + ExerelinData.getInstance().getPlayerFaction(), Color.magenta);
			else if(this.getOwner() != null && this.getOwner().getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				Global.getSector().addMessage(stationToken.getFullName() + " taken over by " + newOwnerFactionId, Color.magenta);
			else
				Global.getSector().addMessage(stationToken.getFullName() + " taken over by " + newOwnerFactionId);

			System.out.println(stationToken.getFullName() + " taken over by " + newOwnerFactionId);
		}

		stationToken.setFaction(newOwnerFactionId);

		attackSpawn.setFaction(newOwnerFactionId);
		defenseSpawn.setFaction(newOwnerFactionId);
		patrolSpawn.setFaction(newOwnerFactionId);
		stationAttackFleetSpawn.setFaction(newOwnerFactionId);
		inSystemSupplyConvoySpawn.setFaction(newOwnerFactionId);
		outSystemSupplyConvoySpawn.setFaction(newOwnerFactionId);
		asteroidMiningFleetSpawnPoint.setFaction(newOwnerFactionId);
		gasMiningFleetSpawnPoint.setFaction(newOwnerFactionId);

		owningFaction = ExerelinData.getInstance().systemManager.diplomacyManager.getRecordForFaction(newOwnerFactionId);

		if(newOwnerFactionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			stationToken.getCargo().setFreeTransfer(ExerelinData.getInstance().playerOwnedStationFreeTransfer);
		else
			stationToken.getCargo().setFreeTransfer(false);

		// Update relationship
		if(!originalOwnerId.equalsIgnoreCase("") && updateRelationship)
			ExerelinData.getInstance().systemManager.diplomacyManager.updateRelationshipOnEvent(originalOwnerId, newOwnerFactionId, "LostStation");
	}

	public void setEfficiency(float value)
	{
		efficiency = value;
	}

	public SectorEntityToken getStationToken()
	{
		return stationToken;
	}

	public int getNumAttacking()
	{
		return numStationsTargeting;
	}

	public StationRecord getTargetStationRecord()
	{
		return targetStationRecord;
	}

	public void startTargeting()
	{
		numStationsTargeting++;
	}

	public void stopTargeting()
	{
		numStationsTargeting--;
		if(numStationsTargeting <= 0)
		{
			numStationsTargeting = 0;
		}
	}

	public void spawnFleets()
	{
		deriveClosestEnemyTarget();
		deriveStationToAssist();
		deriveClosestAsteroid();
		deriveClosestGasGiant();

		setFleetTargets();

		removeDeadOrRebelFleets(attackSpawn);
		removeDeadOrRebelFleets(stationAttackFleetSpawn);
		removeDeadOrRebelFleets(defenseSpawn);
		removeDeadOrRebelFleets(patrolSpawn);
		removeDeadOrRebelFleets(outSystemSupplyConvoySpawn);
		removeDeadOrRebelFleets(inSystemSupplyConvoySpawn);
		removeDeadOrRebelFleets(asteroidMiningFleetSpawnPoint);
		removeDeadOrRebelFleets(gasMiningFleetSpawnPoint);

		outSystemSupplyConvoySpawn.spawnFleet();
		inSystemSupplyConvoySpawn.spawnFleet();
		asteroidMiningFleetSpawnPoint.spawnFleet();
		gasMiningFleetSpawnPoint.spawnFleet();


		if(ExerelinUtils.getRandomInRange(0, 2) == 0 || (targetStationRecord != null && targetStationRecord.getOwner() == null))
			stationAttackFleetSpawn.spawnFleet();

		for(int i = defenseSpawn.getFleets().size(); i < defenseSpawn.getMaxFleets(); i++)
			defenseSpawn.spawnFleet();

		for(int i = attackSpawn.getFleets().size(); i < attackSpawn.getMaxFleets(); i++)
			attackSpawn.spawnFleet();

		for(int i = patrolSpawn.getFleets().size(); i < patrolSpawn.getMaxFleets(); i++)
			patrolSpawn.spawnFleet();
	}

	// Increase resources in station based off efficiency
	public void increaseResources()
	{
		if(stationCargo.getFuel() < 1600)
			stationCargo.addFuel(150*efficiency);
		if(stationCargo.getSupplies() < 6400)
			stationCargo.addSupplies(600*efficiency);
		if(stationCargo.getMarines() < 800)
			stationCargo.addMarines((int)(100*efficiency));
		if(stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) < 1600)
			stationCargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, (int)(200*efficiency));

		if(planetType.equalsIgnoreCase("gas"))
			stationCargo.addFuel(200*efficiency);
		if(planetType.equalsIgnoreCase("moon"))
			stationCargo.addSupplies(800*efficiency);
		if(planetType.equalsIgnoreCase("planet"))
		{
			stationCargo.addMarines((int)(100*efficiency));
			stationCargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, (int)(200*efficiency));
		}

		if(owningFaction.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()) && efficiency > 0.6)
		{
			ExerelinUtils.addRandomFactionShipsToCargo(stationCargo, 1, owningFaction.getFactionId(), Global.getSector());
			ExerelinUtils.addWeaponsToCargo(stationCargo, 2, owningFaction.getFactionId(), Global.getSector());
		}
		if(efficiency < 1)
			efficiency = efficiency + 0.1f;
		else if(efficiency > 1)
			efficiency = efficiency - 0.1f;
	}

	// Clear cargo and ships from station
	public void clearCargo()
	{
		stationCargo.clear();
		ExerelinUtils.removeRandomWeaponStacksFromCargo(stationCargo,  9999);
		ExerelinUtils.removeRandomShipsFromCargo(stationCargo,  9999);
	}


	private void deriveClosestEnemyTarget()
	{
		String[] enemies = owningFaction.getEnemyFactions();
		Float bestDistance = 99999999999f;
		StationRecord bestTarget = null;

		for(int i = 0; i < stationManager.getStationRecords().length; i++)
		{
			StationRecord possibleTarget = stationManager.getStationRecords()[i];

			if(possibleTarget.getStationToken().getFullName().equalsIgnoreCase(this.getStationToken().getFullName()))
				continue;

			if(stationManager.getStationRecords()[i].getOwner() == null)
			{
				Float distance = MathUtils.getDistanceSquared(possibleTarget.getStationToken(), this.getStationToken());
				if(distance < bestDistance)
				{
					bestDistance = distance;
					bestTarget = possibleTarget;
				}
			}
			else
			{
				for(int j = 0; j < enemies.length; j++)
				{
					if(stationManager.getStationRecords()[i].getOwner().getFactionId().equalsIgnoreCase(enemies[j]))
					{
						Float distance = MathUtils.getDistanceSquared(possibleTarget.getStationToken(), this.getStationToken());
						if(distance < bestDistance)
						{
							bestDistance = distance;
							bestTarget = possibleTarget;
						}
					}
				}
			}
		}
		if(targetStationRecord != null)
			targetStationRecord.stopTargeting();
		if(bestTarget != null && bestTarget.getOwner() != null)
			bestTarget.startTargeting();

		targetStationRecord = bestTarget;
	}

	private void deriveStationToAssist()
	{
		StationRecord assistStation = null;

		// Check if we are under attack first
		if(getNumAttacking() > 0)
		{
			this.assistStationRecord = this;
			return;
		}

		for(int i = 0; i < stationManager.getStationRecords().length; i++)
		{
			StationRecord possibleAssist = stationManager.getStationRecords()[i];

			if(possibleAssist.getOwner() == null)
				continue;

			if(possibleAssist.getOwner().getFactionId().equalsIgnoreCase(this.getOwner().getFactionId()) || possibleAssist.getOwner().getGameRelationship(this.getOwner().getFactionId()) >= 1)
			{
				// Check severity of attack
				if((assistStation != null && possibleAssist.numStationsTargeting > assistStation.getNumAttacking()) || (assistStation == null && possibleAssist.numStationsTargeting > 0))
					assistStation = possibleAssist;
			}
		}

		//if(assistStation != null)
		//	System.out.println(this.getStationToken().getFullName() + " is assisting" + assistStation.getStationToken().getFullName() + " which is targeted " + assistStation.getNumAttacking());

		this.assistStationRecord = assistStation;
	}

	private void deriveClosestAsteroid()
	{
		List asteroids = system.getAsteroids();
		SectorEntityToken closestAsteroid = null;
		float closestDistance = 999999999f;

		if(targetAsteroid != null && ExerelinUtils.getRandomInRange(0,2) != 0)
			return; // Don't recalc every time

		// Only check every 4th asteroid as they are fairly close normally
		for(int i = 0; i < asteroids.size(); i = i + 4)
		{
			SectorEntityToken asteroid = (SectorEntityToken)asteroids.get(i);

			Float distance = MathUtils.getDistanceSquared(this.stationToken, asteroid) ;
			if(distance < closestDistance)
			{
				closestAsteroid = asteroid;
				closestDistance = distance;
			}
		}

		targetAsteroid = closestAsteroid;
	}

	private void deriveClosestGasGiant()
	{
		List planets = system.getPlanets();
		SectorEntityToken closestPlanet = null;
		float closestDistance = 999999999f;

		if(targetGasGiant != null && ExerelinUtils.getRandomInRange(0,2) != 0)
			return; // Don't recalc each time

		for(int i = 0; i < planets.size(); i++)
		{
			SectorEntityToken planet = (SectorEntityToken)planets.get(i);

			if(!derivePlanetType(planet).equalsIgnoreCase("gas"))
				continue;

			Float distance = MathUtils.getDistanceSquared(this.stationToken,  planet) ;
			if(distance < closestDistance)
			{
				closestPlanet = planet;
				closestDistance = distance;
			}
		}
		targetGasGiant = closestPlanet;
	}

	private void setFleetTargets()
	{
		stationAttackFleetSpawn.setTarget(targetStationRecord);
		attackSpawn.setTarget(targetStationRecord, assistStationRecord);
		patrolSpawn.setDefendStation(assistStationRecord);
		inSystemSupplyConvoySpawn.setFriendlyStation(assistStationRecord);
		asteroidMiningFleetSpawnPoint.setTargetAsteroid(targetAsteroid);
		gasMiningFleetSpawnPoint.setTargetPlanet(targetGasGiant);
	}

	private String derivePlanetType(SectorEntityToken token)
	{
		String name = token.getFullName();
		if(name.contains("Gaseous") && !(name.contains(" I") || name.contains(" II") || name.contains(" III")))
			return "gas";
		if(name.contains(" I") || name.contains(" II") || name.contains(" III"))
			return "moon";
		else
			return "planet";
	}

	private void removeDeadOrRebelFleets(BaseSpawnPoint spawnPoint)
	{
		int i = 0;
		while(i < spawnPoint.getFleets().size())
		{
			CampaignFleetAPI fleet = (CampaignFleetAPI)spawnPoint.getFleets().get(i);
			if(!fleet.isAlive() || !fleet.getFaction().getId().equalsIgnoreCase(this.owningFaction.getFactionId()))
				spawnPoint.getFleets().remove(i);
			else
				i++;
		}
	}
}
