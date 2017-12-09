package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;


public class FollowersTaxEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FollowersTaxEvent.class);
	protected static float HARD_MODE_MULT = 0.5f;
	
	protected int month;
	protected int day;
	protected float revenue = 0f;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		Global.getSector().getPersistentData().put("taxClock", Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
		month = Global.getSector().getClock().getMonth();
		day = Global.getSector().getClock().getDay();
	}
	
	protected float getMarketDailyRevenue(MarketAPI market)
	{
		float base = ExerelinConfig.followersBaseTax;
		float sizeMult = (float)Math.pow(2, market.getSize() - 4);
		float stabilityMult = 0.2f + 0.8f * market.getStabilityValue()/6;
		
		float thisRev = base * sizeMult * stabilityMult;
		
		return (int)(thisRev + 0.5f);
	}
	
	public void addDailyRevenue()
	{
		List<MarketAPI> markets = ExerelinUtilsFaction.getFactionMarkets(ExerelinConstants.PLAYER_NPC_ID);
		for (MarketAPI market : markets)
		{
			revenue += getMarketDailyRevenue(market);
		}
	}
	
	@Override
	public void advance(float amount) {
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		if (playerFleet == null) {
			return;
		}
		CampaignClockAPI clock = Global.getSector().getClock();
		if (clock.getDay() != day)
		{
			day = clock.getDay();
			addDailyRevenue();
		}
		
		if (day == 1 && clock.getMonth() != month) {
			month = Global.getSector().getClock().getMonth();
			String stage = "report";
			
			if (revenue == 0)
				return;
			
			if (!SectorManager.isFactionAlive(ExerelinConstants.PLAYER_NPC_ID)) 
				return;
			
			FactionAPI alignedFaction = Global.getSector().getFaction(ExerelinConstants.PLAYER_NPC_ID);

			RepLevel relation = alignedFaction.getRelationshipLevel(Factions.PLAYER);
			if (alignedFaction.isAtBest(Factions.PLAYER, RepLevel.SUSPICIOUS))
			{
				revenue = 0;
				stage = "report_unpaid";
			}
			else if (relation == RepLevel.NEUTRAL)
				revenue *= 0.25f;
			else if (relation == RepLevel.FAVORABLE)
				revenue *= 0.5f;
			else if (relation == RepLevel.WELCOMING)
				revenue *= 0.75f;

			if (SectorManager.getHardMode())
				revenue *= HARD_MODE_MULT; 
			
			playerFleet.getCargo().getCredits().add(revenue);
			Global.getSector().reportEventStage(this, stage, playerFleet, MessagePriority.DELIVER_IMMEDIATELY);
			Global.getSector().getPersistentData().put("taxClock", 
					Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
			
			revenue = 0;
		}
	}

	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_events", "followersTax");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		CampaignClockAPI previous = (CampaignClockAPI) Global.getSector().getPersistentData().get("taxClock");
		if (previous != null) {
			map.put("$date", previous.getMonthString() + ", c." + previous.getCycle());
		}
		FactionAPI faction = Global.getSector().getFaction(ExerelinConstants.PLAYER_NPC_ID);
		String factionName = ExerelinUtilsFaction.getFactionShortName(faction);
		String theFactionName = faction.getDisplayNameLongWithArticle();
		map.put("$sender", factionName);
		map.put("$faction", factionName);
		map.put("$Faction", Misc.ucFirst(factionName));
		map.put("$theFaction", theFactionName);
		map.put("$TheFaction", Misc.ucFirst(theFactionName));
		map.put("$paid", Misc.getWithDGS((int)revenue) + Strings.C);
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$paid");
		return result.toArray(new String[0]);
	}
	
	@Override
	public String getCurrentImage() {
		FactionAPI myFaction = Global.getSector().getFaction(ExerelinConstants.PLAYER_NPC_ID);
		return myFaction.getLogo();
	}
	
	@Override
	public boolean isDone() {
		return false;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}