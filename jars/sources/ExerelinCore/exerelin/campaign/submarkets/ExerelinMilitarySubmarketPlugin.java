package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;

public class ExerelinMilitarySubmarketPlugin extends MilitarySubmarketPlugin {
	@Override
	protected boolean hasCommission() {
		String commissionFaction = Misc.getCommissionFaction();
		if (commissionFaction != null && AllianceManager.areFactionsAllied(commissionFaction, submarket.getFaction().getId())) {
			return true;
		}
		if (AllianceManager.areFactionsAllied(PlayerFactionStore.getPlayerFactionId(), submarket.getFaction().getId())) {
			return true;
		}
		return submarket.getFaction().getId().equals(commissionFaction);
	}
}
