package exerelin.world.industry;

import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;

public class Commerce extends IndustryClassGen {

	public Commerce() {
		super("commerce");
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		return entity.numProductiveIndustries / 2f;
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (entity.numProductiveIndustries >= NexMarketBuilder.getMaxProductiveIndustries(entity))
			return false;
		return super.canApply(entity);
	}
}
