package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;

// Tweaked version of from RemnantFleetInteractionConfigGen
public class RemnantRaidFleetInteractionConfigGen implements FleetInteractionDialogPluginImpl.FIDConfigGen {
	@Override
	public FIDConfig createConfig() {
		FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
		//config.showTransponderStatus = false;
		config.delegate = new FleetInteractionDialogPluginImpl.FIDDelegate() {
			
			// salvage is handled elsewhere
			public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
				
			}
			public void notifyLeave(InteractionDialogAPI dialog) {
			}
			public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
				bcc.aiRetreatAllowed = false;
				//bcc.objectivesAllowed = false;
			}
		};
		return config;
	}
	
}
