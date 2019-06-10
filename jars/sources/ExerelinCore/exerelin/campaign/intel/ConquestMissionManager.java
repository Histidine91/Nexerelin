package exerelin.campaign.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseEventManager;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

public class ConquestMissionManager extends BaseEventManager {

	public static final String KEY = "nex_ConquestMissionManager";
	public static final int MIN_PLAYER_LEVEL = 25;
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
		
		log.info("Attempting to create conquest mission event");
		if ((float) Math.random() < 0.75f) return null;
		
		FactionAPI faction = pickSourceFaction();
		if (faction == null) {
			log.info("Failed to pick source faction");
			return null;
		}
		
		MarketAPI target = InvasionFleetManager.getManager().getTargetMarketForFleet(
				faction, null, null, Global.getSector().getEconomy().getMarketsCopy());
		if (target == null) {
			log.info("Failed to pick target market");
			return null;
		}
		
		float duration = 45 + target.getSize() * 10;
		
		ConquestMissionIntel intel = new ConquestMissionIntel(target, faction, duration);
		intel.init();
		if (intel.isDone()) intel = null;
		
		log.info("Intel successfully created");
		return intel;
	}
	
	protected int getNumWars() {
		int numWars = 0;
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		for (String factionId : liveFactions)
		{
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, 
					ExerelinConfig.allowPirateInvasions, true, false);
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
		for (Pair<String, Integer> result : getNumWarsByFaction()) {
			picker.add(result.one, result.two);
		}
		String factionId = picker.pick();
		if (factionId == null) return null;
		return Global.getSector().getFaction(factionId);
	}
	
	protected List<Pair<String, Integer>> getNumWarsByFaction()
	{
		List<Pair<String, Integer>> atWar = new ArrayList<>();
		List<String> liveFactions = SectorManager.getLiveFactionIdsCopy();
		for (String factionId : liveFactions)
		{
			if (!ExerelinConfig.allowPirateInvasions && ExerelinUtilsFaction.isPirateFaction(factionId))
				continue;
			List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, ExerelinConfig.allowPirateInvasions, true, false);
			if (!enemies.isEmpty())
				atWar.add(new Pair<>(factionId, enemies.size()));
		}
		return atWar;
	}
}
