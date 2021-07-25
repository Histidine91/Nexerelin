package exerelin.world.industry.vic;

import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Tourism extends IndustryClassGen {

	public Tourism() {
		super("victourism");
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		return entity.market.getSize() * getFactionMult(entity);
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		return super.canApply(entity);
	}
	
	@Override
	public boolean canAutogen() {
		return false;
	}
}
