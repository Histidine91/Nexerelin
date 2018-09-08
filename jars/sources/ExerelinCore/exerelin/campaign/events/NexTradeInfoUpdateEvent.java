package exerelin.campaign.events;

import java.util.Map;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.impl.campaign.events.PriceUpdate;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


// this thing is dumb, it doesn't put the price info icons in the right places
// see http://fractalsoftworks.com/forum/index.php?topic=13180.0
@Deprecated
public class NexTradeInfoUpdateEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(NexTradeInfoUpdateEvent.class);
	
	protected String commodityToUpdate = Commodities.CREW;
	protected MarketAPI lastOrigin = null;
	protected List<MarketAPI> updateMarkets = new ArrayList<>();
	protected List<PriceUpdatePlugin> updates = new ArrayList<>();
	
	public static NexTradeInfoUpdateEvent getEvent()
	{
		CampaignEventPlugin event = Global.getSector().getEventManager().getOngoingEvent(null, "nex_trade_info");
		if (event != null) return (NexTradeInfoUpdateEvent)event;
		else return null;
	}
	
	public void reportEvent(MarketAPI origin, String commodityId, List<MarketAPI> markets)
	{
		commodityToUpdate = commodityId;
		updateMarkets = markets;
		lastOrigin = origin;
		
		updates = new ArrayList<>();
		for (MarketAPI updateMarket : updateMarkets)
		{
			updates.add(new PriceUpdate(updateMarket.getCommodityData(commodityId)));
		}
		log.info("Reporting event, " + updates.size());
		
		if (!updateMarkets.isEmpty())
			Global.getSector().reportEventStage(this, "prices_helper", origin.getPrimaryEntity(), 
					MessagePriority.DELIVER_IMMEDIATELY);
	}
	
	@Override
	public List<PriceUpdatePlugin> getPriceUpdates() {
		return updates;
	}
	
	@Override
	public List<String> getRelatedCommodities() {
		return Arrays.asList(new String[] {commodityToUpdate});
	}
		
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public Map<String, String> getTokenReplacements() {
		Map<String, String> map = super.getTokenReplacements();
		map.put("$market", lastOrigin.getName());
		map.put("$commodity", Global.getSector().getEconomy()
				.getCommoditySpec(commodityToUpdate).getName().toLowerCase());
		
		List<String> marketNames = new ArrayList<>();
		for (MarketAPI market : updateMarkets)
		{
			marketNames.add(market.getName());
		}
		String marketsStr = StringHelper.writeStringCollection(marketNames, false, false);
		map.put("$forMarkets", marketsStr);
		
		return map;
	}
	
	@Override
	public String[] getHighlights(String stageId) {
		List<String> result = new ArrayList<>();
		//addTokensToList(result, "$paid");
		return result.toArray(new String[0]);
	}
	
	@Override
	public boolean isDone() {
		return false;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}