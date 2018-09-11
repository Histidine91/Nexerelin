package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DiplomacyManager.DiplomacyEventParams;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinFactionConfig.Morality;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.MutableStatNoFloor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

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
	--Our militarism
	--Dominance modifiers
	--How much stronger than them are we?
	--How many wars are we already in?
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
	
	public static final float RELATIONS_MULT = 30f;
	public static final float ALIGNMENT_MULT = 2f;
	public static final float ALIGNMENT_DIPLOMATIC_MULT = 1.5f;
	public static final float COMMON_ENEMY_MULT = 12.5f;
	public static final float MORALITY_EFFECT = 10f;
	public static final float EVENT_MULT = 80f;
	public static final float EVENT_PEACE_MULT = 40f;
	public static final float EVENT_DECREMENT_PER_DAY = 0.2f;
	public static final float REVANCHISM_SIZE_MULT = 2;
	public static final float DOMINANCE_MULT = 25;
	public static final float DOMINANCE_HARD_MULT = 1.5f;
	public static final float HARD_MODE_MOD = -25f;
	public static final float MAX_DISPOSITION_FOR_WAR = -15f;
	public static final float MILITARISM_WAR_MULT = 1;
	public static final float MAX_WEARINESS_FOR_WAR = 7500f;
	public static final float LIKE_THRESHOLD = 10;
	public static final float DISLIKE_THRESHOLD = -10;
	public static final float EVENT_SKIP_CHANCE = 0.5f;
	public static final float EVENT_CHANCE_EXPONENT_BASE = 0.8f;
	public static final float CEASEFIRE_LENGTH = 90f;
	//public static final float EVENT_AGENT_CHANCE = 0.35f;
	
	public static final Map<String, Float> revanchismCache = new HashMap<>();
	
	public static Logger log = Global.getLogger(DiplomacyBrain.class);
	
	protected String factionId;
	protected transient FactionAPI faction;
	protected Map<String, DispositionEntry> dispositions = new HashMap<>();
	protected Map<String, Float> ceasefires = new HashMap<>();
	protected List<String> enemies = new ArrayList<>();
	protected IntervalUtil intervalShort = new IntervalUtil(0.45f, 0.55f);
	protected IntervalUtil interval = new IntervalUtil(9.5f, 10.5f);
	protected float ourStrength = 0;
	protected float enemyStrength = 0;
	
	//==========================================================================
	//==========================================================================
	
	public DiplomacyBrain(String factionId)
	{
		this.factionId = factionId;
		this.faction = Global.getSector().getFaction(factionId);
	}
	
	//==========================================================================
	//==========================================================================
		
	public DispositionEntry getDisposition(String factionId)
	{
		if (!dispositions.containsKey(factionId))
		{
			dispositions.put(factionId, new DispositionEntry(factionId));
			updateDisposition(factionId, 0);
		}
		return dispositions.get(factionId);
	}
	
	public float getDispositionFromAlignments(String factionId)
	{
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		ExerelinFactionConfig ourConf = ExerelinConfig.getExerelinFactionConfig(this.factionId);
		float disposition = 0;
		
		//log.info("Checking alignments for factions: " + factionId + ", " + this.factionId);
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
			
			//log.info("\tAlignment disposition for " + align.toString() +": " + thisDisp);
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
	
	public float getDispositionFromEnemies(String factionId)
	{
		FactionAPI other = Global.getSector().getFaction(factionId);
		float numCommon = 0;
		for (String enemy : enemies)
		{
			if (other.isHostileTo(enemy))
				numCommon++;
		}
		
		return numCommon * COMMON_ENEMY_MULT;
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
		if (dispFromEvents == 0) disposition.unmodify("events");
		else disposition.modifyFlat("events", dispFromEvents, "Recent events");
		return dispFromEvents;
	}
	
	protected float getDispositionFromEvents(String factionId)
	{
		DispositionEntry disposition = this.getDisposition(factionId);
		if (disposition == null) return 0;
		
		if (disposition.disposition.getFlatStatMod("events") != null)
			return disposition.disposition.getFlatStatMod("events").getValue();
		
		return 0;
	}
	
	/**
	 * Update our dispositions towards the specified faction.
	 * @param factionId
	 * @param days Time since last update (for decaying event effects)
	 */
	public void updateDisposition(String factionId, float days)
	{
		MutableStat disposition = getDisposition(factionId).disposition;
		
		boolean isHardMode = isHardMode(factionId);
		
		float dispBase = ExerelinConfig.getExerelinFactionConfig(this.factionId).getDisposition(factionId);
		if (!DiplomacyManager.isRandomFactionRelationships())
			disposition.modifyFlat("base", dispBase, "Base disposition");
		//else
		//	disposition.unmodify("base");
		
		float dispFromRel = faction.getRelationship(factionId) * RELATIONS_MULT;
		disposition.modifyFlat("relationship", dispFromRel, "Relationship");
		
		float dispFromAlign = getDispositionFromAlignments(factionId);
		disposition.modifyFlat("alignments", dispFromAlign, "Alignments");
		
		float dispFromMoral = getDispositionFromMorality(factionId);
		disposition.modifyFlat("morality", dispFromMoral, "Morality");
		
		float dispFromEnemies = getDispositionFromEnemies(factionId);
		disposition.modifyFlat("commonEnemies", dispFromEnemies, "Common enemies");
		
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
		//else
		//	disposition.unmodify("hardmode");
		
		disposition.getModifiedValue();
	}
	
	public float reportDiplomacyEvent(String factionId, float effect)
	{
		MutableStat disposition = getDisposition(factionId).disposition;
		float dispFromEvents = 0;
		if (disposition.getFlatStatMod("events") != null)
			dispFromEvents = disposition.getFlatStatMod("events").getValue();
		
		dispFromEvents += effect * EVENT_MULT;
		
		disposition.modifyFlat("events", dispFromEvents, "Recent events");
		return dispFromEvents;
	}
	
	public void updateAllDispositions(float days)
	{
		for (String factionId : SectorManager.getLiveFactionIdsCopy())
		{
			updateDisposition(factionId, days);
		}
	}
	
	public float getWarDecisionRating(String enemyId)
	{
		log.info("Considering war declaration by " + this.factionId + " against " + enemyId);
		ExerelinFactionConfig ourConf = ExerelinConfig.getExerelinFactionConfig(this.factionId);
		
		float disposition = getDisposition(enemyId).disposition.getModifiedValue();
		log.info("\tDisposition: " + disposition);
		
		float targetStrength = getFactionStrength(enemyId);
		float targetEnemyStrength = getFactionEnemyStrength(enemyId);
		log.info("\tOur strength: " + ourStrength);
		log.info("\tTheir strength: " + targetStrength);
		log.info("\tTheir enemies' strength: " + targetEnemyStrength);
		log.info("\tExisting enemies' strength " + enemyStrength);
		//float netStrength = ourStrength - enemyStrength - (targetStrength - targetEnemyStrength);
		//if (netStrength < 0) netStrength *= 0.5f;	// make small fry a bit more reckless
		
		// existing enemy strength is weighted less, to discourage dogpiles
		float strRatio = (ourStrength + targetEnemyStrength * 0.5f) / (targetStrength + enemyStrength);
		
		float militarismMult = ourConf.alignments.get(Alignment.MILITARIST) * MILITARISM_WAR_MULT + 1;
		log.info("\tMilitarism mult: " + militarismMult);
		
		float dominance = DiplomacyManager.getDominanceFactor(enemyId) * 40;
		log.info("\tTarget dominance: " + dominance);
		
		float score = (-disposition + dominance);
		if (score > 0) 
		{
			score *= militarismMult * strRatio;
		}
		score += dominance;
		log.info("\tTotal score: " + score);
		return score;
	}
	
	protected boolean tryMakePeace(String enemyId, float ourWeariness)
	{
		FactionAPI enemy = Global.getSector().getFaction(enemyId);
		float enemyWeariness = DiplomacyManager.getWarWeariness(enemyId, true);
		log.info("\t" + enemyId + " weariness: " + enemyWeariness + "/" + ExerelinConfig.minWarWearinessForPeace);
		if (enemyWeariness < ExerelinConfig.minWarWearinessForPeace)
			return false;
		
		// add war weariness of both factions, plus effects from recent events
		float sumWeariness = ourWeariness + enemyWeariness;
		log.info("\tWeariness sum: " + sumWeariness);
		
		float eventsMod = getDispositionFromEvents(enemyId) +
				DiplomacyManager.getManager().getDiplomacyBrain(enemyId).getDispositionFromEvents(factionId);
		eventsMod *= EVENT_PEACE_MULT;
		log.info("\tEvents modifier: " + eventsMod);
		
		sumWeariness += eventsMod;
		
		// roll chance for peace
		float divisor = ExerelinConfig.warWearinessDivisor + ExerelinConfig.warWearinessDivisorModPerLevel 
				* Global.getSector().getPlayerPerson().getStats().getLevel();
		if (Math.random() > sumWeariness / divisor)
			return false;
		
		log.info("\tNegotiating treaty");
		boolean peaceTreaty = false;    // if false, only ceasefire
		// can't peace treaty if vengeful, only ceasefire
		if (faction.isAtWorst(enemy, RepLevel.HOSTILE))
		{
			peaceTreaty = Math.random() < DiplomacyManager.PEACE_TREATY_CHANCE;
		}
		String eventId = peaceTreaty ? "peace_treaty" : "ceasefire";
		float reduction = peaceTreaty ? ExerelinConfig.warWearinessPeaceTreatyReduction : ExerelinConfig.warWearinessCeasefireReduction;
		
		DiplomacyManager.createDiplomacyEvent(faction, enemy, eventId, null);
		DiplomacyManager.getManager().reduceWarWeariness(factionId, reduction);
		DiplomacyManager.getManager().reduceWarWeariness(enemyId, reduction);
		return true;
	}
	
	public boolean checkPeace()
	{
		if (enemies.isEmpty()) return false;
		if (ExerelinUtilsFaction.isPirateFaction(factionId) && !ExerelinConfig.allowPirateInvasions)
			return false;
		
		long lastWar = DiplomacyManager.getManager().getLastWarTimestamp();
		if (Global.getSector().getClock().getElapsedDaysSince(lastWar) < DiplomacyManager.MIN_INTERVAL_BETWEEN_WARS)
			return false;
		
		float ourWeariness = DiplomacyManager.getWarWeariness(factionId, true);
		log.info("Checking peace for faction " + faction.getDisplayName() + ": weariness " + ourWeariness);
		if (ourWeariness < ExerelinConfig.minWarWearinessForPeace)
			return false;
		
		List<String> enemiesLocal = new ArrayList<>(this.enemies);		
		Collections.sort(enemiesLocal, new Comparator<String>() {
			@Override
			public int compare(String factionId1, String factionId2)
			{
				float weariness1 = DiplomacyManager.getWarWeariness(factionId1);
				float weariness2 = DiplomacyManager.getWarWeariness(factionId2);
				
				return Float.compare(weariness1, weariness2);
			}
		});
		
		/*
		List<CampaignEventPlugin> events = Global.getSector().getEventManager().getOngoingEvents();
		for (CampaignEventPlugin event : events)
		{
			
		}
		*/
		
		int tries = 3;
		for (String enemyId : enemiesLocal)
		{
			// TODO: check if we have invasion fleet en route first?
			boolean success = tryMakePeace(enemyId, ourWeariness);
			if (success) return true;
			tries--;
			if (tries <= 0) break;
		}
		
		return false;
	}
	
	public boolean checkWar()
	{
		long lastWar = DiplomacyManager.getManager().getLastWarTimestamp();
		if (Global.getSector().getClock().getElapsedDaysSince(lastWar) < DiplomacyManager.MIN_INTERVAL_BETWEEN_WARS)
			return false;
		
		log.info("Checking war for faction " + faction.getDisplayName());
		if (ExerelinUtilsFaction.isPirateOrTemplarFaction(factionId) && !ExerelinConfig.allowPirateInvasions)
			return false;
		
		float ourWeariness = DiplomacyManager.getWarWeariness(factionId, true);
		if (ourWeariness > MAX_WEARINESS_FOR_WAR)
			return false;
		
		// check factions in order of how much we hate them
		List<DispositionEntry> dispositionsList = getDispositionsList();
		Collections.sort(dispositionsList, new Comparator<DispositionEntry>() {
			@Override
			public int compare(DispositionEntry data1, DispositionEntry data2)
			{
				return -Float.compare(data1.disposition.getModifiedValue(), data2.disposition.getModifiedValue());
			}
		});
		
		for (DispositionEntry disposition : dispositionsList)
		{
			String otherFactionId = disposition.factionId;
			if (otherFactionId.equals(this.factionId)) continue;
			log.info("Checking vs. " + otherFactionId + ": " + disposition.disposition.getModifiedValue()
					+ ", " + faction.isAtWorst(otherFactionId, RepLevel.NEUTRAL));
			
			if (!SectorManager.isFactionAlive(otherFactionId)) continue;
			if (DiplomacyManager.disallowedFactions.contains(otherFactionId)) continue;
			if (ceasefires.containsKey(otherFactionId)) continue;
			if (faction.isAtWorst(otherFactionId, RepLevel.NEUTRAL)) continue;	// relations aren't bad enough yet
			if (faction.isHostileTo(otherFactionId)) continue;	// already at war
			if (disposition.disposition.getModifiedValue() > MAX_DISPOSITION_FOR_WAR) continue;
			
			float decisionRating = getWarDecisionRating(otherFactionId);
			if (decisionRating > 40 + MathUtils.getRandomNumberInRange(-5, 5))
			{
				DiplomacyManager.createDiplomacyEvent(faction, Global.getSector().getFaction(otherFactionId), "declare_war", null);
				DiplomacyManager.getManager().setLastWarTimestamp(Global.getSector().getClock().getTimestamp());
				return true;
			}
		}
		
		return false;
	}
	
	public void doRandomEvent()
	{
		Random random = new Random();
		List<String> factions = SectorManager.getLiveFactionIdsCopy();
		
		float chance = (float)Math.pow(EVENT_CHANCE_EXPONENT_BASE, factions.size());
		if (random.nextFloat() > chance)
			return;
		
		Collections.shuffle(factions);
		
		int loopCount = 0;
		for (String otherFactionId : factions)
		{
			if (otherFactionId.equals(factionId)) continue;
			if (DiplomacyManager.disallowedFactions.contains(otherFactionId))
				continue;
			if (otherFactionId.equals(ExerelinConstants.PLAYER_NPC_ID) && !ExerelinConfig.followersDiplomacy)
				continue;
			if (random.nextFloat() < EVENT_SKIP_CHANCE)
				continue;
			loopCount++;
			if (loopCount > 2) break;
			
			DiplomacyEventParams params = new DiplomacyEventParams();
			params.random = false;
			float disp = getDisposition(otherFactionId).disposition.getModifiedValue();
			
			if (ourStrength*1.5f < enemyStrength)
			{
				params.onlyPositive = true;
			}
			else if (disp < DISLIKE_THRESHOLD)
			{
				params.onlyNegative = true;
			}
			else if (disp > LIKE_THRESHOLD)
			{
				params.onlyPositive = true;
			}
			
			//log.info("Executing random diplomacy event");
			DiplomacyManager.createDiplomacyEvent(faction, Global.getSector().getFaction(otherFactionId), null, params);
			return;
		}
	}
	
	public void considerOptions()
	{
		if (DiplomacyManager.disallowedFactions.contains(factionId)) return;
		if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID))
			return;
		
		boolean didSomething = false;
		
		// first see if we should make peace
		didSomething = checkPeace();
		if (didSomething) return;
		
		// let's see if we should declare war on anyone
		didSomething = checkWar();
		if (didSomething) return;
		
		// do a random event
		doRandomEvent();
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
	
	public List<DispositionEntry> getDispositionsList()
	{
		List<DispositionEntry> result = new ArrayList<>();
		Iterator<String> entries = dispositions.keySet().iterator();
		while (entries.hasNext())
		{
			String key = entries.next();
			result.add(dispositions.get(key));
		}
		return result;
	}
	
	public void updateEnemiesAndCeasefires(float days)
	{
		List<String> ceasefiresToRemove = new ArrayList<>();
		Iterator<String> ceasefiresIter = ceasefires.keySet().iterator();
		while (ceasefiresIter.hasNext())
		{
			String otherFactionId = ceasefiresIter.next();
			float timeRemaining = ceasefires.get(otherFactionId);
			timeRemaining -= days;
			if (timeRemaining <= 0)
				ceasefiresToRemove.add(otherFactionId);
			else
				ceasefires.put(otherFactionId, timeRemaining);
		}
		for (String otherFactionId : ceasefiresToRemove)
		{
			ceasefires.remove(otherFactionId);
		}
		
		List<String> latestEnemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, false, true, true);
		for (String enemyId : enemies)
		{
			if (!faction.isHostileTo(enemyId))	// no longer enemy, mark as ceasefired
			{
				log.info("Faction " + factionId + " no longer hostile to " + enemyId);
				ceasefires.put(enemyId, CEASEFIRE_LENGTH);
			}
		}
		enemies = latestEnemies;
	}
	
	public void update(float days)
	{
		cacheRevanchism();
		ourStrength = getFactionStrength(factionId);
		enemyStrength = getFactionEnemyStrength(factionId);
		updateAllDispositions(days);
		considerOptions();
	}
	
	//==========================================================================
	//==========================================================================
	
	public void advance(float days) 
	{
		intervalShort.advance(days);
		if (intervalShort.intervalElapsed())
		{
			updateEnemiesAndCeasefires(intervalShort.getElapsed());
		}
		
		interval.advance(days);
		if (interval.intervalElapsed())
		{
			update(interval.getElapsed());
		}
	}
	
	// don't need to save faction as well as factionId, just recreate the former on load	
	protected Object readResolve() {
		if (intervalShort == null)
			intervalShort = new IntervalUtil(0.45f, 0.55f);
		if (ceasefires == null)
			ceasefires = new HashMap<>();
		if (enemies == null)
			enemies = new ArrayList<>();
		faction = Global.getSector().getFaction(factionId);
		return this;
	}
	
	//==========================================================================
	//==========================================================================

	/**
	 * Gets the sum of the faction's market sizes, plus half that sum for the faction's allies.
	 * @param factionId
	 * @return
	 */
	public static float getFactionStrength(String factionId)
	{
		float str = 0;
		Collection<String> allies;
		Alliance alliance = AllianceManager.getFactionAlliance(factionId);
		if (alliance != null)
			allies = alliance.getMembersCopy();
		else allies = new ArrayList<>(0);
		
		List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
		for (MarketAPI market : allMarkets)
		{
			String marketFactionId = market.getFaction().getId();
			if (factionId.equals(marketFactionId))
				str += market.getSize();
			else if (allies.contains(marketFactionId))
				str += market.getSize()/2;
		}
		return str;
	}
	
	public static float getFactionEnemyStrength(String factionId)
	{
		Set<String> enemies = new HashSet<>(DiplomacyManager.getFactionsAtWarWithFaction(
				factionId, ExerelinConfig.allowPirateInvasions, false, true));
		
		float str = 0;
		
		List<MarketAPI> allMarkets = Global.getSector().getEconomy().getMarketsCopy();
		for (MarketAPI market : allMarkets)
		{
			String marketFactionId = market.getFaction().getId();
			if (enemies.contains(marketFactionId))
				str += market.getSize();
		}
		return str;
	}
	
	public static class DispositionEntry
	{
		public String factionId;
		public MutableStatNoFloor disposition = new MutableStatNoFloor(0);
		
		public DispositionEntry(String factionId)
		{
			this.factionId = factionId;
		}
	}
}
