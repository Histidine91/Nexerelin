package exerelin.world.industry.vic;

import exerelin.utilities.NexUtilsFaction;
import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class RevCenter extends IndustryClassGen {

	public RevCenter() {
		super("vic_revCenter");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {		
		if (NexUtilsFaction.isLuddicFaction(entity.market.getFactionId()))
			return false;
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 2.25f + entity.market.getSize()/2f;
		if (entity.market.getFactionId().equals("vic")) weight *= 1.5f;
		return weight * getFactionMult(entity);
	}
}
