package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class Nex_BlackMarketPlugin extends BlackMarketPlugin {
	
	@Override
	public void updateCargoPrePlayerInteraction() {
		if (okToUpdateShipsAndWeapons()) {
			super.updateCargoPrePlayerInteraction();
			// this was already done in super method, so what we're doing is doubling weapon/fighter counts
			int weapons = 4 + Math.max(0, market.getSize() - 3) + (Misc.isMilitary(market) ? 5 : 0);
			int fighters = 2 + Math.max(0, (market.getSize() - 3) / 2) + (Misc.isMilitary(market) ? 2 : 0);

			// just kidding, it's only 50% more
			//weapons /= 2;
			//fighters /= 2;

			WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<String>();
			factionPicker.add(market.getFactionId(), 15f - market.getStabilityValue());
			factionPicker.add(Factions.INDEPENDENT, 4f);
			factionPicker.add(submarket.getFaction().getId(), 6f);

			addWeapons(weapons, weapons + 1, 3, factionPicker);
			addFighters(fighters, fighters + 1, 3, factionPicker);
			
			getCargo().sort();
		}
		else {
			super.updateCargoPrePlayerInteraction();
		}
	}
}
