package exerelin.plugins;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.*;

@SuppressWarnings("unchecked")
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

        /*if (interactionTarget instanceof OrbitalStationAPI) {
            return new PluginPick(new ExerelinOrbitalStationInteractionDialogPluginImpl(), PickPriority.HIGHEST);   // Thanks Uomoz for guide :)
        }*/

        /*if (interactionTarget instanceof CampaignFleetAPI) {
            return new PluginPick(new ExerelinFleetInteractionDialogPluginImpl(), PickPriority.HIGHEST);
        }*/

        return null;
    }

    public PluginPick<BattleAutoresolverPlugin> pickBattleAutoresolverPlugin(SectorEntityToken one, SectorEntityToken two) {
        /*if (one instanceof CampaignFleetAPI && two instanceof CampaignFleetAPI) {
            return new PluginPick<BattleAutoresolverPlugin>(
                    new ExerelinBattleAutoresolverPluginImpl((CampaignFleetAPI) one, (CampaignFleetAPI) two),
                    PickPriority.HIGHEST
            );
        }*/
        return null;
    }
}