package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;

// same as vanilla one except accessible when player grants autonomy
public class Nex_LocalResourcesSubmarketPlugin extends LocalResourcesSubmarketPlugin {
	
	@Override
	public boolean isHidden() {
		return !market.isPlayerOwned() && !market.getFaction().isPlayerFaction();
	}
}
