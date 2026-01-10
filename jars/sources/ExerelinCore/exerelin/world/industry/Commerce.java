package exerelin.world.industry;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.util.Misc;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.industry.aotd.AotDIndustries;

public class Commerce extends IndustryClassGen {

	public Commerce() {
		super(Industries.COMMERCE, AotDIndustries.TRADE_POST, AotDIndustries.UNDERWORLD);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		//return entity.numProductiveIndustries / 2f;
		return Misc.getNumIndustries(entity.market)/2f * getFactionMult(entity);
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		//if (entity.numProductiveIndustries >= NexMarketBuilder.getMaxProductiveIndustries(entity))
		//	return false;
		if (Misc.getNumIndustries(entity.market) >= Misc.getMaxIndustries(entity.market))
			return false;
		return super.canApply(entity);
	}
	
	@Override
	public void apply(ProcGenEntity entity, boolean instant) {
		super.apply(entity, instant);
	}
}
