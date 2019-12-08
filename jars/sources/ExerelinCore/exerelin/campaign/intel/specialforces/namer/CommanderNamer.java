package exerelin.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.utilities.StringHelper;

/**
 * Names the special forces fleet as "[commander]'s Fleet".
 */
public class CommanderNamer implements SpecialForcesNamer {

	@Override
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
		String lastName = commander.getName().getLast();
		String fleetName = lastName;
		int length = lastName.length();
		if (lastName.substring(length-1, length).equals("s"))
			fleetName += "'";
		else
			fleetName += "'s";
		fleetName += " " + StringHelper.getString("fleet", true);
		return fleetName;
	}
}
