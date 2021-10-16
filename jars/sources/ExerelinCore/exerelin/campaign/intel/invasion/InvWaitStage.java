package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.WaitStage;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.utilities.NexConfig;

/**
 * Go home if planet is captured by someone else
 */
public class InvWaitStage extends WaitStage {
	
	public InvWaitStage(OffensiveFleetIntel intel, SectorEntityToken token, float time, boolean aggressive) 
	{
		super(intel, token, time, aggressive);
	}
	
	@Override
	protected void updateStatus() {
		
		// if legacy invasions are ON and we don't hold the market (maybe it got retaken), go home
		if (NexConfig.legacyInvasions) {
			if (offFltIntel.getTarget().getFaction() != offFltIntel.getFaction()) {
				status = RaidIntel.RaidStageStatus.SUCCESS;
				giveReturnOrdersToStragglers(getRoutes());
				return;
			}
		} 		
		// if legacy invasions are OFF, and there is no ground battle, or it's ended without us winning, go home
		else {
			if (offFltIntel instanceof InvasionIntel) {
				InvasionIntel inv = (InvasionIntel)offFltIntel;
				GroundBattleIntel gb = inv.getGroundBattle();
				if (gb == null) {
					status = RaidIntel.RaidStageStatus.SUCCESS;
					giveReturnOrdersToStragglers(getRoutes());
					return;
				}
				if (gb.getOutcome() != null && gb.getOutcome() != GroundBattleIntel.BattleOutcome.ATTACKER_VICTORY) {
					status = RaidIntel.RaidStageStatus.SUCCESS;
					giveReturnOrdersToStragglers(getRoutes());
					return;
				}
			}
		}
		
		super.updateStatus();
	}
}
