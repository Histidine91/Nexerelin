package exerelin.campaign.ai.action;

import com.fs.starfarer.api.combat.MutableStat;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.StrategicConcern;

public interface StrategicAction {

    StrategicAI getAI();
    void setAI(StrategicAI ai);

    StrategicConcern getConcern();
    void setConcern(StrategicConcern concern);

    StrategicActionDelegate getDelegate();
    void setDelegate(StrategicActionDelegate delegate);

    boolean generate();
    boolean isValid();
    MutableStat getPriority();
    float getPriorityFloat();
}
