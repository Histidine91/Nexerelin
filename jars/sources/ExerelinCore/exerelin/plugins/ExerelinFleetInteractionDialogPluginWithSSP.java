package exerelin.plugins;

import com.fs.starfarer.api.campaign.InteractionDialogAPI;

public class ExerelinFleetInteractionDialogPluginWithSSP extends ExerelinFleetInteractionDialogPlugin {
           
    @Override
    public void init(InteractionDialogAPI dialog) {
        context = new data.scripts.campaign.SSP_FleetEncounterContext();
        super.init(dialog);
    }
}
