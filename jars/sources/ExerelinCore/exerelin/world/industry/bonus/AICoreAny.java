package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen;

public class AICoreAny extends AICore {
	
	@Override
	public boolean canApply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		if (ind.getId().equals(Industries.POPULATION)) return false;
		return super.canApply(ind, entity);
	}
	
	@Override
	public float getPriority(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		return 100;
	}
}
