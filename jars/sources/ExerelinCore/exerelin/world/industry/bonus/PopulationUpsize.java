package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen;

public class PopulationUpsize extends BonusGen {
	
	public PopulationUpsize() {
		super(Industries.POPULATION);
	}
	
	@Override
	public float getPriority(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		float priority = entity.market.getSize();
		if (entity.isHQ) priority *= 10;
		
		return priority;
	}
	
	@Override
	public void apply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		MarketAPI market = entity.market;
		// do our own upsize because CoreImmigrationPluginImpl checks max size
		market.removeCondition("population_" + market.getSize());
		market.addCondition("population_" + (market.getSize() + 1));

		market.setSize(market.getSize() + 1);
		market.reapplyConditions();
		market.reapplyIndustries();
		super.apply(ind, entity);
	}
}
