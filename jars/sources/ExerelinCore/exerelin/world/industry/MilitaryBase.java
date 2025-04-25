package exerelin.world.industry;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;

public class MilitaryBase extends IndustryClassGen {

	public MilitaryBase() {
		super(
				Industries.PATROLHQ,
				Industries.MILITARYBASE, 
				Industries.HIGHCOMMAND
		);
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (entity.market.hasIndustry(Industries.HIGHCOMMAND))
			return false;
		if (entity.market.hasIndustry(Industries.MILITARYBASE))
			return entity.market.getSize() >= 6;
		
		return true;
	}

	@Override
	public void apply(ProcGenEntity entity, boolean instant) {
		// addIndustry will now automatically upgrade Patrol HQ if it exists, or upgrade Military Base if it exists
		NexMarketBuilder.addIndustry(entity.market, Industries.MILITARYBASE, this.id, instant);
		entity.numProductiveIndustries += 1;
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		return (2 + entity.market.getSize() / 2) * 10 * getFactionMult(entity);
	}
	
	@Override
	public boolean canAutogen() {
		return false;
	}
}
