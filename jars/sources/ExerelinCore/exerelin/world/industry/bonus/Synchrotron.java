package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import exerelin.world.ExerelinProcGen;

public class Synchrotron extends BonusGen {
	
	public Synchrotron() {
		super(Industries.FUELPROD);
	}
	
	public static boolean isSynchrotronApplicable(MarketAPI market) {
		return market.hasCondition(Conditions.NO_ATMOSPHERE) || market.getPlanetEntity() == null;
	}
	
	@Override
	public boolean canApply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		if (ind.getSpecialItem() != null)
			return false;
		if (!isSynchrotronApplicable(entity.market))
			return false;
		
		return super.canApply(ind, entity);
	}
	
	@Override
	public void apply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		String type = Items.SYNCHROTRON;
		ind.setSpecialItem(new SpecialItemData(type, null));
		super.apply(ind, entity);
	}
}
