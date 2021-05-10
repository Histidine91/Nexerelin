package exerelin.campaign.intel.groundbattle.plugins;

import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.utilities.StringHelper;

public class GeneralPlugin extends BaseGroundBattlePlugin {
	
	@Override
	public void unapply() {
		intel.getSide(true).getMovementPointsPerTurn().modifyFlat("base", 
				GBConstants.BASE_MOVES_PER_TURN, StringHelper.getString("base", true));
	}

	@Override
	public void apply() {
		intel.getSide(true).getMovementPointsPerTurn().unmodify("base"); 
	}
}
