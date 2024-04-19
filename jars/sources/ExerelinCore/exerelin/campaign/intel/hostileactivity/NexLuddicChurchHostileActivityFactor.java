package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.LuddicChurchHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.NexUtilsFaction;

import java.util.List;

public class NexLuddicChurchHostileActivityFactor extends LuddicChurchHostileActivityFactor implements InvasionListener {

    public NexLuddicChurchHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (getHesperus(true, true) == null) {
            return 0;
        }
        return super.getProgress(intel);
    }

    @Override
    public float getEventFrequency(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage) {
        if (stage.id == HostileActivityEventIntel.Stage.HA_EVENT) {
            if (getHesperus(true, true) == null) {
                return 0f;
            }
        }
        return super.getEventFrequency(intel, stage);
    }

    @Override
    public MarketAPI getExpeditionSource(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage, final MarketAPI target) {
        return getHesperus(true, true);
    }

    public static MarketAPI getHesperus(boolean requireMilitaryBase, boolean checkFaction) {
        if (checkFaction) {
            MarketAPI hesperus = Global.getSector().getEconomy().getMarket("hesperus");
            if (hesperus == null || !NexUtilsFaction.isLuddicFaction(hesperus.getFactionId())) {
                return null;
            }
        }

        return LuddicChurchHostileActivityFactor.getHesperus(requireMilitaryBase);
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {}

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {}

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) {}

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
        if (market != getHesperus(false)) return;
        if (NexUtilsFaction.isLuddicFaction(newOwner.getId())) return;

        BaseEventIntel.EventStageData stage = intel.getDataFor(HostileActivityEventIntel.Stage.HA_EVENT);
        if (stage != null && stage.rollData instanceof HostileActivityEventIntel.HAERandomEventData &&
                ((HostileActivityEventIntel.HAERandomEventData)stage.rollData).factor == this) {
            intel.resetHA_EVENT();
        }
    }
}
