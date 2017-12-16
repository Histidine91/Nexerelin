package exerelin.campaign.events.covertops;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstigateRebellionEvent extends CovertOpsEventBase {
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$timeFrame", (String)params.get("timeFrame") + "");
		
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		addTokensToList(result, "$repEffectAbs");
		addTokensToList(result, "$newRelationStr");
		addTokensToList(result, "$timeFrame");
		
		return result.toArray(new String[0]);
	}
}
