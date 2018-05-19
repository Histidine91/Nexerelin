package exerelin.campaign.events;

import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.List;


public class InvasionFleetEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(InvasionFleetEvent.class);
	protected MarketAPI target = null;
	protected int dp = 0;
	protected float killPoints = 0;
	protected CampaignFleetAPI fleet;
	public boolean done = false;
	protected FactionAPI factionPermanent;	// doesn't change if the origin market gets captured in the meantime
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		factionPermanent = eventTarget.getFaction();
	}
	
	@Override
	public void setParam(Object param) {
		Map<String, Object> params = (HashMap)param;
		target = (MarketAPI)params.get("target");
		dp = (int)(float)params.get("dp");
	}
		
	@Override
	public void startEvent()
	{
		
	}
	
	public void setFleet(CampaignFleetAPI fleet)
	{
		this.fleet = fleet;
	}
	
	public void reportStart()
	{
		MessagePriority priority = MessagePriority.SECTOR;
		String stage = "start";
		if (faction.getId().equals(PlayerFactionStore.getPlayerFactionId()))
			stage = "start_player";
		Global.getSector().reportEventStage(this, stage, market.getPrimaryEntity(), priority);
	}
	
	public void endEvent(FleetReturnReason reason, SectorEntityToken reportSource)
	{
		if (done) return;
		done = true;
		String stage = reason.toString().toLowerCase();
		if (faction.getId().equals(PlayerFactionStore.getPlayerFactionId()) || faction.getId().equals(ExerelinConstants.PLAYER_NPC_ID))
			stage += "_player";
		
		log.info("Ending invasion event: " + stage);
		fleet = null;
		
		OnMessageDeliveryScript script = null;
		MessagePriority priority = MessagePriority.SECTOR;
		
		// fleet was driven back due in full or part to player?
		if (reason == FleetReturnReason.SHIP_LOSSES && killPoints > 0)
		{
			script = new BaseOnMessageDeliveryScript() {
				public void beforeDelivery(CommMessageAPI message) {
					if (killPoints > 0) {
						Global.getSector().adjustPlayerReputation(
								new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.SYSTEM_BOUNTY_REWARD, 
										killPoints, message, true), faction.getId());
					}
				}
			};
			priority = MessagePriority.ENSURE_DELIVERY;
		}
		
		Global.getSector().reportEventStage(this, stage, reportSource, priority, script);
	}
	
	@Override
	public void reportBattleFinished(CampaignFleetAPI primaryWinner, BattleAPI battle) {
		if (fleet == null) return;
		if (!battle.isPlayerInvolved()) return;
		if (!battle.isInvolved(fleet)) return;
		if (battle.isOnPlayerSide(fleet)) return;
		
		for (FleetMemberAPI loss : Misc.getSnapshotMembersLost(fleet)) {
			killPoints += loss.getFleetPointCost();
		}
	}
	
	@Override
	public void advance(float amount)
	{

	}
	
	@Override
	public String getEventName() {
		return StringHelper.getStringAndSubstituteToken("exerelin_events", "invasionFleet", 
				"$faction", Misc.ucFirst(faction.getDisplayName()));
	}
	
	@Override
	public String getCurrentImage() {
		return faction.getLogo();
	}
			
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		
		addFactionNameTokens(map, "", factionPermanent);
		addFactionNameTokens(map, "the", factionPermanent);
		
		FactionAPI targetFaction = target.getFaction();
		LocationAPI loc = target.getContainingLocation();
		String locName = loc.getName();
		if (loc instanceof StarSystemAPI)
			locName = "the " + ((StarSystemAPI)loc).getName();
		int dpEstimate = Math.round(dp/10f) * 10;
		
		String targetFactionStr = ExerelinUtilsFaction.getFactionShortName(targetFaction);
		String theTargetFactionStr = targetFaction.getDisplayNameWithArticle();
		map.put("$sender", ExerelinUtilsFaction.getFactionShortName(faction));
		map.put("$target", target.getName());
		map.put("$targetLocation", locName);
		map.put("$targetFaction", targetFactionStr);
		map.put("$TargetFaction", Misc.ucFirst(targetFactionStr));
		map.put("$theTargetFaction", theTargetFactionStr);
		map.put("$TheTargetFaction", Misc.ucFirst(theTargetFactionStr));
		map.put("$dp", "" + dpEstimate);
		return map;
	}
		
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$dp");
		return result.toArray(new String[0]);
	}
	
	@Override
	public boolean isDone() {
		return done;
	}

	@Override
	public boolean allowMultipleOngoingForSameTarget() {
		return true;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
	
	public static enum FleetReturnReason {
		MISSION_COMPLETE, ALREADY_CAPTURED, NO_LONGER_HOSTILE, MARINE_LOSSES, SHIP_LOSSES, OTHER
	}
}