package exerelin.campaign.intel;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
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

public class ConquestMissionManager extends BaseEventManager {

	public static final String KEY = "nex_ConquestMissionManager";
	
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
		return Math.max(getNumWars()/2, 1);
	}

	@Override
	protected EveryFrameScript createEvent() {
		if ((float) Math.random() < 0.75f) return null;
		
		FactionAPI faction = pickSourceFaction();
		if (faction == null) return null;
		
		MarketAPI target = InvasionFleetManager.getManager().getTargetMarketForFleet(
				faction, null, null, Global.getSector().getEconomy().getMarketsCopy());
		if (target == null) return null;
		
		float duration = 45 + target.getSize() * 10;
		
		ConquestMissionIntel intel = new ConquestMissionIntel(target, faction, duration);
		if (intel.isDone()) intel = null;

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
