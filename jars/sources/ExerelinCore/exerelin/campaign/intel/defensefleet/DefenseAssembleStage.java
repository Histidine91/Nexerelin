package exerelin.campaign.intel.defensefleet;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import org.lazywizard.lazylib.MathUtils;

public class DefenseAssembleStage extends NexAssembleStage {
	
	public DefenseAssembleStage(OffensiveFleetIntel intel, SectorEntityToken gatheringPoint) {
		super(intel, gatheringPoint);
	}
	
	@Override
	protected String pickNextType() {
		return "nex_defenseFleet";
	}
	
	@Override
	protected float getFP(String type) {
		float base = 120f;
		
		if (Math.random() < 0.33f)
			base *= 1.5f;
		
		if (((OffensiveFleetIntel)intel).isBrawlMode())
			base *= 1.25f;
		
		base *= MathUtils.getRandomNumberInRange(0.85f, 1.15f);
		base *= InvasionFleetManager.getInvasionSizeMult(intel.getFaction().getId());
		
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
}
