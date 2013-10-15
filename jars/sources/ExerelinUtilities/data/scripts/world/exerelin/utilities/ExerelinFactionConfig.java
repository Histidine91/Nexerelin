package data.scripts.world.exerelin.utilities;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONObject;

public class ExerelinFactionConfig
{
    public String factionId;

    public String[] stationInteriorIllustrationKeys = new String[]{"hound_hangar"};

    public double crewExpereinceLevelIncreaseChance = 0.0;
    public double baseFleetCostMultiplier = 1.0;

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

    public ExerelinFactionConfig(String factionId)
    {
        this.factionId = factionId;
        loadFactionConfig();
    }

    public void loadFactionConfig()
    {
        try
        {
            JSONObject settings = Global.getSettings().loadJSON("data/config/exerelinFactionConfig/" + factionId + ".json");

            JSONArray JSONstationInteriorIllustrationKeys = settings.getJSONArray("stationInteriorIllustrationKeys");
            stationInteriorIllustrationKeys = JSONstationInteriorIllustrationKeys.toString().substring(1, JSONstationInteriorIllustrationKeys.toString().length() - 1).replaceAll("\"","").split(",");

            crewExpereinceLevelIncreaseChance = settings.getDouble("crewExpereinceLevelIncreaseChance");
            baseFleetCostMultiplier = settings.getDouble("baseFleetCostMultiplier");

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

        }
        catch(Exception e)
        {
            System.out.println("EXERELIN ERROR: Unable to load faction config for: " + this.factionId + ", " + e.getMessage());
        }
    }
}
