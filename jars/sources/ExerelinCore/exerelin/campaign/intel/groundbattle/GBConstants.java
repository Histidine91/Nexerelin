package exerelin.campaign.intel.groundbattle;

public class GBConstants {
	
	public static final String TAG_PREVENT_BOMBARDMENT = "preventBombardment";
	
	public static final String ACTION_WITHDRAW = "withdraw";
	
	public static float BASE_DAMAGE_MULT = 0.1f;
	public static float MORALE_ATTACK_MOD = 0.15f;
	public static float MORALE_DAMAGE_FACTOR = 0.7f;	// 70% losses = 100% morale loss
	public static float MORALE_LOSS_FROM_COMBAT = 0.05f;
	public static float MORALE_RECOVERY_OUT_OF_COMBAT = 0.025f;
	public static float REORGANIZE_AT_MORALE = 0.3f;
	public static float BREAK_AT_MORALE = 0.01f;
	public static float HEAVY_OFFENSIVE_MULT = 1.25f;
	public static float HEAVY_STATION_MULT = 1.25f;
	public static float MORALE_DAM_XP_REDUCTION_MULT = 0.5f;	// take 50% less morale damage at 100% XP
	public static float CAPTURE_MORALE = 0.1f;
	
	public static float SUPPLIES_TO_DEPLOY_MULT = 0.5f;
}
