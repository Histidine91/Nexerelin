package exerelin.utilities;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

// TODO: clean up most of this
// I really don't think most of it is neeeded any more in current SS versions
public class ExerelinFactionConfig
{
    public String factionId;

    public String uniqueModClassName = "";
    public Boolean playableFaction = true;
   
    public Boolean isPirateNeutral = false;
    public Boolean spawnPatrols = true;    // only used for factions set to not spawn patrols in .faction file
    public Boolean spawnPiratesAndMercs = true;    // ditto

    public double crewExpereinceLevelIncreaseChance = 0.0;
    public double baseFleetCostMultiplier = 1.0;

    public String customRebelFaction = "";
    public String customRebelFleetId = "";
    public String rebelFleetSuffix = "Dissenters";

    public String asteroidMiningFleetName = "Asteroid Mining Fleet";
    public String gasMiningFleetName = "Gas Mining Fleet";
    public String logisticsFleetName = "Logistics Convoy";
    public String invasionFleetName = "Invasion Fleet";
    public String invasionSupportFleetName = "Strike Fleet";
    
    public int positiveDiplomacyExtra = 0;
    public int negativeDiplomacyExtra = 0;
    public String[] factionsLiked = new String[]{};
    public String[] factionsDisliked = new String[]{};

    public List<String> miningVariantsOrWings = new ArrayList<String>() {};

    public List<String> carrierVariants = new ArrayList<String>() {};

    public List<String> fighterWings = new ArrayList<String>() {};
    public List<String> frigateVariants = new ArrayList<String>() {};
    public List<String> destroyerVariants = new ArrayList<String>() {};
    public List<String> cruiserVariants = new ArrayList<String>() {};
    public List<String> capitalVariants = new ArrayList<String>() {};

    public ExerelinFactionConfig(String factionId)
    {
        this.factionId = factionId;
        this.loadFactionConfig();
    }

    public void loadFactionConfig()
    {
        try
        {
            JSONObject settings = Global.getSettings().loadJSON("data/config/exerelinFactionConfig/" + factionId + ".json");

            uniqueModClassName = settings.getString("uniqueModClassName");
            playableFaction = settings.optBoolean("playableFaction", true);
            isPirateNeutral = settings.optBoolean("isPirateNeutral", false);
            spawnPatrols = settings.optBoolean("spawnPatrols", true);
            spawnPiratesAndMercs = settings.optBoolean("spawnPiratesAndMercs", true);

            crewExpereinceLevelIncreaseChance = settings.optDouble("crewExpereinceLevelIncreaseChance", 0);
            baseFleetCostMultiplier = settings.optDouble("baseFleetCostMultiplier", 1);

            customRebelFaction = settings.optString("customRebelFaction", customRebelFaction);
            customRebelFleetId = settings.optString("customRebelFleetId", customRebelFleetId);
            rebelFleetSuffix = settings.optString("rebelFleetSuffix", rebelFleetSuffix);
            
            asteroidMiningFleetName = settings.optString("asteroidMiningFleetName", asteroidMiningFleetName);
            gasMiningFleetName = settings.optString("gasMiningFleetName", gasMiningFleetName);
            logisticsFleetName = settings.optString("logisticsFleetName", logisticsFleetName);
            invasionFleetName = settings.optString("boardingFleetName", invasionFleetName);
            invasionSupportFleetName = settings.optString("commandFleetName", invasionSupportFleetName);

            positiveDiplomacyExtra = settings.optInt("positiveDiplomacyExtra");
            negativeDiplomacyExtra = settings.optInt("negativeDiplomacyExtra");
            factionsLiked = JSONArrayToStringArray(settings.getJSONArray("factionsLiked"));
            factionsDisliked = JSONArrayToStringArray(settings.getJSONArray("factionsDisliked"));
            
            miningVariantsOrWings = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("miningVariantsOrWings")));
        }
        catch(Exception e)
        {
            Global.getLogger(ExerelinFactionConfig.class).error(e);
        }
    }

    private String[] JSONArrayToStringArray(JSONArray jsonArray)
    {
        try
        {
            //return jsonArray.toString().substring(1, jsonArray.toString().length() - 1).replaceAll("\"","").split(",");
            String[] ret = new String[jsonArray.length()];
            for (int i=0; i<jsonArray.length(); i++)
            {
                ret[i] = jsonArray.getString(i);
            }
            return ret;
        }
        catch(Exception e)
        {
            Global.getLogger(ExerelinFactionConfig.class).error(e);
            return new String[]{};
        }
    }
}
