package exerelin.campaign.events.covertops;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;


public class CovertOpsEventBase extends BaseEventPlugin {

	public static Logger log = Global.getLogger(CovertOpsEventBase.class);
	public static final String[] EVENT_ICONS = new String[]{
		"graphics/exerelin/icons/intel/spy4.png",
		"graphics/exerelin/icons/intel/spy4_amber.png",
		"graphics/exerelin/icons/intel/spy4_red.png"
	};	
	protected static final int DAYS_TO_KEEP = 45;
	
	protected FactionAPI agentFaction = null;
	protected CovertActionResult result = CovertActionResult.SUCCESS;
	protected boolean playerInvolved = false;
	protected float age = 0;
	protected ExerelinReputationAdjustmentResult repResult;
	protected Map<String, Object> params;	// FIXME left in for reverse compatibility
		
	protected boolean done = false;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
	}
	
	@Override
	public void setParam(Object param) {
		Map<String, Object> params = (HashMap)param;
		agentFaction = (FactionAPI)params.get("agentFaction");
		result = (CovertActionResult)params.get("result");
		playerInvolved = (Boolean)params.get("playerInvolved");
		repResult = (ExerelinReputationAdjustmentResult)params.get("repResult");
	}
	
	// trash values that don't need saving
	protected Object writeReplace() {
		agentFaction = null;
		result = null;
		repResult = null;
		params = null;
		return this;
	}
		
	@Override
	public void advance(float amount)
	{
		if (done)
		{
			return;
		}
		age = age + Global.getSector().getClock().convertToDays(amount);
		if (age > DAYS_TO_KEEP)
		{
			done = true;
			return;
		}
	}
	
	@Override
	public void startEvent()
	{
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;	//MessagePriority.ENSURE_DELIVERY;
		String reportStage = result.name().toLowerCase();
		if (playerInvolved) reportStage += "_player";
		Global.getSector().reportEventStage(this, reportStage, market.getPrimaryEntity(), priority);
		log.info("Covert warfare event: " + reportStage);
	}

	@Override
	public String getEventName() {
		String name = StringHelper.getString("exerelin_events", "covertOps");
		name = StringHelper.substituteToken(name, "$agentFaction", agentFaction.getDisplayName());
		name = StringHelper.substituteToken(name, "$targetFaction", faction.getDisplayName());
		return Misc.ucFirst(name);
	}
	
	/*
	@Override
	public String getCurrentImage() {
		return newOwner.getLogo();
	}

	@Override
	public String getCurrentMessageIcon() {
		return newOwner.getLogo();
	}
	*/
		
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	
		
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		addFactionNameTokens(map, "agent", agentFaction);
		
		map.put("$repEffectAbs", "" + (int)Math.ceil(Math.abs(repResult.delta*100f)));
		map.put("$newRelationStr", NexUtilsReputation.getRelationStr(agentFaction, faction));
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$repEffectAbs");
		addTokensToList(result, "$newRelationStr");
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		Color colorRepEffect = repResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		Color colorNew = agentFaction.getRelColor(faction.getId());
		return new Color[] {colorRepEffect, colorNew};
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
	
	@Override
	public String getCurrentMessageIcon() {
		int significance = 0;
		if (!result.isSucessful() || result.isDetected()) significance = 1;
		if (repResult.wasHostile && !repResult.isHostile) significance = 1;
		if (repResult.isHostile && !repResult.wasHostile) significance = 2;
		return EVENT_ICONS[significance];
	}
}