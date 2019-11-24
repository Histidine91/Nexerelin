package exerelin.world.industry;

import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class MedCenter extends IndustryClassGen {

	public MedCenter() {
		super("ms_medCenter");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (!entity.market.getFactionId().equals("shadow_industry"))
			return false;
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		return (1 + entity.market.getSize() / 5) * getFactionMult(entity);
	}
}
