package exerelin.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;

public interface SpecialForcesNamer {
	
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander);
	
}
