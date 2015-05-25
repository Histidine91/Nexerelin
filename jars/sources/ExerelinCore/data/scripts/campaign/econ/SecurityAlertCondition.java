package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import exerelin.campaign.events.SecurityAlertEvent;
import java.util.Map;

@Deprecated
public class SecurityAlertCondition extends BaseMarketConditionPlugin {
	private SecurityAlertEvent event = null;
	
	@Override
	public void apply(String id) {
		
	}
		
	@Override
	public void unapply(String id) {
		market.getStability().unmodify(id);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> tokens = super.getTokenReplacements();

		float alertLevel = event.getAlertLevel();
		tokens.put("$alertLevel", "" + alertLevel);

		return tokens;
	}
	
	@Override
		public void setParam(Object param) {
		event = (SecurityAlertEvent) param;
	}
	
	@Override
		public String[] getHighlights() {
		return new String[] {"" + event.getAlertLevel() };
	}
}
