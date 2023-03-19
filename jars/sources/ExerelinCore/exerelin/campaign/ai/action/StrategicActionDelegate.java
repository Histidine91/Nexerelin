package exerelin.campaign.ai.action;

/**
 * Implemented by classes connected to a {@code StrategicAction}.
 */
public interface StrategicActionDelegate {

    ActionStatus getStrategicActionStatus();
    String getName();
    String getIcon();
    StrategicAction getStrategicAction();
    void setStrategicAction(StrategicAction action);

    // states: preparing, in progress, done?
    public enum ActionStatus {
        STARTING, IN_PROGRESS, SUCCESS, FAILURE, CANCELLED;

        public boolean isEnded() {
            return this == SUCCESS || this == FAILURE || this == CANCELLED;
        }
    }
}
