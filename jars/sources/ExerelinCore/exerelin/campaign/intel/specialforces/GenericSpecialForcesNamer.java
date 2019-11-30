package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import org.lazywizard.lazylib.MathUtils;

public class GenericSpecialForcesNamer implements SpecialForcesNamer {

	@Override
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
		int num = MathUtils.getRandomNumberInRange(1, 33);
		String suffix = getSuffix(num);
		
		return num + suffix + " " + origin.getName();
	}
	
	
	protected String getSuffix(int num) {
		int lastDigit = num % 10;
		switch (lastDigit) {
			case 1:
				return "st";
			case 2:
				return "nd";
			case 3:
				return "rd";
			default:
				return "th";
		}
	}
}
