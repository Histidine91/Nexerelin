package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.Global;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.ai.action.DiplomacyAction;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.RaiseRelations;
import exerelin.utilities.NexConfig;

public class RaiseRelationsAction extends CovertAction {

    @Override
    public boolean generate() {
        CovertActionIntel intel = new RaiseRelations(null, pickTargetMarket(), getAgentFaction(), getTargetFaction(),
                null, false, null);
        return beginAction(intel);
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
        return CovertOpsManager.CovertActionType.RAISE_RELATIONS;
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (Global.getSector().getMemoryWithoutUpdate().getBoolean(DiplomacyAction.MEM_KEY_GLOBAL_COOLDOWN))
            return false;
        if (NexConfig.getFactionConfig(ai.getFactionId()).disableDiplomacy) return false;
        if (!concern.getDef().hasTag("diplomacy_positive")) return false;

        if (faction != null) {
            float curr = ai.getFaction().getRelationship(faction.getId());
            float max = DiplomacyManager.getManager().getMaxRelationship(ai.getFactionId(),	faction.getId());
            if (curr >= max) return false;
        }

        return super.canUse(concern);
    }
}
