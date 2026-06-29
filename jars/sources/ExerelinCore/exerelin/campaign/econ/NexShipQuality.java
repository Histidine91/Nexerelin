package exerelin.campaign.econ;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.ShipQuality;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

public class NexShipQuality extends ShipQuality {

	public static ShipQuality getInstance() {
		Object test = Global.getSector().getMemoryWithoutUpdate().get(KEY);
		if (test == null) {
			test = new NexShipQuality();
			Global.getSector().getMemoryWithoutUpdate().set(KEY, test);
		}
		return (ShipQuality) test;
	}

	@Override
	public void economyUpdated() {
		// unlike vanilla, highest quality wins period regardless of production
		// no more having your day ruined because highest producer has bad quality (e.g. IndEvo salvage yards)
		data.clear();

		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {

			QualityData d = getQualityData(market); // each combination of faction+econgroup has one QualityData

			CommodityOnMarketAPI com = market.getCommodityData(Commodities.SHIPS);
			int prod = Math.min(com.getAvailable(), com.getMaxSupply());
			int inFactionShipping = com.getCommodityMarketData().getMaxShipping(market, true);
			prod = Math.min(prod, inFactionShipping);
			prod = Math.max(Math.min(com.getAvailable(), com.getMaxSupply()), prod);
			if (prod > 0) {
				float q = market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD).computeEffective(0f);
				if (q >= d.qMod) {
					d.prod = prod;
					d.qMod = q;
					d.market = market;
					d.quality = market.getStats().getDynamic().getMod(Stats.PRODUCTION_QUALITY_MOD);
				}
			}
		}
	}
}
