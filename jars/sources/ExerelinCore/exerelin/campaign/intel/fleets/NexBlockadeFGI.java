package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.impl.campaign.intel.group.BlockadeFGI;
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import lombok.Getter;
import lombok.Setter;

public class NexBlockadeFGI extends BlockadeFGI {

    @Getter @Setter protected StrategicAction strategicAction;

    public NexBlockadeFGI(GenericRaidParams params, FGBlockadeAction.FGBlockadeParams blockadeParams) {
        super(params, blockadeParams);
    }

    @Override
    protected void periodicUpdate() {
        if (Misc.getMarketsInLocation(getTargetSystem(), blockadeParams.targetFaction).isEmpty()) {
            setFailedButNotDefeated(true);
            abort();
        }
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        super.createSmallDescription(info, width, height);
        Global.getLogger(this.getClass()).info("wololo");

        addStrategicActionInfo(info, width);
    }

    protected void addStrategicActionInfo(TooltipMakerAPI info, float width) {
        if (strategicAction == null) return;
        info.addPara(StrategicAI.getString("intelPara_actionDelegateDesc"), 10, Misc.getHighlightColor(), strategicAction.getConcern().getName());
        info.addButton(StrategicAI.getString("btnGoIntel"), StrategicActionDelegate.BUTTON_GO_INTEL, width, 24, 3);
    }

    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == StrategicActionDelegate.BUTTON_GO_INTEL && strategicAction != null) {
            Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, strategicAction.getAI());
            return;
        }
        super.buttonPressConfirmed(buttonId, ui);
    }
}
