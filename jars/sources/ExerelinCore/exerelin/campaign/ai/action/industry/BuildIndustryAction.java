package exerelin.campaign.ai.action.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.ProductionMap;
import exerelin.world.ExerelinProcGen;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.IndustryClassGen;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.util.Collection;
import java.util.List;

// abstract for now?
@Log4j
public abstract class BuildIndustryAction extends BaseStrategicAction implements StrategicActionDelegate {

    /*
        How should it work?

            military base action: build patrol HQ, if there's already one and we have a free slot, upgrade to military base
            ground def action: build ground def or upgrade to heavy batteries
            station action: you get the idea

            prioritizing between the three: if either mil base tier, ground def tier or station tier are below par for the market's size, build/upgrade that one
                make a public method in NexMarketBuilder to get the par size
            if two or more can be upgraded, calc priority based on... hmm what, maybe just favor station -> batteries -> base? seems right

            if no market specified, pick which one?


            economic building action: get the commodity the concern wants, find a building (from EconInfoHelper?) that makes that commodity,
            use the marketBuilder class to find where to put it (if it doesn't already have a market specified, else use the class to validate location)
     */

    @Getter protected Industry industryUnderConstruction;
    @Getter protected MarketAPI market;
    @Getter protected String industryId;

    @Override
    public boolean generate() {
        market = concern.getMarket();
        industryId = pickIndustry(market);

        if (industryId == null) return false;
        if (market == null) market = pickMarketFallback(industryId);
        //log.info(String.format("Concern is %s, industry ID is %s, market is %s", concern, industryId, market != null ? market.getId() : "null"));
        if (market == null) return false;

        delegate = this;
        status = ActionStatus.STARTING;
        return buildOrUpgrade();
    }

    /**
     * Actually build the industry. Note: may not create an actual industry just yet, instead it may go into the Nex construction queue.
     */
    protected abstract boolean buildOrUpgrade();

    protected abstract String pickIndustry(MarketAPI market);

    protected abstract MarketAPI pickMarketFallback(String industryId);

    /*
    protected boolean isUpgradingFromNonIndustryToIndustry(MarketAPI market, String industryId) {
        IndustrySpecAPI ispec = Global.getSettings().getIndustrySpec(industryId);
        String upId = ispec.getUpgrade();
        String downId = ispec.getDowngrade();
        if (upId == null) return false;
        IndustrySpecAPI upspec = Global.getSettings().getIndustrySpec(upId);

        // Does the market already have a building within the upgrade chain (e.g. for Military Base check, both Patrol HQ and Military Base count)?
        boolean doesMarketHaveMemberOfUpgradeChain = market.hasIndustry(industryId) || market.hasIndustry(downId);

        if (doesMarketHaveMemberOfUpgradeChain) {
            return upspec.hasTag(Industries.TAG_INDUSTRY) && !ispec.hasTag(Industries.TAG_INDUSTRY);
        } else {
            return ispec.hasTag(Industries.TAG_INDUSTRY);
        }
    }
    */

    protected boolean willExceedMaxIndustries(MarketAPI market, String industryId) {
        IndustrySpecAPI ispec = Global.getSettings().getIndustrySpec(industryId);

        // not an industry
        if (!ispec.hasTag(Industries.TAG_INDUSTRY)) return false;
        // have free slots anyway
        if (Misc.getNumIndustries(market) < Misc.getMaxIndustries(market)) return false;

        // can we upgrade?
        // actually upgrading should be its own action maybe, this is getting too complicated


        return true;
    }

    /**
     * Tries all the provided producer industries on the market and returns the one with the highest score, along with its score.
     * Uses the market builder's {@code IndustryClassGen} to determine validity and score.
     * @param market
     * @param producerIds The industry IDs being considered for construction.
     * @param commodityId The commodity to be produced, if any. When building primary industries, used for checking whether the needed market condition exists.
     * @return
     */
    protected Pair<String, Float> getBestProducerForMarket(MarketAPI market, Collection<String> producerIds, String commodityId) {
        String best = null;
        float bestScore = 0;
        ProcGenEntity entity = ExerelinProcGen.createEntityData(market.getPrimaryEntity());

        for (String producerId : producerIds) {
            // first check local industry limit
            if (willExceedMaxIndustries(market, producerId)) continue;

            // for extraction industries, check if the relevant resource actually exists
            if (commodityId != null) {
                ProductionMap.IndustryEntry ie = ProductionMap.getOrCreateIndustry(producerId);
                if (ie.isExtractive && !doesMarketHaveConditions(market, ie.getConditionsForOutput(commodityId))) {
                    continue;
                }
            }

            // use the generator class to see if we can/should build that industry here
            IndustryClassGen gen = NexMarketBuilder.getIndustryClassesByIndustryId().get(producerId);
            if (!gen.canApply(entity)) continue;
            float score = gen.getWeight(entity);
            score = modifyProducerScore(producerId, market, score);

            if (score <= 0) continue;

            if (score > bestScore) {
                bestScore = score;
                best = producerId;
            }
        }

        if (best == null) return null;
        return new Pair<>(best, bestScore);
    }

    protected float modifyProducerScore(String industryId, MarketAPI market, float score) {
        return score;
    }

    protected boolean doesMarketHaveConditions(MarketAPI market, List<String> conditions) {
        for (String cond : conditions) {
            if (market.hasCondition(cond)) return true;
        }
        return false;
    }

    /**
     * Tries the provided producer industries on all of the specified markets and returns the one with the highest score, along with its score.
     * Uses the market builder's {@code IndustryClassGen} to determine validity and score.
     * @param producerId The industry ID being considered for construction.
     * @param markets
     * @param commodityId The commodity to be produced, if any. When building primary industries, used for checking whether the needed market condition exists.
     * @return
     */
    protected Pair<MarketAPI, Float> getBestMarketForProducer(String producerId, Collection<MarketAPI> markets, String commodityId) {
        MarketAPI best = null;
        float bestScore = 0;

        for (MarketAPI market : markets) {
            if (market.isPlayerOwned() && !market.getFaction().isPlayerFaction()) continue;

            ProcGenEntity entity = ExerelinProcGen.createEntityData(market.getPrimaryEntity());
            // first check local industry limit
            if (willExceedMaxIndustries(market, producerId)) continue;

            // for extraction industries, check if the relevant resource actually exists
            if (commodityId != null) {
                ProductionMap.IndustryEntry ie = ProductionMap.getOrCreateIndustry(producerId);
                if (ie.isExtractive && !doesMarketHaveConditions(market, ie.getConditionsForOutput(commodityId))) {
                    continue;
                }
            }

            // use the generator class to see if we can/should build that industry here
            IndustryClassGen gen = NexMarketBuilder.getIndustryClassesByIndustryId().get(producerId);
            if (gen == null) {
                log.error("IndustryClassGen not found for industry " + producerId);
            }
            if (!gen.canApply(entity)) continue;
            float score = gen.getWeight(entity);
            if (score <= 0) continue;

            if (score > bestScore) {
                bestScore = score;
                best = market;
            }
        }

        if (best == null) return null;
        return new Pair<>(best, bestScore);
    }

    @Override
    public void advance(float days) {
        super.advance(days);

        if (market != null && !market.isInEconomy()) {
            this.end(ActionStatus.FAILURE);
        }

        if (market != null && industryUnderConstruction == null) {
            // check if there's a correct industry under construction, if so use that
            industryUnderConstruction = market.getIndustry(industryId);
            status = ActionStatus.IN_PROGRESS;
        }

        if (industryUnderConstruction != null && !industryUnderConstruction.isBuilding() && !industryUnderConstruction.isUpgrading()) {
            //Global.getLogger(this.getClass()).info("boom chaka chaka wow ");
            this.end(ActionStatus.SUCCESS);
        }
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        MarketAPI market = concern.getMarket();
        if (market != null && market.isPlayerOwned() && !market.getFaction().isPlayerFaction()) return false;

        return !ai.getFaction().isPlayerFaction();
    }

    @Override
    public String getName() {
        String indName = getIndustryName();
        String name;
        if (indName != null) name = String.format(StrategicAI.getString("actionName_build", true), indName);
        else name = getDef().name;

        if (market != null) name += ": " + market.getName();
        return name;
    }

    public String getIndustryName() {
        if (industryUnderConstruction != null) return industryUnderConstruction.getCurrentName();
        if (industryId != null) return Global.getSettings().getIndustrySpec(industryId).getName();
        return null;
    }

    @Override
    public ActionStatus getStrategicActionStatus() {
        return this.status;
    }

    @Override
    public float getStrategicActionDaysRemaining() {
        if (industryUnderConstruction != null) return industryUnderConstruction.getBuildTime() * (1 - industryUnderConstruction.getBuildOrUpgradeProgress());
        return -1;
    }

    @Override
    public StrategicAction getStrategicAction() {
        return this;
    }

    @Override
    public void setStrategicAction(StrategicAction action) { }

    @Override
    public void abortStrategicAction() {
        if (industryUnderConstruction != null) {
            industryUnderConstruction.getMarket().removeIndustry(industryUnderConstruction.getId(), null, false);
        } else {
            ColonyManager.getManager().removeQueuedIndustry(industryId, market);
        }
    }

    @Override
    public String getStrategicActionName() {
        return getName();
    }
}
