package exerelin.world.industry;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
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
		Industries.HEAVYINDUSTRY, Industries.ORBITALWORKS, "ms_modularFac", 
			"ms_massIndustry", "ms_militaryProduction", "ms_orbitalShipyard"));

	public HeavyIndustry() {
		super(HEAVY_INDUSTRY);
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
		if (market.hasIndustry(Industries.HEAVYINDUSTRY)) {
			return 9999 * market.getSize();
		}
		
		// prioritise new heavy industry if we don't have any
		if (!Global.getSector().isInNewGameAdvance()) {
			if (!EconomyInfoHelper.getInstance().hasHeavyIndustry(market.getFactionId()) 
					&& market.getSize() >= 4)
				return 99999;
		}
		
		float weight = 20 + market.getSize() * 4;
				
		// bad for high hazard worlds
		weight += (150 - market.getHazardValue()) * 2;
		
		// nanoforges cause pollution on habitable worlds, so try to avoid that
		if (market.hasCondition(Conditions.POLLUTION) || !market.hasCondition(Conditions.HABITABLE))
			weight *= 2;
		else
			weight /= 2;
		
		// prefer not to be on same planet as fuel production
		if (market.hasIndustry(Industries.FUELPROD))
			weight -= 200;
		// or light industry
		if (market.hasIndustry(Industries.LIGHTINDUSTRY))
			weight -= 100;
		
		weight *= getCountWeightModifier(9);
		
		weight *= getFactionMult(entity);
		
		return weight;
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.hasIndustry(Industries.ORBITALWORKS) 
				|| market.hasIndustry("ms_massIndustry")
				|| market.hasIndustry("ms_militaryProduction")
				|| market.hasIndustry("ms_orbitalShipyard")
				|| market.hasIndustry("IndEvo_ScrapYard"))
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
			// FIXME: figure out how new upgrade system works
			//Industry ind = market.getIndustry("ms_modularFac");
			//ind.startUpgrading();
			//if (instant) ind.finishBuildingOrUpgrading();
		}
		else {
			boolean upgrade = false;
			if (Global.getSector().isInNewGameAdvance()) {
				//upgrade = Math.random() < 0.25f;
			}
			
			String id;
			if (market.getFactionId().equals("shadow_industry"))
				id = upgrade ? "ms_massIndustry" : "ms_modularFac";
			else
				id = upgrade ? Industries.ORBITALWORKS : Industries.HEAVYINDUSTRY;
			
			NexMarketBuilder.addIndustry(market, id, this.id, instant);
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
