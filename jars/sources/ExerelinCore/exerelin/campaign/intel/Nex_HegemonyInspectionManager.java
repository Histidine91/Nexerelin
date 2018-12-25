package exerelin.campaign.intel;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionManager;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;

public class Nex_HegemonyInspectionManager extends HegemonyInspectionManager {
	
	// Hegemony won't inspect if player is member or allied
	@Override
	protected void checkInspection() {
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (AllianceManager.areFactionsAllied(playerFactionId, Factions.HEGEMONY))
			return;
		
		super.checkInspection();
	}
}
