package exerelin.utilities;

import com.fs.starfarer.api.Global;
import exerelin.campaign.AllianceManager.Alignment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class ExerelinFactionConfig
{
    public static final String[] DEFAULT_MINERS = {"mining_drone_wing", "shepherd_Frontier"};
    
    public String factionId;

    public String uniqueModClassName = "";
    public Boolean playableFaction = true;
    public boolean corvusCompatible = false;
   
    public Boolean isPirateNeutral = false;
    public Boolean spawnPatrols = true;    // only used for factions set to not spawn patrols in .faction file
	@Deprecated
    public Boolean spawnPiratesAndMercs = true;    // ditto
    
    // 0 = not hostile
    // 1 = inhospitable to player, hostile to everyone else
    // 2 = hostile to everyone
    // 3 = vengeful to everyone
    public int hostileToAll = 0;    

    public double crewExpereinceLevelIncreaseChance = 0.0;
    public double baseFleetCostMultiplier = 1.0;

    public String customRebelFaction = "";
    public String customRebelFleetId = "";
    public String rebelFleetSuffix = "Dissenters";

    public String asteroidMiningFleetName = "Mining Fleet";
    public String gasMiningFleetName = "Mining Fleet";
    public String logisticsFleetName = "Logistics Convoy";
    public String invasionFleetName = "Invasion Fleet";
    public String invasionSupportFleetName = "Strike Fleet";
    public String responseFleetName = "Response Fleet";
    public String defenceFleetName = "Defence Fleet";
    
    public int positiveDiplomacyExtra = 0;
    public int negativeDiplomacyExtra = 0;
    public String[] factionsLiked = new String[]{};
    public String[] factionsDisliked = new String[]{};
    public String[] factionsNeutral = new String[]{};
    public Map<Alignment, Float> alignments = new HashMap<>();
    
    public boolean freeMarket = false;

    public float invasionStrengthBonusAttack = 0;
    public float invasionStrengthBonusDefend = 0;
    public float invasionFleetSizeMod = 0;
    public float responseFleetSizeMod = 0;
    public float invasionPointMult = 1;
    
    public boolean dropPrisoners = true;
    
    public List<String> miningVariantsOrWings = new ArrayList<String>() {};

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
            corvusCompatible = settings.optBoolean("corvusCompatible", false);
            
            isPirateNeutral = settings.optBoolean("isPirateNeutral", false);
            spawnPatrols = settings.optBoolean("spawnPatrols", true);
            spawnPiratesAndMercs = settings.optBoolean("spawnPiratesAndMercs", true);
            hostileToAll = settings.optInt("hostileToAll", hostileToAll);

            crewExpereinceLevelIncreaseChance = settings.optDouble("crewExpereinceLevelIncreaseChance", 0);
            baseFleetCostMultiplier = settings.optDouble("baseFleetCostMultiplier", 1);

            customRebelFaction = settings.optString("customRebelFaction", customRebelFaction);
            customRebelFleetId = settings.optString("customRebelFleetId", customRebelFleetId);
            rebelFleetSuffix = settings.optString("rebelFleetSuffix", rebelFleetSuffix);
            
            asteroidMiningFleetName = settings.optString("asteroidMiningFleetName", asteroidMiningFleetName);
            gasMiningFleetName = settings.optString("gasMiningFleetName", gasMiningFleetName);
            logisticsFleetName = settings.optString("logisticsFleetName", logisticsFleetName);
            invasionFleetName = settings.optString("invasionFleetName", invasionFleetName);
            invasionSupportFleetName = settings.optString("invasionSupportFleetName", invasionSupportFleetName);
            defenceFleetName = settings.optString("defenceFleetName", defenceFleetName);
            responseFleetName = settings.optString("responseFleetName", responseFleetName);
            
            positiveDiplomacyExtra = settings.optInt("positiveDiplomacyExtra");
            negativeDiplomacyExtra = settings.optInt("negativeDiplomacyExtra");
            factionsLiked = JSONArrayToStringArray(settings.getJSONArray("factionsLiked"));
            factionsDisliked = JSONArrayToStringArray(settings.getJSONArray("factionsDisliked"));
            if (settings.has("factionsNeutral"))
                factionsNeutral = JSONArrayToStringArray(settings.getJSONArray("factionsNeutral"));
            
            freeMarket = settings.optBoolean("freeMarket", freeMarket);
            
            invasionStrengthBonusAttack = (float)settings.optDouble("invasionStrengthBonusAttack", 0);
            invasionStrengthBonusDefend = (float)settings.optDouble("invasionStrengthBonusDefend", 0);
            invasionFleetSizeMod = (float)settings.optDouble("invasionFleetSizeMod", 0);
            responseFleetSizeMod = (float)settings.optDouble("responseFleetSizeMod", 0);
            invasionPointMult = (float)settings.optDouble("invasionPointMult", 1);
            
            dropPrisoners = settings.optBoolean("dropPrisoners", dropPrisoners);
            
            if (settings.has("miningVariantsOrWings"))
                miningVariantsOrWings = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("miningVariantsOrWings")));
            
            if (settings.has("alignments"))
            {
                JSONObject alignmentsJson = settings.getJSONObject("alignments");
                for (Alignment alignment : Alignment.values())
                {
                    alignments.put(alignment, (float)alignmentsJson.optDouble(alignment.toString().toLowerCase(), 0));
                }
            }
            else
            {
                for (Alignment alignment : Alignment.values())
                {
                    alignments.put(alignment, 0f);
                }
            }
        }
        catch(Exception e)
        {
            Global.getLogger(ExerelinFactionConfig.class).error(e);
        }
        
        if (miningVariantsOrWings.isEmpty())
        {
            miningVariantsOrWings = Arrays.asList(DEFAULT_MINERS);
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
