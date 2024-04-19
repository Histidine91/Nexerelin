package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.SindrianDiktatHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;

import java.util.List;

public class NexSindrianDiktatHostileActivityFactor extends SindrianDiktatHostileActivityFactor implements InvasionListener {

    public NexSindrianDiktatHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
        Global.getSector().getListenerManager().addListener(this);
    }

    @Override
    public float getEventFrequency(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage) {
        if (stage.id == HostileActivityEventIntel.Stage.HA_EVENT) {
            if (getSindria(true, Factions.DIKTAT) == null) {
                return 0;
            }
        }

        return super.getEventFrequency(intel, stage);
    }

    public static MarketAPI getSindria(boolean requireMilitaryBase, String requiredFactionId) {
        if (requiredFactionId != null) {
            MarketAPI sindria = Global.getSector().getEconomy().getMarket("sindria");
            if (sindria == null || !requiredFactionId.equals(sindria.getFactionId())) {
                return null;
            }
        }

        return SindrianDiktatHostileActivityFactor.getSindria(requireMilitaryBase);
    }

    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {

    }

    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {

    }

    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, float numRounds, boolean success) {

    }

    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
        if (market != getSindria(false)) return;
        if (newOwner.getId().equals(Factions.DIKTAT)) return;

        BaseEventIntel.EventStageData stage = intel.getDataFor(HostileActivityEventIntel.Stage.HA_EVENT);
        if (stage != null && stage.rollData instanceof HostileActivityEventIntel.HAERandomEventData &&
                ((HostileActivityEventIntel.HAERandomEventData)stage.rollData).factor == this) {
            intel.resetHA_EVENT();
        }
    }
}
