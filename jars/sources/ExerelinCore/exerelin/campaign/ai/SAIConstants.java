package exerelin.campaign.ai;

public class SAIConstants {

    @Deprecated public static boolean AI_ENABLED = true;
    public static boolean DEBUG_LOGGING = true;

    public static float[] BASE_INTERVAL = {19f, 21f};
    public static float INTERVAL_PER_LIVE_FACTION = 0.5f;

    // Values below this (after dividing by market size) are considered vulnerable.
    public static float GROUND_DEF_THRESHOLD = 200;
    public static float SPACE_DEF_THRESHOLD = 150;
    public static float MARKET_VALUE_DIVISOR = 40;
    public static float MIN_COMPETITOR_SHARE = 10;

    public static float STRENGTH_MULT_FOR_CONCERN = 1f;

    /**
     * Offensive fleet actions: If the ratio of FP available to FP required for target is less than this ratio, action priority is zero.
     */
    public static float MIN_FP_RATIO_THRESHOLD = 0.4f;

    /**
     * For things like the vulnerable faction concern. Try not to be wildly lower than MIN_CONCERN_PRIORITY_TO_ACT to avoid it sitting unattended indefinitely.
     */
    public static float MIN_FACTION_PRIORITY_TO_CARE = 60;
    /**
     * For things like the inadequate defense concern. Try not to be wildly lower than MIN_CONCERN_PRIORITY_TO_ACT to avoid it sitting unattended indefinitely.
     */
    public static float MIN_MARKET_VALUE_PRIORITY_TO_CARE = 60;

    public static float MAX_ALIGNMENT_MODIFIER_FOR_PRIORITY = 0.25f;
    public static float TRAIT_POSITIVE_MULT = 1.3f;
    public static float TRAIT_NEGATIVE_MULT = 0.7f;
    public static float NEGATIVE_DISPOSITION_MULT = 0.6f;
    public static float POSITIVE_DISPOSITION_MULT = 1.25f;

    public static int ACTIONS_PER_MEETING = 2;
    public static int MAX_SIMULTANEOUS_ACTIONS = 10;    // todo?
    public static int MAX_ACTIONS_TO_CHECK_PER_CONCERN = 4;
    public static float MIN_CONCERN_PRIORITY_TO_ACT = 75;
    public static float MIN_ACTION_PRIORITY_TO_USE = 60;
    public static float DEFAULT_ACTION_COOLDOWN = 30;
    public static float DEFAULT_ANTI_REPETITION_VALUE = 25;
    public static float ANTI_REPETITION_DECAY_PER_DAY = 0.1f;
    public static int KEEP_ENDED_ACTIONS_FOR_NUM_MEETINGS = 1;

    // UI stuff
    public static final float CONCERN_ITEM_WIDTH = 320;
    public static final float CONCERN_ITEM_HEIGHT = 72;

    public static final String TAG_MILITARY = "military";
    public static final String TAG_ECONOMY = "economy";
    public static final String TAG_DIPLOMACY = "diplomacy";
    public static final String TAG_FRIENDLY = "friendly";
    public static final String TAG_UNFRIENDLY = "unfriendly";
    public static final String TAG_AGGRESSIVE = "aggressive";
    public static final String TAG_COVERT = "covert";
    public static final String TAG_WANT_CAUSE_HARM = "wantCauseHarm";
}
