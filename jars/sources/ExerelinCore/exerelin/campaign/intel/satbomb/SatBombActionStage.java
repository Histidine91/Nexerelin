package exerelin.campaign.intel.satbomb;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.VIC_FactionVBomb;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class SatBombActionStage extends InvActionStage {
	
	public SatBombActionStage(SatBombIntel bomb, MarketAPI target) {
		super(bomb, target);
	}
	
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		if (offFltIntel.getOutcome() != null)
			return;
		
		boolean vicBioBomb = ((SatBombIntel)offFltIntel).isVirusBomb;
		
		float cost = Nex_MarketCMD.getBombardmentCost(market, fleet);
		//float maxCost = intel.getAssembleStage().getOrigSpawnFP() * Misc.FP_TO_BOMBARD_COST_APPROX_MULT;
		float maxCost = intel.getRaidFP() / intel.getNumFleets() * Misc.FP_TO_BOMBARD_COST_APPROX_MULT;
		if (fleet != null) {
			if (vicBioBomb)	// bio sat bomb
				maxCost = fleet.getCargo().getMaxCapacity() * 0.25f;
			else maxCost = fleet.getCargo().getMaxFuel() * 0.25f;
		}
		log.info(String.format("Attempting sat bomb: %s fuel available, %s cost", maxCost, cost));
		
		if (cost <= maxCost) {
			if (vicBioBomb) {
				new VIC_FactionVBomb().doBombardment(intel.getFaction(), market);
			}
			else {
				new Nex_MarketCMD(market.getPrimaryEntity()).doBombardment(intel.getFaction(), BombardType.SATURATION);
			}
			
			SatBombIntel intel = ((SatBombIntel)this.intel);
			status = RaidIntel.RaidStageStatus.SUCCESS;
			intel.reportOutcome(OffensiveFleetIntel.OffensiveOutcome.SUCCESS);
			intel.sendOutcomeUpdate();
			NexUtils.incrementMemoryValue(intel.getFaction().getMemoryWithoutUpdate(), SatBombIntel.FACTION_MEMORY_KEY, 1);
		}
		offFltIntel.setRouteActionDone(fleet);
	}
	
	@Override
	protected void checkIfInvasionFailed() {
		// do nothing
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		float opad = 10f;
		
		if (curr < index) return;
		
		if (status == RaidIntel.RaidStageStatus.ONGOING && curr == index) {
			info.addPara(StringHelper.getString("nex_satbomb", "intelStageAction"), opad);
			
			if (Global.getSettings().isDevMode()) {
				info.addPara("DEBUG: Autoresolving in %s days", opad, h, 
						String.format("%.1f", untilAutoresolve));
			}
			
			return;
		}
		
		OffensiveFleetIntel intel = ((OffensiveFleetIntel)this.intel);
		if (intel.getOutcome() != null) {
			String key = "intelStageAction";
			switch (intel.getOutcome()) {
			case FAIL:
				key += "DefeatedGround";
				break;
			case SUCCESS:
				key += "Success";
				break;
			case TASK_FORCE_DEFEATED:
				key += "DefeatedSpace";
				break;
			case NO_LONGER_HOSTILE:
			case MARKET_NO_LONGER_EXISTS:
			case OTHER:
				key += "Aborted";
				break;
			}
			info.addPara(StringHelper.getStringAndSubstituteToken("nex_satbomb",
						key, "$market", target.getName()), opad);
		} else if (status == RaidIntel.RaidStageStatus.SUCCESS) {			
			info.addPara("The expeditionary force has succeeded.", opad); // shouldn't happen?
		} else {
			info.addPara("The expeditionary force has failed.", opad); // shouldn't happen?
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
