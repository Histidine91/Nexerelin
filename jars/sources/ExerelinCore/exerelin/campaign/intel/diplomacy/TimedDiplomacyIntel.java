package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ExerelinReputationAdjustmentResult;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public abstract class TimedDiplomacyIntel extends BaseIntelPlugin implements StrategicActionDelegate {

    public static final Object EXPIRED_UPDATE = new Object();
    public static final String BUTTON_ACCEPT = "Accept";
    public static final String BUTTON_REJECT = "Reject";
    public static final float DEFAULT_TIMEOUT = 30;

    protected String factionId;
    @Getter @Setter protected int state = 0;	// 0 = pending, 1 = accepted, -1 = rejected
    protected float daysRemaining;

    protected ExerelinReputationAdjustmentResult repResult;
    protected float storedRelation;

    @Getter @Setter protected StrategicAction strategicAction;

    public TimedDiplomacyIntel() {
        this(DEFAULT_TIMEOUT);
    }

    public TimedDiplomacyIntel(float timeout) {
        this.daysRemaining = timeout;
    }

    protected boolean endOnAccept() {
        return true;
    }
    protected boolean endOnReject() {
        return true;
    }
    protected boolean endOnExpire() {
        return true;
    }

    public void accept() {
        if (getState() == 1) return;
        acceptImpl();
        setState(1);
        if (endOnAccept()) endAfterDelay();
    }

    public void reject() {
        if (getState() == -1) return;
        rejectImpl();
        setState(-1);
        if (endOnReject()) endAfterDelay();
    }

    public abstract void onExpire();
    protected abstract void acceptImpl();
    protected abstract void rejectImpl();

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        float opad = 10;

        createGeneralDescription(info, width, opad);

        if (state == 0) {
            createPendingDescription(info, width, opad);
        }
        else {
            info.addSectionHeading(StringHelper.getString("result", true), getFactionForUIColors().getBaseUIColor(),
                    getFactionForUIColors().getDarkUIColor(), Alignment.MID, opad);
            createOutcomeDescription(info, width, opad);
        }

        if (strategicAction != null) {
            info.addPara(StrategicAI.getString("intelPara_actionDelegateDesc"), opad*2f, Misc.getHighlightColor(), strategicAction.getConcern().getName());
            info.addButton(StrategicAI.getString("btnGoIntel"), StrategicActionDelegate.BUTTON_GO_INTEL, width, 24, 3);
        }
    }

    public abstract void createGeneralDescription(TooltipMakerAPI info, float width, float opad);

    public void createPendingDescription(TooltipMakerAPI info, float width, float opad) {
        Map<String, String> replace = new HashMap<>();
        Color h = Misc.getHighlightColor();
        Color base = getFactionForUIColors().getBaseUIColor();
        Color dark = getFactionForUIColors().getDarkUIColor();

        String days = Math.round(daysRemaining) + "";
        String daysStr = getDaysString(daysRemaining);
        replace.put("$timeLeft", days);
        replace.put("$days", daysStr);
        String str = StringHelper.getStringAndSubstituteTokens("nex_tribute", "intel_descTime", replace);
        info.addPara(str, opad, h, days);

        ButtonAPI button = info.addButton(StringHelper.getString("accept", true), BUTTON_ACCEPT,
                base, dark, (int)(width), 20f, opad * 3f);
        ButtonAPI button2 = info.addButton(StringHelper.getString("reject", true), BUTTON_REJECT,
                getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
                (int)(width), 20f, opad);
    }

    public abstract void createOutcomeDescription(TooltipMakerAPI info, float width, float opad);

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_ACCEPT) {
            accept();
            ui.updateUIForItem(this);
            return;
        }
        else if (buttonId == BUTTON_REJECT) {
            reject();
            ui.updateUIForItem(this);
            return;
        }

        else if (buttonId == StrategicActionDelegate.BUTTON_GO_INTEL && strategicAction != null) {
            Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, strategicAction.getAI());
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }

    @Override
    protected void advanceImpl(float amount) {
        if (isEnding() || isEnded())
            return;

        if (!SectorManager.isFactionAlive(factionId)) {
            reject();
            sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
            endAfterDelay();
            return;
        }

        daysRemaining -= Global.getSector().getClock().convertToDays(amount);

        if (daysRemaining <= 0) {
            onExpire();
            sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
            if (endOnExpire()) endAfterDelay();
        }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        return buttonId != StrategicActionDelegate.BUTTON_GO_INTEL;
    }

    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        prompt.addPara(StringHelper.getString("exerelin_diplomacy", "intelCeasefireConfirm"), 0);
    }

    @Override
    public Color getTitleColor(ListInfoMode mode) {
        return state == 0 ? Misc.getBasePlayerColor() : Misc.getGrayColor();
    }

    @Override
    public ActionStatus getStrategicActionStatus() {
        switch (state) {
            case 0:
                return ActionStatus.IN_PROGRESS;
            case 1:
                return ActionStatus.SUCCESS;
            case -1:
                return ActionStatus.FAILURE;
            default:
                return ActionStatus.CANCELLED;
        }
    }

    @Override
    public float getStrategicActionDaysRemaining() {
        return daysRemaining;
    }

    @Override
    public void abortStrategicAction() {
        state =-1;
        sendUpdateIfPlayerHasIntel(EXPIRED_UPDATE, false);
        endAfterDelay();
    }
}
