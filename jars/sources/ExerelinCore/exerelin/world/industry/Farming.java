package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.EntityType;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Farming extends IndustryClassGen {
	
	public final Set<String> FARMING_CONDITIONS = new HashSet<>(Arrays.asList(
		Conditions.FARMLAND_POOR, Conditions.FARMLAND_ADEQUATE, Conditions.FARMLAND_RICH, Conditions.FARMLAND_BOUNTIFUL
	));

	public Farming() {
		super(Industries.FARMING, Industries.AQUACULTURE);
	}
	
	public boolean isWater(ProcGenEntity entity) {
		return com.fs.starfarer.api.impl.campaign.econ.impl.Farming.AQUA_PLANETS.contains(entity.planetType) || 
				entity.market.hasCondition(Conditions.WATER_SURFACE);
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		
		// aquaculture
		if (entity.type != EntityType.STATION && isWater(entity)) 
			return 90000 * getFactionMult(entity);
		
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
					return 400* getFactionMult(entity);
				case Conditions.FARMLAND_ADEQUATE:
				case Conditions.FARMLAND_RICH:
				case Conditions.FARMLAND_BOUNTIFUL:
					return 90000 * getFactionMult(entity);
			}
		}
		return 0;
	}
		
	@Override
	public void apply(ProcGenEntity entity, boolean instant) {
		MarketAPI market = entity.market;
		
		// farming fix hack
		if (market.hasCondition(Conditions.WATER_SURFACE))
		{
			for (String cond : FARMING_CONDITIONS)
			{
				if (market.hasCondition(cond))
				{
					Global.getLogger(this.getClass()).info("Removing farming condition " + cond + " from planet with water surface");
					market.removeCondition(cond);
				}
			}
			
			// add lobster pens with aquaculture during random sector generation
			// but not for industries being built on existing colonies
			if (Global.getSector().isInNewGameAdvance())
				market.addCondition(Conditions.VOLTURNIAN_LOBSTER_PENS);
		}
		
		String id = Industries.FARMING;
		if (isWater(entity))
			id = Industries.AQUACULTURE;
		
		NexMarketBuilder.addIndustry(market, id, this.id, instant);
		entity.numProductiveIndustries += 1;
	}
}
