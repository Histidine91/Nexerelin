package exerelin.world.industry.deconomics;

import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class VariableAssembler extends IndustryClassGen {

	public VariableAssembler() {
		super("IndEvo_AdAssem");
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = entity.market.getSize() * 0.5f + 1;
		return weight * getFactionMult(entity);
	}
}
