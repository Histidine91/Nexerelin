package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class HeavyIndustry extends IndustryClassGen {
	
	protected static final Set<String> HEAVY_INDUSTRY = new HashSet<>(Arrays.asList(
		Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS, "ms_modularFac", "ms_massIndustry"));

	public HeavyIndustry() {
		super(Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS, 
				"ms_modularFac", "ms_massIndustry");
	}

	@Override
	public float getPriority(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		
		float priority = 25 + market.getSize() * 5;
				
		// bad for high hazard worlds
		priority += (175 - market.getHazardValue()) * 5;
		
		return priority;
	}
	
	@Override
	public boolean canApply(String factionId, ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.ORBITALWORKS) || market.hasIndustry("ms_massIndustry"))
			return false;
		
		return super.canApply(factionId, entity);
	}
	
	@Override
	public void apply(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.HEAVYINDUSTRY)) {
			Industry ind = market.getIndustry(Industries.HEAVYINDUSTRY);
			ind.startUpgrading();
			ind.finishBuildingOrUpgrading();
		}
		else if (market.hasIndustry("ms_modularFac")) {
			Industry ind = market.getIndustry("ms_modularFac");
			ind.startUpgrading();
			ind.finishBuildingOrUpgrading();
		}
		else if (market.getFactionId().equals("shadow_industry"))
			market.addIndustry("ms_modularFac");
		else
			market.addIndustry(Industries.HEAVYINDUSTRY);
		
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
