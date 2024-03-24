package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelData;
import exerelin.utilities.NexConfig;

public class GBConstants {
	
	public static final String TAG_PREVENT_BOMBARDMENT = "preventBombardment";
	public static final String TAG_PREVENT_BOMBARDMENT_SUPER = "preventBombardmentSuper";
	public static final String TAG_PREVENT_EW = "preventEW";
	public static final String TAG_PREVENT_INSPIRE = "preventInspire";
	public static final String TAG_REBEL = "rebel";
	public static final String MEMKEY_GARRISON_DAMAGE = "$nex_garrisonDamage";
	public static final String MEMKEY_AWAIT_DECISION = "$nex_gbAwaitDecision";
	public static final String MEMKEY_INVASION_FAIL_STREAK = "$nex_invasionFailStreak";
	public static final String STAT_MARKET_MORALE_DAMAGE = "nex_moraleDamageTaken";
	public static final String ACTION_MOVE = "move";
	public static final String ACTION_WITHDRAW = "withdraw";
	public static final String CREW_REPLACER_JOB_MARINES = "nex_groundBattle_marines";
	public static final String CREW_REPLACER_JOB_HEAVYARMS = "nex_groundBattle_heavyarms";
	public static final String CREW_REPLACER_JOB_TANKCREW = "nex_groundBattle_tankCrew";
	
	public static int BASE_MOVEMENT_POINTS_PER_TURN = 10;
	public static float TURN_1_MOVE_POINT_MULT = 2;
	public static float HEAVY_DROP_COST_MULT = 1.3f;
	public static float FLEET_SUPPORT_MOVEMENT_MULT = 0.5f;
	public static float SNEAK_ATTACK_MOVE_MULT = 1.25f;
	
	public static float UNIT_MIN_SIZE_MULT = 0.25f;	// of base size
	
	public static float BASE_MORALE = 0.8f;
	public static float BASE_DAMAGE_MULT = 0.1f;
	public static float MORALE_ATTACK_MOD = 0.15f;
	public static float MORALE_DAMAGE_FACTOR = 0.7f;	// 70% losses = 100% morale loss
	public static float DEFENDER_MORALE_DMG_MULT = 0.9f;
	public static float MORALE_LOSS_FROM_COMBAT = 0.05f;
	public static float MORALE_RECOVERY_OUT_OF_COMBAT = 0.025f;
	public static float REORGANIZE_AT_MORALE = 0.3f;
	public static float BREAK_AT_MORALE = 0.01f;
	public static float HEAVY_OFFENSIVE_MULT = 1.25f;
	public static float HEAVY_STATION_MULT = 0.75f;
	public static float XP_MORALE_BONUS = 0.2f;	// 20% more morale at 100% XP
	public static float CAPTURE_MORALE = 0.03f;
	public static float REORGANIZING_DMG_MULT = 0.7f;
	public static float REBEL_DAMAGE_MULT = 0.5f;	// both dealt and received;
	public static float COMBINED_ARMS_BONUS_MULT = 1.25f;
	
	public static int STABILITY_PENALTY_BASE = 2;
	public static int STABILITY_PENALTY_OCCUPATION = 5;
	public static int EXISTING_UNREST_DIVISOR = 4;
	public static float DISRUPTED_TROOP_CONTRIB_MULT = 0.5f;
	public static float DISRUPT_WHEN_CAPTURED_TIME = 0.25f;
	public static float DISRUPT_DAMAGE_MIN_FACTOR = 0.15f;	// local attacker must have at least this much more damage than local defender to disrupt
	public static float DISRUPT_DAMAGE_TIME_MULT = 0.25f;	// multiplier of base disrupt time from raiding
	
	public static float SUPPLIES_TO_DEPLOY_MULT = 0.25f;
	public static float MAX_SUPPORT_DIST = 250;
	
	public static float XP_MARKET_SIZE_MULT = 3f;
	public static float XP_CASUALTY_MULT = 0.15f;
	
	public static float BASE_GARRISON_SIZE = 25;
	public static float EXTERNAL_BOMBARDMENT_DAMAGE = 0.6f;
	public static float INVASION_HEALTH_MONTHS_TO_RECOVER = 3;
	public static float LIBERATION_REBEL_MULT = 0.25f;
	public static float ATTRITION_MULT_PER_DEFICIT_UNIT = 0.25f;
	
	public static int WITHDRAW_AFTER_NO_COMBAT_TURNS = 6;
	
	public static float MAX_DIST_FOR_COUNTER_INVASION = 15;
	
	public static PersonnelData DEFENSE_STAT = new PersonnelData("generic_defender");
	static {
		DEFENSE_STAT.num = 100;
		DEFENSE_STAT.xp = NexConfig.groundBattleGarrisonXP * 100;
	}
	
	public static PersonnelData OFFENSE_STAT = new PersonnelData("generic_attacker");
	static {
		OFFENSE_STAT.num = 100;
		OFFENSE_STAT.xp = NexConfig.groundBattleInvasionTroopXP * 100;
	}
}
