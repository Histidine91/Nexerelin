package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.ExerelinModPlugin;
import exerelin.campaign.AllianceManager.Alignment;
import exerelin.campaign.ExerelinSetupData;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;
import org.json.JSONException;
import org.lazywizard.lazylib.MathUtils;

public class ExerelinFactionConfig
{
    public static final String[] DEFAULT_MINERS = {"mining_drone_wing", "shepherd_Frontier"};
    public static final Map<Alignment, Float> DEFAULT_ALIGNMENTS = new HashMap<>();
    
    public String factionId;

    public String uniqueModClassName = "";
    public boolean playableFaction = true;
    public boolean corvusCompatible = false;
   
    public boolean pirateFaction = false;
    public boolean isPirateNeutral = false;
    public boolean spawnPatrols = true;    // only used for factions set to not spawn patrols in .faction file
    @Deprecated
    public boolean spawnPiratesAndMercs = true;    // ditto
    
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
    public Map<Alignment, Float> alignments = new HashMap<>(DEFAULT_ALIGNMENTS);
    
	public float spawnMarketShare = 1;
    public boolean freeMarket = false;

    public float invasionStrengthBonusAttack = 0;
    public float invasionStrengthBonusDefend = 0;
    public float invasionFleetSizeMod = 0;
    public float responseFleetSizeMod = 0;
    public float invasionPointMult = 1;
    public float patrolSizeMult = 1;
    
    public boolean dropPrisoners = true;
    public boolean noHomeworld = false;
    public boolean showIntelEvenIfDead = false;
    
    public boolean allowAgentActions = true;
    public boolean allowPrisonerActions = true;
    
    public List<String> customStations = new ArrayList<>();
    
    public List<String> miningVariantsOrWings = new ArrayList<>();
    
    public Map<StartFleetType, List<String>> startShips = new HashMap<>();
    
    static {
        for (Alignment alignment : Alignment.values())
        {
            DEFAULT_ALIGNMENTS.put(alignment, 0f);
        }
    }

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
            
            pirateFaction = settings.optBoolean("pirateFaction", false);
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
            spawnMarketShare = (float)settings.optDouble("spawnMarketShare", spawnMarketShare);
            
            invasionStrengthBonusAttack = (float)settings.optDouble("invasionStrengthBonusAttack", 0);
            invasionStrengthBonusDefend = (float)settings.optDouble("invasionStrengthBonusDefend", 0);
            invasionFleetSizeMod = (float)settings.optDouble("invasionFleetSizeMod", 0);
            responseFleetSizeMod = (float)settings.optDouble("responseFleetSizeMod", 0);
            invasionPointMult = (float)settings.optDouble("invasionPointMult", 1);
            patrolSizeMult = (float)settings.optDouble("patrolSizeMult", 1);
            
            dropPrisoners = settings.optBoolean("dropPrisoners", dropPrisoners);
            noHomeworld = settings.optBoolean("noHomeworld", noHomeworld);
            showIntelEvenIfDead = settings.optBoolean("showIntelEvenIfDead", showIntelEvenIfDead);
            
            allowAgentActions = settings.optBoolean("allowAgentActions", allowAgentActions);
            allowPrisonerActions = settings.optBoolean("allowPrisonerActions", allowPrisonerActions);
            
            if (settings.has("miningVariantsOrWings"))
                miningVariantsOrWings = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("miningVariantsOrWings")));
            
            if (settings.has("customStations"))
                customStations = Arrays.asList(JSONArrayToStringArray(settings.getJSONArray("customStations")));
            
            if (settings.has("alignments"))
            {
                JSONObject alignmentsJson = settings.getJSONObject("alignments");
                Iterator<?> keys = alignmentsJson.keys();
                while( keys.hasNext() ) {
                    String key = (String)keys.next();
                    float value = (float)alignmentsJson.optDouble(key, 0);
                    String alignmentName = StringHelper.flattenToAscii(key.toUpperCase());
                    try {
                        Alignment alignment = Alignment.valueOf(alignmentName);
                        alignments.put(alignment, value);
                    } catch (IllegalArgumentException ex) {
                        // do nothing
                        Global.getLogger(this.getClass()).warn("Invalid alignment entry for faction " + this.factionId + ": " + key);
                    }
                }
            }
            loadStartShips(settings);
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
    
    public void getStartShipTypeIfAvailable(JSONObject settings, String key, StartFleetType type) throws JSONException
    {
        if (settings.has(key))
            startShips.put(type, ExerelinUtils.JSONArrayToArrayList(settings.getJSONArray(key)));
    }
    
    public void loadStartShips(JSONObject settings) throws JSONException
    {
        getStartShipTypeIfAvailable(settings, "startShipsSolo", StartFleetType.SOLO);
        getStartShipTypeIfAvailable(settings, "startShipsSoloSSP", StartFleetType.SOLO_SSP);
        getStartShipTypeIfAvailable(settings, "startShipsCombatSmall", StartFleetType.COMBAT_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsCombatSmallSSP", StartFleetType.COMBAT_SMALL_SSP);
        getStartShipTypeIfAvailable(settings, "startShipsTradeSmall", StartFleetType.TRADE_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsTradeSmallSSP", StartFleetType.TRADE_SMALL_SSP);
        getStartShipTypeIfAvailable(settings, "startShipsCombatLarge", StartFleetType.COMBAT_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsCombatLargeSSP", StartFleetType.COMBAT_LARGE_SSP);
        getStartShipTypeIfAvailable(settings, "startShipsTradeLarge", StartFleetType.TRADE_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsTradeLargeSSP", StartFleetType.TRADE_LARGE_SSP);
    }
    
    /**
     * Helper method to pick ships and add to the provided <code>List</code> using a <code>WeightedRandomPicker</code>.
     * @param picker The ship role picker to use
     * @param list The list to modify
     * @param clear If true, clear the picker after use
     */
    protected void pickShipsAndAddToList(WeightedRandomPicker<String> picker, List<String> list, boolean clear)
    {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        List<ShipRolePick> picks = faction.pickShip(picker.pick(), 1, picker.getRandom());
        for (ShipRolePick pick : picks)
            list.add(pick.variantId);
        if (clear) picker.clear();
    }
    
    // lol
    protected List<String> getRandomStartShipsForTypeTemplars(StartFleetType type)
    {
        List<String> ships = new ArrayList<>();
        
        WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();
        
        if (type == StartFleetType.COMBAT_LARGE || type == StartFleetType.COMBAT_LARGE_SSP)
        {
            rolePicker.add(ShipRoles.COMBAT_MEDIUM, 1);	// Crusader
			rolePicker.add(ShipRoles.ESCORT_MEDIUM, 1);	// Crusader
            pickShipsAndAddToList(rolePicker, ships, true);
            
			rolePicker.add(ShipRoles.ESCORT_SMALL, 2);	// Jesuit
			rolePicker.add(ShipRoles.COMBAT_SMALL, 1);	// Martyr or Jesuit
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);	// Martyr, sometimes Jesuit
            pickShipsAndAddToList(rolePicker, ships, true);
            
			rolePicker.add(ShipRoles.FAST_ATTACK, 2);	// Martyr, sometimes Jesuit
			rolePicker.add(ShipRoles.FIGHTER, 1);	// Teuton (no Smiter)
            
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.COMBAT_SMALL || type == StartFleetType.COMBAT_SMALL_SSP)
        {
            //rolePicker.add(ShipRoles.COMBAT_SMALL, 2);	// Martyr or Jesuit
            rolePicker.add(ShipRoles.ESCORT_SMALL, 2);	// Jesuit
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 1);	// Martyr or Jesuit
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);	// Martyr, sometimes Jesuit
			rolePicker.add(ShipRoles.FIGHTER, 1);	// Teuton (no Smiter)
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.SOLO || type == StartFleetType.SOLO_SSP)
        {
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);	// Martyr, sometimes Jesuit
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        return ships;
        
    }
    /**
     * Returns random starting ships
     * @param type
     * @return
     */
    protected List<String> getRandomStartShipsForType(StartFleetType type)
    {
        if (factionId.equals("templars")) 
            return getRandomStartShipsForTypeTemplars(type);
        
        List<String> ships = new ArrayList<>();
        
        WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();
        
        if (type == StartFleetType.COMBAT_LARGE || type == StartFleetType.COMBAT_LARGE_SSP)
        {
            rolePicker.add(ShipRoles.COMBAT_LARGE, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_MEDIUM, 2);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1);
            rolePicker.add(ShipRoles.ESCORT_MEDIUM, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
            rolePicker.add(ShipRoles.ESCORT_SMALL, 1);
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.TRADE_LARGE || type == StartFleetType.TRADE_LARGE_SSP)
        {
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_LARGE, 1);
            rolePicker.add(ShipRoles.FREIGHTER_LARGE, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_MEDIUM, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 2);
            rolePicker.add(ShipRoles.ESCORT_MEDIUM, 2);
            rolePicker.add(ShipRoles.FREIGHTER_MEDIUM, 3);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.ESCORT_SMALL, 1);
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.COMBAT_SMALL || type == StartFleetType.COMBAT_SMALL_SSP)
        {
            rolePicker.add(ShipRoles.COMBAT_MEDIUM, 2);
            rolePicker.add(ShipRoles.ESCORT_MEDIUM, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 3);
            rolePicker.add(ShipRoles.ESCORT_SMALL, 2);
            rolePicker.add(ShipRoles.FAST_ATTACK, 2);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
            rolePicker.add(ShipRoles.ESCORT_SMALL, 2);
            rolePicker.add(ShipRoles.FAST_ATTACK, 2);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.TRADE_SMALL || type == StartFleetType.TRADE_SMALL_SSP)
        {
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 2);
            rolePicker.add(ShipRoles.FREIGHTER_MEDIUM, 3);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.ESCORT_SMALL, 1);
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 2);
            rolePicker.add(ShipRoles.FREIGHTER_SMALL, 2);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.ESCORT_SMALL, 1);
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.SOLO || type == StartFleetType.SOLO_SSP)
        {
            rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
            rolePicker.add(ShipRoles.ESCORT_SMALL, 1);
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        return ships;
        
        // random fleet method: gives too many crappy little ships
        /*
        float combatFP = 4;
        float tradeFP = 0;
        String factoryType = FleetTypes.PATROL_SMALL;    // probably not needed but meh
        switch (type) {
            case COMBAT_SMALL:
            case COMBAT_SMALL_SSP:
                break;
            case COMBAT_LARGE:
            case COMBAT_LARGE_SSP:
                combatFP = 6;
                tradeFP = 2;
                factoryType = FleetTypes.PATROL_LARGE;
                break;
            case TRADE_SMALL:
            case TRADE_SMALL_SSP:
                combatFP = 1;
                tradeFP = 3;
                factoryType = FleetTypes.TRADE_SMALL;
                break;
            case TRADE_LARGE:
            case TRADE_LARGE_SSP:
                combatFP = 2;
                tradeFP = 6;
                factoryType = FleetTypes.TRADE;
        }

        MarketAPI market = Global.getFactory().createMarket("fake_market", "fake market", 6);
        FleetParams fleetParams = new FleetParams(null, market, factionId, null, factoryType, 
                combatFP, // combat
                tradeFP, // freighters
                0,        // tankers
                0,        // personnel transports
                0,        // liners
                0,        // civilian
                0,    // utility
                0, (float)MathUtils.getRandomNumberInRange(0.4f, 0.7f), 0, 0);    // quality bonus, quality override, officer num mult, officer level bonus
        CampaignFleetAPI fleet = FleetFactoryV2.createFleet(fleetParams);
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy())
        {
            if (member.isFighterWing())
                ships.add(member.getSpecId());
            else
                ships.add(member.getVariant().getHullVariantId());
        }
        
        return ships;
        */
    }
    
    /**
     * Gets a list of ships to give to the player at start, based on the chosen starting fleet type.
     * Can use the predefined ships in the faction config, or random ones based on ship roles.
     * @param typeStr
     * @param allowFallback If true, return the specified solo start ship if the chosen type is not available,
     * or a Wolf if that isn't available either
     * @return A list of variant IDs
     */
    public List<String> getStartShipsForType(String typeStr, boolean allowFallback)
    {
        StartFleetType type = StartFleetType.valueOf(typeStr.toUpperCase());
        StartFleetType typeSSP = StartFleetType.valueOf((typeStr + "_SSP").toUpperCase());
        
        boolean useSSPShips = ExerelinUtils.isSSPInstalled(true);
        if (factionId.equals(Factions.PIRATES))
            useSSPShips = useSSPShips || ExerelinModPlugin.HAVE_UNDERWORLD;
        else
            useSSPShips = useSSPShips || ExerelinModPlugin.HAVE_SWP;
        
        if (ExerelinSetupData.getInstance().randomStartShips && (startShips.containsKey(type) || startShips.containsKey(typeSSP)) )
            return getRandomStartShipsForType(type);
        
        if (useSSPShips)
        {
            if (startShips.containsKey(typeSSP))
                return startShips.get(typeSSP);
        }
        
        if (startShips.containsKey(type))
            return startShips.get(type);
        
        if (!allowFallback) return null;
        
        if (startShips.containsKey(StartFleetType.SOLO))
            return startShips.get(StartFleetType.SOLO);
        
        return Arrays.asList(new String[]{"wolf_Starting"});
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
    
    public static enum StartFleetType {
        SOLO, SOLO_SSP,
        COMBAT_SMALL, COMBAT_SMALL_SSP,
        TRADE_SMALL, TRADE_SMALL_SSP,
        COMBAT_LARGE, COMBAT_LARGE_SSP,
        TRADE_LARGE, TRADE_LARGE_SSP
    }
}
