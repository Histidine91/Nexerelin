package exerelin.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import exerelin.campaign.events.MarketAttackedEvent;
import java.util.Map;

public class MarketAttackedCondition extends BaseMarketConditionPlugin {
	private MarketAttackedEvent event = null;
	
	@Override
	public void apply(String id) {
		market.getStability().modifyFlat(id, -1 * event.getStabilityPenalty(), "Market attacked");
	}
		
	@Override
	public void unapply(String id) {
		market.getStability().unmodify(id);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> tokens = super.getTokenReplacements();

		int penalty = event.getStabilityPenalty();
		tokens.put("$stabilityPenalty", "" + penalty);

		return tokens;
	}
	
	@Override
		public void setParam(Object param) {
		event = (MarketAttackedEvent) param;
	}
	
	@Override
		public String[] getHighlights() {
		return new String[] {"" + event.getStabilityPenalty() };
	}
}
