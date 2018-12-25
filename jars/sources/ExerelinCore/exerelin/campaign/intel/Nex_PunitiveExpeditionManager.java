package exerelin.campaign.intel;

import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;

public class Nex_PunitiveExpeditionManager extends PunitiveExpeditionManager {
	
	// don't send punitive expeditions against player if member or ally
	@Override
	protected void checkExpedition(PunExData curr) {
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (AllianceManager.areFactionsAllied(playerFactionId, curr.faction.getId()))
			return;
		
		super.checkExpedition(curr);
	}
}
