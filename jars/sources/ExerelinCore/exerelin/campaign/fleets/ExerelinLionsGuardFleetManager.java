package exerelin.campaign.fleets;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.LionsGuardFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

/**
 * same as vanilla one except does nothing if market does not belong to Sindrian Diktat
 */
public class ExerelinLionsGuardFleetManager extends LionsGuardFleetManager {
	
	protected MarketAPI marketAlt;
	
	public ExerelinLionsGuardFleetManager(MarketAPI market) {
		super(market);
		marketAlt = market;
	}
	
	@Override
	public void advance(float amount) {
		if (!marketAlt.getFactionId().equals(Factions.DIKTAT))
			return;
		super.advance(amount);
	}
	
}
