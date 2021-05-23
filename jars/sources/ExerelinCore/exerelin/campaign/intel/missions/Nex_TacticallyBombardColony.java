package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.missions.TacticallyBombardColony;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;

public class Nex_TacticallyBombardColony extends TacticallyBombardColony {
	
	public static int EXTRA_REWARD_PER_FUEL = 20;
	
	@Override
	protected boolean create(MarketAPI createdAt, boolean barEvent) {
		boolean created = super.create(createdAt, barEvent);
		if (created == false) return created;
		
		int bonus = MarketCMD.getBombardmentCost(market, null) * EXTRA_REWARD_PER_FUEL;
		setCreditReward(this.getCreditsReward() + bonus);
		
		return true;
	}
}
