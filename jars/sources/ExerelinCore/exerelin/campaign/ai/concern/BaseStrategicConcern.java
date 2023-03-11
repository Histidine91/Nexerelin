package exerelin.campaign.ai.concern;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.*;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.Alliance.Alignment;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.ui.FramedCustomPanelPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Log4j
public abstract class BaseStrategicConcern implements StrategicConcern {

    @Getter @Setter protected String id;
    protected StrategicAI ai;
    protected StrategicAIModule module;
    @Getter @Setter protected MutableStat priority = new MutableStat(0);
    @Getter protected MarketAPI market;
    protected FactionAPI faction;
    @Getter protected StrategicAction currentAction;
    @Getter protected boolean ended;
    @Getter protected float actionCooldown;

    @Override
    public void setAI (StrategicAI ai, StrategicAIModule module) {
        this.ai = ai;
        this.module = module;
    }
    @Override
    public StrategicAI getAI() {
        return ai;
    }

    @Override
    public abstract boolean generate();

    @Override
    public boolean isSameAs(StrategicConcern otherConcern, Object param) {
        return false;
    }

    @Override
    public Set getExistingConcernItems() {
        return new HashSet();
    }

    @Override
    public void update() {}

    @Override
    public void reapplyPriorityModifiers() {
        StrategicDefManager.StrategicConcernDef def = getDef();
        MutableStat stat = getPriority();
        if (def.hasTag(TAG_MILITARY)) {
            applyPriorityModifierForAlignment(Alignment.MILITARIST);
        }
        if (def.hasTag(TAG_DIPLOMACY)) {
            applyPriorityModifierForAlignment(Alignment.DIPLOMATIC);
        }
        if (def.hasTag(TAG_ECONOMY)) {
            applyPriorityModifierForTrait(DiplomacyTraits.TraitIds.MONOPOLIST, 1.4f, false);
        }
        if (def.hasTag(TAG_COVERT)) {
            applyPriorityModifierForTrait(DiplomacyTraits.TraitIds.DEVIOUS, 1.4f, false);
        }
    }

    public void applyPriorityModifierForAlignment(Alignment alignment) {
        MutableStat stat = getPriority();
        float alignValue = NexConfig.getFactionConfig(ai.getFactionId()).getAlignments().get(Alignment.DIPLOMATIC).modified;
        stat.modifyMult("alignment_" + alignment.getName(), 1 + SAIConstants.MAX_ALIGNMENT_MODIFIER_FOR_PRIORITY * alignValue,
                "[temp] Alignment: " + Misc.ucFirst(alignment.getName()));
    }

    public void applyPriorityModifierForTrait(String trait, float mult, boolean force) {
        if (!DiplomacyTraits.hasTrait(ai.getFactionId(), trait) && !force)
            return;

        DiplomacyTraits.TraitDef traitDef = DiplomacyTraits.getTrait(trait);
        MutableStat stat = getPriority();
        stat.modifyMult("trait_" + trait, mult, "[temp] Trait: " + traitDef.name);
    }

    @Override
    public CustomPanelAPI createPanel(final CustomPanelAPI holder) {
        final float pad = 3;
        final float opad = 10;

        CustomPanelAPI myPanel = holder.createCustomPanel(SAIConstants.CONCERN_ITEM_WIDTH, SAIConstants.CONCERN_ITEM_HEIGHT,
                new FramedCustomPanelPlugin(0.25f, ai.getFaction().getBaseUIColor(), true));

        TooltipMakerAPI tooltip = myPanel.createUIElement(SAIConstants.CONCERN_ITEM_WIDTH, SAIConstants.CONCERN_ITEM_HEIGHT, true);
        TooltipMakerAPI iwt = tooltip.beginImageWithText(this.getIcon(), 32);

        iwt.addPara(getName(), Misc.getHighlightColor(), 0);

        //createTooltipDesc(iwt, holder, 3);

        int prioVal = (int)getPriorityFloat();
        String prio = String.format(StringHelper.getString("priority", true) + ": %s", prioVal);
        iwt.addPara(prio, 0, Misc.getHighlightColor(), prioVal + "");
        tooltip.addImageWithText(pad);
        tooltip.addTooltipToPrevious(new TooltipMakerAPI.TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 360;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                createTooltipDesc(tooltip, holder, pad);
                tooltip.addPara(StringHelper.getString("priority", true), opad);
                tooltip.addStatModGrid(360, 60, 10, 0, priority,
                        true, NexUtils.getStatModValueGetter(true, 0));
            }
        }, TooltipMakerAPI.TooltipLocation.BELOW);

        if (this.getCurrentAction() != null) {

        } else {

        }

        myPanel.addUIElement(tooltip).inTL(0, 0);
        return myPanel;
    }

    public LabelAPI createTooltipDesc(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        String str = getDef().desc;
        String marketName = "";
        Color hl = Misc.getHighlightColor();
        if (market != null) {
            marketName = getMarket().getName();
            str = StringHelper.substituteToken(str, "$market", marketName);
            hl = market.getFaction().getBaseUIColor();
        }

        return tooltip.addPara(str, pad, hl, marketName);
    }

    @Override
    public List<StrategicAction> generateActions() {
        return null;
    }

    @Override
    public StrategicAction pickAction() {
        return null;
    }

    @Override
    public void notifyActionUpdate() {};

    @Override
    public void end() {
        ended = true;
        // Notify module? or maybe just let it remove on its own in advance();
    }

    public float getPriorityFloat() {
        if (priority == null) return 0;
        return priority.getModifiedValue();
    }

    @Override
    public String getIcon() {
        String icon = getDef().icon;
        if (icon == null || icon.isEmpty()) {
            if (market != null) icon = market.getFaction().getCrest();
            else if (faction != null) icon = faction.getCrest();
            else icon = ai.getFaction().getCrest();
        }

        return icon;
    }

    @Override
    public FactionAPI getFaction() {
        if (faction != null) return faction;
        if (market != null) return market.getFaction();
        return ai.getFaction();
    }

    @Override
    public String getName() {
        return getDef().name;
    }

    @Override
    public String getDesc() {
        return getDef().desc;
    }

    @Override
    public StrategicDefManager.StrategicConcernDef getDef() {
        return StrategicDefManager.getConcernDef(getId());
    }

    public List<StrategicConcern> getExistingConcernsOfSameType() {
        List<StrategicConcern> results = new ArrayList<>();
        for (StrategicConcern concern : module.getCurrentConcerns()) {
            if (concern.isEnded()) continue;
            if (concern.getClass() == this.getClass()) {
                results.add(concern);
            }
        }
        return results;
    }

    protected float getMarketValue(MarketAPI market) {
        float value = NexUtilsMarket.getMarketIndustryValue(market);
        for (Industry ind : market.getIndustries()) {
            if (ind.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY)) {
                value += ind.getBuildCost();
            }
        }
        value += NexUtilsMarket.getIncomeNetPresentValue(market, 6, 0.02f);

        return value;
    }

    @Override
    public String toString() {
        return getName();
    }

    public static float getSpaceDefenseValue(MarketAPI market) {
        MilitaryInfoHelper helper = MilitaryInfoHelper.getInstance();
        MilitaryInfoHelper.PatrolStrengthEntry patrolStr = helper.getPatrolStrength(market.getContainingLocation());
        Float space = 0f;
        if (patrolStr != null && patrolStr.strByFaction.containsKey(market.getFaction().getId())) {
            space += patrolStr.strByFaction.get(market.getFaction().getId());
        }
        CampaignFleetAPI station = Misc.getStationFleet(market);
        float stationStr = 0;
        if (station != null) {
            stationStr = NexUtilsFleet.getFleetStrength(station, true, true, true);
        }
        space += stationStr;
        return space;
    }

    public static float getGroundDefenseValue(MarketAPI market) {
        return market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0);
    }
}
