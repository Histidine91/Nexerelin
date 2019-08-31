package exerelin.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import static com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin.STOCKPILE_MAX_MONTHS;
import exerelin.campaign.intel.PlayerOutpostIntel;
import org.apache.log4j.Logger;

// same as vanilla one except accessible when player grants autonomy + outpost handling
public class Nex_LocalResourcesSubmarketPlugin extends LocalResourcesSubmarketPlugin {
	
	public static Logger log = Global.getLogger(Nex_LocalResourcesSubmarketPlugin.class);
	
	@Override
	public boolean isHidden() {
		return !market.isPlayerOwned() && !market.getFaction().isPlayerFaction();
	}
	
	// special handling for player outposts
	@Override
	public int getStockpileLimit(CommodityOnMarketAPI com) {
		if (market.getMemoryWithoutUpdate().getBoolean(PlayerOutpostIntel.MARKET_MEMORY_FLAG)) {
			if (PlayerOutpostIntel.UNWANTED_COMMODITIES.contains(com.getId())) {
				return 0;
			}
			
			float available = com.getAvailable();
			float limit = available * com.getCommodity().getEconUnit();
			limit *= STOCKPILE_MAX_MONTHS;
			return (int)limit;
			
		}
		return super.getStockpileLimit(com);
	}
	
	@Override
	public void advance(float amount) {
		//Global.getLogger(this.getClass()).info("wololo " + amount);
		super.advance(amount);
	}
}
