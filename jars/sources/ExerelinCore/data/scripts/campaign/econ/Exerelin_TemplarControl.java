package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.BaseMarketConditionPlugin;
import com.fs.starfarer.api.impl.campaign.econ.ConditionData;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class Exerelin_TemplarControl extends BaseMarketConditionPlugin {
	
	@Override
	public void apply(String id) {
		market.resetSmugglingValue();
                float marineNum = 0;
		switch (market.getSize()) {
                    case 1:
                            marineNum = ConditionData.OUTPOST_MARINES_SIZE_1;
                            break;
                    case 2:
                            marineNum = ConditionData.OUTPOST_MARINES_SIZE_2;
                            break;
                    case 3:
                            marineNum = ConditionData.OUTPOST_MARINES_SIZE_3;
                            break;
                    default:
                            marineNum = ConditionData.OUTPOST_MARINES_MAX;
		}
                market.getDemand(Commodities.MARINES).getDemand().modifyFlat(id, marineNum);
                market.getDemand(Commodities.MARINES).getNonConsumingDemand().modifyFlat(id, marineNum * ConditionData.CREW_MARINES_NON_CONSUMING_FRACTION);
		market.getStability().modifyFlat(id, 1, "Templar control");
	}
	
	@Override
	public void unapply(String id) {
		market.getStability().unmodify(id);
		market.getDemand(Commodities.MARINES).getDemand().unmodify(id);
                market.getDemand(Commodities.MARINES).getNonConsumingDemand().unmodify(id);
	}
}
