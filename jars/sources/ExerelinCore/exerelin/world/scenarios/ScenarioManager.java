package exerelin.world.scenarios;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import exerelin.ExerelinConstants;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handles custom scenarios for the starting sector.
 * Examples include "Derelict Empire" where all non-homeworld planets are controlled by derelicts.
 */
public class ScenarioManager {
	
	public static final String CONFIG_FILE = "data/config/exerelin/customScenarios.json";
	protected static final List<StartScenarioDef> defs = new ArrayList<>();
	protected static final Map<String, StartScenarioDef> defsByID = new HashMap<>();
	
	protected static Scenario scenario;
	
	static {
		try {
            loadSettings();
        } catch (IOException | JSONException | NullPointerException ex) {
            throw new RuntimeException(ex);
        }
	}
	
	protected static void loadSettings() throws IOException, JSONException {
		JSONObject configJson = Global.getSettings().getMergedJSONForMod(CONFIG_FILE, ExerelinConstants.MOD_ID);
		JSONArray scenariosJson = configJson.getJSONArray("scenarios");
		for (int i = 0; i < scenariosJson.length(); i++)
		{
			JSONObject defJson = scenariosJson.getJSONObject(i);
			String id = defJson.getString("id");
			String name = defJson.getString("name");
			String desc = defJson.getString("desc");
			String className = defJson.getString("className");
			
			StartScenarioDef def = new StartScenarioDef(id, name, desc, className);
			def.randomSectorOnly = defJson.optBoolean("randomSectorOnly", true);
			def.requiredModId = defJson.optString("requiredModId", null);
			
			defs.add(def);
			defsByID.put(id, def);
		}
	}
	
	public static void prepScenario(String id) {
		if (id == null) {
			scenario = null;
			return;
		}
		
		StartScenarioDef def = defsByID.get(id);
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(def.className);
			scenario = (Scenario)clazz.newInstance();
			scenario.init();
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			//Global.getLogger(StartScenarioManager.class).error("Failed to load scenario " + id, ex);
			throw new RuntimeException("Failed to load scenario " + id, ex);
		}
	}
	
	public static void clearScenario() {
		scenario = null;
	}
	
	public static void onCharacterCreation(CharacterCreationData data) {
		if (scenario != null) scenario.onCharacterCreation(data);
	}
	
	public static void afterProcGen(SectorAPI sector) {
		if (scenario != null) scenario.afterProcGen(sector);
	}
	
	public static void afterEconomyLoad(SectorAPI sector) {
		if (scenario != null) scenario.afterEconomyLoad(sector);
	}
	
	public static void afterTimePass(SectorAPI sector) {
		if (scenario != null) scenario.afterTimePass(sector);
	}
	
	public static StartScenarioDef getScenarioDef(String id) {
		return defsByID.get(id);
	}
	
	public static List<StartScenarioDef> getScenarioDefs() {
		return new ArrayList<>(defs);
	}
	
	public static class StartScenarioDef {
		public final String id;
		public String name;
		public String desc;
		public String className;
		public boolean randomSectorOnly = true;
		public String requiredModId;
		
		public StartScenarioDef(String id, String name, String desc, String className) {
			this.id = id;
			this.name = name;
			this.desc = desc;
			this.className = className;
		}
	}
	
}
