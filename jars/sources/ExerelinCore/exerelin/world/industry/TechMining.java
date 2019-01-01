package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;

public class TechMining extends IndustryClassGen {

	public TechMining() {
		super(Industries.TECHMINING);
	}

	@Override
	public float getPriority(ProcGenEntity entity) {
		MarketAPI market = entity.market;
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.RUINS_SCATTERED:
					return 50;
				case Conditions.RUINS_WIDESPREAD:
					return 125;
				case Conditions.RUINS_EXTENSIVE:
					return 250;
				case Conditions.RUINS_VAST:
					return 500;
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
