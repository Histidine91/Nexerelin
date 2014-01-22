package exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import exerelin.commandQueue.*;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsMessaging;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Random;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings("unchecked")
public class ExerelinUtils
{
	public static int getRandomInRange(int min, int max)
	{
		return min + (int)(Math.random() * ((max - min) + 1)); // hate java
	}

	// rounds up or down with closer integer having a proportionally higher chance
	public static int getRandomNearestInteger(float number)
	{
		if (number >= 0) {
			return (int)(number + Math.random());
		} else {
			return (int)(number - Math.random());
		}
	}

	public static void shuffleStringArray (String[] array)
	{
		Random rng = new Random();   // i.e., java.util.Random.
		int n = array.length;        // The number of items left to shuffle (loop invariant).
		while (n > 1)
		{
			int k = rng.nextInt(n);  // 0 <= k < n.
			n--;                     // n is now the last pertinent index;
			String temp = array[n];     // swap array[n] with array[k] (does nothing if k == n).
			array[n] = array[k];
			array[k] = temp;
		}
	}

    public static Boolean doesStringArrayContainValue(String value, String[] valuesToCheck, Boolean partialMatch)
    {
        for(int i = 0; i < valuesToCheck.length; i++)
        {
            if(partialMatch && value.contains(valuesToCheck[i]))
                return true;
            else if(value.equalsIgnoreCase(valuesToCheck[i]))
                return true;
        }

        return false;
    }

	public static SectorEntityToken getRandomOffMapPoint(LocationAPI location)
	{
		int edge = ExerelinUtils.getRandomInRange(0, 3);
		int x = 0;
		int y = 0;
		int maxSize = 0; //SectorManager.getCurrentSectorManager().getMaxSystemSize();
        if(SectorManager.getCurrentSectorManager() == null) //TODO - Fix when rework is complete or 'frame advance running before onGameLoad' bug is fixed
            maxSize = 16000;
        else
            maxSize = SectorManager.getCurrentSectorManager().getMaxSystemSize();

		int negativeMaxSize = -1 * maxSize;

		if(edge == 0)
			x = maxSize;
		else if(edge == 1)
			x = negativeMaxSize;
		else if(edge == 2)
			y = maxSize;
		else if(edge == 3)
			y = negativeMaxSize;

		if(x == 0)
			x = ExerelinUtils.getRandomInRange(negativeMaxSize, maxSize);

		if(y == 0)
			y = ExerelinUtils.getRandomInRange(negativeMaxSize, maxSize);

		return location.createToken(x, y);
	}

	public static Boolean canStationSpawnFleet(SectorEntityToken station, CampaignFleetAPI fleet, float numberToSpawn, float marinesPercent, boolean noCivilianShips, CargoAPI.CrewXPLevel crewXPLevel)
	{
		if (noCivilianShips) {
			List members = fleet.getFleetData().getMembersListCopy();

			for(int i = 0; i < members.size(); i++)	{
				FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);

				if(fmAPI.isCivilian()) {
					fleet.getFleetData().removeFleetMember(fmAPI);
				}
			}
		}

		CargoAPI stationCargo = station.getCargo();
		if(getBestFleetForStation(stationCargo, fleet, numberToSpawn))
		{
			// Recalc again
			float fleetCost = getFleetCost(fleet);

			float reqCrew     = fleetCost;
			float reqSupplies = fleetCost * 4;
			float reqFuel     = fleetCost;
			int reqMarines    = (int)(fleetCost / 2);

			// Check again just in case other changes
			if((stationCargo.getFuel() / numberToSpawn) < reqFuel || (stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) / numberToSpawn) < reqCrew || (stationCargo.getSupplies() / numberToSpawn) < reqSupplies || (stationCargo.getMarines() / numberToSpawn) < reqMarines)
				return false;
			else
			{
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandRemoveCargo(stationCargo, "regular_crew", CargoAPI.CargoItemType.RESOURCES, (int)reqCrew));
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandRemoveCargo(stationCargo, "fuel", CargoAPI.CargoItemType.RESOURCES, (int)reqFuel));
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandRemoveCargo(stationCargo, "supplies", CargoAPI.CargoItemType.RESOURCES, (int)reqSupplies));
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandRemoveCargo(stationCargo, "marines", CargoAPI.CargoItemType.RESOURCES, (int)reqMarines));
                /*decreaseCargo(stationCargo, "crewRegular", (int) reqCrew);
				decreaseCargo(stationCargo, "fuel", (int)reqFuel);
				decreaseCargo(stationCargo, "supplies", (int)reqSupplies);
				decreaseCargo(stationCargo, "marines", reqMarines);*/

				// Reset fleet cargo and put correct cargo in for fleet size otherwise accidents will occur
				CargoAPI fleetCargo = fleet.getCargo();
				fleetCargo.clear();
                ExerelinUtils.resetFleetCargoToDefaults(fleet, 1.0f - marinesPercent, marinesPercent, crewXPLevel);

				return true;
			}
		}
		else
		{
			return false;
		}
	}

	// Recursive method
	// Will remove fleet members until either 0 is left or fleet can be spawned from station
	private static Boolean getBestFleetForStation(CargoAPI stationCargo, CampaignFleetAPI fleet, float numberToSpawn)
	{
		List members = fleet.getFleetData().getMembersListCopy();

		if(members.size() == 0)
			return false;

		float fleetCost = getFleetCost(fleet);

		float reqCrew     = fleetCost;
		float reqSupplies = fleetCost * 4;
		float reqFuel     = fleetCost;
		int reqMarines    = (int)(fleetCost / 2);

		if((stationCargo.getFuel() / numberToSpawn) < reqFuel || (stationCargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) / numberToSpawn) < reqCrew || (stationCargo.getSupplies() / numberToSpawn) < reqSupplies || (stationCargo.getMarines() / numberToSpawn) < reqMarines)
		{
			if(members.size() == 1)
				return false;

			// Can't spawn, so remove random members and try again
			// Remove capital ships first
			if(isCapitalInFleet(fleet))
			{
				int toRemove = -1;
				for(int i = 0; i < members.size(); i++)
				{
					if(((FleetMemberAPI)members.get(i)).isCapital())
					{
						toRemove = i;
						break;
					}
				}

				if(toRemove != -1)
				{
					fleet.getFleetData().removeFleetMember((FleetMemberAPI)members.get(toRemove));
				}
			}
			else
			{
				int removeMembers = getRandomInRange(1, members.size());
				for(int j = 0; j < removeMembers; j = j + 1)
					fleet.getFleetData().removeFleetMember((FleetMemberAPI)members.get(getRandomInRange(0, members.size() - 1)));
			}

			return getBestFleetForStation(stationCargo, fleet, numberToSpawn);
		}
		else
		{
			// Can spawn so make sure fleet size isn't small if it has capitals
			if(fleet.getFleetData().getFleetPointsUsed() < 40 && isCapitalInFleet(fleet))
			{
				for(int l = 0; l < members.size(); l++)
				{
					if(((FleetMemberAPI)members.get(l)).isCapital())
					{
						members.remove(l);
						break;
					}
				}

				if(members.size() == 0)
					return false;
			}
			return true;
		}
	}

	private static int getFleetCost(CampaignFleetAPI fleet)
	{
		float fleetCostMult = (float) ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).baseFleetCostMultiplier;

        if(fleet.getFaction().getId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
        {
            float playerFleetCostMult = ExerelinUtilsPlayer.getPlayerFleetCostMultiplier();
            fleetCostMult = playerFleetCostMult * fleetCostMult;
        }

		float fleetCost = 0f;
		float mult;
		List members = fleet.getFleetData().getMembersListCopy();

		for (int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI ship = (FleetMemberAPI)members.get(i);

            if (ship.isCivilian()) { // superfreighters are not battleships and shouldn't cost that much
            	mult = 2f;
            } else if (ship.isFighterWing()) {
            	mult = 2f;
            } else if (ship.isFrigate()) {
            	mult = 2f;
            } else if (ship.isDestroyer()) {
            	mult = 3f;
            } else if (ship.isCruiser()) {
            	mult = 4f;
            } else if (ship.isCapital()) {
            	mult = 7f;
            } else {
            	mult = 2f;
            }

            fleetCost += (ship.getFleetPointCost() * mult);
		}

		return Math.round(fleetCost * fleetCostMult);
	}

	public static String getStationOwnerFactionId(SectorEntityToken stationToken)
	{
        return stationToken.getFaction().getId();
	}

    public static SectorEntityToken getClosestStationForFaction(String factionId, StarSystemAPI starSystemAPI, SectorEntityToken anchor)
    {
        List stations = starSystemAPI.getOrbitalStations();
        Float bestDistance = 10000000000000f;
        SectorEntityToken bestStation = anchor;

        for(int i = 0; i < stations.size(); i = i + 1)
        {
            SectorEntityToken theStation = (SectorEntityToken)stations.get(i);

            if(theStation == anchor)
                continue;

            if(theStation.getFaction().getId().equalsIgnoreCase(factionId))
            {
                Float distance = MathUtils.getDistanceSquared(anchor, theStation);
                if(distance < bestDistance)
                {
                    bestStation = theStation;
                    bestDistance = distance;
                }
            }
        }

        if(MathUtils.getDistanceSquared(anchor, bestStation) == 0)
            return null;
        else
            return bestStation;
    }

	public static SectorEntityToken getClosestEnemyStation(String targetingFaction, StarSystemAPI starSystemAPI, SectorEntityToken anchor)
	{
		List stations = starSystemAPI.getOrbitalStations();
		Float bestDistance = 10000000000000f;
		SectorEntityToken bestStation = anchor;

		for(int i = 0; i < stations.size(); i = i + 1)
		{
			SectorEntityToken theStation = (SectorEntityToken)stations.get(i);

			if(theStation.getFullName().contains("Omnifactory"))
				continue;

			if(theStation.getFullName().contains("Storage"))
				continue;

			FactionAPI stationOwner = theStation.getFaction();
			Float attackDistance = MathUtils.getDistanceSquared(anchor, theStation);

			if(stationOwner.getRelationship(targetingFaction) < 0 && bestDistance > attackDistance)
			{
				bestStation = theStation;
				bestDistance = attackDistance;
			}
		}

		if(MathUtils.getDistanceSquared(anchor, bestStation) == 0)
		{
			//System.out.println("Couldn't get station target for: " + targetingFaction);
			return null; // no available targets
		}
		else
			return bestStation;
	}

	public static SectorEntityToken getRandomStationInSystemForFaction(String factionId, StarSystemAPI starSystemAPI)
	{
		List stations = starSystemAPI.getOrbitalStations();
		SectorEntityToken theStation;
		SectorEntityToken factionStation = null;

		for(int i = 0; i < stations.size(); i = i + 1)
		{
			theStation = (SectorEntityToken)stations.get(i);

			if(theStation.getFullName().contains("Omnifactory"))
				continue; // Skip current station

			if(theStation.getFullName().contains("Storage"))
				continue; // Skip current station

			if(!theStation.getFaction().getId().equalsIgnoreCase(factionId))
				continue; // Skip current station

            if(factionStation == null || ExerelinUtils.getRandomInRange(0, 1) == 0)
                factionStation = theStation;
		}

		return factionStation;
	}

	public static void decreaseCargo(CargoAPI cargo, String type, int quantity)
	{
		if(type.equalsIgnoreCase("crewRegular"))
		{
			cargo.removeCrew(CargoAPI.CrewXPLevel.REGULAR,  quantity);
			if(cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR) < 0)
				cargo.addCrew(CargoAPI.CrewXPLevel.REGULAR, cargo.getCrew(CargoAPI.CrewXPLevel.REGULAR)*-1);
		}
		else if(type.equalsIgnoreCase("fuel"))
		{
			cargo.removeFuel(quantity) ;
			if(cargo.getFuel() < 0)
				cargo.addFuel(cargo.getFuel() * -1) ;
		}
		else if(type.equalsIgnoreCase("supplies"))
		{
			cargo.removeSupplies(quantity) ;
			if(cargo.getSupplies() < 0)
				cargo.addSupplies(cargo.getSupplies() * -1) ;
		}
		else if(type.equalsIgnoreCase("marines"))
		{
			cargo.removeMarines(quantity) ;
			if(cargo.getMarines() < 0)
				cargo.addMarines(cargo.getMarines() * -1) ;
		}
		else
		{
			System.out.println("EXERELIN ERROR: Invalid cargo type to remove: " + type);
		}
	}

	public static void renameFleet(CampaignFleetAPI fleet, String type)
	{
		String fleetTypeName = "";
		float fleetSize = fleet.getFleetData().getFleetPointsUsed();

        //System.out.println("Renaming fleet for: " + fleet.getFaction().getId());

		if(type.equalsIgnoreCase("attack"))
		{
			if(fleetSize < 40)
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).smallAttackFleetName;
			else if (fleetSize < 90)
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).mediumAttackFleetName;
			else
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).largeAttackFleetName;
		}
		else if(type.equalsIgnoreCase("defense"))
		{
			if(fleetSize < 40)
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).smallDefenceFleetName;
			else if (fleetSize < 90)
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).mediumDefenceFleetName;
			else
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).largeDefenceFleetName;
		}
		else if(type.equalsIgnoreCase("patrol"))
		{
			if(fleetSize < 40)
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).smallPatrolFleetName;
			else if (fleetSize < 90)
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).mediumPatrolFleetName;
			else
				fleetTypeName = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId()).largePatrolFleetName;
		}
		else
		{
			System.out.println("EXERELIN ERROR: Invalid fleet type to rename: " + type);
		}
		fleet.setName(fleetTypeName);
	}

	public static void addWeaponsToCargo(CargoAPI cargo, int count, String factionId, SectorAPI sector)
	{
        String[] factionWeapons = ExerelinUtils.getFactionWeaponListFromGenericFleet(factionId);
		int maxQuantityInStack = 4;

		List weapons = cargo.getWeapons();
		if(weapons.size() > 30)
			removeRandomWeaponStacksFromCargo(cargo, weapons.size() - 25);


		if(factionWeapons.length > 0)
		{
			for(int i = 0; i < count; i = i + 1)
			{
				String weaponId = factionWeapons[ExerelinUtils.getRandomInRange(0, factionWeapons.length - 1)];
				//cargo.addWeapons(weaponId, ExerelinUtils.getRandomInRange(1, maxQuantityInStack));
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddWeapon(cargo, weaponId, ExerelinUtils.getRandomInRange(1, maxQuantityInStack)));
			}
		}
		else
		{
			addRandomWeapons(cargo, count, sector);
		}
	}

	private static void addRandomWeapons(CargoAPI cargo, int count, SectorAPI sector)
	{
		List weaponIds = sector.getAllWeaponIds();
		for (int i = 0; i < count; i++) {
			String weaponId = (String) weaponIds.get((int) (weaponIds.size() * Math.random()));
			int quantity = 3;
			//cargo.addWeapons(weaponId, quantity);
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddWeapon(cargo, weaponId, quantity));
		}
	}

    private static String[] getFactionWeaponListFromGenericFleet(String factionId)
    {
        ArrayList weaponList;
        CampaignFleetAPI dummyFleet = Global.getSector().createFleet(factionId, "exerelinGenericFleet");
        List members = dummyFleet.getFleetData().getMembersListCopy();

        FleetMemberAPI fleetMemberAPI = null;

        int attempts = 0;
        while((fleetMemberAPI == null || fleetMemberAPI.isFighterWing()) && attempts < 20)
        {
            fleetMemberAPI = (FleetMemberAPI)members.get(ExerelinUtils.getRandomInRange(0, members.size() - 1));
            attempts++;
        }

        if(fleetMemberAPI == null)
            return new String[]{};

        List weaponSlots = fleetMemberAPI.getVariant().getNonBuiltInWeaponSlots();

        weaponList = new ArrayList(weaponSlots.size());
        for(int i = 0; i < weaponSlots.size(); i++)
            weaponList.add(fleetMemberAPI.getVariant().getWeaponId((String)weaponSlots.get(i)));


        return (String[])weaponList.toArray(new String[weaponList.size()]);
    }

	public static void addRandomFactionShipsToCargo(CargoAPI cargo, int count, String factionId, SectorAPI sector, boolean allowToreUpPlenty)
	{
		int r = getRandomInRange(0, 30);
		CampaignFleetAPI fleet;

		if(r == 0)
			fleet = sector.createFleet(factionId, "exerelinGenericFleet");
		else if(r == 1)
			fleet = sector.createFleet(factionId, "exerelinAsteroidMiningFleet");
		else if(r == 2)
			fleet = sector.createFleet(factionId, "exerelinGasMiningFleet");
        else if(r == 3 || r == 4)
            fleet = sector.createFleet(factionId, "exerelinInSystemStationAttackFleet");
        else if(r == 5)
            fleet = sector.createFleet(factionId, "exerelinInSystemSupplyConvoy");
        else if(r == 6)
        {
            // Add from common list
            for(int i = 0; i < count; i++)
            {
                String shipId = ExerelinConfig.commonShipList[ExerelinUtils.getRandomInRange(0, ExerelinConfig.commonShipList.length - 1)];
                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddShip(cargo, FleetMemberType.SHIP, shipId, null));
            }
            return;
        }
        else if ((r == 7 || r == 8) && ExerelinUtilsPlayer.getPlayerEliteShipConstruction())
        {
            fleet = sector.createFleet(factionId, "exerelinEliteFleet");
        }
        else if ((r == 9 || r == 10) && ExerelinUtils.isToreUpPlentyInstalled())
        {
            // Add from Tore Up Plenty
            fleet = sector.createFleet("scavengers", "exerelinGenericFleet");
        }
		else
			fleet = sector.createFleet(factionId, "exerelinGenericFleet");


		/*List ships = cargo.getMothballedShips().getMembersListCopy();
		if(ships.size() > 25)
			removeRandomShipsFromCargo(cargo,  ships.size() - 22);*/

		for(int i = 0; i < count; i = i + 1)
		{
			int memberToGet = ExerelinUtils.getRandomInRange(0, fleet.getFleetData().getMembersListCopy().size() - 1);
			FleetMemberAPI fmAPI = (FleetMemberAPI)fleet.getFleetData().getMembersListCopy().get(memberToGet);
			if(fmAPI.isCapital() && !ExerelinUtilsPlayer.getPlayerCapitalShipAvailability())
			{
				// Get another one to reduce chance of capitals
				memberToGet = ExerelinUtils.getRandomInRange(0, fleet.getFleetData().getMembersListCopy().size() - 1);
				fmAPI = (FleetMemberAPI)fleet.getFleetData().getMembersListCopy().get(memberToGet);
			}

			String shipId = "";

			if(fmAPI.getType() == FleetMemberType.FIGHTER_WING)
                shipId = fmAPI.getSpecId();
			else if (fmAPI.getType() == FleetMemberType.SHIP)
				shipId = fmAPI.getHullId() + "_Hull";
			else
				return;

            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddShip(cargo, fmAPI.getType(), shipId, null));
		}
	}

	public static void addRandomEscortShipsToFleet (CampaignFleetAPI campaignFleet, int minCount, int maxCount, String factionId, SectorAPI sector)
	{
		List members = campaignFleet.getFleetData().getMembersListCopy();
		float minSpeed = 1000f;

		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			float speed = fmAPI.getStats().getMaxSpeed().getModifiedValue();

			if (minSpeed > speed) {
				minSpeed = speed;
			}
		}

		minSpeed -= (minSpeed / 8f + 5f);

		CampaignFleetAPI dummyFleet;
		float[] weights;
		float totalWeight = 0.f;

		do
		{
			dummyFleet = sector.createFleet(factionId, "exerelinGenericFleet");

			members = dummyFleet.getFleetData().getMembersListCopy();
			weights = new float[ members.size() ];
			int m = 0;

			for(Iterator it = members.iterator(); it.hasNext(); )
			{
				FleetMemberAPI fmAPI = (FleetMemberAPI)it.next();

				if (fmAPI.isCapital() || fmAPI.getStats().getMaxSpeed().getModifiedValue() < minSpeed) {
					it.remove();
					continue;
				} else if (fmAPI.isFighterWing()) {
					weights[m] = 1.2f;
				} else if (fmAPI.isFrigate()) {
					weights[m] = 1.2f;
				} else if (fmAPI.isDestroyer()) {
					weights[m] = 0.7f;
				} else if (fmAPI.isCruiser()) {
					weights[m] = 0.2f;
				}

				totalWeight += weights[m];
				m++;
			}
		} while (totalWeight == 0.f); // repeat until dummyFleet contains at least one valid escort ship

		FleetDataAPI fleetData = campaignFleet.getFleetData();
		int count = ExerelinUtils.getRandomInRange(minCount, maxCount);

		for(int i = 0; i < count; )
		{
			float randomWeight = (float)Math.random() * totalWeight;

			int m = 0;
			while (randomWeight >= weights[m])
			{
				randomWeight -= weights[m];
				m++;
			}

			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(m);
			FleetMemberType memberType = fmAPI.getType();
			String shipId = fmAPI.getSpecId();

			if (shipId == null) continue;

			fleetData.addFleetMember( Global.getFactory().createFleetMember(memberType, shipId) );

			i++;
		}
	}

	public static void removeRandomShipsFromCargo(CargoAPI cargoAPI, int numToRemove)
	{
		List ships = cargoAPI.getMothballedShips().getMembersListCopy();
        int totalToRemove = Math.min(numToRemove, ships.size());
		for(int j = 0; j < totalToRemove; j++)
		{
            int toRemove = ExerelinUtils.getRandomInRange(0, cargoAPI.getMothballedShips().getMembersListCopy().size() - 1);
            //cargoAPI.getMothballedShips().removeFleetMember((FleetMemberAPI)cargoAPI.getMothballedShips().getMembersListCopy().get(toRemove));
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandRemoveShip(cargoAPI, (FleetMemberAPI)cargoAPI.getMothballedShips().getMembersListCopy().get(toRemove)));
		}
	}

	public static void removeRandomWeaponStacksFromCargo(CargoAPI cargoAPI, int numToRemove)
	{
		List weapons = cargoAPI.getWeapons();

		for(int j = 0; j < Math.min(weapons.size(), numToRemove); j++)
		{
            CargoAPI.CargoItemQuantity weapon = (CargoAPI.CargoItemQuantity)weapons.get(j);
			//cargoAPI.removeItems(CargoAPI.CargoItemType.WEAPONS, weapon.getItem(), weapon.getCount());
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandRemoveCargo(cargoAPI, weapon.getItem(), CargoAPI.CargoItemType.WEAPONS, weapon.getCount()));
		}
	}

	public static boolean isValidMiningFleet(CampaignFleetAPI fleet)
	{
		List members = fleet.getFleetData().getMembersListCopy();
		Boolean hasMiningShip = false;
		Boolean hasShip = false;
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			if((! fmAPI.isMothballed()) && ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinConfig.validMiningShips, true))
				hasMiningShip = true;
			else if(!fmAPI.isFighterWing())
				hasShip = true;
		}

		return (hasMiningShip && hasShip);
	}

	public static int getMiningPower(CampaignFleetAPI fleet)
	{
		int power = 0;

		List members = fleet.getFleetData().getMembersListCopy();
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			if((! fmAPI.isMothballed()) && ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinConfig.validMiningShips, true))
				power = power + 1;
		}

		return power;
	}

    // Check if a fleet is a valid boarding fleet
	public static boolean isValidBoardingFleet(CampaignFleetAPI fleet, Boolean checkForTroopTransport)
	{
		List members = fleet.getFleetData().getMembersListCopy();

        if(members.size() < 3)
            return false; // Must be 1 flagship, 1 transport, 1 other ship

		Boolean hasValidFlagship = false;
        Boolean hasValidTroopTransport = checkForTroopTransport;

		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);

            if((! fmAPI.isMothballed()) && ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinConfig.validBoardingFlagships, true))
				hasValidFlagship = true;

            if((! fmAPI.isMothballed()) && ExerelinUtils.doesStringArrayContainValue(fmAPI.getSpecId(), ExerelinConfig.validTroopTransportShips, true))
                hasValidTroopTransport = true;

            if(hasValidFlagship && hasValidTroopTransport)
                break;
		}

		return (hasValidFlagship && hasValidTroopTransport);
	}

	private static boolean isCapitalInFleet(CampaignFleetAPI fleet)
	{
		List members = fleet.getFleetData().getMembersListCopy();
		for(int i = 0; i < members.size(); i++)
		{
			FleetMemberAPI fmAPI = (FleetMemberAPI)members.get(i);
			if((! fmAPI.isMothballed()) && fmAPI.isCapital())
			{
				return true;
			}
		}

		return false;
	}

	public static void handlePlayerFleetMining(CampaignFleetAPI playerFleet)
	{

        SectorEntityToken interactionTarget =  SectorManager.getCurrentSectorManager().getLastInteractionToken();
        if(interactionTarget == null)
            return; // Not interacting with anything

        if(playerFleet.isInHyperspace())
            return; // Nothing to mine in hyperspace, although that would be COOL

        if(!ExerelinUtils.isValidMiningFleet(playerFleet))
            return; // Not a mining fleet

        int distanceAllowable = 0;
        if(interactionTarget instanceof AsteroidAPI)
            distanceAllowable = 2500;
        else if(interactionTarget instanceof PlanetAPI)
            distanceAllowable = 7000;


        if(MathUtils.getDistanceSquared(interactionTarget.getLocation(), playerFleet.getLocation()) < distanceAllowable)
        {
            int miningPower = getMiningPower(playerFleet);
            if(miningPower > 0)
            {
                if(interactionTarget instanceof AsteroidAPI)
                {
                    playerFleet.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, ExerelinConfig.asteroidMiningResource, ExerelinConfig.miningAmountPerDayPerMiner*miningPower);
                    ExerelinUtilsMessaging.addMessage("Mined " + ExerelinConfig.miningAmountPerDayPerMiner * miningPower + " " + ExerelinConfig.asteroidMiningResource, Color.green);
                    Global.getSector().getPlayerFleet().getCommanderStats().addXP(400);
                }
                else if(interactionTarget instanceof PlanetAPI && ((PlanetAPI)interactionTarget).isGasGiant())
                {
                    playerFleet.getCargo().addItems(CargoAPI.CargoItemType.RESOURCES, ExerelinConfig.gasgiantMiningResource, ExerelinConfig.miningAmountPerDayPerMiner*miningPower);
                    ExerelinUtilsMessaging.addMessage("Mined " + ExerelinConfig.miningAmountPerDayPerMiner*miningPower + " " + ExerelinConfig.gasgiantMiningResource, Color.green);
                    Global.getSector().getPlayerFleet().getCommanderStats().addXP(400);
                }
                Global.getSector().getPlayerFleet().getCommanderStats().levelUpIfNeeded();
            }
        }
	}

	public static void populateStartingStorageFacility(SectorEntityToken storageFacility)
	{
		CargoAPI cargo = storageFacility.getCargo();
		cargo.addItems(CargoAPI.CargoItemType.RESOURCES, "agent", 2);
		cargo.addItems(CargoAPI.CargoItemType.RESOURCES, "prisoner", 2);
        cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, "mining_drone_wing"));
	}

    public static void addEliteShipToFleet(CampaignFleetAPI fleet)
    {
        try
        {
            CampaignFleetAPI eliteFleet = Global.getSector().createFleet(fleet.getFaction().getId(), "exerelinEliteFleet");
            if(eliteFleet.getFleetData().getMembersListCopy().size() > 0)
            {
                int memberToAdd = getRandomInRange(0, eliteFleet.getFleetData().getMembersListCopy().size() - 1);
                System.out.println("Adding eliteFleet ship:" + ((FleetMemberAPI)eliteFleet.getFleetData().getMembersListCopy().get(memberToAdd)).getSpecId() + " to " + fleet.getName());
                fleet.getFleetData().addFleetMember((FleetMemberAPI)eliteFleet.getFleetData().getMembersListCopy().get(memberToAdd));
            }
        }
        catch(Exception e)
        {
            System.out.println("EXERELIN ERROR (eliteFleetAdd): " + e.getMessage());
            // Elite fleet not setup for this faction
        }
    }

    public static void handlePlayerBoarding(CampaignFleetAPI playerFleet)
    {
        // Check player isn't in hyperspace
        if(playerFleet.isInHyperspace())
            return;

        // Check player fleet composition
        if(!ExerelinUtils.isValidBoardingFleet(playerFleet, true))
            return;

        StarSystemAPI starSystemAPI = (StarSystemAPI)playerFleet.getContainingLocation();
        SystemManager systemManager = SystemManager.getSystemManagerForAPI(starSystemAPI);

        // Get factionId of station at XY coords (if exists)
        SectorEntityToken possibleBoardTarget = systemManager.getStationTokenForXY(playerFleet.getLocation().getX(), playerFleet.getLocation().getY(), 60);
        if(possibleBoardTarget == null)
            return;

        // Get owner faction id
        String possibleBoardTargetFactionId = ExerelinUtils.getStationOwnerFactionId(possibleBoardTarget);
        if(possibleBoardTargetFactionId.equalsIgnoreCase(""))
            return;

        // Check if at war or station is abandonded
        if(Global.getSector().getFaction(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getRelationship(possibleBoardTargetFactionId) >= 0)
            return;

        // Attempt to takeover station
        if(ExerelinUtils.boardStationAttempt(playerFleet, possibleBoardTarget, true, false))
        {
            if(!SectorManager.getCurrentSectorManager().isFactionInSector(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
            {
                // First station takeover so also remove extra transport
                ExerelinUtils.removeShipsFromFleet(playerFleet, ExerelinConfig.validTroopTransportShips, false, false);
                ExerelinUtils.resetFleetCargoToDefaults(playerFleet, 0.1f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(playerFleet.getFaction().getId()));

                possibleBoardTarget.getCargo().addCrew(CargoAPI.CrewXPLevel.REGULAR, 200);
                possibleBoardTarget.getCargo().addMarines(100);
                possibleBoardTarget.getCargo().addFuel(200);
                possibleBoardTarget.getCargo().addSupplies(800);
                ExerelinUtils.addRandomFactionShipsToCargo(possibleBoardTarget.getCargo(), 2, playerFleet.getFaction().getId(), Global.getSector(), false);
                ExerelinUtils.addWeaponsToCargo(possibleBoardTarget.getCargo(), 2, playerFleet.getFaction().getId(), Global.getSector());

                // Reset faction home system for player
                FactionDirector.getFactionDirectorForFactionId(SectorManager.getCurrentSectorManager().getPlayerFactionId()).setHomeSystem((StarSystemAPI)Global.getSector().getPlayerFleet().getContainingLocation());
            }
            systemManager.setStationOwner(possibleBoardTarget, SectorManager.getCurrentSectorManager().getPlayerFactionId(), true, true);
        }
    }

    public static void removeShipsFromFleet(CampaignFleetAPI fleet, String[] shipTypes, Boolean limitToOneRemove, boolean destructionFlash)
    {
        List members = fleet.getFleetData().getMembersListCopy();

        for(int i = 0; i < shipTypes.length; i++)
        {
            for(int j = 0; j < members.size(); j++)
            {
                if(((FleetMemberAPI)members.get(j)).getSpecId().contains(shipTypes[i]))
                {
                    if(destructionFlash)
                        fleet.removeFleetMemberWithDestructionFlash((FleetMemberAPI)members.get((j)));
                    else
                        fleet.getFleetData().removeFleetMember((FleetMemberAPI) members.get((j)));
                    if(limitToOneRemove)
                        return;
                }
            }
        }
    }

    // Play out a station boarding attempt
    // Returns true if board successful, false if not
    // Subsequent code must handle any station ownership changes etc.
    public static boolean boardStationAttempt(CampaignFleetAPI fleet, SectorEntityToken station, Boolean playerFleet, Boolean resetCargo)
    {
        final int NORMAL_DICE_ROLL_MAX = 12;

        int marinesDefending = station.getCargo().getMarines();
        int marinesAttacking = fleet.getCargo().getMarines();

        // Let station record know it is being boarded
        StarSystemAPI starSystemAPI = (StarSystemAPI)fleet.getContainingLocation();
        SystemManager systemManager = SystemManager.getSystemManagerForAPI(starSystemAPI);
        systemManager.getSystemStationManager().getStationRecordForToken(station).setIsBeingBoarded(true);

        while(marinesDefending > 0 && marinesAttacking > 0)
        {
            int attackRoll = 0;
            if(fleet.getCommanderStats().getSkillLevel("advanced_tactics") > 5 )
                attackRoll = ExerelinUtils.getRandomInRange(1, NORMAL_DICE_ROLL_MAX*2);
            else
                attackRoll = ExerelinUtils.getRandomInRange(1, NORMAL_DICE_ROLL_MAX);

            int defendRoll = ExerelinUtils.getRandomInRange(1, NORMAL_DICE_ROLL_MAX);

            if(attackRoll > defendRoll)
                marinesDefending = marinesDefending - (attackRoll - defendRoll);
            else
                marinesAttacking = marinesAttacking - ((defendRoll - attackRoll) + 1);

            if(ExerelinUtils.getRandomInRange(0, 30) == 0)
                break;
        }

        // Report if player fleet
        if(playerFleet)
        {
            if(fleet.getCargo().getMarines() > marinesAttacking)
            {
                ExerelinUtilsMessaging.addMessage("Your fleet lost " + (fleet.getCargo().getMarines() - marinesAttacking) + " marines assualting the station", Color.green);
            }
            if(marinesDefending <= 0)
            {
                ExerelinUtilsMessaging.addMessage("Your fleet successfully boarded " + station.getName(), Color.green);
            }
        }

        if(station.getCargo().getMarines() > marinesDefending)
            ExerelinUtils.decreaseCargo(station.getCargo(), "marines", station.getCargo().getMarines() - marinesDefending);

        if(fleet.getCargo().getMarines() > marinesAttacking)
            ExerelinUtils.decreaseCargo(fleet.getCargo(), "marines", fleet.getCargo().getMarines() - marinesAttacking);

        if(marinesDefending <= 0)
        {
            // Attackers won
            ExerelinUtils.removeShipsFromFleet(fleet, ExerelinConfig.validBoardingFlagships, true, false);
            if(resetCargo)
                ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.1f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleet.getFaction().getId()));
            else
                ExerelinUtils.decreaseCargo(fleet.getCargo(), "marines", fleet.getCargo().getMarines());
            return true;
        }
        else
        {
            // Defenders won
            if(fleet.getCargo().getMarines() <= 0)
            {
                // Defenders total win
                ExerelinUtils.removeShipsFromFleet(fleet, ExerelinConfig.validBoardingFlagships, true, true);
                ExerelinUtils.removeShipsFromFleet(fleet, ExerelinConfig.validTroopTransportShips, false, true);
                if(resetCargo)
                    ExerelinUtils.resetFleetCargoToDefaults(fleet, 0.1f, 0.1f, ExerelinUtils.getCrewXPLevelForFaction(fleet.getFaction().getId()));
                else
                    ExerelinUtils.decreaseCargo(fleet.getCargo(), "marines", fleet.getCargo().getMarines());

                if(playerFleet)
                {
                    ExerelinUtilsMessaging.addMessage("Your fleet has failed to capture station and has suffered extensive losses", Color.green);
                }
            }
            return false;
        }
    }

    // Defaults a exerelin.fleets cargo to acceptable ranges
    // Useful after changing a fleet composition
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

    // Returns a factions crew xp level
    public static CargoAPI.CrewXPLevel getCrewXPLevelForFaction(String factionId)
    {
        if(factionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
        {
            float crewUpgradeChance = ExerelinUtilsPlayer.getPlayerFactionFleetCrewExperienceBonus() + (float)ExerelinConfig.getExerelinFactionConfig(factionId).crewExpereinceLevelIncreaseChance;
            if(ExerelinUtils.getRandomInRange(0, 99) <= -1 + crewUpgradeChance*100)
                return CargoAPI.CrewXPLevel.VETERAN;
        }
        else if(ExerelinConfig.getExerelinFactionConfig(factionId).crewExpereinceLevelIncreaseChance > 0)
        {
            if(ExerelinUtils.getRandomInRange(0, 99) <= -1 + ExerelinConfig.getExerelinFactionConfig(factionId).crewExpereinceLevelIncreaseChance*100)
                return CargoAPI.CrewXPLevel.VETERAN;
        }

        return CargoAPI.CrewXPLevel.REGULAR;
    }

    public static void mergeFleets(CampaignFleetAPI mainFleet, CampaignFleetAPI fleetToMerge)
    {
        List members = fleetToMerge.getFleetData().getMembersListCopy();
        for(int i = 0; i < members.size(); i = i + 1)
            mainFleet.getFleetData().addFleetMember((FleetMemberAPI)members.get((i)));
    }

    public static boolean doesSystemHaveEntityForFaction(StarSystemAPI system, String factionId, float minRelationship, float maxRelationship)
    {
        //System.out.println("Checking: " + system.getName() + " for min: " + minRelationship + ", max: " + maxRelationship);
        for(int i = 0; i < system.getOrbitalStations().size(); i++)
        {
            SectorEntityToken station = (SectorEntityToken)system.getOrbitalStations().get(i);
            float relationship = station.getFaction().getRelationship(factionId);
            //System.out.println("   Checking: " + station.getName() + ", Relationship: " + relationship);
            if((relationship <= maxRelationship && relationship >= minRelationship)
                    || (minRelationship >= 1 && factionId.equalsIgnoreCase(station.getFaction().getId())))
                return true;
        }

        return false;
    }

    public static float getDistanceBetweenSystems(StarSystemAPI system, StarSystemAPI otherSystem)
    {
        return MathUtils.getDistanceSquared(system.getLocation(), otherSystem.getLocation());
    }

    public static StarSystemAPI getClosestSystemForFaction(StarSystemAPI system, String factionId, float minRelationship, float maxRelationship)
    {
        float bestDistance = 99999999999f;
        StarSystemAPI bestSystem = null;

        for(int i = 0; i < Global.getSector().getStarSystems().size(); i++)
        {
            StarSystemAPI potentialSystem = (StarSystemAPI)Global.getSector().getStarSystems().get(i);

            if(potentialSystem.getName().equalsIgnoreCase(system.getName()))
                continue; // Don't find intitial system

            if(!ExerelinUtils.doesSystemHaveEntityForFaction(potentialSystem, factionId, minRelationship, maxRelationship))
                continue; // If searching for war target or friendly target

            float potentialDistance = ExerelinUtils.getDistanceBetweenSystems(system, potentialSystem);
            if(potentialDistance < bestDistance)
            {
                bestSystem = potentialSystem;
                bestDistance = potentialDistance;
            }
        }

        return bestSystem;
    }

    public static StarSystemAPI getClosestSystemWithFaction(StarSystemAPI system, String factionId)
    {
        float bestDistance = 99999999999f;
        StarSystemAPI bestSystem = null;

        for(int i = 0; i < Global.getSector().getStarSystems().size(); i++)
        {
            StarSystemAPI potentialSystem = (StarSystemAPI)Global.getSector().getStarSystems().get(i);

            if(potentialSystem.getName().equalsIgnoreCase(system.getName()))
                continue; // Don't find intitial system

            if(!ExerelinUtils.isFactionPresentInSystem(factionId, potentialSystem))
                continue; // Faction is not present in system

            float potentialDistance = ExerelinUtils.getDistanceBetweenSystems(system, potentialSystem);
            if(potentialDistance < bestDistance)
            {
                bestSystem = potentialSystem;
                bestDistance = potentialDistance;
            }
        }

        return bestSystem;
    }

    public static SectorEntityToken getClosestEntityToSystemEntrance(StarSystemAPI system, String factionId, float minRelationship, float maxRelationship)
    {
        if(system.getHyperspaceAnchor() == null)
            return null;

        Vector2f jumpLoc = system.getHyperspaceAnchor().getLocation();

        float bestDistance = 99999999999f;
        SectorEntityToken bestStation = null;

        for(int i = 0; i < system.getOrbitalStations().size(); i++)
        {
            SectorEntityToken potentialStation = (SectorEntityToken)system.getOrbitalStations().get(i);
            float relationship = potentialStation.getFaction().getRelationship(factionId);

            if((relationship <= maxRelationship && relationship >= minRelationship)
                    || (minRelationship >= 1 && factionId.equalsIgnoreCase(potentialStation.getFaction().getId())))
            {
                float potentialDistance = MathUtils.getDistanceSquared(jumpLoc, potentialStation.getLocation());
                if(potentialDistance < bestDistance)
                {
                    bestDistance = potentialDistance;
                    bestStation = potentialStation;
                }

            }
        }

        return bestStation;
    }

    public static void addFreightersToFleet(CampaignFleetAPI fleet)
    {
        CampaignFleetAPI dummyFleet = Global.getSector().createFleet(fleet.getFaction().getId(), "exerelinInSystemSupplyConvoy");
        int targetFleetSize;

        if(fleet.getFleetData().getFleetPointsUsed() < 40)
            targetFleetSize = 1;
        else if(fleet.getFleetData().getFleetPointsUsed() < 90)
            targetFleetSize = 2;
        else
            targetFleetSize = 3;

        while(dummyFleet.getFleetData().getMembersListCopy().size() > targetFleetSize)
            dummyFleet.getFleetData().removeFleetMember((FleetMemberAPI)dummyFleet.getFleetData().getMembersListCopy().get(0));

        mergeFleets(fleet, dummyFleet);
}

    public static boolean isPlayerInSystem(StarSystemAPI starSystemAPI)
    {
        if(Global.getSector().getPlayerFleet().isInHyperspace())
            return false;

        return ((StarSystemAPI)Global.getSector().getPlayerFleet().getContainingLocation()).getName().equalsIgnoreCase(starSystemAPI.getName());
    }

    public static boolean isFactionPresentInSystem(String factionId, StarSystemAPI starSystemAPI)
    {
        for(int i = 0; i < starSystemAPI.getOrbitalStations().size(); i++)
        {
            if(((SectorEntityToken)starSystemAPI.getOrbitalStations().get(i)).getFaction().getId().equalsIgnoreCase(factionId))
                return true;
        }
        return false;
    }

    public static boolean isToreUpPlentyInstalled()
    {
        try
        {
            Global.getSettings().getScriptClassLoader().loadClass("data.scripts.TUPModPlugin");
            return true;
        }
        catch (ClassNotFoundException ex)
        {
            return false;
        }
    }
}
