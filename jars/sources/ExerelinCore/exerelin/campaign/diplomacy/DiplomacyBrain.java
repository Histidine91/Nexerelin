package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinFactionConfig.Morality;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.MutableStatNoFloor;
import java.util.HashMap;
import java.util.Map;

/*
Which category a faction falls in is based on their disposition towards us. Disposition is based on:
	--Current relationship
	--Alignments
	--Morality compatibility
	--Recent diplomatic events
	--Revanchism
	--Dominance modifiers
	--Faction-specific modifiers

High disposition: try to build closer ties
Low disposition: use agents against them, condemn them easily, etc.

If our disposition and current relationship is bad enough, declare war
Chance of this is based on:
	Our militarism
	Dominance modifiers
	How much stronger than them are we?
	How many wars are we already in?
	Revanchism opportunities?

Sudden war: occurs from detected sabotage; player attacking a market; shots fired event; etc.

War
	Factions we are at war with: must decide whether to continue war or try to make peace
	Base chance based on our war weariness, modified by disposition
	If we have an invasion fleet en route to one of their markets, hold off on peace
	If we are significantly weaker, prefer peace; if stronger, prefer war

Bla
	Every N days, update disposition for all other live factions
	If positive disposition, roll chance to do positive things
	If negative disposition, roll chance to do negative things
	If should declare war, roll chance to do so
	Now roll for peace with any faction we are at war with
	If peace desire is sufficiently high, check other faction’s peace desire with us too
	If both pass, sign ceasefire/peace treaty
	Pick based on disposition?
*/

public class DiplomacyBrain {
	
	public static final float RELATIONS_MULT = 50f;
	public static final float ALIGNMENT_MULT = 5f;
	public static final float ALIGNMENT_DIPLOMATIC_MULT = 1.5f;
	public static final float MORALITY_EFFECT = 25f;
	public static final float EVENT_DECREMENT_PER_DAY = 1;
	public static final float REVANCHISM_SIZE_MULT = 2;
	public static final float DOMINANCE_MULT = 25;
	public static final float DOMINANCE_HARD_MULT = 1.5f;
	public static final float HARD_MODE_MOD = -25f;
	
	public static final Map<String, Float> revanchismCache = new HashMap<>();
	
	String factionId;
	Map<String, MutableStatNoFloor> dispositions = new HashMap<>();
	IntervalUtil interval = new IntervalUtil(9.8f, 10.2f);
	
	public DiplomacyBrain(String factionId)
	{
		this.factionId = factionId;
	}
		
	public MutableStatNoFloor getDisposition(String factionId)
	{
		if (!dispositions.containsKey(factionId))
		{
			dispositions.put(factionId, new MutableStatNoFloor(0));
			cacheRevanchism();
			updateDisposition(factionId, 0);
		}
		return dispositions.get(factionId);
	}
	
	public float getDispositionFromAlignments(String factionId)
	{
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		ExerelinFactionConfig ourConf = ExerelinConfig.getExerelinFactionConfig(this.factionId);
		float disposition = 0;
		
		//Global.getLogger(this.getClass()).info("Checking alignments for factions: " + factionId + ", " + this.factionId);
		for (Alignment align : Alliance.Alignment.values())
		{
			float ours = ourConf.alignments.get(align);
			float theirs = conf.alignments.get(align);
			float thisDisp = 0;
			
			if (ours == 0 || theirs == 0)
				continue;
			
			// both positive, sum
			if (ours > 0 && theirs > 0)
				thisDisp = ours + theirs;
			// both negative, sum and invert
			else if (ours < 0 && theirs < 0)
				thisDisp = (ours + theirs) * -1;
			// opposite signs, get difference
			else
				thisDisp = ours - theirs;
			
			//Global.getLogger(this.getClass()).info("\tAlignment disposition for " + align.toString() +": " + thisDisp);
			disposition += thisDisp;
		}
		
		// diplomatic factions tend to have high dispositions in general
		float ourDiplo = ourConf.alignments.get(Alignment.DIPLOMATIC);
		float theirDiplo = conf.alignments.get(Alignment.DIPLOMATIC);
		
		disposition += (ourDiplo + theirDiplo) * ALIGNMENT_DIPLOMATIC_MULT;
		
		return disposition * ALIGNMENT_MULT;
	}
	
	public float getDispositionFromMorality(String factionId)
	{
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		ExerelinFactionConfig ourConf = ExerelinConfig.getExerelinFactionConfig(this.factionId);
		
		Morality us = ourConf.morality;
		Morality them = conf.morality;
		
		float effect = 0;
		
		if (us == Morality.GOOD) {
			// like other good people
			if (them == us)
				effect = MORALITY_EFFECT;
			// hate evil people
			else if (them == Morality.EVIL)
				effect = -MORALITY_EFFECT;
			// dislike amoral people
			else if (them == Morality.AMORAL)
				effect = -MORALITY_EFFECT * 0.5f;
		}
		else if (us == Morality.EVIL) {
			// like amoral people somewhat
			if (them == Morality.AMORAL)
				effect = MORALITY_EFFECT * 0.5f;
			// hate goody goody two shoes
			else if (them == Morality.GOOD)
				effect = -MORALITY_EFFECT;
		}
		else if (us == Morality.NEUTRAL) {
			// dislike evil people
			if (them == Morality.EVIL)
				effect = -MORALITY_EFFECT * 0.5f;
		}
		return effect;
	}
	
	public float updateDispositionFromEvents(MutableStat disposition, String factionId, float days)
	{
		float dispFromEvents = 0;
		if (disposition.getFlatStatMod("events") != null)
			dispFromEvents = disposition.getFlatStatMod("events").getValue();
		
		if (dispFromEvents > 0)
		{
			dispFromEvents -= EVENT_DECREMENT_PER_DAY * days;
			if (dispFromEvents < 0) dispFromEvents = 0;
		}
		else if (dispFromEvents < 0)
		{
			dispFromEvents += EVENT_DECREMENT_PER_DAY * days;
			if (dispFromEvents > 0) dispFromEvents = 0;
		}
		disposition.modifyFlat("events", dispFromEvents, "Recent events");
		return dispFromEvents;
	}
	
	
	public void updateDisposition(String factionId, float days)
	{
		FactionAPI otherFaction = Global.getSector().getFaction(factionId);
		MutableStat disposition = getDisposition(factionId);
		
		boolean isHardMode = isHardMode(factionId);
		
		float dispBase = ExerelinConfig.getExerelinFactionConfig(this.factionId).getDisposition(factionId);
		if (!DiplomacyManager.isRandomFactionRelationships())
			disposition.modifyFlat("base", dispBase, "Base disposition");
		else
			disposition.unmodify("base");
		
		float dispFromRel = otherFaction.getRelationship(this.factionId) * RELATIONS_MULT;
		disposition.modifyFlat("relationship", dispFromRel, "Relationship");
		
		float dispFromAlign = getDispositionFromAlignments(factionId);
		disposition.modifyFlat("alignments", dispFromAlign, "Alignments");
		
		float dispFromMoral = getDispositionFromMorality(factionId);
		disposition.modifyFlat("morality", dispFromMoral, "Morality");
		
		updateDispositionFromEvents(disposition, factionId, days);	
		
		float dispFromRevan = 0;
		if (revanchismCache.containsKey(factionId))
			dispFromRevan = -revanchismCache.get(factionId);
		disposition.modifyFlat("revanchism", dispFromRevan, "Revanchism");
		
		float dispFromDominance = -DiplomacyManager.getDominanceFactor(factionId) * DOMINANCE_MULT;
		if (isHardMode) dispFromDominance *= DOMINANCE_HARD_MULT;
		disposition.modifyFlat("dominance", dispFromDominance, "Dominance");
		
		if (isHardMode)
			disposition.modifyFlat("hardmode", HARD_MODE_MOD, "Hard mode");
		else
			disposition.unmodify("hardmode");
		
		disposition.getModifiedValue();
	}
	
	// TODO
	public float reportDiplomacyEvent()
	{
		return 0;
	}
	
	public boolean isHardMode(String factionId)
	{
		if (!SectorManager.getHardMode())
			return false;
		String myFactionId = PlayerFactionStore.getPlayerFactionId();
		
		return factionId.equals(ExerelinConstants.PLAYER_NPC_ID) 
				|| factionId.equals(myFactionId)
				|| this.factionId.equals(ExerelinConstants.PLAYER_NPC_ID) 
				|| this.factionId.equals(myFactionId);
	}
	
	public void cacheRevanchism()
	{
		revanchismCache.clear();
		float revanchism = 0;
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			if (market.getFactionId().equals(this.factionId))
				continue;
			if (ExerelinUtilsMarket.wasOriginalOwner(market, this.factionId))
			{
				revanchism += market.getSize() * REVANCHISM_SIZE_MULT;
			}
		}
		
		revanchismCache.put(factionId, revanchism);
	}
	
	public void updateAllDispositions(float days)
	{
		for (String factionId : SectorManager.getLiveFactionIdsCopy())
		{
			updateDisposition(factionId, days);
		}
	}
	
	public void advance(float days) 
	{
		interval.advance(days);
		if (interval.intervalElapsed())
		{
			cacheRevanchism();
			updateAllDispositions(days);
		}
	}
}
