package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.combat.MutableStat;
import exerelin.campaign.intel.groundbattle.GroundUnit;

public class FPEDefensePlugin extends MarketConditionPlugin {
	
	public static float ATK_MULT = 1.25f;
	public static float DEF_MULT = 0.75f;
	
	@Override
	public MutableStat modifyDamageDealt(GroundUnit unit, MutableStat dmg) {
		if (unit.isAttacker()) return dmg;
		if (unit.getLocation() == null || unit.getLocation().heldByAttacker == true)
			return dmg;
		
		dmg.modifyMult(conditionId, ATK_MULT, intel.getMarket().getCondition(conditionId).getName());
		return dmg;
	}
	
	@Override
	public float modifyDamageReceived(GroundUnit unit, float dmg) {
		if (unit.isAttacker()) return dmg;
		if (unit.getLocation() == null || unit.getLocation().heldByAttacker == true)
			return dmg;
		
		return dmg * DEF_MULT;
	}
}