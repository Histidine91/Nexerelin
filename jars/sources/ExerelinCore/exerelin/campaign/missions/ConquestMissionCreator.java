package exerelin.campaign.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignMissionPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.MissionBoardAPI;
import com.fs.starfarer.api.campaign.MissionBoardAPI.MissionAvailabilityAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

public class ConquestMissionCreator implements EveryFrameScript {

	public static final int NUM_MISSIONS_PER_WAR = 2;
	public static final float REWARD_MULT = 1000;
	public static final float DURATION_MULT = 15;
	protected MissionBoardAPI board;
	protected IntervalUtil tracker = new IntervalUtil(5, 7);
	protected IntervalUtil trackerShort = new IntervalUtil(0.45f, 0.55f);
	
	public static Logger log = Global.getLogger(ConquestMissionCreator.class);
	
	public ConquestMissionCreator() {
		board = Global.getSector().getMissionBoard();
	}
	
	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}
	
	protected List<String> getFactionsAtWar()
	{
		/*
		int numWars = 0;
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		for (String factionId : liveFactions)
		{
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, ExerelinConfig.allowPirateInvasions, true);
			numWars += enemies.size();
		}
		numWars = numWars/2;	// since the previous code counts both ends
		return numWars;
		*/
		List<String> atWar = new ArrayList<>();
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		for (String factionId : liveFactions)
		{
			if (!ExerelinConfig.allowPirateInvasions && ExerelinUtilsFaction.isPirateFaction(factionId))
				continue;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, ExerelinConfig.allowPirateInvasions, true);
			if (!enemies.isEmpty()) atWar.add(factionId);
		}
		return atWar;
	}
	
	protected boolean doesFactionTargetPairAlreadyExist(String factionId, MarketAPI target)
	{
		List<MissionAvailabilityAPI> existingMissions = board.getMissionsCopy();
		for (MissionAvailabilityAPI existing : existingMissions)
		{
			CampaignMissionPlugin mission = existing.getMission();
			if (mission.getClass() != ConquestMission.class) continue;
			ConquestMission cMission = (ConquestMission) mission;
			if (cMission.getFactionId().equals(factionId) && cMission.getTarget() == target)
				return true;
		}
		return false;
	}
	
	protected void createMission(List<String> factions)
	{
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>();
		WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker<>();
		Map<String, List<String>> enemiesByFaction = new HashMap<>();
		
		//log.info("Picking faction for conquest mission");
		for (String factionId : factions)
		{
			if (factionId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, ExerelinConfig.allowPirateInvasions, true);
			enemiesByFaction.put(factionId, enemies);
			factionPicker.add(factionId, enemies.size());
		}
		String factionId = factionPicker.pick();
		if (factionId == null) return;
		
		//log.info("Picking target for conquest mission");
		List<String> enemies = enemiesByFaction.get(factionId);
		for (String enemyId: enemies)
		{
			if (enemyId.equals(ExerelinConstants.PLAYER_NPC_ID)) continue;
			List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(enemyId);
			for (MarketAPI market : markets) {
				if (!doesFactionTargetPairAlreadyExist(factionId, market))
					targetPicker.add(market);
			}
		}
		MarketAPI target = targetPicker.pick();
		if (target == null) return;
		
		FactionAPI faction = Global.getSector().getFaction(factionId);
		float duration = target.getSize();
		float bonusDuration = MathUtils.getRandomNumberInRange(-1, 3);
		duration *= DURATION_MULT;
		bonusDuration *= DURATION_MULT;
		
		float reward = (float)Math.pow(3, target.getSize());
		reward *= MathUtils.getRandomNumberInRange(0.75f, 1.25f) * ExerelinConfig.conquestMissionRewardMult;
		float bonusReward = reward * MathUtils.getRandomNumberInRange(0.5f, 1f);
		reward = (int)reward * REWARD_MULT;
		bonusReward = (int)bonusReward * REWARD_MULT;
		
		ConquestMission mission = new ConquestMission(target, faction, duration, bonusDuration, reward, bonusReward);
		log.info("Creating conquest mission: " + factionId + "," + target.getName());
		
		//for (MarketAPI market : ExerelinUtilsFaction.getFactionMarkets(factionId))
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) 
		{
			if (!market.getFaction().isHostileTo(target.getFactionId()))
				continue;
			if (market.getFaction().isAtBest(faction, RepLevel.INHOSPITABLE))
				continue;
			if (market.hasCondition(Conditions.MILITARY_BASE) || market.hasCondition(Conditions.REGIONAL_CAPITAL) || market.hasCondition(Conditions.HEADQUARTERS))
				board.makeAvailableAt(mission, market);
		}
	}
	
	@Override
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		tracker.advance(days);
		if (tracker.intervalElapsed()) {
			List<String> factionsAtWar = getFactionsAtWar();
			int maxConcurrent = factionsAtWar.size() * NUM_MISSIONS_PER_WAR;
			int num = board.getNumMissions(ConquestMission.class);
			if (num < maxConcurrent) {
				createMission(factionsAtWar);
			}
		}
		
		// reverse compatibility
		if (trackerShort == null)
			trackerShort = new IntervalUtil(0.45f,0.55f);
		
		trackerShort.advance(days);
		if (trackerShort.intervalElapsed()) {
			List<ConquestMission> toRemove = new ArrayList<>();
			
			List<MissionAvailabilityAPI> missions = board.getMissionsCopy();
			for (MissionAvailabilityAPI mission : missions) {
				if (mission.getMission() instanceof ConquestMission) {
					ConquestMission cm = (ConquestMission)(mission.getMission());
					FactionAPI targetFaction = cm.getTarget().getFaction();
					if (!targetFaction.isHostileTo(cm.issuer))
						toRemove.add(cm);
					else {
						Set<SectorEntityToken> tokens = mission.getAvailableAt();
						List<SectorEntityToken> toDelist = new ArrayList<>();
						for (SectorEntityToken token: tokens) {
							if (!token.getFaction().isHostileTo(targetFaction) 
									|| token.getFaction().isAtBest(cm.issuer, RepLevel.INHOSPITABLE))
								toDelist.add(token);
						}
						
						for (SectorEntityToken token : toDelist) {
							board.makeUnavailableAt(cm, token);
						}
					}
				}
			}
			
			for (ConquestMission cm : toRemove) {
				board.removeMission(cm, true);
			}
		}
	}
}
