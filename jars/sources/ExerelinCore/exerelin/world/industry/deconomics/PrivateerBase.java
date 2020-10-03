package exerelin.world.industry.deconomics;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class PrivateerBase extends IndustryClassGen {

	public PrivateerBase() {
		super("IndEvo_pirateHaven");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		int size = entity.market.getSize();
		if (size < 4 || size > 6) return false;		
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 2.5f;
		if (entity.market.hasIndustry(Industries.HIGHCOMMAND)) return 0;
		else if (entity.market.hasIndustry(Industries.MILITARYBASE)) weight *= 0.5f;
		return weight * getFactionMult(entity);
	}
}
