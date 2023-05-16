package exerelin.campaign.ai.action;

import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.alliances.Alliance;
import exerelin.utilities.NexConfig;
import lombok.Getter;
import lombok.Setter;

public class EnterAllianceAction extends DiplomacyAction implements StrategicActionDelegate {

    @Getter @Setter protected Alliance alliance;

    @Override
    public boolean generate() {
        if (alliance != null) {
            AllianceManager.joinAllianceStatic(ai.getFactionId(), alliance);
        } else {
            AllianceManager.createAlliance(ai.getFactionId(), this.faction.getId());
        }
        if (AllianceManager.getFactionAlliance(ai.getFactionId()) == null) return false;
        delegate = this;
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

    @Override
    public ActionStatus getStrategicActionStatus() {
        return ActionStatus.SUCCESS;
    }

    @Override
    public float getStrategicActionDaysRemaining() {
        return 0;
    }

    @Override
    public String getStrategicActionName() {
        return this.getName();
    }

    @Override
    public StrategicAction getStrategicAction() {
        return this;
    }

    @Override
    public void setStrategicAction(StrategicAction action) {

    }

    @Override
    public void abortStrategicAction() {
        // action insta-completes so can't abort
    }
}
