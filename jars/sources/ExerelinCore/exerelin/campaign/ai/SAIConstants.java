package exerelin.campaign.ai;

public class SAIConstants {

    public static float[] BASE_INTERVAL = {19f, 21f};
    public static float INTERVAL_PER_LIVE_FACTION = 0.5f;

    // Values below this (after dividing by market size) are considered vulnerable.
    public static float GROUND_DEF_THRESHOLD = 160;
    public static float SPACE_DEF_THRESHOLD = 120;

    public static float STRENGTH_MULT_FOR_CONCERN = 1.2f;
}
