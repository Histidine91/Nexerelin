package exerelin.campaign.ai.action.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.*;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.econ.ProductionMap;
import exerelin.world.ExerelinProcGen;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.IndustryClassGen;
import lombok.extern.log4j.Log4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Build an economy industry. Concerns that want to use this action must implement at least one of {@code HasIndustryToBuild} or {@code HasCommodityTarget}.
 */
@Log4j
public class BuildEconAction extends BuildIndustryAction {

    @Override
    protected boolean buildOrUpgrade() {
        IndustryClassGen gen = NexMarketBuilder.getIndustryClassesByIndustryId().get(industryId);
        ExerelinProcGen.ProcGenEntity entity = ExerelinProcGen.createEntityData(market.getPrimaryEntity());
        gen.apply(entity, false);
        ColonyManager.getManager().processNPCConstruction(market);
        return true;
    }

    @Override
    protected String pickIndustry(MarketAPI market) {
        {
            String fromConcern = null;
            if (concern instanceof HasIndustryToBuild) {
                fromConcern = ((HasIndustryToBuild)concern).getIndustryIdToBuild();
            }
            if (fromConcern != null) return fromConcern;
        }

        String commodityId = ((HasCommodityTarget)concern).getCommodityIds().get(0);
        ProductionMap.CommodityEntry ce = ProductionMap.getOrCreateCommodity(commodityId);
        Set<String> producerIds = ce.producers;

        List<MarketAPI> markets = new ArrayList<>();
        if (market != null) markets.add(market);
        else {
            markets.addAll(Misc.getFactionMarkets(ai.getFactionId()));
        }
        MarketAPI bestLoc = null;
        String bestInd = null;
        float bestScore = 0;
        for (MarketAPI candidate : markets) {
            Pair<String, Float> localBest = getBestProducerForMarket(candidate, producerIds, commodityId);
            if (localBest == null) continue;

            float score = localBest.two;
            if (score > bestScore) {
                bestScore = score;
                bestInd = localBest.one;
                bestLoc = candidate;
            }
        }

        if (bestInd == null) return null;
        this.market = bestLoc;
        return bestInd;
    }

    @Override
    protected MarketAPI pickMarketFallback(String industryId) {
        String commodityId = null;
        if (concern instanceof HasCommodityTarget)
            commodityId = ((HasCommodityTarget)concern).getCommodityIds().get(0);
        Pair<MarketAPI, Float> best = getBestMarketForProducer(industryId, Misc.getFactionMarkets(ai.getFactionId()), commodityId);
        if (best == null) return null;
        return best.one;
    }

    @Override
    protected float modifyProducerScore(String industryId, MarketAPI market, float score) {
        if (concern instanceof ImportDependencyConcern) {
            ImportDependencyConcern idc = (ImportDependencyConcern)concern;
            String commodityId = idc.getCommodityId();
            Industry temp = market.instantiateIndustry(industryId);
            if (temp != null && temp.getSupply(commodityId) != null) {
                int potentialSupply = temp.getSupply(idc.getCommodityId()).getQuantity().getModifiedInt();
                if (potentialSupply >= idc.getRequired()) {
                    score *= 5;
                }
                else {
                    // already have higher in-faction supply than we'll get here?
                    int existingProduction = EconomyInfoHelper.getInstance().getFactionCommodityProduction(ai.getFactionId(), commodityId);
                    if (existingProduction >= potentialSupply) {
                        return -1;
                    }
                }
            }
            else return -1; // should already be checked by getBestProducerForMarket I think
        }

        return score;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();

        // this is not a good way to solve commodity competition
        if (concern instanceof CommodityCompetitionConcern) {
            String commodityId = ((HasCommodityTarget)concern).getCommodityIds().get(0);
            int existingShare = EconomyInfoHelper.getInstance().getMarketShare(ai.getFactionId(), commodityId);
            priority.modifyFlat("existingMarketShare", -existingShare * 2, StrategicAI.getString("statExistingMarketShare", true));
        }
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canBuildEcon")) return false;
        if (!super.canUse(concern)) return false;

        // don't bother building a new econ industry if this is an import dependency concern and we're already producing the good
        /*
        if (concern instanceof ImportDependencyConcern) {
            String commodityId = ((HasCommodityTarget)concern).getCommodityIds().get(0);
            int existingProduction = EconomyInfoHelper.getInstance().getFactionCommodityProduction(ai.getFactionId(), commodityId);
            if (existingProduction > 0)
                return false;
        }
         */

        return concern instanceof HasCommodityTarget || concern instanceof HasIndustryToBuild;
    }
}
