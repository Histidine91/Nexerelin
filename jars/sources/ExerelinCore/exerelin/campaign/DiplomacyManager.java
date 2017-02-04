package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager.Alliance;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import exerelin.utilities.StringHelper;
import exerelin.world.VanillaSystemsGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    public static class DiplomacyEventDef {
        public String name;
        public String stage;
        public RepLevel minRepLevelToOccur;
        public RepLevel maxRepLevelToOccur;
        public RepLevel repEnsureAtWorst;
        public RepLevel repEnsureAtBest;
        public RepLevel repLimit;
        public float minRepChange;
        public float maxRepChange;
        public List<String> allowedFactions1;
        public List<String> allowedFactions2;
        public boolean allowPirates;
        public float chance;
    }
    public static Logger log = Global.getLogger(DiplomacyManager.class);
    private static DiplomacyManager diplomacyManager;
    
    protected static final String CONFIG_FILE = "data/config/exerelin/diplomacyConfig.json";
    protected static final String MANAGER_MAP_KEY = "exerelin_diplomacyManager";
    
    protected static final List<String> disallowedFactions;
        
    protected static List<DiplomacyEventDef> eventDefs;
    
    public static final float STARTING_RELATIONSHIP_HOSTILE = -0.6f;
    public static final float STARTING_RELATIONSHIP_INHOSPITABLE = -0.35f;
    public static final float STARTING_RELATIONSHIP_WELCOMING = 0.4f;
    public static final float STARTING_RELATIONSHIP_FRIENDLY = 0.6f;
    public static final float WAR_WEARINESS_INTERVAL = 3f;
    public static final float WAR_WEARINESS_FLEET_WIN_MULT = 0.5f; // less war weariness from a fleet battle if you win
    public static final float PEACE_TREATY_CHANCE = 0.3f;
    
    public static final float DOMINANCE_MIN = 0.25f;
    public static final float DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD = -0.67f;
    public static final float DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD = 3f;
    public static final float HARD_MODE_DOMINANCE_MOD = 0.5f;
    
    protected Map<String, Float> warWeariness;
    protected static float warWearinessPerInterval = 50f;
    protected static DiplomacyEventDef peaceTreatyEvent;
    protected static DiplomacyEventDef ceasefireEvent;
    
    protected static float baseInterval = 10f;
    protected float interval = baseInterval;
    protected final IntervalUtil intervalUtil;
    
    protected float daysElapsed = 0;
    protected boolean randomFactionRelationships = false;
    
    static {
        String[] factions = {"templars", "independent"};
        disallowedFactions = new ArrayList<>(Arrays.asList(factions));
        if (!ExerelinConfig.followersDiplomacy) disallowedFactions.add(ExerelinConstants.PLAYER_NPC_ID);
        eventDefs = new ArrayList<>();
        
        try {
            loadSettings();
        } catch (IOException | JSONException ex) {
            Global.getLogger(DiplomacyManager.class).log(Level.ERROR, ex);
        }
    }
    
    private static void loadSettings() throws IOException, JSONException {
        JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
        baseInterval = (float)config.optDouble("eventFrequency", 10f);
        warWearinessPerInterval = (float)config.optDouble("warWearinessPerInterval", 10f);
        
        JSONArray eventsJson = config.getJSONArray("events");
        for(int i=0; i<eventsJson.length(); i++)
        {
            JSONObject eventDefJson = eventsJson.getJSONObject(i);
            DiplomacyEventDef eventDef = new DiplomacyEventDef();
            eventDef.name = eventDefJson.getString("name");
            log.info("Adding diplomacy event " + eventDef.name);
            eventDef.stage = eventDefJson.getString("stage");
            
            eventDef.minRepChange = (float)eventDefJson.getDouble("minRepChange");
            eventDef.maxRepChange = (float)eventDefJson.getDouble("maxRepChange");
            eventDef.allowPirates = eventDefJson.optBoolean("allowPirates", false);
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
            
            if(eventDef.name.equals("Peace Treaty"))
                peaceTreatyEvent = eventDef;
            else if (eventDef.name.equals("Ceasefire"))
                ceasefireEvent = eventDef;
        }
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
        if (factionId.equals(playerAlignedFactionId) && !playerAlignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
        {
            // player_npc faction can be in the same alliance so don't count it two times
            if (!(alliance != null && AllianceManager.getFactionAlliance(ExerelinConstants.PLAYER_NPC_ID) == alliance)) 
            {
                List<MarketAPI> playerNpcMarkets = ExerelinUtilsFaction.getFactionMarkets(ExerelinConstants.PLAYER_NPC_ID);
                ourMarkets.addAll(playerNpcMarkets);
            }
        }
        
        int ourSize = 0;
        for (MarketAPI market: ourMarkets) ourSize += market.getSize();
        
        if (ourSize == 0) return 0;
        
        boolean isPlayer = factionId.equals(playerAlignedFactionId) || (alliance != null && alliance == AllianceManager.getFactionAlliance(playerAlignedFactionId));
        
        float dominance = (float)ourSize / globalSize;
        if (SectorManager.getHardMode() && isPlayer)
            dominance += (1 - dominance) * HARD_MODE_DOMINANCE_MOD;
        
        return dominance;
    }
    
    protected float getDiplomacyInterval()
    {
        int numFactions = SectorManager.getLiveFactionIdsCopy().size() - 2;
        if (numFactions < 0) numFactions = 0;
        return baseInterval * (float)Math.pow(0.95, numFactions);
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
    
    // TODO: refactor/test if all that duplicate handling for player vs. aligned faction is really needed
    public static ExerelinReputationAdjustmentResult adjustRelations(FactionAPI faction1, FactionAPI faction2, float delta,
            RepLevel ensureAtBest, RepLevel ensureAtWorst, RepLevel limit)
    {   
        SectorAPI sector = Global.getSector();
        
        float before = faction1.getRelationship(faction2.getId());
        boolean wasHostile = faction1.isHostileTo(faction2);
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = sector.getFaction(playerAlignedFactionId);
        FactionAPI playerFaction = sector.getPlayerFleet().getFaction();
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
        
        // clamp to configs' min/max relationships
        float after = faction1.getRelationship(faction2Id);
        if (!diplomacyManager.randomFactionRelationships)
        {
            float max = ExerelinFactionConfig.getMaxRelationship(faction1Id, faction2Id);
            float min = ExerelinFactionConfig.getMinRelationship(faction1Id, faction2Id);
            if (delta > 0 && max < after)
                faction1.setRelationship(faction2Id, max);
            if (delta < 0 && min > after)
                faction1.setRelationship(faction2Id, min);

            after = faction1.getRelationship(faction2Id);
        }
        delta = after - before;
        //log.info("Relationship delta: " + delta);
        boolean isHostile = faction1.isHostileTo(faction2);

        // TODO: this could go entirely?
        if(faction1 == playerAlignedFaction)
        {
            playerFaction.setRelationship(faction2Id, after);
            faction2.setRelationship(ExerelinConstants.PLAYER_NPC_ID, after);
        }
        else if(faction2 == playerAlignedFaction)
        {
            playerFaction.setRelationship(faction1Id, after);
            faction1.setRelationship(ExerelinConstants.PLAYER_NPC_ID, after);
        }
        
        // if now at peace/war, set relationships for commission holder
        // TODO figure out if the playerFaction bit is really needed
        ExerelinReputationAdjustmentResult repResult = new ExerelinReputationAdjustmentResult(delta, wasHostile, isHostile);
        if (repResult.wasHostile && !repResult.isHostile)
        {
            String commissionFactionId = ExerelinUtilsFaction.getCommissionFactionId();
            if (commissionFactionId != null && playerAlignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))    // i.e. not with a "real faction"
            {                                                // who wouldn't want to change relations just because our employee is working with an involved faction
                if (commissionFactionId.equals(faction1Id) || AllianceManager.areFactionsAllied(commissionFactionId, faction1Id))
                {
                    // for some reason ensureAtWorst sets it to -25 instead of -49, so don't add the delta on top of that
                    playerAlignedFaction.ensureAtWorst(faction2Id, RepLevel.INHOSPITABLE);
                    //playerAlignedFaction.adjustRelationship(faction2Id, delta);
                    playerFaction.ensureAtWorst(faction2Id, RepLevel.INHOSPITABLE);
                    //playerFaction.adjustRelationship(faction2Id, delta);
                }
                if (commissionFactionId.equals(faction2Id) || AllianceManager.areFactionsAllied(commissionFactionId, faction2Id))
                {
                    playerAlignedFaction.ensureAtWorst(faction1Id, RepLevel.INHOSPITABLE);
                    //playerAlignedFaction.adjustRelationship(faction1Id, delta);
                    playerFaction.ensureAtWorst(faction1Id, RepLevel.INHOSPITABLE);
                    //playerFaction.adjustRelationship(faction1Id, delta);
                }
            }
        }
        else if (!repResult.wasHostile && repResult.isHostile)
        {
            String commissionFactionId = ExerelinUtilsFaction.getCommissionFactionId();
            if (commissionFactionId != null && playerAlignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))    // i.e. not with a "real faction"
            {                                                // who wouldn't want to change relations just because our employee is working with an involved faction
                if (commissionFactionId.equals(faction1Id) || AllianceManager.areFactionsAllied(commissionFactionId, faction1Id))
                {
                    playerAlignedFaction.ensureAtBest(faction2Id, RepLevel.HOSTILE);    // is this needed?
                    playerFaction.ensureAtBest(faction2Id, RepLevel.HOSTILE);
                }
                if (commissionFactionId.equals(faction2Id) || AllianceManager.areFactionsAllied(commissionFactionId, faction2Id))
                {
                    playerAlignedFaction.ensureAtBest(faction1Id, RepLevel.HOSTILE);
                    playerFaction.ensureAtBest(faction1Id, RepLevel.HOSTILE);
                }
            }
        }
        
        AllianceManager.remainInAllianceCheck(faction1Id, faction2Id);
        AllianceManager.syncAllianceRelationshipsToFactionRelationship(faction1Id, faction2Id);
        ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(true);    // note: also syncs player_npc to player
        
        SectorManager.checkForVictory();
        return repResult;
    }
    
    public static ExerelinReputationAdjustmentResult adjustRelations(DiplomacyEventDef event, FactionAPI faction1, FactionAPI faction2, float delta)
    {
        return adjustRelations(faction1, faction2, delta, event.repEnsureAtBest, event.repEnsureAtWorst, event.repLimit);
    }
    
    public void doDiplomacyEvent(DiplomacyEventDef event, MarketAPI market, FactionAPI faction1, FactionAPI faction2)
    {
        SectorAPI sector = Global.getSector();
        
        float delta = MathUtils.getRandomNumberInRange(event.minRepChange, event.maxRepChange);
            
        if (delta < 0 && delta > -0.01f) delta = -0.01f;
        if (delta > 0 && delta < 0.01f) delta = 0.01f;
        delta = Math.round(delta * 100f) / 100f;
       
        ExerelinReputationAdjustmentResult result = DiplomacyManager.adjustRelations(event, faction1, faction2, delta);
        delta = result.delta;
        
        if (Math.abs(delta) >= 0.01f) {
            log.info("Transmitting event: " + event.name);
            HashMap<String, Object> params = new HashMap<>();
            String eventType = "exerelin_diplomacy";
            params.put("event", event);
            params.put("result", result);
            params.put("delta", delta);
            params.put("otherFaction", faction2);
            sector.getEventManager().startEvent(new CampaignEventTarget(market), eventType, params);
        }
    }
    
    public static void createDiplomacyEvent(FactionAPI faction1, FactionAPI faction2)
    {
        createDiplomacyEvent(faction1, faction2, null);
    }
    
    public static void createDiplomacyEvent(FactionAPI faction1, FactionAPI faction2, String eventId)
    {
        if (diplomacyManager == null) return;
        
        String factionId1 = faction1.getId();
        String factionId2 = faction2.getId();
        
        WeightedRandomPicker<DiplomacyEventDef> eventPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker();
        List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(factionId1);
        
        log.info("Factions are: " + faction1.getDisplayName() + ", " + faction2.getDisplayName());
        //float dominance = getDominanceFactor(factionId1) + getDominanceFactor(factionId2);
        //dominance = dominance/2;
        float dominance = Math.max( getDominanceFactor(factionId1), getDominanceFactor(factionId2) );
        log.info("Dominance factor: " + dominance);
        
        DiplomacyEventDef event = null;
        for (DiplomacyEventDef eventDef: eventDefs)
        {
            if (eventDef.stage.equals(eventId))
            {
                event = eventDef;
                break;
            }
            
            if ((ExerelinUtilsFaction.isPirateFaction(factionId1) || ExerelinUtilsFaction.isPirateFaction(factionId2)) && !eventDef.allowPirates)
            {
                //log.info("Pirates on non-pirate event, invalid");
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
            
            float chance = eventDef.chance;
			if (!diplomacyManager.randomFactionRelationships) {
				if (isNegative) {
					float mult = ExerelinFactionConfig.getDiplomacyNegativeChance(factionId1, factionId2);
                    //if (mult != 1) log.info("Applying negative event mult: " + mult);
					chance *= mult;
				}
				else
				{
					float mult = ExerelinFactionConfig.getDiplomacyPositiveChance(factionId1, factionId2);
                    //if (mult != 1) log.info("Applying positive event mult: " + mult);
					chance *= mult;
				}

				if (dominance > DOMINANCE_MIN)
				{
					float strength = (dominance - DOMINANCE_MIN)/(1 - DOMINANCE_MIN);
					if (isNegative) chance = chance + (DOMINANCE_DIPLOMACY_NEGATIVE_EVENT_MOD * strength);
					else chance = chance + (DOMINANCE_DIPLOMACY_POSITIVE_EVENT_MOD * strength);
				}
				if (chance <= 0) continue;
				eventPicker.add(eventDef, chance);
			}
        }
        if (event == null) event = eventPicker.pick();
        
        if (event == null)
        {
            log.info("No event available");
            return;
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
            return;
        }
        
        diplomacyManager.doDiplomacyEvent(event, market, faction1, faction2);
    }
    
    public static void createDiplomacyEvent()
    {
        if (diplomacyManager == null) return;
        
        log.info("Starting diplomacy event creation");
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        
        List<FactionAPI> factions = new ArrayList<>();
        for( String factionId : SectorManager.getLiveFactionIdsCopy())
            factions.add(sector.getFaction(factionId));

        int factionCount = 0;
        for (FactionAPI faction: factions)
        {
            if (faction.isNeutralFaction()) continue;
            if (faction.getId().equals(ExerelinConstants.PLAYER_NPC_ID) && !faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;
            
            if (disallowedFactions.contains(faction.getId())) continue;
            factionPicker.add(faction);
            factionCount++;
        }
        //log.info("Possible factions: " + factionCount);
        if (factionCount < 2) return;
        
        FactionAPI faction1 = factionPicker.pickAndRemove();
        FactionAPI faction2 = factionPicker.pickAndRemove();
        createDiplomacyEvent(faction1, faction2, null);
    }
    
    private void reduceWarWeariness(String factionId, float amount)
    {
        Alliance alliance = AllianceManager.getFactionAlliance(factionId);
        if (alliance != null)
        {
            for (String member : alliance.members) 
            {
                float weariness = getWarWeariness(member);
                weariness = Math.max(weariness - amount, 0);
                warWeariness.put(member, weariness);
            }
        }
        else
        {
            float weariness = getWarWeariness(factionId);
            weariness = Math.max(weariness - amount, 0);
            warWeariness.put(factionId, weariness);
        }
    }
    
    private void updateWarWeariness()
    {
        SectorAPI sector = Global.getSector();
        List<String> factionIds = SectorManager.getLiveFactionIdsCopy();
        FactionAPI factionWithMostWars = null;
        int mostWarCount = 0;
        List<String> enemiesOfFaction = new ArrayList<>();
        
        for(String factionId : factionIds)
        {
            if (ExerelinUtilsFaction.isPirateFaction(factionId)) continue;
            if (disallowedFactions.contains(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            if (faction.isNeutralFaction()) continue;
            if (faction.getId().equals(ExerelinConstants.PLAYER_NPC_ID) && !faction.getId().equals(PlayerFactionStore.getPlayerFactionId())) continue;

            float weariness = getWarWeariness(factionId);
            List<String> enemies = getFactionsAtWarWithFaction(faction, false, false, true);
            int warCount = enemies.size();
            if (warCount > 0)
            {
                //log.info("Incrementing war weariness for " + faction.getDisplayName());
                weariness += enemies.size() * warWearinessPerInterval;
                if (weariness >= ExerelinConfig.minWarWearinessForPeace)
                {
                    if (warCount > mostWarCount)
                    {
                        factionWithMostWars = faction;
                        enemiesOfFaction = enemies;
                        mostWarCount = warCount;
                    }
                }
            }
            else weariness -= warWearinessPerInterval;
            if (weariness < 0) weariness = 0f;
            
            warWeariness.put(factionId, weariness);
        }
        
        // sue for peace?
        if (factionWithMostWars != null)
        {
            log.info("Faction " + factionWithMostWars.getDisplayName() + " wants to sue for peace");
            String factionId = factionWithMostWars.getId();
            ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
            
            WeightedRandomPicker<String> picker = new WeightedRandomPicker();            
            for (String enemy : enemiesOfFaction) {
                picker.add(enemy, getWarWeariness(enemy));
            }
            String toPeace = picker.pick();
            
            float sumWeariness = getWarWeariness(factionWithMostWars.getId()) + getWarWeariness(toPeace);
            log.info("Sum with " + sector.getFaction(toPeace).getDisplayName() + ": " + sumWeariness);
            float divisor = ExerelinConfig.warWearinessDivisorModPerLevel + ExerelinConfig.warWearinessDivisorModPerLevel * sector.getPlayerPerson().getStats().getLevel();
            if (Math.random() > sumWeariness / divisor)
                return;
            log.info("Negotiating treaty");
            boolean peaceTreaty = false;    // if false, only ceasefire
            // can't peace treaty if vengeful, only ceasefire
            if (factionWithMostWars.isAtWorst(toPeace, RepLevel.HOSTILE))
            {
                peaceTreaty = Math.random() < PEACE_TREATY_CHANCE;
            }
            DiplomacyEventDef event = peaceTreaty ? peaceTreatyEvent : ceasefireEvent;
            float reduction = peaceTreaty ? ExerelinConfig.warWearinessPeaceTreatyReduction : ExerelinConfig.warWearinessCeasefireReduction;
            // find someplace to sign the treaty
            List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
            for (MarketAPI market : markets)
            {
                if (market.getFaction() == factionWithMostWars)
                {
                    doDiplomacyEvent(event, market, factionWithMostWars, sector.getFaction(toPeace));
                    reduceWarWeariness(factionWithMostWars.getId(), reduction);
                    reduceWarWeariness(toPeace, reduction);
                    return; // done here
                }
            } 
        }
    }
    
    private void handleMarketCapture(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
    {
        String loseFactionId = oldOwner.getId();
        if (!warWeariness.containsKey(loseFactionId)) return;
        float value = (market.getSize()^3) * 5;
        
        warWeariness.put(loseFactionId, getWarWeariness(loseFactionId) + value);
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
        List<FleetMemberAPI> loserCurrent = loser.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : loser.getFleetData().getSnapshot()) {
            if (!loserCurrent.contains(member)) {
                loserLosses += member.getFleetPointCost();
            }
        }
        List<FleetMemberAPI> winnerCurrent = winner.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : winner.getFleetData().getSnapshot()) {
            if (!winnerCurrent.contains(member)) {
                winnerLosses += member.getFleetPointCost();
            }
        }
        winnerLosses *= WAR_WEARINESS_FLEET_WIN_MULT;
        
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
        
        this.intervalUtil.advance(days);
        if (!this.intervalUtil.intervalElapsed()) {
            return;
        }
        createDiplomacyEvent();
        interval = getDiplomacyInterval();
        intervalUtil.setInterval(interval * 0.75f, interval * 1.25f);
    }
    
    @Override
    public void reportPlayerReputationChange(String faction, float delta) {
        FactionAPI player = Global.getSector().getFaction("player");
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        AllianceManager.syncAllianceRelationshipsToFactionRelationship("player", faction);
        ExerelinUtilsReputation.syncFactionRelationshipToPlayer(playerAlignedFactionId, faction);
        if (!playerAlignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
            ExerelinUtilsReputation.syncFactionRelationshipToPlayer(ExerelinConstants.PLAYER_NPC_ID, faction);
        
        if (player.isAtBest(PlayerFactionStore.getPlayerFactionId(), RepLevel.INHOSPITABLE))
            SectorManager.scheduleExpelPlayerFromFaction();

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
    
    public static DiplomacyManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        diplomacyManager = (DiplomacyManager)data.get(MANAGER_MAP_KEY);
        if (diplomacyManager != null)
        {
            try {
                diplomacyManager.loadSettings();
            } catch (IOException | JSONException ex) {
                Global.getLogger(DiplomacyManager.class).log(Level.ERROR, ex);
            }
            return diplomacyManager;
        }
        
        diplomacyManager = new DiplomacyManager();
        data.put(MANAGER_MAP_KEY, diplomacyManager);
        return diplomacyManager;
    }
    
    public static List<String> getFactionsAtWarWithFaction(String factionId, boolean includePirates, boolean includeTemplars, boolean mustAllowCeasefire)
    {
        return getFactionsAtWarWithFaction(Global.getSector().getFaction(factionId), includePirates, includeTemplars, mustAllowCeasefire);
    }
    
    public static List<String> getFactionsAtWarWithFaction(FactionAPI faction, boolean includePirates, boolean includeTemplars, boolean mustAllowCeasefire)
    {
        SectorAPI sector = Global.getSector();
        String factionId = faction.getId();
        List<String> enemies = new ArrayList<>();
        List<String> factions = SectorManager.getLiveFactionIdsCopy();

        for(String otherFactionId : factions)
        {
            if (!faction.isAtBest(otherFactionId, RepLevel.HOSTILE)) continue;
            if (disallowedFactions.contains(otherFactionId)) continue;
            if (!includePirates && ExerelinUtilsFaction.isPirateFaction(otherFactionId)) continue;
            if (!includeTemplars && otherFactionId.equals("templars")) continue;
            if (mustAllowCeasefire && !ExerelinFactionConfig.canCeasefire(factionId, otherFactionId))    // only count non-pirate non-Templar factions with which we can ceasefire as enemies
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
    
    public static List<String> getFactionsAlliedWithFaction(String factionId)
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
        if (diplomacyManager == null) return;
        diplomacyManager.handleMarketCapture(market, oldOwner, newOwner);
    }
    
    public static float getWarWeariness(String factionId)
    {
        if (diplomacyManager == null) 
        {
            log.info("No diplomacy manager found");
            return 0.0f;
        }
        if (!diplomacyManager.warWeariness.containsKey(factionId))
        {
            diplomacyManager.warWeariness.put(factionId, 0f);
            return 0.0f;
        }
        return diplomacyManager.warWeariness.get(factionId);
    }
    
    public static void setRelationshipAtBest(String factionId, String otherFactionId, float rel)
    {
        FactionAPI faction = Global.getSector().getFaction(factionId);
        float currentRel = faction.getRelationship(otherFactionId);
        if (rel < currentRel)
            faction.setRelationship(otherFactionId, rel);
    }
    
    // set relationships for hostile-to-all factions (Templars, Dark Spire, infected)
    public static void handleHostileToAllFaction(String factionId, List<String> factionIds)
    {
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (factionConfig == null) return;
        if (factionConfig.hostileToAll <= 0) return;
        
        SectorAPI sector = Global.getSector();
        String selectedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        float relationship = STARTING_RELATIONSHIP_HOSTILE;
        if (factionConfig.hostileToAll == 3) relationship = -1f;
        boolean isPirateNeutral = factionConfig.isPirateNeutral;
        
        for (String otherFactionId : factionIds)
        {
            if (otherFactionId.equals(factionId)) continue;
            if (isPirateNeutral && ExerelinUtilsFaction.isPirateFaction(otherFactionId))
                continue;
            boolean isPlayer = otherFactionId.equals(ExerelinConstants.PLAYER_NPC_ID) || otherFactionId.equals(Factions.PLAYER);
            
            FactionAPI otherFaction = sector.getFaction(otherFactionId);
            if (factionConfig.hostileToAll == 1 && isPlayer)
            {
                otherFaction.setRelationship(factionId, STARTING_RELATIONSHIP_INHOSPITABLE);
            }
            else if (!otherFaction.isNeutralFaction() && !otherFactionId.equals("factionId"))
            {
                setRelationshipAtBest(factionId, otherFactionId, relationship);
            }
        }
    }
    
    public static void initFactionRelationships(boolean midgameReset)
    {
        SectorAPI sector = Global.getSector();
        FactionAPI player = sector.getFaction(Factions.PLAYER);
        String selectedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI selectedFaction = sector.getFaction(selectedFactionId);
        log.info("Selected faction is " + selectedFaction + " | " + selectedFactionId);

        //List<String> factionIds = ectorManager.getLiveFactionIdsCopy();
        //factionIds.add("independent");
        //factionIds.add(ExerelinConstants.PLAYER_NPC_ID);
        
        List<String> factionIds = new ArrayList<>();
        List<String> alreadyRandomizedIds = new ArrayList<>();
        alreadyRandomizedIds.add("independent");
        
        for (FactionAPI faction : sector.getAllFactions())
        {
            if (faction.isNeutralFaction()) continue;
            factionIds.add(faction.getId());
        }
        // I don't think we need this any more
        /*
        for (String factionId : factionIds)
        {
            if (!SectorManager.isFactionAlive(factionId) && !factionId.equals(ExerelinConstants.PLAYER_NPC_ID) && !factionId.equals(Factions.PLAYER))
            {
                if (!ExerelinUtilsFaction.isExiInCorvus(factionId)) alreadyRandomizedIds.add(factionId);
                handleHostileToAllFaction(factionId, factionIds);
            }
        }
        */

        boolean randomize = false;
        if (diplomacyManager != null)
        {
            randomize = diplomacyManager.randomFactionRelationships;
        }
        
        if (SectorManager.getCorvusMode() && !randomize)
        {
            // load vanilla relationships
            VanillaSystemsGenerator.initFactionRelationships(sector);
        }
        else
        {
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
            for (String factionId : factionIds) {
                FactionAPI faction = sector.getFaction(factionId);
                if (randomize)
                {
                    if (faction.isNeutralFaction() || faction.isPlayerFaction()) continue;
                    if (alreadyRandomizedIds.contains(factionId)) continue;
                    alreadyRandomizedIds.add(factionId);
                                        
                    for (String otherFactionId: factionIds)
                    {
                        if (alreadyRandomizedIds.contains(otherFactionId)) continue;
                        if (otherFactionId.equals(factionId)) continue;
                        
                        if (ExerelinUtilsFaction.isPirateFaction(factionId) && (otherFactionId.equals(selectedFactionId) || otherFactionId.equals(Factions.PLAYER)))
                        {
                            faction.setRelationship(otherFactionId, STARTING_RELATIONSHIP_HOSTILE);
                            continue;
                        }

                        FactionAPI otherFaction = sector.getFaction(otherFactionId);
                        if (otherFaction.isNeutralFaction() || otherFaction.isPlayerFaction()) continue;

                        if (Math.random() < 0.5) // 50% chance to do nothing (lower clutter)
                        {
                            if (ExerelinUtilsFaction.isPirateFaction(factionId) || ExerelinUtilsFaction.isPirateFaction(otherFactionId))
                                faction.setRelationship(otherFactionId, STARTING_RELATIONSHIP_HOSTILE);
                            else
                                faction.setRelationship(otherFactionId, 0);
                        }
                        else
                            faction.setRelationship(otherFactionId, MathUtils.getRandomNumberInRange(-0.85f, 0.6f));
                    }
                    handleHostileToAllFaction(factionId, factionIds);
                }

                else    // start hostile with hated factions, friendly with liked ones (from config)
                {
                    ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
                    if (factionConfig == null) continue;
                    
                    handleHostileToAllFaction(factionId, factionIds);
                    
                    // legacy method
                    for (String likedFactionId : factionConfig.factionsLiked) {
                        FactionAPI likedFaction = sector.getFaction(likedFactionId);
                        if (likedFaction != null && !likedFaction.isNeutralFaction())
                        {
                            //log.info(faction.getDisplayName() + " likes " + dislikedFaction.getDisplayName());
                            faction.setRelationship(likedFactionId, STARTING_RELATIONSHIP_WELCOMING);
                        }
                    }
                    for (String dislikedFactionId : factionConfig.factionsDisliked) {
                        FactionAPI dislikedFaction = sector.getFaction(dislikedFactionId);
                        if (dislikedFaction != null && !dislikedFaction.isNeutralFaction())
                        {
                            //log.info(faction.getDisplayName() + " hates " + dislikedFaction.getDisplayName());
                            setRelationshipAtBest(factionId, dislikedFactionId, STARTING_RELATIONSHIP_HOSTILE);
                        }
                    }
                    for (String indifferentFactionId : factionConfig.factionsNeutral) {
                        FactionAPI indifferentFaction = sector.getFaction(indifferentFactionId);
                        if (indifferentFaction != null && !indifferentFaction.isNeutralFaction())
                        {
                            faction.setRelationship(indifferentFactionId, 0);
                        }
                    }
                    
                    // new specific number method
                    Iterator<Map.Entry<String, Float>> iter = factionConfig.startRelationships.entrySet().iterator();
                    while( iter.hasNext() ) {
                        Map.Entry<String, Float> tmp = iter.next();
                        String key = tmp.getKey();
                        float value = tmp.getValue();
                        faction.setRelationship(key, value);
                    }
                }
            }
        }
        
        FactionAPI bountyHunters = sector.getFaction("merc_hostile");
        if (bountyHunters != null)
        {
            bountyHunters.setRelationship(Factions.INDEPENDENT, 1f);
            bountyHunters.setRelationship(Factions.PLAYER, -1f);
            bountyHunters.setRelationship(ExerelinConstants.PLAYER_NPC_ID, -1f);
        }
        
        FactionAPI famousBounty = sector.getFaction("famous_bounty");
        if (famousBounty != null)
        {
            for (String factionId : factionIds)
            {
                FactionAPI faction = sector.getFaction(factionId);
                if (!faction.isNeutralFaction() && !factionId.equals("famous_bounty"))
                {
                    famousBounty.setRelationship(factionId, 0f);
                }
            }
            famousBounty.setRelationship(Factions.PLAYER, -1f);
            //famousBounty.setRelationship(ExerelinConstants.PLAYER_NPC_ID, -1f);
        }
		player.setRelationship("shippackfaction", RepLevel.FRIENDLY);
        
        player.setRelationship(ExerelinConstants.PLAYER_NPC_ID, 1f);
        // if we leave our faction later, we'll be neutral to most but hostile to pirates and such
        PlayerFactionStore.saveIndependentPlayerRelations();
        
		// set player relations based on selected faction
        if (selectedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
        {
            ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
        }
        else {
            ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(selectedFactionId, true);
            player.setRelationship(selectedFactionId, STARTING_RELATIONSHIP_FRIENDLY);
            //ExerelinUtilsReputation.syncFactionRelationshipsToPlayer(ExerelinConstants.PLAYER_NPC_ID);	// already done in syncPlayerRelationshipsToFaction
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
    
    public static void setRandomFactionRelationships(boolean random)
    {
        if (diplomacyManager == null) return;
        diplomacyManager.randomFactionRelationships = random;
    }
    
    public static boolean getRandomFactionRelationships()
    {
        return diplomacyManager.randomFactionRelationships;
    }
}
