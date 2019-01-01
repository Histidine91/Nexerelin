package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen;

public class AICoreStation extends AICore {
	
	public AICoreStation() {
		super(new String[0]);
	}
	
	@Override
	public boolean canApply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		if (!ind.getSpec().hasTag(Industries.TAG_STATION))
			return false;
		return ind.getAICoreId() == null;
	}
	
	@Override
	public float getPriority(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		float priority = super.getPriority(ind, entity);
		if (entity.isHQ) priority *= 5;
		
		return priority;
	}
	
	@Override
	public void apply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		ind.setAICoreId(Commodities.ALPHA_CORE);
		super.apply(ind, entity);
	}
}
