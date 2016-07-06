package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;


public class FactionInsuranceEvent extends BaseEventPlugin {

	protected static final float HARD_MODE_MULT = 0.5f;
	
	public static Logger log = Global.getLogger(FactionInsuranceEvent.class);
	
	protected float paidAmount = 0f;
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
	
	@Override
	public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
	{
		if (!battle.isPlayerInvolved()) return;
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		
		float value = 0f;
		String stage = "report";
		
		String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
		//if (alignedFactionId.equals(ExerelinConstants.PLAYER_NPC_ID)) return;  // no self-insurance
		
		// Exi is not technically alive in Corvus mode, but still treated as present due to Tasserus
		if (ExerelinUtilsFaction.isExiInCorvus(alignedFactionId))
		{
			// do nothing
		}
		else if (!SectorManager.isFactionAlive(alignedFactionId)) 
			return;
		
		FactionAPI alignedFaction = Global.getSector().getFaction(alignedFactionId);
		
		List<FleetMemberAPI> fleetCurrent = fleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
			if (!fleetCurrent.contains(member)) {
				value += member.getBaseBuyValue();
			}
		}
		if (value <= 0) return;
		
		if (alignedFaction.isAtBest("player", RepLevel.SUSPICIOUS))
		{
			paidAmount = 0;
			stage = "report_unpaid";
		}
		else paidAmount = value * ExerelinConfig.playerInsuranceMult;
		
		if (SectorManager.getHardMode())
			paidAmount *= HARD_MODE_MULT; 
		
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
		return StringHelper.getString("exerelin_events", "insurance");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
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