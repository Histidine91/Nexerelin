package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;


public class FactionInsuranceEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(FactionInsuranceEvent.class);
	
	private float paidAmount = 0f;
	private CampaignFleetAPI winner;
	private CampaignFleetAPI loser;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
	
	@Override
	public void reportBattleOccurred(CampaignFleetAPI winner, CampaignFleetAPI loser) {
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		CampaignFleetAPI fleet = null;
		
		if (winner == playerFleet) fleet = winner;
		else if (loser == playerFleet) fleet = loser;
		else return;
		
		float value = 0f;
		String stage = "report";
		
		String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
		//if (alignedFactionId.equals("player_npc")) return;  // no self-insurance
                if (!SectorManager.isFactionAlive(alignedFactionId)) return;
		FactionAPI alignedFaction = Global.getSector().getFaction(alignedFactionId);
		
		List<FleetMemberAPI> fleetCurrent = fleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
			if (!fleetCurrent.contains(member)) {
				value += member.getBaseSellValue();
			}
		}
		if (value <= 0) return;
		
		if (alignedFaction.isAtBest("player", RepLevel.SUSPICIOUS))
		{
			paidAmount = 0;
			stage = "report_unpaid";
		}
		else paidAmount = value * ExerelinConfig.playerInsuranceMult;
		
		MarketAPI closestMarket = ExerelinUtils.getClosestMarket(alignedFactionId);
		if (closestMarket != null)
		{
			Global.getSector().reportEventStage(this, stage, closestMarket.getPrimaryEntity(), MessagePriority.ENSURE_DELIVERY, new BaseOnMessageDeliveryScript() {
				public void beforeDelivery(CommMessageAPI message) {
					Global.getSector().getPlayerFleet().getCargo().getCredits().add(paidAmount);
				}
			});
		}
	}
	
	@Override
	public String getEventName() {
		return ("Ship loss insurance");
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
		map.put("$paid", "" + (int) paidAmount + Strings.C);
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