package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import exerelin.campaign.fleets.InvasionFleetManager;
import static exerelin.campaign.fleets.InvasionFleetManager.DEFENDER_STRENGTH_MARINE_MULT;
import static exerelin.campaign.fleets.InvasionFleetManager.EXCEPTION_LIST;
import static exerelin.campaign.fleets.InvasionFleetManager.spawnInvasionFleet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lwjgl.util.vector.Vector2f;

public class RevengeanceFleetEvent extends BaseEventPlugin {

	public static final float POINTS_TO_SPAWN = 100;
	
	public static Logger log = Global.getLogger(RevengeanceFleetEvent.class);
	
	private boolean ended = false;
	private final IntervalUtil interval = new IntervalUtil(1f, 1f);
	
	float points = 0;

	public void addPoints(float addedPoints)
	{
		if (!isRevengeanceEnabled()) return;
		
		if (!SectorManager.getHardMode())
			addedPoints *= 0.5f;
		points += addedPoints;
		String debugStr = "Adding revengeance points: " + addedPoints;
		log.info(debugStr);
		//if (Global.getSettings().isDevMode())
		//{
		//	Global.getSector().getCampaignUI().addMessage(debugStr);
		//}
		if (points >= POINTS_TO_SPAWN)
		{
			boolean success = generateRevengeanceFleet();
			if (success) points -= POINTS_TO_SPAWN;
		}
	}

	@Override
	public String getCurrentImage() {
		return faction.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return faction.getCrest();
	}

	@Override
	public CampaignEventCategory getEventCategory() {
		return CampaignEventCategory.EVENT;
	}

	@Override
	public String getEventIcon() {
		return faction.getCrest();
	}

	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_fleets", "revengeanceFleetEvent");
	}
	
	@Override
	public void init(String eventType, CampaignEventTarget eventTarget) {
		super.init(eventType, eventTarget, false);
	}

	@Override
	public boolean isDone() {
		return ended;
	}
	
	public void endEvent()
	{
		ended = true;
	}
	
	// because for some reason ReportBattleFinished isn't being called, have StatsTracker call this instead
	public void reportBattle(CampaignFleetAPI winner, BattleAPI battle)
	{
		if (!battle.isPlayerInvolved()) return;
		
		List<CampaignFleetAPI> killedFleets = battle.getNonPlayerSide();

		float involvedFraction = battle.getPlayerInvolvementFraction();

		float recentFpKilled = 0;
		
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(playerAlignedFactionId, 
				ExerelinConfig.allowPirateInvasions, true, false);
		
		for (CampaignFleetAPI killedFleet : killedFleets)
		{
			String factionId = killedFleet.getFaction().getId();
			if (!enemies.contains(factionId)) continue;
			
			List<FleetMemberAPI> killCurrent = killedFleet.getFleetData().getMembersListCopy();
			for (FleetMemberAPI member : killedFleet.getFleetData().getSnapshot()) {
				if (!killCurrent.contains(member)) {
					recentFpKilled += member.getFleetPointCost();
				}
			}
		}
		
		recentFpKilled *= involvedFraction;
		float points = recentFpKilled * ExerelinConfig.revengePointsPerEnemyFP;
		if (true)	//(points > 0)
			addPoints(points);
	}
	
	public static boolean isRevengeanceEnabled()
	{
		int requiredSetting = 2;
		if (SectorManager.getHardMode()) requiredSetting = 1;
		log.info("Required revengeance setting: " + requiredSetting);
		if (requiredSetting <= ExerelinConfig.enableRevengeFleets) {
			return true;
		}
		return false;
	}
	
	protected boolean generateRevengeanceFleet()
	{
		SectorAPI sector = Global.getSector();
		List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
		WeightedRandomPicker<String> attackerPicker = new WeightedRandomPicker();
		WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
		WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
		
		String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
		
		List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(playerAlignedFactionId, 
				ExerelinConfig.allowPirateInvasions, true, false);
		if (enemies.isEmpty()) return false;
		
		for (String enemyId : enemies)
		{
			// only allow Templars to send revengeance fleet if we have no other enemies
			if (enemyId.equals("templars") && enemies.size() > 1)
				continue;
			attackerPicker.add(enemyId);
		}
		String revengeFactionId = attackerPicker.pick();
		if (revengeFactionId == null || revengeFactionId.isEmpty())
			return false;
		FactionAPI revengeFaction = Global.getSector().getFaction(revengeFactionId);
		
		// pick a source market
		for (MarketAPI market : markets) {
			if (market.hasCondition(Conditions.ABANDONED_STATION)) continue;
			if (market.hasCondition(Conditions.DECIVILIZED)) continue;
			if (market.getPrimaryEntity() instanceof CampaignFleetAPI) continue;
			if  ( market.getFactionId().equals(revengeFactionId) && 
				( (market.hasCondition(Conditions.SPACEPORT)) || (market.hasCondition(Conditions.ORBITAL_STATION)) || (market.hasCondition(Conditions.MILITARY_BASE))
					|| (market.hasCondition(Conditions.REGIONAL_CAPITAL)) || (market.hasCondition(Conditions.HEADQUARTERS))
				) && market.getSize() >= 3 )
			{
				//marineStockpile = market.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();
				//if (marineStockpile < MIN_MARINE_STOCKPILE_FOR_INVASION)
				//		continue;
				float weight = 1;   //marineStockpile;
				if (market.hasCondition(Conditions.MILITARY_BASE)) {
					weight *= 1.4F;
				}
				if (market.hasCondition(Conditions.ORBITAL_STATION)) {
					weight *= 1.15F;
				}
				if (market.hasCondition(Conditions.SPACEPORT)) {
					weight *= 1.35F;
				}
				if (market.hasCondition(Conditions.HEADQUARTERS)) {
					weight *= 1.3F;
				}
				if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) {
					weight *= 1.1F;
				}
				weight *= 0.5f + (0.5f * market.getSize() * market.getStabilityValue());
				sourcePicker.add(market, weight);
			}
		}
		MarketAPI originMarket = sourcePicker.pick();
		if (originMarket == null) {
			return false;
		}
		//log.info("\tStaging from " + originMarket.getName());
		//marineStockpile = originMarket.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();

		// now we pick a target
		Vector2f originMarketLoc = originMarket.getLocationInHyperspace();
		for (MarketAPI market : markets) 
		{
			FactionAPI marketFaction = market.getFaction();
			if (EXCEPTION_LIST.contains(marketFaction.getId())) continue;
			if (!marketFaction.getId().equals(playerAlignedFactionId))
				continue;
			

			if (!ExerelinUtilsMarket.isValidInvasionTarget(market, 0)) continue;
			/*
			float defenderStrength = InvasionRound.GetDefenderStrength(market);
			float estimateMarinesRequired = defenderStrength * 1.2f;
			if (estimateMarinesRequired > marineStockpile * MAX_MARINE_STOCKPILE_TO_DEPLOY)
				continue;   // too strong for us
			*/
			float dist = Misc.getDistance(market.getLocationInHyperspace(), originMarketLoc);
			if (dist < 5000.0F) {
				dist = 5000.0F;
			}
			
			float weight = 20000.0F / dist;
			// prefer high value targets
			if (market.hasCondition(Conditions.MILITARY_BASE)) {
				weight *= 1.25F;
			}
			if (market.hasCondition(Conditions.HEADQUARTERS)) {
				weight *= 1.5F;
			}
			if (market.hasCondition(Conditions.REGIONAL_CAPITAL)) {
				weight *= 1.2F;
			}

			//weight *= market.getSize() * market.getStabilityValue();	// try to go after high value targets
			targetPicker.add(market, weight);
		}
		MarketAPI targetMarket = targetPicker.pick();
		if (targetMarket == null) {
			return false;
		}
		//log.info("\tTarget: " + targetMarket.getName());

		// spawn our revengeance fleet
		String debugStr = "Spawning revengeance fleet for " + revengeFactionId + "; source " + originMarket.getName() + "; target " + targetMarket.getName();
		log.info(debugStr);
		if (Global.getSettings().isDevMode())
		{
			//Global.getSector().getCampaignUI().addMessage(debugStr);
		}
		
		InvasionFleetManager.InvasionFleetData data = spawnInvasionFleet(revengeFaction, originMarket, targetMarket, DEFENDER_STRENGTH_MARINE_MULT, 1.5f, false);
		Map<String, Object> params = new HashMap<>();
		params.put("target", targetMarket);
		params.put("dp", data.startingFleetPoints);
		InvasionFleetEvent event = (InvasionFleetEvent)Global.getSector().getEventManager().startEvent(new CampaignEventTarget(originMarket), "exerelin_invasion_fleet", params);
		data.event = event;
		event.reportStart();
		
		return true;
	}
	
	public static RevengeanceFleetEvent getOngoingEvent()
	{
		CampaignEventPlugin eventSuper = Global.getSector().getEventManager().getOngoingEvent(null, "exerelin_revengeance_fleet");
		if (eventSuper != null) 
		{
			RevengeanceFleetEvent event = (RevengeanceFleetEvent)eventSuper;
			return event;
		}
		return null;
	}
}
