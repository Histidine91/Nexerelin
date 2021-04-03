package exerelin.campaign.intel.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.ProcurementMissionIntel;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.econ.EconomyInfoHelper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Blocks some factions like derelicts; deliveries increase commodity availability
@Deprecated
public class Nex_ProcurementMissionIntel extends ProcurementMissionIntel {
	
	public static final Set<String> DISALLOWED_FACTIONS = new HashSet<>(Arrays.asList(new String[]{
		Factions.DERELICT, Factions.REMNANTS, "templars", "nex_derelict"
	}));
	public static final float TRADE_MULT = 0.5f;
	
	public static boolean isFactionAllowed(String factionId) {
		return !DISALLOWED_FACTIONS.contains(factionId);
	}
	
	// Same as vanilla, but avoids commodities that are not being produced anywhere
	@Override
	protected String pickCommodity() {
		Global.getSettings().profilerBegin(this.getClass().getSimpleName() + ".pickCommodity()");
		EconomyAPI economy = Global.getSector().getEconomy();
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>();
		
		for (String curr : economy.getAllCommodityIds()) {
			CommoditySpecAPI spec = economy.getCommoditySpec(curr);
			if (spec.isMeta()) continue;
			if (spec.hasTag(Commodities.TAG_CREW)) continue;
			if (spec.hasTag(Commodities.TAG_MARINES)) continue;
			if (spec.hasTag(Commodities.TAG_NON_ECONOMIC)) continue;
//			if (spec.getId().equals(Commodities.SUPPLIES)) continue;
//			if (spec.getId().equals(Commodities.FUEL)) continue;

			// MODIFIED
			EconomyInfoHelper eih = EconomyInfoHelper.getInstance();
			if (eih != null && eih.getProducersByCommodity(curr).isEmpty())
				continue;
			
			//float weight = spec.getBasePrice();
			float weight = 1f;
			picker.add(curr, weight);
		}
		Global.getSettings().profilerEnd();
		return picker.pick();
	}
	
	// same as vanilla, but excludes some factions
	@Override
	protected MarketAPI pickMarket(String commodityId, float quantity, float illegalMult, float min) {
//		if (true) {
//			return Global.getSector().getEconomy().getMarket("jangala");
//		}
		Global.getSettings().profilerBegin(this.getClass().getSimpleName() + ".pickMarket()");
		EconomyAPI economy = Global.getSector().getEconomy();
		
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>();
		
		for (MarketAPI market : economy.getMarketsCopy()) {
			if (market.isHidden()) continue;
			if (market.isPlayerOwned()) continue;
			if (!isFactionAllowed(market.getFactionId())) continue;
			
			//CommodityOnMarketAPI com = market.getCommodityData(commodityId);
			
			boolean illegal = market.isIllegal(commodityId);
			float test = getQuantityAdjustedForMarket(commodityId, quantity, illegalMult, min, market);
			if (illegal) {
				test *= illegalMult;
				if (test < min) test = min;
			}
			//if (com.getAverageStockpileAfterDemand() >= minQty) continue;
			// don't filter on demand - open up more possibilities; may be needed for non-market-condition reasons
			//if (com.getDemand().getDemandValue() < minQty) continue;
			
			if (doNearbyMarketsHave(market, commodityId, test * 0.5f)) continue;
			
			float weight = market.getSize();
			
			if (market.getFaction().isPlayerFaction()) {
				weight *= 0.1f;
			}
			
			picker.add(market, weight);
		}
		Global.getSettings().profilerEnd();
		return picker.pick();
	}
	
	@Override
	public boolean callEvent(String ruleId, final InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String action = params.get(0).getString(memoryMap);
		
		boolean illegal = contact.getFaction().isHostileTo(market.getFaction());
		if (!illegal && action.equals("performDelivery")) {
			String commodityId = commodity.getId();
			CommodityOnMarketAPI com = market.getCommodityData(commodityId);
			float qty = quantity * TRADE_MULT;
			float deficit = com.getDeficitQuantity();
			if (qty > deficit) qty = deficit;
			if (qty > 0) {
				log.info("Adding trade mod " + qty + " to market " + market.getName());
				com.addTradeModPlus("deliver_" + Misc.genUID(), qty, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);
			}
		}
		
		return super.callEvent(ruleId, dialog, params, memoryMap);
	}
	
	// runcode exerelin.campaign.intel.missions.Nex_ProcurementMissionIntel.debug()
	public static void debug() {
		MarketAPI market = Global.getSector().getCampaignUI().getCurrentInteractionDialog().getInteractionTarget().getMarket();
		CommodityOnMarketAPI com = market.getCommodityData(Commodities.SUPPLIES);
		log.info("wololo " + com.getDeficitQuantity());
		//com.addTradeModPlus("temp_" + Misc.genUID(), 100, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);
	}
}
