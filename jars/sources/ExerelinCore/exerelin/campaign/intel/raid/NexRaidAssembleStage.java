package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import org.lazywizard.lazylib.MathUtils;

public class NexRaidAssembleStage extends NexAssembleStage {
	
	public NexRaidAssembleStage(RaidIntel raid, SectorEntityToken gatheringPoint) {
		super(raid, gatheringPoint);
	}
	
	@Override
	protected String pickNextType() {
		return "exerelinInvasionSupportFleet";
	}
	
	@Override
	protected float getFP(String type) {
		float base = 120f;
		
		if (Math.random() < 0.33f)
			base *= 1.5f;
		
		base *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			
		if (spawnFP < base * 1.5f) {
			base = spawnFP;
		}
		if (base > spawnFP) base = spawnFP;
		
		spawnFP -= base;
		return base;
	}
	
	
}
