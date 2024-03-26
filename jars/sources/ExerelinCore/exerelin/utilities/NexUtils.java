package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.List;
import java.util.*;

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

	public static <T> int getMapSumInteger(Map<T, Integer> map) {
		int i = 0;
		for (Integer value : map.values()) {
			if (value == null) continue;
			i += value;
		}
		return i;
	}

	public static <T> float getMapSumFloat(Map<T, Float> map) {
		float f = 0;
		for (Float value : map.values()) {
			if (value == null) continue;
			f += value;
		}
		return f;
	}

	public static void incrementMemoryValue(MemoryAPI mem, String key, int amount) {
		if (!mem.contains(key)) {
			mem.set(key, amount);
			return;
		}
		int current = mem.getInt(key);
		amount += current;
		mem.set(key, amount);
	}

	public static void incrementMemoryValue(MemoryAPI mem, String key, float amount) {
		if (!mem.contains(key)) {
			mem.set(key, amount);
			return;
		}
		float current = mem.getFloat(key);
		amount += current;
		mem.set(key, amount);
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

	public static float getTrueDaysSinceStart() {
		if (Global.getSector().getMemoryWithoutUpdate().contains("$nex_startTimestamp")) {
			long timestamp = Global.getSector().getMemoryWithoutUpdate().getLong("$nex_startTimestamp");
			return Global.getSector().getClock().getElapsedDaysSince(timestamp);
		}

		return PirateBaseManager.getInstance().getUnadjustedDaysSinceStart() - 60;	// for existing saves, assumes didn't do tutorial or use dev fast start
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
	
	public static void printStackTrace(Logger log, int depth) {
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		// skip the first two elements, which are getStackTrace and this method itself
		for (int i=2; i<depth+2; i++) {
			if (i >= stack.length) return;
			log.info(stack[i].toString());
		}
	}

	public static String mutableStatToString(MutableStat stat) {
		StringBuilder sb = new StringBuilder();
		sb.append("Value: " + stat.getModifiedValue());
		for (String flatModId : stat.getFlatMods().keySet()) {
			MutableStat.StatMod mod = stat.getFlatMods().get(flatModId);
			float val = mod.value;
			sb.append(String.format("\n  %s: %s%.1f", mod.desc, val > 0 ? "+" : "", val));
		}
		for (String percentModId : stat.getPercentMods().keySet()) {
			MutableStat.StatMod mod = stat.getPercentMods().get(percentModId);
			float val = mod.value;
			sb.append(String.format("\n  %s: %s%.1f%%", mod.desc, val > 0 ? "+" : "", val));
		}
		for (String multModId : stat.getMultMods().keySet()) {
			MutableStat.StatMod mod = stat.getMultMods().get(multModId);
			float val = mod.value;
			sb.append(String.format("\n  %s: %.2fx", mod.desc, val));
		}

		return sb.toString();
	}

	public static String statBonusToString(StatBonus stat, float base) {
		StringBuilder sb = new StringBuilder();
		sb.append("Value: " + stat.computeEffective(base));
		for (String flatModId : stat.getFlatBonuses().keySet()) {
			MutableStat.StatMod mod = stat.getFlatBonuses().get(flatModId);
			float val = mod.value;
			sb.append(String.format("\n  %s: %s%.1f", mod.desc, val > 0 ? "+" : "", val));
		}
		for (String percentModId : stat.getPercentBonuses().keySet()) {
			MutableStat.StatMod mod = stat.getPercentBonuses().get(percentModId);
			float val = mod.value;
			sb.append(String.format("\n  %s: %s%.1f%%", mod.desc, val > 0 ? "+" : "", val));
		}
		for (String multModId : stat.getMultBonuses().keySet()) {
			MutableStat.StatMod mod = stat.getMultBonuses().get(multModId);
			float val = mod.value;
			sb.append(String.format("\n  %s: %.2fx", mod.desc, val));
		}

		return sb.toString();
	}

	public static Object instantiateClassByName(String className)
	{
		Object object = null;

		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			object = clazz.newInstance();
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(NexUtils.class).error("Class load-by-name failure: " + className, ex);
		}

		return object;
	}

	public static class PairWithFloatComparator implements Comparator<Pair<?, Float>> {

		public boolean descending;

		public PairWithFloatComparator(boolean descending) {
			this.descending = descending;
		}

		@Override
		public int compare(Pair<?, Float> o1, Pair<?, Float> o2) {
			if (descending) return Float.compare(o2.two, o1.two);
			return Float.compare(o1.two, o2.two);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PairWithFloatComparator) {
				return ((PairWithFloatComparator)obj).descending = this.descending;
			}
			return false;
		}
	}

	public static class PairWithIntegerComparator implements Comparator<Pair<?, Integer>> {

		public boolean descending;

		public PairWithIntegerComparator(boolean descending) {
			this.descending = descending;
		}

		@Override
		public int compare(Pair<?, Integer> o1, Pair<?, Integer> o2) {
			if (descending) return Integer.compare(o2.two, o1.two);
			return Integer.compare(o1.two, o2.two);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof PairWithFloatComparator) {
				return ((PairWithFloatComparator)obj).descending = this.descending;
			}
			return false;
		}
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
