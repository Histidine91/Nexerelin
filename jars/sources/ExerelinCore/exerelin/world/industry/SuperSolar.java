package exerelin.world.industry;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import org.lazywizard.lazylib.MathUtils;

public class SuperSolar extends IndustryClassGen {

	public SuperSolar() {
		super("ms_supersolar");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (!entity.market.getFactionId().equals("shadow_industry"))
			return false;
		StarSystemAPI system = entity.entity.getStarSystem();
		if (system == null) return false;
		if (system.isNebula()) return false;
		
		return true;
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 5000 / MathUtils.getDistance(entity.entity, entity.entity.getStarSystem().getCenter());
		if (weight < 0.3) return 0;
		if (weight > 3) weight = 3;
		return weight * getFactionMult(entity);
	}
}
