package exerelin.world.industry;

import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class MedCenter extends IndustryClassGen {

	public MedCenter() {
		super("ms_medCenter");
	}
	
	@Override
	public boolean canApply(String factionId, ProcGenEntity entity) {
		if (!factionId.equals("shadow_industry"))
			return false;
		return super.canApply(factionId, entity);
	}
	
	@Override
	public float getSpecialWeight(ProcGenEntity entity) {
		return 1 + entity.market.getSize() / 5;
	}
}
