package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.InvasionFleetManager.EventType;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

public class ConquestMissionManager extends BaseEventManager {

	public static final String KEY = "nex_ConquestMissionManager";
	public static final int MIN_PLAYER_LEVEL = 10;
	public static Logger log = Global.getLogger(ConquestMissionManager.class);
	
	public static ConquestMissionManager getInstance() {
		Object test = Global.getSector().getPersistentData().get(KEY);
		return (ConquestMissionManager) test; 
	}
	
	public ConquestMissionManager() {
		super();
		Global.getSector().getPersistentData().put(KEY, this);
	}
	
	@Override
	protected int getMinConcurrent() {
		return 0;
	}
	
	@Override
	protected int getMaxConcurrent() {
		/*
		int numWars = getNumWars();
		log.info("Number of ongoing wars: " + numWars);
		int max = numWars/2;
		if (max < 1) max = 1;
		else if (max > 3) max = 3;
		return max;
		*/
		return 2;
	}

	@Override
	protected float getIntervalRateMult() {
		return Global.getSettings().getFloat("nex_conquestMissionIntervalRateMult");
	}

	@Override
	protected EveryFrameScript createEvent() {
		if (Global.getSector().getPlayerStats().getLevel() < MIN_PLAYER_LEVEL)
			return null;

		if (!NexConfig.enableHostileFleetEvents) return null;
		if (!NexConfig.enableInvasions)	return null;
		
		log.info("Attempting to create conquest mission event");
		if ((float) Math.random() < 0.75f) return null;
		
		FactionAPI faction = pickSourceFaction();
		if (faction == null) {
			log.info("Failed to pick source faction");
			return null;
		}
		
		MarketAPI target = InvasionFleetManager.getManager().getTargetMarketForFleet(
				faction, null, null, Global.getSector().getEconomy().getMarketsCopy(), 
				EventType.INVASION);
		if (target == null) {
			log.info("Failed to pick target market");
			return null;
		} 
		else if (target.getFaction().isPlayerFaction() || target.getFaction() == Misc.getCommissionFaction()) 
		{
			log.info("Target market belongs to player, retry later");
			return null;
		}
		
		float duration = getDuration(target);
		float currReward = ConquestMissionIntel.calculateReward(target, true);
		if (currReward <= 0) return null;
		
		ConquestMissionIntel intel = new ConquestMissionIntel(target, faction, duration);
		intel.init();
		if (intel.isDone()) intel = null;
		
		log.info("Intel successfully created");
		return intel;
	}
	
	public static float getDuration(MarketAPI target) {
		return 90 + target.getSize() * 20;
	}
	
	protected int getNumWars() {
		int numWars = 0;
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		for (String factionId : liveFactions)
		{
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, 
					NexConfig.allowPirateInvasions, true, false);
			numWars += enemies.size();
		}
		numWars = numWars/2;	// since the preceding loop counts both ends
		return numWars;
	}
	
	public void cleanup() {
		active.clear();
	}
	
	protected FactionAPI pickSourceFaction() {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		for (Pair<String, Float> result : getNumWeightedWarsByFaction()) {
			picker.add(result.one, result.two);
		}
		String factionId = picker.pick();
		if (factionId == null) return null;
		return Global.getSector().getFaction(factionId);
	}
	
	protected List<Pair<String, Float>> getNumWeightedWarsByFaction()
	{
		List<Pair<String, Float>> atWar = new ArrayList<>();
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		FactionAPI player = Global.getSector().getPlayerFaction();
		
		// Wars with factions not hostile to player count only 25% as much
		// If the faction is hostile to player, its war count score is halved
		
		for (String factionId : liveFactions)
		{
			if (factionId.equals(Factions.PLAYER)) continue;
			if (!NexConfig.getFactionConfig(factionId).canInvade) continue;
			if (!NexConfig.allowPirateInvasions && NexUtilsFaction.isPirateFaction(factionId))
				continue;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, NexConfig.allowPirateInvasions, true, false);
			if (enemies.isEmpty()) continue;
			
			float count = 0;
			for (String enemyId : enemies) {
				if (player.isHostileTo(enemyId))
					count += 1;
				else
					count += 0.25f;
			}
			
			if (player.isHostileTo(factionId))
				count *= 0.5f;
			
			atWar.add(new Pair<>(factionId, count));
		}
		return atWar;
	}
}
