package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.OreRefiningComplex;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

// fixes the unapply bug in the vanilla one
public class ExerelinOreRefiningComplex extends OreRefiningComplex {
	
	@Override
	public void unapply(String id) {
		super.unapply(id);
		market.getCommodityData(Commodities.METALS).getSupply().unmodify(id);
		market.getCommodityData(Commodities.RARE_METALS).getSupply().unmodify(id);
	}
	
}