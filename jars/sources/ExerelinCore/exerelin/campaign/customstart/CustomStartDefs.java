package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class CustomStartDefs {
	public static final String CONFIG_FILE = "data/config/exerelin/customStarts.json";
	@Getter	protected static final List<CustomStartDef> defs = new ArrayList<>();
	@Getter	protected static final Map<String, CustomStartDef> defsByID = new HashMap<>();
	@Getter	protected static final Map<String, Color> difficultyColors = new HashMap<>();
	
	static {
		try {
            loadSettings();
        } catch (IOException | JSONException | NullPointerException ex) {
            throw new RuntimeException(ex);
        }
	}
	
	protected static void loadSettings() throws IOException, JSONException {
		JSONObject baseJson = Global.getSettings().getMergedJSONForMod(CONFIG_FILE, ExerelinConstants.MOD_ID);
		
		// load colors
		JSONObject configJson = baseJson.getJSONObject("config");
		JSONObject colorsJson = configJson.getJSONObject("colors");
		Iterator<String> keys = colorsJson.sortedKeys();
		while (keys.hasNext()) {
			String difficulty = keys.next();
			JSONArray colorJson = colorsJson.getJSONArray(difficulty);
			Color color = new Color(colorJson.getInt(0), colorJson.getInt(1), colorJson.getInt(2));
			difficultyColors.put(difficulty, color);
		}
		
		// load custom start defs
		JSONArray scenariosJson = baseJson.getJSONArray("starts");
		for (int i = 0; i < scenariosJson.length(); i++)
		{
			JSONObject defJson = scenariosJson.getJSONObject(i);
			String id = defJson.getString("id");
			String name = defJson.getString("name");
			String desc = defJson.getString("desc");
			String className = defJson.getString("className");
			
			CustomStartDef def = new CustomStartDef(id, name, desc, className);
			def.requiredModId = defJson.optString("requiredModId", null);
			def.difficulty = defJson.optString("difficulty", StringHelper.getString("unknown"));
			def.factionId = defJson.optString("factionId", Factions.PLAYER);
			def.randomSector = defJson.optInt("randomSector", 0);
			//def.configStartingResources = defJson.optBoolean("configStartingResources", true);
			
			addCustomStart(def);
		}
	}

	public static void addCustomStart(CustomStartDef def) {
		defs.add(def);
		defsByID.put(def.id, def);
	}

	public static void setDifficultyColors(String id, Color color) {
		difficultyColors.put(id, color);
	}
	
	public static CustomStartDef getStartDef(String id) {
		return defsByID.get(id);
	}
	
	public static List<CustomStartDef> getStartDefs() {
		return new ArrayList<>(defs);
	}
	
	public static Color getDifficultyColor(String difficulty) {
		difficulty = difficulty.toLowerCase(Locale.ROOT);
		if (!difficultyColors.containsKey(difficulty))
			return Misc.getHighlightColor();
		return difficultyColors.get(difficulty);
	}
	
	public static void loadCustomStart(String id, InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
		CustomStartDef def = defsByID.get(id);
		CustomStart start = (CustomStart) NexUtils.instantiateClassByName(def.className);
		memoryMap.get(MemKeys.LOCAL).set("$playerFaction", def.factionId, 0);
		start.execute(dialog, memoryMap);
	}
	
	public static boolean validateCustomStart(String id) {
		CustomStartDef def = defsByID.get(id);
		try {
			Class.forName(def.className);
			return true;
		} catch (ClassNotFoundException ex) {
			return false;
		}
	}
	
	public static class CustomStartDef {
		public final String id;
		public String name;
		public String desc;
		public String difficulty;
		public String factionId = Factions.PLAYER;
		public String className;
		public String requiredModId;
		public int randomSector;
		//public boolean configStartingResources = true;
		
		public CustomStartDef(String id, String name, String desc, String className) {
			this.id = id;
			this.name = name;
			this.desc = desc;
			this.className = className;
		}
	}
}
