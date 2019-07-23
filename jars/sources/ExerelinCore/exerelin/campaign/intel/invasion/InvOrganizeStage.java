package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.intel.fleets.NexOrganizeStage;
import exerelin.utilities.StringHelper;

@Deprecated
public class InvOrganizeStage extends NexOrganizeStage {
	
	public InvOrganizeStage(InvasionIntel invasion, MarketAPI market, float durDays) {
		super(invasion, market, durDays);
	}
		
	@Override
	protected String getRaidString() {
		return StringHelper.getString("exerelin_invasion", "invasion");
	}
}