package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
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
		CoreImmigrationPluginImpl.increaseMarketSize(entity.market);
		super.apply(ind, entity);
	}
}
