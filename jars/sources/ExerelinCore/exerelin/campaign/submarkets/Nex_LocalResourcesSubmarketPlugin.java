package exerelin.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.impl.campaign.ids.Strings;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin;
import static com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin.STOCKPILE_MAX_MONTHS;
import static com.fs.starfarer.api.impl.campaign.submarkets.LocalResourcesSubmarketPlugin.getStockpilingUnitPrice;
import com.fs.starfarer.api.util.Misc;
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
		if (market.getMemoryWithoutUpdate().contains(PlayerOutpostIntel.MARKET_MEMORY_FLAG)) {
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
	
	public void billCargo() {
		CargoAPI copy = taken.createCopy();
		taken.removeAll(left);
		left.removeAll(copy);

		MonthlyReport report = SharedData.getData().getCurrentReport();


		for (CargoStackAPI stack : taken.getStacksCopy()) {
			if (!stack.isCommodityStack()) continue;

			MonthlyReport.FDNode node = report.getRestockingNode(market);
			CargoAPI tooltipCargo = (CargoAPI) node.custom2;

			float addToTooltipCargo = stack.getSize();
			String cid = stack.getCommodityId();
			float q = tooltipCargo.getCommodityQuantity(cid) + addToTooltipCargo;
			if (q < 1) {
				addToTooltipCargo = 1f; // add at least 1 unit or it won't do anything
			}
			tooltipCargo.addCommodity(cid, addToTooltipCargo);

			float unitPrice = (int) getStockpilingUnitPrice(stack.getResourceIfResource(), false);
			//node.upkeep += unitPrice * addAmount;

			MonthlyReport.FDNode comNode = report.getNode(node, cid);

			CommoditySpecAPI spec = stack.getResourceIfResource();
			comNode.icon = spec.getIconName();
			comNode.upkeep += unitPrice * addToTooltipCargo;
			comNode.custom = market.getCommodityData(cid);

			if (comNode.custom2 == null) {
				comNode.custom2 = 0f;
			}
			comNode.custom2 = (Float)comNode.custom2 + addToTooltipCargo;

			float qty = Math.max(1, (Float) comNode.custom2);
			qty = (float) Math.ceil(qty);
			comNode.name = spec.getName() + " " + Strings.X + Misc.getWithDGS(qty);
			comNode.tooltipCreator = report.getMonthlyReportTooltip();
		}
		taken.clear();
	}
	
	// cargo is always taken regardless of faction or player ownership
	// so you can't cheat by sucking out all the stuff and giving to another faction
	@Override
	public void reportEconomyTick(int iterIndex) {
		if (isEconomyListenerExpired()) {
			Global.getSector().getListenerManager().removeListener(this);
			return;
		}
		
		int lastIterInMonth = (int) Global.getSettings().getFloat("economyIterPerMonth") - 1;
		if (iterIndex != lastIterInMonth) return;
		
		// MODIFIED
		billCargo();
	}
}
