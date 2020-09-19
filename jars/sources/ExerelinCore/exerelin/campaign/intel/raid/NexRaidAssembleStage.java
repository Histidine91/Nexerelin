package exerelin.campaign.intel.raid;

import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import org.lazywizard.lazylib.MathUtils;

public class NexRaidAssembleStage extends NexAssembleStage {
	
	public NexRaidAssembleStage(OffensiveFleetIntel intel, SectorEntityToken gatheringPoint) {
		super(intel, gatheringPoint);
	}
	
	@Override
	protected String pickNextType() {
		if (intel instanceof SatBombIntel) return "nex_satBombFleet";
		return "exerelinInvasionSupportFleet";
	}
	
	@Override
	protected float getFP(String type) {
		float base = 120f;
		if (intel instanceof SatBombIntel) {
			base = 180f;
		}
		
		if (Math.random() < 0.33f)
			base *= 1.5f;
		
		base *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
		base *= InvasionFleetManager.getInvasionSizeMult(intel.getFaction().getId());
			
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
}
