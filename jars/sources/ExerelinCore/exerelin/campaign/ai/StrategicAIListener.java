package exerelin.campaign.ai;

import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;

public interface StrategicAIListener  {

    void reportStrategyMeetingHeld();
    void reportConcernAdded(StrategicConcern concern);
    void reportConcernUpdated(StrategicConcern concern);
    void reportConcernRemoved(StrategicConcern concern);
    void reportActionAdded(StrategicAction action);
    void reportActionCompleted(StrategicAction action);
    void reportActionRemoved(StrategicAction action);
}
