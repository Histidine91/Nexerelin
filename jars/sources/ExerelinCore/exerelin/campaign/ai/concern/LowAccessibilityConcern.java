package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.campaign.econ.reach.CommodityMarketData;
import exerelin.campaign.ai.action.industry.BuildForAccessibilityAction;
import exerelin.utilities.NexUtilsFaction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class LowAccessibilityConcern extends BaseStrategicConcern implements HasIndustryToBuild {

    public static final float BASE_DESIRED_QUALITY = 0.2f;
    public static final float BASE_PRIORITY = 150;

    protected String commodityId;
    protected int insufficiencyAmount;

    @Override
    public boolean generate() {
        List<InsufficiencyEntry> targetsSorted = new ArrayList<>();

        Set<Object> alreadyConcernMarkets = getExistingConcernItems();


        for (MarketAPI market : NexUtilsFaction.getFactionMarkets(ai.getFactionId(), true)) {
            if (alreadyConcernMarkets.contains(market)) continue;
            if (isSpaceportOrMegaportDisrupted(market)) continue;
            //int capacity = CommodityMarketData.getShippingCapacity(market, false);
            Pair<String, Integer> insufficiency = getHighestInsufficiency(market);
            if (insufficiency == null) continue;

            InsufficiencyEntry entry = new InsufficiencyEntry(market, insufficiency.one, insufficiency.two);
            targetsSorted.add(entry);
            Collections.sort(targetsSorted);
        }

        if (targetsSorted.isEmpty()) return false;

        for (InsufficiencyEntry entry : targetsSorted) {
            if (!BuildForAccessibilityAction.canDoAnything(market)) continue;

            this.market = entry.market;
            this.commodityId = entry.commodityId;
            this.insufficiencyAmount = entry.amount;
            return true;
        }

        return false;
    }

    @Override
    public boolean isValid() {
        CommodityOnMarketAPI com = market.getCommodityData(commodityId);
        if (getInsufficiencyForCommodity(com) <= 0) return false;

        return true;
    }

    public void update() {
        Pair<String, Integer> insufficiency = getHighestInsufficiency(market);
        if (insufficiency == null) {
            end();
            return;
        }
        this.commodityId = insufficiency.one;
        this.insufficiencyAmount = insufficiency.two;
    }

    @Nullable
    protected Pair<String, Integer> getHighestInsufficiency(MarketAPI market) {
        int highest = 0;
        String highestCommod = null;

        // look for insufficient accessibility mod and check what the export limited mod is (seems there isn't one)

        for (CommodityOnMarketAPI com : market.getAllCommodities()) {
            int insufficiency = getInsufficiencyForCommodity(com);

            if (insufficiency > highest) {
                highest = insufficiency;
                highestCommod = com.getId();
            }
        }
        if (highestCommod == null) return null;

        return new Pair<>(highestCommod, highest);
    }

    protected int getInsufficiencyForCommodity(CommodityOnMarketAPI com) {
        int usefulExports = Math.min(com.getMaxSupply(), com.getAvailable());
        int expInsufficiency = usefulExports - com.getAvailable();
        int impInsufficiency = com.getAvailableStat().getFlatMods().containsKey(CommodityMarketData.KEY_LOWACCESS) ?
                Math.round(com.getAvailableStat().getFlatMods().get(CommodityMarketData.KEY_LOWACCESS).value) : 0;

        int insufficiency = Math.max(expInsufficiency, impInsufficiency);

        return insufficiency;
    }

    public boolean isSpaceportOrMegaportDisrupted(MarketAPI market) {
        Industry ind = market.getIndustry(Industries.SPACEPORT);
        if (ind != null && ind.isDisrupted()) return true;
        ind = market.getIndustry(Industries.MEGAPORT);
        if (ind != null && ind.isDisrupted()) return true;

        return false;
    }

    @Override
    public void reapplyPriorityModifiers() {
        super.reapplyPriorityModifiers();

        // TODO: modifier based on the commodity type and the magnitude of deficit
    }

    @Override
    public String getIndustryIdToBuild() {
        return Industries.SPACEPORT;
    }

    public static class InsufficiencyEntry implements Comparable<InsufficiencyEntry> {
        public MarketAPI market;
        public String commodityId;
        public int amount;

        public InsufficiencyEntry(MarketAPI market, String commodityId, int amount) {
            this.market = market;
            this.commodityId = commodityId;
            this.amount = amount;
        }

        @Override
        public int compareTo(@NotNull InsufficiencyEntry other) {
            return Integer.compare(other.amount, this.amount);
        }
    }
}
