package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import exerelin.campaign.events.MarketAttackedEvent;
import exerelin.utilities.StringHelper;
import java.util.Map;

public class MarketAttackedCondition extends BaseMarketConditionPlugin {
	protected MarketAttackedEvent event = null;
	
	// FIXME: diagnose the underlying issue!
	protected boolean refetchEventIfNeeded()
	{
		if (event == null)	// try regetting
		{
			Global.getLogger(this.getClass()).warn("Event is null, re-fetching");
			event = (MarketAttackedEvent)Global.getSector().getEventManager().
					getOngoingEvent(new CampaignEventTarget(market), "exerelin_market_attacked");
		}
		return event != null;
	}
	
	@Override
	public void apply(String id) {
		if (refetchEventIfNeeded())
			market.getStability().modifyFlat(id, -1 * event.getStabilityPenalty(), 
				StringHelper.getString("exerelin_marketConditions", "marketAttacked"));
	}
		
	@Override
	public void unapply(String id) {
		market.getStability().unmodify(id);
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> tokens = super.getTokenReplacements();
		
		if (refetchEventIfNeeded())
		{
			int penalty = event.getStabilityPenalty();
			tokens.put("$stabilityPenalty", "" + penalty);
		}

		return tokens;
	}
	
	@Override
	public void setParam(Object param) {
		Global.getLogger(this.getClass()).info("Setting param, " + (param != null && param instanceof MarketAttackedEvent));
		event = (MarketAttackedEvent) param;
	}
	
	@Override
		public String[] getHighlights() {
		return new String[] {"" + event.getStabilityPenalty() };
	}
}
