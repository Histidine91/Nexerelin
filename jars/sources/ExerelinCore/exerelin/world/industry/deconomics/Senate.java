package exerelin.world.industry.deconomics;

import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Senate extends IndustryClassGen {

	public Senate() {
		super("IndEvo_senate");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (!entity.isHQ) return false;		
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 4;
		return weight * getFactionMult(entity);
	}
}
