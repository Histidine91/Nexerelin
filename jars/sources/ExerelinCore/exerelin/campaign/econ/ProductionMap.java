package exerelin.campaign.econ;

import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;

import java.util.*;

/**
 * Maps out the game's production chains (e.g. mining makes ore, which refining turns into metals, which is then used by heavy industry)
 */
public class ProductionMap {

    public static final Map<String, IndustryEntry> INDUSTRIES = new HashMap<>();
    public static final Map<String, CommodityEntry> COMMODITIES = new HashMap<>();
    public static final Map<String, ConditionEntry> CONDITIONS = new HashMap<>();

    static {
        addExtraction(Industries.MINING, Commodities.ORE, Conditions.ORE_SPARSE);
        addExtraction(Industries.MINING, Commodities.ORE, Conditions.ORE_MODERATE);
        addExtraction(Industries.MINING, Commodities.ORE, Conditions.ORE_ABUNDANT);
        addExtraction(Industries.MINING, Commodities.ORE, Conditions.ORE_RICH);
        addExtraction(Industries.MINING, Commodities.ORE, Conditions.ORE_ULTRARICH);

        addExtraction(Industries.MINING, Commodities.RARE_ORE, Conditions.RARE_ORE_SPARSE);
        addExtraction(Industries.MINING, Commodities.RARE_ORE, Conditions.RARE_ORE_MODERATE);
        addExtraction(Industries.MINING, Commodities.RARE_ORE, Conditions.RARE_ORE_ABUNDANT);
        addExtraction(Industries.MINING, Commodities.RARE_ORE, Conditions.RARE_ORE_RICH);
        addExtraction(Industries.MINING, Commodities.RARE_ORE, Conditions.RARE_ORE_ULTRARICH);

        addExtraction(Industries.MINING, Commodities.ORGANICS, Conditions.ORGANICS_TRACE);
        addExtraction(Industries.MINING, Commodities.ORGANICS, Conditions.ORGANICS_COMMON);
        addExtraction(Industries.MINING, Commodities.ORGANICS, Conditions.ORGANICS_ABUNDANT);
        addExtraction(Industries.MINING, Commodities.ORGANICS, Conditions.ORGANICS_PLENTIFUL);

        addExtraction(Industries.MINING, Commodities.VOLATILES, Conditions.VOLATILES_TRACE);
        addExtraction(Industries.MINING, Commodities.VOLATILES, Conditions.VOLATILES_DIFFUSE);
        addExtraction(Industries.MINING, Commodities.VOLATILES, Conditions.VOLATILES_ABUNDANT);
        addExtraction(Industries.MINING, Commodities.VOLATILES, Conditions.VOLATILES_PLENTIFUL);

        addInput(Industries.MINING, Commodities.DRUGS);
        addInput(Industries.MINING, Commodities.HEAVY_MACHINERY);

        addExtraction(Industries.FARMING, Commodities.FOOD, Conditions.FARMLAND_POOR);
        addExtraction(Industries.FARMING, Commodities.FOOD, Conditions.FARMLAND_ADEQUATE);
        addExtraction(Industries.FARMING, Commodities.FOOD, Conditions.FARMLAND_RICH);
        addExtraction(Industries.FARMING, Commodities.FOOD, Conditions.FARMLAND_BOUNTIFUL);
        addExtraction(Industries.AQUACULTURE, Commodities.FOOD, Conditions.WATER_SURFACE);
        addInput(Industries.FARMING, Commodities.HEAVY_MACHINERY);
        addInput(Industries.AQUACULTURE, Commodities.HEAVY_MACHINERY);

        addInput(Industries.LIGHTINDUSTRY, Commodities.ORGANICS);
        addOutput(Industries.LIGHTINDUSTRY, Commodities.DOMESTIC_GOODS);
        addOutput(Industries.LIGHTINDUSTRY, Commodities.LUXURY_GOODS);
        addOutput(Industries.LIGHTINDUSTRY, Commodities.DRUGS);

        addInput(Industries.REFINING, Commodities.ORE);
        addInput(Industries.REFINING, Commodities.RARE_ORE);
        addInput(Industries.REFINING, Commodities.HEAVY_MACHINERY);
        addOutput(Industries.REFINING, Commodities.METALS);
        addOutput(Industries.REFINING, Commodities.RARE_METALS);

        addInput(Industries.HEAVYINDUSTRY, Commodities.METALS);
        addInput(Industries.HEAVYINDUSTRY, Commodities.RARE_METALS);
        addOutput(Industries.HEAVYINDUSTRY, Commodities.SUPPLIES);
        addOutput(Industries.HEAVYINDUSTRY, Commodities.HEAVY_MACHINERY);
        addOutput(Industries.HEAVYINDUSTRY, Commodities.HAND_WEAPONS);
        addOutput(Industries.HEAVYINDUSTRY, Commodities.SHIP_WEAPONS);

        cloneIndustry(Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS);
        cloneIndustry(Industries.HEAVYINDUSTRY, "ms_modularFac");

        addInput(Industries.FUELPROD, Commodities.VOLATILES);
        addInput(Industries.FUELPROD, Commodities.HEAVY_MACHINERY);
        addOutput(Industries.FUELPROD, Commodities.FUEL);
    }

    public static void addInput(String industry, String commodity) {
        getOrCreateIndustry(industry).inputs.add(commodity);
        getOrCreateCommodity(commodity).consumers.add(industry);
    }

    public static void addOutput(String industry, String commodity) {
        getOrCreateIndustry(industry).outputs.add(commodity);
        getOrCreateCommodity(commodity).producers.add(industry);
    }

    public static void addExtraction(String industry, String commodity, String condition) {
        getOrCreateIndustry(industry).outputs.add(commodity);
        getOrCreateIndustry(industry).conditionsToOutputs.put(condition, commodity);
        getOrCreateIndustry(industry).isExtractive = true;
        getOrCreateCommodity(commodity).isExtracted = true;
        getOrCreateCommodity(commodity).producers.add(industry);
        getOrCreateCommodity(commodity).conditions.add(condition);
        getOrCreateCondition(condition).addIndustryOutput(industry, commodity);
    }

    public static IndustryEntry getOrCreateIndustry(String id) {
        if (!INDUSTRIES.containsKey(id)) {
            INDUSTRIES.put(id, new IndustryEntry(id));
        }
        return INDUSTRIES.get(id);
    }

    public static CommodityEntry getOrCreateCommodity(String id) {
        if (!COMMODITIES.containsKey(id)) {
            COMMODITIES.put(id, new CommodityEntry(id));
        }
        return COMMODITIES.get(id);
    }

    public static ConditionEntry getOrCreateCondition(String id) {
        if (!CONDITIONS.containsKey(id)) {
            CONDITIONS.put(id, new ConditionEntry(id));
        }
        return CONDITIONS.get(id);
    }

    public static void cloneIndustry(String industry, String newIndustry) {
        IndustryEntry oldInd = getOrCreateIndustry(industry);
        IndustryEntry newInd = getOrCreateIndustry(newIndustry);
        newInd.inputs.addAll(oldInd.inputs);
        newInd.outputs.addAll(oldInd.outputs);
    }

    /**
     * For example, "if we decide to produce more heavy machinery, what else will we also produce at the same time?"
     * @param commodity
     * @param industryTypesToCheck Set to 1 to check only the first industry in the list of producers, for instance.
     *                             The rationale for this being, if we're going to build a Mining industry this doesn't
     *                             make it think we'll also get the food, volatiles and such from Void Extraction.
     * @return
     */
    public static Set<String> getCommoditiesFromSameIndustry(String commodity, int industryTypesToCheck) {
        Set<String> results = new HashSet<>();
        results.add(commodity);
        CommodityEntry ce = COMMODITIES.get(commodity);
        if (ce == null) return results;

        int industryTypes = 0;
        for (String industry : ce.producers) {
            IndustryEntry ie = INDUSTRIES.get(industry);
            results.addAll(ie.outputs);

            industryTypes++;
            if (industryTypes >= industryTypesToCheck) break;
        }
        return results;
    }


    public static class IndustryEntry {
        public String id;
        public Set<String> inputs = new LinkedHashSet<>();
        public Set<String> outputs = new LinkedHashSet<>();
        public Map<String, String> conditionsToOutputs = new HashMap<>();
        public boolean isExtractive;

        public IndustryEntry(String id) {
            this.id = id;
        }

        /**
         * Gets the conditions which this industry extracts to turn into {@code outputCommodity}.
         * @param outputCommodity
         * @return
         */
        public List<String> getConditionsForOutput(String outputCommodity) {
            List<String> list = new ArrayList<>();
            for (String condition : conditionsToOutputs.keySet()) {
                String thisCommodity = conditionsToOutputs.get(condition);
                if (thisCommodity.equals(outputCommodity)) {
                    list.add(condition);
                }
            }
            return list;
        }
    }

    public static class CommodityEntry {
        public String id;
        public Set<String> consumers = new LinkedHashSet<>();
        public Set<String> producers = new LinkedHashSet<>();
        public Set<String> conditions = new LinkedHashSet<>();
        public boolean isExtracted;

        public CommodityEntry(String id) {
            this.id = id;
        }
    }

    public static class ConditionEntry {
        public String id;
        public Set<String> allOutputs = new LinkedHashSet<>();
        public Map<String, List<String>> outputsByIndustry = new LinkedHashMap<>();

        public ConditionEntry (String id) {
            this.id = id;
        }

        public void addIndustryOutput(String industry, String commodity) {
            allOutputs.add(commodity);
            if (!outputsByIndustry.containsKey(industry)) {
                outputsByIndustry.put(industry, new ArrayList<String>());
            }
            outputsByIndustry.get(industry).add(commodity);
        }
    }
}
