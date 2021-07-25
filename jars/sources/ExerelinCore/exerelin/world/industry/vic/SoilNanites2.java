package exerelin.world.industry.vic;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import exerelin.world.ExerelinProcGen;
import exerelin.world.industry.bonus.BonusGen;

public class SoilNanites2 extends BonusGen {
	
	public SoilNanites2() {
		super(Industries.FARMING, "vicbiolabs");
	}
	
	public boolean hasOrganics(MarketAPI market) {
		return market.hasCondition(Conditions.ORGANICS_TRACE) 
				|| market.hasCondition(Conditions.ORGANICS_COMMON)
				|| market.hasCondition(Conditions.ORGANICS_ABUNDANT)
				|| market.hasCondition(Conditions.ORGANICS_PLENTIFUL);
	}
	
	@Override
	public boolean canApply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		if (ind.getSpecialItem() != null)
			return false;
		if (!hasOrganics(entity.market)) 
			return false;
		
		return super.canApply(ind, entity);
	}
	
	@Override
	public void apply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		String type = "vic_soil_nanites2";
		ind.setSpecialItem(new SpecialItemData(type, null));
		super.apply(ind, entity);
	}
}
