package exerelin.campaign.intel.hostileactivity;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.PerseanLeagueHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;

import java.util.List;

/**
 * Same as vanilla but with faction check for Kazeron.
 */
public class NexPerseanLeagueHostileActivityFactor extends PerseanLeagueHostileActivityFactor implements InvasionListener {

    public NexPerseanLeagueHostileActivityFactor(HostileActivityEventIntel intel) {
        super(intel);
    }

    @Override
    public int getProgress(BaseEventIntel intel) {
        if (getKazeron(true, Factions.PERSEAN) == null) {
            return 0;
        }
        return super.getProgress(intel);
    }

    @Override
    public float getEventFrequency(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage) {
        if (stage.id == HostileActivityEventIntel.Stage.HA_EVENT) {
            if (getKazeron(true, Factions.PERSEAN) == null) {
                return 0;
            }
        }

        return super.getEventFrequency(intel, stage);
    }

    @Override
    public MarketAPI getBlockadeSource(HostileActivityEventIntel intel, BaseEventIntel.EventStageData stage, StarSystemAPI target) {
        return getKazeron(true, Factions.PERSEAN);
    }

    public static MarketAPI getKazeron(boolean requireMilitaryBase, String requiredFactionId) {
        if (requiredFactionId != null) {
            MarketAPI kazeron = Global.getSector().getEconomy().getMarket("kazeron");
            if (kazeron == null || !requiredFactionId.equals(kazeron.getFactionId())) {
                return null;
            }
        }

        return PerseanLeagueHostileActivityFactor.getKazeron(requireMilitaryBase);
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
        if (market != getKazeron(false)) return;
        if (newOwner.getId().equals(Factions.PERSEAN)) return;

        BaseEventIntel.EventStageData stage = intel.getDataFor(HostileActivityEventIntel.Stage.HA_EVENT);
        if (stage != null && stage.rollData instanceof HostileActivityEventIntel.HAERandomEventData &&
                ((HostileActivityEventIntel.HAERandomEventData)stage.rollData).factor == this) {
            intel.resetHA_EVENT();
        }
    }
}
