package exerelin.campaign.ai.action.covert;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.ai.action.BaseStrategicAction;
import exerelin.campaign.intel.agents.CovertActionIntel;

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
        return concern.getFaction();
    }

    public FactionAPI getThirdFaction() {
        List<FactionAPI> factions = concern.getFactions();
        if (factions != null && factions.size() <= 1) return null;
        return factions.get(1);
    }

    public MarketAPI pickTargetMarket() {
        if (concern.getMarket() != null) return concern.getMarket();

        return CovertOpsManager.getManager().pickTargetMarket(getAgentFaction(), getTargetFaction(), getActionType(), concern.getMarkets(), null);
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
