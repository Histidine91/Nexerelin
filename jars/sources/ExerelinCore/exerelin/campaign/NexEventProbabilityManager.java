package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.events.CampaignEventManagerAPI;
import com.fs.starfarer.api.campaign.events.EventProbabilityAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.events.FactionBountyEvent;
import exerelin.campaign.events.FactionBountyEvent.FactionBountyPairKey;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import java.util.List;
import org.apache.log4j.Logger;


/* Faction bounty
 Every 15 days, go through all live factions
 For each unfriendly faction, increment event probability based on our relationship with them

 If a fleet led by faction X kills our ships, increment probability against faction X by (FP lost - FP killed) * something
 If faction X captures one of our markets, increment probability against faction X? (meh)

 Pirate factions are only processed if pirate invasions are enabled
*/

public class NexEventProbabilityManager extends BaseCampaignEventListener implements EveryFrameScript {

	public static Logger log = Global.getLogger(NexEventProbabilityManager.class);
	
	protected IntervalUtil factionBountyInterval = new IntervalUtil(15, 15);
	
	public NexEventProbabilityManager() {
		super(true);
	}
	
	protected boolean canFactionBounty(String factionId)
	{
		FactionAPI faction = Global.getSector().getFaction(factionId);
		if (faction.getCustom().optBoolean(Factions.CUSTOM_POSTS_NO_BOUNTIES))
			return false;
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (conf.pirateFaction && !ExerelinConfig.allowPirateInvasions)
			return false;
		if (!SectorManager.isFactionAlive(factionId))
			return false;
		
		return true;
	}

	/**
	 * Advance probability of faction bounty events based on their relationship with each other
	 */	
	protected void advanceFactionBountyTracker()
	{
		CampaignEventManagerAPI eventManager = Global.getSector().getEventManager();
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		for (String factionId : liveFactions)
		{
			if (!canFactionBounty(factionId))
				continue;
				
			FactionAPI faction = Global.getSector().getFaction(factionId);
			for (String otherFactionId : liveFactions)
			{
				if (!canFactionBounty(otherFactionId))
					continue;
				
				FactionAPI otherFaction = Global.getSector().getFaction(otherFactionId);
				float increment = 0;
				FactionBountyPairKey key = new FactionBountyPairKey(faction, otherFaction);
				EventProbabilityAPI ep = eventManager.getProbability("exerelin_faction_bounty", key);
				if (factionId.equals(otherFactionId)) continue;
				RepLevel rel = faction.getRelationshipLevel(otherFactionId);
				if (rel == RepLevel.INHOSPITABLE)
					increment = FactionBountyEvent.PROBABILITY_INHOSPITABLE;
				else if (rel == RepLevel.HOSTILE)
					increment = FactionBountyEvent.PROBABILITY_HOSTILE;
				else if (rel == RepLevel.VENGEFUL)
					increment = FactionBountyEvent.PROBABILITY_VENGEFUL;
				
				if (increment > 0)
				{
					log.info(String.format("Increasing faction bounty probability %s -> %s due to relationship, by %s, is now %s",
											faction.getDisplayName(), otherFaction.getDisplayName(), "" + increment,
											"" + ep.getProbability()));
					ep.increaseProbability(increment);
				}
			}
		}
	}
	
	@Override
	public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
		CampaignFleetAPI primary1 = battle.getPrimary(battle.getSideOne());
		CampaignFleetAPI primary2 = battle.getPrimary(battle.getSideTwo());
		FactionAPI faction1 = primary1.getFaction();
		FactionAPI faction2 = primary2.getFaction();
		
		if (!canFactionBounty(faction1.getId()) || !canFactionBounty(faction2.getId()))
			return;
		
		float netLoss = 0;
		for (FleetMemberAPI member : Misc.getSnapshotMembersLost(primary1))
			netLoss += member.getFleetPointCost() * 1.5f;
		for (FleetMemberAPI member : Misc.getSnapshotMembersLost(primary2))
			netLoss -= member.getFleetPointCost();
		
		if (Math.abs(netLoss) < 5)
			return;
		
		if (netLoss < 0)	// faction1 had a net gain, increase probability for faction2 instead
		{
			netLoss *= -1;
			faction1 = primary2.getFaction();
			faction2 = primary1.getFaction();
		}
		
		float increment = netLoss * FactionBountyEvent.PROBABILITY_PER_FP;
		CampaignEventManagerAPI eventManager = Global.getSector().getEventManager();
		FactionBountyPairKey key = new FactionBountyPairKey(faction1, faction2);
		EventProbabilityAPI ep = eventManager.getProbability("exerelin_faction_bounty", key);
		log.info(String.format("Increasing faction bounty probability %s -> %s due to battle, by %s, is now %s",
											faction1.getDisplayName(), faction2.getDisplayName(), "" + increment,
											"" + ep.getProbability()));
		ep.increaseProbability(increment);
	}
	
	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		factionBountyInterval.advance(days);
		if (factionBountyInterval.intervalElapsed())
			advanceFactionBountyTracker();
	}
	
}
