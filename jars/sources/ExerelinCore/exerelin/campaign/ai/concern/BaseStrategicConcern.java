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
import exerelin.campaign.ai.MilitaryInfoHelper;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.StrategicAIModule;
import exerelin.campaign.ai.StrategicDefManager;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
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
    public void createTooltip(TooltipMakerAPI tooltip, CustomPanelAPI holder, float pad) {
        TooltipMakerAPI iwt = tooltip.beginImageWithText(this.getIcon(), 48);
        iwt.addPara(getName(), Misc.getHighlightColor(), 0);
        createTooltipDesc(iwt, holder, 3);
        int prioVal = (int)getPriorityFloat();
        String prio = String.format("Priority: %s", prioVal);
        iwt.addPara(prio, pad, Misc.getHighlightColor(), prioVal + "");
        //iwt.addTooltipToPrevious();   // TODO
        tooltip.addImageWithText(pad);
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
