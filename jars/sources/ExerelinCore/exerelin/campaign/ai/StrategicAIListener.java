package exerelin.campaign.ai;

import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.ai.concern.StrategicConcern;

public interface StrategicAIListener  {

    void reportStrategyMeetingHeld(StrategicAI ai);
    boolean allowConcern(StrategicAI ai, StrategicConcern concern);
    void reportConcernAdded(StrategicAI ai, StrategicConcern concern);
    void reportConcernUpdated(StrategicAI ai, StrategicConcern concern);
    void reportConcernRemoved(StrategicAI ai, StrategicConcern concern);
    boolean allowAction(StrategicAI ai, StrategicAction action);
    void reportActionAdded(StrategicAI ai, StrategicAction action);
    /**
     * Note: Because this is called prior to actual generation of the action, not all data may be available.
     * @param ai
     * @param action
     */
    void reportActionPriorityUpdated(StrategicAI ai, StrategicAction action);
    void reportActionUpdated(StrategicAI ai, StrategicAction action, StrategicActionDelegate.ActionStatus status);
    void reportActionCancelled(StrategicAI ai, StrategicAction action);
}
