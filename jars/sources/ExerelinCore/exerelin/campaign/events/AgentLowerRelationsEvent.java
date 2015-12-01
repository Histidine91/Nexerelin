package exerelin.campaign.events;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinReputationAdjustmentResult;


public class AgentLowerRelationsEvent extends CovertOpsEventBase {

	public static Logger log = Global.getLogger(AgentLowerRelationsEvent.class);
	
		protected float repEffect2;
		protected ExerelinReputationAdjustmentResult repResult2;
		protected FactionAPI thirdFaction;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		repEffect2 = 0;
		thirdFaction = null;
	}
	
	@Override
	public void setParam(Object param) {
		super.setParam(param);
		thirdFaction = (FactionAPI)params.get("thirdFaction");
		if (params.containsKey("repEffect2"))
		{
			repEffect2 = (Float)params.get("repEffect2");
			repResult2 = (ExerelinReputationAdjustmentResult)params.get("repResult2");
		}
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		String thirdFactionStr = thirdFaction.getEntityNamePrefix();
		String theThirdFactionStr = thirdFaction.getDisplayNameWithArticle();
		map.put("$thirdFaction", thirdFactionStr);
		map.put("$theThirdFaction", theThirdFactionStr);
		map.put("$ThirdFaction", Misc.ucFirst(thirdFactionStr));
		map.put("$TheThirdFaction", Misc.ucFirst(theThirdFactionStr));
		
		// need to distinguish between agentFaction-thirdFaction and faction-thirdFaction relations
		map.put("$repEffectAbs", "" + (int)Math.ceil(Math.abs(repEffect*100f)));
		map.put("$repEffectAbs2", "" + (int)Math.ceil(Math.abs(repEffect2*100f)));
		map.put("$newRelationStr", getNewRelationStr(agentFaction, faction));
		if (result.isSucessful())
			map.put("$newRelationStr2", getNewRelationStr(faction, thirdFaction));
		else
		{
			map.put("$newRelationStr2", getNewRelationStr(agentFaction, thirdFaction));
		}
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> highlights = new ArrayList<>();
		if (!result.isSucessful() && result.isDetected())
		{
			addTokensToList(highlights, "$repEffectAbs");
			addTokensToList(highlights, "$newRelationStr");
		}
		addTokensToList(highlights, "$repEffectAbs2");
		addTokensToList(highlights, "$newRelationStr2");
		return highlights.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		Color colorRepEffect = repEffect > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		Color colorRepEffect2 = repEffect2 > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		Color colorNew = agentFaction.getRelColor(faction.getId());
		Color colorNew2 = Color.WHITE;
		if (result.isSucessful())
		{
			colorNew2 = faction.getRelColor(thirdFaction.getId());
			return new Color[] {colorRepEffect2, colorNew2};
		}
		else if (result.isDetected())
		{
			colorNew2 = agentFaction.getRelColor(thirdFaction.getId());
		}
		return new Color[] {colorRepEffect, colorNew, colorRepEffect2, colorNew2};
	}
	
	@Override
	public String getCurrentMessageIcon() {
		int significance = 0;
		if (!result.isSucessful() || result.isDetected()) significance = 1;
		if (repResult.isHostile != repResult.wasHostile) significance = 2;
		if (repResult2 != null)
			if (repResult2.isHostile != repResult2.wasHostile) significance = 2;
		return EVENT_ICONS[significance];
	}
}