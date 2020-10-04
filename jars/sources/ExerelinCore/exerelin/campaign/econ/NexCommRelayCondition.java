package exerelin.campaign.econ;

import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.econ.CommRelayCondition;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;

public class NexCommRelayCondition extends CommRelayCondition {
	
	// changed from vanilla: can use relay of anyone we're friendly with
	@Override
	protected SectorEntityToken getBestRelay() {
		if (market.getContainingLocation() == null) return null;
		
		SectorEntityToken best = null;
		for (SectorEntityToken relay : relays) {
			if (relay.getMemoryWithoutUpdate().getBoolean(MemFlags.OBJECTIVE_NON_FUNCTIONAL)) 
			{
				continue;
			}
			if (relay.getFaction().isAtWorst(market.getFaction(), RepLevel.FRIENDLY))
			{
				if (best == null || (isMakeshift(best) && !isMakeshift(relay))) {
					best = relay;
				}
			}
		}
		return best;
	}
	
}
