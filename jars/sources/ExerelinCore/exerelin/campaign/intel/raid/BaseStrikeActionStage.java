package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.battle.NexWarSimScript;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.utilities.StringHelper;
import java.awt.Color;

public class BaseStrikeActionStage extends InvActionStage {
	
	public BaseStrikeActionStage(OffensiveFleetIntel strike, MarketAPI target) {
		super(strike, target);
	}
	
	@Override
	protected void updateStatus() {
		if (!target.isInEconomy()) {
			status = RaidIntel.RaidStageStatus.SUCCESS;
			BaseStrikeIntel intel = (BaseStrikeIntel)this.intel;
			intel.reportOutcome(OffensiveFleetIntel.OffensiveOutcome.SUCCESS);
			intel.sendOutcomeUpdate();
		}
		
		super.updateStatus();
	}
	
	@Override
	protected void updateRoutes() {		
		untilAutoresolve = 10f + 2.5f * (float) Math.random();
		// increase autoresolve time if target location is unknown (we have to search for it)
		if (target.getPrimaryEntity().isDiscoverable()) {
			untilAutoresolve += 10 + 5 * (float) Math.random();
		}
		
		super.updateRoutes();
	}
	
	@Override
	public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("attacking", market.getName());
	}

	@Override
	public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("movingInToAttack", market.getName());
	}
	
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		// do nothing, if the base is whacked we require no further action
	}
	
	@Override
	protected void autoresolve() {
		Global.getLogger(this.getClass()).info("Autoresolving base strike action");
		float str = NexWarSimScript.getFactionAndAlliedStrength(intel.getFaction(), getTarget().getFaction(), getTarget().getStarSystem());
		float enemyStr = NexWarSimScript.getFactionAndAlliedStrength(getTarget().getFaction(), intel.getFaction(), getTarget().getStarSystem());
		
		float defensiveStr = enemyStr + WarSimScript.getStationStrength(target.getFaction(), 
							 target.getStarSystem(), target.getPrimaryEntity());
		BaseStrikeIntel intel = ((BaseStrikeIntel)this.intel);
		
		if (defensiveStr >= str) {
			status = RaidIntel.RaidStageStatus.FAILURE;
			removeMilScripts();
			giveReturnOrdersToStragglers(getRoutes());
			
			intel.reportOutcome(OffensiveFleetIntel.OffensiveOutcome.TASK_FORCE_DEFEATED);
			return;
		}
		
		// kill base
		/*
		Industry station = Misc.getStationIndustry(target);
		if (station != null) {
			OrbitalStation.disrupt(station);
		}
		*/
		CampaignFleetAPI fleet = Misc.getStationFleet(target);
		if (fleet != null && fleet.isAlive()) {
			for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy())
			{
				fleet.removeFleetMemberWithDestructionFlash(member);
			}
		}
		
		// base killed; we're done 
		status = RaidIntel.RaidStageStatus.SUCCESS;
		intel.reportOutcome(OffensiveFleetIntel.OffensiveOutcome.SUCCESS);
		intel.sendOutcomeUpdate();
	}
	
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		float opad = 10f;
		
		if (curr < index) return;
		
		if (status == RaidIntel.RaidStageStatus.ONGOING && curr == index) {
			info.addPara(StringHelper.getString("nex_baseStrike", "intelStageAction"), opad);
			
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
			case SUCCESS:
				key += "Success";
				break;
			case FAIL:
			case TASK_FORCE_DEFEATED:
				key += "Defeated";
				break;
			case NO_LONGER_HOSTILE:
			case MARKET_NO_LONGER_EXISTS:
			case OTHER:
				key += "Aborted";
				break;
			}
			info.addPara(StringHelper.getStringAndSubstituteToken("nex_baseStrike",
						key, "$market", target.getName()), opad);
		} else if (status == RaidIntel.RaidStageStatus.SUCCESS) {			
			info.addPara("The expeditionary force has succeeded.", opad); // shouldn't happen?
		} else {
			info.addPara("The expeditionary force has failed.", opad); // shouldn't happen?
		}
	}
}
