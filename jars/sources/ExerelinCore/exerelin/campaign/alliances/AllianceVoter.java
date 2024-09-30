package exerelin.campaign.alliances;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyBrain;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import exerelin.campaign.intel.AllianceVoteIntel;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handles voting by an alliance's members to join one of their own in war or peace
 */
public class AllianceVoter {
	
	public static final float BASE_POINTS = 75;
	public static final float POINTS_NEEDED = 100;
	public static final float MAX_POINTS_TO_DEFY = 50;
	public static final float ABSTAIN_THRESHOLD = 40;
	public static final float STRENGTH_POINT_MULT = 0.5f;
	public static final float HAWKISHNESS_POINTS = 50;
	public static final float WEARINESS_MULT = 0.005f;
	public static final float RANDOM_POINTS = 50;
	public static final float DEFIER_REP_LOSS_YES = -0.1f;
	public static final float DEFIER_REP_LOSS_ABSTAIN = -0.05f;
	public static final String VOTE_MEM_KEY = "$nex_allianceVote";
	public static final String VOTE_DEFY_MEM_KEY = "$nex_allianceVote_willDefy";
	
	protected static final boolean DEBUG_LOGGING = true;
	
	public static Logger log = Global.getLogger(AllianceVoter.class);
	
	/**
	 * Decide whether the alliance(s) should declare war on/make peace with the other party, e.g. after a diplomacy event
	 * i.e. if faction1 has declared war on or made peace with faction2, decide whether their alliance(s) should do the same
	 * If either or both vote yes, modify relationships as appropriate
	 * 
	 * If one alliance votes yes but other does not, only declare on/make peace with the one faction involved
	 * @param faction1Id
	 * @param faction2Id
	 * @param isWar True if the two factions have gone to war, false if they are making peace
	 */
	public static void allianceVote(String faction1Id, String faction2Id, boolean isWar)
	{
		String commId = Misc.getCommissionFactionId();
		if (faction1Id.equals(Factions.PLAYER) && commId != null) {
			faction1Id = commId;
		}
		if (faction2Id.equals(Factions.PLAYER) && commId != null) {
			faction2Id = commId;
		}

		Alliance ally1 = AllianceManager.getFactionAlliance(faction1Id);
		Alliance ally2 = AllianceManager.getFactionAlliance(faction2Id);
		if (ally1 == null && ally2 == null) return;
		if (ally1 == ally2) return;

		log.info("Vote initiated, printing partial stacktrace");
		NexUtils.printStackTrace(log, 7);
		
		String playerFacId = PlayerFactionStore.getPlayerFactionId();
		
		// prompt player for vote if we're allied with one of the factions, while not being one of the factions
		if (Nex_IsFactionRuler.isRuler(playerFacId))
		{
			if (!playerFacId.equals(faction1Id) && !playerFacId.equals(faction2Id))
			{
				Alliance pAlly = AllianceManager.getFactionAlliance(playerFacId);
				if (pAlly != null && (pAlly == ally1 || pAlly == ally2))
				{
					//log.info("Triggering alliance vote as " + playerFacId);
					Global.getSector().addTransientScript(new AllianceVoteScreenScript(faction1Id, faction2Id, isWar));
					return;
				}
			}
		}
		//log.info("No alliance vote for " + playerFacId);
		allianceVote(faction1Id, faction2Id, isWar, null, null);	
	}
	
	/**
	 * Decide whether the alliance(s) should declare war on/make peace with the other party, e.g. after a diplomacy event
	 * i.e. if faction1 has declared war on or made peace with faction2, decide whether their alliance(s) should do the same
	 * If either or both vote yes, modify relationships as appropriate
	 * 
	 * If one alliance votes yes but other does not, only declare on/make peace with the one faction involved
	 * @param faction1Id
	 * @param faction2Id
	 * @param isWar True if the two factions have gone to war, false if they are making peace
	 * @param vote1 Alliance 1 vote, if already executed
	 * @param vote2 Alliance 2 vote, if already executed
	 */
	public static void allianceVote(String faction1Id, String faction2Id, boolean isWar, VoteResult vote1, VoteResult vote2)
	{
		Alliance ally1 = AllianceManager.getFactionAlliance(faction1Id);
		Alliance ally2 = AllianceManager.getFactionAlliance(faction2Id);
		if (ally1 == null && ally2 == null) return;
		if (ally1 == ally2) return;
		
		if (ally1 != null && vote1 == null) vote1 = allianceVote(ally1, faction1Id, faction2Id, isWar);		
		if (ally2 != null && vote2 == null) vote2 = allianceVote(ally2, faction2Id, faction1Id, isWar);
		
		Set<String> defyingFactions = new HashSet<>();
		if (vote1 != null) defyingFactions.addAll(vote1.defied);
		if (vote2 != null) defyingFactions.addAll(vote2.defied);

		Set<String> side1 = new HashSet<>();
		side1.add(faction1Id);
		if (ally1 != null) side1.addAll(ally1.getMembersCopy());

		Set<String> side2 = new HashSet<>();
		side2.add(faction2Id);
		if (ally2 != null) side2.addAll(ally2.getMembersCopy());
		
		// handle vote results
		if (vote1 != null && vote1.success)
		{
			if (vote2 != null && vote2.success)
				AllianceManager.doAlliancePeaceStateChange(faction1Id, faction2Id, ally1, ally2, vote1, vote2, isWar, defyingFactions);
			else
				AllianceManager.doAlliancePeaceStateChange(faction1Id, faction2Id, ally1, null, vote1, null, isWar, defyingFactions);
		}
		else if (vote2 != null && vote2.success)
		{
			AllianceManager.doAlliancePeaceStateChange(faction1Id, faction2Id, null, ally2, null, vote2, isWar, defyingFactions);
		}

		// after a war vote, add ceasefires to factions choosing not to participate
		// this is so they don't join the war immediately after with a strategy AI intervention action
		if (isWar) {
			if (vote1 != null) {
				AllianceManager.applyCeasefireOnNoVote(vote1, side2);
			}
			if (vote2 != null) {
				AllianceManager.applyCeasefireOnNoVote(vote2, side1);
			}
		}
		
		// report intel
		if (ally1 != null)
		{
			AllianceVoteIntel intel = new AllianceVoteIntel(ally1.uuId, vote1, 
					ally2 != null ? ally2.uuId: faction2Id, 
					ally2 != null, isWar);
			NexUtils.addExpiringIntel(intel);
		}
		if (ally2 != null)
		{
			AllianceVoteIntel intel = new AllianceVoteIntel(ally2.uuId, vote2, 
					ally1 != null ? ally1.uuId: faction1Id, 
					ally1 != null, isWar);
			NexUtils.addExpiringIntel(intel);
		}
		
		// alliance hates on defiers
		handleDefyRelations(vote1);
		handleDefyRelations(vote2);
	}
	
	protected static void handleDefyRelations(VoteResult vote)
	{
		if (vote == null) return;
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		if (vote.defied.isEmpty()) return;
		
		Set<String> toModify = new HashSet<>(vote.yesVotes);
		toModify.addAll(vote.abstentions);
		for (String member : toModify)
		{
			FactionAPI faction = Global.getSector().getFaction(member);
			float penalty = DEFIER_REP_LOSS_YES;
			if (vote.abstentions.contains(member))
				penalty = DEFIER_REP_LOSS_ABSTAIN;
			for (String defier : vote.defied)
			{
				FactionAPI defierFaction = Global.getSector().getFaction(defier);
				// uses adjustPlayerReputation to generate a notification message
				if (member.equals(playerAlignedFactionId))
				{
					NexUtilsReputation.adjustPlayerReputation(defierFaction, penalty);
				}
				else if (defier.equals(playerAlignedFactionId))
				{
					NexUtilsReputation.adjustPlayerReputation(faction, penalty);
				}
				else
				{
					DiplomacyManager.adjustRelations(faction, defierFaction, 
							penalty, null, null, RepLevel.HOSTILE, true);
				}
				AllianceManager.remainInAllianceCheck(defier, member);
			}
		}
		NexUtilsReputation.syncFactionRelationshipsToPlayer();
	}
	
	/**
	 * Decide whether the alliance should declare war on/make peace with the other faction (and possibly their allies, if any)
	 * @param alliance
	 * @param factionId The alliance member whose action triggered the vote
	 * @param otherFactionId The other faction with whom the alliance member interacted with
	 * @param isWar True if the two factions have gone to war, false if they are making peace
	 * @return True if the alliance votes to also declare war on/make peace with otherFaction, as faction has done
	 */
	protected static VoteResult allianceVote(Alliance alliance, String factionId, String otherFactionId, boolean isWar)
	{
		Set<String> factionsToConsider = new HashSet<>();
		Alliance otherAlliance = AllianceManager.getFactionAlliance(otherFactionId);
		float ourStrength = alliance.getAllianceMarketSizeSum();
		float theirStrength = 0;
		Set<String> yesVotes = new HashSet<>();
		Set<String> noVotes = new HashSet<>();
		Set<String> abstentions = new HashSet<>();
		Set<String> defied = new HashSet<>();
		
		if (otherAlliance != null)
		{
			factionsToConsider.addAll(otherAlliance.members);
			theirStrength = otherAlliance.getAllianceMarketSizeSum();
		}
		else {
			theirStrength = NexUtilsFaction.getFactionMarketSizeSum(otherFactionId);
		}
		if (theirStrength <= 0) theirStrength = 1;
		float strengthRatio = ourStrength / theirStrength;
		
		if (DEBUG_LOGGING)
		{
			String header = "== Vote for peace with ";
			if (isWar) header = "== Vote for war with ";
			
			header += otherFactionId + " ==";
			log.info(header);
		}
		
		for (String allianceMember : alliance.members)
		{
			Vote vote = factionVote(isWar, alliance, allianceMember, factionId, otherFactionId, 
					factionsToConsider, strengthRatio);
			if (vote == Vote.YES) {
				yesVotes.add(allianceMember);
			}
			else if (vote == Vote.NO) {
				noVotes.add(allianceMember);
			}
			else {
				abstentions.add(allianceMember);
			}
		}
		
		boolean success = yesVotes.size() > noVotes.size();
		if (success)	// note: only check for defiance if vote passes
		{
			for (String voter : noVotes)
			{
				if (decideToDefyVote(isWar, alliance, voter, factionId, otherFactionId))
				{
					defied.add(voter);
				}
			}
		}
		
		if (DEBUG_LOGGING)
		{
			log.info("Final vote: " + success + " (" + yesVotes.size() + " to " + noVotes.size() + ")");
		}
		
		return new VoteResult(success, yesVotes, noVotes, abstentions, defied);
	}
	
	/**
	 * Makes the faction vote for whether the alliance should join a war or make peace with another faction/alliance
	 * @param isWar
	 * @param alliance
	 * @param factionId The voting faction
	 * @param friendId The alliance member whose action triggered the vote
	 * @param otherFactionId The other faction with whom the alliance member interacted with
	 * @param otherAllianceMembers Other members of the other faction's alliance, if any
	 * @param strengthRatio Ratio of the sum of market sizes for each side
	 * @return A yes vote if the faction wants to join its ally in war/peace, or no if it does not; abstain if undecided
	 */
	protected static Vote factionVote(boolean isWar, Alliance alliance, String factionId, String friendId, 
			String otherFactionId, Set<String> otherAllianceMembers, float strengthRatio)
	{
		/*
		Stuff that goes into the vote:
		- How much we like our friend
		- How much we like the other faction being interacted with
		- How much we like the other faction's allies in general
		- Our strength versus theirs
		- How aggressive we are in general
		- Random factor
		*/
		
		// return vote from memory if this is player faction
		if (factionId.equals(PlayerFactionStore.getPlayerFactionId()))
		{
			FactionAPI faction = Global.getSector().getFaction(factionId);
			MemoryAPI mem = faction.getMemoryWithoutUpdate();
			if (mem.contains(VOTE_MEM_KEY))
			{
				Vote vote = (Vote)mem.get(VOTE_MEM_KEY);
				mem.unset(VOTE_MEM_KEY);
				return vote;
			}
		}
		
		// if we're the friend, auto-vote yes
		if (factionId.equals(friendId)) return Vote.YES;
		
		// Helps Allies traits
		if (DiplomacyTraits.hasTrait(factionId, TraitIds.HELPS_ALLIES))
			return Vote.YES;
		
		FactionAPI us = Global.getSector().getFaction(factionId);
		NexFactionConfig usConf = NexConfig.getFactionConfig(factionId);
		
		// already at war/peace
		if (isWar && us.isHostileTo(otherFactionId)) {
			return Vote.YES;
		}
		else if (!isWar && !us.isHostileTo(otherFactionId)) {
			return Vote.YES;
		}
		
		// don't bother calculating vote if we like/hate them forever
		if (!DiplomacyManager.haveRandomRelationships(factionId, otherFactionId))
		{
			if (isWar)
			{
				if (NexFactionConfig.getMinRelationship(factionId, otherFactionId) > AllianceManager.HOSTILE_THRESHOLD)
					return Vote.NO;
			}
			else
			{
				if (DiplomacyManager.getManager().getMaxRelationship(factionId, otherFactionId) < AllianceManager.HOSTILE_THRESHOLD)
					return Vote.NO;
			}
		}
		
		// refuse to declare war if we're friendly or better with the target
		if (isWar && us.isAtWorst(otherFactionId, RepLevel.FRIENDLY)) {
			return Vote.NO;
		}
		DiplomacyBrain brain = DiplomacyManager.getManager().getDiplomacyBrain(factionId);
		float friendDisposition = brain.getDisposition(friendId).disposition.getModifiedValue();
		float otherDisposition = brain.getDisposition(otherFactionId).disposition.getModifiedValue();
				
		int factionCount = 0;
		float otherAllianceDispositionSum = 0;
		
		Map<Alignment, Float> alignments = usConf.getAlignmentValues();
		float hawkishness = alignments.get(Alignment.MILITARIST);
		float diplomaticness = alignments.get(Alignment.DIPLOMATIC);
		
		for (String otherMember: otherAllianceMembers)
		{
			if (otherMember.equals(otherFactionId)) continue;
			otherAllianceDispositionSum += brain.getDisposition(otherMember).disposition.getModifiedValue();
			
			factionCount++;
		}
		
		// from relationships
		float totalPoints = BASE_POINTS + friendDisposition*2f;
		float otherDispositionPoints = isWar ? -(otherDisposition)*2f : (otherDisposition)*2f;
		totalPoints += otherDispositionPoints;
		
		float otherAllianceDispositionAvg = (factionCount > 0) ? otherAllianceDispositionSum/factionCount : 0;
		float otherAllianceDispositionPoints = isWar ? -(otherAllianceDispositionAvg) * 1f 
				: (otherAllianceDispositionAvg) * 1f;
		totalPoints += otherAllianceDispositionPoints;
		
		// strength
		float strengthPoints = (strengthRatio - 1) * 100 * STRENGTH_POINT_MULT;
		if (!isWar) strengthPoints *= -1;	// favour peace if we're weaker than them, war if we're stronger
		totalPoints += strengthPoints;
		
		// hawkishness
		float hawkPoints = (hawkishness - diplomaticness) * HAWKISHNESS_POINTS;
		if (!isWar) hawkPoints *= -1;
		totalPoints += hawkPoints;
		
		// weariness
		float wearinessPoints = DiplomacyManager.getWarWeariness(factionId, false) * WEARINESS_MULT;
		if (isWar) wearinessPoints *= -1;
		totalPoints += wearinessPoints;
		
		// random
		float randomPoints = RANDOM_POINTS * (MathUtils.getRandomNumberInRange(-0.5f, 0.5f) + MathUtils.getRandomNumberInRange(-0.5f, 0.5f));
		totalPoints += randomPoints;
		
		Vote vote = Vote.ABSTAIN;
		if (totalPoints > POINTS_NEEDED + ABSTAIN_THRESHOLD)
		{
			vote = Vote.YES;
		}
		else if (totalPoints < POINTS_NEEDED - ABSTAIN_THRESHOLD)
		{
			vote = Vote.NO;
		}
		
		// debug
		if (DEBUG_LOGGING)
		{
			log.info(us.getDisplayName() + " votes: " + vote);
			log.info("\tTotal points: " + totalPoints);
			log.info("\tBase points: " + BASE_POINTS);
			log.info("\tFriend disposition: " + friendDisposition * 2f);
			log.info("\tOther disposition: " + otherDispositionPoints);
			log.info("\tOther alliance disposition: " + otherAllianceDispositionPoints);
			log.info("\tStrength points: " + strengthPoints);
			log.info("\tHawk/dove points: " + hawkPoints);
			log.info("\tWar weariness points: " + wearinessPoints);
			log.info("\tRandom points: " + randomPoints);
		}
		
		return vote;
	}
	
	/**
	 * If the vote result was not to our liking, should we ignore it and do our own thing?
	 * @param isWar
	 * @param alliance
	 * @param factionId The faction considering defying the vote
	 * @param friendId The alliance member whose action triggered the vote
	 * @param otherFactionId The other faction with whom the alliance member interacted with
	 * @return
	 */
	protected static boolean decideToDefyVote(boolean isWar, Alliance alliance, String factionId,
			String friendId, String otherFactionId)
	{
		FactionAPI us = Global.getSector().getFaction(factionId);
		NexFactionConfig usConf = NexConfig.getFactionConfig(factionId);
		
		// if we like/hate them forever, defy a vote that would require us to break this
		if (!DiplomacyManager.haveRandomRelationships(factionId, otherFactionId))
		{
			if (isWar)
			{
				if (NexFactionConfig.getMinRelationship(factionId, otherFactionId) > AllianceManager.HOSTILE_THRESHOLD)
					return true;
			}
			else
			{
				if (DiplomacyManager.getManager().getMaxRelationship(factionId, otherFactionId) < AllianceManager.HOSTILE_THRESHOLD)
					return true;
			}
		}
		if (factionId.equals(Factions.PLAYER)) {
			return Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId()).getMemoryWithoutUpdate().getBoolean(VOTE_DEFY_MEM_KEY);
		}
		
		Map<Alignment, Float> alignments = usConf.getAlignmentValues();
		float hawkishness = alignments.get(Alignment.MILITARIST);
		float diplomaticness = alignments.get(Alignment.DIPLOMATIC);
		float friendRelationship = us.getRelationship(friendId);
		
		// if war vote, and we're friendly to target and like the target more than the triggering friend, defy
		if (isWar && us.isAtWorst(otherFactionId, RepLevel.FRIENDLY) 
				&& us.getRelationship(otherFactionId) > friendRelationship)
			return true;
		
		float totalPoints = BASE_POINTS + friendRelationship*100;
		float hawkPoints = hawkishness * HAWKISHNESS_POINTS;
		if (!isWar) hawkPoints *= -1;
		totalPoints += hawkPoints;
		if (!isWar) totalPoints += diplomaticness * HAWKISHNESS_POINTS;
		
		return totalPoints <= MAX_POINTS_TO_DEFY;
	}
	
	public static class VoteResult {
		public final boolean success;
		public final Set<String> yesVotes;
		public final Set<String> noVotes;
		public final Set<String> abstentions;
		public final Set<String> defied;	// factions that refuse to obey the vote results
		
		public VoteResult(boolean success, Set<String> yesVotes, Set<String> noVotes, 
				Set<String> abstentions, Set<String> defied)
		{
			this.success = success;
			this.yesVotes = yesVotes;
			this.noVotes = noVotes;
			this.abstentions = abstentions;
			this.defied = defied;
		}
	}
	
	public enum Vote {
		YES,
		NO,
		ABSTAIN,
		NO_VOTE,
	}
}
