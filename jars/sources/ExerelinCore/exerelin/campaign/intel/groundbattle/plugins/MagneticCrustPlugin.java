package exerelin.campaign.intel.groundbattle.plugins;

import exerelin.campaign.intel.groundbattle.GroundUnit;
import exerelin.campaign.intel.groundbattle.IndustryForBattle;

public class MagneticCrustPlugin extends MarketConditionPlugin {
	
	
	@Override
	public void reportUnitMoved(GroundUnit unit, IndustryForBattle lastLoc) {
		if (unit.isAttacker())
			unit.reorganize(1);
	}
}
