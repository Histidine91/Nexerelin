package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.ai.action.DiplomacyAction;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.LowerRelations;
import exerelin.utilities.NexConfig;

import java.util.ArrayList;
import java.util.List;

public class LowerRelationsAction extends CovertAction {

    @Override
    public boolean generate() {
        FactionAPI thirdFaction = getThirdFaction();
        Global.getLogger(this.getClass()).info("Third faction is " + thirdFaction);
        if (thirdFaction == null) {
            thirdFaction = pickThirdFactionFallback();
            Global.getLogger(this.getClass()).info("Third faction (fallback) is " + thirdFaction);
        }
        if (thirdFaction == null) return false;

        CovertActionIntel intel = new LowerRelations(null, pickTargetMarket(), getAgentFaction(), getTargetFaction(),
                thirdFaction, false, null);
        return beginAction(intel);
    }

    protected FactionAPI pickThirdFactionFallback() {
        List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(ai.getFaction(), NexConfig.allowPirateInvasions, false, true);
        List<FactionAPI> enemies2 = new ArrayList<>();

        for (String factionId : enemies) enemies2.add(Global.getSector().getFaction(factionId));
        enemies2.remove(getTargetFaction());

        return CovertOpsManager.getManager().generateTargetFactionPicker(getActionType(), ai.getFaction(), enemies2).pick();
    }

    @Override
    protected boolean beginAction(CovertActionIntel intel) {
        boolean result = super.beginAction(intel);
        if (result) {
            Global.getSector().getMemoryWithoutUpdate().set(DiplomacyAction.MEM_KEY_GLOBAL_COOLDOWN, true, DiplomacyAction.DIPLOMACY_GLOBAL_COOLDOWN);
        }
        return result;
    }

    @Override
    public String getActionType() {
        return CovertOpsManager.CovertActionType.LOWER_RELATIONS;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(DiplomacyAction.MEM_KEY_GLOBAL_COOLDOWN))
            return false;
        if (!concern.getDef().hasTag("diplomacy_negative")) return false;
        return super.canUse(concern);
    }
}
