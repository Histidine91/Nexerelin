package exerelin.campaign.ai.action;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.concern.StrategicConcern;

public class TransferMarketAction extends BaseStrategicAction implements StrategicActionDelegate {

    @Override
    public boolean generate() {
        MarketAPI market = concern.getMarket();
        if (Nex_IsFactionRuler.isRuler(market.getFaction())) {
            // TODO: offer to buy the market
            return false;
        }

        SectorManager.transferMarket(market, ai.getFaction(), market.getFaction(), false, false, null, 0);
        delegate = this;
        return true;
    }

    @Override
    public void postGenerate() {
        super.postGenerate();
        if (delegate == this) {
            end(StrategicActionDelegate.ActionStatus.SUCCESS);
        }
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        return concern.getDef().hasTag("canTransferMarket") && concern.getMarket() != null && enoughRep(concern.getMarket());
    }

    @Override
    public void applyPriorityModifiers() {
        super.applyPriorityModifiers();
        priority.modifyFlat("base", 200, StrategicAI.getString("statBase", true));
    }

    public boolean enoughRep(MarketAPI market) {
        FactionAPI faction = market.getFaction();
        if (AllianceManager.areFactionsAllied(faction.getId(), ai.getFactionId())) return true;

        RepLevel rel = RepLevel.FRIENDLY;
        if (market.getSize() >= 5) rel = RepLevel.COOPERATIVE;

        return faction.getRelationshipLevel(ai.getFaction()).isAtWorst(rel);
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
        // do nothing?
    }

    @Override
    public void abortStrategicAction() {
        // too late, it's done instantly
    }
}
