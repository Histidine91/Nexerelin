package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import exerelin.world.ExerelinProcGen;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class FuelProduction extends IndustryClassGen {

	public FuelProduction() {
		super(Industries.FUELPROD);
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		
		float weight = 25 + market.getSize() * 5;
		
		// don't give bonus for local volatiles; we don't want this thing competing with volatiles mines
		/*
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
		*/
		
		// bad for high hazard worlds
		weight += (175 - market.getHazardValue()) * 2;
		
		// prefer to not be on same planet as heavy industry
		if (HeavyIndustry.hasHeavyIndustry(market))
			weight -= 200;
		// or light industry for that matter
		if (market.hasIndustry(Industries.LIGHTINDUSTRY))
			weight -= 75;
		
		// hax so this isn't the default choice for little stations
		if (entity.type == ExerelinProcGen.EntityType.STATION && StarSystemGenerator.random.nextBoolean())
			weight = -1;
		
		return weight;
	}
}
