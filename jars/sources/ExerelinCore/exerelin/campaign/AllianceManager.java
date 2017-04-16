package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

public class AllianceManager  extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(AllianceManager.class);
    protected static AllianceManager allianceManager;   
    protected static final String MANAGER_MAP_KEY = "exerelin_allianceManager";
    protected static final String ALLIANCE_NAMES_FILE = "data/config/exerelin/allianceNames.json";
    protected static final float MIN_ALIGNMENT_FOR_NEW_ALLIANCE = 1f;
    public static final float MIN_ALIGNMENT_TO_JOIN_ALLIANCE = 0f;
    protected static final float MIN_RELATIONSHIP_TO_JOIN = RepLevel.FRIENDLY.getMin();
    protected static final float MIN_RELATIONSHIP_TO_STAY = RepLevel.WELCOMING.getMin();
    protected static final float JOIN_CHANCE_MULT = 0.7f;   // multiplies relationship to get chance to join alliance
    protected static final float JOIN_CHANCE_MULT_PER_MEMBER = 0.8f;
    protected static final float FORM_CHANCE_MULT = 0.6f;   // multiplies relationship to get chance to form alliance
    protected static final float JOIN_CHANCE_FAIL_PER_NEW_ENEMY = 0.4f;
    protected static final List<String> INVALID_FACTIONS = Arrays.asList(new String[] {"templars", "independent"});
    protected static final float HOSTILE_THRESHOLD = -RepLevel.HOSTILE.getMin();
    
    protected static Map<Alignment, List<String>> allianceNamesByAlignment = new HashMap<>();
    protected static Map<Alignment, List<String>> alliancePrefixesByAlignment = new HashMap<>();
    protected static List<String> allianceNameCommonPrefixes;
    
    protected final Set<Alliance> alliances = new HashSet<>();
    protected       Map<String, Alliance> alliancesByName = new HashMap<>();
    protected final Map<String, Alliance> alliancesByFactionId = new HashMap<>();
    
    protected float daysElapsed = 0;
    protected final IntervalUtil tracker;
    
    protected static SectorEntityToken playerInteractionTarget = null;
    
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
            JSONObject namePrefixes = nameConfig.getJSONObject("prefixes");
            JSONArray namePrefixesCommon = namePrefixes.getJSONArray("common");
            allianceNameCommonPrefixes = ExerelinUtils.JSONArrayToArrayList(namePrefixesCommon);
            for (Alignment alignment : Alignment.values())
            {
                List<String> names =  ExerelinUtils.JSONArrayToArrayList( namesByAlignment.getJSONArray(alignment.toString().toLowerCase()) );
                allianceNamesByAlignment.put(alignment, names);
                List<String> prefixes = ExerelinUtils.JSONArrayToArrayList( namePrefixes.getJSONArray(alignment.toString().toLowerCase()) );
                alliancePrefixesByAlignment.put(alignment, prefixes);
            }
        } catch (JSONException | IOException ex) {
                Global.getLogger(AllianceManager.class).log(Level.ERROR, ex);
        }
    }
    
    /**
     * Returns the alignment with the largest affinity sum between two factions (with random tiebreaker)
     * @param factionId
     * @param otherFactionId
     * @return
     */
    public static Alignment getBestAlignment(String factionId, String otherFactionId)
    {
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
            return (Alignment)ExerelinUtils.getRandomListElement(bestAlignments);
        }
        return null;
    }
    
    public static float getAlignmentCompatibilityWithAlliance(String factionId, Alliance alliance)
    {
        if (alliance == null) return 0;
        float value = 0;
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(factionId);
        if (config!= null && config.alignments != null)
        {
            log.info("Checking alliance join validity for faction " + factionId + ", alliance " + alliance.name);
            //log.info("Alliance alignment: " + alliance.alignment.toString());
            Alignment align = alliance.alignment;
            if (config.alignments.containsKey(align))
                value = config.alignments.get(align);
        }
        return value;
    }
    
    public void createAllianceEvent(String faction1, String faction2, Alliance alliance, String stage)
    {
        HashMap<String, Object> params = new HashMap<>();
        SectorAPI sector = Global.getSector();
        String eventType = "exerelin_alliance_changed";
        params.put("faction1", sector.getFaction(faction1));
        if (faction2 != null) params.put("faction2", sector.getFaction(faction2));
        params.put("alliance", alliance);
        params.put("stage", stage);
        
        CampaignEventTarget eventTarget;
        if (playerInteractionTarget != null) {
            eventTarget = new CampaignEventTarget(playerInteractionTarget);
        } else {
            List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(faction1);
            if (markets.isEmpty()) markets = alliance.getAllianceMarkets();
            MarketAPI market = (MarketAPI) ExerelinUtils.getRandomListElement(markets);
            
            eventTarget = new CampaignEventTarget(market);
        }
        
        sector.getEventManager().startEvent(eventTarget, eventType, params);
    }
    
    public static Alliance createAlliance(String member1, String member2, Alignment type)
    {
        return createAlliance(member1, member2, type, null);
    }
    
    public static Alliance createAlliance(String member1, String member2, Alignment type, String name)
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
        
        FactionAPI memberFaction1 = Global.getSector().getFaction(member1);
        FactionAPI memberFaction2 = Global.getSector().getFaction(member2);
        
        if (type == null) type = (Alignment) ExerelinUtils.getRandomArrayElement(Alignment.values());
        
        // name stuff + population count
        float pop1 = 0, pop2 = 0;
        List<String> namePrefixes = new ArrayList<>(allianceNameCommonPrefixes);
        log.info("Getting name prefixes of alliance type " + type);
        if (!alliancePrefixesByAlignment.containsKey(type))
        {
            log.info("Missing name prefixes for alliance type " + type);
            type = Alignment.MILITARIST;
        }
        namePrefixes.addAll(alliancePrefixesByAlignment.get(type));
        
        if (namePrefixes.isEmpty())
        {
            log.info("Missing name prefixes");
            namePrefixes.add("Common");    // just to make it not crash
        }
        
        List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(member1);
        for (MarketAPI market : markets)
        {
            pop1 += market.getSize();
            if (market.getPrimaryEntity().isInHyperspace()) continue;
            String systemName = ((StarSystemAPI)market.getContainingLocation()).getBaseName();
            //if (!namePrefixes.contains(systemName))
               namePrefixes.add(systemName);
        }
        
        markets = ExerelinUtilsFaction.getFactionMarkets(member2);
        for (MarketAPI market : markets)
        {
            pop2 += market.getSize();
            if (market.getPrimaryEntity().isInHyperspace()) continue;
            String systemName = ((StarSystemAPI)market.getContainingLocation()).getBaseName();
            //if (!namePrefixes.contains(systemName))
               namePrefixes.add(systemName);
        }
        
        // generate alliance name
        if (name == null || name.isEmpty())
        {
            boolean validName;
            int tries = 0;
            namePrefixes.addAll(alliancePrefixesByAlignment.get(type));

            do {
                tries++;
                name = (String) ExerelinUtils.getRandomListElement(namePrefixes);
                List<String> alignmentNames = allianceNamesByAlignment.get(type);
                name = name + " " + (String) ExerelinUtils.getRandomListElement(alignmentNames);

                validName = !allianceManager.alliancesByName.containsKey(name);
            }
            while (validName == false && tries < 25);
        }
        
        SectorAPI sector = Global.getSector();
        sector.getFaction(member1).ensureAtWorst(member2, RepLevel.FRIENDLY);
        
        Alliance alliance = new Alliance(name, type, member1, member2);
        allianceManager.alliancesByFactionId.put(member1, alliance);
        allianceManager.alliancesByFactionId.put(member2, alliance);
        allianceManager.alliancesByName.put(name, alliance);
        allianceManager.alliances.add(alliance);
        
        //average out faction relationships
        boolean playerWasHostile1 = memberFaction1.isHostileTo(Factions.PLAYER);
        boolean playerWasHostile2 = memberFaction2.isHostileTo(Factions.PLAYER);
        
        /*
        for (FactionAPI faction : Global.getSector().getAllFactions())
        {
            if (faction.getId().equals(member1)) continue;
            if (faction.getId().equals(member2)) continue;
            
            float rel1 = faction.getRelationship(member1);
            float rel2 = faction.getRelationship(member2);
            float average = (rel1*pop1 + rel2*pop2)/(pop1 + pop2);
            faction.setRelationship(member1, average);
            //faction.setRelationship(member2, average);
        }
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (member1.equals(playerAlignedFactionId) || member2.equals(playerAlignedFactionId))
        {
            ExerelinUtilsReputation.syncPlayerRelationshipsToFaction(playerAlignedFactionId, true);
            if (!playerAlignedFactionId.equals("player_npc"))
                ExerelinUtilsReputation.syncFactionRelationshipsToPlayer("player_npc");
        }
        
        boolean playerIsHostile1 = memberFaction1.isHostileTo(Factions.PLAYER);
        boolean playerIsHostile2 = memberFaction2.isHostileTo(Factions.PLAYER);
        if (playerIsHostile1 != playerWasHostile1 || playerIsHostile2 != playerWasHostile2)
            DiplomacyManager.printPlayerHostileStateMessage(memberFaction1, playerIsHostile1);
        */
        
        allianceManager.createAllianceEvent(member1, member2, alliance, "formed");
        SectorManager.checkForVictory();
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
        if (firstMember == null)
        {
            log.error("Alliance does not exist");
            return;
        }
        
        boolean playerIsHostile = faction.isHostileTo(Factions.PLAYER);
        
        // sync relationship with existing members
        /*
        List<FactionAPI> factions = sector.getAllFactions();
        for (FactionAPI otherFaction: factions)
        {
            String otherFactionId = otherFaction.getId();
            if (otherFaction == faction) continue;
            if (getFactionAlliance(otherFactionId) == alliance) continue;
            if (otherFactionId.equals(Factions.PLAYER) || otherFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
            {
                String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
                if (getFactionAlliance(playerAlignedFactionId) == alliance) continue;
            }
            faction.setRelationship( otherFactionId, firstMember.getRelationship(otherFaction.getId()) );
        }
        */
        
        alliance.members.add(factionId);
        alliancesByFactionId.put(factionId, alliance);
        
        boolean playerWasHostile = faction.isHostileTo(Factions.PLAYER);
        if (playerIsHostile != playerWasHostile)
            DiplomacyManager.printPlayerHostileStateMessage(faction, playerIsHostile);
        
        createAllianceEvent(factionId, null, alliance, "join");
        SectorManager.checkForVictory();
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
        SectorManager.checkForVictory();
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
        alliancesByName.remove(alliance.name);
        alliances.remove(alliance);
        if (randomMember != null) createAllianceEvent(randomMember, null, alliance, "dissolved");
        SectorManager.checkForVictory();
    }  
    

    /**
     * Check all factions for eligibility to join/form an alliance
     */
    public void tryMakeAlliance()
    {
        SectorAPI sector = Global.getSector();
        List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
        Collections.shuffle(liveFactionIds);
        
        // first let's look at forming a new alliance
        for (String factionId : liveFactionIds)
        {
            if (alliancesByFactionId.containsKey(factionId)) continue;
            if (ExerelinUtilsFaction.isPirateFaction(factionId)) continue;
            if (INVALID_FACTIONS.contains(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            
            for (String otherFactionId : liveFactionIds)
            {
                if (alliancesByFactionId.containsKey(otherFactionId)) continue;
                if (otherFactionId.equals(factionId)) continue;
                if (ExerelinUtilsFaction.isPirateFaction(otherFactionId)) continue;
                if (INVALID_FACTIONS.contains(otherFactionId)) continue;
                if (faction.isAtBest(otherFactionId, RepLevel.WELCOMING)) continue;
                
                // better relationships are more likely to form alliances
                float rel = faction.getRelationship(otherFactionId);
                if (Math.random() > rel * FORM_CHANCE_MULT ) continue;
                
                Alignment bestAlignment = getBestAlignment(factionId, otherFactionId);
                if (bestAlignment != null)
                {
                    createAlliance(factionId, otherFactionId, bestAlignment);
                    return; // only one alliance at a time
                }
            }
        }
        
        // no valid alliances to create, let's look for an existing alliance to join
        for (String factionId : liveFactionIds)
        {
            if (alliancesByFactionId.containsKey(factionId)) continue;
            if (INVALID_FACTIONS.contains(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            
            WeightedRandomPicker<Alliance> picker = new WeightedRandomPicker<>();
            
            for (Alliance alliance : alliances)
            {
                float value = getAlignmentCompatibilityWithAlliance(factionId, alliance);
                if (value < MIN_ALIGNMENT_TO_JOIN_ALLIANCE  && !ExerelinConfig.ignoreAlignmentForAlliances) continue;
                
                float relationship = 0;
                boolean abort = false;
                
                // DOES NOT ACTUALLY LOOP - just used to get a member at random
                for (String memberId : alliance.members)
                {
                    if (faction.isHostileTo(memberId))
                    {
                        abort = true;
                        break;
                    }
                    relationship = faction.getRelationship(memberId);
                    if (relationship < MIN_RELATIONSHIP_TO_JOIN || Math.random() > relationship * JOIN_CHANCE_MULT)
                    {
                        abort = true;
                        break;
                    }
                    
                    if (Math.random() > Math.pow(JOIN_CHANCE_MULT_PER_MEMBER, alliance.members.size() ))
                    {
                        abort = true;
                        break;
                    }
                    
                    // don't go joining alliances if it just drags us into multiple wars
                    /*
                    List<String> theirEnemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, false, false, false);
                    List<String> ourEnemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, false, false, false);
                    int numNewEnemies = 0;
                    for (String enemy: theirEnemies)
                    {
                        if (!ourEnemies.contains(enemy)) numNewEnemies++;
                    }
                    //log.info("Joining alliance " + alliance.name + " would add enemies: " + numNewEnemies);
                    if (Math.random() < numNewEnemies * JOIN_CHANCE_FAIL_PER_NEW_ENEMY)
                        abort = true;
                    */
                    
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
        if (Global.getSector().isInNewGameAdvance())
            return;
        
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
    
    /**
     * Returns the average relationship between a faction and the members of an alliance
     * This is only interesting if the faction is a member of that alliance, otherwise all members will have the same relationship with it
     * @param factionId
     * @param alliance
     * @return
     */
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
       
    /**
     * Check if faction should leave an alliance due to disliking its allies
     * @param factionId
     * @param otherFactionId
     */
    public static void remainInAllianceCheck(String factionId, String otherFactionId)
    {
        if (allianceManager == null) return;
        Alliance alliance1 = allianceManager.alliancesByFactionId.get(factionId);
        Alliance alliance2 = allianceManager.alliancesByFactionId.get(otherFactionId);
        if (alliance1 == null || alliance2 == null || alliance1 != alliance2) return;
    
        FactionAPI faction = Global.getSector().getFaction(factionId);
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
        
    public static void doAlliancePeaceStateChange(String faction1Id, String faction2Id, boolean isWar)
    {
        doAlliancePeaceStateChange(faction1Id, faction2Id, 
                getFactionAlliance(faction1Id), getFactionAlliance(faction2Id), isWar);
    }

    public static void doAlliancePeaceStateChange(String faction1Id, String faction2Id, 
            Alliance alliance1, Alliance alliance2, boolean isWar)
    {
        if (alliance1 == null && alliance2 == null)
            return;
        
        SectorAPI sector = Global.getSector();
        FactionAPI faction1 = sector.getFaction(faction1Id);
        FactionAPI faction2 = sector.getFaction(faction2Id);
        /*
        if (faction1.getId().equals(Factions.PLAYER)) return null;
        else if (faction2.getId().equals(Factions.PLAYER)) return null;

        if (faction1.getId().equals(ExerelinConstants.PLAYER_NPC_ID) || faction2.getId().equals(ExerelinConstants.PLAYER_NPC_ID))
        {
            if (PlayerFactionStore.getPlayerFactionId().equals(ExerelinConstants.PLAYER_NPC_ID) == false)
                return null;
        }
        */
        
        // declare war on/make peace with the other faction/alliance
        float delta = isWar ? -0.1f : 0.3f;
        RepLevel ensureAtBest = isWar ? RepLevel.HOSTILE : null;
        RepLevel ensureAtWorst = isWar ? null : RepLevel.INHOSPITABLE;
        RepLevel limit = faction1.getRelationshipLevel(faction2);
        boolean anyChanges = false;
        
        if (alliance1 != null)
        {
            for (String memberId : alliance1.members)
            {
                FactionAPI member = sector.getFaction(memberId);
                if (alliance2 != null)
                {
                    for (String otherMemberId : alliance2.members)
                    {
                        // already in correct state, do nothing
                        FactionAPI otherMember = sector.getFaction(otherMemberId);
                        if (isWar && member.isHostileTo(otherMemberId) || !isWar && !member.isHostileTo(otherMemberId))
                        {
                            continue;
                        }
                        DiplomacyManager.adjustRelations(member, otherMember, delta, 
                                ensureAtBest, ensureAtWorst, limit, true);
                        anyChanges = true;
                    }
                }
                else
                {
                    // already in correct state, do nothing
                    if (isWar && member.isHostileTo(faction2Id) || !isWar && !member.isHostileTo(faction2Id))
                    {
                        continue;
                    }
                    DiplomacyManager.adjustRelations(member, faction2, delta, 
                            ensureAtBest, ensureAtWorst, limit, true);
                    anyChanges = true;
                }
            }
        }
        else if (alliance2 != null)
        {
            for (String memberId : alliance2.members)
            {
                FactionAPI member = sector.getFaction(memberId);
                // already in correct state, do nothing
                if (isWar && member.isHostileTo(faction1Id) || !isWar && !member.isHostileTo(faction1Id))
                {
                    continue;
                }
                DiplomacyManager.adjustRelations(member, faction1, delta, 
                            ensureAtBest, ensureAtWorst, limit, true);
                anyChanges = true;
            }
        }
        
        if (!anyChanges) return;    // done here
        
        // report results
        String party1 = Misc.ucFirst(faction1.getEntityNamePrefix());
        String highlight1 = party1;
        if (alliance1 != null) 
        {
            party1 = alliance1.getAllianceNameAndMembers();
            highlight1 = alliance1.name;
        }
        //else if (faction1.getId().equals("player")) party1 = Misc.ucFirst(playerAlignedFaction.getEntityNamePrefix());

        String party2 = Misc.ucFirst(faction2.getEntityNamePrefix());
        String highlight2 = party2;
        if (alliance2 != null) 
        {
            party2 = alliance2.getAllianceNameAndMembers();
            highlight2 = alliance2.name;
        }
        //else if (faction2.getId().equals("player")) party2 = Misc.ucFirst(playerAlignedFaction.getEntityNamePrefix());

        String messageStr = isWar ? StringHelper.getString("exerelin_alliances", "joinsWarAgainst") 
                : StringHelper.getString("exerelin_alliances", "makesPeaceWith");
        String highlightOrdered1 = highlight1, highlightOrdered2 = highlight2;
        
        if (alliance1 == null) 
        {
            messageStr = StringHelper.substituteToken(messageStr, "$factionOrAlliance1", party2);
            messageStr = StringHelper.substituteToken(messageStr, "$factionOrAlliance2", party1);
            highlightOrdered1 = highlight2;
            highlightOrdered2 = highlight1;
        }
        else 
        {
            messageStr = StringHelper.substituteToken(messageStr, "$factionOrAlliance1", party1);
            messageStr = StringHelper.substituteToken(messageStr, "$factionOrAlliance2", party2);
        }

        // notification message if player's relationships are affected
        Color highlightColor = Misc.getHighlightColor();
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        Alliance playerAlliance = getFactionAlliance(playerAlignedFactionId);
        if (playerAlliance != null && (playerAlliance == alliance1 || playerAlliance == alliance2)
            || faction1.getId().equals(playerAlignedFactionId) || faction2.getId().equals(playerAlignedFactionId))
        {
            highlightColor = isWar ? Misc.getNegativeHighlightColor() : Misc.getPositiveHighlightColor();
        }

        //AllianceSyncMessage message = new AllianceSyncMessage(messageStr, party1, party2);
        CampaignUIAPI ui = sector.getCampaignUI();
        ui.addMessage(messageStr, Color.WHITE, highlightOrdered1, highlightOrdered2, highlightColor, highlightColor);
        
        SectorManager.checkForVictory();
    }
    
    public static void setPlayerInteractionTarget(SectorEntityToken interactionTarget) {
        // used to set entity where the events will be generated
        // set as static value because it is used too deep in the call hierarchy to pass it as argument of every function
        AllianceManager.playerInteractionTarget = interactionTarget;
    }
    
    public static void joinAllianceStatic(String factionId, Alliance alliance)
    {
        if (allianceManager == null) return;
        allianceManager.joinAlliance(factionId, alliance);
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
    
    public static Alliance getAllianceByName(String allianceName)
    {
        if (allianceManager == null) return null;
        return allianceManager.alliancesByName.get(allianceName);
    }
    
    public static Alliance getFactionAlliance(String factionId)
    {
        if (allianceManager == null) return null;
        return allianceManager.alliancesByFactionId.get(factionId);
    }
    
    public static boolean areFactionsAllied(String factionId1, String factionId2)
    {
        if (factionId1.equals(factionId2)) return true;
        if (allianceManager == null) return false;
        Alliance alliance1 = getFactionAlliance(factionId1);
        if (alliance1 == null) return false;
        Alliance alliance2 = getFactionAlliance(factionId2);
        if (alliance2 == null) return false;
        
        return alliance1 == alliance2;
    }
    
    public static void renameAlliance(Alliance alliance, String newName)
    {
        if (allianceManager == null) return;
        if (alliance == null || newName == null) throw new IllegalArgumentException("Alliance or new name is null");
        String oldName = alliance.name;
        if (allianceManager.alliancesByName.containsKey(oldName))
        {
            allianceManager.alliancesByName.remove(oldName);
            allianceManager.alliancesByName.put(newName, alliance);
        }
        alliance.name = newName;
    }
    
    public static AllianceManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        allianceManager = (AllianceManager)data.get(MANAGER_MAP_KEY);
        if (allianceManager != null) {
            
            // reverse compatibility
            if (allianceManager.alliancesByName == null) {
                allianceManager.alliancesByName = new HashMap<>();
                
                for (Alliance alliance : allianceManager.alliances) {
                    allianceManager.alliancesByName.put(alliance.name, alliance);
                }
            }
            
            return allianceManager;
        }
        
        allianceManager = new AllianceManager();
        data.put(MANAGER_MAP_KEY, allianceManager);
        return allianceManager;
    }
    
    public static void printAllianceList(TextPanelAPI text)
    {
        List<AllianceManager.Alliance> alliances = AllianceManager.getAllianceList();
        Collections.sort(alliances, new AllianceManager.AllianceComparator());

        Color hl = Misc.getHighlightColor();

        text.addParagraph(StringHelper.getStringAndSubstituteToken("exerelin_alliances", "numAlliances", "$numAlliances", alliances.size()+""));
        text.highlightInLastPara(hl, "" + alliances.size());
        text.setFontSmallInsignia();
        text.addParagraph("-----------------------------------------------------------------------------");
        for (AllianceManager.Alliance alliance : alliances)
        {
            String allianceName = alliance.name;
            String allianceString = alliance.getAllianceNameAndMembers();

            text.addParagraph(allianceString);
            text.highlightInLastPara(hl, allianceName);
        }
        text.addParagraph("-----------------------------------------------------------------------------");
        text.setFontInsignia();
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
        
        /**
         * Returns a string of format "[Alliance name] ([member1], [member2], ...)"
         * @return
         */
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