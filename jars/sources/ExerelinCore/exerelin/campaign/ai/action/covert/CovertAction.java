package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.intel.agents.CovertActionIntel;
import exerelin.utilities.NexConfig;

import java.util.List;

public abstract class CovertAction extends BaseStrategicAction {

    @Override
    public boolean generate() {
        return false;
    }

    public CovertActionIntel getIntel() {
        return (CovertActionIntel)delegate;
    }

    public FactionAPI getAgentFaction() {
        return ai.getFaction();
    }

    public FactionAPI getTargetFaction() {
        return faction;
    }

    public FactionAPI getThirdFaction() {
        List<FactionAPI> factions = concern.getFactions();
        if (factions != null && factions.size() <= 1) return null;
        return factions.get(1);
    }

    public MarketAPI pickTargetMarket() {
        MarketAPI market = concern.getMarket();

        // check if the market specified by concern is hostile (it may not be, e.g. retaliation concern sometimes targets markets that have already been taken)
        boolean blockedByRelationship = false;
        if (market != null) {
            if (ai.getFaction().getRelationshipLevel(market.getFaction()).ordinal() > getMaxRelToTarget(market.getFaction()).ordinal())
                blockedByRelationship = true;
        }

        if (market != null && !blockedByRelationship) {
            return market;
        }

        return CovertOpsManager.getManager().pickTargetMarket(getAgentFaction(), getTargetFaction(), getActionType(), concern.getMarkets(), null);
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        if (ai.getFaction().isPlayerFaction() && !NexConfig.followersAgents)
            return false;

        return true;
    }

    public abstract String getActionType();

    protected boolean beginAction(CovertActionIntel intel) {
        if (intel == null) return false;
        delegate = intel;
        intel.activate();
        //intel.execute();
        return true;
    }
}
