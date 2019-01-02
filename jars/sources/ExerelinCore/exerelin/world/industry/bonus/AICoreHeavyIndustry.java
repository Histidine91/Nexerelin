package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.industry.HeavyIndustry;

public class AICoreHeavyIndustry extends AICore {
	
	public AICoreHeavyIndustry()	{
		super(HeavyIndustry.HEAVY_INDUSTRY.toArray(new String[0]));
	}
	
	@Override
	public float getPriority(Industry ind, ProcGenEntity entity) {
		//float priority = ind.getBaseUpkeep() * entity.market.getUpkeepMult().getModifiedValue() / 1000;
		//priority /= (1 + entity.numBonuses/2);
		float priority = super.getPriority(ind, entity);
		String indId = ind.getId();
		if (indId.equals(Industries.ORBITALWORKS) || indId.equals("ms_massIndustry"))
			priority *= 2;
		if (entity.isHQ) priority *= 2;
		
		return priority;
	}
}
