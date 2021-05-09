package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
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
public class NexUtils
{	
	public static long getStartingSeed()
	{
		String seedStr = Global.getSector().getSeedString().replaceAll("[^0-9]", "");
		return Long.parseLong(seedStr);
	}
	
	public static void advanceIntervalDays (IntervalUtil interval, float time)
	{
		float days = Global.getSector().getClock().convertToDays(time);
		interval.advance(days);
	}
	
	/**
	 * Adds an intel item that "ends" as soon as it starts (e.g. notification messages).
	 * @param <T>
	 * @param intel
	 */
	public static <T extends BaseIntelPlugin> void addExpiringIntel(T intel)
	{
		Global.getSector().getIntelManager().addIntel(intel);
		Global.getSector().addScript(intel);
		intel.endAfterDelay();
	}

	public static Object getRandomArrayElement(Object[] array)
	{
		if (array.length == 0)
			return null;

		int randomIndex = MathUtils.getRandomNumberInRange(0, array.length - 1);

		return array[randomIndex];
	}

	public static <T> T getRandomListElement(List<T> list)
	{
		if (list.isEmpty())
			return null;

		int randomIndex = MathUtils.getRandomNumberInRange(0, list.size() - 1);

		return list.get(randomIndex);
	}
	
	public static <T> T getRandomListElement(List<T> list, Random rand)
	{
		if (list.isEmpty())
			return null;

		int randomIndex = rand.nextInt(list.size());

		return list.get(randomIndex);
	}
	
	public static <T> int modifyMapEntry(Map<T, Integer> map, T key, int amount) {
		int curr = 0;
		if (map.containsKey(key))
			curr = map.get(key);
		map.put(key, curr + amount);
		return curr + amount;
	}
	
	public static <T> float modifyMapEntry(Map<T, Float> map, T key, float amount) {
		float curr = 0;
		if (map.containsKey(key))
			curr = map.get(key);
		map.put(key, curr + amount);
		return curr + amount;
	}
	
	/**
	 * Generates a gaussian value using the provided {@code Random} (equivalent to
	 * calling {@code nextGaussian()}, then clamps it to between {@code min} and {@code max}.
	 * @param random
	 * @param min
	 * @param max
	 * @return
	 */
	public static float getBoundedGaussian(Random random, float min, float max) {
		float gauss = (float)random.nextGaussian();
		if (gauss > max) gauss = max;
		if (gauss < min) gauss = min;
		return gauss;
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
	
	// see http://fractalsoftworks.com/forum/index.php?topic=5061.msg312513#msg312513
	public static long getBonusXPForSpendingStoryPointBeforeSpendingIt(MutableCharacterStatsAPI stats) 
	{
		LevelupPlugin plugin = Global.getSettings().getLevelupPlugin();
		int per = plugin.getStoryPointsPerLevel();
		long xp = stats.getXP();
		long bonusXp = stats.getBonusXp();
		
		// what we want to do is figure out exactly how much bonus XP is needed to gain 1 story point
		// if the bonus XP crosses over to the next level - so part of the bonus XP is based on the XP
		// for the current level, and part for the next.
		int levelWithBonusXP = 1;
		while (plugin.getXPForLevel(levelWithBonusXP + 1) <= xp + bonusXp * 2L) {
			levelWithBonusXP++;
			if (levelWithBonusXP >= plugin.getMaxLevel()) break;
		}
		
		if (levelWithBonusXP < plugin.getMaxLevel() - 1) {
			long currLevelStart = plugin.getXPForLevel(levelWithBonusXP);
			long currLevelEnd = plugin.getXPForLevel(levelWithBonusXP + 1);
			long nextLevelEnd = plugin.getXPForLevel(levelWithBonusXP + 2);
			
			currLevelEnd -= currLevelStart;
			nextLevelEnd -= currLevelStart;
			
			float fraction = (float)(xp + bonusXp * 2L - currLevelStart) / (float) currLevelEnd;
			float fractionPerPt = 1f / (float) per;
			float fractionAfter = fraction + fractionPerPt * 2f; // * 2 because it'll be gained alongside real XP
			
			float xpForThisLevel = Math.max(0f, Math.min(1f, fractionAfter) - fraction) * currLevelEnd;
			float xpForNextLevel = Math.max(0f, fractionAfter - 1f) * (nextLevelEnd - currLevelEnd);
			
			return (long)Math.round((xpForThisLevel + xpForNextLevel) * 0.5f); // * 0.5f to undo the earlier doubling
		}
		
		int level = stats.getLevel() + 1;
		level = Math.min(level, plugin.getMaxLevel() + 1);
		long neededForPrevLevel = plugin.getXPForLevel(level - 1);
		long neededForSPLevel = plugin.getXPForLevel(level);

		return ((neededForSPLevel - neededForPrevLevel) / per);
	}
	
	public static void addDevModeDialogOptions(InteractionDialogAPI dialog)
	{
		if (Global.getSettings().isDevMode())
		{
			DevMenuOptions.addOptions(dialog);
		}
	}
	
	public static boolean isNonPlaytestDevMode() {
		return Global.getSettings().isDevMode() && !Global.getSettings().getBoolean("playtestingMode");
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
        catch (Exception e)
        {
            Global.getLogger(NexFactionConfig.class).warn(e);
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

	public static Map<String, Object> jsonToMap(JSONObject object) throws JSONException {
		Map<String, Object> map = new HashMap<>();

		Iterator<String> keysItr = object.keys();
		while(keysItr.hasNext()) {
			String key = keysItr.next();
			Object value = object.get(key);

			if(value instanceof JSONArray) {
				value = jsonToList((JSONArray) value);
			}

			else if(value instanceof JSONObject) {
				value = jsonToMap((JSONObject) value);
			}
			map.put(key, value);
		}
		return map;
	}

	public static List jsonToList(JSONArray array) throws JSONException {
		List<Object> list = new ArrayList<>();
		for(int i = 0; i < array.length(); i++) {
			Object value = array.get(i);
			if(value instanceof JSONArray) {
				value = jsonToList((JSONArray) value);
			}

			else if(value instanceof JSONObject) {
				value = jsonToMap((JSONObject) value);
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
	
	public static StatBonus cloneStatBonus(StatBonus orig) {
		StatBonus clone = new StatBonus();
		for (StatMod stat : orig.getFlatBonuses().values()) {
			orig.modifyFlat(stat.source, stat.value, stat.desc);
		}
		for (StatMod stat : orig.getMultBonuses().values()) {
			orig.modifyMult(stat.source, stat.value, stat.desc);
		}
		for (StatMod stat : orig.getPercentBonuses().values()) {
			orig.modifyPercent(stat.source, stat.value, stat.desc);
		}
		return clone;
	}
	
	public static boolean objectToBoolean(Object val) {
		if (val == null) return false;
		if (val instanceof String)
			return Boolean.parseBoolean((String)val);
		else if (val instanceof Boolean)
			return (Boolean)val;
		return false;
	}
	
	public static TooltipMakerAPI.StatModValueGetter getStatModValueGetter(final boolean color, 
			final int numDigits) {
		return new TooltipMakerAPI.StatModValueGetter() {
			public String getPercentValue(MutableStat.StatMod mod) {
				String prefix = mod.getValue() > 0 ? "+" : "";
				return prefix + (int)(mod.getValue()) + "%";
			}
			public String getMultValue(MutableStat.StatMod mod) {
				return Strings.X + "" + Misc.getRoundedValue(mod.getValue());
			}
			public String getFlatValue(MutableStat.StatMod mod) {
				String prefix = mod.getValue() > 0 ? "+" : "";
				return prefix + String.format("%." + numDigits + "f", mod.getValue()) + "";
			}
			public Color getModColor(MutableStat.StatMod mod) {
				if (!color) return null;
				if (mod.getValue() < 1) return Misc.getNegativeHighlightColor();
				if (mod.getValue() > 1) return Misc.getPositiveHighlightColor();
				return null;
			}
		};
	}
}
