package exerelin.world.industry;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
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
		if (entity.market.hasCondition(Conditions.DARK)) return false;
		StarSystemAPI system = entity.entity.getStarSystem();
		if (system == null) return false;
		if (system.isNebula()) return false;
		
		return true;
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 7500 / MathUtils.getDistance(entity.entity, entity.entity.getStarSystem().getCenter());
		if (entity.market.hasCondition(Conditions.POOR_LIGHT)) weight *= 0.5f;
		if (weight < 0.3) return 0;
		if (weight > 5) weight = 5;
		
		return weight * getFactionMult(entity);
	}
}
