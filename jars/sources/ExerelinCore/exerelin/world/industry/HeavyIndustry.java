package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.econ.EconomyInfoHelper;
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
		// special handling for Derelict Empire: make sure capitals always have heavy industry
		if (Global.getSector().isInNewGameAdvance() 
				&& "derelict_empire".equals(ExerelinSetupData.getInstance().startScenario)) 
		{
			if (entity.isHQ) {
				//Global.getLogger(this.getClass()).info("Enforcing heavy industry for homeworld " + entity.name + "(" + entity.market.getFactionId() + ")");
				return 9999;
			}
		}
		
		MarketAPI market = entity.market;
		
		// upgrades have max priority
		if (market.hasIndustry(Industries.HEAVYINDUSTRY) || market.hasIndustry("ms_modularFac")) {
			return 9999 * market.getSize();
		}
		
		// prioritise new heavy industry if we don't have any
		if (!Global.getSector().isInNewGameAdvance()) {
			if (!EconomyInfoHelper.getInstance().hasHeavyIndustry(market.getFactionId()) 
					&& market.getSize() >= 4)
				return 99999;
		}
		
		float weight = (25 + market.getSize() * 5) * 2;
				
		// bad for high hazard worlds
		weight += (150 - market.getHazardValue()) * 2;
		
		// prefer not to be on same planet as fuel production
		if (market.hasIndustry(Industries.FUELPROD))
			weight -= 200;
		// or light industry
		if (market.hasIndustry(Industries.LIGHTINDUSTRY))
			weight -= 100;
		
		weight *= getFactionMult(entity);
		
		return weight;
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.ORBITALWORKS) || market.hasIndustry("ms_massIndustry")
				|| market.hasIndustry("deconomics_ScrapYard"))
			return false;
		
		// don't build heavy industry on new small colonies, they're raid bait
		if (!Global.getSector().isInNewGameAdvance()) {
			int minSize = 4;
			if (EconomyInfoHelper.getInstance().hasHeavyIndustry(market.getFactionId())) {
				minSize = 5;
			}
			if (market.getSize() < minSize)
				return false;
		}
		
		return true;
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
