package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.SAIUtils;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.fleet.OffensiveFleetAction;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.List;
import java.util.*;

@Log4j
public class ImportDependencyConcern extends BaseStrategicConcern implements HasCommodityTarget {

    @Getter protected String commodityId;
    @Getter protected int required;

    public static final Map<String, Float> MULTIPLIERS = new HashMap<>();
    static {
        MULTIPLIERS.put(Commodities.FOOD, 2f);
        MULTIPLIERS.put(Commodities.SUPPLIES, 2f);
        MULTIPLIERS.put(Commodities.HAND_WEAPONS, 2f);
        MULTIPLIERS.put(Commodities.SHIP_WEAPONS, 2f);
        MULTIPLIERS.put(Commodities.HEAVY_MACHINERY, 2f);
        MULTIPLIERS.put(Commodities.METALS, 1.5f);
        MULTIPLIERS.put(Commodities.RARE_METALS, 1.5f);
        MULTIPLIERS.put(Commodities.FUEL, 1.5f);
    }

    @Override
    public boolean generate() {
        Set alreadyConcerned = getExistingConcernItems();

        //log.info("Generating import dependency concern for " + ai.getFaction().getDisplayName());
        //log.info("Import dependency existing concerns: " + alreadyConcerned + ", size " + alreadyConcerned.size());

        Map<String, Integer> imports = EconomyInfoHelper.getInstance().getCommoditiesImportedByFaction(ai.getFactionId());
        Map<String, Integer> trueImports = new HashMap<>();
        Map<String, Integer> production = EconomyInfoHelper.getInstance().getCommoditiesProducedByFaction(ai.getFactionId());

        for (String commodityId : imports.keySet()) {
            if (alreadyConcerned.contains(commodityId)) continue;

            int rawImport = imports.get(commodityId);
            // don't bother making a concern if imports are less than 4 units (modified by the commodity's output modifier)
            if (rawImport < 4 + EconomyInfoHelper.getCommodityOutputModifier(commodityId))
                continue;
            Integer prod = production.get(commodityId);
            if (prod != null && prod >= rawImport) continue;
            trueImports.put(commodityId, rawImport);
        }

        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        for (String commodityId : trueImports.keySet()) {
            int importVol = trueImports.get(commodityId);
            picker.add(commodityId, importVol);
        }

        commodityId = picker.pick();
        if (commodityId != null) {
            required = Math.round(picker.getWeight(commodityId));
            float thisProd = EconomyInfoHelper.getInstance().getFactionCommodityProduction(ai.getFactionId(), commodityId);

            float prio = required * 10;
            float prioMinus = thisProd * 5;

            priority.modifyFlat("importVolume", prio, StrategicAI.getString("statImportVolume", true));
            priority.modifyFlat("domesticProduction", -prioMinus, StrategicAI.getString("statDomesticProduction", true));

            reapplyPriorityModifiers();

            // import dependency concerns tend to get pretty cluttery, so don't even bother unless we're likely to do something about it
            if (priority.getModifiedValue() < SAIConstants.MIN_CONCERN_PRIORITY_TO_ACT)
                return false;

            return true;
        }

        return false;
    }

    @Override
    public void reapplyPriorityModifiers() {
        super.reapplyPriorityModifiers();

        if (MULTIPLIERS.containsKey(commodityId)) {
            priority.modifyMult("commodityType", MULTIPLIERS.get(commodityId), StrategicAI.getString("statCommodityType", true));
        }
    }

    /**
     * Gets a list of enemy faction markets producing enough of the commodity to cover our imports.
     * @return
     */
    @Override
    public List<MarketAPI> getMarkets() {
        FactionAPI us = ai.getFaction();
        int imports = EconomyInfoHelper.getInstance().getFactionCommodityImports(ai.getFactionId(), commodityId);
        List<MarketAPI> markets = new ArrayList<>();
        List<EconomyInfoHelper.ProducerEntry> competitors = EconomyInfoHelper.getInstance().getCompetingProducers(
                ai.getFactionId(), commodityId, imports);
        for (EconomyInfoHelper.ProducerEntry entry : competitors) {
            if (!entry.market.getFaction().isHostileTo(us)) continue;
            markets.add(entry.market);
        }
        return markets;
    }

    @Override
    public boolean canTakeAction(StrategicAction action) {
        if (action instanceof OffensiveFleetAction && getMarkets().isEmpty()) {
            return false;
        }

        return super.canTakeAction(action);
    }

    @Override
    public void update() {
        int threshold = 4 + EconomyInfoHelper.getCommodityOutputModifier(commodityId);
        int imports = EconomyInfoHelper.getInstance().getFactionCommodityImports(ai.getFactionId(), commodityId);
        if (imports < threshold) {
            end();
            return;
        }
        int production = EconomyInfoHelper.getInstance().getFactionCommodityProduction(ai.getFactionId(), commodityId);
        if (production >= imports) {
            end();
            return;
        }

        required = imports;
        float prio = required * 10;
        float prioMinus = production * 5;

        priority.modifyFlat("importVolume", prio, StrategicAI.getString("statImportVolume", true));
        priority.modifyFlat("domesticProduction", -prioMinus, StrategicAI.getString("statDomesticProduction", true));
        reapplyPriorityModifiers();

        // import dependency concerns tend to get pretty cluttery, so don't even bother unless we're likely to do something about it
        if (priority.getModifiedValue() < SAIConstants.MIN_CONCERN_PRIORITY_TO_ACT) {
            end();
            return;
        }

        SAIUtils.reportConcernUpdated(ai, this);
    }

    @Override
    public List<String> getCommodityIds() {
        return new ArrayList<>(Arrays.asList(new String[] {commodityId}));
    }

    @Override
    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        if (commodityId == null) return null;
        String str = getDef().desc;
        str = StringHelper.substituteToken(str, "$commodity", StringHelper.getCommodityName(commodityId));
        str = StringHelper.substituteToken(str, "$amount", required + "");
        Color hl = Misc.getHighlightColor();
        return tooltip.addPara(str, pad, hl, required + "");
    }

    @Override
    public String getName() {
        return String.format("%s - %s", super.getName(), StringHelper.getCommodityName(commodityId));
    }

    @Override
    public String getDisplayName() {
        return super.getName();
    }

    @Override
    public String getIcon() {
        if (commodityId != null) return Global.getSettings().getCommoditySpec(commodityId).getIconName();
        return super.getIcon();
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        String commodityId = this.commodityId;
        if (commodityId == null && param instanceof String)
            commodityId = (String)param;

        if (otherConcern instanceof ImportDependencyConcern) {
            ImportDependencyConcern idc = (ImportDependencyConcern)otherConcern;
            return idc.commodityId == commodityId;
        }
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        Set<String> commodities = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            ImportDependencyConcern idc = (ImportDependencyConcern)concern;
            commodities.add(idc.commodityId);
        }
        return commodities;
    }

    @Override
    public boolean isValid() {
        return commodityId != null;
    }
}
