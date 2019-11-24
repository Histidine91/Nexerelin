package exerelin.world.industry;

import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Cryosanctum extends IndustryClassGen {

	public Cryosanctum() {
		super(Industries.CRYOSANCTUM);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 6 - entity.market.getSize();
		if (weight < 0) weight = 0;
		return (weight * getFactionMult(entity) / 2) + 0.5f;
	}
}
