package exerelin.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.MilitaryBase;

public class ExerelinMilitaryBase extends MilitaryBase {

	public void apply(String id) {
		super.apply(id);
		market.getCommodityData("agent").getSupply().modifyFlat(id, 2);
		market.getCommodityData("saboteur").getSupply().modifyFlat(id, 1);
	}

	public void unapply(String id) {
		super.unapply(id);
		market.getCommodityData("agent").getSupply().unmodify(id);
		market.getCommodityData("saboteur").getSupply().unmodify(id);
	}

}
