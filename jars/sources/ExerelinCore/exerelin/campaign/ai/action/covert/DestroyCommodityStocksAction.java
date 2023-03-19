package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.HasCommodityTarget;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.DestroyCommodityStocks;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DestroyCommodityStocksAction extends CovertAction {

    public static final Set<String> DEFAULT_COMMODITY_TARGETS = new LinkedHashSet<>();

    static {
        DEFAULT_COMMODITY_TARGETS.add(Commodities.SUPPLIES);
        DEFAULT_COMMODITY_TARGETS.add(Commodities.FOOD);
        DEFAULT_COMMODITY_TARGETS.add(Commodities.FUEL);
        DEFAULT_COMMODITY_TARGETS.add(Commodities.HAND_WEAPONS);
    }

    protected String commodityId;
    protected MarketAPI market;

    @Override
    public boolean generate() {
        if (concern.getMarket() != null)
            market = concern.getMarket();
        if (concern instanceof HasCommodityTarget) {
            commodityId = ((HasCommodityTarget)concern).getCommodityIds().get(0);
        }

        if (market == null) market = pickTargetMarket();
        if (commodityId == null) commodityId = pickTargetCommodityFallback(market);

        CovertActionIntel intel = new DestroyCommodityStocks(null, market, commodityId, getAgentFaction(), getTargetFaction(),
                false, null);
        return beginAction(intel);
    }

    public MarketAPI pickTargetMarket() {
        if (concern.getMarket() != null)
            return concern.getMarket();

        if (commodityId != null) {
            WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
            List<EconomyInfoHelper.ProducerEntry> competitors = EconomyInfoHelper.getInstance().getProducers(
                    concern.getFaction().getId(), commodityId, 6, true);
            for (EconomyInfoHelper.ProducerEntry entry : competitors) {
                picker.add(entry.market, entry.output);
            }
            return picker.pick();
        }

        return null;
    }

    public String pickTargetCommodityFallback(MarketAPI market) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String commodity : DEFAULT_COMMODITY_TARGETS) {
            float weight = 0;
            if (market != null) weight = market.getCommodityData(commodity).getAvailable();
            picker.add(commodity, weight);
        }
        return picker.pick();
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.DESTROY_COMMODITY_STOCKS;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        return concern.getDef().hasTag("canDestroyCommodity") || concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM);
    }
}
