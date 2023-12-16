package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig.StartFleetType;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* This class functions as a data structure for Exerelin setup
 */

@SuppressWarnings("unchecked")
public class ExerelinSetupData
{
	public static final int NUM_DMOD_LEVELS = 4;
	public static final String MEM_KEY_START_FLEET_TYPE = "$nex_startFleetType";
	
	public static Logger log = Global.getLogger(ExerelinSetupData.class);
	protected static ExerelinSetupData instance = null;

	// Sector Generation Defaults
	public int numSystems = 16;
	public int numPlanets = 36;
	public int numStations = 12;
	public int maxPlanetsPerSystem = 3;
	public int maxMarketsPerSystem = 4;	// includes stations
	public int randomColonies = 0;
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
	@Deprecated public boolean randomStartShips = false;
	public int dModLevel = 0;
	public boolean skipStory = Global.getSettings().getBoolean("nex_skipStoryDefault");
	public boolean enableStipend = true;
	public String backgroundId = null;
	public String selectedFactionForBackground = null;
	
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
	
	public static enum StartRelationsMode {
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
	
	public static enum HomeworldPickMode {
		ANY, CORE, NON_CORE;
		
		public boolean canPickCore() {
			return this != NON_CORE;
		}
		
		public boolean canPickNonCore() {
			return this != CORE;
		}
	}
}
