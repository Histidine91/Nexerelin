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
	public float getWeight(ProcGenEntity entity) {
		float weight = 250;
		MarketAPI market = entity.market;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.ORE_SPARSE:
				case Conditions.RARE_ORE_SPARSE:
					weight += 25;
					break;
				case Conditions.ORE_MODERATE:
				case Conditions.RARE_ORE_MODERATE:
					weight += 75;
					break;
				case Conditions.ORE_ABUNDANT:
				case Conditions.RARE_ORE_ABUNDANT:
					weight += 100;
					break;
				case Conditions.ORE_RICH:
				case Conditions.RARE_ORE_RICH:
					weight += 150;
					break;
				case Conditions.ORE_ULTRARICH:
				case Conditions.RARE_ORE_ULTRARICH:
					weight += 200;
					break;
			}
		}
		// bad for high hazard worlds
		weight += (175 - market.getHazardValue()) * 2f;
		
		return weight;
	}
}
