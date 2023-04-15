package exerelin.campaign.ai.action;

import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ai.StrategicAI;

import java.awt.*;

/**
 * Implemented by classes connected to a {@code StrategicAction}.
 */
public interface StrategicActionDelegate {

    public static final Object BUTTON_GO_INTEL = new Object();

    ActionStatus getStrategicActionStatus();
    float getStrategicActionDaysRemaining();
    String getStrategicActionName();
    String getIcon();
    StrategicAction getStrategicAction();
    void setStrategicAction(StrategicAction action);
    void abortStrategicAction();

    // states: preparing, in progress, done?
    public enum ActionStatus {
        STARTING("starting", Misc.getHighlightColor(), false),
        IN_PROGRESS("inProgress", Misc.getHighlightColor(), false),
        SUCCESS("success", Misc.getPositiveHighlightColor(), true),
        FAILURE("failure", Misc.getNegativeHighlightColor(), true),
        CANCELLED("cancelled", Misc.getGrayColor(), true);

        public final Color color;
        public final boolean ended;
        public final String strId;

        private ActionStatus(String strId, Color color, boolean ended) {
            this.strId = strId;
            this.color = color;
            this.ended = ended;
        }

        public String getStatusName(boolean ucFirst) {
            return StrategicAI.getString("actionStatus_" + strId, ucFirst);
        }

        public String getStatusString() {
            return String.format(StrategicAI.getString("actionStatus"), getStatusName(true));
        }
    }
}
