package exerelin.world.industry;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Waystation extends IndustryClassGen {

	public Waystation() {
		super(Industries.WAYSTATION);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 2 - entity.market.getAccessibilityMod().computeEffective(0);
		if (weight < 0) weight = 0.01f;
		weight *= 1.5f;
		return weight * getFactionMult(entity);
	}
}
