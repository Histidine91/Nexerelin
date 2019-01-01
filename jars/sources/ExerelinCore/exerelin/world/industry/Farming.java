package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.EntityType;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class Farming extends IndustryClassGen {

	public Farming() {
		super(Industries.FARMING, Industries.AQUACULTURE);
	}

	@Override
	public float getPriority(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		
		// aquaculture
		if (entity.type != EntityType.STATION 
				&& com.fs.starfarer.api.impl.campaign.econ.impl.Farming.AQUA_PLANETS.contains(entity.planetType)) 
			return 9000;
		
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				/*
				case Conditions.FARMLAND_POOR:
					return 100;
				case Conditions.FARMLAND_ADEQUATE:
					return 250;
				case Conditions.FARMLAND_RICH:
					return 400;
				case Conditions.FARMLAND_BOUNTIFUL:
					return 600;
				*/
				case Conditions.FARMLAND_POOR:
					return 400;
				case Conditions.FARMLAND_ADEQUATE:
				case Conditions.FARMLAND_RICH:
				case Conditions.FARMLAND_BOUNTIFUL:
					return 9000;
			}
		}
		return 0;
	}
	
	@Override
	public boolean canApply(String factionId, ProcGenEntity entity) {
		if (entity.market.hasIndustry(Industries.AQUACULTURE)) return false;
		return super.canApply(factionId, entity);
	}
		
	@Override
	public void apply(ProcGenEntity entity) {
		if (com.fs.starfarer.api.impl.campaign.econ.impl.Farming.AQUA_PLANETS.contains(entity.planetType))
			entity.market.addIndustry(Industries.AQUACULTURE);
		else
			entity.market.addIndustry(Industries.FARMING);
	}
}
