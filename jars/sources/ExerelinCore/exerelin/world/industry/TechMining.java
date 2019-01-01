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
					return 200;
				case Conditions.RUINS_WIDESPREAD:
					return 500;
				case Conditions.RUINS_EXTENSIVE:
					return 1000;
				case Conditions.RUINS_VAST:
					return 9000;
			}
		}
		return 0;
	}
}
