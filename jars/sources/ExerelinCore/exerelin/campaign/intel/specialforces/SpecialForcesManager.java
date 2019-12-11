package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.lazywizard.lazylib.MathUtils;

/**
 * Generates special forces fleets for factions.
 */
public class SpecialForcesManager implements EveryFrameScript {
	
	public static final String PERSISTENT_KEY = "nex_specialForcesManager";
	public static final float MAX_POINTS = 350;
	public static final float POINTS_TO_SPAWN = 300;
	public static final float POINT_GENERATION_MULT = 0.005f;
	
	protected Map<String, Float> factionPoints = new HashMap<>();
	protected final List<SpecialForcesIntel> activeIntel = new LinkedList();
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	// TODO
	public int getMaxFleets(String factionId) {
		return ExerelinConfig.getExerelinFactionConfig(factionId).specialForcesMaxFleets;
	}
	
	@Override
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		interval.advance(days);
		if (interval.intervalElapsed()) {
			cleanupDoneIntel();
			processPoints(interval.getElapsed());
		}
	}
	
	protected void cleanupDoneIntel() {
		List<SpecialForcesIntel> remove = new LinkedList();
		for (SpecialForcesIntel intel : activeIntel) {
			if (intel.isEnded() || intel.isEnding()) {
				remove.add(intel);
			}
		}
		this.activeIntel.removeAll(remove);
	}
	
	// runcode Console.showMessage(exerelin.campaign.intel.specialforces.SpecialForcesManager.getPointsPerDay("persean") + "");
	public static float getPointsPerDay(String factionId) {
		List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(factionId);
		float totalPoints = 0;
		for (MarketAPI market : markets)
		{
			float points = InvasionFleetManager.getMarketInvasionCommodityValue(market);
			points *= POINT_GENERATION_MULT;
			ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
			points *= conf.specialForcesPointMult;
			
			totalPoints += points;
		}
		return totalPoints;
	}
	
	/**
	 * Increments SF points for each faction based on its markets, approximately once per ingame day.
	 * @param days
	 */
	protected void processPoints(float days) 
	{
		if (Global.getSector().isInNewGameAdvance()) return;
		
		Set<String> liveFactions = new HashSet<>(SectorManager.getLiveFactionIdsCopy());
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		
		for (MarketAPI market : markets)
		{
			String factionId = market.getFactionId();
			if (!liveFactions.contains(factionId)) continue;
			
			float points = InvasionFleetManager.getMarketInvasionCommodityValue(market);
			points *= days * POINT_GENERATION_MULT * ExerelinConfig.specialForcesPointMult;
			ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
			points *= conf.specialForcesPointMult;
			
			incrementPoints(factionId, points);
		}
		
		// spawn fleets if needed
		for (String factionId : liveFactions) {
			float points = getPoints(factionId);
			if (points >= POINTS_TO_SPAWN && countActiveFleetsForFaction(factionId) < getMaxFleets(factionId)) 
			{
				SpecialForcesIntel intel = generateFleet(factionId);
				if (intel != null) {
					activeIntel.add(intel);
					incrementPoints(factionId, -POINTS_TO_SPAWN);
				}
			}
		}
	}
	
	/**
	 * Start a special forces intel event once the faction accumulates enough points.
	 * @param factionId
	 * @return
	 */
	public SpecialForcesIntel generateFleet(String factionId) 
	{
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		FactionAPI faction = Global.getSector().getFaction(factionId);
		for (MarketAPI market : Misc.getFactionMarkets(faction)) {
			if (market.isHidden()) continue;
			if (!market.hasSpaceport()) continue;
			if (!market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY))
				continue;
			
			picker.add(market, market.getSize() * market.getSize());
		}
		
		MarketAPI origin = picker.pick();
		if (origin == null) {
			Global.getLogger(this.getClass()).info("No market found to spawn special task force for " + factionId);
			return null;
		}
		
		float fp = POINTS_TO_SPAWN * MathUtils.getRandomNumberInRange(0.95f, 1.05f);
		SpecialForcesIntel intel = new SpecialForcesIntel(origin, faction, fp);
		intel.init(null);
		return intel;
	}
	
	protected int countActiveFleetsForFaction(String factionId) {
		int count = 0;
		FactionAPI faction = Global.getSector().getFaction(factionId);
		for (SpecialForcesIntel intel : activeIntel) {
			if (intel.getFaction() == faction)
				count++;
		}
		return count;
	}
	
	protected void incrementPoints(String factionId, float points) {
		if (!factionPoints.containsKey(factionId))
			factionPoints.put(factionId, 0f);

		float currPoints = factionPoints.get(factionId);
		float newPoints = Math.min(currPoints + points, MAX_POINTS);
		factionPoints.put(factionId, newPoints);
	}
	
	public Float getPoints(String factionId) {
		Float result = factionPoints.get(factionId);
		return result == null ? 0 : result;
	}
	
	public static SpecialForcesManager getManager() {
		return (SpecialForcesManager)Global.getSector().getPersistentData().get(PERSISTENT_KEY);
	}
	
	// this exists because else it'd be a leak in constructor
	public void init() {
		Global.getSector().getPersistentData().put(PERSISTENT_KEY, this);
		Global.getSector().addScript(this);
	}
	
	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}	
}
