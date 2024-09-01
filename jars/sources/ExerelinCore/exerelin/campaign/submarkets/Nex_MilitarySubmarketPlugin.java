package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.NexConfig;

public class Nex_MilitarySubmarketPlugin extends MilitarySubmarketPlugin {
	
	@Override
	public void updateCargoPrePlayerInteraction() {
		boolean okToUpdate = okToUpdateShipsAndWeapons();
		super.updateCargoPrePlayerInteraction();
		if (NexConfig.doubleSubmarketWeapons && okToUpdate) {
			super.updateCargoPrePlayerInteraction();
			// this was already done in super method, so what we're doing is doubling weapon/fighter counts
			int weapons = 7 + Math.max(0, market.getSize() - 1) * 2;
			int fighters = 2 + Math.max(0, market.getSize() - 3);
			
			addWeapons(weapons, weapons + 2, 3, submarket.getFaction().getId());
			addFighters(fighters, fighters + 2, 3, market.getFactionId());
			
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
		String commissionFaction = Misc.getCommissionFactionId();
		if (commissionFaction != null && AllianceManager.areFactionsAllied(commissionFaction, submarket.getFaction().getId())) {
			return true;
		}
		if (AllianceManager.areFactionsAllied(PlayerFactionStore.getPlayerFactionId(), submarket.getFaction().getId())) {
			return true;
		}
		return submarket.getFaction().getId().equals(commissionFaction);
	}
}
