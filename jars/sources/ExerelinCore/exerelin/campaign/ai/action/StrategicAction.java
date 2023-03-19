package exerelin.campaign.ai.action;

import com.fs.starfarer.api.combat.MutableStat;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.concern.StrategicConcern;

public interface StrategicAction {

    StrategicAI getAI();
    void setAI(StrategicAI ai);
    StrategicActionDelegate.ActionStatus getStatus();

    StrategicConcern getConcern();
    void setConcern(StrategicConcern concern);

    StrategicActionDelegate getDelegate();
    void setDelegate(StrategicActionDelegate delegate);

    boolean generate();
    void init();
    boolean isValid();
    MutableStat getPriority();
    float getPriorityFloat();
    void updatePriority();

    void advance(float days);
    void end(StrategicActionDelegate.ActionStatus newStatus);
    boolean isEnded();

    String getName();
    String getIcon();
    String getId();
    void setId(String id);

    boolean canUseForConcern(StrategicConcern concern);

    StrategicDefManager.StrategicActionDef getDef();
}
