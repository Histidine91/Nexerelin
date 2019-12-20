package exerelin.campaign.intel;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionManager;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;

public class Nex_HegemonyInspectionManager extends HegemonyInspectionManager {
	
	// Hegemony won't inspect if player is member or allied
	@Override
	protected void checkInspection() {
		
		if (!SectorManager.isFactionAlive(Factions.HEGEMONY))
			return;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (AllianceManager.areFactionsAllied(playerFactionId, Factions.HEGEMONY))
			return;
		
		super.checkInspection();
	}
	
	@Override
	public void createInspection() {
		super.createInspection();
		if (intel != null && ExerelinConfig.autoResistAIInspections) {
			intel.setOrders(HegemonyInspectionIntel.AntiInspectionOrders.RESIST);
		}
	}

}
