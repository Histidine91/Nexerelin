package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.TacticallyBombardColony;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;

public class Nex_TacticallyBombardColony extends TacticallyBombardColony {
	
	public static int EXTRA_REWARD_PER_FUEL = 20;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		boolean created = super.create(createdAt, barEvent);
		if (!created) return false;
		
		int bombCost = MarketCMD.getBombardmentCost(market, null);
		int bonus = bombCost * EXTRA_REWARD_PER_FUEL;
		Global.getLogger(this.getClass()).info(String.format("Fuel bonus: %s * %s = %s", bombCost, EXTRA_REWARD_PER_FUEL, bonus));
		setCreditReward(this.getCreditsReward() + bonus);
		
		return true;
	}
}
