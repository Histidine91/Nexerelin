package exerelin.campaign.ai;

import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;

public interface StrategicAIListener  {

    void reportStrategyMeetingHeld(StrategicAI ai);
    void reportConcernAdded(StrategicAI ai, StrategicConcern concern);
    void reportConcernUpdated(StrategicAI ai, StrategicConcern concern);
    void reportConcernRemoved(StrategicAI ai, StrategicConcern concern);
    void reportActionAdded(StrategicAI ai, StrategicAction action);
    void reportActionUpdated(StrategicAI ai, StrategicAction action);
    void reportActionCompleted(StrategicAI ai, StrategicAction action);
    void reportActionRemoved(StrategicAI ai, StrategicAction action);
}
