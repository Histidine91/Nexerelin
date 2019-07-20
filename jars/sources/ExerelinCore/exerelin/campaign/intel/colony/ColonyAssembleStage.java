package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import org.lazywizard.lazylib.MathUtils;

public class ColonyAssembleStage extends NexAssembleStage {
	
	public ColonyAssembleStage(OffensiveFleetIntel intel, SectorEntityToken gatheringPoint) {
		super(intel, gatheringPoint);
	}
	
	@Override
	protected String pickNextType() {		
		int fleetCount = this.getRoutes().size();
		Global.getLogger(this.getClass()).info("Current fleet count: " + fleetCount);
		//if (fleetCount % 3 == 1)
		if (fleetCount == 1)
			return "nex_colonyFleet";
		return FleetTypes.PATROL_LARGE;
	}
	
	@Override
	protected float getFP(String type) {
		float base = 120f;
		if (type.equals("nex_colonyFleet"))
			base = 180f;
		
		if (Math.random() < 0.33f)
			base *= 1.5f;
		
		base *= MathUtils.getRandomNumberInRange(0.85f, 1.15f);
			
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
	
	@Override
	protected void updateStatus() {
		super.updateStatus();
		if (offFltIntel.getTarget().isInEconomy()) {
			status = RaidIntel.RaidStageStatus.FAILURE;
			((ColonyExpeditionIntel)intel).notifyQueueJumpedEarly();
		}
	}
}
