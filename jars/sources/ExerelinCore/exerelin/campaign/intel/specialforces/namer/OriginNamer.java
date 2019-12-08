package exerelin.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Names the special forces fleet for the market it spawned from, e.g. "13th Jangala".
 */
public class OriginNamer extends PlanetNamer {
	
	@Override
	protected MarketAPI getMarket(FactionAPI faction) {
		return null;	// let it default to origin
	}
}
