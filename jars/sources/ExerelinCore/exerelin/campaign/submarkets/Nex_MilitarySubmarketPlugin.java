package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsFaction;

// TODO: should also sell relevant package blueprints
public class Nex_MilitarySubmarketPlugin extends MilitarySubmarketPlugin {
	
	@Override
	public void updateCargoPrePlayerInteraction() {
		super.updateCargoPrePlayerInteraction();
		if (okToUpdateShipsAndWeapons()) {
			// this was already done in super method, so what we're doing is doubling weapon/fighter counts
			int weapons = 4 + Math.max(0, market.getSize() - 3) * 2;
			int fighters = 2 + Math.max(0, market.getSize() - 3);

			// just kidding, it's only 50% more
			//weapons /= 2;
			//fighters /= 2;

			addWeapons(weapons, weapons + 1, 0, market.getFactionId());
			addFighters(fighters, fighters + 1, 0, market.getFactionId());
			
			getCargo().sort();
		}
	}
	
	// not now, Tiandong compatibility is more than I want to do atm
	/*
	@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
		super.reportPlayerMarketTransaction(transaction);
		
		FactionAPI faction = submarket.getFaction();
		BlackMarketPlugin.delayedLearnBlueprintsFromTransaction(faction, getCargo(), 
				transaction, 60f + 60 * (float) Math.random());
	}
	*/
	
	@Override
	protected boolean hasCommission() {
		String commissionFaction = ExerelinUtilsFaction.getCommissionFactionId();
		if (commissionFaction != null && AllianceManager.areFactionsAllied(commissionFaction, submarket.getFaction().getId())) {
			return true;
		}
		if (AllianceManager.areFactionsAllied(PlayerFactionStore.getPlayerFactionId(), submarket.getFaction().getId())) {
			return true;
		}
		return submarket.getFaction().getId().equals(commissionFaction);
	}
}
