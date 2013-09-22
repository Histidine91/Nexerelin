package data.scripts.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;

public class ExerelinCoreCampaignPlugin extends BaseCampaignPlugin {

    @Override
    public String getId() {
        return null;
    }

    @Override
    public boolean isTransient() {
        return false;
    }

    @Override
    public PluginPick pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {

        if (interactionTarget instanceof OrbitalStationAPI) {
            return new PluginPick(new ExerelinOrbitalStationInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);  ///HERE IT IS. UsSStationInteractionDialogPlugin is my implementation of OrbitalStationInteractionDialogPlugin
        }

        if (interactionTarget instanceof CampaignFleetAPI) {
            return new PluginPick(new ExerelinFleetInteractionDialogPluginImpl(), PickPriority.MOD_GENERAL);
        }

        return null;
    }
}