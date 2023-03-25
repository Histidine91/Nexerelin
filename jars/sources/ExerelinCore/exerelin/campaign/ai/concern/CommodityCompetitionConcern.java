package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.SAIConstants;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.utilities.StringHelper;
import lombok.Getter;

import java.awt.*;
import java.util.*;
import java.util.List;

public class CommodityCompetitionConcern extends BaseStrategicConcern implements HasCommodityTarget {

    public static final int MAX_SIMULTANEOUS_CONCERNS = 4;

    @Getter protected String commodityId;
    @Getter protected int competitorShare;

    @Override
    public boolean generate() {
        Set alreadyConcerned = getExistingConcernItems();
        if (alreadyConcerned.size() >= MAX_SIMULTANEOUS_CONCERNS) return false;
        String factionId = ai.getFactionId();
        EconomyInfoHelper helper = EconomyInfoHelper.getInstance();

        //Map<String, Integer> imports = EconomyInfoHelper.getInstance().getCommoditiesImportedByFaction(ai.getFaction().getId());
        //Map<String, Integer> trueImports = new HashMap<>();
        //Map<String, Integer> production = EconomyInfoHelper.getInstance().getCommoditiesProducedByFaction(ai.getFaction().getId());

        List<EconomyInfoHelper.ProducerEntry> competitors = helper.getCompetingProducers(factionId, 6, true);
        WeightedRandomPicker<EconomyInfoHelper.ProducerEntry> picker1 = new WeightedRandomPicker<>();

        for (EconomyInfoHelper.ProducerEntry pe : competitors) {
            if (alreadyConcerned.contains(pe.factionId)) continue;
            if (alreadyConcerned.contains(pe.commodityId)) continue;
            picker1.add(pe, pe.output);
        }

        EconomyInfoHelper.ProducerEntry pePicked = picker1.pick();
        if (pePicked == null) return false;

        commodityId = pePicked.commodityId;
        int ourShare = helper.getMarketShare(ai.getFaction(), commodityId);

        List<Pair<FactionAPI, Integer>> competitors2 = helper.getCompetingProducerFactions(factionId, commodityId);

        WeightedRandomPicker<Pair<FactionAPI, Integer>> picker2 = new WeightedRandomPicker<>();
        for (Pair<FactionAPI, Integer> competitorEntry : competitors2) {
            int share = competitorEntry.two;
            if (share < ourShare/2) continue;
            if (share < SAIConstants.MIN_COMPETITOR_SHARE) continue;
            picker2.add(competitorEntry, share);
        }

        Pair<FactionAPI, Integer> result = picker2.pick();
        if (result == null) return false;

        this.competitorShare = result.two;
        faction = result.one;
        updatePriority();
        return true;
    }

    @Override
    public void update() {
        EconomyInfoHelper helper = EconomyInfoHelper.getInstance();
        int ourShare = helper.getMarketShare(ai.getFaction(), commodityId);
        int theirShare = helper.getMarketShare(faction.getId(), getCommodityId());
        if (theirShare < ourShare/2) {
            end();
            return;
        }
        updatePriority();
    }

    protected void updatePriority() {
        priority.modifyFlat("competingShare", competitorShare * 6, StrategicAI.getString("statCompetingShare", true));
        reapplyPriorityModifiers();
    }

    @Override
    public List<MarketAPI> getMarkets() {
        return super.getMarkets();
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
        str = StringHelper.substituteToken(str, "$theirShare", competitorShare + "");
        str = StringHelper.substituteFactionTokens(str, faction);
        Color hl = Misc.getHighlightColor();
        LabelAPI label = tooltip.addPara(str, pad, hl, faction.getDisplayName(), competitorShare + "");
        label.setHighlightColors(faction.getBaseUIColor(), hl);
        return label;
    }

    @Override
    public String getIcon() {
        if (commodityId != null) return Global.getSettings().getCommoditySpec(commodityId).getIconName();
        return super.getIcon();
    }

    @Override
    public String getName() {
        return String.format("%s: %s", super.getName(), faction.getDisplayName());  //, StringHelper.getCommodityName(commodityId));
    }

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        String commodityId = this.commodityId;
        if (commodityId == null && param instanceof String)
            commodityId = (String)param;

        if (otherConcern instanceof CommodityCompetitionConcern) {
            CommodityCompetitionConcern idc = (CommodityCompetitionConcern)otherConcern;
            return idc.commodityId == commodityId && idc.faction == faction;
        }
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        Set<String> commoditiesAndFactions = new HashSet<>();
        for (StrategicConcern concern : getExistingConcernsOfSameType()) {
            CommodityCompetitionConcern ccc = (CommodityCompetitionConcern)concern;
            commoditiesAndFactions.add(ccc.commodityId);
            commoditiesAndFactions.add(ccc.faction.getId());
        }
        return commoditiesAndFactions;
    }

    @Override
    public boolean isValid() {
        return commodityId != null && faction != null && competitorShare > 0;
    }
}
