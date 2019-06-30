package exerelin.campaign.submarkets;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.impl.campaign.submarkets.BlackMarketPlugin;
import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsFaction;

// TODO: should also sell relevant package blueprints
public class Nex_MilitarySubmarketPlugin extends MilitarySubmarketPlugin {
	
	// same as vanilla except sells more weapons/fighters
	// TODO: not same anymore
	/*
	@Override
	public void updateCargoPrePlayerInteraction() {
		float seconds = Global.getSector().getClock().convertToSeconds(sinceLastCargoUpdate);
		addAndRemoveStockpiledResources(seconds, false, true, true);
		sinceLastCargoUpdate = 0f;
		
		if (okToUpdateShipsAndWeapons()) {
			sinceSWUpdate = 0f;
			
			pruneWeapons(0f);
			addWeapons(12, 16, 3, submarket.getFaction().getId());
			addFighters(6, 8, 3, market.getFactionId());

			float stability = market.getStabilityValue();
			float sMult = Math.max(0.1f, stability / 10f);
			getCargo().getMothballedShips().clear();
			addShips(submarket.getFaction().getId(),
					200f * sMult, // combat
					15f, // freighter 
					10f, // tanker
					20f, // transport
					10f, // liner
					10f, // utilityPts
					null, // qualityOverride
					0f, // qualityMod
					null,
					null);
				
			addHullMods(4, 2 + itemGenRandom.nextInt(4));
		}
		
		getCargo().sort();
	}
	*/
	
	@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
		super.reportPlayerMarketTransaction(transaction);
		
		FactionAPI faction = submarket.getFaction();
		BlackMarketPlugin.delayedLearnBlueprintsFromTransaction(faction, getCargo(), 
				transaction, 60f + 60 * (float) Math.random());
	}
	
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
