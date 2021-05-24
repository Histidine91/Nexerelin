package exerelin.campaign.colony;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.StabilizeMarketPluginImpl;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.SectorManager;
import exerelin.utilities.StringHelper;

public class NexStabilizeMarketPlugin extends StabilizeMarketPluginImpl {
	
	@Override
	public void createStabilizeButtonTooltip(TooltipMakerAPI info, float width, boolean expanded, MarketAPI market) {
		super.createStabilizeButtonTooltip(info, width, expanded, market);
		
		if (market.getMemoryWithoutUpdate().getBoolean(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT)) {
			float expireTime = market.getMemoryWithoutUpdate().getExpire(SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT);
			String expireStr = String.format("%.1f", expireTime);
			
			info.addPara(StringHelper.getString("exerelin_markets", "stabilizeInvasionTimeout"), 
					10, Misc.getNegativeHighlightColor(), Misc.getHighlightColor(), expireStr);
		}
	}
	
	public boolean canStabilize(MarketAPI market) {
		return super.canStabilize(market) && !market.getMemoryWithoutUpdate().getBoolean(
				SectorManager.MEMORY_KEY_CAPTURE_STABILIZE_TIMEOUT);
	}
	
	@Override
	public int getHandlingPriority(Object params) {
		return 1;
	}
}
