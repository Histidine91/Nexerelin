package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.satbomb.SatBombIntel;
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
		float base = getBaseSize();
		
		if (Math.random() < 0.33f)
			base *= 1.5f;
		
		base *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
		base *= Math.sqrt(InvasionFleetManager.getInvasionSizeMult(intel.getFaction().getId()));
			
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
}
