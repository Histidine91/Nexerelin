package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class LightIndustry extends IndustryClassGen {

	public LightIndustry() {
		super(Industries.LIGHTINDUSTRY);
	}

	@Override
	public float getPriority(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		
		float priority = 25 + market.getSize() * 4;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.ORGANICS_TRACE:
					priority += 20;
					break;
				case Conditions.ORGANICS_COMMON:
					priority += 50;
					break;
				case Conditions.ORGANICS_ABUNDANT:
					priority += 75;
					break;
				case Conditions.ORGANICS_PLENTIFUL:
					priority += 100;
					break;
			}
		}
		
		// bad for high hazard worlds
		priority += (175 - market.getHazardValue()) * 2;
		
		// prefer to not be on same planet as heavy industry
		if (HeavyIndustry.hasHeavyIndustry(market))
			priority -= 300;
		// nor fuel production
		if (market.hasIndustry(Industries.FUELPROD))
			priority -= 250;
		
		return priority;
	}
}
