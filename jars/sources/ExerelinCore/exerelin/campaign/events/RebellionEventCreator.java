package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

// Periodically starts rebellions on suitably restive planets
public class RebellionEventCreator extends BaseEventPlugin {
	
	public static final float REBELLION_POINT_MULT = 0.25f;
	public static final float HARD_MODE_MULT = 1.25f;
	public static final float HARD_MODE_STABILITY_MODIFIER = -1;
	
	Map<String, Float> rebellionPoints = new HashMap<>();
	protected IntervalUtil interval = new IntervalUtil(1,1);
	
	public static RebellionEvent createRebellion(MarketAPI market, String factionId, boolean report)
	{
		SectorAPI sector = Global.getSector();
		if (sector.getEventManager().isOngoing(new CampaignEventTarget(market), "nex_rebellion"))
			return null;
		
		float prepTime = market.getSize() * 2 * MathUtils.getRandomNumberInRange(0.8f, 1.2f);
		Map<String, Object> eventParams = new HashMap<>();
		eventParams.put("rebelFactionId", factionId);
		eventParams.put("delay", prepTime);
		RebellionEvent event = (RebellionEvent)sector.getEventManager().startEvent(new CampaignEventTarget(market), "nex_rebellion", eventParams);
		
		if (report)
			sector.reportEventStage(event, "before_start", market.getPrimaryEntity(), event.getPriority());
		
		return event;
	}
	
	protected static void addToListIfNotPresent(List<String> list, String toAdd)
	{
		if (list.contains(toAdd)) return;
		list.add(toAdd);
	}
	
	public static RebellionEvent createRebellion(MarketAPI market, boolean report)
	{
		if (Global.getSector().getEventManager().isOngoing(new CampaignEventTarget(market), "nex_rebellion"))
			return null;
		FactionAPI faction = market.getFaction();
		
		WeightedRandomPicker<String> enemyPicker = new WeightedRandomPicker<>();
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(market.getFaction(), true, false, false);
		if (faction.isHostileTo(Factions.INDEPENDENT))
			addToListIfNotPresent(enemies, Factions.INDEPENDENT);
		if (faction.isHostileTo(Factions.LUDDIC_PATH))
			addToListIfNotPresent(enemies, Factions.LUDDIC_PATH);
		
		for (String candidate : enemies)
		{
			float weight = 1;
			if (faction.isAtBest(candidate, RepLevel.VENGEFUL))
				weight += 2;
			if (ExerelinUtilsFaction.isPirateFaction(candidate))
				weight += 1;
			if (candidate.equals(Factions.INDEPENDENT))
				weight += 3;
			if (candidate.equals(Factions.LUDDIC_PATH) && market.hasCondition(Conditions.LUDDIC_MAJORITY))
				weight += 5;
			
			enemyPicker.add(candidate, weight);
		}
		
		String enemyId = enemyPicker.pick();
		if (enemyId == null)
			return null;
		return createRebellion(market, enemyId, report);
	}
	
	/**
	 * How many rebellion points should this market get today?
	 * @param market
	 * @return
	 */
	protected float getRebellionIncrement(MarketAPI market)
	{
		int stability = (int)market.getStabilityValue();
		int size = market.getSize();
		String factionId = market.getFactionId();
		boolean hardModePenalty = SectorManager.getHardMode() 
				&& (factionId.equals(PlayerFactionStore.getPlayerFactionId()) || factionId.equals(ExerelinConstants.PLAYER_NPC_ID));
		
		if (market.hasCondition(Conditions.DISSIDENT))
			stability -= 1;
		if (!ExerelinUtilsMarket.isWithOriginalOwner(market))
			stability -= 1;		
		if (hardModePenalty)
			stability -= HARD_MODE_STABILITY_MODIFIER;
		
		int requiredThreshold = Math.min(size - 1, 5);
		
		float points = (requiredThreshold - stability) * REBELLION_POINT_MULT;
		if (hardModePenalty && points > 0) points += HARD_MODE_MULT;
		return points;		
	}
	
	public float getRebellionPoints(MarketAPI market)
	{
		float points = 0;
		if (rebellionPoints.containsKey(market.getId()))
			points = rebellionPoints.get(market.getId());
		return points;
	}
	
	public static float getRebellionPointsStatic(MarketAPI market)
	{
		CampaignEventPlugin event = Global.getSector().getEventManager().getOngoingEvent(
					null, "nex_rebellion_creator");
		if (event != null)
			return ((RebellionEventCreator)event).getRebellionPoints(market);
		return 0;
	}
	
	protected void incrementRebellionPoints(MarketAPI market, float points)
	{
		String marketId = market.getId();
		float currPoints = getRebellionPoints(market);
		
		currPoints += points;
		if (currPoints <= 0)
		{
			rebellionPoints.remove(marketId);
			return;
		}
		if (currPoints >= 100)
		{
			createRebellion(market, true);
			currPoints = 0;
		}
		rebellionPoints.put(marketId, currPoints);
	}
	
	protected void processMarket(MarketAPI market, float days)
	{
		if (!ExerelinUtilsMarket.canBeInvaded(market, false))
			return;
		if (market.getFactionId().equals("templars"))
			return;
		if (market.hasCondition(Conditions.DECIVILIZED))
			return;
		
		if (ExerelinUtilsFaction.isPirateFaction(market.getFactionId()) && ExerelinUtilsMarket.isWithOriginalOwner(market))
			return;
		
		float points = getRebellionIncrement(market) * days;
		incrementRebellionPoints(market, points);
	}
	
	@Override
	public void advance(float amount) {
		ExerelinUtils.advanceIntervalDays(interval, amount);
		if (!interval.intervalElapsed()) return;
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			processMarket(market, interval.getElapsed());
		}
	}
	
	@Override
	public boolean isDone() {
		return false;
	}
	
	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
}
