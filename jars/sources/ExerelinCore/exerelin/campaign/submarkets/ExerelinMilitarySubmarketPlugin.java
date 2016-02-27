package exerelin.campaign.submarkets;

import com.fs.starfarer.api.impl.campaign.submarkets.MilitarySubmarketPlugin;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsFaction;

@Deprecated	// for now
public class ExerelinMilitarySubmarketPlugin extends MilitarySubmarketPlugin {
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
