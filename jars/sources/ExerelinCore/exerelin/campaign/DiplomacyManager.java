package exerelin.campaign;

import exerelin.campaign.alliances.AllianceVoter;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import exerelin.world.VanillaSystemsGenerator;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
public class DiplomacyManager extends BaseCampaignEventListener implements EveryFrameScript
{
    public static Logger log = Global.getLogger(DiplomacyManager.class);
    
    protected static final String CONFIG_FILE = "data/config/exerelin/diplomacyConfig.json";
    protected static final String MANAGER_MAP_KEY = "exerelin_diplomacyManager";
    
    public static final List<String> disallowedFactions;
        
    protected static List<DiplomacyEventDef> eventDefs;
    protected static Map<String, DiplomacyEventDef> eventDefsById;
    
    public static final float STARTING_RELATIONSHIP_HOSTILE = -0.6f;
    public static final float STARTING_RELATIONSHIP_INHOSPITABLE = -0.4f;
    public static final float STARTING_RELATIONSHIP_WELCOMING = 0.4f;
    public static final float STARTING_RELATIONSHIP_FRIENDLY = 0.6f;
    public static final float WAR_WEARINESS_INTERVAL = 3f;
    public static final float WAR_WEARINESS_FLEET_WIN_MULT = 0.5f; // less war weariness from a fleet battle if you win
    public static final float WAR_WEARINESS_ENEMY_COUNT_MULT = 0.25f;
    public static final float PEACE_TREATY_CHANCE = 0.3f;
    public static final float MIN_INTERVAL_BETWEEN_WARS = 30f;
    
    public static final float DOMINANCE_MIN = 0.25f;
    public static final float DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD = -0.67f;
    public static final float DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD = 3f;
    
    public static final List<String> DO_NOT_RANDOMIZE = Arrays.asList(new String[]{
        "sector", "domain", "everything"
    });
    
    protected Map<String, Float> warWeariness;
    protected static float warWearinessPerInterval = 50f;
    protected static DiplomacyEventDef peaceTreatyEvent;
    protected static DiplomacyEventDef ceasefireEvent;
    
    protected static float baseInterval = 10f;
    protected float interval = baseInterval;
    protected final IntervalUtil intervalUtil;
    
    protected float daysElapsed = 0;
    protected boolean randomFactionRelationships = false;
    protected boolean randomFactionRelationshipsPirate = false;
    protected long lastWarTimestamp = 0;
    
    protected Map<String, DiplomacyBrain> diplomacyBrains = new HashMap<>();
    protected Map<String, DiplomacyProfileIntel> profiles = new HashMap<>();
    
    static {
        String[] factions = {"templars", Factions.INDEPENDENT, Factions.LUDDIC_PATH};
        disallowedFactions = new ArrayList<>(Arrays.asList(factions));
        // disallowed factions is also used for things like rebellions
        //if (!ExerelinConfig.followersDiplomacy) disallowedFactions.add(ExerelinConstants.PLAYER_NPC_ID);
        eventDefs = new ArrayList<>();
        eventDefsById = new HashMap<>();
        
        for (FactionAPI faction : Global.getSector().getAllFactions())
        {
            if (ExerelinConfig.getExerelinFactionConfig(faction.getId()).disableDiplomacy)
                disallowedFactions.add(faction.getId());
        }
        
        try {
            loadSettings();
        } catch (IOException | JSONException ex) {
            Global.getLogger(DiplomacyManager.class).log(Level.ERROR, ex);
        }
    }
    
    private static void loadSettings() throws IOException, JSONException {
        JSONObject config = Global.getSettings().getMergedJSONForMod(CONFIG_FILE, ExerelinConstants.MOD_ID);
        baseInterval = (float)config.optDouble("eventFrequency", 10f);
        warWearinessPerInterval = (float)config.optDouble("warWearinessPerInterval", 10f);
        
        JSONArray eventsJson = config.getJSONArray("events");
        for(int i=0; i<eventsJson.length(); i++)
        {
            JSONObject eventDefJson = eventsJson.getJSONObject(i);
            DiplomacyEventDef eventDef = new DiplomacyEventDef();
            eventDef.name = eventDefJson.getString("name");
            //log.info("Adding diplomacy event " + eventDef.name);
            eventDef.id = eventDefJson.getString("stage");
			eventDef.desc = eventDefJson.getString("desc");
            eventDef.random = eventDefJson.optBoolean("random", true);
            
            eventDef.minRepChange = (float)eventDefJson.getDouble("minRepChange");
            eventDef.maxRepChange = (float)eventDefJson.getDouble("maxRepChange");
            eventDef.allowPiratesToPirates = eventDefJson.optBoolean("allowPiratesToPirates", false);
            eventDef.allowPiratesToNonPirates = eventDefJson.optBoolean("allowPiratesToNonPirates", false);
            eventDef.allowNonPiratesToPirates = eventDefJson.optBoolean("allowNonPiratesToPirates", false);
            eventDef.chance = (float)eventDefJson.optDouble("chance", 1f);
            if (eventDefJson.has("allowedFactions1"))
                eventDef.allowedFactions1 = ExerelinUtils.JSONArrayToArrayList(eventDefJson.getJSONArray("allowedFactions1"));
            if (eventDefJson.has("allowedFactions2"))
                eventDef.allowedFactions2 = ExerelinUtils.JSONArrayToArrayList(eventDefJson.getJSONArray("allowedFactions2"));
            
            String repLimit = eventDefJson.optString("repLimit");
            if (!repLimit.isEmpty())
                eventDef.repLimit = RepLevel.valueOf(StringHelper.flattenToAscii(repLimit.toUpperCase()));
            String minRepLevelToOccur = eventDefJson.optString("minRepLevelToOccur");
            if (!minRepLevelToOccur.isEmpty())
                eventDef.minRepLevelToOccur = RepLevel.valueOf(StringHelper.flattenToAscii(minRepLevelToOccur.toUpperCase()));
            String maxRepLevelToOccur = eventDefJson.optString("maxRepLevelToOccur");
            if (!maxRepLevelToOccur.isEmpty())
                eventDef.maxRepLevelToOccur = RepLevel.valueOf(StringHelper.flattenToAscii(maxRepLevelToOccur.toUpperCase()));
            String repEnsureAtWorst = eventDefJson.optString("repEnsureAtWorst");
            if (!repEnsureAtWorst.isEmpty())
                eventDef.repEnsureAtWorst = RepLevel.valueOf(StringHelper.flattenToAscii(repEnsureAtWorst.toUpperCase()));
            String repEnsureAtBest = eventDefJson.optString("repEnsureAtBest");
            if (!repEnsureAtBest.isEmpty())
                eventDef.repEnsureAtBest = RepLevel.valueOf(StringHelper.flattenToAscii(repEnsureAtBest.toUpperCase())); 
            
            eventDefs.add(eventDef);
            eventDefsById.put(eventDef.id, eventDef);
            
            if(eventDef.name.equals("Peace Treaty"))
                peaceTreatyEvent = eventDef;
            else if (eventDef.name.equals("Ceasefire"))
                ceasefireEvent = eventDef;
        }
    }
    
    public static DiplomacyEventDef getEventByStage(String stage)
    {
        if (!eventDefsById.containsKey(stage)) return null;
        return eventDefsById.get(stage);
    }
    
    public static float getHardModeDispositionMod() {
        return Global.getSettings().getFloat("nex_hardModeDispositionModifier");
    }

    public DiplomacyManager()
    {
        super(true);
        
        interval = getDiplomacyInterval();
        this.intervalUtil = new IntervalUtil(interval * 0.75F, interval * 1.25F);
              
        if (warWeariness == null)
        {
            warWeariness = new HashMap<>();
        }
    }
    
	/**
	 * Returns (sum of market sizes under our control)/(sum of total market sizes in the sector).
	 * Multiplied by 1.5 (default) for player in Starfarer mode, so can exceed 1.
	 * "Our control" includes all members of the alliance if the specified faction is in one,
	 * and the player markets if player is commissioned with that faction.
	 * @param factionId
	 * @return
	 */
	public static float getDominanceFactor(String factionId)
    {
        List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
        int globalSize = 0;
        for (MarketAPI market: allMarkets) globalSize += market.getSize();
        if (globalSize == 0) return 0;
        
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        List<MarketAPI> ourMarkets = null;
        Alliance alliance = AllianceManager.getFactionAlliance(factionId);
        if (alliance != null) ourMarkets = alliance.getAllianceMarkets();
        else ourMarkets = ExerelinUtilsFaction.getFactionMarkets(factionId);
        //ourMarkets = ExerelinUtilsFaction.getFactionMarkets(factionId);
		
		// If we are commissioned with this faction, add player markets to its list of markets
		// (unless we're in the same alliance as commissioning faction, to prevent double counting)
		// although we shouldn't ever be allied and commissioned at the same time anyway?
		// (dunno if we actually enforce this)
        if (factionId.equals(playerAlignedFactionId) && !playerAlignedFactionId.equals(Factions.PLAYER))
        {
            boolean isAlliedWithCommissioningFaction = alliance != null && AllianceManager.getFactionAlliance(Factions.PLAYER) == alliance;
            if (!isAlliedWithCommissioningFaction)
            {
                List<MarketAPI> playerNpcMarkets = ExerelinUtilsFaction.getFactionMarkets(Factions.PLAYER);
                ourMarkets.addAll(playerNpcMarkets);
            }
        }
        
        int ourSize = 0;
        for (MarketAPI market: ourMarkets) ourSize += market.getSize();
        
        if (ourSize == 0) return 0;
        
        boolean isPlayer = factionId.equals(playerAlignedFactionId) || (alliance != null && alliance == AllianceManager.getFactionAlliance(playerAlignedFactionId));
        
        float dominance = (float)ourSize / globalSize;
        if (SectorManager.getManager().isHardMode() && isPlayer)
            dominance *= Global.getSettings().getFloat("nex_hardModeDominanceMult");
        
        return dominance;
    }
    
    protected float getDiplomacyInterval()
    {
        int numFactions = SectorManager.getLiveFactionIdsCopy().size() - 2;
        if (numFactions < 0) numFactions = 0;
        return baseInterval * (float)Math.pow(0.95, numFactions);
    }
    
    public static float getBaseInterval() {
        return baseInterval;
    }
    
    protected static void printPlayerHostileStateMessage(FactionAPI faction, boolean isHostile, boolean forAlliance)
    {
        String msg;
        Color highlightColor = Misc.getPositiveHighlightColor();
        
        String factionOrAllianceName = faction.getDisplayName();
        if (forAlliance)
        {
            Alliance alliance = AllianceManager.getFactionAlliance(faction.getId());
            if (alliance != null)
            {
                factionOrAllianceName = alliance.getAllianceNameAndMembers();
            }
        }
        if (isHostile)
        {
            msg = StringHelper.getStringAndSubstituteToken("exerelin_diplomacy", "player_war_msg", "$factionOrAlliance", factionOrAllianceName);
            highlightColor = Misc.getNegativeHighlightColor();
        }
        else
        {
            msg = StringHelper.getStringAndSubstituteToken("exerelin_diplomacy", "player_peace_msg", "$factionOrAlliance", factionOrAllianceName);
        }
        
        Global.getSector().getCampaignUI().addMessage(msg, highlightColor);
    }
    
    // started working on this but decided I don't need it no more
    /*
    public static ExerelinReputationAdjustmentResult testAdjustRelations(FactionAPI faction1, FactionAPI faction2, float delta,
            RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit)
    {
        SectorAPI sector = Global.getSector();
        
        float before = faction1.getRelationship(faction2.getId());
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        FactionAPI playerFaction = sector.getPlayerFleet().getFaction();
        String faction1Id = faction1.getId();
        String faction2Id = faction2.getId();
    }
    */

    /**
     * If the current relationship between two factions is outside the min/max
     * specified in their Nexerelin faction configs, clamp accordingly.
     * Will only check max when the specified delta is non-negative, and min 
     * when the delta is non-positive (i.e. increasing a below-minimum relationship
     * won't trigger clamping).
     * @param faction1Id
     * @param faction2Id
     * @param delta
     * @return True if any action was taken, false otherwise.
     */

    
    public static boolean clampRelations(String faction1Id, String faction2Id, float delta)
    {
        if (getManager().randomFactionRelationships)
            return false;
        
        FactionAPI faction1 = Global.getSector().getFaction(faction1Id);
        float curr = faction1.getRelationship(faction2Id);
        float max = ExerelinFactionConfig.getMaxRelationship(faction1Id, faction2Id);
        float min = ExerelinFactionConfig.getMinRelationship(faction1Id, faction2Id);
        if (delta >= 0 && max < curr)
        {
            faction1.setRelationship(faction2Id, max);
            return true;
        }
        if (delta <= 0 && min > curr)
        {
            faction1.setRelationship(faction2Id, min);
            return true;
        }
        return false;
    }
    
    public static ExerelinReputationAdjustmentResult adjustRelations(FactionAPI faction1, FactionAPI faction2, float delta,
            RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit)
    {
        return adjustRelations(faction1, faction2, delta, ensureAtBest, ensureAtWorst, limit, false);
    }
    
    /**
     * Complicated stuff for setting relationships between two factions
     * Checks relationship bounds, etc.
     * @param faction1
     * @param faction2
     * @param delta
     * @param ensureAtBest
     * @param ensureAtWorst
     * @param limit
     * @param isAllianceAction Is this change resulting from an alliance action (a war vote etc.)? Don't do looping calls to AllianceManager
     * @return
     */
    public static ExerelinReputationAdjustmentResult adjustRelations(FactionAPI faction1, FactionAPI faction2, float delta,
            RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit, boolean isAllianceAction)
    {   
        SectorAPI sector = Global.getSector();
        
        float before = faction1.getRelationship(faction2.getId());
        boolean wasHostile = faction1.isHostileTo(faction2);
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        String faction1Id = faction1.getId();
        String faction2Id = faction2.getId();
        
        if (limit != null)
            faction1.adjustRelationship(faction2Id, delta, limit);
        else
            faction1.adjustRelationship(faction2Id, delta);
        
        if (ensureAtBest != null) {
                faction1.ensureAtBest(faction2Id, ensureAtBest);
        }
        if (ensureAtWorst != null) {
                faction1.ensureAtWorst(faction2Id, ensureAtWorst);
        }
        boolean playerWasHostile1 = faction1.isHostileTo(Factions.PLAYER);
        boolean playerWasHostile2 = faction2.isHostileTo(Factions.PLAYER);
        
        clampRelations(faction1Id, faction2Id, delta);
        float after = faction1.getRelationship(faction2Id);
        delta = after - before;
        //log.info("Relationship delta: " + delta);
        boolean isHostile = faction1.isHostileTo(faction2);
        
        // if now at peace/war, do alliance vote
        ExerelinReputationAdjustmentResult repResult = new ExerelinReputationAdjustmentResult(delta, wasHostile, isHostile);
        
        if (repResult.wasHostile && !repResult.isHostile)
        {
            DiplomacyManager manager = getManager();
            if (!isAllianceAction) AllianceVoter.allianceVote(faction1Id, faction2Id, false);
            manager.getDiplomacyBrain(faction1Id).addCeasefire(faction2Id);
            manager.getDiplomacyBrain(faction2Id).addCeasefire(faction1Id);
        }
        else if (!repResult.wasHostile && repResult.isHostile)
        {
            if (!isAllianceAction) AllianceVoter.allianceVote(faction1Id, faction2Id, true);
            
            getManager().setLastWarTimestamp(Global.getSector().getClock().getTimestamp());
        }
        
        if (!isAllianceAction)
            AllianceManager.remainInAllianceCheck(faction1Id, faction2Id);
        
        if (faction1Id.equals(playerAlignedFactionId) || faction2Id.equals(playerAlignedFactionId))
            NexUtilsReputation.syncPlayerRelationshipsToFaction();
        
        boolean playerIsHostile1 = faction1.isHostileTo(Factions.PLAYER);
        boolean playerIsHostile2 = faction2.isHostileTo(Factions.PLAYER);
        if (playerIsHostile1 != playerWasHostile1)
            printPlayerHostileStateMessage(faction1, playerIsHostile1, false);
        if (playerIsHostile2 != playerWasHostile2)
            printPlayerHostileStateMessage(faction2, playerIsHostile2, false);
        
        // TODO: display specific reputation change in message field if it affects player
        if (faction1Id.equals(playerAlignedFactionId) || faction2Id.equals(playerAlignedFactionId))
        {
            
        }
        
        SectorManager.checkForVictory();
        return repResult;
    }
    
    public static ExerelinReputationAdjustmentResult adjustRelations(DiplomacyEventDef event, FactionAPI faction1, FactionAPI faction2, float delta)
    {
        return adjustRelations(faction1, faction2, delta, event.repEnsureAtBest, event.repEnsureAtWorst, event.repLimit);
    }
    
    public ExerelinReputationAdjustmentResult doDiplomacyEvent(DiplomacyEventDef event, MarketAPI market, FactionAPI faction1, FactionAPI faction2)
    {
        SectorAPI sector = Global.getSector();
        
        float delta = MathUtils.getRandomNumberInRange(event.minRepChange, event.maxRepChange);
            
        if (delta < 0 && delta > -0.01f) delta = -0.01f;
        if (delta > 0 && delta < 0.01f) delta = 0.01f;
        delta = Math.round(delta * 100f) / 100f;
        float deltaBase = delta;
       
        ExerelinReputationAdjustmentResult result = DiplomacyManager.adjustRelations(event, faction1, faction2, delta);
        delta = result.delta;
        
        if (Math.abs(delta) >= 0.01f) {
            log.info("Transmitting event: " + event.name);
            
            DiplomacyIntel intel = new DiplomacyIntel(event.id, faction1.getId(), faction2.getId(), market, result);
            ExerelinUtils.addExpiringIntel(intel);
            
            if (diplomacyBrains.containsKey(faction1.getId()))
                diplomacyBrains.get(faction1.getId()).reportDiplomacyEvent(faction2.getId(), deltaBase);
            if (diplomacyBrains.containsKey(faction2.getId()))
                diplomacyBrains.get(faction2.getId()).reportDiplomacyEvent(faction1.getId(), deltaBase);
        }
        
        return result;
    }
    
    /**
     * Picks a random diplomacy event to execute between the two specified factions,
     * obeying the supplied parameters.
     * @param faction1
     * @param faction2
     * @param params
     * @return
     */
    public DiplomacyEventDef pickDiplomacyEvent(FactionAPI faction1, FactionAPI faction2, DiplomacyEventParams params)
    {
        DiplomacyEventDef event = null;
        String factionId1 = faction1.getId();
        String factionId2 = faction2.getId();
        
        if (params == null) params = new DiplomacyEventParams();
        
        float dominance = 0;
        //float dominance = getDominanceFactor(factionId1) + getDominanceFactor(factionId2);
        //dominance = dominance/2;
        if (params.useDominance)
        {
            dominance = Math.max( getDominanceFactor(factionId1), getDominanceFactor(factionId2) );
            log.info("Dominance factor: " + dominance);
        }
        
        WeightedRandomPicker<DiplomacyEventDef> eventPicker = new WeightedRandomPicker();
        for (DiplomacyEventDef eventDef: eventDefs)
        {
            if (params.random != eventDef.random)
                continue;
            
            boolean pirate1 = ExerelinUtilsFaction.isPirateFaction(factionId1);
            boolean pirate2 = ExerelinUtilsFaction.isPirateFaction(factionId2);
            
            if (pirate1 && pirate2 && !eventDef.allowPiratesToPirates)
                continue;
            if (pirate1 != pirate2)
            {
                if (pirate1 && !eventDef.allowPiratesToNonPirates)
                    continue;
                if (pirate2 && !eventDef.allowNonPiratesToPirates)
                    continue;
                
                if (!ExerelinConfig.allowPirateInvasions)
                    continue;
            }
            
            //float rel = faction1.getRelationship(factionId2);
            if (eventDef.minRepLevelToOccur != null && !faction1.isAtWorst(faction2, eventDef.minRepLevelToOccur))
            {
                //log.info("Rep too low");
                continue;
            }
            if (eventDef.maxRepLevelToOccur != null && !faction1.isAtBest(faction2, eventDef.maxRepLevelToOccur))
            {
                //log.info("Rep too high");
                continue;
            }
            if (eventDef.allowedFactions1 != null && !eventDef.allowedFactions1.contains(factionId1))
                continue;
            if (eventDef.allowedFactions2 != null && !eventDef.allowedFactions2.contains(factionId2))
                continue;
            
            boolean isNegative = (eventDef.maxRepChange + eventDef.minRepChange)/2 < 0;
            if (!isNegative && params.onlyNegative) continue;
            if (isNegative && params.onlyPositive) continue;
            
            float chance = eventDef.chance;
            if (chance <= 0) continue;
            if (!getManager().randomFactionRelationships) {
                if (isNegative) {
                    float mult = ExerelinFactionConfig.getDiplomacyNegativeChance(factionId1, factionId2);
                    //if (mult != 1) log.info("Applying negative event mult: " + mult);
                    chance *= mult;
                    chance *= params.negativeChanceMult;
                }
                else
                {
                    float mult = ExerelinFactionConfig.getDiplomacyPositiveChance(factionId1, factionId2);
                    //if (mult != 1) log.info("Applying positive event mult: " + mult);
                    chance *= mult;
                    chance *= params.positiveChanceMult;
                }

                if (dominance > DOMINANCE_MIN)
                {
                    float strength = (dominance - DOMINANCE_MIN)/(1 - DOMINANCE_MIN);
                    if (isNegative) chance += (DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD * strength);
                    else chance += (DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD * strength);
                }
                if (chance <= 0) continue;
            }
            eventPicker.add(eventDef, chance);
        }
        if (event == null) event = eventPicker.pick();
        return event;
    }
    
    public static void createDiplomacyEvent(FactionAPI faction1, FactionAPI faction2)
    {
        createDiplomacyEvent(faction1, faction2, null, new DiplomacyEventParams());
    }
    
    /**
     * Executes a diplomacy event between the two specified factions.
     * @param faction1
     * @param faction2
     * @param eventId If null, pick a random event.
     * @param params
     * @return
     */
    public static ExerelinReputationAdjustmentResult createDiplomacyEvent(
            FactionAPI faction1, FactionAPI faction2, String eventId, DiplomacyEventParams params)
    {        
        String factionId1 = faction1.getId();
        String factionId2 = faction2.getId();
        
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker();
        List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(factionId1);
        
        log.info("Factions are: " + faction1.getDisplayName() + ", " + faction2.getDisplayName());
        
        DiplomacyEventDef event;
        if (eventId != null) event = eventDefsById.get(eventId);
        else event = getManager().pickDiplomacyEvent(faction1, faction2, params);
        
        if (event == null)
        {
            log.info("No event available");
            return null;
        }
        log.info("Trying event: " + event.name);
        for (MarketAPI market:markets)
        {
            marketPicker.add(market);
        }
        
        MarketAPI market = marketPicker.pick();
        if (market == null)
        {
            log.info("No market available");
            return null;
        }
        
        return getManager().doDiplomacyEvent(event, market, faction1, faction2);
    }
    
    /**
     * Executes a random diplomacy event between two randomly selected factions.
     */
    public static void createDiplomacyEvent()
    {
        log.info("Starting diplomacy event creation");
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        WeightedRandomPicker<FactionAPI> factionPickerPirate = new WeightedRandomPicker();
        
        List<FactionAPI> factions = new ArrayList<>();
        for( String factionId : SectorManager.getLiveFactionIdsCopy())
            factions.add(sector.getFaction(factionId));

        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            if (faction.isNeutralFaction()) continue;
            if (Nex_IsFactionRuler.isRuler(faction.getId())) {
                if (!ExerelinConfig.followersDiplomacy) continue;
                if (!faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            }
            
            if (disallowedFactions.contains(faction.getId())) continue;
            //log.info("\tAdding eligible diplomacy faction: " + faction.getDisplayName());
            factionPicker.add(faction);
			if (ExerelinUtilsFaction.isPirateFaction(faction.getId()))
				factionPickerPirate.add(faction);
            factionCount++;
        }
        if (factionCount < 2) return;
        
        FactionAPI faction1 = factionPicker.pickAndRemove();
        FactionAPI faction2;
		if (!ExerelinConfig.allowPirateInvasions && ExerelinUtilsFaction.isPirateFaction(faction1.getId()))
		{
			factionPickerPirate.remove(faction1);
			if (factionPickerPirate.isEmpty()) return;
			faction2 = factionPickerPirate.pickAndRemove();
		}
		else
			faction2 = factionPicker.pickAndRemove();
		
		if (faction2 == null) return;
        createDiplomacyEvent(faction1, faction2);
    }
    
    public void modifyWarWeariness(String factionId, float amount)
    {
        ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (conf.hasDiplomacyTrait(TraitIds.STALWART))
            amount *= 0.67;
        else if (conf.hasDiplomacyTrait(TraitIds.WEAK_WILLED))
            amount *= 1.5f;
        
        Alliance alliance = AllianceManager.getFactionAlliance(factionId);
        if (alliance != null)
        {
            for (String member : alliance.getMembersCopy()) 
            {
                float weariness = getWarWeariness(member);
                weariness = Math.max(weariness + amount, 0);
                warWeariness.put(member, weariness);
            }
        }
        else
        {
            float weariness = getWarWeariness(factionId);
            weariness = Math.max(weariness + amount, 0);
            warWeariness.put(factionId, weariness);
        }
    }
    
    protected void updateWarWeariness()
    {
        SectorAPI sector = Global.getSector();
        List<String> factionIds = SectorManager.getLiveFactionIdsCopy();
        
        for(String factionId : factionIds)
        {
            if (ExerelinUtilsFaction.isPirateFaction(factionId)) continue;
            if (disallowedFactions.contains(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            if (faction.isNeutralFaction()) continue;
            // don't use followers if player is affiliated with another faction
            if (Nex_IsFactionRuler.isRuler(faction.getId()))
            { 
                if (!ExerelinConfig.followersDiplomacy) continue;
                if (!faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            }

            float weariness = getWarWeariness(factionId);
            List<String> enemies = getFactionsAtWarWithFaction(faction, false, false, true);
            int warCount = enemies.size();
            if (warCount > 0)
            {
                //log.info("Incrementing war weariness for " + faction.getDisplayName());
                weariness += enemies.size() * warWearinessPerInterval;
            }
            else weariness -= warWearinessPerInterval;
            if (weariness < 0) weariness = 0f;
            
            warWeariness.put(factionId, weariness);
        }
    }
    
    protected void handleMarketCapture(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
    {
        String loseFactionId = oldOwner.getId();
        if (!warWeariness.containsKey(loseFactionId)) return;
        float value = (market.getSize()^3) * 5;
        
        warWeariness.put(loseFactionId, getWarWeariness(loseFactionId) + value);
        
        // update revanchism caches
        for (Map.Entry<String, DiplomacyBrain> tmp : diplomacyBrains.entrySet()) {
            tmp.getValue().cacheRevanchism();
        }
    }
    
    public DiplomacyBrain getDiplomacyBrain(String factionId)
    {
        if (!diplomacyBrains.containsKey(factionId))
            diplomacyBrains.put(factionId, new DiplomacyBrain(factionId));
            
        return diplomacyBrains.get(factionId);
    }
    
    public DiplomacyProfileIntel getDiplomacyProfile(String factionId)
    {
        return profiles.get(factionId);
    }
    
    public DiplomacyProfileIntel createDiplomacyProfile(String factionId)
    {
        // donut double add
        if (profiles.containsKey(factionId)) {
            return profiles.get(factionId);
        }
        
        DiplomacyProfileIntel profile = DiplomacyProfileIntel.createEvent(factionId);
		if (profile == null) return null;
        profiles.put(factionId, profile);
        return profile;
    }
    
    public void removeDiplomacyProfile(String factionId) 
    {
        if (profiles.containsKey(factionId)) {
            profiles.get(factionId).endImmediately();
            profiles.remove(factionId);
        }
    }
    
    public long getLastWarTimestamp() {
        return lastWarTimestamp;
    }
    
    public void setLastWarTimestamp(long lastWarTimestamp) {
        this.lastWarTimestamp = lastWarTimestamp;
    }
    
    @Override
    public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
    {
        if (winner == null) return;
        CampaignFleetAPI loser = battle.getPrimary(battle.getOtherSideFor(winner));
        if (loser == null) return;
        
        FactionAPI winFaction = winner.getFaction();
        FactionAPI loseFaction = loser.getFaction();
        
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = Global.getSector().getFaction(playerAlignedFactionId);
        
        if (winFaction.isPlayerFaction())
        {
            winFaction = playerAlignedFaction;
        }
        else if (loseFaction.isPlayerFaction())
        {
            loseFaction = playerAlignedFaction;
        }
        
        String winFactionId = winFaction.getId();
        String loseFactionId = loseFaction.getId();
        
        // e.g. independents
        if (!warWeariness.containsKey(winFactionId) || !warWeariness.containsKey(loseFactionId))
        {
            return;
        }
        // pirate battles don't cause war weariness
        if (ExerelinUtilsFaction.isPirateFaction(winFactionId)) {
            return;
        }
        if (ExerelinUtilsFaction.isPirateFaction(loseFactionId)) {
            return;
        }

        float loserLosses = 0f;
        float winnerLosses = 0f;
        for (FleetMemberAPI member : Misc.getSnapshotMembersLost(loser)) {
            loserLosses += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : Misc.getSnapshotMembersLost(winner)) {
            winnerLosses += member.getFleetPointCost();
        }
        winnerLosses *= WAR_WEARINESS_FLEET_WIN_MULT;
        //log.info(winFaction.getDisplayName() + " war weariness from battle: " + winnerLosses);
        //log.info(loseFaction.getDisplayName() + " war weariness from battle: " + loserLosses);
        
        warWeariness.put(winFactionId, getWarWeariness(winFactionId) + winnerLosses);
        warWeariness.put(loseFactionId, getWarWeariness(loseFactionId) + loserLosses);
    }
            
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        daysElapsed += days;
        if (daysElapsed >= WAR_WEARINESS_INTERVAL)
        {
            daysElapsed -= WAR_WEARINESS_INTERVAL;
            updateWarWeariness();
        }
        
        for (String factionId : SectorManager.getLiveFactionIdsCopy())
        {
            getDiplomacyBrain(factionId).advance(days);
        }
        
        this.intervalUtil.advance(days);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        
        if (Global.getSector().isInNewGameAdvance())
            return;
        
        createDiplomacyEvent();
        interval = getDiplomacyInterval();
        intervalUtil.setInterval(interval * 0.75f, interval * 1.25f);
    }
    
    @Override
    public void reportPlayerReputationChange(String factionId, float delta) {
        FactionAPI player = Global.getSector().getFaction("player");
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        // clamp
        NexUtilsReputation.syncFactionRelationshipToPlayer(playerAlignedFactionId, factionId);
        NexUtilsReputation.syncPlayerRelationshipToFaction(playerAlignedFactionId, factionId);
        //if (!playerAlignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
        //    NexUtilsReputation.syncFactionRelationshipToPlayer(ExerelinConstants.PLAYER_NPC_ID, factionId);
        
        float currentRel = player.getRelationship(factionId);
        boolean isHostile = player.isHostileTo(factionId);
        
        // if we changed peace/war state, decide if alliances should get involved
        if (isHostile && currentRel - delta > AllianceManager.HOSTILE_THRESHOLD 
                || !isHostile && currentRel - delta < AllianceManager.HOSTILE_THRESHOLD)
            AllianceVoter.allianceVote(playerAlignedFactionId, factionId, isHostile);
        
        // handled by commission intel
        /*
        if (player.isAtBest(PlayerFactionStore.getPlayerFactionId(), RepLevel.INHOSPITABLE))
            SectorManager.scheduleExpelPlayerFromFaction();
        */

        SectorManager.checkForVictory();
    }
  
    @Override
    public boolean isDone()
    {
        return false;
    }
    
    @Override
    public boolean runWhilePaused()
    {
        return false;
    }
    
    public static DiplomacyManager getManager()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        return (DiplomacyManager)data.get(MANAGER_MAP_KEY);
    }
    
    public static DiplomacyManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        DiplomacyManager manager = (DiplomacyManager)data.get(MANAGER_MAP_KEY);
        if (manager != null)
        {
            try {
                loadSettings();
            } catch (IOException | JSONException ex) {
                Global.getLogger(DiplomacyManager.class).log(Level.ERROR, ex);
            }
            return manager;
        }
        
        manager = new DiplomacyManager();
        data.put(MANAGER_MAP_KEY, manager);
        return manager;
    }
    
    public static List<String> getFactionsAtWarWithFaction(String factionId, 
            boolean includePirates, boolean includeTemplars, boolean mustAllowCeasefire)
    {
        return getFactionsOfAtBestRepWithFaction(Global.getSector().getFaction(factionId), 
                RepLevel.HOSTILE, includePirates, includeTemplars, mustAllowCeasefire);
    }
    
    public static List<String> getFactionsAtWarWithFaction(FactionAPI faction, 
            boolean includePirates, boolean includeTemplars, boolean mustAllowCeasefire)
    {
        return getFactionsOfAtBestRepWithFaction(faction, RepLevel.HOSTILE, includePirates, 
                includeTemplars, mustAllowCeasefire);
    }
    
    /**
     * Gets all factions whose reputation is at best {@code rep} with the specified faction.
     * @param faction
     * @param rep
     * @param includePirates
     * @param includeTemplars
     * @param mustAllowCeasefire If true, will exclude any factions whose relationship bounds prevent peace with {@code faction},
     * or which can never have a positive diplomacy event with {@code faction}.
     * @return
     */
    public static List<String> getFactionsOfAtBestRepWithFaction(FactionAPI faction, RepLevel rep,
            boolean includePirates, boolean includeTemplars, boolean mustAllowCeasefire)
    {
        String factionId = faction.getId();
        List<String> enemies = new ArrayList<>();
        List<String> factions = SectorManager.getLiveFactionIdsCopy();

        for(String otherFactionId : factions)
        {
            if (factionId.equals(otherFactionId)) continue;
            if (!faction.isAtBest(otherFactionId, rep)) continue;
            if (disallowedFactions.contains(otherFactionId)) continue;
            if (!includePirates && ExerelinUtilsFaction.isPirateFaction(otherFactionId)) continue;
            if (!includeTemplars && otherFactionId.equals("templars")) continue;
            // only count non-pirate non-Templar factions with which we can ceasefire as enemies
            if (mustAllowCeasefire && !ExerelinFactionConfig.canCeasefire(factionId, otherFactionId))
                continue;
            enemies.add(otherFactionId);
        }
        return enemies;
    }
    
    public static boolean isFactionAtWar(String factionId, boolean excludeNeutralAndRebels)
    {
        if(getFactionsAtWarWithFaction(factionId, !excludeNeutralAndRebels, true, false).size() > 0)
            return true;
        else
            return false;
    }
    
    public static List<String> getFactionsFriendlyWithFaction(String factionId)
    {
        List<String> allies = new ArrayList<>();

        List<FactionAPI> factions = Global.getSector().getAllFactions();

        for(int i = 0; i < factions.size(); i++)
        {
            if(factions.get(i).isAtWorst(factionId, RepLevel.WELCOMING))
                allies.add(factions.get(i).getId());
        }

        return allies;
    }
    
    public static void notifyMarketCaptured(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
    {
        getManager().handleMarketCapture(market, oldOwner, newOwner);
        for (DiplomacyBrain brain : getManager().diplomacyBrains.values())
        {
            brain.updateAllDispositions(0);
        }
    }
    
    public static float getWarWeariness(String factionId)
    {
        return getWarWeariness(factionId, false);
    }
    
    public static float getWarWeariness(String factionId, boolean useEnemyCountModifier)
    {
        DiplomacyManager manager = getManager();
        if (manager == null) 
        {
            log.info("No diplomacy manager found");
            return 0.0f;
        }
        if (!manager.warWeariness.containsKey(factionId))
        {
            manager.warWeariness.put(factionId, 0f);
        }
        float weariness = manager.warWeariness.get(factionId);
        if (useEnemyCountModifier)
        {
            int enemies = getFactionsAtWarWithFaction(factionId, ExerelinConfig.allowPirateInvasions, true, false).size();
            weariness *= 1 + 0.25f * (enemies - 1);
        }
        
        return weariness;
    }
    
    public static void setRelationshipAtBest(String factionId, String otherFactionId, float rel)
    {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        float currentRel = faction.getRelationship(otherFactionId);
        if (rel < currentRel)
            faction.setRelationship(otherFactionId, rel);
    }
    
    /**
     * Set relationships for hostile-to-all factions (Templars, Dark Spire, infected).
     * @param factionId The hostile-to-all faction.
     * @param factionIds The factions with which the {@code factionId} faction 
     * should have its relationships set.
     */
    public static void handleHostileToAllFaction(String factionId, List<String> factionIds)
    {
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (factionConfig == null) return;
        if (factionConfig.hostileToAll <= 0) return;
        
        float relationship = STARTING_RELATIONSHIP_HOSTILE;
        if (factionConfig.hostileToAll == 3) relationship = -1f;
        boolean isPirateNeutral = factionConfig.isPirateNeutral;
        
        for (String otherFactionId : factionIds)
        {
            if (otherFactionId.equals(factionId)) continue;
            if (isPirateNeutral && ExerelinUtilsFaction.isPirateFaction(otherFactionId))
                continue;
            boolean isPlayer = otherFactionId.equals(Factions.PLAYER);
            
            FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
            if (factionConfig.hostileToAll == 1 && isPlayer)
            {
                otherFaction.setRelationship(factionId, STARTING_RELATIONSHIP_INHOSPITABLE);
            }
            else if (!otherFaction.isNeutralFaction())
            {
                setRelationshipAtBest(factionId, otherFactionId, relationship);
            }
        }
    }
    
    public static void initFactionRelationships(boolean midgameReset)
    {
        DiplomacyManager manager = getManager();
        SectorAPI sector = Global.getSector();
        FactionAPI player = sector.getFaction(Factions.PLAYER);
        String selectedFactionId = PlayerFactionStore.getPlayerFactionIdNGC();
        ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(selectedFactionId);
        if (conf.spawnAsFactionId != null && !conf.spawnAsFactionId.equals(Factions.PLAYER))
            selectedFactionId = conf.spawnAsFactionId;
        
        FactionAPI selectedFaction = sector.getFaction(selectedFactionId);
        log.info("Selected faction is " + selectedFaction + " | " + selectedFactionId);

        //List<String> factionIds = SectorManager.getLiveFactionIdsCopy();
        //factionIds.add("independent");
        //factionIds.add(ExerelinConstants.PLAYER_NPC_ID);
        
        List<String> factionIds = new ArrayList<>();
        List<String> alreadyRandomizedIds = new ArrayList<>();
        alreadyRandomizedIds.addAll(DO_NOT_RANDOMIZE);
        
        // should we prevent randomization of pirate factions?
        boolean pirateBlock = !manager.randomFactionRelationshipsPirate;
        
        for (FactionAPI faction : sector.getAllFactions())
        {
            if (faction.isNeutralFaction()) continue;
            String factionId = faction.getId();
            factionIds.add(factionId);
            
            // mark non-randomizable factions as such
            if (alreadyRandomizedIds.contains(factionId)) continue;
            if (ExerelinConfig.getExerelinFactionConfig(factionId).noRandomizeRelations)
            {
                log.info("Faction " + factionId + " marked as non-randomizable");
                alreadyRandomizedIds.add(factionId);
            }
            else if (pirateBlock && ExerelinConfig.getExerelinFactionConfig(factionId).pirateFaction)
            {
                log.info("Faction " + factionId + " is pirates, and pirate relations randomization is disabled");
                alreadyRandomizedIds.add(factionId);
            }
        }

        boolean randomize = manager.randomFactionRelationships;
        
        if (SectorManager.getCorvusMode() && !randomize)
        {
            // load vanilla relationships
            VanillaSystemsGenerator.initFactionRelationships(sector);
        }
        else
        {
            // use sector generation seed, unless resetting game at start
            Random random = new Random();
            if (!midgameReset) 
            {
                random.setSeed(ExerelinUtils.getStartingSeed());
            }
            else random.setSeed(Misc.genRandomSeed());
            
            // first make everyone neutral to each other (for midgame reset)
            if (midgameReset)
            {
                for (String factionId : factionIds) 
                {
                    FactionAPI faction = sector.getFaction(factionId);
                    for (String otherFactionId: factionIds)
                    {
                        if (factionId.equals(otherFactionId)) continue;
                        faction.setRelationship(otherFactionId, 0);
                    }
                }
            }
            
            // pirates are hostile to everyone, except some factions like Mayorate
            for (String factionId : factionIds) 
            {
                if (ExerelinUtilsFaction.isPirateFaction(factionId))
                {
                    for (String otherFactionId : factionIds) 
                    {
                        if (otherFactionId.equals(factionId)) continue;
                        FactionAPI otherFaction = sector.getFaction(otherFactionId);
                        if (otherFaction.isNeutralFaction()) continue;
                        
                        ExerelinFactionConfig otherConfig = ExerelinConfig.getExerelinFactionConfig(otherFactionId);
                        if (otherConfig != null && (otherConfig.isPirateNeutral || otherConfig.pirateFaction)) continue;
                        
                        otherFaction.setRelationship(factionId, STARTING_RELATIONSHIP_HOSTILE);
                    }
                }
            }

            // randomize if needed
            for (String factionId : factionIds) 
            {
                FactionAPI faction = sector.getFaction(factionId);
                ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
                
                if (randomize && !alreadyRandomizedIds.contains(factionId))
                {
                    if (faction.isNeutralFaction() || faction.isPlayerFaction()) continue;
                    alreadyRandomizedIds.add(factionId);
                                        
                    for (String otherFactionId: factionIds)
                    {
                        if (alreadyRandomizedIds.contains(otherFactionId)) continue;
                        if (otherFactionId.equals(factionId)) continue;
                        
                        FactionAPI otherFaction = sector.getFaction(otherFactionId);
                        if (otherFaction.isNeutralFaction() || otherFaction.isPlayerFaction()) continue;
                        
                        if (random.nextFloat() < 0.5) // 50% chance to do nothing (lower clutter)
                        {
                            // empty
                        }
                        else
                        {
                            float min = -0.85f, max = 0.6f;
                            float randomRel = random.nextFloat() * (max - min) + min;
                            log.info("\tSetting relations " + factionId + "|" + otherFactionId + ": " + randomRel);
                            faction.setRelationship(otherFactionId, randomRel);
                        }
                        
                        // player's faction is always enemy to pirate factions
                        // do this after the random stuff to ensure constant RNG output sequence
                        if (ExerelinUtilsFaction.isPirateFaction(factionId) && (otherFactionId.equals(selectedFactionId) || otherFactionId.equals(Factions.PLAYER)))
                        {
                            faction.setRelationship(otherFactionId, STARTING_RELATIONSHIP_HOSTILE);
                        }
                    }
                    handleHostileToAllFaction(factionId, factionIds);
                }

                else    // start hostile with hated factions, friendly with liked ones (from config)
                {
                    handleHostileToAllFaction(factionId, factionIds);
                    
                    for (Map.Entry<String, Float> entry : factionConfig.startRelationships.entrySet())
                    {
						if (factionId.equals(entry.getKey())) continue;
                        faction.setRelationship(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        
        // if we leave our faction later, we'll be neutral to most but hostile to pirates and such
        PlayerFactionStore.saveIndependentPlayerRelations();
        
        // set player relations based on selected faction
        if (selectedFactionId.equals(Factions.PLAYER))
        {
            // do nothing
        }
        else {
            NexUtilsReputation.syncPlayerRelationshipsToFaction(selectedFactionId);
            player.setRelationship(selectedFactionId, STARTING_RELATIONSHIP_FRIENDLY);
            //ExerelinUtilsReputation.syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);    // already done in syncPlayerRelationshipsToFaction
        }
    }
    
    public static void resetFactionRelationships(String factionId)
    {
        if (!factionId.equals(PlayerFactionStore.getPlayerFactionId()) 
                && !ExerelinUtilsFaction.isFactionHostileToAll(factionId)
                && !ExerelinUtilsFaction.isExiInCorvus(factionId))
        {
            for (FactionAPI faction : Global.getSector().getAllFactions())
            {
                String otherFactionId = faction.getId();
                if (!ExerelinUtilsFaction.isFactionHostileToAll(otherFactionId)
                        && !ExerelinUtilsFaction.isExiInCorvus(otherFactionId)
                        && !otherFactionId.equals(factionId))
                {
                    faction.setRelationship(factionId, 0f);
                }
            }
        }
    }
    
    public static void setRandomFactionRelationships(boolean random, boolean pirate)
    {
        DiplomacyManager manager = getManager();
        manager.randomFactionRelationships = random;
        manager.randomFactionRelationshipsPirate = pirate;
    }
    
    public static boolean isRandomFactionRelationships()
    {
        return getManager().randomFactionRelationships;
    }
    
     public boolean isRandomPirateFactionRelationships()
    {
        return randomFactionRelationshipsPirate;
    }
    
    protected Object readResolve()
    {
        if (diplomacyBrains == null)
            diplomacyBrains = new HashMap<>();
        
        return this;
    }
    
    public void reverseCompatibility() {
        if (profiles == null) {
            profiles = new HashMap<>();
            for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
                createDiplomacyProfile(factionId);
            }
        }
    }
    
    
    public static class DiplomacyEventDef {
        public String name;
        public String id;
        public String desc;
        public boolean random = true;
        public RepLevel minRepLevelToOccur;
        public RepLevel maxRepLevelToOccur;
        public RepLevel repEnsureAtWorst;
        public RepLevel repEnsureAtBest;
        public RepLevel repLimit;
        public float minRepChange;
        public float maxRepChange;
        public List<String> allowedFactions1;
        public List<String> allowedFactions2;
        public boolean allowPiratesToPirates;
        public boolean allowPiratesToNonPirates;
        public boolean allowNonPiratesToPirates;
        public float chance;
    }
    
    public static class DiplomacyEventParams {
        public boolean onlyPositive = false;
        public boolean onlyNegative = false;
        public boolean useDominance = true;
        public boolean random = true;
        public float positiveChanceMult = 1;
        public float negativeChanceMult = 1;
    }
}
