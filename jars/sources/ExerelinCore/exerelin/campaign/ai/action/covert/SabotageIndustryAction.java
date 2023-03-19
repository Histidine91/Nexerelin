package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.HasCommodityTarget;
import exerelin.campaign.ai.concern.HasIndustryTarget;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.DestroyCommodityStocks;
import exerelin.campaign.intel.agents.SabotageIndustry;
import exerelin.utilities.NexUtils;

import java.util.List;

public class SabotageIndustryAction extends CovertAction {

    protected MarketAPI market;
    protected Industry industry;
    protected String commodityId;

    @Override
    public boolean generate() {
        industry = ((HasIndustryTarget)concern).getTargetIndustry();

        CovertActionIntel intel = new SabotageIndustry(null, industry.getMarket(), industry, getAgentFaction(), getTargetFaction(),
                false, null);
        return beginAction(intel);
    }

    public MarketAPI pickTargetMarket() {
        if (concern.getMarket() != null)
            return concern.getMarket();

        if (commodityId != null) {
            WeightedRandomPicker<com.fs.starfarer.api.campaign.econ.MarketAPI> picker = new WeightedRandomPicker<>();
            List<EconomyInfoHelper.ProducerEntry> competitors = EconomyInfoHelper.getInstance().getProducers(
                    concern.getFaction().getId(), commodityId, 6, true);
            for (EconomyInfoHelper.ProducerEntry entry : competitors) {
                picker.add(entry.market, entry.output);
            }
            return picker.pick();
        }

        return null;
    }

    public Industry pickTargetIndustryFallback(MarketAPI market) {
        return NexUtils.getRandomListElement(market.getIndustries());
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.SABOTAGE_INDUSTRY;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        return concern.getDef().hasTag("canSabotageIndustry") || concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM);
    }
}
