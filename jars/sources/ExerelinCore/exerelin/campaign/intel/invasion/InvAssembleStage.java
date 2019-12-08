package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.NexAssembleStage;
import org.lazywizard.lazylib.MathUtils;

public class InvAssembleStage extends NexAssembleStage {
	
	public InvAssembleStage(OffensiveFleetIntel intel, SectorEntityToken gatheringPoint) {
		super(intel, gatheringPoint);
	}
	
	@Override
	protected String pickNextType() {
		if (InvasionIntel.NO_STRIKE_FLEETS && !((InvasionIntel)intel).isBrawlMode())
			return "exerelinInvasionFleet";
		
		int fleetCount = this.getRoutes().size();
		Global.getLogger(this.getClass()).info("Current fleet count: " + fleetCount);
		if (fleetCount % 3 == 1)	// first fleet will be invasion
			return "exerelinInvasionFleet";
		return "exerelinInvasionSupportFleet";
	}
	
	@Override
	protected float getFP(String type) {
		float base = 150f;
		if (type.equals("exerelinInvasionFleet"))
			base = 180f;
		
		if (Math.random() < 0.33f)
			base *= 1.5f;
		else if (((OffensiveFleetIntel)intel).isBrawlMode())
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
