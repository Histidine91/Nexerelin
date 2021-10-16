package exerelin.campaign.colony;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.AbandonMarketPluginImpl;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;

public class NexAbandonMarketPlugin extends AbandonMarketPluginImpl {
	
	@Override
	public int getHandlingPriority(Object params) {
		return 1;	// supersede vanilla plugin, which has priority 0
	}
	
	public boolean canAbandon(MarketAPI market) {
		if (!NexConfig.allowInvadeStoryCritical && Misc.isStoryCritical(market)) return false;
		return super.canAbandon(market);
	}
	
	public void createAbandonButtonTooltip(TooltipMakerAPI info, float width, boolean expanded, MarketAPI market) {
		if (!NexConfig.allowInvadeStoryCritical && Misc.isStoryCritical(market)) {
			info.addPara(StringHelper.getString("exerelin_misc", "abandonColonyStoryCriticalTooltip"), 
					Misc.getNegativeHighlightColor(), 0f);
			return;
		}
		super.createAbandonButtonTooltip(info, width, expanded, market);
	}
}
