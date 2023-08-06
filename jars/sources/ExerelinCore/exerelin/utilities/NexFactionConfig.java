package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

public class NexFactionConfig
{
    public static final String[] DEFAULT_MINERS = {"venture_Outdated", "shepherd_Frontier"};
    public static final List<DefenceStationSet> DEFAULT_DEFENCE_STATIONS = new ArrayList<>();
    public static final List<IndustrySeed> DEFAULT_INDUSTRY_SEEDS = new ArrayList<>();
    public static final Map<Alignment, Float> DEFAULT_ALIGNMENTS = new HashMap<>();
    
    public static Logger log = Global.getLogger(NexFactionConfig.class);
    
    public String factionId;
    public boolean playableFaction = true;
    public boolean startingFaction = true;
    public boolean enabledByDefault = true;
    public boolean corvusCompatible = false;
    public boolean isBuiltIn = false;
    public String spawnAsFactionId = null;
    public boolean freeStart = false;
    public String ngcTooltip = null;
    public boolean isPlayerRuled = false;
   
    public boolean pirateFaction = false;
    public boolean isPirateNeutral = false;
    
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
    protected Map<Alignment, Float> alignments = new HashMap<>(DEFAULT_ALIGNMENTS);
    public Morality morality = Morality.NEUTRAL;
    public List<String> diplomacyTraits = new ArrayList<>();
    public boolean allowRandomDiplomacyTraits = true;
    public boolean noSyncRelations = false;
    public boolean noRandomizeRelations = false;
    public boolean useConfigRelationshipsInNonRandomSector = false;
    
    // economy and such
    public float marketSpawnWeight = 1;	// what proportion of procgen markets this faction gets
    public boolean freeMarket = false;
    public float tariffMult = 1;
    
    public List<IndustrySeed> industrySeeds = new ArrayList<>();
    public Map<String, Float> industrySpawnMults = new HashMap<>();
    public List<BonusSeed> bonusSeeds = new ArrayList<>();
    
    // invasions and stuff
    public boolean canInvade = true;
    public boolean invasionOnlyRetake = false;
    @Deprecated public float invasionStrengthBonusAttack = 0;    // marines
    @Deprecated public float invasionStrengthBonusDefend = 0;
    public float invasionFleetSizeMod = 0;	// ships
    public float responseFleetSizeMod = 0;
    public float invasionPointMult = 1;	// point accumulation for launching invasions
    @Deprecated public float patrolSizeMult = 1;
    public float vengeanceFleetSizeMult = 1;
    public String factionIdForHqResponse = null;
    public boolean raidsFromBases = false;
    public Map<String, Object> groundBattleSettings;
    
    // special forces
    public int specialForcesMaxFleets = 2;
    public float specialForcesCountMult = 1;	// TODO
    public float specialForcesPointMult = 1;
    public float specialForcesSizeMult = 1;
    public String specialForcesNamerClass = "exerelin.campaign.intel.specialforces.namer.CommanderNamer";
    public Map<String, Float> specialForcesFlagshipVariants = new HashMap<>();
    
    // misc
    public boolean dropPrisoners = true;
    public boolean noHomeworld = false;	// don't give this faction a HQ in procgen
    public boolean showIntelEvenIfDead = false;	// intel tab
    public boolean noMissionTarget = false;
    public List<String> stabilizeCommodities = null;
    
    public boolean allowAgentActions = true;
    public boolean allowPrisonerActions = true;
    
    public boolean directoryUseShortName = false;
    public String difficultyString = "";
    
    public boolean noStartingContact = false;
    
    // vengeance
    public List<String> vengeanceLevelNames = new ArrayList<>();
    public List<String> vengeanceFleetNames = new ArrayList<>();
    public List<String> vengeanceFleetNamesSingle = new ArrayList<>();
    
    // colonies
    public float colonyExpeditionChance = 0;
    public String colonyTargetValuator = "exerelin.campaign.colony.ColonyTargetValuator";
    public float maxColonyDistance = 18;
	public String factionConditionSubplugin = null;
			
    // misc. part 2
    public List<CustomStation> customStations = new ArrayList<>();
    public List<DefenceStationSet> defenceStations = new ArrayList<>();
    
    public List<String> miningVariantsOrWings = new ArrayList<>();
    
    public Map<StartFleetType, StartFleetSet> startShips = new HashMap<>();
    public List<SpecialItemSet> startSpecialItems = new ArrayList<>();

    // strategy AI
    public Map<String, Float> strategyPriorityMults = new HashMap<>();
    
    // set defaults
    static {
        for (Alignment alignment : Alignment.values())
        {
            //if (alignment.redirect != null) continue;
            DEFAULT_ALIGNMENTS.put(alignment, 0f);
        }
        
        DefenceStationSet low = new DefenceStationSet(1, Industries.ORBITALSTATION, Industries.BATTLESTATION, Industries.STARFORTRESS);
        DefenceStationSet mid = new DefenceStationSet(0.75f, Industries.ORBITALSTATION_MID, Industries.BATTLESTATION_MID, Industries.STARFORTRESS_MID);
        DefenceStationSet high = new DefenceStationSet(0.5f, Industries.ORBITALSTATION_HIGH, Industries.BATTLESTATION_HIGH, Industries.STARFORTRESS_HIGH);
        DEFAULT_DEFENCE_STATIONS.addAll(Arrays.asList(low, mid, high));
        
        DEFAULT_INDUSTRY_SEEDS.add(new IndustrySeed(Industries.HEAVYINDUSTRY, 0.1f, 0, true));
        //DEFAULT_INDUSTRY_SEEDS.add(new IndustrySeed(Industries.FUELPROD, 0.1f, true));
        //DEFAULT_INDUSTRY_SEEDS.add(new IndustrySeed(Industries.LIGHTINDUSTRY, 0.1f, true));
    }

    public NexFactionConfig(String factionId)
    {
        this.factionId = factionId;
        this.loadFactionConfig();
    }
    
    public void clearLists() {
        diplomacyTraits.clear();
        vengeanceLevelNames.clear();
        vengeanceFleetNames.clear();
        vengeanceFleetNamesSingle.clear();
        startSpecialItems.clear();
        customStations.clear();
        defenceStations.clear();
        miningVariantsOrWings.clear();
    }
    public void loadFactionConfig()
    {
        clearLists();    // in case someone asks it to load config again, as Vayra's does
        
        try
        {
            JSONObject settings = Global.getSettings().getMergedJSONForMod(
                    "data/config/exerelinFactionConfig/" + factionId + ".json", ExerelinConstants.MOD_ID);
    
            playableFaction = settings.optBoolean("playableFaction", true);
            startingFaction = settings.optBoolean("startingFaction", playableFaction);
            enabledByDefault = settings.optBoolean("enabledByDefault", startingFaction);
            corvusCompatible = settings.optBoolean("corvusCompatible", false);
            
            pirateFaction = settings.optBoolean("pirateFaction", false);
            isPirateNeutral = settings.optBoolean("isPirateNeutral", false);
            hostileToAll = settings.optInt("hostileToAll", hostileToAll);
            spawnAsFactionId = settings.optString("spawnAsFactionId", spawnAsFactionId);
            freeStart = settings.optBoolean("freeStart", false);
            ngcTooltip = settings.optString("ngcTooltip", ngcTooltip);
            isPlayerRuled = settings.optBoolean("isPlayerRuled", isPlayerRuled);
            
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
            
            canInvade = settings.optBoolean("canInvade", canInvade);
			invasionOnlyRetake = settings.optBoolean("invasionOnlyRetake", invasionOnlyRetake);
            invasionStrengthBonusAttack = (float)settings.optDouble("invasionStrengthBonusAttack", 0);
            invasionStrengthBonusDefend = (float)settings.optDouble("invasionStrengthBonusDefend", 0);
            invasionFleetSizeMod = (float)settings.optDouble("invasionFleetSizeMod", 0);
            responseFleetSizeMod = (float)settings.optDouble("responseFleetSizeMod", 0);
            invasionPointMult = (float)settings.optDouble("invasionPointMult", invasionPointMult);
            patrolSizeMult = (float)settings.optDouble("patrolSizeMult", patrolSizeMult);
            vengeanceFleetSizeMult = (float)settings.optDouble("vengeanceFleetSizeMult", vengeanceFleetSizeMult);
            factionIdForHqResponse = settings.optString("factionIdForHqResponse", factionIdForHqResponse);
            raidsFromBases = settings.optBoolean("raidsFromBases", raidsFromBases);
            
            // ground battle
            if (settings.has("groundBattleSettings")) {
                JSONObject gbsJson = settings.getJSONObject("groundBattleSettings");
                groundBattleSettings = NexUtils.jsonToMap(gbsJson);
            }
            
            dropPrisoners = settings.optBoolean("dropPrisoners", dropPrisoners);
            noHomeworld = settings.optBoolean("noHomeworld", noHomeworld);
            showIntelEvenIfDead = settings.optBoolean("showIntelEvenIfDead", showIntelEvenIfDead);
            noMissionTarget = settings.optBoolean("noMissionTarget", noMissionTarget);
            noStartingContact = settings.optBoolean("noStartingContact", noStartingContact);
            
            allowAgentActions = settings.optBoolean("allowAgentActions", allowAgentActions);
            allowPrisonerActions = settings.optBoolean("allowPrisonerActions", allowPrisonerActions);
            
            directoryUseShortName = settings.optBoolean("directoryUseShortName", directoryUseShortName);
            difficultyString = settings.optString("difficultyString", difficultyString);
            
            colonyExpeditionChance = (float)settings.optDouble("colonyExpeditionChance", colonyExpeditionChance);
            colonyTargetValuator = settings.optString("colonyTargetValuator", colonyTargetValuator);
            maxColonyDistance = (float)settings.optDouble("maxColonyDistance", maxColonyDistance);
            factionConditionSubplugin = settings.optString("factionConditionSubplugin", factionConditionSubplugin);
            
            specialForcesMaxFleets = settings.optInt("specialForcesMaxFleets", playableFaction ? specialForcesMaxFleets : 0);
            specialForcesCountMult = (float)settings.optDouble("specialForcesCountMult", specialForcesCountMult);
            specialForcesPointMult = (float)settings.optDouble("specialForcesPointMult", specialForcesPointMult);
            specialForcesSizeMult = (float)settings.optDouble("specialForcesSizeMult", specialForcesSizeMult);
            specialForcesNamerClass = settings.optString("specialForcesNamerClass", specialForcesNamerClass);
            if (settings.has("specialForcesFlagshipVariants")) {
                JSONObject flagJson = settings.getJSONObject("specialForcesFlagshipVariants");
                Iterator<String> keys = flagJson.sortedKeys();
                while (keys.hasNext()) {
                    String variantId = keys.next();
                    float weight = (float)flagJson.getDouble(variantId);
                    specialForcesFlagshipVariants.put(variantId, weight);
                }
            }
            
            if (settings.has("miningVariantsOrWings")) {
				miningVariantsOrWings.addAll(Arrays.asList(NexUtils.JSONArrayToStringArray(
						settings.getJSONArray("miningVariantsOrWings"))));
			}
			
            loadDefenceStations(settings);
            
            loadCustomStations(settings);
            
            // Diplomacy
            disableDiplomacy = settings.optBoolean("disableDiplomacy", disableDiplomacy);
            
            if (settings.has("factionsLiked"))
                factionsLiked = NexUtils.JSONArrayToStringArray(settings.getJSONArray("factionsLiked"));
            if (settings.has("factionsDisliked"))
                factionsDisliked = NexUtils.JSONArrayToStringArray(settings.getJSONArray("factionsDisliked"));
            if (settings.has("factionsNeutral"))
                factionsNeutral = NexUtils.JSONArrayToStringArray(settings.getJSONArray("factionsNeutral"));
            
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
            
            if (settings.has("diplomacyTraits")) {
                List<String> traitsList = NexUtils.JSONArrayToArrayList(settings.getJSONArray("diplomacyTraits"));
                // validate traits
                /*
                for (String traitId : traitsList) {
                    TraitDef trait = DiplomacyTraits.getTrait(traitId);
                    if (trait == null)
                        throw new RuntimeException("Faction " + factionId + " has invalid trait " + traitId);
                }
                */
                
                diplomacyTraits.addAll(traitsList);
            }
            
            allowRandomDiplomacyTraits = settings.optBoolean("allowRandomDiplomacyTraits", allowRandomDiplomacyTraits);
            noRandomizeRelations = settings.optBoolean("noRandomizeRelations", noRandomizeRelations);
            noSyncRelations = settings.optBoolean("noSyncRelations", noSyncRelations);
            useConfigRelationshipsInNonRandomSector = settings.optBoolean("useConfigRelationshipsInNonRandomSector", useConfigRelationshipsInNonRandomSector);
            
            // morality
            if (settings.has("morality"))
            {
                try {
                    String moralityName = StringHelper.flattenToAscii(settings.getString("morality").toUpperCase());
                    morality = Morality.valueOf(moralityName);
                } catch (IllegalArgumentException ex) {
                    // do nothing
                    log.warn("Invalid morality entry for faction " + this.factionId + ": " 
                            + settings.getString("morality"));
                }
            }
            else
            {
                if (pirateFaction) morality = Morality.EVIL;
                else if (isPirateNeutral) morality = Morality.AMORAL;
            }
            //log.info("Faction " + factionId + " has morality " + morality.toString());
            
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
                        if (alignmentName.equals("HIERARCHIAL")) alignmentName = Alignment.HIERARCHICAL.toString();    // spelling fix
                        Alignment alignment = Alignment.valueOf(alignmentName);
                        alignments.put(alignment, value);
                    } catch (IllegalArgumentException ex) {
                        // do nothing
                        log.warn("Invalid alignment entry for faction " + this.factionId + ": " + key);
                    }
                }
            }
            loadVengeanceNames(settings);
			
			loadStartSpecialItems(settings);
            loadStartShips(settings);
			
			if (settings.has("stabilizeCommodities")) {
				stabilizeCommodities = NexUtils.JSONArrayToArrayList(settings.getJSONArray("stabilizeCommodities"));
			}
            
            // industry
            if (settings.has("industrySeeds"))
            {
                JSONArray seedsJson = settings.getJSONArray("industrySeeds");
                for (int i = 0; i < seedsJson.length(); i++)
                {
                    JSONObject seedJson = seedsJson.getJSONObject(i);
                    String id = seedJson.getString("id");
                    int count = seedJson.optInt("count", 0);
                    float mult = (float)seedJson.optDouble("mult", 0);
                    boolean roundUp = seedJson.optBoolean("roundUp", true);
                    industrySeeds.add(new IndustrySeed(id, mult, count, roundUp));
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
                    float mult = (float)seedJson.optDouble("mult", 0);
                    bonusSeeds.add(new BonusSeed(id, count, mult));
                }
            }

            if (settings.has("strategyPriorityMults")) {
                JSONObject multsJson = settings.getJSONObject("strategyPriorityMults");
                Iterator<?> keys = multsJson.keys();
                while( keys.hasNext() ) {
                    String key = (String)keys.next();
                    float value = (float)multsJson.optDouble(key, 0);
                    strategyPriorityMults.put(key, value);
                }
            }
            
        } catch (IOException | JSONException ex)
        {
            log.error("Failed to load faction config for " + factionId + ": " + ex);
			throw new RuntimeException(ex);
        }
        
        if (miningVariantsOrWings.isEmpty())
        {
            miningVariantsOrWings.addAll(Arrays.asList(DEFAULT_MINERS));
        }
    }
    
    protected void fillRelationshipMap(JSONObject factionSettings, Map<String, Float> map, String configKey)
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
					if (thisId.equals(factionId)) continue;
					
					if (!map.containsKey(thisId))
						map.put(thisId, def);
				}
				map.remove("default");
			}
			
        } catch (Exception ex) {
            log.error("Failed to load diplomacy map " + configKey, ex);
        }
    }
    
    float guessDispositionTowardsFaction(String factionId)
    {
        float posChance = getDiplomacyPositiveChance(factionId, true);
        float negChance = getDiplomacyNegativeChance(factionId, true);
        
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
                //log.info("Disposition of " + this.factionId + " towards " + factionId + " is " + disp);
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
    
    //runcode exerelin.utilities.NexFactionConfig.getMaxRelationship("hegemony", "al_ars")
    public static float getMaxRelationship(String factionId1, String factionId2)
    {
        if (!DiplomacyManager.getManager().getStartRelationsMode().isDefault())
            return 1;
        
        if (!NexConfig.useRelationshipBounds) return 1;
        if (factionId1 == null || factionId2 == null) return 1;
        
        if (factionId1.equals(Factions.PLAYER)) {
            if (factionId2.equals(Misc.getCommissionFactionId()))
                return 1;
        }
        else if (factionId2.equals(Factions.PLAYER)) {
            if (factionId1.equals(Misc.getCommissionFactionId()))
                return 1;
        }
        
        float max1 = NexConfig.getFactionConfig(factionId1).getMaxRelationship(factionId2);
        float max2 = NexConfig.getFactionConfig(factionId2).getMaxRelationship(factionId1);
        return Math.min(max1, max2);
    }
    
    public static float getMinRelationship(String factionId1, String factionId2)
    {
        if (!NexConfig.useRelationshipBounds) return -1;
        float min1 = NexConfig.getFactionConfig(factionId1).getMinRelationship(factionId2);
        float min2 = NexConfig.getFactionConfig(factionId2).getMinRelationship(factionId1);
        return Math.max(min1, min2);
    }
    
    public float getDiplomacyPositiveChance(String factionId)
    {
        return getDiplomacyPositiveChance(factionId, false);
    }
    
    /**
     * Chance mult for positive diplomacy events towards the other faction.
     * @param factionId
     * @param raw If false, check if start relations mode is "random" or "flatten" (chance will be 1 in that case).
     * @return
     */
    public float getDiplomacyPositiveChance(String factionId, boolean raw)
    {
        if (!raw && !DiplomacyManager.getManager().getStartRelationsMode().isDefault())
            return 1;
        
        if (diplomacyPositiveChance.containsKey(factionId))
            return diplomacyPositiveChance.get(factionId);
        if (diplomacyPositiveChance.containsKey("default"))
            return diplomacyPositiveChance.get("default");
        return 1;
    }
    
    public float getDiplomacyNegativeChance(String factionId) 
    {
        return getDiplomacyNegativeChance(factionId, false);
    }
    
    /**
     * Chance mult for negative diplomacy events towards the other faction.
     * @param factionId
     * @param raw If false, check if start relations mode is "random" or "flatten" (chance will be 1 in that case).
     * @return
     */
    public float getDiplomacyNegativeChance(String factionId, boolean raw)
    {
        if (!raw && !DiplomacyManager.getManager().getStartRelationsMode().isDefault())
            return 1;
        
        if (diplomacyNegativeChance.containsKey(factionId))
            return diplomacyNegativeChance.get(factionId);
        if (diplomacyNegativeChance.containsKey("default"))
            return diplomacyNegativeChance.get("default");
        return 1;
    }
    
    public static float getDiplomacyPositiveChance(String factionId1, String factionId2)
    {
        float chance1mod = NexConfig.getFactionConfig(factionId1).getDiplomacyPositiveChance(factionId2) - 1;
        float chance2mod = NexConfig.getFactionConfig(factionId2).getDiplomacyPositiveChance(factionId1) - 1;
        if (Math.abs(chance1mod) > Math.abs(chance2mod))
            return chance1mod + 1;
        else
            return chance2mod + 1;
    }
    
    public static float getDiplomacyNegativeChance(String factionId1, String factionId2)
    {
        float chance1mod = NexConfig.getFactionConfig(factionId1).getDiplomacyNegativeChance(factionId2) - 1;
        float chance2mod = NexConfig.getFactionConfig(factionId2).getDiplomacyNegativeChance(factionId1) - 1;
        if (Math.abs(chance1mod) > Math.abs(chance2mod))
            return chance1mod + 1;
        else
            return chance2mod + 1;
    }
    
    public float getDisposition(String factionId)
    {
        if (!DiplomacyManager.getManager().getStartRelationsMode().isDefault())
            return 0;
        
        if (dispositions.containsKey(factionId))
            return dispositions.get(factionId);
        return 0;
    }
    
    // runcode $print(exerelin.utilities.NexFactionConfig.canCeasefire("hegemony", "al_ars"))
    public static boolean canCeasefire(String factionId1, String factionId2)
    {
        // remove relationship bound clamp if both sides have random relations
        // moved the check to DiplomacyManager's getMaxRelationship
        //if (DiplomacyManager.haveRandomRelationships(factionId1, factionId2)) return true;
        
        if (DiplomacyManager.getManager().getMaxRelationship(factionId1, factionId2) <= -0.5) return false;
        if (getDiplomacyPositiveChance(factionId1, factionId2) <= 0) return false;
        return true;
    }
    
    public String getRandomSpecialForcesFlagship(Random rand) {
        if (specialForcesFlagshipVariants.isEmpty())
            return null;
        if (rand == null) rand = new Random();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(rand);
        for (String variantId : specialForcesFlagshipVariants.keySet()) {
            picker.add(variantId, specialForcesFlagshipVariants.get(variantId));
        }
        return picker.pick();
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
            List<String> ids = NexUtils.JSONArrayToArrayList(defJson.getJSONArray("ids"));
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
    
    public DefenceStationSet getRandomDefenceStationSet(Random rand) {
        if (defenceStations.isEmpty()) return null;
        WeightedRandomPicker<DefenceStationSet> picker = new WeightedRandomPicker<>(rand);
        for (DefenceStationSet set : defenceStations)
        {
            picker.add(set, set.weight);
        }
        DefenceStationSet set = picker.pick();
        if (set == null) return null;
        if (set.industryIds.isEmpty()) return null;
        
        return set;
    }
    
    public String getRandomDefenceStation(Random rand, int sizeIndex)
    {
        DefenceStationSet set = getRandomDefenceStationSet(rand);
        if (set == null || set.industryIds.isEmpty()) return null;
        sizeIndex = Math.min(sizeIndex, set.industryIds.size() - 1);
        return set.industryIds.get(sizeIndex);
    }
    
    public float getIndustryTypeMult(String defId)
    {
        if (!industrySpawnMults.containsKey(defId))
            return 1;
        return (float)industrySpawnMults.get(defId);
    }
    
    /**
	 * Gets this faction's alignments specified in config, without any ingame modifiers.
     * @return
     */
	public Map<Alignment, Float> getBaseAlignments() {
        Map<Alignment, Float> align = new HashMap<>(alignments);
        
        return align;
    }
	
	public Map<Alignment, MutableStat> createInitialAlignmentsMap() {
		Map<Alignment, MutableStat> alignMap = new HashMap<>();
		for (Alignment align : Alignment.getAlignments()) {
			MutableStat stat = new MutableStat(0);
			float base = alignments.get(align);
			stat.modifyFlat("base", base, StringHelper.getString("base"));
			
			alignMap.put(align, stat);
		}
		return alignMap;
	}
	
	public Map<Alignment, MutableStat> storeInitialAlignmentsInMemory() {
		MemoryAPI mem = Global.getSector().getFaction(factionId).getMemoryWithoutUpdate();
		Map<Alignment, MutableStat> alignMap = createInitialAlignmentsMap();
		mem.set(Alliance.MEMORY_KEY_ALIGNMENTS, alignMap);
		return alignMap;
	}
	
	public void updateBaseAlignmentsInMemory() {
		Map<Alignment, MutableStat> alignMap = getAlignments();
		for (Alignment align : alignMap.keySet()) {
			MutableStat stat = alignMap.get(align);
			float base = alignments.get(align);
			stat.modifyFlat("base", base, StringHelper.getString("base"));
		}
	}
	
	public Map<Alignment, MutableStat> getAlignments() {
		FactionAPI faction;
		if (Global.getSector() == null || Global.getSector().getFaction(factionId) == null) {
			faction = Global.getSettings().createBaseFaction(factionId);
		} else {
			faction = Global.getSector().getFaction(factionId);
		}
		MemoryAPI mem = faction.getMemoryWithoutUpdate();
		
		if (!mem.contains(Alliance.MEMORY_KEY_ALIGNMENTS)) {
			storeInitialAlignmentsInMemory();
		}
		return (Map<Alignment, MutableStat>)mem.get(Alliance.MEMORY_KEY_ALIGNMENTS);
	}
	
	public Map<Alignment, Float> getAlignmentValues() {
		Map<Alignment, MutableStat> alignMap = getAlignments();
		Map<Alignment, Float> alignMapConverted = new HashMap<>();
		for (Alignment align : alignMap.keySet()) {
			alignMapConverted.put(align, alignMap.get(align).getModifiedValue());
		}
		return alignMapConverted;
	}
    
    public List<String> getStartShipList(JSONArray array) throws JSONException
    {
        List<String> list = NexUtils.JSONArrayToArrayList(array);
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
				List<String> fleet = NexUtils.JSONArrayToArrayList(fleetJson);
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
            vengeanceLevelNames = NexUtils.JSONArrayToArrayList(settings.getJSONArray("vengeanceLevelNames"));
        if (settings.has("vengeanceFleetNames"))
            vengeanceFleetNames = NexUtils.JSONArrayToArrayList(settings.getJSONArray("vengeanceFleetNames"));
        if (settings.has("vengeanceFleetNamesSingle"))
            vengeanceFleetNamesSingle = NexUtils.JSONArrayToArrayList(settings.getJSONArray("vengeanceFleetNamesSingle"));
    }
	
	protected void loadStartSpecialItems(JSONObject settings) throws JSONException
	{
		if (!settings.has("startSpecialItems"))
			return;
		
		//CargoAPI tester = Global.getFactory().createCargo(false);
		JSONArray allItemsJson = settings.getJSONArray("startSpecialItems");
		for (int i = 0; i < allItemsJson.length(); i++)
		{
			SpecialItemSet set = new SpecialItemSet();
			JSONArray itemsJson = allItemsJson.getJSONArray(i);
			for (int j = 0; j < itemsJson.length(); j++)
			{
				JSONArray itemJson = itemsJson.getJSONArray(j);
				Pair<String, String> item = new Pair<>(itemJson.getString(0), itemJson.getString(1));
				set.items.add(item);
				
				// validate item existence - disabled, doesn't actually detect invalid IDs
				//tester.addSpecial(new SpecialItemData(item.one, item.two), 1);
			}
			startSpecialItems.add(set);
		}
	}
    
    protected void loadStartShips(JSONObject settings) throws JSONException
    {
        getStartShipTypeIfAvailable(settings, "startShipsSolo", StartFleetType.SOLO);
        getStartShipTypeIfAvailable(settings, "startShipsCombatSmall", StartFleetType.COMBAT_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsCombatLarge", StartFleetType.COMBAT_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsTradeSmall", StartFleetType.TRADE_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsTradeLarge", StartFleetType.TRADE_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsExplorerSmall", StartFleetType.EXPLORER_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsExplorerLarge", StartFleetType.EXPLORER_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsCarrierSmall", StartFleetType.CARRIER_SMALL);
        getStartShipTypeIfAvailable(settings, "startShipsCarrierLarge", StartFleetType.CARRIER_LARGE);
        getStartShipTypeIfAvailable(settings, "startShipsSuper", StartFleetType.SUPER);
        getStartShipTypeIfAvailable(settings, "startShipsGrandFleet", StartFleetType.GRAND_FLEET);
    }
    
    /**
     * Helper method to pick ships and add to the provided <code>List</code> using a <code>WeightedRandomPicker</code>.
     * @param picker The ship role picker to use
     * @param list The list to modify
     * @param clear If true, clear the picker after use
     */
    protected void pickShipsAndAddToList(WeightedRandomPicker<String> picker, List<String> list, boolean clear)
    {
        FactionAPI faction = Global.getSettings().createBaseFaction(factionId);
        List<ShipRolePick> picks;
        try {
            picks = faction.pickShip(picker.pick(), ShipPickParams.priority(), null, picker.getRandom());
        } catch (NullPointerException npe) {    // picking role that doesn't exist
            return;
        }
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
    
    protected List<String> getShipsFromFleetFactory(float fp) 
    {
        FleetParamsV3 params = new FleetParamsV3(
                null,
                null,
                factionId,
                1.5f,
                FleetTypes.TASK_FORCE,
                fp, // combatPts
                fp/8, // freighterPts 
                fp/8, // tankerPts
                0, // transportPts
                0f, // linerPts
                0f, // utilityPts
                0f // qualityMod
        );
        params.withOfficers = false;
        
        List<String> results = new ArrayList<>();
        for (FleetMemberAPI member : FleetFactoryV3.createFleet(params).getFleetData().getMembersInPriorityOrder())
        {
            results.add(member.getVariant().getHullVariantId());
        }
        return results;
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
        int phaseDoctrine = Global.getSettings().createBaseFaction(factionId).getDoctrine().getPhaseShips();
        float phaseMult = phaseDoctrine;
        
        switch (type) {
            case COMBAT_LARGE:
                rolePicker.add(ShipRoles.COMBAT_LARGE, 1);
                rolePicker.add(ShipRoles.PHASE_LARGE, 0.25f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_MEDIUM, 3);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1);
                rolePicker.add(ShipRoles.PHASE_MEDIUM, 0.4f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.25f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case TRADE_LARGE:
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_LARGE, 1);
                rolePicker.add(ShipRoles.FREIGHTER_LARGE, 1);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_MEDIUM, 3);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 2);
                rolePicker.add(ShipRoles.FREIGHTER_MEDIUM, 3);
                rolePicker.add(ShipRoles.PHASE_MEDIUM, 0.4f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.25f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case EXPLORER_LARGE:
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_LARGE, 1);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_MEDIUM, 1);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1);
                rolePicker.add(ShipRoles.PHASE_MEDIUM, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 1);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case CARRIER_LARGE:
                rolePicker.add(ShipRoles.CARRIER_MEDIUM, 1);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_MEDIUM, 3);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1);
                rolePicker.add(ShipRoles.PHASE_MEDIUM, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 4);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case COMBAT_SMALL:
                rolePicker.add(ShipRoles.COMBAT_MEDIUM, 3);
                rolePicker.add(ShipRoles.PHASE_MEDIUM, 0.4f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 7);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.6f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 6);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.6f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case TRADE_SMALL:
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 2);
                rolePicker.add(ShipRoles.FREIGHTER_MEDIUM, 3);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 1);
                rolePicker.add(ShipRoles.COMBAT_SMALL_FOR_SMALL_FLEET, 1);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 2);
                rolePicker.add(ShipRoles.FREIGHTER_SMALL, 2);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 1);
                rolePicker.add(ShipRoles.COMBAT_SMALL_FOR_SMALL_FLEET, 1);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case EXPLORER_SMALL:
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 1);
                rolePicker.add(ShipRoles.COMBAT_SMALL_FOR_SMALL_FLEET, 1);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 2);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 1);
                rolePicker.add(ShipRoles.COMBAT_SMALL_FOR_SMALL_FLEET, 1);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 2);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case CARRIER_SMALL:
                rolePicker.add(ShipRoles.CARRIER_SMALL, 1);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
                rolePicker.add(ShipRoles.COMBAT_SMALL_FOR_SMALL_FLEET, 2);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                rolePicker.add(ShipRoles.COMBAT_SMALL, 4);
                rolePicker.add(ShipRoles.COMBAT_SMALL_FOR_SMALL_FLEET, 2);
                rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.25f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case SOLO:
                rolePicker.add(ShipRoles.COMBAT_SMALL, 2);
                rolePicker.add(ShipRoles.COMBAT_SMALL_FOR_SMALL_FLEET, 2);
                rolePicker.add(ShipRoles.PHASE_SMALL, 0.2f * phaseMult);
                pickShipsAndAddToList(rolePicker, ships, true);
                break;
            case GRAND_FLEET:
                ships = getShipsFromFleetFactory(100);
                break;
            default:
                break;
        }
        
        return ships;
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
            if (!Global.getSettings().doesVariantExist(variantId)) {
                // log.info("wtf variant does not exist: " + variantId);
                return false;
            }
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
     * Gets a list of ships to give to the player at start, based on the chosen starting fleet type.Can use the predefined ships in the faction config, or random ones based on ship roles.
     * @param typeStr
     * @param allowFallback If true, return the specified solo start ship if the chosen type is not available,
     * or a Wolf if that isn't available either
     * @param index
     * @return The currently selected fleet for that type, which is a list of variant IDs
     */
    public List<String> getStartFleetForType(String typeStr, boolean allowFallback, int index)
    {
        StartFleetType type = StartFleetType.getType(typeStr);
        boolean random = index < 0;
                
        if (random && (type != StartFleetType.SUPER) && (startShips.containsKey(type)))
        {
            int tries = 0;
            boolean valid = false;
            List<String> result = null;
            while (!valid && tries < 10)
            {
                if (factionId.equals(Factions.PLAYER))    // since player doesn't have ships to randomize
                    result = NexConfig.getFactionConfig(Factions.INDEPENDENT)
                            .getRandomStartShipsForType(type);
                else
                    result = getRandomStartShipsForType(type);
                valid = isRandomStartingFleetValid(result);
                tries++;
            }
            if (valid) return result;
        }
        
        if (startShips.containsKey(type))
            return startShips.get(type).getFleet(index);
        
        if (!allowFallback) return null;
        
        if (startShips.containsKey(StartFleetType.SOLO))
            return startShips.get(StartFleetType.SOLO).getFleet(index);
        
        return new ArrayList<>(Arrays.asList(new String[]{"wolf_Starting"}));
    }
	
	
	public int getNumStartFleetsForType(String typeStr)
	{
		StartFleetType type = StartFleetType.getType(typeStr);
		if (!startShips.containsKey(type)) return 0;
		return startShips.get(type).getNumFleets();
	}
	
	public StartFleetSet getStartFleetSet(String type) {
		return startShips.get(StartFleetType.getType(type));
	}

	/**
	 * Contains one or more fleets for a given starting fleet type
	 */
	public static class StartFleetSet {
		public StartFleetType type;
		public List<List<String>> fleets = new ArrayList<>();
		
		public StartFleetSet(StartFleetType type)
		{
			this.type = type;
		}
		
		public List<String> getFleet(int index) {
			if (index < 0) index = 0;
			if (index >= fleets.size()) index = fleets.size() - 1;
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
		public int count;
		public boolean roundUp;
		
		public IndustrySeed(String industryId, float mult, int count, boolean roundUp)
		{
			this.industryId = industryId;
			this.mult = mult;
			this.count = count;
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
	
	public static class SpecialItemSet {
		public List<Pair<String, String>> items = new ArrayList<>();
		
		public void pickItemsAndAddToCargo(CargoAPI cargo, Random random) {
			WeightedRandomPicker<Pair<String, String>> picker = new WeightedRandomPicker<>(random);
			for (Pair<String, String> item : items) {
				picker.add(item);
			}
			Pair<String, String> item = picker.pick();
			if (item != null) {
				cargo.addSpecial(new SpecialItemData(item.one, item.two), 1);
			}
		}
	}
	
	public static enum StartFleetType {
		SOLO, COMBAT_SMALL, COMBAT_LARGE, TRADE_SMALL, TRADE_LARGE,
		EXPLORER_SMALL, EXPLORER_LARGE, CARRIER_SMALL, CARRIER_LARGE, 
		SUPER, GRAND_FLEET, CUSTOM;
		
		public static StartFleetType getType(String str)
		{
			return StartFleetType.valueOf(str.toUpperCase());
		}
		
		public boolean isTrade() {
			return this == TRADE_SMALL || this == TRADE_LARGE;
		}
	}
	
	public static enum Morality {
		GOOD(Color.CYAN), 
		NEUTRAL(Color.GREEN), 
		AMORAL(Color.ORANGE), 
		EVIL(Color.RED);
		
		public final Color color;
		
		private Morality(Color color) {
			this.color = color;
		}
	}
}
