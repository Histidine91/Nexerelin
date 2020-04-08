package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.OpenMarketPlugin;
import com.fs.starfarer.api.util.Misc;

public class Nex_OpenMarketPlugin extends OpenMarketPlugin {
	
	@Override
	public void updateCargoPrePlayerInteraction() {
		if (okToUpdateShipsAndWeapons()) {
			super.updateCargoPrePlayerInteraction();
			// this was already done in super method, so what we're doing is doubling weapon/fighter counts
			int weapons = 2 + Math.max(0, market.getSize() - 3) + (Misc.isMilitary(market) ? 5 : 0);
			int fighters = 1 + Math.max(0, (market.getSize() - 3) / 2) + (Misc.isMilitary(market) ? 2 : 0);

			// just kidding, it's only 50% more
			//weapons /= 2;
			//fighters /= 2;

			addWeapons(weapons, weapons + 1, 0, market.getFactionId());
			addFighters(fighters, fighters + 1, 0, market.getFactionId());
			
			getCargo().sort();
		}
		else {
			super.updateCargoPrePlayerInteraction();
		}
	}
}
