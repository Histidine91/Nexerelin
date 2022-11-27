package exerelin.world.industry.bonus;

import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import exerelin.campaign.ColonyManager;
import exerelin.world.ExerelinProcGen;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.HeavyIndustry;
import lombok.extern.log4j.Log4j;

@Log4j
public class Nanoforge extends BonusGen {
	
	public Nanoforge() {
		super(HeavyIndustry.HEAVY_INDUSTRY);
	}
	
	@Override
	public boolean canApply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		if (ind.getSpecialItem() != null)
			return false;
		return super.canApply(ind, entity);
	}
	
	@Override
	public float getPriority(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		float priority = 100;
		String indId = ind.getId();
		if (indId.equals(Industries.ORBITALWORKS) || indId.equals("ms_massIndustry"))
			priority *= 2;
		if (entity.isHQ) priority += 100;
		
		return priority;
	}
	
	@Override
	public void apply(Industry ind, ExerelinProcGen.ProcGenEntity entity) {
		boolean corrupted = id.equals("nanoforge_corrupted");
		String type = corrupted ? Items.CORRUPTED_NANOFORGE : Items.PRISTINE_NANOFORGE;
		ind.setSpecialItem(new SpecialItemData(type, null));
		super.apply(ind, entity);
		
		if (!corrupted) {
			log.info(String.format("Upgrading defenses on %s after adding %s", entity.market.getName(), name));
			NexMarketBuilder.addOrQueueHeavyBatteries(entity.market, ColonyManager.getManager(), true);
			NexMarketBuilder.addOrUpgradeStation(entity.market, -1, true, ColonyManager.getManager(), marketBuilder.getRandom());
		}
	}
}
