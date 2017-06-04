package exerelin.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RuinsFleetRouteManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

// overrides pickSourceMarket
// see http://fractalsoftworks.com/forum/index.php?topic=12548.0
public class ExerelinRuinsFleetRouteManager extends RuinsFleetRouteManager {

	public ExerelinRuinsFleetRouteManager(StarSystemAPI system) {
		super(system);
	}
		
	@Override
	public MarketAPI pickSourceMarket() {
		
		WeightedRandomPicker<MarketAPI> markets = new WeightedRandomPicker<MarketAPI>();
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (market.getFaction().isHostileTo(Factions.INDEPENDENT)) continue;
			if (market.getContainingLocation() == null) continue;
			if (market.getContainingLocation().isHyperspace()) continue;
			
			float distLY = Misc.getDistanceLY(system.getLocation(), market.getLocationInHyperspace());
			float weight = market.getSize();
			
			float f = Math.max(0.1f, 1f - Math.min(1f, distLY / 20f));
			f *= f;
			weight *= f;
			
			markets.add(market, weight);
		}
		
		return markets.pick();
	}
}
