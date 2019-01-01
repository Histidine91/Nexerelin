package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Mining extends IndustryClassGen {

	public Mining() {
		super(Industries.MINING);
	}

	@Override
	public float getPriority(ProcGenEntity entity) {
		float priority = 0;
		MarketAPI market = entity.market;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			// volatiles are sufficiently rare that we want to make sure there's a source of them
			switch (cond.getId())
			{
				case Conditions.ORE_SPARSE:
				case Conditions.RARE_ORE_SPARSE:
				case Conditions.ORGANICS_TRACE:
					priority += 25;
					break;
				case Conditions.ORE_MODERATE:
				case Conditions.RARE_ORE_MODERATE:
				case Conditions.ORGANICS_COMMON:
					priority += 75;
					break;
				case Conditions.ORE_ABUNDANT:
				case Conditions.RARE_ORE_ABUNDANT:
				case Conditions.ORGANICS_ABUNDANT:
					priority += 100;
					break;
				case Conditions.ORE_RICH:
				case Conditions.RARE_ORE_RICH:
				case Conditions.ORGANICS_PLENTIFUL:
				case Conditions.VOLATILES_TRACE:
					priority += 150;
					break;
				case Conditions.ORE_ULTRARICH:
				case Conditions.RARE_ORE_ULTRARICH:
				case Conditions.VOLATILES_DIFFUSE:
					priority += 200;
					break;
				case Conditions.VOLATILES_ABUNDANT:
					priority += 250;
					break;
				case Conditions.VOLATILES_PLENTIFUL:
					priority += 300;
					break;
			}
		}
		return priority;
	}
}
