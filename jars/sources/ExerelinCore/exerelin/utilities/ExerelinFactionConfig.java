package exerelin.utilities;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONObject;

public class ExerelinFactionConfig
{
    public String factionId;

    public String[] stationInteriorIllustrationKeys = new String[]{"hound_hangar"};

    public Boolean changeSystemSpecsOnSystemLockdown = false;
    public String preferredBackgroundImagePath = "";
    public String preferredStarType = "";
    public String preferredStarLight = "";

    public String[] stationNameSuffixes = new String[]{"Base", "Orbital", "Trading Post", "HQ", "Post", "Dock", "Mantle", "Ledge"};

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

    public String smallPatrolFleetName = "Recon PATROL";
    public String mediumPatrolFleetName = "Ranger PATROL";
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

    public ExerelinFactionConfig(String factionId, boolean load)
    {
        this.factionId = factionId;
        if(load)
            loadFactionConfig();
    }

    public void loadFactionConfig()
    {
        try
        {
            JSONObject settings = Global.getSettings().loadJSON("data/config/exerelinFactionConfig/" + factionId + ".json");

            stationInteriorIllustrationKeys = JSONArrayToStringArray(settings.getJSONArray("stationInteriorIllustrationKeys"));

            changeSystemSpecsOnSystemLockdown = settings.getBoolean("changeSystemSpecsOnSystemLockdown");
            preferredBackgroundImagePath = settings.getString("preferredBackgroundImagePath");
            preferredStarType = settings.getString("preferredStarType");
            preferredStarLight = settings.getString("preferredStarLight");

            stationNameSuffixes = JSONArrayToStringArray(settings.getJSONArray("stationNameSuffixes"));

            crewExpereinceLevelIncreaseChance = settings.getDouble("crewExpereinceLevelIncreaseChance");
            baseFleetCostMultiplier = settings.getDouble("baseFleetCostMultiplier");

            customRebelFaction = settings.getString("customRebelFaction");
            customRebelFleetId = settings.getString("customRebelFleetId");
            rebelFleetSuffix = settings.getString("rebelFleetSuffix");

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

            positiveDiplomacyExtra = settings.getInt("positiveDiplomacyExtra");
            negativeDiplomacyExtra = settings.getInt("negativeDiplomacyExtra");
            factionsLiked = JSONArrayToStringArray(settings.getJSONArray("factionsLiked"));
            factionsDisliked = JSONArrayToStringArray(settings.getJSONArray("factionsDisliked"));
        }
        catch(Exception e)
        {
            System.out.println("EXERELIN ERROR: Unable to load faction config for: " + this.factionId);
            System.out.println(e.getMessage());
        }
    }

    private String[] JSONArrayToStringArray(JSONArray jsonArray)
    {
        try
        {
            return jsonArray.toString().substring(1, jsonArray.toString().length() - 1).replaceAll("\"","").split(",");
        }
        catch(Exception e)
        {
            return new String[]{};
        }
    }
}
