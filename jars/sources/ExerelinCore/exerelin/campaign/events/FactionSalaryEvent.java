package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;


public class FactionSalaryEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionSalaryEvent.class);
	protected static float HARD_MODE_MULT = 0.5f;
	
	private int month;
	private float paidAmount = 0f;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		Global.getSector().getPersistentData().put("salariesClock", Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
		month = Global.getSector().getClock().getMonth();
	}
	
	@Override
	public void advance(float amount) {
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		if (playerFleet == null) {
			return;
		}
		CampaignClockAPI clock = Global.getSector().getClock();
		if (clock.getDay() == 1 && clock.getMonth() != month) {
			month = Global.getSector().getClock().getMonth();
			int level = Global.getSector().getPlayerPerson().getStats().getLevel();
			String stage = "report";
			paidAmount = ExerelinConfig.playerBaseSalary + ExerelinConfig.playerSalaryIncrementPerLevel * (level - 1);
			if (paidAmount == 0)
				return;

			String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
			//if (alignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;  // no self-salary
			
			// Exi is not technically alive in Corvus mode, but still treated as present due to Tasserus
			if (ExerelinUtilsFaction.isExiInCorvus(alignedFactionId))
			{
				// do nothing
			}
			else if (!SectorManager.isFactionAlive(alignedFactionId)) 
				return;
			
			FactionAPI alignedFaction = Global.getSector().getFaction(alignedFactionId);

			RepLevel relation = alignedFaction.getRelationshipLevel("player");
			if (alignedFaction.isAtBest("player", RepLevel.SUSPICIOUS))
			{
				paidAmount = 0;
				stage = "report_unpaid";
			}
			else if (relation == RepLevel.NEUTRAL)
				paidAmount *= 0.25f;
			else if (relation == RepLevel.FAVORABLE)
				paidAmount *= 0.5f;
			else if (relation == RepLevel.WELCOMING)
				paidAmount *= 0.75f;

			if (SectorManager.getHardMode())
				paidAmount *= HARD_MODE_MULT; 
			
			playerFleet.getCargo().getCredits().add(paidAmount);
			Global.getSector().reportEventStage(this, stage, playerFleet, MessagePriority.DELIVER_IMMEDIATELY);
			Global.getSector().getPersistentData().put("salariesClock", Global.getSector().getClock().createClock(Global.getSector().getClock().getTimestamp()));
		}
	}

	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_events", "factionSalary");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		CampaignClockAPI previous = (CampaignClockAPI) Global.getSector().getPersistentData().get("salariesClock");
		if (previous != null) {
			map.put("$date", previous.getMonthString() + ", c." + previous.getCycle());
		}
		FactionAPI faction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
		String factionName = faction.getEntityNamePrefix();
		String theFactionName = faction.getDisplayNameLongWithArticle();
		map.put("$sender", factionName);
		map.put("$employer", factionName);
		map.put("$Employer", Misc.ucFirst(factionName));
		map.put("$theEmployer", theFactionName);
		map.put("$TheEmployer", Misc.ucFirst(theFactionName));
		map.put("$paid", Misc.getWithDGS((int)paidAmount) + Strings.C);
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
		FactionAPI myFaction = Global.getSector().getFaction(PlayerFactionStore.getPlayerFactionId());
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