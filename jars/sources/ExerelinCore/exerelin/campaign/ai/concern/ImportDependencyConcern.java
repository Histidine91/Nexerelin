package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.*;
import java.util.List;

@Log4j
public class ImportDependencyConcern extends BaseStrategicConcern {

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

        log.info("Generating import dependency concern for " + ai.getFaction().getDisplayName());
        log.info("Import dependency existing concerns: " + alreadyConcerned + ", size " + alreadyConcerned.size());

        Map<String, Integer> imports = EconomyInfoHelper.getInstance().getCommoditiesImportedByFaction(ai.getFaction().getId());
        Map<String, Integer> trueImports = new HashMap<>();
        Map<String, Integer> production = EconomyInfoHelper.getInstance().getCommoditiesProducedByFaction(ai.getFaction().getId());

        for (String commodityId : imports.keySet()) {
            if (alreadyConcerned.contains(commodityId)) continue;

            int rawImport = imports.get(commodityId);
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
            float prio = required * 10;
            if (MULTIPLIERS.containsKey(commodityId)) prio *= MULTIPLIERS.get(commodityId);
            priority.modifyFlat("importVolume", prio, StrategicAI.getString("statImportVolume", true));
            return true;
        }

        return false;
    }

    protected boolean isRelyOnImports(String commodityId) {
        int threshold = 4 + EconomyInfoHelper.getCommodityOutputModifier(commodityId);
        int imports = EconomyInfoHelper.getInstance().getFactionCommodityImports(ai.getFaction().getId(), commodityId);
        if (imports < threshold) {
            return false;
        }
        int production = EconomyInfoHelper.getInstance().getFactionCommodityProduction(ai.getFaction().getId(), commodityId);
        if (production > imports) return false;

        return true;
    }

    @Override
    public void update() {
        int threshold = 4 + EconomyInfoHelper.getCommodityOutputModifier(commodityId);
        int imports = EconomyInfoHelper.getInstance().getFactionCommodityImports(ai.getFaction().getId(), commodityId);
        if (imports < threshold) {
            end();
            return;
        }
        int production = EconomyInfoHelper.getInstance().getFactionCommodityProduction(ai.getFaction().getId(), commodityId);
        if (production > imports) {
            end();
            return;
        }

        required = imports;
        float prio = required * 10;
        if (MULTIPLIERS.containsKey(commodityId)) prio *= MULTIPLIERS.get(commodityId);
        priority.modifyFlat("importVolume", prio, StrategicAI.getString("statImportVolume", true));
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
