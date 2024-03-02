package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityMarketDataAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.econ.ProductionMap;
import exerelin.utilities.StringHelper;
import lombok.Getter;

import java.awt.*;
import java.util.*;
import java.util.List;

public class UndersuppliedMarketConcern extends BaseStrategicConcern implements HasCommodityTarget {

    public static final int MAX_SIMULTANEOUS_CONCERNS = 3;

    public static float MARKET_PER_PRODUCER_THRESHOLD = 10000;

    @Getter protected CommodityBundle commodityBundle;

    @Override
    public boolean generate() {
        // only monopolist factions have this concern
        if (!DiplomacyTraits.hasTrait(ai.getFactionId(), DiplomacyTraits.TraitIds.MONOPOLIST))
            return false;

        Set alreadyConcerned = getExistingConcernItems();
        if (alreadyConcerned.size() >= MAX_SIMULTANEOUS_CONCERNS) return false;

        java.util.List<CommoditySpecAPI> commoditySpecs = Global.getSettings().getAllCommoditySpecs();
        MarketAPI testMarket = Global.getSector().getEconomy().getMarketsCopy().get(0);

        Map<String, Float> commoditiesToCheck = new HashMap<>();
        for (CommoditySpecAPI spec : commoditySpecs) {
            if (spec.isNonEcon() || spec.isMeta() || spec.isPersonnel()) continue;

            String commodityId = spec.getId();
            if (alreadyConcerned.contains(spec.getId())) continue;

            // market size
            CommodityMarketDataAPI data = testMarket.getCommodityData(commodityId).getCommodityMarketData();

            float size = data.getMarketValue();
            if (size <= 0) continue;
            List<EconomyInfoHelper.ProducerEntry> producers = EconomyInfoHelper.getInstance().getProducersByCommodity(commodityId);
            int numProducers = producers.size();
            // output units
            int totalOutput = 0;
            for (EconomyInfoHelper.ProducerEntry producer : producers) {
                totalOutput += producer.output;
            }

            // market per producer
            float marketPerProducer = size/numProducers;
            // market per unit
            float marketPerUnit = size/totalOutput;

            commoditiesToCheck.put(commodityId, marketPerProducer);
        }

        commodityBundle = pickCommodityBundle(commoditiesToCheck);
        if (commodityBundle != null) {
            float valuePerProducer = commodityBundle.totalPerProducerValue;
            priority.modifyFlat("value", valuePerProducer/500, StrategicAI.getString("statValue", true));
            reapplyPriorityModifiers();
            return true;
        }

        return false;
    }

    public CommodityBundle pickCommodityBundle(Map<String, Float> commodities) {
        Set<String> alreadyChecked = new HashSet<>();
        WeightedRandomPicker<CommodityBundle> picker = new WeightedRandomPicker<>();

        for (String commodity : commodities.keySet()) {
            if (alreadyChecked.contains(commodity)) continue;
            CommodityBundle bundle = new CommodityBundle();

            // get all commodities made by the industry that makes this one
            Set<String> relatedCommodities = ProductionMap.getCommoditiesFromSameIndustry(commodity, 1);
            for (String relCommod : relatedCommodities) {
                alreadyChecked.add(relCommod);
                Float thisValue = commodities.get(relCommod);
                if (thisValue == null) continue;
                bundle.commodities.add(relCommod);
                bundle.perProducerValuesByCommodity.put(relCommod, thisValue);
                bundle.totalPerProducerValue += thisValue;
            }
            if (bundle.totalPerProducerValue < MARKET_PER_PRODUCER_THRESHOLD) continue;

            picker.add(bundle, bundle.totalPerProducerValue);
        }

        return picker.pick();
    }

    @Override
    public void update() {
        MarketAPI testMarket = Global.getSector().getEconomy().getMarketsCopy().get(0);
        commodityBundle.totalPerProducerValue = 0;
        for (String commodityId : commodityBundle.commodities) {

            // market size
            CommodityMarketDataAPI data = testMarket.getCommodityData(commodityId).getCommodityMarketData();

            float size = data.getMarketValue();
            List<EconomyInfoHelper.ProducerEntry> producers = EconomyInfoHelper.getInstance().getProducersByCommodity(commodityId);
            if (producers.isEmpty()) {
                this.end();
                return;
            }

            int numProducers = producers.size();
            // output units
            int totalOutput = 0;
            for (EconomyInfoHelper.ProducerEntry producer : producers) {
                totalOutput += producer.output;
            }

            // market per producer
            float marketPerProducer = size/numProducers;
            // market per unit
            float marketPerUnit = size/totalOutput;

            commodityBundle.perProducerValuesByCommodity.put(commodityId, marketPerProducer);
            commodityBundle.totalPerProducerValue += marketPerProducer;
        }

        float valuePerProducer = commodityBundle.totalPerProducerValue;
        priority.modifyFlat("value", valuePerProducer/500, StrategicAI.getString("statValue", true));

        super.update();
    }

    @Override
    public List<String> getCommodityIds() {
        return new ArrayList<>(commodityBundle.commodities);
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        if (commodityBundle == null || commodityBundle.commodities.isEmpty()) return null;

        String str = getDef().desc;
        List<String> commodityNames = StringHelper.commodityIdListToCommodityNameList(commodityBundle.commodities);
        String commodityNames2 = StringHelper.writeStringCollection(commodityNames);
        str = StringHelper.substituteToken(str, "$commodities", commodityNames2);
        Color hl = Misc.getHighlightColor();
        return tooltip.addPara(str, pad, hl, commodityNames.toArray(new String[0]));
    }

    @Override
    public String getIcon() {
        if (commodityBundle != null && !commodityBundle.commodities.isEmpty())
            return Global.getSettings().getCommoditySpec(commodityBundle.commodities.iterator().next()).getIconName();
        return super.getIcon();
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        CommodityBundle bundle = commodityBundle;
        if (bundle == null && param instanceof CommodityBundle)
            bundle = (CommodityBundle)param;

        if (otherConcern instanceof UndersuppliedMarketConcern) {
            UndersuppliedMarketConcern umc = (UndersuppliedMarketConcern)otherConcern;
            if (bundle != null) {
                return bundle.commodities.equals(umc.getCommodityBundle());
            }
        }
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        Set<String> commodities = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            UndersuppliedMarketConcern idc = (UndersuppliedMarketConcern)concern;
            commodities.addAll(idc.getCommodityBundle().commodities);
        }
        return commodities;
    }

    @Override
    public boolean isValid() {
        return commodityBundle != null && !commodityBundle.commodities.isEmpty();
    }

    public static class CommodityBundle {
        Set<String> commodities = new HashSet<>();
        Map<String, Float> perProducerValuesByCommodity = new HashMap<>();
        float totalPerProducerValue;
    }
}
