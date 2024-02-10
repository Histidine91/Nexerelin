package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel.AntiInspectionOrders;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DiplomacyManager.DiplomacyEventParams;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.intel.diplomacy.CeasefirePromptIntel;
import exerelin.campaign.intel.diplomacy.DiplomacyIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.Morality;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;
import lombok.Getter;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

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
	
	public static final float RELATIONS_MULT = 25f;
	public static final float ALIGNMENT_MULT = 2f;
	public static final float ALIGNMENT_DIPLOMATIC_MULT = 1.5f;
	public static final float COMMON_ENEMY_MULT = 12.5f;
	public static final float MORALITY_EFFECT = 10f;
	public static final float EVENT_MULT = 80f;
	public static final float EVENT_PEACE_MULT = 40f;
	public static final float EVENT_DECREMENT_PER_DAY = 0.2f;
	public static final float REVANCHISM_SIZE_MULT = 2;
	public static final float REVANCHISM_FACTION_MAX = 40;
	public static final float REVANCHISM_MAX = 50;
	public static final float DOMINANCE_MULT = 25;
	public static final float MAX_DISPOSITION_FOR_WAR = -20;
	public static final float DECISION_RATING_FOR_WAR = 40;
	public static final float MILITARISM_WAR_MULT = 1;
	public static final float MAX_WEARINESS_FOR_WAR = 7500f;
	public static final float LIKE_THRESHOLD = 15;
	public static final float DISLIKE_THRESHOLD = -20;
	public static final float EVENT_SKIP_CHANCE = 0.5f;
	public static final float EVENT_CHANCE_EXPONENT_BASE = 0.8f;
	public static final float CEASEFIRE_LENGTH = 150f;
	public static final float RECENT_WAR_LENGTH = 120f;
	
	// used to be in DiplomacyTraits but that made compiling annoying
	public static final float FREE_PORT_PENALTY_MULT = 0.4f;
	public static final float FREE_PORT_BONUS_MULT = 0.2f;
	public static final float ENEMY_OF_ALLY_PENALTY_MULT = 7.5f;
	public static final float COMPETITION_PENALTY_MULT = 0.12f;
	public static final float AI_PENALTY_MULT = 0.5f;
	public static final float MONSTROUS_PENALTY = -10f;
	//public static final float EVENT_AGENT_CHANCE = 0.35f;
	
	public static Logger log = Global.getLogger(DiplomacyBrain.class);
	
	protected String factionId;
	protected transient FactionAPI faction;
	protected Map<String, DispositionEntry> dispositions = new HashMap<>();
	@Getter protected Map<String, Float> ceasefires = new HashMap<>();
	@Getter protected Map<String, Float> recentWars = new HashMap<>();
	protected List<String> enemies = new ArrayList<>();
	protected IntervalUtil intervalShort = new IntervalUtil(0.45f, 0.55f);
	protected IntervalUtil interval;
	@Getter	protected float ourStrength = 0;
	@Getter protected float enemyStrength = 0;
	protected float playerCeasefireOfferCooldown = 0;
	protected Map<String, Float> revanchismCache = new HashMap<>();
	
	//==========================================================================
	//==========================================================================
	
	public DiplomacyBrain(String factionId)
	{
		this.factionId = factionId;
		this.faction = Global.getSector().getFaction(factionId);
		float time = DiplomacyManager.getBaseInterval();
		interval = new IntervalUtil(time * 0.95f, time * 1.05f);
	}
	
	//==========================================================================
	//==========================================================================

	/**
	 * Gets the brain faction's disposition towards the specified faction.
	 * @param factionId
	 * @return
	 */	
	public DispositionEntry getDisposition(String factionId)
	{
		if (!dispositions.containsKey(factionId))
		{
			dispositions.put(factionId, new DispositionEntry(factionId));
			updateDisposition(factionId, 0);
		}
		return dispositions.get(factionId);
	}
	
	public float getDispositionFromAlignments(String factionId) {
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		NexFactionConfig ourConf = NexConfig.getFactionConfig(this.factionId);
		Map<Alignment, Float> alignments = conf.getAlignmentValues();
		Map<Alignment, Float> ourAlignments = ourConf.getAlignmentValues();
		
		return getDispositionFromAlignments(alignments, ourAlignments);
	}
	
	public float getDispositionFromAlignments(Map<Alignment, Float> alignments, Map<Alignment, Float> ourAlignments)
	{		
		float disposition = 0;
		
		//log.info("Checking alignments for factions: " + factionId + ", " + this.factionId);
		for (Alignment align : Alliance.Alignment.getAlignments())
		{
			
			float ours = ourAlignments.get(align);
			float theirs = alignments.get(align);
			float thisDisp = 0;
			
			if (ours == 0 || theirs == 0)
				continue;
			
			if (ours > 0) {
				if (theirs > 0) // both positive, sum as bonus
					thisDisp = ours + theirs;
				else			// opposite signs, apply difference as penalty
					thisDisp = -(ours + Math.abs(theirs));
			}
			else {
				if (theirs < 0)	// both negative, sum as bonus
					thisDisp = (ours + theirs) * -1;
				else			// opposite signs, apply difference as penalty
					thisDisp = -(Math.abs(ours) + theirs);
			}
			
			//log.info("\tAlignment disposition for " + align.toString() +": " + thisDisp);
			disposition += thisDisp;
		}
		
		// diplomatic factions tend to have high dispositions in general
		float ourDiplo = ourAlignments.get(Alignment.DIPLOMATIC);
		float theirDiplo = alignments.get(Alignment.DIPLOMATIC);
		
		disposition += (ourDiplo + theirDiplo) * ALIGNMENT_DIPLOMATIC_MULT;
		
		return disposition * ALIGNMENT_MULT;
	}
	
	public float getDispositionFromMorality(String factionId)
	{
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		NexFactionConfig ourConf = NexConfig.getFactionConfig(this.factionId);
		
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
	
	public void modifyDispositionFromTraits(MutableStat disposition, float delta) {
		float currTraitScore = 0;
		if (disposition.getFlatMods().containsKey("traits"))
			currTraitScore = disposition.getFlatStatMod("traits").getValue();
		//log.info(factionId + " modifying trait score by " + delta);
		disposition.modifyFlat("traits", currTraitScore + delta, "Traits");
	}
	
	protected void updateDispositionFromTraits(MutableStat disposition, String otherFactionId) 
	{
		Set<String> traits = new HashSet<>(DiplomacyTraits.getFactionTraits(this.factionId));
		
		if (traits.contains(TraitIds.IRREDENTIST) && disposition.getFlatMods().containsKey("revanchism")
				&& disposition.getFlatMods().containsKey("revanchism"))
		{
			float revanchism = disposition.getFlatStatMod("revanchism").value;
			disposition.modifyFlat("revanchism", revanchism * 1.5f, "Revanchism");
		}
		if (traits.contains(TraitIds.SELFRIGHTEOUS) && disposition.getFlatMods().containsKey("alignments")) 
		{
			float align = disposition.getFlatStatMod("alignments").value;
			disposition.modifyFlat("alignments", align * 2, "Alignments");
		}
		if (traits.contains(TraitIds.TEMPERAMENTAL)) {
			disposition.modifyMult("trait_temperamental", 1.25f, "Trait: Temperamental");
		}
		
		boolean dislikesAI = traits.contains(TraitIds.DISLIKES_AI);
		boolean hatesAI = traits.contains(TraitIds.HATES_AI);
		boolean likesAI = traits.contains(TraitIds.LIKES_AI);
		
		if (dislikesAI || hatesAI || likesAI) {
			float aiScore = 0;
			Map<MarketAPI, Float> aiHavers = EconomyInfoHelper.getInstance().getAICoreUsers();
			for (MarketAPI market : aiHavers.keySet()) {
				if (market.getFactionId().equals(otherFactionId))
					aiScore += aiHavers.get(market);
			}
			aiScore *= AI_PENALTY_MULT;
			if (dislikesAI)
				aiScore *= 0.5;
			else if (likesAI)
				aiScore *= -0.5;
			
			modifyDispositionFromTraits(disposition, -aiScore);
		}
		
		if (disposition.getFlatMods().containsKey("dominance"))
		{
			if (traits.contains(TraitIds.ENVIOUS)) {
				float dominance = disposition.getFlatStatMod("dominance").value;
				disposition.modifyFlat("dominance", dominance * 1.5f, "Dominance");
			}
			if (traits.contains(TraitIds.SUBMISSIVE)) {
				float dominance = disposition.getFlatStatMod("dominance").value;
				disposition.modifyFlat("dominance", -dominance, "Dominance");
			}
			if (traits.contains(TraitIds.NEUTRALIST)) {
				disposition.unmodifyFlat("dominance");
			}
		}
		
		if (traits.contains(TraitIds.MONOPOLIST)) {
			float monopolyScore = EconomyInfoHelper.getInstance().getCompetitionFactor(this.factionId, otherFactionId);
			monopolyScore *= COMPETITION_PENALTY_MULT;
			modifyDispositionFromTraits(disposition, -monopolyScore);
		}
		
		if (traits.contains(TraitIds.HELPS_ALLIES)) 
		{
			float enemyScore = 0;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(otherFactionId, true, true, false);
			for (String thirdFactionId : enemies) 
			{
				if (this.factionId.equals(thirdFactionId)) continue;
				
				FactionAPI thirdFaction = Global.getSector().getFaction(thirdFactionId);
				if (AllianceManager.areFactionsAllied(this.factionId, thirdFactionId))
					enemyScore += 1;
				else if (thirdFaction.isAtWorst(this.factionId, RepLevel.FRIENDLY))
					enemyScore += 0.5f;
			}
			enemyScore *= ENEMY_OF_ALLY_PENALTY_MULT;
			modifyDispositionFromTraits(disposition, -enemyScore);
		}
		
		boolean lawAndOrder = traits.contains(TraitIds.LAW_AND_ORDER);
		boolean anarchist = traits.contains(TraitIds.ANARCHIST);
		
		if (lawAndOrder || anarchist) {
			float freeportScore = 0;
			List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(otherFactionId);
			for (MarketAPI market : markets) {
				if (market.isFreePort()) freeportScore += market.getSize();
			}
			
			if (lawAndOrder) {
				freeportScore *= -FREE_PORT_PENALTY_MULT;
				//log.info(String.format("Freeport score for %s against %s: %s (multiplier %s)", 
				//		this.factionId, factionId, freeportScore, -FREE_PORT_PENALTY_MULT));
			}
			else if (anarchist) {
				freeportScore *= FREE_PORT_BONUS_MULT;
			}
			modifyDispositionFromTraits(disposition, freeportScore);
		}
		
		if (DiplomacyTraits.hasTrait(otherFactionId, TraitIds.MONSTROUS)) {
			modifyDispositionFromTraits(disposition, MONSTROUS_PENALTY);
		}
	}
		
	public void updateDispositionFromAlignment(MutableStat disposition, String otherFactionId) 
	{
		float dispFromAlign = getDispositionFromAlignments(otherFactionId);
		disposition.modifyFlat("alignments", dispFromAlign, "Alignments");
	}
	
	public void updateDisposition(String otherFactionId, float days) {
		updateDisposition(otherFactionId, null, days);
	}
	
	/**
	 * Update our dispositions towards the specified faction.
	 * @param otherFactionId
	 * @param disposition Specify a {@code MutableStat} here to update those stats instead of the one the brain actually uses (use to preview disposition changes).
	 * @param days Time since last update (for decaying event effects)
	 */
	public void updateDisposition(String otherFactionId, MutableStat disposition, float days)
	{
		if (disposition == null) {
			disposition = getDisposition(otherFactionId).disposition;
		}
		
		// clear disposition except for recent events
		Float recent = disposition.getFlatMods().containsKey("events") ? 
				disposition.getFlatStatMod("events").getValue() : null;
		disposition.unmodify();
		if (recent != null)
			disposition.modifyFlat("events", recent);
		
		boolean isHardMode = isHardMode(otherFactionId);
		
		float dispBase = NexConfig.getFactionConfig(this.factionId).getDisposition(otherFactionId);
		if (!DiplomacyManager.haveRandomRelationships(this.factionId, otherFactionId))
			disposition.modifyFlat("base", dispBase, "Base disposition");
		//else
		//	disposition.unmodify("base");
		
		float dispFromRel = faction.getRelationship(otherFactionId) * RELATIONS_MULT;
		disposition.modifyFlat("relationship", dispFromRel, "Relationship");
		
		updateDispositionFromAlignment(disposition, otherFactionId);
		
		//float dispFromMoral = getDispositionFromMorality(factionId);
		//disposition.modifyFlat("morality", dispFromMoral, "Morality");
		disposition.unmodify("morality");
		
		float dispFromEnemies = getDispositionFromEnemies(otherFactionId);
		disposition.modifyFlat("commonEnemies", dispFromEnemies, "Common enemies");
		
		updateDispositionFromEvents(disposition, otherFactionId, days);	
		
		float dispFromRevan = 0;
		if (revanchismCache.containsKey(otherFactionId))
			dispFromRevan = -revanchismCache.get(otherFactionId);
		disposition.modifyFlat("revanchism", dispFromRevan, "Revanchism");
		
		float dispFromDominance = -DiplomacyManager.getDominanceFactor(otherFactionId) * DOMINANCE_MULT;
		disposition.modifyFlat("dominance", dispFromDominance, "Dominance");
		
		if (isHardMode)
			disposition.modifyFlat("hardmode", DiplomacyManager.getHardModeDispositionMod(), "Hard mode");
		//else
		//	disposition.unmodify("hardmode");
		
		updateDispositionFromTraits(disposition, otherFactionId);
		
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
		reportDispositionsUpdated(factionId, this);
	}
	
	public float getWarDecisionRating(String enemyId)
	{
		logDebug("Considering war declaration by " + this.factionId + " against " + enemyId);
		NexFactionConfig ourConf = NexConfig.getFactionConfig(this.factionId);
		
		float disposition = getDisposition(enemyId).disposition.getModifiedValue();
		logDebug("\tDisposition: " + disposition);
		
		float targetStrength = getFactionStrength(enemyId);
		float targetEnemyStrength = getFactionEnemyStrength(enemyId);
		logDebug("\tOur strength: " + ourStrength);
		logDebug("\tTheir strength: " + targetStrength);
		logDebug("\tTheir enemies' strength: " + targetEnemyStrength);
		logDebug("\tExisting enemies' strength: " + enemyStrength);
		//float netStrength = ourStrength - enemyStrength - (targetStrength - targetEnemyStrength);
		//if (netStrength < 0) netStrength *= 0.5f;	// make small fry a bit more reckless
		
		// existing enemy strength is weighted less, to discourage dogpiles
		float strRatio = (ourStrength + targetEnemyStrength * 0.5f) / (targetStrength + enemyStrength);
		logDebug("\tStrength ratio: " + strRatio);
		
		float militarismMult = ourConf.getAlignmentValues().get(Alignment.MILITARIST) * MILITARISM_WAR_MULT + 1;
		logDebug("\tMilitarism mult: " + militarismMult);
		
		float dominance = DiplomacyManager.getDominanceFactor(enemyId) * 40;
		logDebug("\tTarget dominance: " + dominance);
		
		float score = (-disposition + dominance);
		logDebug("\tDisposition + dominance score: " + score);
		if (score > 0) 
		{
			float mult = militarismMult * strRatio;
			logDebug("\t\tMilitarism/strength multiplier: " + mult);
			score *= mult;
		}
		logDebug("\tTotal score: " + score);
		return score;
	}
	
	/**
	 * Try to make peace with the specified faction. May fail if our/their war weariness is too low.
	 * @param enemyId
	 * @param ourWeariness
	 * @return A {@code DiplomacyIntel} representing the ceasefire/peace treaty if successful, 
	 * or a {@code CeasefirePromptIntel} if offering to player.
	 */
	protected IntelInfoPlugin tryMakePeace(String enemyId, float ourWeariness)
	{
		FactionAPI enemy = Global.getSector().getFaction(enemyId);
		// don't diplo with player if they're commissioned with someone else
		
		boolean enemyIsPlayer = Nex_IsFactionRuler.isRuler(enemyId);
		float enemyWeariness = DiplomacyManager.getWarWeariness(enemyId, true);
		log.info("\t" + enemyId + " weariness: " + enemyWeariness + "/" + NexConfig.minWarWearinessForPeace);
		if (!enemyIsPlayer) {
			if (enemyWeariness < NexConfig.minWarWearinessForPeace)
				return null;
		} else {
			if (playerCeasefireOfferCooldown > 0)
				return null;
		}
		
		// add war weariness of both factions, plus effects from recent events
		float sumWeariness = ourWeariness + enemyWeariness;
		log.info("\tWeariness sum: " + sumWeariness);
		
		float eventsMod = getDispositionFromEvents(enemyId) +
				DiplomacyManager.getManager().getDiplomacyBrain(enemyId).getDispositionFromEvents(factionId);
		eventsMod *= EVENT_PEACE_MULT;
		log.info("\tEvents modifier: " + eventsMod);
		
		sumWeariness += eventsMod;
		
		// roll chance for peace
		float divisor = NexConfig.warWearinessDivisor + NexConfig.warWearinessDivisorModPerLevel 
				* Global.getSector().getPlayerPerson().getStats().getLevel();
		if (Math.random() > sumWeariness / divisor)
			return null;
		
		log.info("\tNegotiating treaty");
		boolean peaceTreaty = false;    // if false, only ceasefire
		// can't peace treaty if vengeful, only ceasefire
		if (faction.isAtWorst(enemy, RepLevel.HOSTILE))
		{
			peaceTreaty = Math.random() < DiplomacyManager.PEACE_TREATY_CHANCE;
		}
		if (enemyIsPlayer) {
			CeasefirePromptIntel intel = new CeasefirePromptIntel(factionId, peaceTreaty);
			intel.init();
			playerCeasefireOfferCooldown = 60;
			return intel;
		}
		String eventId = peaceTreaty ? "peace_treaty" : "ceasefire";
		float reduction = peaceTreaty ? NexConfig.warWearinessPeaceTreatyReduction : NexConfig.warWearinessCeasefireReduction;
		
		DiplomacyIntel intel = DiplomacyManager.createDiplomacyEventV2(faction, enemy, eventId, null);
		DiplomacyManager.getManager().modifyWarWeariness(factionId, -reduction);
		DiplomacyManager.getManager().modifyWarWeariness(enemyId, -reduction);
		return intel;
	}
	
	/**
	 * Try to make peace with one of our enemies. 
	 * Normally checks the top three enemy factions sorted by their war weariness, descending.
	 * May fail if our war weariness is too low or there are no suitable targets for this action.
	 * @param targetFactionId If non-null, only check peace with this faction.
	 * @return A {@code DiplomacyIntel} representing the ceasefire/peace treaty if successful, 
	 * or a {@code CeasefirePromptIntel} if offering to player.
	 */
	public IntelInfoPlugin checkPeace(@Nullable String targetFactionId)
	{
		if (enemies.isEmpty()) return null;
		if (NexUtilsFaction.isPirateFaction(factionId) && !NexConfig.allowPirateInvasions)
			return null;
		
		long lastWar = DiplomacyManager.getManager().getLastWarTimestamp();
		if (Global.getSector().getClock().getElapsedDaysSince(lastWar) < DiplomacyManager.MIN_INTERVAL_BETWEEN_WARS)
			return null;
		
		float ourWeariness = DiplomacyManager.getWarWeariness(factionId, true);
		log.info("Checking peace for faction " + faction.getDisplayName() + ": weariness " + ourWeariness);
		if (ourWeariness < NexConfig.minWarWearinessForPeace)
			return null;
		
		List<String> enemiesLocal = new ArrayList<>();
		if (targetFactionId == null) {
			enemiesLocal.addAll(this.enemies);
			Collections.sort(enemiesLocal, new Comparator<String>() {
				@Override
				public int compare(String factionId1, String factionId2)
				{
					float weariness1 = DiplomacyManager.getWarWeariness(factionId1);
					float weariness2 = DiplomacyManager.getWarWeariness(factionId2);

					return Float.compare(weariness1, weariness2);
				}
			});
		} else {
			enemiesLocal.add(targetFactionId);
		}
		
		// list everyone we're currently trying to invade/raid/etc., don't bother making peace with them
		Set<BaseIntelPlugin> offensives = getOffensivesToCheckWhenCeasefiring();
		
		int tries = 3;
		FACTION: for (String enemyId : enemiesLocal)
		{
			if (recentWars.containsKey(enemyId)) continue;
			if (!NexFactionConfig.canCeasefire(factionId, enemyId))
				continue;
			// don't diplomacy with a commissioned player
			if (enemyId.equals(Factions.PLAYER) && Misc.getCommissionFaction() != null)
				continue;

			for (BaseIntelPlugin off : offensives) {
				if (this.shouldOffensiveBlockCeasefire(off, enemyId)) {
					log.info("  Blocking peace with " + enemyId + " due to ongoing offensive");
					continue FACTION;
				}
			}

			IntelInfoPlugin intel = tryMakePeace(enemyId, ourWeariness);
			if (intel != null) {
				updateEnemiesAndCeasefires(0);
				return intel;
			}
			tries--;
			if (tries <= 0) break;
		}
		
		return null;
	}

	public Set<BaseIntelPlugin> getOffensivesToCheckWhenCeasefiring() {
		Set<BaseIntelPlugin> raids = new LinkedHashSet<>();
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(OffensiveFleetIntel.class)) {
			OffensiveFleetIntel ofi = (OffensiveFleetIntel)intel;
			if (!shouldCheckOffensiveForBlockCeasefire(ofi)) continue;
			raids.add(ofi);
		}

		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(HegemonyInspectionIntel.class)) {
			HegemonyInspectionIntel hii = (HegemonyInspectionIntel)intel;
			if (!shouldCheckOffensiveForBlockCeasefire(hii)) continue;
			raids.add(hii);
		}

		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(GroundBattleIntel.class)) {
			GroundBattleIntel gbi = (GroundBattleIntel)intel;
			if (!shouldCheckOffensiveForBlockCeasefire(gbi)) continue;
			raids.add(gbi);
		}

		return raids;
	}

	protected boolean shouldCheckOffensiveForBlockCeasefire(BaseIntelPlugin intel) {
		if (intel.isEnding() || intel.isEnded()) return false;
		if (intel instanceof RaidIntel) {
			//RaidIntel raid = (RaidIntel) intel;
			//if (raid.getCurrentStage() == 0) return false;
		}

		if (intel instanceof OffensiveFleetIntel) {
			OffensiveFleetIntel ofi = (OffensiveFleetIntel)intel;
			if (ofi.getOutcome() != null) return false;
			if (!ofi.isAbortIfNonHostile()) return false;
		}
		if (intel instanceof HegemonyInspectionIntel) {
			HegemonyInspectionIntel hii = (HegemonyInspectionIntel)intel;
			if (hii.getOrders() != AntiInspectionOrders.RESIST) return false;
		}
		if (intel instanceof GroundBattleIntel) {
			GroundBattleIntel gbi = (GroundBattleIntel)intel;
			if (gbi.getOutcome() != null) return false;
		}

		return true;
	}

	protected boolean shouldOffensiveBlockCeasefire(BaseIntelPlugin intel, String factionIdForPotentialPeace) {
		String target = null;
		String source = null;
		if (intel instanceof RaidIntel) {
			RaidIntel raid = (RaidIntel) intel;
			source = raid.getFaction().getId();
		}
		else if (intel instanceof GroundBattleIntel) {
			GroundBattleIntel gbi = (GroundBattleIntel)intel;
			source = gbi.getSide(true).getFaction().getId();
			target = gbi.getSide(false).getFaction().getId();
		}

		String cfid = Misc.getCommissionFactionId();

		//log.info("  Checking offensive intel " + intel.getSmallDescriptionTitle());

		if (intel instanceof OffensiveFleetIntel) {
			OffensiveFleetIntel ofi = (OffensiveFleetIntel)intel;
			target = ofi.getTarget() != null ? ofi.getTarget().getFactionId() : null;
		}
		if (intel instanceof HegemonyInspectionIntel) {
			target = Factions.PLAYER;
		}

		if (Factions.PLAYER.equals(source) && cfid != null)
			source = cfid;
		if (Factions.PLAYER.equals(target) && cfid != null)
			target = cfid;

		// we or our ally are attacking them or their ally
		if (AllianceManager.areFactionsAllied(source, this.factionId) && AllianceManager.areFactionsAllied(target, factionIdForPotentialPeace))
			return true;
		// they or their ally are attacking us or our ally
		if (AllianceManager.areFactionsAllied(target, this.factionId) && AllianceManager.areFactionsAllied(source, factionIdForPotentialPeace))
			return true;

		return false;
	}


	
	public RepLevel getMaxRepForOpportunisticWar() {
		
		/*
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		float niceness = conf.alignments.get(Alignment.DIPLOMATIC) - conf.alignments.get(Alignment.MILITARIST);
		if (niceness >= 1f)
			return RepLevel.HOSTILE;	//effectively disabled (we'd already be at war)
		else if (niceness >= 0.5f)
			return RepLevel.INHOSPITABLE;
		return RepLevel.SUSPICIOUS;
		*/
		
		RepLevel required = RepLevel.INHOSPITABLE;
		List<String> traits = DiplomacyTraits.getFactionTraits(factionId);
		if (traits.contains(TraitIds.PREDATORY))
			required = RepLevel.WELCOMING;
		else if (traits.contains(TraitIds.PARANOID))
			required = RepLevel.SUSPICIOUS;
		else if (traits.contains(TraitIds.PACIFIST))
			required = RepLevel.VENGEFUL;
		
		return required;
	}
	
	/**
	 * Try to declare war on a target that we dislike and consider vulnerable. 
	 * Don't do anything if no suitable targets or we are otherwise not willing to go to war.
	 * @param targetFactionId If non-null, only check war with this faction.
	 * @return A {@code DiplomacyIntel} representing the war declaration, if successful.
	 */
	public DiplomacyIntel checkWar(@Nullable String targetFactionId)
	{
		long lastWar = DiplomacyManager.getManager().getLastWarTimestamp();
		if (Global.getSector().getClock().getElapsedDaysSince(lastWar) < DiplomacyManager.MIN_INTERVAL_BETWEEN_WARS)
			return null;
		
		log.info("Checking war for faction " + faction.getDisplayName());
		if (NexUtilsFaction.isPirateOrTemplarFaction(factionId) && !NexConfig.allowPirateInvasions)
			return null;
		
		float ourWeariness = DiplomacyManager.getWarWeariness(factionId, true);
		if (ourWeariness > MAX_WEARINESS_FOR_WAR)
			return null;
		
		// check factions in order of how much we hate them
		List<DispositionEntry> dispositionsList = getDispositionsList();
		Collections.sort(dispositionsList, new Comparator<DispositionEntry>() {
			@Override
			public int compare(DispositionEntry data1, DispositionEntry data2)
			{
				return -Float.compare(data1.disposition.getModifiedValue(), data2.disposition.getModifiedValue());
			}
		});
		
		RepLevel maxRep = getMaxRepForOpportunisticWar();
		log.info("Relationship required for war: " + maxRep);
		if (maxRep.isAtBest(RepLevel.HOSTILE))
			return null;
		
		WeightedRandomPicker<String> warPicker = new WeightedRandomPicker<>();
		for (DispositionEntry disposition : dispositionsList)
		{
			String otherFactionId = disposition.factionId;
			if (targetFactionId != null && !targetFactionId.equals(otherFactionId)) continue;
			
			// don't diplo with player if they're commissioned with someone else
			if (!canWarWithFaction(otherFactionId, disposition)) continue;
			
			float decisionRating = getWarDecisionRating(otherFactionId);
			if (decisionRating > DECISION_RATING_FOR_WAR + MathUtils.getRandomNumberInRange(-5, 5))
			{
				warPicker.add(otherFactionId, decisionRating);
			}
		}
		if (warPicker.isEmpty()) return null;
		
		return DiplomacyManager.createDiplomacyEventV2(faction, Global.getSector().getFaction(warPicker.pick()),
				"declare_war", null);
	}

	public boolean canWarWithFaction(String otherFactionId) {
		return canWarWithFaction(otherFactionId, this.getDisposition(otherFactionId));
	}

	public boolean canWarWithFaction(String otherFactionId, DispositionEntry disposition) {
		boolean predatory = DiplomacyTraits.hasTrait(factionId, TraitIds.PREDATORY);
		RepLevel maxRep = getMaxRepForOpportunisticWar();
		if (predatory && otherFactionId.equals(Factions.PLAYER)) {
			maxRep = RepLevel.SUSPICIOUS;
		}

		if (AllianceManager.areFactionsAllied(factionId, otherFactionId)) return false;

		log.info("Checking vs. " + otherFactionId + ": " + disposition.disposition.getModifiedValue()
				+ ", " + faction.isAtBest(otherFactionId, maxRep));

		// don't diplo with player if they're commissioned with someone else
		if (otherFactionId.equals(Factions.PLAYER) && !otherFactionId.equals(PlayerFactionStore.getPlayerFactionId()))
			return false;
		if (!SectorManager.isFactionAlive(otherFactionId)) return false;
		if (DiplomacyManager.disallowedFactions.contains(otherFactionId)) return false;
		if (NexUtilsFaction.isPirateFaction(otherFactionId) && !NexConfig.allowPirateInvasions)
			return false;
		if (ceasefires.containsKey(otherFactionId)) return false;
		if (!faction.isAtBest(otherFactionId, maxRep)) return false;	// relations aren't bad enough yet
		if (faction.isHostileTo(otherFactionId)) return false;	// already at war
		if (!predatory && disposition.disposition.getModifiedValue() > MAX_DISPOSITION_FOR_WAR) return false;
		return true;
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
			if (Nex_IsFactionRuler.isRuler(otherFactionId)) {
				if (!NexConfig.followersDiplomacy) continue;
				if (!otherFactionId.equals(PlayerFactionStore.getPlayerFactionId())) continue;
			}
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
			else if (disp <= DISLIKE_THRESHOLD)
			{
				params.onlyNegative = true;
			}
			else if (disp >= LIKE_THRESHOLD)
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
		if (Global.getSector().isInNewGameAdvance()) return;

		if (!NexConfig.enableDiplomacy) return;
		if (StrategicAI.getAI(factionId) != null) return;

		if (DiplomacyManager.disallowedFactions.contains(factionId)) return;
		if (Nex_IsFactionRuler.isRuler(factionId))
			return;
		
		log.info("Diplomacy brain for " + factionId + " considering options");
		
		boolean didSomething = false;
		
		// first see if we should make peace
		didSomething = checkPeace(null) != null;
		if (didSomething) return;
		
		// let's see if we should declare war on anyone
		didSomething = checkWar(null) != null;
		if (didSomething) return;
		
		// do a random event
		doRandomEvent();
	}
	
	// Total disposition penalty from revanchism for a faction, towards all other factions,
	// cannot exceed REVANCHISM_FACTION_MAX
	// if the raw sum does, disposition penalty will be pro-rated among occupier factions
	public void cacheRevanchism()
	{
		revanchismCache.clear();
		// player has no revanchism
		if (factionId.equals(Factions.PLAYER))
			return;
		
		Map<String, Float> revanchismTemp = new HashMap<>();
		Set<String> seenFactions = new HashSet<>();
		float total = 0;
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			String mktFactionId = market.getFactionId();
			if (mktFactionId.equals(this.factionId))
				continue;
			
			// this market used to belong to us, increment revanchism towards its current owner
			if (NexUtilsMarket.wasOriginalOwner(market, this.factionId))
			{
				float curr = 0;
				if (revanchismTemp.containsKey(mktFactionId)) 
				{
					curr = revanchismTemp.get(mktFactionId);
				}
				float fromMarket = market.getSize() * REVANCHISM_SIZE_MULT;
				
				curr += fromMarket;
				if (curr > REVANCHISM_FACTION_MAX)
					curr = REVANCHISM_FACTION_MAX;
				
				revanchismTemp.put(mktFactionId, curr);
				
				total += fromMarket;
				
				seenFactions.add(mktFactionId);
			}
		}
		
		float mult = 1;
		if (total > REVANCHISM_MAX) {
			mult = REVANCHISM_MAX / total;
		}
		
		for (String otherFactionId : seenFactions) {
			revanchismCache.put(otherFactionId, revanchismTemp.get(otherFactionId) * mult);
		}
	}
	
	/**
	 * Is either the brain's faction or the faction specified in argument 
	 * the player faction or the player's commissioning faction, 
	 * and are we in Starfarer mode?
	 * @param factionId
	 * @return
	 */
	public boolean isHardMode(String factionId)
	{
		if (!SectorManager.getManager().isHardMode())
			return false;
		String myFactionId = PlayerFactionStore.getPlayerFactionId();
		
		return factionId.equals(Factions.PLAYER) 
				|| factionId.equals(myFactionId)
				|| this.factionId.equals(Factions.PLAYER) 
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
		Iterator<String> ceasefiresIter = ceasefires.keySet().iterator();
		while (ceasefiresIter.hasNext())
		{
			String otherFactionId = ceasefiresIter.next();
			float timeRemaining = ceasefires.get(otherFactionId);
			timeRemaining -= days;
			if (timeRemaining <= 0)
				ceasefiresIter.remove();
			else
				ceasefires.put(otherFactionId, timeRemaining);
		}
		
		Iterator<String> recentWarsIter = recentWars.keySet().iterator();
		while (recentWarsIter.hasNext())
		{
			String otherFactionId = recentWarsIter.next();
			float timeRemaining = recentWars.get(otherFactionId);
			timeRemaining -= days;
			if (timeRemaining <= 0)
				recentWarsIter.remove();
			else
				recentWars.put(otherFactionId, timeRemaining);
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
		
		Set<String> newEnemies = new HashSet<>(latestEnemies);
		newEnemies.removeAll(enemies);
		for (String enemyId : newEnemies) {
			recentWars.put(enemyId, RECENT_WAR_LENGTH);
		}
		
		enemies = latestEnemies;
	}
	
	public void addCeasefire(String enemyId) {
		ceasefires.put(enemyId, CEASEFIRE_LENGTH);
	}
	
	public void update(float days)
	{
		cacheRevanchism();
		ourStrength = getFactionStrength(factionId);
		enemyStrength = getFactionEnemyStrength(factionId);
		updateAllDispositions(days);
		considerOptions();
		
		float time = DiplomacyManager.getBaseInterval();
		interval.setInterval(time * 0.95f, time * 1.05f);
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
		
		if (playerCeasefireOfferCooldown > 0) {
			playerCeasefireOfferCooldown -= days;
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
		if (revanchismCache == null)
			revanchismCache = new HashMap<>();
		if (recentWars == null)
			recentWars = new HashMap<>();
		
		faction = Global.getSector().getFaction(factionId);
		return this;
	}
	
	//==========================================================================
	//==========================================================================
	
	protected static void logDebug(String str) {
		if (!ExerelinModPlugin.isNexDev) return;
		log.info(str);
	}
	
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
		Set<String> enemies = new HashSet<>(DiplomacyManager.getFactionsAtWarWithFaction(factionId, NexConfig.allowPirateInvasions, false, true));
		
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

	public static void reportDispositionsUpdated(String factionId, DiplomacyBrain brain) {
		for (DiplomacyBrainListener x : Global.getSector().getListenerManager().getListeners(DiplomacyBrainListener.class))
		{
			x.reportDispositionsUpdated(factionId, brain);
		}
	}
	
	public static class DispositionEntry
	{
		public String factionId;
		public MutableStat disposition = new MutableStat(0);
		
		public DispositionEntry(String factionId)
		{
			this.factionId = factionId;
		}
	}
}
