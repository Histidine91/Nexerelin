package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings("unchecked")
public class ExerelinUtils
{
	// use LazyLib's MathUtils.getRandomNumberInRange() instead
	@Deprecated
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

	public static Object getRandomArrayElement(Object[] array)
	{
		if (array.length == 0)
			return null;

		int randomIndex = MathUtils.getRandomNumberInRange(0, array.length - 1);

		return array[randomIndex];
	}

	public static Object getRandomListElement(List list)
	{
		if (list.isEmpty())
			return null;

		int randomIndex = MathUtils.getRandomNumberInRange(0, list.size() - 1);

		return list.get(randomIndex);
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

	// FIXME
	@Deprecated//
	public static SectorEntityToken getRandomOffMapPoint(LocationAPI location)
	{
		int edge = ExerelinUtils.getRandomInRange(0, 3);
		int x = 0;
		int y = 0;
		int maxSize = 16000;	//0; //SectorManager.getCurrentSectorManager().getMaxSystemSize();
        //if(SectorManager.getCurrentSectorManager() == null) //TODO - Fix when rework is complete or 'frame advance running before onGameLoad' bug is fixed
        //    maxSize = 16000;
        //else
        //    maxSize = SectorManager.getCurrentSectorManager().getMaxSystemSize();

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

	public static String getStationOwnerFactionId(SectorEntityToken stationToken)
	{
        return stationToken.getFaction().getId();
	}

    public static MarketAPI getClosestMarket(String factionId)
    {
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        FactionAPI faction = Global.getSector().getFaction(factionId);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        
        MarketAPI closestMarket = null;
        float closestDist = 999999f;
        Vector2f playerLoc = playerFleet.getLocationInHyperspace();
        for (MarketAPI market : markets)
        {
            float dist = Misc.getDistance(market.getLocationInHyperspace(), playerLoc);
            if (dist < closestDist && market.getFaction() == faction)
            {
                closestMarket = market;
                closestDist = dist;
            }
        }
        return closestMarket;
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
	
	// FIXME: Returns a factions crew xp level
	@Deprecated
    public static CargoAPI.CrewXPLevel getCrewXPLevelForFaction(String factionId)
    {
        if(factionId.equalsIgnoreCase(PlayerFactionStore.getPlayerFactionId()))
        {
            float crewUpgradeChance = 0;	//ExerelinUtilsPlayer.getPlayerFactionFleetCrewExperienceBonus() + (float)ExerelinConfig.getExerelinFactionConfig(factionId).crewExpereinceLevelIncreaseChance;
            if(MathUtils.getRandomNumberInRange(0, 99) <= -1 + crewUpgradeChance*100)
                return CargoAPI.CrewXPLevel.VETERAN;
        }
        else if(ExerelinConfig.getExerelinFactionConfig(factionId).crewExpereinceLevelIncreaseChance > 0)
        {
            if(MathUtils.getRandomNumberInRange(0, 99) <= -1 + ExerelinConfig.getExerelinFactionConfig(factionId).crewExpereinceLevelIncreaseChance*100)
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

	// who knows what this does
	public static boolean doesSystemHaveEntityForFaction(StarSystemAPI system, String factionId, float minRelationship, float maxRelationship)
    {
        //System.out.println("Checking: " + system.getName() + " for min: " + minRelationship + ", max: " + maxRelationship);
        for (MarketAPI market : Misc.getMarketsInLocation(system))
        {
            float relationship = market.getFaction().getRelationship(factionId);
            //System.out.println("   Checking: " + station.getName() + ", Relationship: " + relationship);
            if((relationship <= maxRelationship && relationship >= minRelationship)
                    || (minRelationship >= 1 && factionId.equalsIgnoreCase(market.getFaction().getId())))
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
	
    public static boolean isSSPInstalled()
    {
        try
        {
            Global.getSettings().getScriptClassLoader().loadClass("data.scripts.SSPModPlugin");
            return true;
        }
        catch (ClassNotFoundException ex)
        {
            return false;
        }
    }
    
    public static String[] JSONArrayToStringArray(JSONArray jsonArray)
    {
        try
        {
            return jsonArray.toString().substring(1, jsonArray.toString().length() - 1).replaceAll("\"","").split(",");
        }
        catch(Exception e)
        {
            return new String[]{};
        }
    }
    
    public static ArrayList<String> JSONArrayToArrayList(JSONArray jsonArray)
    {
        try
        {
            ArrayList<String> ret = new ArrayList<>();
            for (int i=0; i<jsonArray.length(); i++)
            {
                ret.add(jsonArray.getString(i));
            }
            return ret;
        }
        catch(Exception e)
        {
            return new ArrayList<>();
        }
    }
    
    public static Map jsonToMap(JSONObject json) throws JSONException {
        Map<String, Object> retMap = new HashMap<>();

        if(json != JSONObject.NULL) {
            retMap = toMap(json);
        }
        return retMap;
    }

    public static Map toMap(JSONObject object) throws JSONException {
        Map<String, Object> map = new HashMap<>();

        Iterator<String> keysItr = object.keys();
        while(keysItr.hasNext()) {
            String key = keysItr.next();
            Object value = object.get(key);

            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            map.put(key, value);
        }
        return map;
    }

    public static List toList(JSONArray array) throws JSONException {
        List<Object> list = new ArrayList<>();
        for(int i = 0; i < array.length(); i++) {
            Object value = array.get(i);
            if(value instanceof JSONArray) {
                value = toList((JSONArray) value);
            }

            else if(value instanceof JSONObject) {
                value = toMap((JSONObject) value);
            }
            list.add(value);
        }
        return list;
    }
    
    
}
