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
	public float getWeight(ProcGenEntity entity) {
		float weight = 0;
		MarketAPI market = entity.market;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			// volatiles are sufficiently rare that we want to make sure there's a source of them
			// actually screw it, if there's anything even slightly minable, by God we will mine it!
			switch (cond.getId())
			{
				case Conditions.ORE_SPARSE:
				case Conditions.RARE_ORE_SPARSE:
				case Conditions.ORGANICS_TRACE:
				//	weight += 100;
				//	break;
				case Conditions.ORE_MODERATE:
				case Conditions.RARE_ORE_MODERATE:
				case Conditions.ORGANICS_COMMON:
				//	weight += 250;
				//	break;
				case Conditions.ORE_ABUNDANT:
				case Conditions.RARE_ORE_ABUNDANT:
				case Conditions.ORGANICS_ABUNDANT:
				//	weight += 400;
				//	break;
				case Conditions.ORE_RICH:
				case Conditions.RARE_ORE_RICH:
				case Conditions.ORGANICS_PLENTIFUL:
				//case Conditions.VOLATILES_TRACE:
				//	weight += 600;
				//	break;
				case Conditions.ORE_ULTRARICH:
				case Conditions.RARE_ORE_ULTRARICH:
				//case Conditions.VOLATILES_DIFFUSE:
				//	weight += 800;
				//	break;
					weight += 2000;
					break;
				case Conditions.VOLATILES_TRACE:
				case Conditions.VOLATILES_DIFFUSE:
				case Conditions.VOLATILES_ABUNDANT:
				case Conditions.VOLATILES_PLENTIFUL:
					return 999999;
			}
		}
		return weight;
	}
}
