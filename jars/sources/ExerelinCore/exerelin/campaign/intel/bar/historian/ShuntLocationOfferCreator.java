package exerelin.campaign.intel.bar.historian;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.historian.BaseHistorianOfferCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.historian.HistorianData;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.historian.HistorianData.HistorianOffer;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class ShuntLocationOfferCreator extends BaseHistorianOfferCreator {
	
	public static final boolean DEBUG_MODE = false;
	
	public ShuntLocationOfferCreator() {
		super();
		setFrequency(DEBUG_MODE ? 9999 : 5);
	}
	
	@Override
	public HistorianOffer createOffer(Random random, List<HistorianOffer> soFar) {
		//HistorianData hd = HistorianData.getInstance();
		
		SectorEntityToken entity = pickEntity(random, false);
		if (entity == null) return null;
				
		return new EntityLocationOffer(entity);
	}
	
	@Override
	public SectorEntityToken pickEntity(Random random, boolean allowDerelict) {
		return pickEntity(random, allowDerelict, new ArrayList<HistorianOffer>());
	}
	
	public SectorEntityToken pickEntity(Random random, boolean allowDerelict, List<HistorianOffer> soFar) 
	{		
		Set<SectorEntityToken> already = new HashSet<>();
		if (soFar != null) {
			for (HistorianOffer offer : soFar) {
				if (offer instanceof EntityLocationOffer) {
					EntityLocationOffer elo = (EntityLocationOffer)offer;
					already.add(elo.getTarget());
				}
			}
		}
		
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			if (!system.hasTag(Tags.HAS_CORONAL_TAP)) continue;
			
			for (SectorEntityToken entity : system.getEntitiesWithTag(Tags.CORONAL_TAP)) 
			{
				if (!entity.isDiscoverable()) continue;
				if (already.contains(entity)) continue;
				picker.add(entity);
			}
		}
		return picker.pick();
	}
	
	// runcode exerelin.campaign.intel.bar.historian.ShuntLocationOfferCreator.debug();
	public static void debug() {
		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			if (!system.hasTag(Tags.HAS_CORONAL_TAP)) continue;
			
			for (SectorEntityToken entity : system.getEntitiesWithTag(Tags.CORONAL_TAP)) 
			{
				if (!entity.isDiscoverable()) continue;
				Global.getLogger(ShuntLocationOfferCreator.class).info(String.format("Shunt in %s, %s", 
						entity.getContainingLocation().getNameWithLowercaseType(), 
						entity.getConstellation().getName()));
			}
		}
	}
}
