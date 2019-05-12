package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.OffensiveFleetIntel;
import exerelin.utilities.StringHelper;

public class SatBombActionStage extends NexRaidActionStage {
	// TODO
	public SatBombActionStage(RaidIntel raid, StarSystemAPI system) {
		super(raid, system);
	}
	
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		float cost = Nex_MarketCMD.getBombardmentCost(market, fleet);
		//float maxCost = intel.getAssembleStage().getOrigSpawnFP() * Misc.FP_TO_BOMBARD_COST_APPROX_MULT;
		float maxCost = intel.getRaidFP() / intel.getNumFleets() * Misc.FP_TO_BOMBARD_COST_APPROX_MULT;
		if (fleet != null) {
			maxCost = fleet.getCargo().getMaxFuel() * 0.25f;
		}
		
		if (cost <= maxCost) {
			new Nex_MarketCMD(market.getPrimaryEntity()).doBombardment(intel.getFaction(), BombardType.SATURATION);
			SatBombIntel intel = ((SatBombIntel)this.intel);
			status = RaidIntel.RaidStageStatus.SUCCESS;
			intel.setOutcome(OffensiveFleetIntel.OffensiveOutcome.SUCCESS);
		}
	}
	
	@Override
	public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("bombarding", market.getName());
	}

	@Override
	public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("movingInToBombard", market.getName());
	}
}
