package exerelin.campaign.events.covertops;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.Misc;
import java.util.HashMap;


public class SaboteurSabotageReserveEvent extends CovertOpsEventBase {

	public static Logger log = Global.getLogger(SaboteurSabotageReserveEvent.class);
	
	protected float reserveDamage;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		reserveDamage = 0;
	}
	
	@Override
	public void setParam(Object param) {
		super.setParam(param);
		Map<String, Object> params = (Map<String, Object>)param;
		if (params.containsKey("reserveDamage"))
			reserveDamage = (Float)params.get("reserveDamage");
	}
				
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$reserveDamage", "" + (int)reserveDamage);
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		if (reserveDamage > 0)
		{
			//log.info("Reserve damage: " + reserveDamage);
			addTokensToList(result, "$reserveDamage");
		}
		addTokensToList(result, "$repEffectAbs");
		addTokensToList(result, "$newRelationStr");
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		Color colorRepEffect = repResult.delta > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		Color colorNew = agentFaction.getRelColor(faction.getId());
		if (reserveDamage > 0)
			return new Color[] {Misc.getHighlightColor(), colorRepEffect, colorNew};
		else return new Color[] {colorRepEffect, colorNew};
	}
}