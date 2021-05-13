package exerelin.campaign.intel.groundbattle.plugins;

import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;

public class GeneralPlugin extends BaseGroundBattlePlugin {
	
	@Override
	public void apply() {
		int size = intel.getMarket().getSize();
		int baseMovePoints = getBaseMovementPointsPerTurn(size);
		String desc = GroundBattleIntel.getString("modifierMovementPointsBase");
		desc = String.format(desc, size);
		intel.getSide(false).getMovementPointsPerTurn().modifyFlat("base", 
				baseMovePoints, desc);
		intel.getSide(true).getMovementPointsPerTurn().modifyFlat("base", 
				baseMovePoints, desc);
		
		if (intel.getTurnNum() == 1) {
			intel.getSide(true).getMovementPointsPerTurn().modifyMult("turn1", 
				1.5f, GroundBattleIntel.getString("modifierMovementPointsTurn1"));
		}
		else {
			intel.getSide(true).getMovementPointsPerTurn().unmodify("turn1");
		}
	}

	@Override
	public void unapply() {
		intel.getSide(false).getMovementPointsPerTurn().unmodify("base");
		intel.getSide(true).getMovementPointsPerTurn().unmodify("base");
	}
	
	public static int getBaseMovementPointsPerTurn(int marketSize) {
		return (int)Math.round(GBConstants.BASE_MOVEMENT_POINTS_PER_TURN * Math.pow(2, marketSize - 2));
	}
}
