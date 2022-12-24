package exerelin.campaign.ai.action;

public interface StrategicActionDelegate {

    ActionStatus getStrategicActionStatus();

    // states: preparing, in progress, done?
    public enum ActionStatus {
        PREPARING, IN_PROGRESS, SUCCESS, FAILURE
    }
}
