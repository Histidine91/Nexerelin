package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMarket;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

/**
 * Generates special forces fleets for factions.
 */
@Log4j
public class SpecialForcesManager implements EveryFrameScript {
	
	public static final String PERSISTENT_KEY = "nex_specialForcesManager";
	public static final float MAX_POINTS = 300;
	public static final float POINTS_TO_SPAWN = 250;
	public static final float POINT_GENERATION_MULT = 0.1f;	// since we're now working with fleet pool points instead of invasion points
	public static final int RESPAWN_DELAY = 60;
	public static final float SIZE_MULT = 0.6f;
	public static final String MEM_KEY_RESPAWN_DELAY = "$nex_specialForcesRespawnDelay";
	
	@Getter protected Map<String, Float> factionPoints = new HashMap<>();
	//@Getter protected Map<StarSystemAPI, Float> hostileActivityContrib = new HashMap<>();
	protected final List<SpecialForcesIntel> activeIntel = new LinkedList();
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	
	// TODO: maybe also limit to number of markets
	public int getMaxFleets(String factionId) {
		return NexConfig.getFactionConfig(factionId).specialForcesMaxFleets;
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
	
	// runcode Console.showMessage(exerelin.campaign.intel.specialforces.SpecialForcesManager.getManager().getFactionPoints().get("independent") + "");
	// runcode Console.showMessage(exerelin.campaign.intel.specialforces.SpecialForcesManager.getPointsPerDay("independent") + "");
	public static float getPointsPerDay(String factionId) {
		List<MarketAPI> markets = NexUtilsFaction.getFactionMarkets(factionId);
		float totalPoints = 0;
		for (MarketAPI market : markets)
		{
			float points = FleetPoolManager.getMarketCommodityValueStatic(market);
			points *= POINT_GENERATION_MULT;
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
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
		
		Set<String> factions = SectorManager.getManager().getPresentFactionIdsCopy();
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		
		for (MarketAPI market : markets)
		{
			String factionId = market.getFactionId();
			if (!factions.contains(factionId)) continue;
			
			float points = FleetPoolManager.getMarketCommodityValueStatic(market);
			points *= days * POINT_GENERATION_MULT * NexConfig.specialForcesPointMult;
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			points *= conf.specialForcesPointMult;
			
			incrementPoints(factionId, points);
		}
		
		// spawn fleets if needed
		for (String factionId : factions) {
			float points = getPoints(factionId);
			if (points >= POINTS_TO_SPAWN && countActiveFleetsForFaction(factionId) < getMaxFleets(factionId)) 
			{
				if (Global.getSector().getFaction(factionId).getMemoryWithoutUpdate().contains(MEM_KEY_RESPAWN_DELAY))
					continue;

				SpecialForcesIntel intel = generateFleet(factionId);
				if (intel != null) {
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
		
		// don't spawn special forces fleet if player is hostile and in-system
		LocationAPI playerLoc = null;
		if (faction.isHostileTo(Factions.PLAYER)) {
			playerLoc = Global.getSector().getPlayerFleet().getContainingLocation();
		}
		
		for (MarketAPI market : Misc.getFactionMarkets(faction)) {
			if (market.isHidden()) continue;
			if (!NexUtilsMarket.hasWorkingSpaceport(market)) continue;
			if (!market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY))
				continue;
			if (market.getContainingLocation() == playerLoc)
				continue;
			
			picker.add(market, market.getSize() * market.getSize());
		}
		
		MarketAPI origin = picker.pick();
		if (origin == null) {
			//log.info("No market found to spawn special task force for " + factionId);
			return null;
		}
		
		float fp = POINTS_TO_SPAWN * MathUtils.getRandomNumberInRange(0.95f, 1.05f) * SIZE_MULT;
		log.info("Generating special forces intel for faction " + factionId);
		try {
			SpecialForcesIntel intel = new SpecialForcesIntel(origin, faction, fp);
			FleetPoolManager.getManager().modifyPool(factionId, -fp);
			intel.init(null);
			return intel;
		} catch (Exception ex) {
			Global.getSector().getCampaignUI().addMessage("Failed to generate special task group for faction " + factionId + ", please report with log",
					Misc.getNegativeHighlightColor());
		}
		return null;
	}
	
	public List<SpecialForcesIntel> getActiveIntelCopy() {
		return new LinkedList<>(activeIntel);
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
		//Global.getLogger(this.getClass()).info("Adding " + points + " points for faction " + factionId);
		factionPoints.put(factionId, newPoints);
	}
	
	public Float getPoints(String factionId) {
		Float result = factionPoints.get(factionId);
		return result == null ? 0 : result;
	}

	public void registerIntel(SpecialForcesIntel intel) {
		activeIntel.add(intel);
	}

	public void deregisterIntel(SpecialForcesIntel intel) {
		activeIntel.remove(intel);
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
	
	// runcode exerelin.campaign.intel.specialforces.SpecialForcesManager.getManager().spawnDebug("luddic_church");
	public void spawnDebug(String factionId) {
		SpecialForcesIntel intel = generateFleet(factionId);
	}
}
