package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.campaign.intel.agents.LowerRelations;
import exerelin.utilities.NexConfig;

import java.util.ArrayList;
import java.util.List;

public class LowerRelationsAction extends CovertAction {

    @Override
    public boolean generate() {
        FactionAPI thirdFaction = getThirdFaction();
        if (thirdFaction == null) {
            thirdFaction = pickThirdFactionFallback();
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
    public String getActionType() {
        return CovertOpsManager.CovertActionType.LOWER_RELATIONS;
    }

    @Override
    public boolean canUseForConcern(StrategicConcern concern) {
        return concern.getDef().hasTag("diplomacy_negative");
    }
}
