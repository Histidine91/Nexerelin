package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.Set;

public class MercPackageIntel extends BaseIntelPlugin implements EconomyTickListener {

    public static final String MEM_KEY = "$nex_HAmercPackageIntel";

    public static final Object BUTTON_END_CONTRACT = new Object();
    public static final String OUTCOME_ENDED = "ended";
    public static final String OUTCOME_DEBT = "ended_debt";

    protected String outcome;
    protected Long startTimestamp;
    protected int monthsRemaining = MercPackageActivityCause.MONTHS;

    public void init(TextPanelAPI text) {
        Global.getSector().getIntelManager().addIntel(this, false, text);
        Global.getSector().getListenerManager().addListener(this);
        startTimestamp = Global.getSector().getClock().getTimestamp();
        Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY, this);
    }

    public static MercPackageIntel getInstance() {
        return (MercPackageIntel)Global.getSector().getMemoryWithoutUpdate().get(MEM_KEY);
    }

    public void end(String outcome) {
        this.outcome = outcome;
        endAfterDelay();
    }

    @Override
    protected void notifyEnding() {
        Global.getSector().getListenerManager().removeListener(this);
        Global.getSector().getMemoryWithoutUpdate().unset(MEM_KEY);
    }

    @Override
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
                                   Color tc, float initPad) {
        if (OUTCOME_DEBT.equals(outcome)) {
            info.addPara(NexHostileActivityManager.getString("mercPackageBulletDebt"), initPad, tc, Misc.getHighlightColor(),
                    Misc.getWithDGS(MercPackageActivityCause.MONTHLY_FEE));
            return;
        }

        if (isEnded() || isEnded()) return;
        // bullet for fee
        info.addPara(NexHostileActivityManager.getString("mercPackageBulletFee"), initPad, tc, Misc.getHighlightColor(),
                Misc.getWithDGS(MercPackageActivityCause.MONTHLY_FEE));
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10f;
        info.addPara(NexHostileActivityManager.getString("mercPackageDesc"), opad);

        if (isEnding() || isEnded()) {
            info.addPara(NexHostileActivityManager.getString("mercPackageDescCancelled") + (OUTCOME_DEBT.equals(outcome) ? "Debt" : ""), opad);
        } else {
            addBulletPoints(info, ListInfoMode.IN_DESC, false, Misc.getTextColor(), opad);
            info.addButton(NexHostileActivityManager.getString("mercPackageBtnCancel"), BUTTON_END_CONTRACT, width, 24, opad);
        }
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_END_CONTRACT) {
            end(OUTCOME_ENDED);
            ui.updateUIForItem(this);
        }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        return true;
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        prompt.addPara(NexHostileActivityManager.getString("mercPackageConfirmPromptCancel"), 0);
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getPlayerFaction();
    }

    @Override
    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public String getName() {
        String name = NexHostileActivityManager.getString("mercPackageName");
        if (isEnded() || isEnding()) name += " - " + StringHelper.getString("over", true);
        return name;
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_FLEET_LOG);
        tags.add(Tags.INTEL_MILITARY);
        tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"));
        return tags;
    }

    @Override
    public String getIcon() {
        return "graphics/icons/missions/pirate_system_bounty.png";
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
    }

    @Override
    public void reportEconomyMonthEnd() {
        if (isEnding() || isEnded()) return;

        // monthly report
        MonthlyReport report = SharedData.getData().getCurrentReport();

        MonthlyReport.FDNode parentNode = report.getNode(MonthlyReport.OUTPOSTS);

        MonthlyReport.FDNode mercNode = report.getNode(parentNode, "nex_node_id_HAmercPackage");
        mercNode.name = this.getName();
        mercNode.custom = "nex_node_id_HAmercPackage";
        mercNode.icon = this.getIcon();
        mercNode.tooltipCreator = MercPackageActivityCause.getTooltipStatic();
        mercNode.upkeep = MercPackageActivityCause.MONTHLY_FEE;

        // cancel if in debt
        report = SharedData.getData().getPreviousReport();
        boolean debt = report.getDebt() > 0;
        // only have debt if the merc company was hired for at least 45 days and was thus around long enough to have seen the debt
        // this is how base game does it for merc officers
        debt &= Global.getSector().getClock().getElapsedDaysSince(this.startTimestamp) > 45;
        if (debt) {
            end(OUTCOME_DEBT);
        }
    }

    @Override
    public IntelSortTier getSortTier() {
        if (isEnding()) {
            return IntelSortTier.TIER_COMPLETED;
        }
        return IntelSortTier.TIER_2;
    }
}
