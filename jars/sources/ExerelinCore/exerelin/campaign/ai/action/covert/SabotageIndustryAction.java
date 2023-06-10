package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.concern.HasIndustryTarget;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.SabotageIndustry;

import java.util.List;
import java.util.Map;

public class SabotageIndustryAction extends CovertAction {

    protected MarketAPI market;
    protected Industry industry;

    @Override
    public boolean generate() {
        industry = null;
        market = concern.getMarket();

        // get the target industry from concern if it has one
        if (concern instanceof HasIndustryTarget) {
            List<Industry> candidates = ((HasIndustryTarget)concern).getTargetIndustries();
            if (candidates != null && !candidates.isEmpty()) {
                industry = candidates.get(0);
            }

            if (industry == null && market != null) {
                List<String> candidateIds = ((HasIndustryTarget)concern).getTargetIndustryIds();
                if (!candidateIds.isEmpty()) {
                    for (String indId : candidateIds) {
                        if (market.hasIndustry(indId)) {
                            industry = market.getIndustry(indId);
                            break;
                        }
                    }
                }
            }
        }

        if (industry == null) industry = pickTargetIndustryFallback(market);
        if (industry == null) return false;

        if (market == null) market = industry.getMarket();
        if (market == null) return false;

        CovertActionIntel intel = new SabotageIndustry(null, market, industry, getAgentFaction(), getTargetFaction(),
                false, null);
        return beginAction(intel);
    }

    @Override
    public MarketAPI pickTargetMarket() {
        return null;    // get from industry instead
    }

    public Industry pickTargetIndustryFallback(MarketAPI market) {

        // If we have a market specified, blow up the spaceport or something with military value, or something that makes goods
        if (market != null) {
            WeightedRandomPicker<Industry> picker = new WeightedRandomPicker<>();
            for (Industry ind : market.getIndustries()) {
                IndustrySpecAPI spec = ind.getSpec();
                if (ind.getId().equals(Industries.POPULATION)) continue;

                int weight = 0;
                if (spec.hasTag(Industries.TAG_SPACEPORT))
                    weight += 3;
                if (spec.hasTag(Industries.GROUNDDEFENSES))
                    weight += 2;
                if (spec.hasTag(Industries.TAG_PATROL))
                    weight += 1;
                if (spec.hasTag(Industries.TAG_MILITARY))
                    weight += 2;
                if (spec.hasTag(Industries.TAG_COMMAND))
                    weight += 3;

                if (weight <= 0 && !ind.getAllSupply().isEmpty())
                    weight +=1;
                picker.add(ind, weight);
            }
            return picker.pick();
        }

        // else, ask CovertOpsManager to find something for us
        Industry ind = CovertOpsManager.getManager().pickMilitarySabotageTarget(ai.getFaction());
        if (ind != null) return ind;
        EconomyInfoHelper.ProducerEntry prod = CovertOpsManager.getManager().pickEconomicSabotageTarget(ai.getFaction());
        if (prod != null) {
            Map<String, Object> targetData = CovertOpsManager.getManager().pickSabotageIndustryTargetFromProducerEntry(prod);
            return (Industry)targetData.get("industry");
        }

        return null;
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.SABOTAGE_INDUSTRY;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        //if (!(concern instanceof HasIndustryTarget)) return false;
        return concern.getDef().hasTag("canSabotageIndustry") || concern.getDef().hasTag(SAIConstants.TAG_WANT_CAUSE_HARM);
    }
}
