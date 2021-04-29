package exerelin.utilities;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;

public interface ColonyNPCHostileActListener {
	
	void reportNPCGenericRaid(MarketAPI market, MarketCMD.TempData actionData);
	void reportNPCIndustryRaid(MarketAPI market, MarketCMD.TempData actionData, Industry industry);
	
	void reportNPCTacticalBombardment(MarketAPI market, MarketCMD.TempData actionData);
	void reportNPCSaturationBombardment(MarketAPI market, MarketCMD.TempData actionData);
	
}
