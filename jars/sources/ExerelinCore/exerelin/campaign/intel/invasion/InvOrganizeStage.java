package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.utilities.StringHelper;

public class InvOrganizeStage extends OrganizeStage {
	
	public InvOrganizeStage(InvasionIntel invasion, MarketAPI market, float durDays) {
		super(invasion, market, durDays);
	}
	
	@Override
	protected String getForcesString() {
		return "The invasion force";
	}
	
	@Override
	protected String getRaidString() {
		return StringHelper.getString("exerelin_invasion", "invasion");
	}
}