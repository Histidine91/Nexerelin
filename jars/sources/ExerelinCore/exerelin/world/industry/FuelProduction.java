package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.world.ExerelinProcGen;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import java.util.Map;

public class FuelProduction extends IndustryClassGen {

	public FuelProduction() {
		super(Industries.FUELPROD);
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		boolean newGame = Global.getSector().isInNewGameAdvance();
		
		// hax so this isn't the default choice for little stations
		if (newGame && entity.type == ExerelinProcGen.EntityType.STATION && StarSystemGenerator.random.nextBoolean())
			return -1;
		
		MarketAPI market = entity.market;
		
		float weight = 25 + market.getSize() * 5;
		
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.VOLATILES_TRACE:
					priority += 20;
					break;
				case Conditions.VOLATILES_DIFFUSE:
					priority += 50;
					break;
				case Conditions.VOLATILES_ABUNDANT:
					priority += 75;
					break;
				case Conditions.VOLATILES_PLENTIFUL:
					priority += 100;
					break;
			}
		}
		
		// bad for high hazard worlds
		weight += (175 - market.getHazardValue()) * 2;
		
		// prefer to not be on same planet as heavy industry
		if (HeavyIndustry.hasHeavyIndustry(market))
			weight -= 200;
		// or light industry for that matter
		if (market.hasIndustry(Industries.LIGHTINDUSTRY))
			weight -= 75;
		
		// if we're not already producing fuel, prioritise it
		if (!newGame) {
			if (EconomyInfoHelper.getInstance().getFactionCommodityProduction(
					market.getFactionId(), Commodities.FUEL) <= 0)
				weight *= 2f;
		}
		
		return weight;
	}
}
