package exerelin.campaign.ai.action;

import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.NexConfig;
import lombok.Getter;
import lombok.Setter;

public class EnterAllianceAction extends DiplomacyAction {

    @Getter @Setter protected Alliance alliance;

    @Override
    public boolean generate() {
        if (alliance != null) {
            AllianceManager.joinAllianceStatic(ai.getFactionId(), alliance);
        } else {
            AllianceManager.createAlliance(ai.getFactionId(), this.faction.getId());
        }

        priority.modifyFlat("base", 300, StrategicAI.getString("statBase", true));
        applyPriorityModifiers();
        return true;
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (alliance != null) {
            if (!alliance.canJoin(ai.getFaction())) return false;
        }
        else if (faction != null) {
            if (!AllianceManager.getManager().canAlly(ai.getFactionId(), faction.getId())) return false;
        }

        if (PirateBaseManager.getInstance().getUnadjustedDaysSinceStart() < NexConfig.allianceGracePeriod) return false;
        return concern.getDef().hasTag("canAlly") || concern.getDef().hasTag("canCoalition");
    }

}
