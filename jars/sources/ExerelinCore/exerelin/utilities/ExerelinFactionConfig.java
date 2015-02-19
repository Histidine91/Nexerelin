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
    public String factionNiceName = "";
    public Boolean playableFaction = true;

    public String[] stationInteriorIllustrationKeys = new String[]{"hound_hangar"};

    public Boolean changeSystemSpecsOnSystemLockdown = false;
    public String preferredBackgroundImagePath = "";
    public String preferredStarType = "";
    public String preferredStarLight = "";

    public String[] stationNameSuffixes = new String[]{"Base", "Orbital", "Trading Post", "HQ", "Post", "Dock", "Mantle", "Ledge"};
    
    public Boolean isPirateNeutral = false;

    public double crewExpereinceLevelIncreaseChance = 0.0;
    public double baseFleetCostMultiplier = 1.0;

    public String customRebelFaction = "";
    public String customRebelFleetId = "";
    public String rebelFleetSuffix = "Dissenters";

    public String smallAttackFleetName = "Advance Force";
    public String mediumAttackFleetName = "Strike Force";
    public String largeAttackFleetName = "Crusaders";

    public String smallDefenceFleetName = "Watch Fleet";
    public String mediumDefenceFleetName = "Guard Fleet";
    public String largeDefenceFleetName = "Sentinels";

    public String smallPatrolFleetName = "Recon Patrol";
    public String mediumPatrolFleetName = "Ranger Patrol";
    public String largePatrolFleetName = "Wayfarers";

    public String asteroidMiningFleetName = "Asteroid Mining Fleet";
    public String gasMiningFleetName = "Gas Mining Fleet";
    public String logisticsFleetName = "Logistics Convoy";
    public String boardingFleetName = "Boarding Fleet";
    public String commandFleetName = "Command Fleet";

    public int positiveDiplomacyExtra = 0;
    public int negativeDiplomacyExtra = 0;
    public String[] factionsLiked = new String[]{};
    public String[] factionsDisliked = new String[]{};

    public String[] startingVariants = new String[]{};

    public List<String> freighterVariants = new ArrayList<String>() {};
    public List<String> tankerVariants = new ArrayList<String>() {};
    public List<String> miningVariantsOrWings = new ArrayList<String>() {};

    public List<String> troopTransportVariants = new ArrayList<String>() {};
    public List<String> superFreighterVariants = new ArrayList<String>() {};

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
            factionNiceName = settings.getString("factionNiceName");
            playableFaction = settings.optBoolean("playableFaction", true);
            isPirateNeutral = settings.optBoolean("isPirateNeutral", false);

            stationInteriorIllustrationKeys = JSONArrayToStringArray(settings.getJSONArray("stationInteriorIllustrationKeys"));

            changeSystemSpecsOnSystemLockdown = settings.getBoolean("changeSystemSpecsOnSystemLockdown");
            preferredBackgroundImagePath = settings.getString("preferredBackgroundImagePath");
            preferredStarType = settings.getString("preferredStarType");
            preferredStarLight = settings.getString("preferredStarLight");

            stationNameSuffixes = JSONArrayToStringArray(settings.getJSONArray("stationNameSuffixes"));

            crewExpereinceLevelIncreaseChance = settings.optDouble("crewExpereinceLevelIncreaseChance", 0);
            baseFleetCostMultiplier = settings.optDouble("baseFleetCostMultiplier", 1);

            customRebelFaction = settings.optString("customRebelFaction");
            customRebelFleetId = settings.optString("customRebelFleetId");
            rebelFleetSuffix = settings.optString("rebelFleetSuffix");

            /*
            smallAttackFleetName = settings.getString("smallAttackFleetName");
            mediumAttackFleetName = settings.getString("mediumAttackFleetName");
            largeAttackFleetName = settings.getString("largeAttackFleetName");

            smallDefenceFleetName = settings.getString("smallDefenceFleetName");
            mediumDefenceFleetName = settings.getString("mediumDefenceFleetName");
            largeDefenceFleetName = settings.getString("largeDefenceFleetName");

            smallPatrolFleetName = settings.getString("smallPatrolFleetName");
            mediumPatrolFleetName = settings.getString("mediumPatrolFleetName");
            largePatrolFleetName = settings.getString("largePatrolFleetName");

            asteroidMiningFleetName = settings.getString("asteroidMiningFleetName");
            gasMiningFleetName = settings.getString("gasMiningFleetName");
            logisticsFleetName = settings.getString("logisticsFleetName");
            boardingFleetName = settings.getString("boardingFleetName");
            commandFleetName = settings.getString("commandFleetName");
            */
            positiveDiplomacyExtra = settings.getInt("positiveDiplomacyExtra");
            negativeDiplomacyExtra = settings.getInt("negativeDiplomacyExtra");
            factionsLiked = JSONArrayToStringArray(settings.getJSONArray("factionsLiked"));
            factionsDisliked = JSONArrayToStringArray(settings.getJSONArray("factionsDisliked"));

            //startingVariants = JSONArrayToStringArray(settings.getJSONArray("startingVariants"));

            freighterVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("freighterVariants")));
            tankerVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("tankerVariants")));
            miningVariantsOrWings = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("miningVariantsOrWings")));

            troopTransportVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("troopTransportVariants")));
            superFreighterVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("superFreighterVariants")));

            //carrierVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("carrierVariants")));

            //fighterWings = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("fighterWings")));
            //frigateVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("frigateVariants")));
            //destroyerVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("destroyerVariants")));
            //cruiserVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("cruiserVariants")));
            //capitalVariants = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("capitalVariants")));
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
