package exerelin.campaign.ai.action;

import org.jetbrains.annotations.Nullable;

/**
 * Actions that exist only to replace themselves with other actions, with the shim responsible for picking the best such action.
 * See https://stackoverflow.com/questions/2116142/what-is-a-shim for an explanation.
 */
public interface ShimAction {

    /**
     * Returns an action that the shim generates in place of itself. Should not be generated or inited yet.
     * @return
     */
    @Nullable StrategicAction pickShimmedAction();
}
