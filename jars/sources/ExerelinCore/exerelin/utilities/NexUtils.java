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
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
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
	
	/**
	 * Converts the specified time to days and advances the provided interval.
	 * @param interval
	 * @param time
	 */
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
			Global.getLogger(NexUtils.class).error(e);
			return new String[]{};
		}
	}
	
	public static Color JSONArrayToColor(JSONArray array)
	{
		try
		{
			float alpha = 255;
			if (array.length() > 3) alpha = array.getInt(3)/255f;
			Color color = new Color(array.getInt(0)/255f, array.getInt(1)/255f, array.getInt(2)/255f, alpha);
			return color;
		}
		catch (Exception e)
		{
			Global.getLogger(NexUtils.class).error(e);
			return Color.WHITE;
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
	
	public static Object getJSONValueAsTrueType(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ex) {}
		try {
			return Float.parseFloat(value);
		} catch (NumberFormatException ex) {}
		if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
			return Boolean.parseBoolean(value);
		return value;
	}

	public static Map<String, Object> jsonToMap(JSONObject object) throws JSONException {
		Map<String, Object> map = new HashMap<>();

		Iterator<String> keysItr = object.keys();
		while(keysItr.hasNext()) {
			String key = keysItr.next();
			Object value = object.get(key);

			if (value instanceof JSONArray) {
				value = jsonToList((JSONArray) value);
			}

			else if (value instanceof JSONObject) {
				value = jsonToMap((JSONObject) value);
			}
			else if (value instanceof String) {
				value = getJSONValueAsTrueType((String)value);
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
		clone.applyMods(orig);
		return clone;
	}
	
	public static int getEstimateNum(float num, int precision) {
		int result = Math.round(num/precision);
		result *= precision;
		
		return result;
	}	
	
	public static TooltipMakerAPI.StatModValueGetter getStatModValueGetter(boolean color, 
			final int numDigits) {
		return getStatModValueGetter(color, numDigits, false);
	}
	
	public static TooltipMakerAPI.StatModValueGetter getStatModValueGetter(boolean color, 
			int numDigits, boolean lowerIsBetter) {
		
		return new NexStatModValueGetter(color, numDigits, lowerIsBetter);	
	}
	
	public static class NexStatModValueGetter implements TooltipMakerAPI.StatModValueGetter 
	{		
		protected Color high = Misc.getPositiveHighlightColor();
		protected Color low = Misc.getNegativeHighlightColor();
		
		public boolean color;
		public int numDigits;
		public boolean lowerIsBetter;
		
		public NexStatModValueGetter(boolean color, int numDigits, boolean lowerIsBetter) {
			this.color = color;
			this.numDigits = numDigits;
			this.lowerIsBetter = lowerIsBetter;
			
			if (lowerIsBetter) {
				high = Misc.getNegativeHighlightColor();
				low = Misc.getPositiveHighlightColor();
			}
		}			
			
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
			
			if (mod.getValue() < 1) return low;
			if (mod.getValue() > 1) return high;
			return null;
		}
	}
}
