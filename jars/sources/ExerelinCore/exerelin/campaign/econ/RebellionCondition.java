package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.utilities.StringHelper;
import java.util.Map;

public class RebellionCondition extends BaseMarketConditionPlugin {
	protected RebellionIntel event = null;
	
	@Override
	public void init(MarketAPI market, MarketConditionAPI condition) {
		super.init(market, condition);
		event = RebellionIntel.getOngoingEvent(market);
	}
	
	// FIXME: diagnose the underlying issue!
	protected boolean refetchEventIfNeeded()
	{
		if (event == null)	// try regetting
		{
			Global.getLogger(this.getClass()).warn("Event is null, re-fetching");
			event = RebellionIntel.getOngoingEvent(market);
		}
		return event != null;
	}
	
	@Override
	public void apply(String id) {
		if (refetchEventIfNeeded())
			market.getStability().modifyFlat(id, -1 * event.getStabilityPenalty(), 
					StringHelper.getString("exerelin_marketConditions", "rebellion"));
		
		if (event == null)	// refetch failed
			market.removeSpecificCondition(this.getModId());
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
			//RebellionIntel.addFactionNameTokens(tokens, "rebel", 
			//		Global.getSector().getFaction(event.getRebelFactionId()));
		}
		

		return tokens;
	}
	
	@Override
	public void setParam(Object param) {
		Global.getLogger(this.getClass()).info("Setting param, " + (param != null && param instanceof RebellionIntel));
		event = (RebellionIntel) param;
	}
	
	@Override
	public String[] getHighlights() {
		return new String[] {
			//Global.getSector().getFaction(event.getRebelFactionId()).getDisplayNameWithArticle(),
			"" + event.getStabilityPenalty()
		};
	}
}
