package exerelin.campaign.events;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.CovertOpsManager.CovertActionResult;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.PlayerFactionStore;


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
	protected float repEffect = 0;	// between agent faction and target faction
	protected float age = 0;
	protected Map<String, Object> params;
	protected ExerelinReputationAdjustmentResult repResult;
		
	protected boolean done = false;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		params = new HashMap<>();
	}
	
	@Override
	public void setParam(Object param) {
		params = (HashMap)param;
		agentFaction = (FactionAPI)params.get("agentFaction");
		if (params.containsKey("repEffect"))
			repEffect = (Float)params.get("repEffect");
		result = (CovertActionResult)params.get("result");
		playerInvolved = (Boolean)params.get("playerInvolved");
		repResult = (ExerelinReputationAdjustmentResult)params.get("repResult");
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
		MessagePriority priority = MessagePriority.DELIVER_IMMEDIATELY;
		String reportStage = result.name().toLowerCase();
		if (playerInvolved) reportStage += "_player";
		Global.getSector().reportEventStage(this, reportStage, market.getPrimaryEntity(), priority);
		log.info("Covert warfare event: " + reportStage);
	}

	@Override
	public String getEventName() {
		return (Misc.ucFirst(agentFaction.getEntityNamePrefix()) + " covert action against " + faction.getEntityNamePrefix());
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
	
	protected String getNewRelationStr(FactionAPI faction1, FactionAPI faction2)
	{
		RepLevel level = faction1.getRelationshipLevel(faction2.getId());
		int repInt = (int) Math.ceil((faction1.getRelationship(faction2.getId())) * 100f);
		
		String standing = "" + repInt + "/100" + " (" + level.getDisplayName().toLowerCase() + ")";
		return standing;
	}
		
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		String agentFactionStr = agentFaction.getEntityNamePrefix();
		String theAgentFactionStr = agentFaction.getDisplayNameWithArticle();
		
		map.put("$agentFaction", agentFactionStr);
		map.put("$theAgentFaction", theAgentFactionStr);
		map.put("$AgentFaction", Misc.ucFirst(agentFactionStr));
		map.put("$TheAgentFaction", Misc.ucFirst(theAgentFactionStr));
		
		map.put("$repEffectAbs", "" + (int)Math.ceil(Math.abs(repEffect*100f)));
		map.put("$newRelationStr", getNewRelationStr(agentFaction, faction));		return map;
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
		Color colorRepEffect = repEffect > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
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
		if (repResult.isHostile && !repResult.wasHostile) significance = 2;
		log.info("wololo, Icon: " + EVENT_ICONS[significance]);
		return EVENT_ICONS[significance];
	}
}