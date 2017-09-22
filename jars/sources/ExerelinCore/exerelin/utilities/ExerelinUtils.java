package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.plugins.ExerelinModPlugin;
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
	public static long getStartingSeed()
	{
		String seedStr = Global.getSector().getSeedString().replaceAll("[^0-9]", "");
		return Long.parseLong(seedStr);
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
	
	public static Object getRandomListElement(List list, Random rand)
	{
		if (list.isEmpty())
			return null;

		int randomIndex = rand.nextInt(list.size());

		return list.get(randomIndex);
	}
		
	public static float lerp(float x, float y, float alpha) {
		return (1f - alpha) * x + alpha * y;
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

	public static String[] JSONArrayToStringArray(JSONArray jsonArray)
    {
        try
        {
            String[] ret = new String[jsonArray.length()];
            for (int i=0; i<jsonArray.length(); i++)
            {
                ret[i] = jsonArray.getString(i);
            }
            return ret;
        }
        catch(Exception e)
        {
            Global.getLogger(ExerelinFactionConfig.class).warn(e);
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
		catch(Exception e) { }
		return new ArrayList<>();
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
	
	// invented by DarkRevenant
	// see DynaSector mod plugin for example
	public static void removeScriptAndListener(SectorEntityToken entity, Class<?> oldClass, Class<?> newClass)
	{
		CampaignEventListener listener = null;
        for (CampaignEventListener l : Global.getSector().getAllListeners()) {
            if (oldClass.isInstance(l) && (newClass == null || !newClass.isInstance(l))) {
                listener = l;
                break;
            }
        }
        if (listener != null) {
            Global.getSector().removeListener(listener);
        }
		entity.removeScriptsOfClass(oldClass);
	}
	
	public static void removeScriptAndListener(LocationAPI loc, Class<?> oldClass, Class<?> newClass)
	{
		CampaignEventListener listener = null;
        for (CampaignEventListener l : Global.getSector().getAllListeners()) {
            if (oldClass.isInstance(l) && (newClass == null || !newClass.isInstance(l))) {
                listener = l;
                break;
            }
        }
        if (listener != null) {
            Global.getSector().removeListener(listener);
        }
		loc.removeScriptsOfClass(oldClass);
	}
}
