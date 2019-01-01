package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Refining extends IndustryClassGen {

	public Refining() {
		super(Industries.REFINING);
	}

	@Override
	public float getPriority(ProcGenEntity entity) {
		float priority = 0;
		MarketAPI market = entity.market;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.ORE_SPARSE:
				case Conditions.RARE_ORE_SPARSE:
					priority += 25;
					break;
				case Conditions.ORE_MODERATE:
				case Conditions.RARE_ORE_MODERATE:
					priority += 75;
					break;
				case Conditions.ORE_ABUNDANT:
				case Conditions.RARE_ORE_ABUNDANT:
					priority += 100;
					break;
				case Conditions.ORE_RICH:
				case Conditions.RARE_ORE_RICH:
					priority += 150;
					break;
				case Conditions.ORE_ULTRARICH:
				case Conditions.RARE_ORE_ULTRARICH:
					priority += 200;
					break;
			}
		}
		// bad for high hazard worlds
		priority += (175 - market.getHazardValue()) * 2;
		
		priority /= 2;
		
		return priority;
	}
}
