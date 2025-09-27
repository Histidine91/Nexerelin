package exerelin.world.scenarios;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.CharacterCreationData;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles custom scenarios for the starting sector.
 * Examples include "Derelict Empire" where all non-homeworld planets are controlled by derelicts.
 */
public class ScenarioManager {

	public static final boolean DEBUG_MODE = false;
	
	public static final String CONFIG_FILE = "data/config/exerelin/customScenarios.json";
	protected static final List<StartScenarioDef> defs = new ArrayList<>();
	protected static final Map<String, StartScenarioDef> defsByID = new HashMap<>();

	public static final String MEMORY_KEY_SCENARIO = "$nex_scenarioId";
	public static final String MEMORY_KEY_SCENARIO_SCRIPT = "$nex_scenario_script";

	protected static String scenarioId;
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
			def.randomSectorOnly = defJson.optBoolean("randomSectorOnly");
			def.requiredModId = defJson.optString("requiredModId", null);
			
			defs.add(def);
			defsByID.put(id, def);
		}
	}
	
	public static void prepScenario(String id) {
		scenarioId = id;

		if (id == null) {
			scenario = null;
			return;
		}
		
		StartScenarioDef def = defsByID.get(id);
		scenario = (Scenario) NexUtils.instantiateClassByName(def.className);
		scenario.init();
	}
	
	public static void clearScenario() {
		scenario = null;
		scenarioId = null;
	}
	
	public static void onCharacterCreation(CharacterCreationData data) {
		if (scenario != null) scenario.onCharacterCreation(data);
	}

	public static void afterNewGame(SectorAPI sector) {
		if (scenario != null) {
			sector.getMemoryWithoutUpdate().set(MEMORY_KEY_SCENARIO, scenarioId);
		}
	}
	
	public static void afterProcGen(SectorAPI sector) {
		if (scenario != null) scenario.afterProcGen(sector);
	}
	
	public static void afterEconomyLoad(SectorAPI sector) {
		if (scenario != null) scenario.afterEconomyLoad(sector);
	}
	
	public static void afterTimePass(SectorAPI sector) {
		if (scenario != null) {
			if (!DEBUG_MODE) {
				scenario.afterTimePass(sector);
			} else {
				Global.getSector().getMemoryWithoutUpdate().set(MEMORY_KEY_SCENARIO_SCRIPT, scenario);
			}
		}

	}

	// runcode exerelin.world.scenarios.ScenarioManager.getSavedScenario().afterTimePass(Global.getSector());
	public static Scenario getSavedScenario() {
		return (Scenario) Global.getSector().getMemoryWithoutUpdate().get(MEMORY_KEY_SCENARIO_SCRIPT);
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
		public boolean randomSectorOnly = false;
		public String requiredModId;
		
		public StartScenarioDef(String id, String name, String desc, String className) {
			this.id = id;
			this.name = name;
			this.desc = desc;
			this.className = className;
		}
	}
	
}
