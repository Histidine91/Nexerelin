package data.scripts.campaign.econ;

import com.fs.starfarer.api.impl.campaign.econ.VolturnianLobster;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;

public class Exerelin_VolturnianLobster extends VolturnianLobster {
	
	public static final int LOBSTER_PENS_SMALL_LOBSTER = 2000;	// vanilla is 100k
	
	@Override
	public void apply(String id) {
		market.getCommodityData(Commodities.LOBSTER).getSupply().modifyFlat(id, LOBSTER_PENS_SMALL_LOBSTER);
	}
}