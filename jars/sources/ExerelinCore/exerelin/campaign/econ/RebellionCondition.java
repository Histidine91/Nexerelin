package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import exerelin.campaign.events.RebellionEvent;
import exerelin.utilities.StringHelper;
import java.util.Map;

public class RebellionCondition extends BaseMarketConditionPlugin {
	private RebellionEvent event = null;
	
	@Override
	public void apply(String id) {
		// FIXME: diagnose the underlying issue!
		if (event == null)	// try regetting
		{
			Global.getLogger(this.getClass()).info("ERROR: Event is null, re-fetching");
			event = (RebellionEvent)Global.getSector().getEventManager().getOngoingEvent(new CampaignEventTarget(market), "nex_rebellion");
		}
		if (event == null) return;
		market.getStability().modifyFlat(id, -1 * event.getStabilityPenalty(), 
				StringHelper.getString("exerelin_marketConditions", "rebellion"));
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
		RebellionEvent.addFactionNameTokens(tokens, "rebel", Global.getSector().getFaction(event.getRebelFactionId()));

		return tokens;
	}
	
	@Override
	public void setParam(Object param) {
		Global.getLogger(this.getClass()).info("Setting param, " + (param != null && param instanceof RebellionEvent));
		event = (RebellionEvent) param;
	}
	
	@Override
		public String[] getHighlights() {
		return new String[] {
			Global.getSector().getFaction(event.getRebelFactionId()).getDisplayNameWithArticle(),
			"" + event.getStabilityPenalty()
		};
	}
}
