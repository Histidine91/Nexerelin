package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
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
		// If already have military base, upgrade to high command
		if (entity.market.hasIndustry(Industries.MILITARYBASE)) {
			Industry ind = entity.market.getIndustry(Industries.MILITARYBASE);
			ind.startUpgrading();
			if (instant) ind.finishBuildingOrUpgrading();
			return;
		}
		// if already have patrol HQ, upgrade to military base
		else if (entity.market.hasIndustry(Industries.PATROLHQ)) {
			Industry ind = entity.market.getIndustry(Industries.PATROLHQ);
			ind.startUpgrading();
			if (instant) ind.finishBuildingOrUpgrading();
			return;
		}
		// build military base directly
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
