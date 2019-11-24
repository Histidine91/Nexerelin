package exerelin.world.industry;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TechMining extends IndustryClassGen {
	public static final Set<String> RUINS = new HashSet<>(Arrays.asList(
			Conditions.RUINS_SCATTERED, Conditions.RUINS_WIDESPREAD,
			Conditions.RUINS_EXTENSIVE, Conditions.RUINS_VAST
	));
	

	public TechMining() {
		super(Industries.TECHMINING);
	}
	
	@Override
	public boolean canApply(ProcGenEntity entity) {
		if (entity.market.getFactionId().equals(Factions.LUDDIC_PATH))
			return false;
		for (String ruinCond : RUINS)
		{
			if (entity.market.hasCondition(ruinCond))
				return true;
		}
		return false;
	}

	@Override
	public float getWeight(ProcGenEntity entity) {
		MarketAPI market = entity.market;
		float mult = getFactionMult(entity);
				
		for (MarketConditionAPI cond : market.getConditions())
		{
			switch (cond.getId())
			{
				case Conditions.RUINS_SCATTERED:
					return 200 * mult;
				case Conditions.RUINS_WIDESPREAD:
					return 500 * mult;
				case Conditions.RUINS_EXTENSIVE:
					return 1000 * mult;
				case Conditions.RUINS_VAST:
					return 9000 * mult;
			}
		}
		return 0;
	}
}
