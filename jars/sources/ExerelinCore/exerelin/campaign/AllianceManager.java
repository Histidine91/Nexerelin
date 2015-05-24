package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsReputation;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

public class AllianceManager  extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(AllianceManager.class);
    protected static AllianceManager allianceManager;   
    protected static final String MANAGER_MAP_KEY = "exerelin_allianceManager";
    protected static final String ALLIANCE_NAMES_FILE = "data/config/allianceNames.json";
    protected static final float MIN_ALIGNMENT_FOR_NEW_ALLIANCE = 1f;
    protected static final float MIN_ALIGNMENT_TO_JOIN_ALLIANCE = 0f;
    protected static final float MIN_RELATIONSHIP_TO_JOIN = RepLevel.FRIENDLY.getMin();
    protected static final float MIN_RELATIONSHIP_TO_STAY = RepLevel.FAVORABLE.getMin();
    protected static final float JOIN_CHANCE_MULT = 0.5f;
    protected static final float FORM_CHANCE_MULT = 0.5f;
    protected static final float JOIN_CHANCE_FAIL_PER_NEW_ENEMY = 0.4f;
    
    protected static Map<Alignment, List<String>> allianceNamesByAlignment = new HashMap<>();
    protected static List<String> allianceNamePrefixes;
    
    protected final Set<Alliance> alliances = new HashSet<>();
    protected final Map<String, Alliance> alliancesByFactionId = new HashMap<>();
    
    protected float daysElapsed = 0;
    protected final IntervalUtil tracker;
    
    static {
        loadAllianceNames();
    }
    
    public AllianceManager()
    {
        super(true);
        float interval = ExerelinConfig.allianceFormationInterval;
        this.tracker = new IntervalUtil(interval * 0.8f, interval * 1.2f);
    }
    
    public static void loadAllianceNames()
    {
        try {
            JSONObject nameConfig = Global.getSettings().loadJSON(ALLIANCE_NAMES_FILE);
            JSONObject namesByAlignment = nameConfig.getJSONObject("namesByAlignment");
            JSONArray namePrefixes = nameConfig.getJSONArray("prefixes");
            allianceNamePrefixes = ExerelinUtils.JSONArrayToArrayList(namePrefixes);
            for (Alignment alignment : Alignment.values())
            {
                List<String> names =  ExerelinUtils.JSONArrayToArrayList( namesByAlignment.getJSONArray(alignment.toString().toLowerCase()) );
                allianceNamesByAlignment.put(alignment, names);
            }
        } catch (JSONException | IOException ex) {
                Global.getLogger(AllianceManager.class).log(Level.ERROR, ex);
        }
    }
    
    public void createAllianceEvent(String faction1, String faction2, Alliance alliance, String stage)
    {
        HashMap<String, Object> params = new HashMap<>();
        SectorAPI sector = Global.getSector();
        String eventType = "exerelin_alliance_changed";
        params.put("faction1", sector.getFaction(faction1));
        params.put("faction2", sector.getFaction(faction2));
        params.put("alliance", alliance);
        params.put("stage", stage);
        
        List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(faction1);
        MarketAPI market = markets.get(MathUtils.getRandomNumberInRange(0, markets.size() - 1));
        sector.getEventManager().startEvent(new CampaignEventTarget(market), eventType, params);
    }
    
    public static Alliance createAlliance(String member1, String member2, Alignment type)
    {
        if (allianceManager == null) return null;
        
        // first check if one or both parties are already in an alliance
        Alliance alliance1 = allianceManager.alliancesByFactionId.get(member1);
        Alliance alliance2 = allianceManager.alliancesByFactionId.get(member2);
        
        if (alliance1 != null && alliance2 != null)
        {
            log.error("Attempt to form alliance with two factions who are already in an alliance");
            return null;
        }
        else if (alliance1 != null)
        {
            allianceManager.joinAlliance(member2, alliance1);
            return alliance1;
        }
        else if (alliance2 != null)
        {
            allianceManager.joinAlliance(member1, alliance2);
            return alliance2;
        }
        
        // generate alliance name
        String name = "";
        boolean validName = true;
        int tries = 0;
        List<String> namePrefixes = new ArrayList<>(allianceNamePrefixes);
        
        List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(member1);
        float pop1 = 0;
        for (MarketAPI market : markets)
        {
            pop1 += market.getSize();
            if (market.getPrimaryEntity().isInHyperspace()) continue;
            String systemName = ((StarSystemAPI)market.getContainingLocation()).getBaseName();
            //if (!namePrefixes.contains(systemName))
               namePrefixes.add(systemName);
        }
        markets = ExerelinUtilsFaction.getFactionMarkets(member2);
        float pop2 = 0;
        for (MarketAPI market : markets)
        {
            pop2 += market.getSize();
            if (market.getPrimaryEntity().isInHyperspace()) continue;
            String systemName = ((StarSystemAPI)market.getContainingLocation()).getBaseName();
            //if (!namePrefixes.contains(systemName))
               namePrefixes.add(systemName);
        }
        
        do {
            tries++;
            name = namePrefixes.get(MathUtils.getRandomNumberInRange(0, namePrefixes.size() - 1));
            List<String> alignmentNames = allianceNamesByAlignment.get(type);
            name = name + " " + alignmentNames.get(MathUtils.getRandomNumberInRange(0, alignmentNames.size() - 1));
            
            for (Alliance alliance : allianceManager.alliances)
            {
                if (alliance.name.equals(name))
                {
                    validName = false;
                    break;
                }
            }
        }
        while (validName == false && tries < 25);
        
        SectorAPI sector = Global.getSector();
        sector.getFaction(member1).ensureAtWorst(member2, RepLevel.FRIENDLY);
        
        Alliance alliance = new Alliance(name, type, member1, member2);
        allianceManager.alliancesByFactionId.put(member1, alliance);
        allianceManager.alliancesByFactionId.put(member2, alliance);
        allianceManager.alliances.add(alliance);
               
        //average out faction relationships
        for (FactionAPI faction : Global.getSector().getAllFactions())
        {
            if (faction.getId().equals(member1)) continue;
            if (faction.getId().equals(member2)) continue;
            
            float rel1 = faction.getRelationship(member1);
            float rel2 = faction.getRelationship(member2);
            float average = (rel1*pop1 + rel2*pop2)/(pop1 + pop2);
            faction.setRelationship(member1, average);
            faction.setRelationship(member2, average);
        }
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (member1.equals(playerAlignedFactionId) || member2.equals(playerAlignedFactionId))
        {
            ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(playerAlignedFactionId);
            ExerelinUtilsReputation.syncFactionRelationshipsToPlayer("player_npc");
        }
        
        allianceManager.createAllianceEvent(member1, member2, alliance, "formed");
        
        return alliance;
    }
    
    public void joinAlliance(String factionId, Alliance alliance)
    {
        // sync faction relationships
        SectorAPI sector = Global.getSector();
        FactionAPI faction = sector.getFaction(factionId);
        FactionAPI firstMember = null;
        for (String memberId : alliance.members)
        {
            firstMember = sector.getFaction(memberId);
            break;
        }
        
        List<FactionAPI> factions = sector.getAllFactions();
        for (FactionAPI otherFaction: factions)
        {
            faction.setRelationship( otherFaction.getId(), firstMember.getRelationship(otherFaction.getId()) );
        }
        
        alliance.members.add(factionId);
        alliancesByFactionId.put(factionId, alliance);
        
        createAllianceEvent(factionId, null, alliance, "join");
    }
       
    public void leaveAlliance(String factionId, Alliance alliance, boolean noEvent)
    {
        alliance.members.remove(factionId);
        alliancesByFactionId.remove(factionId);
        if (alliance.members.size() <= 1) 
        {
            dissolveAlliance(alliance);
            return;
        }
        
        if (!noEvent) createAllianceEvent(factionId, null, alliance, "leave");
    }
    
    public void leaveAlliance(String factionId, Alliance alliance)
    {
        leaveAlliance(factionId, alliance, false);
    }
    
    public void dissolveAlliance(Alliance alliance)
    {
        if (!alliances.contains(alliance)) return;
        
        String randomMember = null;
        for (String member : alliance.members)
        {
            alliancesByFactionId.remove(member);
            randomMember = member;
        }
        alliances.remove(alliance);
        if (randomMember != null) createAllianceEvent(randomMember, null, alliance, "dissolve");
    }  
    
    // check factions for eligibility to join/form an alliance
    public void tryMakeAlliance()
    {
        SectorAPI sector = Global.getSector();
        List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
        Collections.shuffle(liveFactionIds);
        
        // first let's look at forming a new alliance
        for (String factionId : liveFactionIds)
        {
            if (alliancesByFactionId.containsKey(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            
            for (String otherFactionId : liveFactionIds)
            {
                if (alliancesByFactionId.containsKey(otherFactionId)) continue;
                if (otherFactionId.equals(factionId)) continue;
                if (faction.isAtBest(otherFactionId, RepLevel.WELCOMING)) continue;
                
                // better relationships are more likely to form alliances
                float rel = faction.getRelationship(otherFactionId);
                if (Math.random() > rel * FORM_CHANCE_MULT ) continue;
                
                float bestAlignmentValue = 0;
                List<Alignment> bestAlignments = new ArrayList<>();
                ExerelinFactionConfig config1 = ExerelinConfig.getExerelinFactionConfig(factionId);
                ExerelinFactionConfig config2 = ExerelinConfig.getExerelinFactionConfig(otherFactionId);   
                for (Alignment alignment : Alignment.values())
                {
                    float alignment1 = 0;
                    float alignment2 = 0;
                    if (config1 != null)
                    {
                        alignment1 = config1.alignments.get(alignment);
                    }
                    if (config2 != null)
                    {
                        alignment2 = config2.alignments.get(alignment);
                    }
                    float sum = alignment1 + alignment2;
                    if (sum < MIN_ALIGNMENT_FOR_NEW_ALLIANCE && !ExerelinConfig.ignoreAlignmentForAlliances) continue;
                    if (sum > bestAlignmentValue)
                    {
                        bestAlignments.clear();
                        bestAlignmentValue = sum;
                        bestAlignments.add(alignment);
                    }
                    else if (sum == bestAlignmentValue)
                    {
                        bestAlignments.add(alignment);
                    }
                }
                if (!bestAlignments.isEmpty())
                {
                    // okay, make alliance
                    Alignment allianceAlignment = bestAlignments.get(MathUtils.getRandomNumberInRange(0, bestAlignments.size() - 1));
                    createAlliance(factionId, otherFactionId, allianceAlignment);
                    return; // only one alliance at a time
                }
            }
        }
        
        // no valid alliances to create, let's look for an existing alliance to join
        for (String factionId : liveFactionIds)
        {
            if (alliancesByFactionId.containsKey(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            
            WeightedRandomPicker<Alliance> picker = new WeightedRandomPicker<>(); 
            ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
            
            for (Alliance alliance : alliances)
            {
                float value = 0;
                if (config!= null && config.alignments != null)
                {
                    //log.info("Alliance alignment: " + alliance.alignment.toString());
                    value = config.alignments.get(alliance.alignment);
                }
                if (value < MIN_ALIGNMENT_TO_JOIN_ALLIANCE  && !ExerelinConfig.ignoreAlignmentForAlliances) continue;
                
                float relationship = 0;
                boolean abort = false;
                
                for (String memberId : alliance.members)
                {
                    if (faction.isHostileTo(memberId))
                    {
                        abort = true;
                    }
                    relationship = faction.getRelationship(memberId);
                    if (relationship < MIN_RELATIONSHIP_TO_JOIN || Math.random() > relationship * JOIN_CHANCE_MULT)
                    {
                        abort = true;
                    }
                    
                    // don't go joining alliances if it just drags us into multiple wars
                    List<String> theirEnemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, false, false);
                    List<String> ourEnemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, false, false);
                    int numNewEnemies = 0;
                    for (String enemy: theirEnemies)
                    {
                        if (!ourEnemies.contains(enemy)) numNewEnemies++;
                    }
                    //log.info("Joining alliance " + alliance.name + " would add enemies: " + numNewEnemies);
                    if (Math.random() < numNewEnemies * JOIN_CHANCE_FAIL_PER_NEW_ENEMY)
                        abort = true;
                    
                    break;
                }
                if (abort) continue;
                
                picker.add(alliance, relationship * (value + 1));
            }
            // okay, join an alliance
            if (!picker.isEmpty())
            {
                joinAlliance(factionId, picker.pick());
                return; // only one alliance at a time
            }
        }
    }
    
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        if (daysElapsed < ExerelinConfig.allianceGracePeriod)
        {
            daysElapsed += days;
            return;
        }
        
        this.tracker.advance(days);
        if (!this.tracker.intervalElapsed()) {
            return;
        }
        float interval = ExerelinConfig.allianceFormationInterval;
        tracker.setInterval(interval * 0.8f, interval * 1.2f);
        
        tryMakeAlliance();
    }
    
    @Override
    public boolean runWhilePaused()
    {
        return false;
    }
    
    @Override
    public boolean isDone()
    {
        return false;
    }
    
    public static float getAverageRelationshipWithAlliance(String factionId, Alliance alliance)
    {
        float sumRelationships = 0;
        int numFactions = 0;
        FactionAPI faction = Global.getSector().getFaction(factionId);
        for (String memberId : alliance.members)
        {
            if (memberId.equals(factionId)) continue;
            sumRelationships += faction.getRelationship(memberId);
            numFactions++;
        }
        if (numFactions == 0) return 1;
        return sumRelationships/numFactions;
    }
       
    public static void remainInAllianceCheck(String factionId, String otherFactionId)
    {
        if (allianceManager == null) return;
        Alliance alliance1 = allianceManager.alliancesByFactionId.get(factionId);
        Alliance alliance2 = allianceManager.alliancesByFactionId.get(otherFactionId);
        if (alliance1 == null || alliance2 == null || alliance1 != alliance2) return;
    
        FactionAPI faction = Global.getSector().getFaction(factionId);
        FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
        if (faction.isHostileTo(otherFactionId))
        {
            // no fighting here, both of you get out of our clubhouse!
            if (alliance1.members.size() <= 3) allianceManager.dissolveAlliance(alliance1);
            else
            {
                allianceManager.leaveAlliance(factionId, alliance1);
                allianceManager.leaveAlliance(otherFactionId, alliance1);
            }
        }
        else
        {
            boolean leave1 = false;
            boolean leave2 = false;
            int numLeavers = 0;
            float averageRel1 = getAverageRelationshipWithAlliance(factionId, alliance1);
            float averageRel2 = getAverageRelationshipWithAlliance(otherFactionId, alliance1);
            
            if (averageRel1 < MIN_RELATIONSHIP_TO_STAY)
            {
                leave1 = true;
                numLeavers++;
            }
            if (averageRel2 < MIN_RELATIONSHIP_TO_STAY)
            {
                leave2 = true;
                numLeavers++;
            }
            // both want to leave
            if (numLeavers == 2)
            {
                if (alliance1.members.size() <= 3) allianceManager.dissolveAlliance(alliance1);
                else
                {
                    allianceManager.leaveAlliance(factionId, alliance1);
                    allianceManager.leaveAlliance(otherFactionId, alliance1);
                }
            }
            else if (leave1) allianceManager.leaveAlliance(factionId, alliance1);
            else if (leave2) allianceManager.leaveAlliance(otherFactionId, alliance1);
        }
    }
    
    protected static float setRelationshipAndUpdateDelta(FactionAPI faction, String otherFactionId, float newRel, float highestDelta)
    {
        float oldRel = faction.getRelationship(otherFactionId);
        if (oldRel == newRel) return highestDelta;
        faction.setRelationship(otherFactionId, newRel);
        float delta = newRel - oldRel;
        if (Math.abs(delta) > Math.abs(highestDelta)) highestDelta = delta;
        return highestDelta;
    }
    
    public static AllianceSyncMessage syncAllianceRelationshipsToFactionRelationship(String factionId1, String factionId2)
    {
        if (allianceManager == null) return null;
        
        Alliance alliance1 = allianceManager.alliancesByFactionId.get(factionId1);
        Alliance alliance2 = allianceManager.alliancesByFactionId.get(factionId2);
        
        if (alliance1 == alliance2) // e.g. if both are null
        {
            if (alliance1 != null) log.info("Same alliance: " + alliance1.name);
            return null; 
        }
        
        SectorAPI sector = Global.getSector();
        FactionAPI faction1 = sector.getFaction(factionId1);
        FactionAPI faction2 = sector.getFaction(factionId2);
        float relationship = faction1.getRelationship(factionId2);
        float highestDelta = 0;
        
        if (alliance1 != null)
        {
            for (String memberId : alliance1.members)
            {
                FactionAPI member = sector.getFaction(memberId);
                if (alliance2 != null)
                {
                    for (String otherFactionId : alliance2.members)
                    {
                        highestDelta = setRelationshipAndUpdateDelta(member, otherFactionId, relationship, highestDelta);
                    }
                }
                else highestDelta = setRelationshipAndUpdateDelta(member, factionId2, relationship, highestDelta);
            }
        }
        else if (alliance2 != null)
        {
            for (String memberId : alliance2.members)
            {
                FactionAPI member = sector.getFaction(memberId);
                highestDelta = setRelationshipAndUpdateDelta(member, factionId1, relationship, highestDelta);
            }
        }
        
        // 1 = was at war, now at peace
        // -1 = was at peace, now at war
        // 0 = no change
        int peaceState = 0;
        float hostileBoundary = -RepLevel.HOSTILE.getMin();
        if (relationship < hostileBoundary && relationship - highestDelta >= hostileBoundary) peaceState = -1;  // now hostile, was not hostile
        else if (relationship >= hostileBoundary && relationship - highestDelta < hostileBoundary) peaceState = 1; // now not hostile, was hostile
        //log.info("Peace state: " + peaceState + " (delta " + highestDelta + ")");
        
        if (peaceState != 0)
        {
            if (faction1.getId().equals("player")) return null;
            else if (faction2.getId().equals("player")) return null;
            
            String party1 = Misc.ucFirst(faction1.getEntityNamePrefix());
            if (alliance1 != null) party1 = alliance1.getAllianceNameAndMembers();
            //else if (faction1.getId().equals("player")) party1 = Misc.ucFirst(playerAlignedFaction.getEntityNamePrefix());
            
            String party2 = Misc.ucFirst(faction2.getEntityNamePrefix());
            if (alliance2 != null) party2 = alliance2.getAllianceNameAndMembers();
            //else if (faction2.getId().equals("player")) party2 = Misc.ucFirst(playerAlignedFaction.getEntityNamePrefix());
            
            String action = " joins war against ";
            if (peaceState == 1) action = " makes peace with ";
            
            String messageStr = "";
            if (alliance1 == null) messageStr = party2 + action + party1;
            else messageStr = party1 + action + party2;
            if (peaceState == -1) messageStr += "!";
            
            AllianceSyncMessage message = new AllianceSyncMessage(messageStr, party1, party2);
            CampaignUIAPI ui = sector.getCampaignUI();
            ui.addMessage(messageStr, Color.WHITE, party1, party2, Misc.getHighlightColor(), Misc.getHighlightColor());
        }
        return null;
    }
    
    public static void leaveAlliance(String factionId, boolean noEvent)
    {
        if (allianceManager == null) return;
        Alliance alliance = allianceManager.alliancesByFactionId.get(factionId);
        if (alliance == null) return;
        allianceManager.leaveAlliance(factionId, alliance, noEvent);
    }
    
    public static List<Alliance> getAllianceList()
    {
        if (allianceManager == null) return new ArrayList<>();
        return new ArrayList<>(allianceManager.alliances);
    }
    
    public static Alliance getFactionAlliance(String factionId)
    {
        if (allianceManager == null) return null;
        return allianceManager.alliancesByFactionId.get(factionId);
    }
    
    public static AllianceManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        allianceManager = (AllianceManager)data.get(MANAGER_MAP_KEY);
        if (allianceManager != null)
            return allianceManager;
        
        allianceManager = new AllianceManager();
        data.put(MANAGER_MAP_KEY, allianceManager);
        return allianceManager;
    }
    
    public static class Alliance {
        public String name;
        public Set<String> members;
        public Alignment alignment;
        
        public Alliance(String name, Alignment alignment, String member1, String member2)
        {
            this.name = name;
            this.alignment = alignment;
            members = new HashSet<>();
            members.add(member1);
            members.add(member2);
        }
        
        public List<MarketAPI> getAllianceMarkets()
        {
            List<MarketAPI> markets = new ArrayList<>();
            for (String memberId : members)
            {
                List<MarketAPI> factionMarkets = ExerelinUtilsFaction.getFactionMarkets(memberId);
                markets.addAll(factionMarkets);
            }
            return markets;
        }
        
        public int getNumAllianceMarkets()
        {
            int numMarkets = 0;
            for (String memberId : members)
            {
                numMarkets += ExerelinUtilsFaction.getFactionMarkets(memberId).size();
            }
            return numMarkets;
        }
    
        public int getAllianceMarketSizeSum()
        {
            int size = 0;
            for (String memberId : members)
            {
                for (MarketAPI market : ExerelinUtilsFaction.getFactionMarkets(memberId))
                {
                    size += market.getSize();
                }
            }
            return size;
        }
        
        public String getAllianceNameAndMembers()
        {
            String factions = "";
            int num = 0;
            for (String memberId : members)
            {
                FactionAPI faction = Global.getSector().getFaction(memberId);
                factions += Misc.ucFirst(faction.getEntityNamePrefix());
                num++;
                if (num < members.size()) factions += ", ";
            }
            return name + " (" + factions + ")";
        }
    }
    
    public static class AllianceSyncMessage {
        public String message;
        public String party1;
        public String party2;
        
        public AllianceSyncMessage(String message, String party1, String party2)
        {
            this.message = message;
            this.party1 = party1;
            this.party2 = party2;
        }
    }
    
    public enum Alignment {
        CORPORATE,
        TECHNOCRATIC,
        MILITARIST,
        DIPLOMATIC,
        IDEOLOGICAL
    }
    
    public static class AllianceComparator implements Comparator<AllianceManager.Alliance>
    {
        @Override
        public int compare(AllianceManager.Alliance alliance1, AllianceManager.Alliance alliance2) {

            int size1 = alliance1.members.size();
            int size2 = alliance2.members.size();

            if (size1 > size2) return -1;
            else if (size2 > size1) return 1;
            else return 0;
        }
    }
}
