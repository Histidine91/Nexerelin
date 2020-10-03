package exerelin.world.industry.deconomics;

import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Academy extends IndustryClassGen {

	public Academy() {
		super("IndEvo_Academy");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		int size = entity.market.getSize();
		if (size < 5) return false;		
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = entity.market.getAccessibilityMod().computeEffective(0) * 2;
		if (weight < 0.6) return 0;
		
		if (entity.isCapital || entity.isHQ) weight *= 2;
		return weight * getFactionMult(entity);
	}
}
