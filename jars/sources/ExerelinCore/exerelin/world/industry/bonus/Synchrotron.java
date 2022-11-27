package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import exerelin.campaign.ColonyManager;
import exerelin.world.ExerelinProcGen;
import exerelin.world.NexMarketBuilder;
import lombok.extern.log4j.Log4j;

@Log4j
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

		log.info(String.format("Upgrading defenses on %s after adding %s", entity.market.getName(), name));
		NexMarketBuilder.addOrQueueHeavyBatteries(entity.market, ColonyManager.getManager(), true);
		NexMarketBuilder.addOrUpgradeStation(entity.market, -1, true, ColonyManager.getManager(), marketBuilder.getRandom());
	}
}
