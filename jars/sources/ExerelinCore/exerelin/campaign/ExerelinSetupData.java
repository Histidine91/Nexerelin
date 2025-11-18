package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig.StartFleetType;
import exerelin.utilities.ReflectionUtils;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/* This class functions as a data structure for Exerelin setup
 */

public class ExerelinSetupData
{
	public static final int NUM_DMOD_LEVELS = 4;
	public static final String MEM_KEY_START_FLEET_TYPE = "$nex_startFleetType";
	public static final String SAVE_FILE_PATH = "nex_setupData";

	public static Logger log = Global.getLogger(ExerelinSetupData.class);
	protected static ExerelinSetupData instance = null;

	public static final Set<String> NO_SAVE_FIELDS = new HashSet<>(Arrays.asList(
		"NO_SAVE_FIELDS", "NUM_DMOD_LEVELS", "MEM_KEY_START_FLEET_TYPE", "SAVE_FILE_PATH", "log", "instance", "randomStartRelationships", "randomStartRelationshipsPirate",
			"randomStartShips", "skipStory", "startFleetType"
	));

	// Sector Generation Defaults
	public int numSystems = 16;
	public int numPlanets = 36;
	public int numStations = 12;
	public int maxPlanetsPerSystem = 3;
	public int maxMarketsPerSystem = 4;	// includes stations
	public int randomColonies = 0;
	public int randomColoniesMaxSize = 4;
	public int procGenColonySizeOffset = 0;
	public Map<String, Boolean> factions = new HashMap<>();
	
	// Game defaults
	public boolean corvusMode = true;
	public boolean respawnFactions = false;
	public boolean onlyRespawnStartingFactions = false;
	@Deprecated public boolean randomStartRelationships;	
	@Deprecated public boolean randomStartRelationshipsPirate;
	
	public StartRelationsMode startRelationsMode = StartRelationsMode.DEFAULT;
	public boolean applyStartRelationsModeToPirates = false;
	public HomeworldPickMode homeworldPickMode = HomeworldPickMode.ANY;
	public boolean homeworldAllowNeighbors = true;
	
	public boolean easyMode = false;
	public boolean hardMode = false;
	public boolean prismMarketPresent = true;
	public boolean randomAntiochEnabled = true;
	public boolean freeStart = false;
	public boolean spacerObligation = false;
	public boolean useFactionWeights = true;
	public boolean randomFactionWeights = false;
	public boolean randomStartLocation = false;
	public int numStartingOfficers = 0;
	public int numStartingOperatives = 0;
	@Deprecated public boolean randomStartShips = false;
	public int dModLevel = 0;
	public boolean enableStipend = true;
	public String backgroundId = null;
	public String selectedFactionForBackground = null;

	/**
	 * Replaced by quest-specific handling.
	 */
	@Deprecated public boolean skipStory = false;
	
	/**
	 * Can be null with special starts.
	 */
	public StartFleetType startFleetType = null;
	
	public String startScenario = null;

	public ExerelinSetupData()
	{
		List<String> factionIds = NexConfig.getFactions(true, false, true);
		factionIds.add(Factions.INDEPENDENT);
		factionIds.remove(Factions.PLAYER);
		for (String factionId : factionIds) {
			factions.put(factionId, NexConfig.getFactionConfig(factionId).enabledByDefault);
		}
	}

	protected void loadSavedFactions(JSONObject saved) {
		Iterator iter = saved.keys();
		while (iter.hasNext()) {
			String factionId = (String)iter.next();
			boolean enabled = saved.optBoolean(factionId, true);
			factions.put(factionId, enabled);
		}
	}

	public static ExerelinSetupData getInstance() {
		if (instance == null) {
			instance = new ExerelinSetupData();
		}
		return instance;
	}

	public static void resetInstance() {
		instance = new ExerelinSetupData();
	}
	
	public static String getDModCountText(int level) {
		return StringHelper.getString("exerelin_ngc", "dModLevel" + level);
	}

	public static void writeToFile() {
		try {
			Global.getSettings().writeJSONToCommon(SAVE_FILE_PATH, getInstance().toJson(), true);
		} catch (JSONException | IOException ex) {
			log.error("Failed to save setup data", ex);
		}
	}

	public static void readFromFile() {
		ExerelinSetupData data = getInstance();
		try {
			JSONObject json = Global.getSettings().readJSONFromCommon(SAVE_FILE_PATH, true);
			Iterator iter = json.keys();
			while (iter.hasNext()) {
				String key = (String)iter.next();
				Object value = json.get(key);
				if (key.equals("startRelationsMode")) {
					data.startRelationsMode = StartRelationsMode.valueOf((String)value);
					continue;
				} else if (key.equals("homeworldPickMode")) {
					data.homeworldPickMode = HomeworldPickMode.valueOf((String)value);
					continue;
				} else if (key.equals("factions")) {
					data.loadSavedFactions(json.getJSONObject("factions"));
					continue;
				}

				ReflectionUtils.set(data, key, value);
			}
		} catch (JSONException | IOException ex) {
			log.warn("Failed to load setup data", ex);
		}
	}


	protected JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		for (ReflectionUtils.ReflectedField field : ReflectionUtils.getFieldsMatching(this.getClass())) {
			String name = field.getName();
			if (NO_SAVE_FIELDS.contains(name)) continue;
			json.put(name, ReflectionUtils.get(this, name));
		}
		return json;
	}

	public enum StartRelationsMode {
		DEFAULT,
		FLATTEN,
		RANDOM;

		public boolean isRandom() {
			return this == RANDOM;
		}

		public boolean isDefault() {
			return this == DEFAULT;
		}
	}

	public enum HomeworldPickMode {
		ANY, CORE, NON_CORE;

		public boolean canPickCore() {
			return this != NON_CORE;
		}

		public boolean canPickNonCore() {
			return this != CORE;
		}
	}
}
