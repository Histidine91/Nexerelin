package exerelin.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.econ.MilitaryBase;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

public class ExerelinMilitaryBase extends MilitaryBase {
	public static final float EXTRA_MARINES_MULT = 1.4f;	// hax
	public static final float AGENT_SUPPLY = 8f;
	
	public void apply(String id) {
		super.apply(id);
		if (this.market.getFactionId().equals("templars"))
		{
			market.removeSubmarket(Submarkets.GENERIC_MILITARY);
		}
		market.getCommodityData(Commodities.MARINES).getSupply().modifyFlat(id, ConditionData.MILITARY_BASE_MARINES_SUPPLY * EXTRA_MARINES_MULT);
		market.getCommodityData("agent").getSupply().modifyFlat(id, AGENT_SUPPLY);
		market.getCommodityData("saboteur").getSupply().modifyFlat(id, AGENT_SUPPLY);
		market.getCommodityData(Commodities.MARINES).getDemand().getDemand().modifyFlat(id, 
				ConditionData.MILITARY_BASE_MARINES_DEMAND * EXTRA_MARINES_MULT);
		market.getCommodityData(Commodities.MARINES).getDemand().getNonConsumingDemand().modifyFlat(id, 
				ConditionData.MILITARY_BASE_MARINES_DEMAND * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION * EXTRA_MARINES_MULT);
		market.getCommodityData("agent").getDemand().getDemand().modifyFlat(id, AGENT_SUPPLY * 2);
		market.getCommodityData("agent").getDemand().getNonConsumingDemand().modifyFlat(id, AGENT_SUPPLY * 2
				* ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);
	}

	public void unapply(String id) {
		super.unapply(id);
		market.getCommodityData("agent").getSupply().unmodify(id);
		market.getCommodityData("saboteur").getSupply().unmodify(id);
		market.getCommodityData("agent").getDemand().getDemand().unmodify(id);
		market.getCommodityData("agent").getDemand().getNonConsumingDemand().unmodify(id);
	}

}
