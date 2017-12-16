package exerelin.campaign.events.covertops;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.util.Misc;


public class SaboteurDestroyFoodEvent extends CovertOpsEventBase {

	public static Logger log = Global.getLogger(SaboteurDestroyFoodEvent.class);
	
        protected float foodDestroyed;
		
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
                foodDestroyed = 0;
	}
	
	@Override
	public void setParam(Object param) {
                super.setParam(param);
                if (params.containsKey("foodDestroyed"))
                    foodDestroyed = (Float)params.get("foodDestroyed");
	}
	        	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$foodDestroyed", "" + (int)foodDestroyed);
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
                if (foodDestroyed > 0)
                {
                    addTokensToList(result, "$foodDestroyed");
                }
                addTokensToList(result, "$repEffectAbs");
		addTokensToList(result, "$newRelationStr");
		return result.toArray(new String[0]);
	}
	
	@Override
	public Color[] getHighlightColors(String stageId) {
		Color colorRepEffect = repEffect > 0 ? Global.getSettings().getColor("textFriendColor") : Global.getSettings().getColor("textEnemyColor");
		Color colorNew = agentFaction.getRelColor(faction.getId());
		if (foodDestroyed > 0)
			return new Color[] {Misc.getHighlightColor(), colorRepEffect, colorNew};
		else return new Color[] {colorRepEffect, colorNew};
	}
}