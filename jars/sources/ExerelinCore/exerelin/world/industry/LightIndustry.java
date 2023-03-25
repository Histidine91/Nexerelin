package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.world.ExerelinProcGen.EntityType;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class LightIndustry extends IndustryClassGen {

	public LightIndustry() {
		super(Industries.LIGHTINDUSTRY);
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		boolean newGame = Global.getSector().isInNewGameAdvance();
		
		// hax so this isn't the default choice for little stations
		if (newGame && entity.type == EntityType.STATION && StarSystemGenerator.random.nextBoolean())
			return -1;
		
		MarketAPI market = entity.market;
		
		float weight = 25 + market.getSize() * 4;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.ORGANICS_TRACE:
					weight += 20;
					break;
				case Conditions.ORGANICS_COMMON:
					weight += 50;
					break;
				case Conditions.ORGANICS_ABUNDANT:
					weight += 75;
					break;
				case Conditions.ORGANICS_PLENTIFUL:
					weight += 100;
					break;
			}
		}
		
		// bad for high hazard worlds
		weight += (175 - market.getHazardValue()) * 2;
		
		// prefer to not be on same planet as heavy industry
		if (HeavyIndustry.hasHeavyIndustry(market))
			weight -= 100;
		// nor fuel production
		if (market.hasIndustry(Industries.FUELPROD))
			weight -= 75;
		
		// if we're not already producing domestic goods, prioritise it
		if (!newGame) {
			if (EconomyInfoHelper.getInstance().getFactionCommodityProduction(
					market.getFactionId(), Commodities.DOMESTIC_GOODS) <= 0)
				weight *= 2f;
		}

		// drug lab dosh :^)
		if (market.isFreePort()) weight *= 2.5f;
		
		weight *= getFactionMult(entity);
		
		return weight;
	}
}
