package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.alliances.Alliance.Alignment;
import java.io.IOException;
import org.json.JSONObject;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONException;

public class ExerelinFactionConfig
{
    public static final String[] DEFAULT_MINERS = {"venture_Outdated", "shepherd_Frontier"};
    public static final List<DefenceStationSet> DEFAULT_DEFENCE_STATIONS = new ArrayList<>();
    public static final List<IndustrySeed> DEFAULT_INDUSTRY_SEEDS = new ArrayList<>();
    public static final Map<Alignment, Float> DEFAULT_ALIGNMENTS = new HashMap<>();
    
    public String factionId;
    public boolean playableFaction = true;
    public boolean startingFaction = true;
    public boolean corvusCompatible = false;
    public boolean isBuiltIn = false;
    public String spawnAsFactionId = null;
    public boolean freeStart = false;
    public String ngcTooltip = null;
   
    public boolean pirateFaction = false;
    public boolean isPirateNeutral = false;
    @Deprecated
    public boolean spawnPatrols = false;    // only used for factions set to not spawn patrols in .faction file
    @Deprecated
    public boolean spawnPiratesAndMercs = false;    // ditto
    
    // 0 = not hostile
    // 1 = inhospitable to player, hostile to everyone else
    // 2 = hostile to everyone
    // 3 = vengeful to everyone
    public int hostileToAll = 0;
    
    public double baseFleetCostMultiplier = 1.0;    // currently unused

    // currently unused
    public String customRebelFaction = "";
    public String customRebelFleetId = "";
    public String rebelFleetSuffix = "Dissenters";

    // Fleet names
    public String asteroidMiningFleetName = StringHelper.getString("exerelin_fleets", "miningFleetName");
    public String gasMiningFleetName = StringHelper.getString("exerelin_fleets", "miningFleetName");
    public String invasionFleetName = StringHelper.getString("exerelin_fleets", "invasionFleetName");
    public String invasionSupportFleetName = StringHelper.getString("exerelin_fleets", "invasionSupportFleetName");
    public String responseFleetName = StringHelper.getString("exerelin_fleets", "responseFleetName");
    public String defenceFleetName = StringHelper.getString("exerelin_fleets", "defenceFleetName");
    public String suppressionFleetName = StringHelper.getString("exerelin_fleets", "suppressionFleetName");
    
    // Diplomacy
    public boolean disableDiplomacy = false;
    public int positiveDiplomacyExtra = 0;
    public int negativeDiplomacyExtra = 0;
    @Deprecated
    public String[] factionsLiked = new String[]{};
    @Deprecated
    public String[] factionsDisliked = new String[]{};
    @Deprecated
    public String[] factionsNeutral = new String[]{};
    public Map<String, Float> minRelationships = new HashMap<>();
    public Map<String, Float> maxRelationships = new HashMap<>();
    public Map<String, Float> startRelationships = new HashMap<>();
    public Map<String, Float> diplomacyPositiveChance = new HashMap<>();
    public Map<String, Float> diplomacyNegativeChance = new HashMap<>();
    public Map<String, Float> dispositions = new HashMap<>();
    public Map<Alignment, Float> alignments = new HashMap<>(DEFAULT_ALIGNMENTS);
    public Morality morality = Morality.NEUTRAL;
    public boolean noSyncRelations = false;
    public boolean noRandomizeRelations = false;
    
    // economy and such
    public float marketSpawnWeight = 1;	// what proportion of procgen markets this faction gets
    public boolean freeMarket = false;
    public float tariffMult = 1;
    
    public List<IndustrySeed> industrySeeds = new ArrayList<>();
    public Map<String, Float> industrySpawnMults = new HashMap<>();    // TODO: load this
    public List<BonusSeed> bonusSeeds = new ArrayList<>();
    
    // invasions and stuff
    public float invasionStrengthBonusAttack = 0;	// marines
    public float invasionStrengthBonusDefend = 0;
    public float invasionFleetSizeMod = 0;	// ships
    public float responseFleetSizeMod = 0;
    public float invasionPointMult = 1;	// point accumulation for launching invasions
    public float patrolSizeMult = 1;
    public float vengeanceFleetSizeMult = 1;
    public String factionIdForHqResponse = null;
    
    // misc
    public boolean dropPrisoners = true;
    public boolean noHomeworld = false;	// don't give this faction a HQ in procgen
    public boolean showIntelEvenIfDead = false;	// intel tab
    
    public boolean allowAgentActions = true;
    public boolean allowPrisonerActions = true;
    
    public boolean directoryUseShortName = false;
    public String difficultyString = "";
    
    // vengeance
    public List<String> vengeanceLevelNames = new ArrayList<>();
    public List<String> vengeanceFleetNames = new ArrayList<>();
    public List<String> vengeanceFleetNamesSingle = new ArrayList<>();
    
    // misc. part 2
    public List<CustomStation> customStations = new ArrayList<>();
    public List<DefenceStationSet> defenceStations = new ArrayList<>();
    
    public List<String> miningVariantsOrWings = new ArrayList<>();
    
    public Map<StartFleetType, StartFleetSet> startShips = new HashMap<>();
    
    // set defaults
    static {
        for (Alignment alignment : Alignment.values())
        {
            DEFAULT_ALIGNMENTS.put(alignment, 0f);
        }
        
        DefenceStationSet low = new DefenceStationSet(1, Industries.ORBITALSTATION, Industries.BATTLESTATION, Industries.STARFORTRESS);
        DefenceStationSet mid = new DefenceStationSet(0.75f, Industries.ORBITALSTATION_MID, Industries.BATTLESTATION_MID, Industries.STARFORTRESS_MID);
        DefenceStationSet high = new DefenceStationSet(0.5f, Industries.ORBITALSTATION_HIGH, Industries.BATTLESTATION_HIGH, Industries.STARFORTRESS_HIGH);
        DEFAULT_DEFENCE_STATIONS.addAll(Arrays.asList(low, mid, high));
        
        DEFAULT_INDUSTRY_SEEDS.add(new IndustrySeed(Industries.HEAVYINDUSTRY, 0.1f, true));
        DEFAULT_INDUSTRY_SEEDS.add(new IndustrySeed(Industries.FUELPROD, 0.1f, true));
        DEFAULT_INDUSTRY_SEEDS.add(new IndustrySeed(Industries.LIGHTINDUSTRY, 0.1f, true));
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
            JSONObject settings = Global.getSettings().getMergedJSONForMod(
                    "data/config/exerelinFactionConfig/" + factionId + ".json", ExerelinConstants.MOD_ID);
    
            playableFaction = settings.optBoolean("playableFaction", true);
            startingFaction = settings.optBoolean("startingFaction", playableFaction);
            corvusCompatible = settings.optBoolean("corvusCompatible", false);
            
            pirateFaction = settings.optBoolean("pirateFaction", false);
            isPirateNeutral = settings.optBoolean("isPirateNeutral", false);
            spawnPatrols = settings.optBoolean("spawnPatrols", true);
            hostileToAll = settings.optInt("hostileToAll", hostileToAll);
            spawnAsFactionId = settings.optString("spawnAsFactionId", spawnAsFactionId);
            freeStart = settings.optBoolean("freeStart", false);
            ngcTooltip = settings.optString("ngcTooltip", ngcTooltip);
            
            baseFleetCostMultiplier = settings.optDouble("baseFleetCostMultiplier", 1);
            
            customRebelFaction = settings.optString("customRebelFaction", customRebelFaction);
            customRebelFleetId = settings.optString("customRebelFleetId", customRebelFleetId);
            rebelFleetSuffix = settings.optString("rebelFleetSuffix", rebelFleetSuffix);
            
            asteroidMiningFleetName = settings.optString("asteroidMiningFleetName", asteroidMiningFleetName);
            gasMiningFleetName = settings.optString("gasMiningFleetName", gasMiningFleetName);
            invasionFleetName = settings.optString("invasionFleetName", invasionFleetName);
            invasionSupportFleetName = settings.optString("invasionSupportFleetName", invasionSupportFleetName);
            defenceFleetName = settings.optString("defenceFleetName", defenceFleetName);
            responseFleetName = settings.optString("responseFleetName", responseFleetName);
            
            positiveDiplomacyExtra = settings.optInt("positiveDiplomacyExtra");
            negativeDiplomacyExtra = settings.optInt("negativeDiplomacyExtra");
            
            freeMarket = settings.optBoolean("freeMarket", freeMarket);
            marketSpawnWeight = (float)settings.optDouble("marketSpawnWeight", marketSpawnWeight);
            tariffMult = (float)settings.optDouble("tariffMult", tariffMult);
            
            invasionStrengthBonusAttack = (float)settings.optDouble("invasionStrengthBonusAttack", 0);
            invasionStrengthBonusDefend = (float)settings.optDouble("invasionStrengthBonusDefend", 0);
            invasionFleetSizeMod = (float)settings.optDouble("invasionFleetSizeMod", 0);
            responseFleetSizeMod = (float)settings.optDouble("responseFleetSizeMod", 0);
            invasionPointMult = (float)settings.optDouble("invasionPointMult", invasionPointMult);
            patrolSizeMult = (float)settings.optDouble("patrolSizeMult", patrolSizeMult);
            vengeanceFleetSizeMult = (float)settings.optDouble("vengeanceFleetSizeMult", vengeanceFleetSizeMult);
            factionIdForHqResponse = settings.optString("factionIdForHqResponse", factionIdForHqResponse);
            
            dropPrisoners = settings.optBoolean("dropPrisoners", dropPrisoners);
            noHomeworld = settings.optBoolean("noHomeworld", noHomeworld);
            showIntelEvenIfDead = settings.optBoolean("showIntelEvenIfDead", showIntelEvenIfDead);
            
            allowAgentActions = settings.optBoolean("allowAgentActions", allowAgentActions);
            allowPrisonerActions = settings.optBoolean("allowPrisonerActions", allowPrisonerActions);
            
            directoryUseShortName = settings.optBoolean("directoryUseShortName", directoryUseShortName);
            difficultyString = settings.optString("difficultyString", difficultyString);
            
            if (settings.has("miningVariantsOrWings"))
                miningVariantsOrWings = Arrays.asList(ExerelinUtils.JSONArrayToStringArray(settings.getJSONArray("miningVariantsOrWings")));
            
            loadDefenceStations(settings);
            
            loadCustomStations(settings);
            
            // Diplomacy
            disableDiplomacy = settings.optBoolean("disableDiplomacy", disableDiplomacy);
            
            if (settings.has("factionsLiked"))
                factionsLiked = ExerelinUtils.JSONArrayToStringArray(settings.getJSONArray("factionsLiked"));
            if (settings.has("factionsDisliked"))
                factionsDisliked = ExerelinUtils.JSONArrayToStringArray(settings.getJSONArray("factionsDisliked"));
            if (settings.has("factionsNeutral"))
                factionsNeutral = ExerelinUtils.JSONArrayToStringArray(settings.getJSONArray("factionsNeutral"));
            
            for (String factionId : factionsLiked)
            {
                startRelationships.put(factionId, DiplomacyManager.STARTING_RELATIONSHIP_WELCOMING);
            }
            for (String factionId : factionsDisliked)
            {
                startRelationships.put(factionId, DiplomacyManager.STARTING_RELATIONSHIP_HOSTILE);
            }
            for (String factionId : factionsNeutral)
            {
                startRelationships.put(factionId, 0f);
            }
                        
            fillRelationshipMap(settings, minRelationships, "minRelationships");
            fillRelationshipMap(settings, maxRelationships, "maxRelationships");
            fillRelationshipMap(settings, startRelationships, "startRelationships");
            fillRelationshipMap(settings, diplomacyPositiveChance, "diplomacyPositiveChance");
            fillRelationshipMap(settings, diplomacyNegativeChance, "diplomacyNegativeChance");
            
            if (!diplomacyPositiveChance.containsKey("default"))
                diplomacyPositiveChance.put("default", 1f);
            if (!diplomacyNegativeChance.containsKey("default"))
                diplomacyNegativeChance.put("default", 1f);
            
            loadDispositions(settings);
            
            noRandomizeRelations = settings.optBoolean("noRandomizeRelations", noRandomizeRelations);
            noSyncRelations = settings.optBoolean("noSyncRelations", noSyncRelations);
            
            // morality
            if (settings.has("morality"))
            {
                try {
                    String moralityName = StringHelper.flattenToAscii(settings.getString("morality").toUpperCase());
                    morality = Morality.valueOf(moralityName);
                } catch (IllegalArgumentException ex) {
                    // do nothing
                    Global.getLogger(this.getClass()).warn("Invalid morality entry for faction " + this.factionId + ": " 
                            + settings.getString("morality"));
                }
            }
            else
            {
                if (pirateFaction) morality = Morality.EVIL;
                else if (isPirateNeutral) morality = Morality.AMORAL;
            }
            //Global.getLogger(this.getClass()).info("Faction " + factionId + " has morality " + morality.toString());
            
            // alignments
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
            loadVengeanceNames(settings);
            
            loadStartShips(settings);
            
            // industry
            if (settings.has("industrySeeds"))
            {
                JSONArray seedsJson = settings.getJSONArray("industrySeeds");
                for (int i = 0; i < seedsJson.length(); i++)
                {
                    JSONObject seedJson = seedsJson.getJSONObject(i);
                    String id = seedJson.getString("id");
                    float mult = (float)seedJson.getDouble("mult");
                    boolean roundUp = seedJson.optBoolean("roundUp", true);
                    industrySeeds.add(new IndustrySeed(id, mult, roundUp));
                }
            }
            else
                industrySeeds = DEFAULT_INDUSTRY_SEEDS;
            
            if (settings.has("industrySpawnMults"))
            {
                JSONObject multsJson = settings.getJSONObject("industrySpawnMults");
                Iterator<?> keys = multsJson.keys();
                while( keys.hasNext() ) {
                    String key = (String)keys.next();
                    float value = (float)multsJson.optDouble(key, 0);
                    industrySpawnMults.put(key, value);
                }
            }
            
            if (settings.has("bonusSeeds"))
            {
                JSONArray seedsJson = settings.getJSONArray("bonusSeeds");
                for (int i = 0; i < seedsJson.length(); i++)
                {
                    JSONObject seedJson = seedsJson.getJSONObject(i);
                    String id = seedJson.getString("id");
                    int count = seedJson.optInt("count", 0);
                    float mult = (float)seedJson.optDouble("mult", -1);
                    bonusSeeds.add(new BonusSeed(id, count, mult));
                }
            }
            
        } catch(IOException | JSONException ex)
        {
            Global.getLogger(this.getClass()).error("Failed to load faction config for " + factionId + ": " + ex);
        }
        
        if (miningVariantsOrWings.isEmpty())
        {
            miningVariantsOrWings = Arrays.asList(DEFAULT_MINERS);
        }
    }
    
    void fillRelationshipMap(JSONObject factionSettings, Map<String, Float> map, String configKey)
    {
        try {
            if (!factionSettings.has(configKey)) return;
            JSONObject json = factionSettings.getJSONObject(configKey);

            Iterator<?> keys = json.keys();
            while( keys.hasNext() ) {
                String key = (String)keys.next();
                float value = (float)json.getDouble(key);
                map.put(key, value);
            }
			if (map.containsKey("default"))
			{
				float def = map.get("default");
				for (FactionAPI faction : Global.getSector().getAllFactions())
				{
					String thisId = faction.getId();
					if (!map.containsKey(thisId))
						map.put(thisId, def);
				}
				map.remove("default");
			}
			
        } catch (Exception ex) {
            Global.getLogger(this.getClass()).error("Failed to load diplomacy map " + configKey, ex);
        }
    }
    
    float guessDispositionTowardsFaction(String factionId)
    {
        float posChance = getDiplomacyPositiveChance(factionId);
        float negChance = getDiplomacyNegativeChance(factionId);
        
        return (posChance - negChance) * 25;
    }
    
    void loadDispositions(JSONObject factionSettings)
    {
        Map<String, Float> dispositions = new HashMap<>();
        if (factionSettings.has("dispositions"))
        {
            fillRelationshipMap(factionSettings, dispositions, "dispositions");
        }
        // no dispositions table, estimate based on diplomacy positive/negative chances
        else
        {
            List<String> factions = new ArrayList<>();
            Iterator<String> positive = diplomacyPositiveChance.keySet().iterator();
            while (positive.hasNext())
            {
                factions.add(positive.next());
            }
            Iterator<String> negative = diplomacyNegativeChance.keySet().iterator();
            while (negative.hasNext())
            {
                factions.add(negative.next());
            }
            for (String factionId : factions)
            {
                float disp = guessDispositionTowardsFaction(factionId);
                Global.getLogger(this.getClass()).info("Disposition of " + this.factionId + " towards " + factionId + " is " + disp);
                dispositions.put(factionId, disp);
            }
            dispositions.remove("default");
        }
        this.dispositions = dispositions;
    }

    float getMaxRelationship(String factionId)
    {
        if (!maxRelationships.containsKey(factionId))
            return 1;
        return maxRelationships.get(factionId);
    }
    
    float getMinRelationship(String factionId)
    {
        if (!minRelationships.containsKey(factionId))
            return -1;
        return minRelationships.get(factionId);
    }
    
    public static float getMaxRelationship(String factionId1, String factionId2)
    {
        float max1 = ExerelinConfig.getExerelinFactionConfig(factionId1).getMaxRelationship(factionId2);
        float max2 = ExerelinConfig.getExerelinFactionConfig(factionId2).getMaxRelationship(factionId1);
        return Math.min(max1, max2);
    }
    
    public static float getMinRelationship(String factionId1, String factionId2)
    {
        float min1 = ExerelinConfig.getExerelinFactionConfig(factionId1).getMinRelationship(factionId2);
        float min2 = ExerelinConfig.getExerelinFactionConfig(factionId2).getMinRelationship(factionId1);
        return Math.max(min1, min2);
    }
    
    float getDiplomacyPositiveChance(String factionId)
    {
        if (diplomacyPositiveChance.containsKey(factionId))
            return diplomacyPositiveChance.get(factionId);
        if (diplomacyPositiveChance.containsKey("default"))
            return diplomacyPositiveChance.get("default");
        return 1;
    }
    
    float getDiplomacyNegativeChance(String factionId)
    {
        if (diplomacyNegativeChance.containsKey(factionId))
            return diplomacyNegativeChance.get(factionId);
        if (diplomacyNegativeChance.containsKey("default"))
            return diplomacyNegativeChance.get("default");
        return 1;
    }
    
    public static float getDiplomacyPositiveChance(String factionId1, String factionId2)
    {
        float chance1mod = ExerelinConfig.getExerelinFactionConfig(factionId1).getDiplomacyPositiveChance(factionId2) - 1;
        float chance2mod = ExerelinConfig.getExerelinFactionConfig(factionId2).getDiplomacyPositiveChance(factionId1) - 1;
        if (Math.abs(chance1mod) > Math.abs(chance2mod))
            return chance1mod + 1;
        else
            return chance2mod + 1;
    }
    
    public static float getDiplomacyNegativeChance(String factionId1, String factionId2)
    {
        float chance1mod = ExerelinConfig.getExerelinFactionConfig(factionId1).getDiplomacyNegativeChance(factionId2) - 1;
        float chance2mod = ExerelinConfig.getExerelinFactionConfig(factionId2).getDiplomacyNegativeChance(factionId1) - 1;
        if (Math.abs(chance1mod) > Math.abs(chance2mod))
            return chance1mod + 1;
        else
            return chance2mod + 1;
    }
    
    public float getDisposition(String factionId)
    {
        if (dispositions.containsKey(factionId))
            return dispositions.get(factionId);
        return 0;
    }
    
    public static boolean canCeasefire(String factionId1, String factionId2)
    {
		if (DiplomacyManager.isRandomFactionRelationships()) return true;
        if (ExerelinConfig.useRelationshipBounds && getMaxRelationship(factionId1, factionId2) < -0.5) return false;
        if (getDiplomacyPositiveChance(factionId1, factionId2) <= 0) return false;
        return true;
    }
    
    protected void loadCustomStations(JSONObject factionSettings) throws JSONException
    {
        if (!factionSettings.has("customStations"))
            return;
        
        JSONArray array = factionSettings.optJSONArray("customStations");
        if (array == null || array.length() == 0)
            return;
        
        boolean reverseCompat = array.get(0) instanceof String;
        
        for (int i=0; i<array.length(); i++)
        {
            if (reverseCompat)
            {
                customStations.add(new CustomStation(array.getString(i)));
                continue;
            }
            JSONObject stationDef = array.getJSONObject(i);
            CustomStation station = new CustomStation(stationDef.getString("entity"));
            if (stationDef.has("minSize")) station.minSize = stationDef.getInt("minSize");
            if (stationDef.has("maxSize")) station.maxSize = stationDef.getInt("maxSize");
            customStations.add(station);
        }
    }
    
    protected void loadDefenceStations(JSONObject factionSettings) throws JSONException
    {
        if (!factionSettings.has("defenceStations"))
        {
            defenceStations = DEFAULT_DEFENCE_STATIONS;
            return;
        }            
        
        JSONArray array = factionSettings.optJSONArray("defenceStations");
        if (array == null || array.length() == 0)
            return;
        
        for (int i=0; i<array.length(); i++)
        {
            JSONObject defJson;
            // reverse compatibility: don't break on faction configs with old defence station list
            try {
                defJson = array.getJSONObject(i);
            } catch (JSONException ex) {
                defenceStations = DEFAULT_DEFENCE_STATIONS;
                return;
            }
            List<String> ids = ExerelinUtils.JSONArrayToArrayList(defJson.getJSONArray("ids"));
            float weight = (float)defJson.optDouble("weight", 1);
            
            defenceStations.add(new DefenceStationSet(weight, ids));
        }
    }
    
    public String getRandomCustomStation(int size, Random rand)
    {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(rand);
        for (CustomStation station : customStations)
        {
            if (station.maxSize < size || station.minSize > size) continue;
            picker.add(station.customEntityId);
        }
        return picker.pick();
    }
    
    public String getRandomDefenceStation(Random rand, int sizeIndex)
    {
        if (defenceStations.isEmpty()) return null;
        WeightedRandomPicker<DefenceStationSet> picker = new WeightedRandomPicker<>(rand);
        for (DefenceStationSet set : defenceStations)
        {
            picker.add(set, set.weight);
        }
        DefenceStationSet set = picker.pick();
        if (set == null) return null;
        if (set.industryIds.isEmpty()) return null;
        
        sizeIndex = Math.min(sizeIndex, set.industryIds.size() - 1);
        return set.industryIds.get(sizeIndex);
    }
    
    public float getIndustryTypeMult(String defId)
    {
        if (!industrySpawnMults.containsKey(defId))
            return 1;
        Global.getLogger(this.getClass()).info("What the fuck is this, " 
                + industrySpawnMults.get(defId) + ", " + industrySpawnMults.get(defId).getClass().getName());
        return (float)industrySpawnMults.get(defId);
    }
    
    public List<String> getStartShipList(JSONArray array) throws JSONException
    {
        List<String> list = ExerelinUtils.JSONArrayToArrayList(array);
        if (!isStartingFleetValid(list)) return null;
        return list;
    }
    
    public void getStartShipTypeIfAvailable(JSONObject settings, String key, StartFleetType type) throws JSONException
    {
        if (!settings.has(key)) return;
		StartFleetSet set = new StartFleetSet(type);
		
		JSONArray allFleets = settings.getJSONArray(key);
		
		// reverse compatibility
		Object firstItem = allFleets.get(0);
		if (firstItem instanceof String)
		{
			set.addFleet(getStartShipList(settings.getJSONArray(key)));
		}
		else
		{
			for (int i=0; i<allFleets.length(); i++)
			{
				JSONArray fleetJson = allFleets.optJSONArray(i);
				if (fleetJson == null) continue;
				List<String> fleet = ExerelinUtils.JSONArrayToArrayList(fleetJson);
				if (!isStartingFleetValid(fleet)) continue;
				set.addFleet(fleet);
			}
		}
		
		if (set.getNumFleets() > 0)
			startShips.put(type, set);
    }
    
    protected void loadVengeanceNames(JSONObject settings) throws JSONException
    {
        if (settings.has("vengeanceLevelNames"))
            vengeanceLevelNames = ExerelinUtils.JSONArrayToArrayList(settings.getJSONArray("vengeanceLevelNames"));
        if (settings.has("vengeanceFleetNames"))
            vengeanceFleetNames = ExerelinUtils.JSONArrayToArrayList(settings.getJSONArray("vengeanceFleetNames"));
        if (settings.has("vengeanceFleetNamesSingle"))
            vengeanceFleetNamesSingle = ExerelinUtils.JSONArrayToArrayList(settings.getJSONArray("vengeanceFleetNamesSingle"));
    }
    
    protected void loadStartShips(JSONObject settings) throws JSONException
    {
        getStartShipTypeIfAvailable(settings, "startShipsSolo", StartFleetType.SOLO);
        getStartShipTypeIfAvailable(settings, "startShipsCombatSmall", StartFleetType.COMBAT_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsTradeSmall", StartFleetType.TRADE_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsCombatLarge", StartFleetType.COMBAT_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsTradeLarge", StartFleetType.TRADE_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsCarrierSmall", StartFleetType.CARRIER_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsCarrierLarge", StartFleetType.CARRIER_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsSuper", StartFleetType.SUPER);
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
        List<ShipRolePick> picks = faction.pickShip(picker.pick(), ShipPickParams.priority(), null, picker.getRandom());
        for (ShipRolePick pick : picks)
            list.add(pick.variantId);
        if (clear) picker.clear();
    }
    
    // lol
    protected List<String> getRandomStartShipsForTypeTemplars(StartFleetType type)
    {
        List<String> ships = new ArrayList<>();
        
        WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();
        
        if (type == StartFleetType.COMBAT_LARGE)
        {
            rolePicker.add(ShipRoles.COMBAT_MEDIUM, 1);	// Crusader
            rolePicker.add(ShipRoles.ESCORT_MEDIUM, 1);	// Crusader
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.ESCORT_SMALL, 2);	// Jesuit
            rolePicker.add(ShipRoles.COMBAT_SMALL, 1);	// Martyr or Jesuit
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);	// Martyr, sometimes Jesuit
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.FAST_ATTACK, 2);	// Martyr, sometimes Jesuit
            //rolePicker.add(ShipRoles.FIGHTER, 1);	// Teuton (no Smiter)
            
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.COMBAT_SMALL)
        {
            //rolePicker.add(ShipRoles.COMBAT_SMALL, 2);	// Martyr or Jesuit
            rolePicker.add(ShipRoles.ESCORT_SMALL, 2);	// Jesuit
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 1);	// Martyr or Jesuit
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);	// Martyr, sometimes Jesuit
            //rolePicker.add(ShipRoles.FIGHTER, 1);	// Teuton (no Smiter)
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.SOLO)
        {
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);	// Martyr, sometimes Jesuit
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.CARRIER_LARGE)
        {
            rolePicker.add(ShipRoles.CARRIER_LARGE, 1);	// Archbishop (lol)
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
        
        if (type == StartFleetType.COMBAT_LARGE)
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
        else if (type == StartFleetType.TRADE_LARGE)
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
        else if (type == StartFleetType.CARRIER_LARGE)
        {
            rolePicker.add(ShipRoles.CARRIER_MEDIUM, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_MEDIUM, 2);
            rolePicker.add(ShipRoles.ESCORT_MEDIUM, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
            rolePicker.add(ShipRoles.ESCORT_SMALL, 1);
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.COMBAT_SMALL)
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
        else if (type == StartFleetType.TRADE_SMALL)
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
        else if (type == StartFleetType.CARRIER_SMALL)
        {
            rolePicker.add(ShipRoles.CARRIER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
            rolePicker.add(ShipRoles.ESCORT_SMALL, 1);
            rolePicker.add(ShipRoles.FAST_ATTACK, 1);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
            
            rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
            rolePicker.add(ShipRoles.ESCORT_SMALL, 2);
            rolePicker.add(ShipRoles.FAST_ATTACK, 2);
            rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
            pickShipsAndAddToList(rolePicker, ships, true);
        }
        else if (type == StartFleetType.SOLO)
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
     * Checks if the provided list of starting ships is valid (variants actually exist).
     * @param ships A List of variant/wing IDs
     * @return True if the ship set is valid, false otherwise
     */
    protected boolean isStartingFleetValid(List<String> ships)
    {
        for (String variantId : ships)
        {
            if (!Global.getSettings().doesVariantExist(variantId))
                return false;
        }
        return true;
    }
    
    /**
     * Checks if the provided list of starting ships is valid 
     * (must have at least one non-fighter wing with nonzero cargo capacity)
     * @param ships A List of variant/wing IDs
     * @return True if the ship set is valid, false otherwise
     */
    protected boolean isRandomStartingFleetValid(List<String> ships)
    {
        for (String variantId : ships)
        {
            FleetMemberAPI temp = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            if (!temp.isFighterWing() && temp.getCargoCapacity() > 0 && temp.getFuelCapacity() > 0)
                return true;
        }
        return false;
    }
    
    /**
     * Gets a list of ships to give to the player at start, based on the chosen starting fleet type.
     * Can use the predefined ships in the faction config, or random ones based on ship roles.
     * @param typeStr
     * @param allowFallback If true, return the specified solo start ship if the chosen type is not available,
     * or a Wolf if that isn't available either
     * @return The currently selected fleet for that type, which is a list of variant IDs
     */
    public List<String> getStartFleetForType(String typeStr, boolean allowFallback)
    {
        StartFleetType type = StartFleetType.getType(typeStr);
                
        if (ExerelinSetupData.getInstance().randomStartShips
				&& (type != StartFleetType.SUPER)
				&& (startShips.containsKey(type)))
        {
            int tries = 0;
            boolean valid = false;
            List<String> result = null;
            while (!valid && tries < 10)
            {
                result = getRandomStartShipsForType(type);
                valid = isRandomStartingFleetValid(result);
                tries++;
            }
            if (valid) return result;
        }
        
        if (startShips.containsKey(type))
            return startShips.get(type).getCurrentFleet();
        
        if (!allowFallback) return null;
        
        if (startShips.containsKey(StartFleetType.SOLO))
            return startShips.get(StartFleetType.SOLO).getCurrentFleet();
        
        return Arrays.asList(new String[]{"wolf_Starting"});
    }
	
	/**
	 * Cycles through the available fleets for all starting fleet types.
	 */
	public void cycleStartFleets()
    {
        for (Map.Entry<StartFleetType, StartFleetSet> tmp : startShips.entrySet())
		{
			tmp.getValue().incrementIndex();
		}
	}
	
	public int getNumStartFleetsForType(String typeStr)
	{
		StartFleetType type = StartFleetType.getType(typeStr);
		if (!startShips.containsKey(type)) return 0;
		return startShips.get(type).getNumFleets();
	}
	
	public int getStartFleetIndexForType(String typeStr)
	{
		StartFleetType type = StartFleetType.getType(typeStr);
		if (!startShips.containsKey(type)) return -1;
		return startShips.get(type).index;
	}

	/**
	 * Contains one or more fleets for a given starting fleet type
	 */
	public static class StartFleetSet {
		public StartFleetType type;
		public List<List<String>> fleets = new ArrayList<>();
		public int index = 0;
		
		public StartFleetSet(StartFleetType type)
		{
			this.type = type;
		}
		
		public void incrementIndex()
		{
			index++;
			if (index >= fleets.size()) index = 0;
		}
		
		public List<String> getCurrentFleet()
		{
			if (fleets.isEmpty()) return null;
			return fleets.get(index);
		}
		
		public int getNumFleets()
		{
			return fleets.size();
		}
		
		public void addFleet(List<String> fleet)
		{
			if (fleet == null) return;
			fleets.add(fleet);
		}
	}
	
	public static class CustomStation
	{
		public String customEntityId;
		public int minSize = 0;
		public int maxSize = 99;
		
		public CustomStation(String entityId)
		{
			this.customEntityId = entityId;
		}
	}
	
	public static class IndustrySeed
	{
		public String industryId;
		public float mult;
		public boolean roundUp;
		
		public IndustrySeed(String industryId, float mult, boolean roundUp)
		{
			this.industryId = industryId;
			this.mult = mult;
			this.roundUp = roundUp;
		}
	}
	
	public static class BonusSeed
	{
		public String id;
		public float mult;
		public int count;
		
		public BonusSeed(String id, int count, float mult)
		{
			this.id = id;
			this.count = count;
			this.mult = mult;
		}
	}
	
	public static class DefenceStationSet
	{
		public float weight;
		public List<String> industryIds;
		
		public DefenceStationSet(float weight, String... industryIds)
		{
			this.weight = weight;
			this.industryIds = new ArrayList<>();
			for (String industryId : industryIds)
				this.industryIds.add(industryId);
		}
		
		public DefenceStationSet(float weight, List<String> industryIds)
		{
			this.weight = weight;
			this.industryIds = industryIds;
		}
	}
	
	public static enum StartFleetType {
		SOLO, COMBAT_SMALL, TRADE_SMALL,COMBAT_LARGE,TRADE_LARGE, 
		CARRIER_SMALL, CARRIER_LARGE, SUPER;
		
		public static StartFleetType getType(String str)
		{
			return StartFleetType.valueOf(str.toUpperCase());
		}
	}
	
	public static enum Morality {GOOD, NEUTRAL, AMORAL, EVIL}
}
