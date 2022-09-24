package exerelin.world.industry.deconomics;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.utilities.NexUtilsMarket;
import exerelin.world.industry.*;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class SalvageYards extends IndustryClassGen {

	public SalvageYards() {
		super("IndEvo_ScrapYard");
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		if (market.getSize() < 4) return false;
		// must have heavy industry first
		if (!NexUtilsMarket.hasHeavyIndustry(market)) return false;
		
		return super.canApply(entity);
	}
	
	@Override
	public float getWeight(ProcGenEntity entity) {
		float weight = 250;
		MarketAPI market = entity.market;
		
		// bad for high hazard worlds
		weight += (125 - market.getHazardValue()) * 2f;
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
