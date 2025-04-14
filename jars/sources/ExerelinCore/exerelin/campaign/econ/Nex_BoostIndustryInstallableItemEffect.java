package exerelin.campaign.econ;

import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.econ.impl.BoostIndustryInstallableItemEffect;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import exerelin.utilities.StringHelper;
import java.util.List;
import java.util.Set;

public abstract class Nex_BoostIndustryInstallableItemEffect extends BoostIndustryInstallableItemEffect {
	
	public static String STATION_OR_NO_ATMO = StringHelper.getString("nex_industry", "req_noAtmoOrIsStation");
	
	public Nex_BoostIndustryInstallableItemEffect(String id, int supplyIncrease, int demandIncrease) {
		super(id, supplyIncrease, demandIncrease);
	}
	
	@Override
	public List<String> getUnmetRequirements(Industry industry, boolean checkSurveyed) {
		List<String> unmet = super.getUnmetRequirements(industry, checkSurveyed);
		if (industry == null) return unmet;
		
		MarketAPI market = industry.getMarket();

		for (String curr : getRequirements(industry)) {
			if (getRequirements(industry).contains(STATION_OR_NO_ATMO)) {
				if (!market.hasCondition(Conditions.NO_ATMOSPHERE) && market.getPlanetEntity() != null)
				{
					unmet.add(curr);
				}
			}
		}
		
		return unmet;
	}

	@Override
	public Set<String> getConditionsRelatedToRequirements(Industry industry) {
		Set<String> cond = super.getConditionsRelatedToRequirements(industry);
		if (getRequirements(industry).contains(STATION_OR_NO_ATMO)) {
			cond.add(Conditions.NO_ATMOSPHERE);
		}
		return cond;
	}
}
