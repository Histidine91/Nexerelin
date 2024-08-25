package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.rulecmd.ShowDefaultVisual;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.battle.NexFleetInteractionDialogPluginImpl;
import exerelin.utilities.ReflectionUtils;
import exerelin.utilities.StringHelper;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Code to forcibly join fleets that should join our battle but don't in vanilla {@code FleetInteractionDialogPluginImpl}.<br/>
 * Normally {@code NexFleetInteractionDialogPluginImpl} would take care of this, but it may not be running if another fleet interaction dialog plugin is taking over.
 */
@Log4j
public class BattleForceJoinHelper implements EveryFrameScript {

    protected boolean wantDialogCheck = false;
    protected boolean wasInDialog = false;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return;
        BattleAPI battle = player.getBattle();

        InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
        boolean inBattle = dialog != null && battle != null && dialog.getPlugin() instanceof FleetInteractionDialogPluginImpl
                && !(dialog.getPlugin() instanceof NexFleetInteractionDialogPluginImpl);
        if (!inBattle) {
            wasInDialog = false;
            return;
        }
        CampaignFleetAPI actualOther = (CampaignFleetAPI) dialog.getInteractionTarget();

        if (actualOther == null) return;

        // check during the first frame that we're in the dialog and weren't before
        if (!wasInDialog) {
            wantDialogCheck = true;
        }

        if (!wantDialogCheck) return;

        //dialog.getTextPanel().addPara("[temp] Running force-join script");

        List<CampaignFleetAPI> fleetsToJoin = getFollowingFleets(actualOther);
        if (fleetsToJoin.isEmpty()) {
            endCheck();
            return;
        }
        BattleAPI.BattleSide playerSide = battle.pickSide(player);
        FleetInteractionDialogPluginImpl fidpi = (FleetInteractionDialogPluginImpl)dialog.getPlugin();
        FIDConfig config = (FIDConfig)ReflectionUtils.getIncludingSuperclasses("config", fidpi, fidpi.getClass());
        if (config == null) {
            endCheck();
            return;
        }

        for (CampaignFleetAPI fleet : fleetsToJoin) {
            forceJoinFleet(fleet, battle, playerSide, dialog, config);
        }
        battle.genCombined();
        if (!FleetInteractionDialogPluginImpl.inConversation) {
            new ShowDefaultVisual().execute(null, dialog, null, null);
            //ReflectionUtils.invokeIncludingSuperclasses("showFleetInfo", fidpi, fidpi.getClass(), new Object[0], true);
        }
        int numFleets = fleetsToJoin.size();
        dialog.getTextPanel().addPara(StringHelper.getString("exerelin_fleets", "forceJoinMsg"), Misc.getHighlightColor(), "" + numFleets);

        endCheck();
    }

    public void endCheck() {
        wasInDialog = true;
        wantDialogCheck = false;
    }

    protected void forceJoinFleet(CampaignFleetAPI fleet, BattleAPI battle, BattleAPI.BattleSide playerSide, InteractionDialogAPI dialog,
                                  FIDConfig config) {
        BattleAPI.BattleSide joiningSide = battle.pickSide(fleet, true);
        if (!config.pullInAllies && joiningSide == playerSide) return;
        if (!config.pullInEnemies && joiningSide != playerSide) return;

        battle.join(fleet);
        fleet.inflateIfNeeded();

        FleetInteractionDialogPluginImpl fidpi = (FleetInteractionDialogPluginImpl)dialog.getPlugin();
        List<CampaignFleetAPI> pulledIn = new ArrayList<>();
        pulledIn = (List<CampaignFleetAPI>)ReflectionUtils.getIncludingSuperclasses("pulledIn", fidpi, fidpi.getClass());
        pulledIn.add(fleet);
    }

    protected List<CampaignFleetAPI> getFollowingFleets(CampaignFleetAPI actualOther) {
        List<CampaignFleetAPI> fleets = new ArrayList<>();
        CampaignFleetAPI player = Global.getSector().getPlayerFleet();
        if (player == null) return fleets;
        BattleAPI battle = player.getBattle();
        if (battle == null) return fleets;

        for (CampaignFleetAPI fleet : player.getContainingLocation().getFleets()) {
            if (fleet.getBattle() == player.getBattle()) continue;
            if (NexFleetInteractionDialogPluginImpl.shouldPullInFleet(battle, fleet, player, actualOther)) {
                fleets.add(fleet);
            }
        }
        return fleets;
    }
}
