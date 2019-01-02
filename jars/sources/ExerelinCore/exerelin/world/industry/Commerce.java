package exerelin.world.industry;

import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Commerce extends IndustryClassGen {

	public Commerce() {
		super("commerce");
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		return entity.numProductiveIndustries / 2f;
	}
}
