package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.MutableStat.StatMod;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.ExerelinSetupData.StartRelationsMode;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceVoter;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import exerelin.campaign.intel.MilestoneTracker;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel;
import exerelin.utilities.*;
import exerelin.world.VanillaSystemsGenerator;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import org.apache.log4j.Level;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * Creates diplomacy events at regular intervals; handles war weariness
 */
@Log4j
public class DiplomacyManager extends BaseCampaignEventListener implements EveryFrameScript
{
    
    protected static final String CONFIG_FILE = "data/config/exerelin/diplomacyConfig.json";
    protected static final String MANAGER_MAP_KEY = "exerelin_diplomacyManager";
	public static final String MEM_KEY_MAX_RELATIONS = "$nex_max_relations";
	public static final String MEM_KEY_BADBOY = "$nex_badboy";
    
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
    public static final float BADBOY_DECAY_PER_MONTH = 3f;
    
    public static final float DOMINANCE_MIN = 0.25f;
    public static final float DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD = -0.67f;
    public static final float DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD = 3f;
    
    public static final List<String> DO_NOT_RANDOMIZE = Arrays.asList(new String[]{
        "sector", "domain", "everything"
    });
    
    protected Map<String, Float> warWeariness = new HashMap<>();
    protected static float warWearinessPerInterval = 50f;
    protected static DiplomacyEventDef peaceTreatyEvent;
    protected static DiplomacyEventDef ceasefireEvent;
    
    protected static float baseInterval = 20f;
    protected float interval = baseInterval;
    protected final IntervalUtil intervalUtil;
    
    protected float daysElapsed = 0;
    @Getter @Setter protected StartRelationsMode startRelationsMode = StartRelationsMode.DEFAULT;
    @Getter @Setter protected boolean applyStartRelationsModeToPirates = false;
    protected long lastWarTimestamp = 0;
    
    @Getter protected Map<String, DiplomacyBrain> diplomacyBrains = new HashMap<>();
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
            if (NexConfig.getFactionConfig(faction.getId()).disableDiplomacy)
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
        baseInterval = (float)config.optDouble("eventFrequency", 20f);
        warWearinessPerInterval = (float)config.optDouble("warWearinessPerInterval", 30f);
        
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
            eventDef.invert = eventDefJson.optBoolean("invert", false);
            
            eventDef.minRepChange = (float)eventDefJson.getDouble("minRepChange");
            eventDef.maxRepChange = (float)eventDefJson.getDouble("maxRepChange");
            eventDef.allowPiratesToPirates = eventDefJson.optBoolean("allowPiratesToPirates", false);
            eventDef.allowPiratesToNonPirates = eventDefJson.optBoolean("allowPiratesToNonPirates", false);
            eventDef.allowNonPiratesToPirates = eventDefJson.optBoolean("allowNonPiratesToPirates", false);
            eventDef.chance = (float)eventDefJson.optDouble("chance", 1f);
            if (eventDefJson.has("allowedFactions1"))
                eventDef.allowedFactions1 = NexUtils.JSONArrayToArrayList(eventDefJson.getJSONArray("allowedFactions1"));
            if (eventDefJson.has("allowedFactions2"))
                eventDef.allowedFactions2 = NexUtils.JSONArrayToArrayList(eventDefJson.getJSONArray("allowedFactions2"));
            
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
        Global.getSector().getListenerManager().addListener(this, false);
        
        interval = getDiplomacyInterval();
        this.intervalUtil = new IntervalUtil(interval * 0.75F, interval * 1.25F);
    }
    
    protected Object readResolve() {
        if (startRelationsMode == null) startRelationsMode = StartRelationsMode.DEFAULT;
        return this;
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
        else ourMarkets = NexUtilsFaction.getFactionMarkets(factionId);
        //ourMarkets = ExerelinUtilsFaction.getFactionMarkets(factionId);
        
        // If we are commissioned with this faction, add player markets to its list of markets
        if (factionId.equals(Misc.getCommissionFactionId()))
        {
            List<MarketAPI> playerMarkets = NexUtilsFaction.getFactionMarkets(Factions.PLAYER);
            ourMarkets.addAll(playerMarkets);
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
    public static void setBaseInterval(float interval) {
        baseInterval = interval;

        // resetting the intervalutil is too messy and inconsistent with the diplomacy brain besides
        // so just leave it be until it next elapses
        if (true) return;

        if (getManager() != null) {
            float savedInterval = getManager().interval;
            float wantedNewInterval = getManager().getDiplomacyInterval();
            if (Math.abs(savedInterval - wantedNewInterval) < 0.1f) {
                log.info("Interval unchanged, taking no action");
                return;
            }
            log.info("Resetting interval");
            getManager().resetInterval();
        }
    }

    protected void resetInterval() {
        interval = getDiplomacyInterval();
        intervalUtil.setInterval(interval * 0.75f, interval * 1.25f);
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
        if (getManager().startRelationsMode.isRandom())
            return false;
        
        FactionAPI faction1 = Global.getSector().getFaction(faction1Id);
        float curr = faction1.getRelationship(faction2Id);
        float max = DiplomacyManager.getManager().getMaxRelationship(faction1Id, faction2Id);
        float min = NexFactionConfig.getMinRelationship(faction1Id, faction2Id);
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
    
    public static boolean isOutsideRepBounds(String faction1Id, String faction2Id, float delta) 
    {
        if (!getManager().startRelationsMode.isDefault())
            return false;
        
        FactionAPI faction1 = Global.getSector().getFaction(faction1Id);
        float curr = faction1.getRelationship(faction2Id);
        float max = DiplomacyManager.getManager().getMaxRelationship(faction1Id, faction2Id);
        float min = NexFactionConfig.getMinRelationship(faction1Id, faction2Id);
        if (delta >= 0 && curr > max)
        {
            log.info("Factions " + faction1Id + " and " + faction2Id + " are above their maximum relations");
            return true;
        }
        if (delta <= 0 && curr < min)
        {
            log.info("Factions " + faction1Id + " and " + faction2Id + " are below their minimum relations");
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
        float before = faction1.getRelationship(faction2.getId());
        boolean wasHostile = faction1.isHostileTo(faction2);
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        String faction1Id = faction1.getId();
        String faction2Id = faction2.getId();
        
        // Do nothing if we're outside relationship bounds (to prevent clamping from sending us "backwards")
        if (isOutsideRepBounds(faction1Id, faction2Id, delta)) 
        {
            return new ExerelinReputationAdjustmentResult(0, wasHostile, wasHostile);
        }
        
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
            if (!isAllianceAction) {
                log.info(String.format("Initiating alliance vote due to diplomacy event between %s, %s", faction1Id, faction2Id));
                AllianceVoter.allianceVote(faction1Id, faction2Id, false);
            }
            manager.getDiplomacyBrain(faction1Id).addCeasefire(faction2Id);
            manager.getDiplomacyBrain(faction2Id).addCeasefire(faction1Id);
        }
        else if (!repResult.wasHostile && repResult.isHostile)
        {
            if (!isAllianceAction) {
                log.info(String.format("Initiating alliance vote due to diplomacy event between %s, %s", faction1Id, faction2Id));
                AllianceVoter.allianceVote(faction1Id, faction2Id, true);
            }
            
            getManager().setLastWarTimestamp(Global.getSector().getClock().getTimestamp());
        }
        
        if (!isAllianceAction && delta < 0)
            AllianceManager.remainInAllianceCheck(faction1Id, faction2Id);
        
        if (faction1Id.equals(Factions.PLAYER) || faction2Id.equals(Factions.PLAYER))
            NexUtilsReputation.syncFactionRelationshipsToPlayer();
        else if (faction1Id.equals(playerAlignedFactionId) || faction2Id.equals(playerAlignedFactionId))
            NexUtilsReputation.syncPlayerRelationshipsToFaction();
        
        boolean playerIsHostile1 = faction1.isHostileTo(Factions.PLAYER);
        boolean playerIsHostile2 = faction2.isHostileTo(Factions.PLAYER);
        if (playerIsHostile1 != playerWasHostile1)
            printPlayerHostileStateMessage(faction1, playerIsHostile1, false);
        if (playerIsHostile2 != playerWasHostile2)
            printPlayerHostileStateMessage(faction2, playerIsHostile2, false);
        
        // TODO: display specific reputation change in message field if it affects player?
        if (faction1Id.equals(playerAlignedFactionId) || faction2Id.equals(playerAlignedFactionId))
        {
            
        }
        
        SectorManager.checkForVictory();
        if (MilestoneTracker.getIntel() != null) {
            MilestoneTracker.getIntel().reportPlayerReputationChanged(faction1Id);
            MilestoneTracker.getIntel().reportPlayerReputationChanged(faction2Id);
        }
        
        return repResult;
    }
    
    public static ExerelinReputationAdjustmentResult adjustRelations(DiplomacyEventDef event, FactionAPI faction1, FactionAPI faction2, float delta)
    {
        return adjustRelations(faction1, faction2, delta, event.repEnsureAtBest, event.repEnsureAtWorst, event.repLimit);
    }
    
    public DiplomacyIntel doDiplomacyEvent(DiplomacyEventDef event, MarketAPI market, FactionAPI faction1, FactionAPI faction2)
    {        
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
            //ExerelinUtils.addExpiringIntel(intel);
            intel.addEvent();
            
            if (diplomacyBrains.containsKey(faction1.getId()))
                diplomacyBrains.get(faction1.getId()).reportDiplomacyEvent(faction2.getId(), deltaBase);
            if (diplomacyBrains.containsKey(faction2.getId()))
                diplomacyBrains.get(faction2.getId()).reportDiplomacyEvent(faction1.getId(), deltaBase);

            return intel;
        }
        
        return null;
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
        
        List<Pair<DiplomacyEventDef, Float>> validEvents = new ArrayList<>();
        float sumChancesPositive = 0;
        float sumChancesNegative = 0;
        
        for (DiplomacyEventDef eventDef: eventDefs)
        {
            if (params.random != eventDef.random)
                continue;
            
            boolean pirate1 = NexUtilsFaction.isPirateFaction(factionId1);
            boolean pirate2 = NexUtilsFaction.isPirateFaction(factionId2);
            
            if (pirate1 && pirate2 && !eventDef.allowPiratesToPirates)
                continue;
            if (pirate1 != pirate2)
            {
                if (pirate1 && !eventDef.allowPiratesToNonPirates)
                    continue;
                if (pirate2 && !eventDef.allowNonPiratesToPirates)
                    continue;
                
                if (!NexConfig.allowPirateInvasions)
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
            if (getManager().startRelationsMode.isDefault()) {
                if (isNegative) {
                    float mult = NexFactionConfig.getDiplomacyNegativeChance(factionId1, factionId2);
                    //if (mult != 1) log.info("Applying negative event mult: " + mult);
                    chance *= mult;
                    chance *= params.negativeChanceMult;
                }
                else
                {
                    float mult = NexFactionConfig.getDiplomacyPositiveChance(factionId1, factionId2);
                    //if (mult != 1) log.info("Applying positive event mult: " + mult);
                    chance *= mult;
                    chance *= params.positiveChanceMult;
                }
            }
            if (dominance > DOMINANCE_MIN)
            {
                float strength = (dominance - DOMINANCE_MIN)/(1 - DOMINANCE_MIN);
                if (isNegative) chance += (DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD * strength);
                else chance += (DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD * strength);
            }
            if (chance <= 0) continue;
            
            validEvents.add(new Pair<>(eventDef, chance));
            if (isNegative) sumChancesNegative += chance;
            else sumChancesPositive += chance;
        }
        
        WeightedRandomPicker<DiplomacyEventDef> eventPicker = new WeightedRandomPicker();
        
        // normalize chances and add to picker
        for (Pair<DiplomacyEventDef, Float> validEvent : validEvents) {
            DiplomacyEventDef event = validEvent.one;
            float chance = validEvent.two;
            
            boolean isNegative = (event.maxRepChange + event.minRepChange)/2 < 0;
            if (isNegative) chance /= sumChancesNegative;
            else chance /= sumChancesPositive;
            //log.info("Adding event " + event.name + " with chance " + chance + " to picker");
            
            eventPicker.add(event, chance);
        }
        
        
        return eventPicker.pick();
    }
    
    public static void createDiplomacyEvent(FactionAPI faction1, FactionAPI faction2)
    {
        createDiplomacyEvent(faction1, faction2, null, new DiplomacyEventParams());
    }


    /**
     * Executes a diplomacy event between the two specified factions. Deprecated, use the mostly identical (except for return type)
     * {@code createDiplomacyEventV2} instead.
     * @param faction1
     * @param faction2
     * @param eventId If null, pick a random event.
     * @param params
     * @return
     */
    @Deprecated
    public static ExerelinReputationAdjustmentResult createDiplomacyEvent(
            FactionAPI faction1, FactionAPI faction2, String eventId, DiplomacyEventParams params) {
        DiplomacyIntel intel = createDiplomacyEventV2(faction1, faction2, eventId, params);
        if (intel == null) return null;
        return intel.getReputation();
    }
    
    /**
     * Executes a diplomacy event between the two specified factions.
     * @param faction1
     * @param faction2
     * @param eventId If null, pick a random event.
     * @param params
     * @return
     */
    public static DiplomacyIntel createDiplomacyEventV2(
            FactionAPI faction1, FactionAPI faction2, String eventId, DiplomacyEventParams params)
    {
        DiplomacyEventDef event;
        if (eventId != null) event = eventDefsById.get(eventId);
        else event = getManager().pickDiplomacyEvent(faction1, faction2, params);

        String type = "either";
        if (params != null) {
            if (params.onlyPositive) type = "positive";
            if (params.onlyNegative) type = "negative";
        }

        log.info(String.format("Creating %s diplomacy event for factions %s, %s", type, faction1.getDisplayName(), faction2.getDisplayName()));
        if (event == null)
        {
            log.info("No event available");
            return null;
        }
        log.info("Trying event: " + event.name);

        if (event.invert) {
            FactionAPI temp = faction1;
            faction1 = faction2;
            faction2 = temp;
        }

        String factionId1 = faction1.getId();
        String factionId2 = faction2.getId();
        
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker();
        List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(factionId1);
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
        if (!NexConfig.enableDiplomacy) return;

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
                if (!NexConfig.followersDiplomacy) continue;
                if (!faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            }
            
            if (disallowedFactions.contains(faction.getId())) continue;
            //log.info("\tAdding eligible diplomacy faction: " + faction.getDisplayName());
            factionPicker.add(faction);
            if (NexUtilsFaction.isPirateFaction(faction.getId()))
                factionPickerPirate.add(faction);
            factionCount++;
        }
        if (factionCount < 2) return;
        
        FactionAPI faction1 = factionPicker.pickAndRemove();
        FactionAPI faction2;
        if (!NexConfig.allowPirateInvasions && NexUtilsFaction.isPirateFaction(faction1.getId()))
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
        List<String> traits = DiplomacyTraits.getFactionTraits(factionId);
        if (amount > 0) {
            if (traits.contains(TraitIds.FOREVERWAR))
                return;
            else if (traits.contains(TraitIds.STALWART))
                amount *= 0.67;
            else if (traits.contains(TraitIds.WEAK_WILLED))
                amount *= 1.5f;
        }
        
        
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
            if (NexUtilsFaction.isPirateFaction(factionId)) continue;
            if (disallowedFactions.contains(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            if (faction.isNeutralFaction()) continue;
            // don't use followers if player is affiliated with another faction
            if (Nex_IsFactionRuler.isRuler(faction.getId()))
            { 
                if (!NexConfig.followersDiplomacy) continue;
                if (!faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            }
			
			float wearinessDelta = 0;
            List<String> enemies = getFactionsAtWarWithFaction(faction, false, false, true);
            int warCount = enemies.size();
            if (warCount > 0)
            {
                //log.info("Incrementing war weariness for " + faction.getDisplayName());
                wearinessDelta += enemies.size() * warWearinessPerInterval;
            }
            else wearinessDelta -= warWearinessPerInterval;
            
			modifyWarWeariness(factionId, wearinessDelta);
        }
    }
    
    protected void handleMarketCapture(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
    {
        String loseFactionId = oldOwner.getId();
        if (!warWeariness.containsKey(loseFactionId)) return;
        float value = (market.getSize()^3) * 5;
        
        modifyWarWeariness(loseFactionId, value);
        
        // update revanchism caches
        for (Map.Entry<String, DiplomacyBrain> tmp : diplomacyBrains.entrySet()) {
            tmp.getValue().cacheRevanchism();
        }
    }
	
	public Map<String, MutableStat> getMaxRelationshipModMap(String factionId) {
		return getMaxRelationshipModMap(Global.getSector().getFaction(factionId));
	}
	
	public Map<String, MutableStat> getMaxRelationshipModMap(FactionAPI faction) {
		Map<String, MutableStat> maxTable = (Map<String, MutableStat>)faction.getMemoryWithoutUpdate().get(MEM_KEY_MAX_RELATIONS);
		if (maxTable == null) {
			maxTable = new HashMap<>();
			faction.getMemoryWithoutUpdate().set(MEM_KEY_MAX_RELATIONS, maxTable);
		}
		return maxTable;
	}
	
	/**
	 * Gets the modifier stat to maximum relationship with the other faction (may be null).
	 * @param factionId
	 * @param otherFactionId
	 * @return
	 */
	public MutableStat getMaxRelationshipMod(String factionId, String otherFactionId) {
		return getMaxRelationshipMod(Global.getSector().getFaction(factionId), otherFactionId);
	}
	
	/**
	 * Gets the modifier value to maximum relationship with the other faction, if any.
	 * @param faction
	 * @param otherFactionId
	 * @return
	 */
	public MutableStat getMaxRelationshipMod(FactionAPI faction, String otherFactionId) {
		Map<String, MutableStat> maxTable = this.getMaxRelationshipModMap(faction);		
		return maxTable.get(otherFactionId);
	}
	
	/**
	 * Gets the modifier to maximum relationship with the other faction .
	 * @param factionId
	 * @param otherFactionId
	 * @return
	 */
	public float getMaxRelationshipModValue(String factionId, String otherFactionId) {
		MutableStat stat = getMaxRelationshipMod(factionId, otherFactionId);
		if (stat == null) return 0;
		return stat.getModifiedValue();
	}
	
	public float getMaxRelationship(String factionId, String otherFactionId) {
		if (factionId.equals(otherFactionId)) return 1;

		// check max relationship modifiers
		float mod1 = getMaxRelationshipModValue(factionId, otherFactionId);
		float mod2 = getMaxRelationshipModValue(otherFactionId, factionId);
		float mod = Math.min(mod1, mod2);
		float baseMax = 1;

		if (!haveRandomRelationships(factionId, otherFactionId))
			baseMax = NexFactionConfig.getMaxRelationship(factionId, otherFactionId);

		float result = mod + baseMax;
		if (result < -1) result = -1;
		
		return result;
	}
	
	public void modifyMaxRelationshipMod(String modifierId, float mod, String factionId, String otherFactionId, String desc) 
	{
		MutableStat stat = getMaxRelationshipMod(factionId, otherFactionId);
		if (stat == null) {
			stat = new MutableStat(0);
		}
		Map<String, StatMod> currentMods = stat.getFlatMods();
		float currentValue = 0;
		if (currentMods.containsKey(modifierId)) currentValue = currentMods.get(modifierId).getValue();
		stat.modifyFlat(modifierId, mod + currentValue, desc);
		
		Map<String, MutableStat> modMap = getMaxRelationshipModMap(factionId);
		modMap.put(otherFactionId, stat);
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
        if (NexUtilsFaction.isPirateFaction(winFactionId)) {
            return;
        }
        if (NexUtilsFaction.isPirateFaction(loseFactionId)) {
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
        
        modifyWarWeariness(winFactionId, winnerLosses);
        modifyWarWeariness(loseFactionId, loserLosses);
    }
            
    @Override
    public void advance(float amount)
    {
		if (TutorialMissionIntel.isTutorialInProgress()) return;
        float days = Global.getSector().getClock().convertToDays(amount);
    
        daysElapsed += days;
        if (daysElapsed >= WAR_WEARINESS_INTERVAL)
        {
            daysElapsed -= WAR_WEARINESS_INTERVAL;
            updateWarWeariness();
        }
        
        for (String factionId : SectorManager.getLiveFactionIdsCopy())
        {
            if (disallowedFactions.contains(factionId)) continue;
            getDiplomacyBrain(factionId).advance(days);
        }
        
        this.intervalUtil.advance(days);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        
        if (Global.getSector().isInNewGameAdvance())
            return;
        
        createDiplomacyEvent();
        resetInterval();
    }
    
    @Override
    public void reportPlayerReputationChange(String factionId, float delta) {
        FactionAPI player = Global.getSector().getPlayerFaction();
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        // clamp
        NexUtilsReputation.syncFactionRelationshipToPlayer(playerAlignedFactionId, factionId);
        NexUtilsReputation.syncPlayerRelationshipToFaction(playerAlignedFactionId, factionId);
        //if (!playerAlignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
        //    NexUtilsReputation.syncFactionRelationshipToPlayer(ExerelinConstants.PLAYER_NPC_ID, factionId);
        
        float currentRel = player.getRelationship(factionId);
        boolean isHostile = player.isHostileTo(factionId);
        
        // if we changed peace/war state, decide if alliances should get involved
        // but only if our relationship should be synced
        if (!NexConfig.getFactionConfig(factionId).noSyncRelations) {
            if (isHostile && currentRel - delta > AllianceManager.HOSTILE_THRESHOLD 
                || !isHostile && currentRel - delta < AllianceManager.HOSTILE_THRESHOLD) {
                log.info("Initiating alliance vote due to player relationship with " + factionId + " crossing threshold");
                AllianceVoter.allianceVote(playerAlignedFactionId, factionId, isHostile);
            }
        }
        
        // handled by commission intel
        /*
        if (player.isAtBest(PlayerFactionStore.getPlayerFactionId(), RepLevel.INHOSPITABLE))
            SectorManager.scheduleExpelPlayerFromFaction();
        */
        
        SectorManager.checkForVictory();
        
        if (MilestoneTracker.getIntel() != null)
            MilestoneTracker.getIntel().reportPlayerReputationChanged(factionId);
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
            if (!includePirates && NexUtilsFaction.isPirateFaction(otherFactionId)) continue;
            if (!includeTemplars && otherFactionId.equals("templars")) continue;
            // only count non-pirate non-Templar factions with which we can ceasefire as enemies
            if (mustAllowCeasefire && !NexFactionConfig.canCeasefire(factionId, otherFactionId))
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

    public static void notifyMarketCaptured(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner, boolean isCapture)
    {
        getManager().handleMarketCapture(market, oldOwner, newOwner);
        for (DiplomacyBrain brain : getManager().diplomacyBrains.values())
        {
            brain.updateAllDispositions(0);
        }
        if (!NexUtilsMarket.wasOriginalOwner(market, newOwner.getId())) {
            modifyBadboy(newOwner, market.getSize() * market.getSize());
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
            int enemies = getFactionsAtWarWithFaction(factionId, NexConfig.allowPirateInvasions, true, false).size();
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
        NexFactionConfig factionConfig = NexConfig.getFactionConfig(factionId);
        if (factionConfig == null) return;
        if (factionConfig.hostileToAll <= 0) return;
        
        float relationship = STARTING_RELATIONSHIP_HOSTILE;
        if (factionConfig.hostileToAll == 3) relationship = -1f;
        boolean isPirateNeutral = factionConfig.isPirateNeutral;
        
        for (String otherFactionId : factionIds)
        {
            if (otherFactionId.equals(factionId)) continue;
            if (isPirateNeutral && NexUtilsFaction.isPirateFaction(otherFactionId))
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
        NexFactionConfig conf = NexConfig.getFactionConfig(selectedFactionId);
        if (conf.spawnAsFactionId != null && !conf.spawnAsFactionId.equals(Factions.PLAYER))
            selectedFactionId = conf.spawnAsFactionId;
        
        boolean corvus = SectorManager.getManager().isCorvusMode();
        
        FactionAPI selectedFaction = sector.getFaction(selectedFactionId);
        log.info("Selected faction is " + selectedFaction + " | " + selectedFactionId);

        //List<String> factionIds = SectorManager.getLiveFactionIdsCopy();
        //factionIds.add("independent");
        //factionIds.add(ExerelinConstants.PLAYER_NPC_ID);
        
        List<String> factionIds = new ArrayList<>();
        List<String> alreadyRandomizedIds = new ArrayList<>();
        alreadyRandomizedIds.addAll(DO_NOT_RANDOMIZE);
        
        // should we prevent randomization of pirate factions?
        boolean blockPirates = !manager.applyStartRelationsModeToPirates;
        
        for (FactionAPI faction : sector.getAllFactions())
        {
            if (faction.isNeutralFaction()) continue;
            String factionId = faction.getId();
            factionIds.add(factionId);
            
            // mark non-randomizable factions as such
            if (alreadyRandomizedIds.contains(factionId)) continue;
            if (NexConfig.getFactionConfig(factionId).noRandomizeRelations)
            {
                log.info("Faction " + factionId + " marked as non-randomizable");
                alreadyRandomizedIds.add(factionId);
            }
            else if (blockPirates && NexConfig.getFactionConfig(factionId).pirateFaction)
            {
                log.info("Faction " + factionId + " is pirates, and pirate relations randomization is disabled");
                alreadyRandomizedIds.add(factionId);
            }
        }

        StartRelationsMode mode = manager.startRelationsMode;
        
        // apply relations even in flatten mode
        // we'll do the flattening later
        boolean useScriptRelations = corvus && !mode.isRandom() && !NexConfig.useConfigRelationshipsInNonRandomSector;
        if (useScriptRelations)
        {
            // load vanilla relationships
            VanillaSystemsGenerator.initFactionRelationships(sector);
            for (String factionId : factionIds) {
                NexFactionConfig factionConfig = NexConfig.getFactionConfig(factionId);
                if (factionConfig.useConfigRelationshipsInNonRandomSector) {
                    applyFactionRelationshipsFromConfig(factionId, factionIds, factionConfig);
                }
            }
        }
        else
        {
            // use sector generation seed, unless resetting game at start
            Random random = new Random();
            if (!midgameReset) 
            {
                random.setSeed(NexUtils.getStartingSeed());
            }
            
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
                if (NexUtilsFaction.isPirateFaction(factionId))
                {
                    for (String otherFactionId : factionIds) 
                    {
                        if (otherFactionId.equals(factionId)) continue;
                        FactionAPI otherFaction = sector.getFaction(otherFactionId);
                        if (otherFaction.isNeutralFaction()) continue;
                        
                        NexFactionConfig otherConfig = NexConfig.getFactionConfig(otherFactionId);
                        if (otherConfig != null && (otherConfig.isPirateNeutral || otherConfig.pirateFaction)) continue;
                        
                        otherFaction.setRelationship(factionId, STARTING_RELATIONSHIP_HOSTILE);
                    }
                }
            }

            // randomize if needed
            for (String factionId : factionIds) 
            {
                FactionAPI faction = sector.getFaction(factionId);
                NexFactionConfig factionConfig = NexConfig.getFactionConfig(factionId);
                
                if (mode.isRandom() && !alreadyRandomizedIds.contains(factionId))
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
                        if (NexUtilsFaction.isPirateFaction(factionId) && (otherFactionId.equals(selectedFactionId) || otherFactionId.equals(Factions.PLAYER)))
                        {
                            faction.setRelationship(otherFactionId, STARTING_RELATIONSHIP_HOSTILE);
                        }
                    }
                    handleHostileToAllFaction(factionId, factionIds);
                    
                    // randomize diplomacy traits
                    if (NexConfig.allowRandomDiplomacyTraits && conf.allowRandomDiplomacyTraits) 
                    {
                        faction.getMemoryWithoutUpdate().set(DiplomacyTraits.MEM_KEY_RANDOM_TRAITS, 
                                DiplomacyTraits.generateRandomTraits(random));
                    }
                }

                else    // start hostile with hated factions, friendly with liked ones (from config)
                {
                    applyFactionRelationshipsFromConfig(factionId, factionIds, factionConfig);
                }
            }
        }
        
        // if we leave our faction later, we'll be neutral to most but hostile to pirates and such
        PlayerFactionStore.saveIndependentPlayerRelations();
        
        // run before setting player relations to chosen faction
        if (mode == StartRelationsMode.FLATTEN) {
            for (String factionId : factionIds) 
            {
                resetFactionRelationships(factionId, blockPirates);
            }
        }
        
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
    
    public static void applyFactionRelationshipsFromConfig(String factionId, List<String> otherFactionIds, 
            NexFactionConfig conf) 
    {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        handleHostileToAllFaction(factionId, otherFactionIds);
        for (Map.Entry<String, Float> entry : conf.startRelationships.entrySet())
        {
            if (factionId.equals(entry.getKey())) continue;
            faction.setRelationship(entry.getKey(), entry.getValue());
        }
    }
    
    public static void resetFactionRelationships(String factionId, boolean exemptPirates)
    {
        if (NexUtilsFaction.isPirateFaction(factionId)) {
            if (exemptPirates) {
                log.info("Not flattening relations for pirate faction: " + factionId);
                return;
            }
        }
        else if (NexUtilsFaction.isFactionHostileToAll(factionId)) 
        {
            return;
        }
        
        log.info("Flattening relations for faction " + factionId);
        
        for (FactionAPI otherFaction : Global.getSector().getAllFactions())
        {
            String otherFactionId = otherFaction.getId();
            if (otherFactionId.equals(factionId))
                continue;
            if (NexUtilsFaction.isPirateFaction(otherFactionId) && exemptPirates) {
                log.info("Not flattening relations for other pirate faction: " + factionId);
                continue;
            }
            
            if (NexUtilsFaction.isFactionHostileToAll(otherFactionId)) continue;
            //log.info("  Flattening relations for " + otherFactionId + " with factionId");
            otherFaction.setRelationship(factionId, 0f);
        }        
    }
    
    /**
     * Do these two factions have randomized (or flattened) relationships with each other?
     * @param factionId1
     * @param factionId2
     * @return False if random relations are disabled or either faction is set to non-randomized relations, true otherwise.
     */
    public static boolean haveRandomRelationships(String factionId1, String factionId2) 
    {
        if (getManager().startRelationsMode.isDefault()) return false;
        if (NexConfig.getFactionConfig(factionId1).noRandomizeRelations) 
            return false;
        if (NexConfig.getFactionConfig(factionId2).noRandomizeRelations) 
            return false;
        return true;
    }
    
    @Deprecated
    public static void setRandomFactionRelationships(StartRelationsMode mode, boolean pirate)
    {
        DiplomacyManager manager = getManager();
        manager.applyStartRelationsModeToPirates = pirate;
    }
    
    public static boolean isRandomFactionRelationships()
    {
        return getManager().startRelationsMode.isRandom();
    }
    
    public boolean isRandomPirateFactionRelationships()
    {
        return applyStartRelationsModeToPirates;
    }

    public static float decayBadboy(FactionAPI faction, float elapsedMult) {
        float curr = getBadboy(faction);
        if (curr <= 0) return 0;
        float decay = BADBOY_DECAY_PER_MONTH * elapsedMult;
        curr -= decay;
        if (curr < 0) {
            faction.getMemoryWithoutUpdate().unset(MEM_KEY_BADBOY);
            return 0;
        }
        setBadboy(faction, curr);
        return curr;
    }

    public static float getBadboy(FactionAPI faction) {
        MemoryAPI mem = faction.getMemoryWithoutUpdate();
        if (!mem.contains(MEM_KEY_BADBOY)) return 0;
        return mem.getFloat(MEM_KEY_BADBOY);
    }

    public static void setBadboy(FactionAPI faction, float amount) {
        MemoryAPI mem = faction.getMemoryWithoutUpdate();
        mem.set(MEM_KEY_BADBOY, amount);
    }

    public static float modifyBadboy(FactionAPI faction, float amount) {
        float newAmount = getBadboy(faction) + amount;
        setBadboy(faction, newAmount);
        return newAmount;
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        float numIter = Global.getSettings().getFloat("economyIterPerMonth");
        float tickMult = 1/numIter;
        for (FactionAPI faction : Global.getSector().getAllFactions()) {
            decayBadboy(faction, tickMult);
        }
    }

    public void reportEconomyMonthEnd() {}
    
    public static class DiplomacyEventDef {
        public String name;
        public String id;
        public String desc;
        public boolean random = true;
        public boolean invert;
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
