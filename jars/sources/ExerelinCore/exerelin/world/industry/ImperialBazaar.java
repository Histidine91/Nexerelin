package exerelin.world.industry;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class ImperialBazaar extends IndustryClassGen {
	
	public ImperialBazaar() {
		super("ii_interstellarbazaar");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (entity.market.getIndustries().size() >= 12) return false;
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = entity.market.getSize() * 100 * getFactionMult(entity);
		if (entity.market.hasIndustry(Industries.MILITARYBASE) || entity.market.hasIndustry(Industries.HIGHCOMMAND))
			weight = weight/2 + 1;
		
		return weight;
	}
	
	@Override
	public void apply(ProcGenEntity entity, boolean instant) {
		entity.market.addIndustry("ii_interstellarbazaar");
		super.apply(entity, instant);
	}
	
	@Override
	public boolean canAutogen() {
		return false;
	}
}
