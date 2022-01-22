package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Refining extends IndustryClassGen {

	public Refining() {
		super(Industries.REFINING);
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 200;
		MarketAPI market = entity.market;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.ORE_SPARSE:
				case Conditions.RARE_ORE_SPARSE:
					weight += 20;
					break;
				case Conditions.ORE_MODERATE:
				case Conditions.RARE_ORE_MODERATE:
					weight += 50;
					break;
				case Conditions.ORE_ABUNDANT:
				case Conditions.RARE_ORE_ABUNDANT:
					weight += 75;
					break;
				case Conditions.ORE_RICH:
				case Conditions.RARE_ORE_RICH:
					weight += 100;
					break;
				case Conditions.ORE_ULTRARICH:
				case Conditions.RARE_ORE_ULTRARICH:
					weight += 150;
					break;
			}
		}
		// bad for high hazard worlds
		weight += (175 - market.getHazardValue()) * 2f;
		weight *= 1.5f;
		
		// if we're not already producing metals, prioritise it
		if (!Global.getSector().isInNewGameAdvance()) {
			if (EconomyInfoHelper.getInstance().getFactionCommodityProduction(
					market.getFactionId(), Commodities.METALS) <= 0)
				weight *= 2f;
		}
		
		weight *= getCountWeightModifier(4);
		
		weight *= getFactionMult(entity);
		
		return weight;
	}
}
