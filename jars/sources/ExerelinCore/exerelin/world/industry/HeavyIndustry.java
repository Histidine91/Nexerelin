package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class HeavyIndustry extends IndustryClassGen {
	
	public static final Set<String> HEAVY_INDUSTRY = new HashSet<>(Arrays.asList(
		Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS, "ms_modularFac", "ms_massIndustry"));

	public HeavyIndustry() {
		super(Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS, 
				"ms_modularFac", "ms_massIndustry");
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		
		float weight = (25 + market.getSize() * 5) * 2;
				
		// bad for high hazard worlds
		weight += (150 - market.getHazardValue()) * 2;
		
		// prefer not to be on same planet as fuel production
		if (market.hasIndustry(Industries.FUELPROD))
			weight -= 400;
		// or light industry
		if (market.hasIndustry(Industries.LIGHTINDUSTRY))
			weight -= 250;
		
		return weight;
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.ORBITALWORKS) || market.hasIndustry("ms_massIndustry"))
			return false;
		
		return super.canApply(entity);
	}
	
	@Override
	public void apply(ProcGenEntity entity, boolean instant) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.HEAVYINDUSTRY)) {
			Industry ind = market.getIndustry(Industries.HEAVYINDUSTRY);
			ind.startUpgrading();
			if (instant) ind.finishBuildingOrUpgrading();
		}
		else if (market.hasIndustry("ms_modularFac")) {
			Industry ind = market.getIndustry("ms_modularFac");
			ind.startUpgrading();
			if (instant) ind.finishBuildingOrUpgrading();
		}
		else {
			String id = Industries.HEAVYINDUSTRY;
			if (market.getFactionId().equals("shadow_industry"))
				id = "ms_modularFac";
			
			NexMarketBuilder.addIndustry(market, id, instant);
		}
		
		entity.numProductiveIndustries += 1;
	}
	
	public static boolean hasHeavyIndustry(MarketAPI market)
	{
		for (String ind : HEAVY_INDUSTRY)
		{
			if (market.hasIndustry(ind))
				return true;
		}
		return false;
	}
}
