package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.OrganizeStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.utilities.StringHelper;

public class InvOrganizeStage extends OrganizeStage {
	
	public InvOrganizeStage(RaidIntel invasion, MarketAPI market, float durDays) {
		super(invasion, market, durDays);
	}
	
	@Override
	protected String getForcesString() {
		return StringHelper.getString("exerelin_invasion", "intelOrganizeGetForcesString");
	}
	
	@Override
	protected String getRaidString() {
		return StringHelper.getString("exerelin_invasion", "invasion");
	}
}