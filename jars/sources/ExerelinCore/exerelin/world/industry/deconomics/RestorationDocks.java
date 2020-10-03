package exerelin.world.industry.deconomics;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class RestorationDocks extends IndustryClassGen {

	public RestorationDocks() {
		super("IndEvo_dryDock");
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
		if (weight <= 0) weight = 0;
		return weight * getFactionMult(entity);
	}
}
