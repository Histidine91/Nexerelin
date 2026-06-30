package exerelin.campaign.ai.action;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.InterventionConcern;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsMath;
import exerelin.utilities.StringHelper;

import java.util.List;

public class TransferFleetPointsAction extends BaseStrategicAction implements StrategicActionDelegate {

    public int invPoints;
    public int fleetReqPoints;
    public FactionAPI recipient;

    @Override
    public boolean generate() {
        if (faction == null) return false;

        recipient = pickRecipientFaction();
        if (recipient == null) return false;

        calcPointsToTransfer();
        if (invPoints <= 0 && fleetReqPoints <= 0) return false;

        if (invPoints > 0) {
            InvasionFleetManager.getManager().modifySpawnCounterV2(recipient.getId(), invPoints);
            InvasionFleetManager.getManager().modifySpawnCounterV2(ai.getFactionId(), -invPoints);
        }
        if (fleetReqPoints > 0) {
            FleetPoolManager.getManager().modifyPool(recipient.getId(), fleetReqPoints);
            FleetPoolManager.getManager().modifyPool(ai.getFactionId(), -fleetReqPoints);
        }
        delegate = this;
        return true;
    }

    protected void calcPointsToTransfer() {
        String aifid = ai.getFactionId();
        float capMult = getTransferCap(recipient);

        if (FleetPoolManager.USE_POOL) {
            float curPool = FleetPoolManager.getManager().getCurrentPool(aifid);
            float max = FleetPoolManager.getManager().getMaxPool(aifid);
            if (max < 1) max = 1;

            float toTransfer = curPool * capMult;
            toTransfer = Math.min(toTransfer, max/2 * capMult);

            fleetReqPoints = (int)toTransfer;
        }
        {
            float curPoints = InvasionFleetManager.getManager().getSpawnCounter(aifid);
            float max = InvasionFleetManager.getMaxInvasionPoints(ai.getFaction());

            if (max < 1) max = 1;

            float toTransfer = curPoints * capMult;
            toTransfer = Math.min(toTransfer, max/2 * capMult);

            invPoints = (int)toTransfer;
        }
    }

    protected FactionAPI pickRecipientFaction() {
        if (factionFieldIsEnemy()) {
            List<FactionAPI> enemiesOfMyEnemy = NexUtilsFaction.factionIdsToFactions(DiplomacyManager.getFactionsAtWarWithFaction(this.faction, false, false, false));
            WeightedRandomPicker<FactionAPI> picker = new WeightedRandomPicker<>();
            for (FactionAPI potential : enemiesOfMyEnemy) {
                float weight = getTransferCap(potential);
                if (weight <= 0) continue;
                picker.add(potential, weight);
            }
            return picker.pick();
        } else {
            return faction;
        }
    }

    public float getTransferCap(FactionAPI recipient) {
        if (AllianceManager.areFactionsAllied(recipient.getId(), ai.getFactionId())) return 0.5f;
        switch (ai.getFaction().getRelationshipLevel(recipient)) {
            case COOPERATIVE: return 0.4f;
            case FRIENDLY: return 0.3f;
            case WELCOMING: return 0.2f;
            case FAVORABLE: return 0.1f;
            default: return 0;
        }
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();

        String aifid = ai.getFactionId();

        // reduce interest in this action at low available points
        if (FleetPoolManager.USE_POOL) {
            float curPool = FleetPoolManager.getManager().getCurrentPool(aifid);
            float max = FleetPoolManager.getManager().getMaxPool(aifid);
            if (max < 1) max = 1;

            float ratio = Math.min(curPool/max, 1.25f);
            float proportion = NexUtilsMath.lerp(0.4f, 1f, ratio);

            if (max <= 0 || curPool <= 0) proportion = 0;
            if (ratio > 0.75f) proportion = Math.max(proportion, 1);

            priority.modifyMult("fleetPool", proportion, StrategicAI.getString("statFleetPool", true));
        } else {
            float invPoints = InvasionFleetManager.getManager().getSpawnCounter(aifid);
            float baseline = NexConfig.pointsRequiredForInvasionFleet;
            float ratio = invPoints/baseline;

            float mult = ratio * 0.5f + 0.5f;
            if (mult < 0.5f) mult = 0.5f;
            else if (mult > 1.5f) mult = 1.5f;
            priority.modifyMult("invPoints", mult, StrategicAI.getString("statInvPoints", true));
        }
    }

    protected boolean factionFieldIsEnemy() {
        return ai.getFaction().isAtBest(this.faction, RepLevel.SUSPICIOUS);
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (!concern.getDef().hasTag("canTransferFleetPoints")) return false;

        // only use this if we're not at war ourselves
        // may be too strict, seeing as we can outright join war even if already fighting elsewhere
        // add a war weariness check?
        boolean isAtWar = !DiplomacyManager.getFactionsAtWarWithFaction(this.faction, false, false, true).isEmpty();

        return !isAtWar || (concern instanceof InterventionConcern);
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
        if (recipient == null) return getDef().name;

        String name = StrategicAI.getString("actionName_transferFleetPoints", true);
        if (FleetPoolManager.USE_POOL) {
            name = String.format(name, fleetReqPoints, StringHelper.getString("fleet"), recipient.getDisplayName());
        } else {
            name = String.format(name, invPoints, StringHelper.getString("exerelin_invasion", "invasion"), recipient.getDisplayName());
        }

        return name;
    }

    @Override
    public StrategicAction getStrategicAction() {
        return this;
    }

    @Override
    public void setStrategicAction(StrategicAction action) {
        // no-op
    }

    @Override
    public void abortStrategicAction() {
        // instant so shouldn't happen
    }
}
