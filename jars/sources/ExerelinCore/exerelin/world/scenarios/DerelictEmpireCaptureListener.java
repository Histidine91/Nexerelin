package exerelin.world.scenarios;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;

import java.util.List;

public class DerelictEmpireCaptureListener implements InvasionListener {
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
        // capturing a DE market brings it into the core sector economy
        // we could do the reverse, but currently derelicts don't invade anyway and if they did, it could be troublesome if e.g. Sindria was taken off the grid
        if (DerelictEmpireV2.ECON_GROUP_ID.equals(market.getEconGroup()) && !"nex_derelict".equals(newOwner.getId())) {
            market.setEconGroup(null);
        }
    }
}
