package exerelin.utilities;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD.TempDataInvasion;
import exerelin.campaign.InvasionRound.InvasionRoundResult;
import java.util.List;

/**
 * Except for {@code reportMarketTransfered}, these are only used for legacy invasions.
 */
public interface InvasionListener {
	void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, 
			TempDataInvasion actionData, CargoAPI cargo);
	
	void reportInvasionRound(InvasionRoundResult result, CampaignFleetAPI fleet, 
			MarketAPI defender, float atkStr, float defStr);
	
	void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, 
			MarketAPI market, float numRounds, boolean success);
	
	void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, 
            boolean playerInvolved, boolean isCapture, List<String> factionsToNotify, float repChangeStrength);
}
