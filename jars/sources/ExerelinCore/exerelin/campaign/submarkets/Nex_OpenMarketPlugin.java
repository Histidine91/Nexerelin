package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexConfig;

public class Nex_OpenMarketPlugin extends OpenMarketPlugin {
	
	@Override
	public void updateCargoPrePlayerInteraction() {
		boolean okToUpdate = okToUpdateShipsAndWeapons();
		super.updateCargoPrePlayerInteraction();
		if (NexConfig.doubleSubmarketWeapons && okToUpdate) {
			super.updateCargoPrePlayerInteraction();
			// this was already done in super method, so what we're doing is doubling weapon/fighter counts
			int weapons = 5 + Math.max(0, market.getSize() - 1) + (Misc.isMilitary(market) ? 5 : 0);
			int fighters = 1 + Math.max(0, (market.getSize() - 3) / 2) + (Misc.isMilitary(market) ? 2 : 0);
			
			addWeapons(weapons, weapons + 2, 0, market.getFactionId());
			addFighters(fighters, fighters + 2, 0, market.getFactionId());
			
			getCargo().sort();
		}
	}
}
