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
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.intel.AllianceIntel;
import exerelin.campaign.intel.AllianceIntel.UpdateType;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AllianceManager  extends BaseCampaignEventListener implements EveryFrameScript {
    public static Logger log = Global.getLogger(AllianceManager.class);
    
    protected static final String MANAGER_MAP_KEY = "exerelin_allianceManager";
    protected static final String ALLIANCE_NAMES_FILE = "data/config/exerelin/allianceNames.json";
    protected static final float MIN_ALIGNMENT_FOR_NEW_ALLIANCE = 1f;
    public static final float MIN_ALIGNMENT_TO_JOIN_ALLIANCE = 0f;
    public static final float MIN_RELATIONSHIP_TO_JOIN = RepLevel.FRIENDLY.getMin();
    public static final float MIN_RELATIONSHIP_TO_STAY = RepLevel.WELCOMING.getMin();
    protected static final float JOIN_CHANCE_MULT = 0.7f;   // multiplies relationship to get chance to join alliance
    protected static final float JOIN_CHANCE_MULT_PER_MEMBER = 0.8f;
    protected static final float FORM_CHANCE_MULT = 0.6f;   // multiplies relationship to get chance to form alliance
    protected static final float JOIN_CHANCE_FAIL_PER_NEW_ENEMY = 0.4f;
    protected static final List<String> INVALID_FACTIONS = Arrays.asList(new String[] {"templars", Factions.INDEPENDENT});
    public static final float HOSTILE_THRESHOLD = -RepLevel.HOSTILE.getMin();
    
    protected static Map<Alignment, List<String>> allianceNamesByAlignment = new HashMap<>();
    protected static Map<Alignment, List<String>> alliancePrefixesByAlignment = new HashMap<>();
    protected static List<String> allianceNameCommonPrefixes;
	
	// This stores pre-defined alliance names for certain faction pairings
	// first value in array = faction ID 1, second value = faction ID 2, third value = name
	// Allowed names for an alliance are insensitive to the order in which the founder IDs are specified
	protected static List<String[]> staticAllianceNames = new ArrayList<>();
    
    protected final Set<Alliance> alliances = new HashSet<>();
    protected final Map<String, Alliance> alliancesByName = new HashMap<>();
	// UUID keys; never removed from
	protected final Map<String, Alliance> alliancesById = new HashMap<>();	
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
        float interval = 2f;
        this.tracker = new IntervalUtil(interval * 0.8f, interval * 1.2f);
    }
    
    protected static void loadAllianceNames()
    {
        try {
            JSONObject nameConfig = Global.getSettings().getMergedJSONForMod(ALLIANCE_NAMES_FILE, ExerelinConstants.MOD_ID);
            JSONObject namesByAlignment = nameConfig.getJSONObject("namesByAlignment");
            JSONObject namePrefixes = nameConfig.getJSONObject("prefixes");
            JSONArray namePrefixesCommon = namePrefixes.getJSONArray("common");
            
			allianceNameCommonPrefixes = NexUtils.JSONArrayToArrayList(namePrefixesCommon);
            for (Alignment alignment : Alignment.values())
            {
                List<String> names =  NexUtils.JSONArrayToArrayList( namesByAlignment.getJSONArray(alignment.toString().toLowerCase(Locale.ROOT)) );
                allianceNamesByAlignment.put(alignment, names);
                List<String> prefixes = NexUtils.JSONArrayToArrayList( namePrefixes.getJSONArray(alignment.toString().toLowerCase(Locale.ROOT)) );
                alliancePrefixesByAlignment.put(alignment, prefixes);
            }
			
			JSONObject staticNames = nameConfig.getJSONObject("staticNames");
			//log.info("Loading static alliance names");
			Iterator<String> keys = staticNames.sortedKeys();
			while (keys.hasNext()) 
			{
				String factionId1 = (String)keys.next();
				JSONObject namesLevel2 = staticNames.getJSONObject(factionId1);
				
				Iterator<String> keys2 = namesLevel2.sortedKeys();
				while (keys2.hasNext()) {
					String factionId2 = (String)keys2.next();
					List<String> namesLevel3 = NexUtils.JSONArrayToArrayList(namesLevel2.getJSONArray(factionId2));
					for (String allianceName : namesLevel3) {
						String[] nameEntry = new String[]{factionId1, factionId2, allianceName};
						staticAllianceNames.add(nameEntry);
					}
				}
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
        NexFactionConfig config1 = NexConfig.getFactionConfig(factionId);
        NexFactionConfig config2 = NexConfig.getFactionConfig(otherFactionId);   
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
            if (sum < MIN_ALIGNMENT_FOR_NEW_ALLIANCE && !NexConfig.ignoreAlignmentForAlliances) continue;
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
            return NexUtils.getRandomListElement(bestAlignments);
        }
        return null;
    }
    
    public static float getAlignmentCompatibilityWithAlliance(String factionId, Alliance alliance)
    {
        if (alliance == null) return 0;
        float value = 0;
        NexFactionConfig config = NexConfig.getFactionConfig(factionId);
        if (config.alignments != null)
        {
            //log.info("Checking alliance join validity for faction " + factionId + ", alliance " + alliance.getName());
            //log.info("Alliance alignment: " + alliance.alignment.toString());
            Alignment align = alliance.getAlignment();
            if (config.alignments.containsKey(align))
                value = config.alignments.get(align);
        }
        return value;
    }
    
    public static Alliance createAlliance(String member1, String member2, Alignment type)
    {
        return createAlliance(member1, member2, type, null);
    }
    
    public static Alliance createAlliance(String member1, String member2, Alignment type, String name)
    {
        AllianceManager manager = getManager();
        
        // first check if one or both parties are already in an alliance
        Alliance alliance1 = manager.alliancesByFactionId.get(member1);
        Alliance alliance2 = manager.alliancesByFactionId.get(member2);
        
        if (alliance1 != null && alliance2 != null)
        {
            log.error("Attempt to form alliance with two factions who are already in an alliance");
            return null;
        }
        else if (alliance1 != null)
        {
            manager.joinAlliance(member2, alliance1);
            return alliance1;
        }
        else if (alliance2 != null)
        {
            manager.joinAlliance(member1, alliance2);
            return alliance2;
        }
        
        if (type == null) type = (Alignment) NexUtils.getRandomArrayElement(Alignment.values());
        
        List<String> namePrefixes = generateNamePrefixes(member1, member2, type);
        
        // generate alliance name
        if (name == null || name.isEmpty())
        {
        	name = generateStaticAllianceName(member1, member2);
        }
		if (name == null || name.isEmpty())
        {
        	name = generateAllianceName(namePrefixes, type);
        }
        
        SectorAPI sector = Global.getSector();
        sector.getFaction(member1).ensureAtWorst(member2, RepLevel.FRIENDLY);
        
        Alliance alliance = new Alliance(name, type, member1, member2);
        manager.alliancesByFactionId.put(member1, alliance);
        manager.alliancesByFactionId.put(member2, alliance);
        manager.alliancesByName.put(name, alliance);
        manager.alliancesById.put(alliance.uuId, alliance);
        manager.alliances.add(alliance);
        alliance.createIntel(member1, member2);
        SectorManager.checkForVictory();
        return alliance;
    }

    private static List<String> generateNamePrefixes(String member1, String member2, Alignment type) {
		List<String> namePrefixes = new ArrayList<>(allianceNameCommonPrefixes);
		log.info("Getting name prefixes of alliance type " + type);
		if (!alliancePrefixesByAlignment.containsKey(type))
		{
			log.error("Missing name prefixes for alliance type " + type);
		}
		else namePrefixes.addAll(alliancePrefixesByAlignment.get(type));

		if (namePrefixes.isEmpty())
		{
			log.error("Missing name prefixes");
			namePrefixes.add("Common");    // just to make it not crash
		}

		List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(member1);
		for (MarketAPI market : markets)
		{
			if (market.getPrimaryEntity().isInHyperspace()) continue;
			String systemName = ((StarSystemAPI)market.getContainingLocation()).getBaseName();
			namePrefixes.add(systemName);
		}

		markets = NexUtilsFaction.getFactionMarkets(member2);
		for (MarketAPI market : markets)
		{
			if (market.getPrimaryEntity().isInHyperspace()) continue;
			if (market.getContainingLocation() == null) continue;
			String systemName = ((StarSystemAPI)market.getContainingLocation()).getBaseName();
			namePrefixes.add(systemName);
		}
		return namePrefixes;
	}
	
	public static String generateStaticAllianceName(String factionId1, String factionId2) 
	{
		if (DiplomacyManager.isRandomFactionRelationships())
			return null;
		if (NexConfig.predefinedAllianceNameChance < Math.random())
			return null;
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		for (String[] entry : staticAllianceNames) {
			if (entry[0].equals(factionId1) && entry[1].equals(factionId2))
				picker.add(entry[2]);
			else if (entry[0].equals(factionId2) && entry[1].equals(factionId1))
				picker.add(entry[2]);
		}
		
		return picker.pick();
	}
	
    public static String generateAllianceName(List<String> namePrefixes, Alignment type) {
    	String name;
		boolean validName;
		int tries = 0;
		namePrefixes.addAll(alliancePrefixesByAlignment.get(type));

		do {
			tries++;
			name = NexUtils.getRandomListElement(namePrefixes);
			List<String> alignmentNames = allianceNamesByAlignment.get(type);
			name = name + " " + NexUtils.getRandomListElement(alignmentNames);

			validName = !getManager().alliancesByName.containsKey(name);
		}
		while (!validName && tries < 25);

		//I doubt this will occur
		if(tries == 25)
			name = "No Valid Alliance Name " + System.currentTimeMillis();

		return  name;
	}

    public void joinAlliance(String factionId, Alliance alliance)
    {
        // sync faction relationships
        SectorAPI sector = Global.getSector();
        FactionAPI faction = sector.getFaction(factionId);
        FactionAPI firstMember = null;
        for (String memberId : alliance.getMembersCopy())
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
        
        alliance.addMember(factionId);
        alliancesByFactionId.put(factionId, alliance);
        
        // don't do this anymore, since relationship doesn't change on joining alliance
        //boolean playerWasHostile = faction.isHostileTo(Factions.PLAYER);
        //if (playerIsHostile != playerWasHostile)
        //    DiplomacyManager.printPlayerHostileStateMessage(faction, playerIsHostile, false);
        
        alliance.updateIntel(factionId, null, UpdateType.JOINED);
        SectorManager.checkForVictory();
    }
       
    public void leaveAlliance(String factionId, Alliance alliance, boolean noEvent)
    {
        if (alliance.isPermaMember(factionId)) return;
        
        if (alliance.getMembersCopy().size() <= 2) 
        {
            dissolveAlliance(alliance);
            return;
        }
		
        alliance.removeMember(factionId);
        alliancesByFactionId.remove(factionId);
        
        if (!noEvent) alliance.updateIntel(factionId, null, UpdateType.LEFT);
        SectorManager.checkForVictory();
    }
    
    public void leaveAlliance(String factionId, Alliance alliance)
    {
        leaveAlliance(factionId, alliance, false);
    }
    
    public void dissolveAlliance(Alliance alliance)
    {
        if (!alliances.contains(alliance)) return;
		
		WeightedRandomPicker<String> memberPicker = new WeightedRandomPicker<>();
        
        for (String member : alliance.getMembersCopy())
        {
            alliancesByFactionId.remove(member);
			memberPicker.add(member);
        }
        alliancesByName.remove(alliance.getName());
		//alliancesById.remove(alliance.uuId);	// events will still want to read this
        alliances.remove(alliance);
		alliance.clearMembers();

        alliance.updateIntel(memberPicker.pickAndRemove(), memberPicker.pickAndRemove(), UpdateType.DISSOLVED);
		AllianceIntel intel = alliance.getIntel();
		Global.getSector().addScript(intel);	// so its advance() method can run and the intel can expire
		intel.endAfterDelay();
		
//		alliance.getEvent().setDone(true);
        SectorManager.checkForVictory();
    }

    /**
     * Check all factions for eligibility to join/form an alliance
     */
    public void tryMakeAlliance()
    {
        log.info("Trying to make alliance");
        SectorAPI sector = Global.getSector();
        List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
        Collections.shuffle(liveFactionIds);
        
        // first let's look at forming a new alliance
        for (String factionId : liveFactionIds)
        {
            if (alliancesByFactionId.containsKey(factionId)) continue;
            if (NexUtilsFaction.isPirateFaction(factionId)) continue;
            if (INVALID_FACTIONS.contains(factionId)) continue;
			if (Nex_IsFactionRuler.isRuler(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            
            for (String otherFactionId : liveFactionIds)
            {
                if (alliancesByFactionId.containsKey(otherFactionId)) continue;
                if (otherFactionId.equals(factionId)) continue;
                if (NexUtilsFaction.isPirateFaction(otherFactionId)) continue;
                if (INVALID_FACTIONS.contains(otherFactionId)) continue;
				if (Nex_IsFactionRuler.isRuler(otherFactionId)) continue;
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
            if (Nex_IsFactionRuler.isRuler(factionId)) continue;
            FactionAPI faction = sector.getFaction(factionId);
            
            WeightedRandomPicker<Alliance> picker = new WeightedRandomPicker<>();
            
            for (Alliance alliance : alliances)
            {
                float value = getAlignmentCompatibilityWithAlliance(factionId, alliance);
                if (value < MIN_ALIGNMENT_TO_JOIN_ALLIANCE  && !NexConfig.ignoreAlignmentForAlliances) continue;
                
				if (Math.random() > Math.pow(JOIN_CHANCE_MULT_PER_MEMBER, alliance.getMembersCopy().size() ))
				{
					continue;
				}
                
				float relationship = alliance.getAverageRelationshipWithFaction(factionId);
				if (relationship < MIN_RELATIONSHIP_TO_JOIN || Math.random() > relationship * JOIN_CHANCE_MULT)
				{
					continue;
				}
				
				boolean abort = false;
                for (String memberId : alliance.getMembersCopy())
                {
                    if (faction.isHostileTo(memberId))
                    {
                        abort = true;
                        break;
                    }
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
		if (TutorialMissionIntel.isTutorialInProgress()) 
			return;
        
        float days = Global.getSector().getClock().convertToDays(amount);
    
        if (daysElapsed < NexConfig.allianceGracePeriod)
        {
            daysElapsed += days;
            return;
        }
        
        this.tracker.advance(days);
        if (!this.tracker.intervalElapsed()) {
            return;
        }
        float interval = NexConfig.allianceFormationInterval;
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
     * Check if faction should leave an alliance due to disliking its allies
     * @param factionId
     * @param otherFactionId
     */
    public static void remainInAllianceCheck(String factionId, String otherFactionId)
    {
        AllianceManager manager = getManager();
        Alliance alliance1 = manager.alliancesByFactionId.get(factionId);
        Alliance alliance2 = manager.alliancesByFactionId.get(otherFactionId);
        if (alliance1 == null || alliance2 == null || alliance1 != alliance2) return;
        
        boolean perma1 = alliance1.isPermaMember(factionId);
        boolean perma2 = alliance1.isPermaMember(otherFactionId);
    
        FactionAPI faction = Global.getSector().getFaction(factionId);
        if (faction.isHostileTo(otherFactionId))
        {
            // no fighting here, both of you get out of our clubhouse!
            if (!perma1 && !perma2 && alliance1.getMembersCopy().size() <= 3) {
                manager.dissolveAlliance(alliance1);
            }
            else
            {
                if (!perma1) manager.leaveAlliance(factionId, alliance1);
                if (!perma2) manager.leaveAlliance(otherFactionId, alliance1);
            }
        }
        else
        {
            boolean leave1 = false;
            boolean leave2 = false;
            int numLeavers = 0;
            float averageRel1 = alliance1.getAverageRelationshipWithFaction(factionId);
            float averageRel2 = alliance1.getAverageRelationshipWithFaction(otherFactionId);
            
            if (!perma1 && averageRel1 < MIN_RELATIONSHIP_TO_STAY)
            {
                leave1 = true;
                numLeavers++;
            }
            if (!perma2 && averageRel2 < MIN_RELATIONSHIP_TO_STAY)
            {
                leave2 = true;
                numLeavers++;
            }
            // both want to leave
            if (numLeavers == 2)
            {
                if (alliance1.getMembersCopy().size() <= 3) manager.dissolveAlliance(alliance1);
                else
                {
                    manager.leaveAlliance(factionId, alliance1);
                    manager.leaveAlliance(otherFactionId, alliance1);
                }
            }
            else if (leave1) manager.leaveAlliance(factionId, alliance1);
            else if (leave2) manager.leaveAlliance(otherFactionId, alliance1);
        }
    }
        
    public static void doAlliancePeaceStateChange(String faction1Id, String faction2Id, boolean isWar)
    {
        doAlliancePeaceStateChange(faction1Id, faction2Id, getFactionAlliance(faction1Id), getFactionAlliance(faction2Id), isWar, new HashSet<String>());
    }

    public static void doAlliancePeaceStateChange(String faction1Id, String faction2Id, 
            Alliance alliance1, Alliance alliance2, boolean isWar, Set<String> defyingFactions)
    {
        if (alliance1 == null && alliance2 == null)
            return;
        
        SectorAPI sector = Global.getSector();
        FactionAPI faction1 = sector.getFaction(faction1Id);
        FactionAPI faction2 = sector.getFaction(faction2Id);
        
        // declare war on/make peace with the other faction/alliance
        float delta = isWar ? -0.1f : 0.3f;
        RepLevel ensureAtBest = isWar ? RepLevel.HOSTILE : null;
        RepLevel ensureAtWorst = isWar ? null : RepLevel.INHOSPITABLE;
        RepLevel limit = faction1.getRelationshipLevel(faction2);
        boolean anyChanges = false;
        
        if (alliance1 != null)
        {
            for (String memberId : alliance1.getMembersCopy())
            {
                if (defyingFactions.contains(memberId)) continue;
                
                FactionAPI member = sector.getFaction(memberId);
                if (alliance2 != null)
                {
                    for (String otherMemberId : alliance2.getMembersCopy())
                    {
                        if (defyingFactions.contains(otherMemberId)) continue;
                        
                        FactionAPI otherMember = sector.getFaction(otherMemberId);
                        // already in correct state, do nothing
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
            for (String memberId : alliance2.getMembersCopy())
            {
                if (defyingFactions.contains(memberId)) continue;
                
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
        String party1 = Misc.ucFirst(NexUtilsFaction.getFactionShortName(faction1));
        String highlight1 = party1;
        if (alliance1 != null) 
        {
            party1 = alliance1.getAllianceNameAndMembers();
            highlight1 = alliance1.getName();
        }
        //else if (faction1.getId().equals("player")) party1 = Misc.ucFirst(playerAlignedFaction.getEntityNamePrefix());

        String party2 = Misc.ucFirst(NexUtilsFaction.getFactionShortName(faction2));
        String highlight2 = party2;
        if (alliance2 != null) 
        {
            party2 = alliance2.getAllianceNameAndMembers();
            highlight2 = alliance2.getName();
        }
        //else if (faction2.getId().equals("player")) party2 = Misc.ucFirst(playerAlignedFaction.getEntityNamePrefix());

		//TODO use something similar to Hostility Intel
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
	
	public static SectorEntityToken getPlayerInteractionTarget()
	{
		return AllianceManager.playerInteractionTarget;
	}
    
    public static void joinAllianceStatic(String factionId, Alliance alliance)
    {
        getManager().joinAlliance(factionId, alliance);
    }
    
    public static void leaveAlliance(String factionId, boolean noEvent)
    {
        AllianceManager manager = getManager();
        Alliance alliance = manager.alliancesByFactionId.get(factionId);
        if (alliance == null) return;
        manager.leaveAlliance(factionId, alliance, noEvent);
    }
    
    public static List<Alliance> getAllianceList()
    {
        return new ArrayList<>(getManager().alliances);
    }
    
    public static Alliance getAllianceByName(String allianceName)
    {
        return getManager().alliancesByName.get(allianceName);
    }
    
    public static Alliance getAllianceByUUID(String id)
    {
        return getManager().alliancesById.get(id);
    }
    
    /**
     * Gets the alliance this faction belongs to, if any. 
     * @param factionId
     * @return
     */
    public static Alliance getFactionAlliance(String factionId)
    {
        if (factionId == null) return null;
        return getManager().alliancesByFactionId.get(factionId);
    }
    
    /**
     * Gets the alliance the player or their commissioning faction belong to, if any.
     * @param allowInherit If false, do not check commissioning faction's alliance.
     * @return
     */
    public static Alliance getPlayerAlliance(boolean allowInherit) {
        Alliance alliance = getFactionAlliance(Factions.PLAYER);
        if (alliance == null && allowInherit && Misc.getCommissionFactionId() != null)
        {
            alliance = getFactionAlliance(Misc.getCommissionFactionId());
        }
        return alliance;
    }
    
    /**
     * Are these two factions allied?<br/>
     * Note: A faction is considered allied to itself here, even if not a member of an alliance.
     * @param factionId1
     * @param factionId2
     * @return
     */
    public static boolean areFactionsAllied(String factionId1, String factionId2)
    {
        if (factionId1.equals(factionId2)) return true;
        Alliance alliance1 = getFactionAlliance(factionId1);
        if (alliance1 == null) return false;
        Alliance alliance2 = getFactionAlliance(factionId2);
        if (alliance2 == null) return false;
        
        return alliance1 == alliance2;
    }
    
    public static void renameAlliance(Alliance alliance, String newName)
    {
        AllianceManager manager = getManager();
        if (alliance == null || newName == null) throw new IllegalArgumentException("Alliance or new name is null");
        String oldName = alliance.getName();
        if (manager.alliancesByName.containsKey(oldName))
        {
            manager.alliancesByName.remove(oldName);
            manager.alliancesByName.put(newName, alliance);
        }
        alliance.setName(newName);
    }
    
    public static void setMemoryKeys(MemoryAPI memory, Alliance alliance)
    {
        memory.set("$isInAlliance", true, 0);
        memory.set("$allianceId", alliance.uuId, 0);
        memory.set("$allianceName", alliance.getName(), 0);
    }
    
    public static void unsetMemoryKeys(MemoryAPI memory)
    {
        memory.set("$isInAlliance", false, 0);
        memory.unset("$allianceId");
        memory.unset("$allianceName");
    }
    
    public static AllianceManager getManager()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        AllianceManager manager = (AllianceManager)data.get(MANAGER_MAP_KEY);
        return manager;
    }
    
    public static AllianceManager create()
    {
        AllianceManager manager = getManager();
        if (manager != null) {            
            return manager;
        }
        
        Map<String, Object> data = Global.getSector().getPersistentData();
        manager = new AllianceManager();
        data.put(MANAGER_MAP_KEY, manager);
        return manager;
    }
        
    public static class AllianceComparator implements Comparator<Alliance>
    {
        @Override
        public int compare(Alliance alliance1, Alliance alliance2) {

            int size1 = alliance1.getMembersCopy().size();
            int size2 = alliance2.getMembersCopy().size();

            return Integer.compare(size2, size1);
        }
    }
}