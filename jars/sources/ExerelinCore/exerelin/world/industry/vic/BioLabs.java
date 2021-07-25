package exerelin.world.industry.vic;

import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class BioLabs extends IndustryClassGen {

	public BioLabs() {
		super("vicbiolabs");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (!entity.market.getFactionId().equals("vic"))
			return false;
		
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = (2 + entity.market.getSize() / 5) * getFactionMult(entity);
		if (entity.market.hasCondition(Conditions.HABITABLE)) weight *= 0.25f;
		return weight;
	}
	
	@Override
	public boolean canAutogen() {
		return false;
	}
}
