package exerelin.world.scenarios;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;

@Deprecated
public class DerelictEmpireFleetInteractionConfigGen implements FleetInteractionDialogPluginImpl.FIDConfigGen {
	@Override
	public FleetInteractionDialogPluginImpl.FIDConfig createConfig() {
		FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
		//config.showTransponderStatus = false;
		config.delegate = new FleetInteractionDialogPluginImpl.FIDDelegate() {
			public void postPlayerSalvageGeneration(InteractionDialogAPI dialog, FleetEncounterContext context, CargoAPI salvage) {
				
			}
			public void notifyLeave(InteractionDialogAPI dialog) {
			}
			public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
				bcc.aiRetreatAllowed = false;
				//bcc.objectivesAllowed = false;
				bcc.enemyDeployAll = true;
			}
		};
		return config;
	}
	
}