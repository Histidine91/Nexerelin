package exerelin.campaign.ai;

public class SAIConstants {

    public static float[] BASE_INTERVAL = {19f, 21f};
    public static float INTERVAL_PER_LIVE_FACTION = 0.5f;

    // Values below this (after dividing by market size) are considered vulnerable.
    public static float GROUND_DEF_THRESHOLD = 160;
    public static float SPACE_DEF_THRESHOLD = 120;
    public static float MARKET_VALUE_DIVISOR = 40;
    public static float MIN_COMPETITOR_SHARE = 10;

    public static float STRENGTH_MULT_FOR_CONCERN = 1.2f;

    /**
     * For things like the vulnerable faction concern.
     */
    public static float MIN_FACTION_PRIORITY_TO_CARE = 40;
    /**
     * For things like the inadequate defense concern.
     */
    public static float MIN_MARKET_VALUE_PRIORITY_TO_CARE = 40;

    public static float MAX_ALIGNMENT_MODIFIER_FOR_PRIORITY = 0.25f;

    // UI stuff

    public static final float CONCERN_ITEM_WIDTH = 320;
    public static final float CONCERN_ITEM_HEIGHT = 72;
}
