package exerelin.campaign;

import java.util.List;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class EconomyLogger extends BaseCampaignEventListener implements EveryFrameScript {

	public static Logger log = Global.getLogger(EconomyLogger.class);
	
	int lastDays = -7;
	
	public EconomyLogger() {
		super(true);
	}

	private boolean firstFrame = true;
	public void advance(float amount) {
		SectorAPI sector = Global.getSector();
		float days = sector.getClock().convertToDays(amount);
		
		if (days - lastDays >= 1)
		{
			lastDays = (int)days;
			printMarketInfo(lastDays);
		}
	}
	
	public boolean isDone() {
		return false;
	}

	public boolean runWhilePaused() {
		return true;
	}

	private void printMarketInfo(int days)
	{
		log.info("MARKET STATUS ON DAY " + days);
		SectorAPI sector = Global.getSector();
		EconomyAPI econ = sector.getEconomy();
		List markets = econ.getMarketsCopy();
		for (int i=0; i<markets.size(); i++)
		{
			MarketAPI market = (MarketAPI)(markets.get(i));
			String name = market.getName();
			int size = market.getSize();
			CommodityOnMarketAPI commodity = market.getCommodityData(Commodities.CREW);
			float stockpile = commodity.getStockpile();
			float supply = commodity.getSupply().getModifiedValue();
			float demand = commodity.getDemand().getDemandValue();
			log.info("\t" + name + " (" + size + ") : " + supply + ", " + demand + ", " + stockpile);
		}
	}
	
}