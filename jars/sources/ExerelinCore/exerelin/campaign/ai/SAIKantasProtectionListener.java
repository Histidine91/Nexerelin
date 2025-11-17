package exerelin.campaign.ai;

import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.KantaCMD;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.ai.concern.StrategicConcern;

/**
 * Blocks pirate strategic AI from launching aggressive actions against player
 */
public class SAIKantasProtectionListener implements StrategicAIListener {

    @Override
    public void reportAIAdded(String factionId, StrategicAI ai) {

    }

    @Override
    public void reportAIRemoved(String factionId, StrategicAI ai) {

    }

    @Override
    public void reportStrategyMeetingHeld(StrategicAI ai) {

    }

    @Override
    public boolean allowConcern(StrategicAI ai, StrategicConcern concern) {
        return true;
    }

    @Override
    public void reportConcernAdded(StrategicAI ai, StrategicConcern concern) {

    }

    @Override
    public void reportConcernUpdated(StrategicAI ai, StrategicConcern concern) {

    }

    @Override
    public void reportConcernRemoved(StrategicAI ai, StrategicConcern concern) {

    }

    @Override
    public boolean allowAction(StrategicAI ai, StrategicAction action) {
        try {
            if (ai.getFaction().getId().equals(Factions.PIRATES) && KantaCMD.playerHasProtection()) {
                // if not an action targeting player, nothing to do with us
                if (!action.getFaction().isPlayerFaction() && !action.getConcern().getFaction().isPlayerFaction()) return true;

                if (action.getDef().hasTag("wartime") || action.getDef().hasTag("aggressive")) return false;
            }
        } catch (Exception ex) {}

        return true;
    }

    @Override
    public void reportActionAdded(StrategicAI ai, StrategicAction action) {

    }

    @Override
    public void reportActionPriorityUpdated(StrategicAI ai, StrategicAction action) {

    }

    @Override
    public void reportActionUpdated(StrategicAI ai, StrategicAction action, StrategicActionDelegate.ActionStatus status) {

    }

    @Override
    public void reportActionCancelled(StrategicAI ai, StrategicAction action) {

    }
}
