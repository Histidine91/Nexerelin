package exerelin.world.industry.deconomics;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class RequisitionsCenter extends IndustryClassGen {

	public RequisitionsCenter() {
		super("IndEvo_ReqCenter");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.getSize() < 5) return false;
		
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 1 + entity.market.getAccessibilityMod().computeEffective(0) * 0.5f;
		weight *= (entity.market.getSize()/2) - 1;
		if (entity.market.isFreePort()) weight *= 1.25f;
		if (entity.market.hasIndustry("commerce")) weight *= 1.25f;
		if (weight <= 0) weight = 0;
		return weight * getFactionMult(entity);
	}
}
