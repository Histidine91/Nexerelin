package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.NexConfig;

public class Nex_BlackMarketPlugin extends BlackMarketPlugin {
	
	@Override
	public void updateCargoPrePlayerInteraction() {
		boolean okToUpdate = okToUpdateShipsAndWeapons();
		super.updateCargoPrePlayerInteraction();
		if (NexConfig.doubleSubmarketWeapons && okToUpdate) {
			
			// this was already done in super method, so what we're doing is doubling weapon/fighter counts
			boolean military = Misc.isMilitary(market);

			WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<String>();
			factionPicker.add(market.getFactionId(), 15f - market.getStabilityValue());
			factionPicker.add(Factions.INDEPENDENT, 4f);
			factionPicker.add(submarket.getFaction().getId(), 6f);

			int weapons = 6 + Math.max(0, market.getSize() - 1) + (military ? 5 : 0);
			int fighters = 2 + Math.max(0, (market.getSize() - 3) / 2) + (military ? 2 : 0);
			weapons = 6 + Math.max(0, market.getSize() - 1);
			fighters = 2 + Math.max(0, (market.getSize() - 3) / 2);
			
			addWeapons(weapons, weapons + 2, 3, factionPicker);
			addFighters(fighters, fighters + 2, 3, factionPicker);

			if (military) {
				weapons = market.getSize();
				fighters = Math.max(1, market.getSize() / 3);
				addWeapons(weapons, weapons + 2, 3, market.getFactionId(), false);
				addFighters(fighters, fighters + 2, 3, market.getFactionId());
			}
			
			getCargo().sort();
		}
	}
}
