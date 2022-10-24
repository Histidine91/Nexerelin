package exerelin.campaign.intel;

import exerelin.utilities.StringHelper;

/**
 * Trying something: sharing mission/event cancellation reasons between various classes.
 * The player-facing text should be handled by whichever intel item chooses to use this enum.
 */
public enum EventCancelReason {
    ALREADY_CAPTURED("alreadyCaptured"),
    NOT_IN_ECONOMY("notInEconomy"),
    NO_LONGER_HOSTILE("noLongerHostile"),
    RELATIONS_TOO_HIGH("relationsTooHigh"),
    NO_LONGER_INTERESTED("noLongerInterested"),
    OTHER(null);

    public String defaultReasonStringId;

    private EventCancelReason(String defaultReasonStringId) {
        this.defaultReasonStringId = defaultReasonStringId;
    }

    public String getReason() {
        return getReason(defaultReasonStringId);
    }
    public static String getReason(String id) {
        return StringHelper.getString("nex_missions", "cancelReason_" + id);
    }
}
