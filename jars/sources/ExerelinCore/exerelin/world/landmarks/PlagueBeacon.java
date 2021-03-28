package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.NexUtilsAstro;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlagueBeacon extends BaseLandmarkDef {
	
	protected static final Set<String> ALLOWED_CONDITIONS = new HashSet<>(Arrays.asList(new String[]{
		Conditions.DECIVILIZED, Conditions.RUINS_SCATTERED, Conditions.RUINS_EXTENSIVE, 
		Conditions.RUINS_WIDESPREAD, Conditions.RUINS_VAST, "US_virus"
	}));
	
	@Override
	public boolean isApplicableToEntity(SectorEntityToken entity)
	{
		MarketAPI market = entity.getMarket();
		if (market == null) 
			return false;
		
		if (!market.isPlanetConditionMarketOnly())
			return false;
		
		for (String cond : ALLOWED_CONDITIONS)
			if (market.hasCondition(cond)) return true;
		
		return false;
	}
	
	@Override
	public List<SectorEntityToken> getRandomLocations() {
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
		
		for (SectorEntityToken token : getEligibleLocations())
		{
			float weight = 1;
			if (token.getMarket().hasCondition("US_virus")) {
				log.info("Virus found on " + token.getName() + ", " + token.getContainingLocation().getName());
				weight = 15;
			}
			
			picker.add(token, weight);
		}
		
		List<SectorEntityToken> results = new ArrayList<>();
		int count = getCount();
		for (int i=0; i<count; i++)
		{
			if (picker.isEmpty()) break;
			results.add(picker.pickAndRemove());
		}
		
		return results;
	}
	
	@Override
	public int getCount() {
		int starSystemCount = Global.getSector().getStarSystems().size();
		return (int)Math.ceil(starSystemCount *= 0.02f);
	}
		
	@Override
	public void createAt(SectorEntityToken entity)
	{
		FactionAPI faction = entity.getFaction();
		
		CustomCampaignEntityAPI beacon = entity.getContainingLocation().addCustomEntity(null, null, Entities.WARNING_BEACON, faction.getId());
		float orbitRadius = entity.getRadius() + 100;
		float orbitPeriod = NexUtilsAstro.getOrbitalPeriod(entity, orbitRadius);
		beacon.setCircularOrbitWithSpin(entity, NexUtilsAstro.getRandomAngle(), orbitRadius, orbitPeriod, 20, 30);
		beacon.getMemoryWithoutUpdate().set("$nex_plagueBeacon", true);
		beacon.setDiscoverable(true);
		
		Misc.setWarningBeaconGlowColor(beacon, Color.GREEN);
		Misc.setWarningBeaconPingColor(beacon, Color.GREEN);
		
		log.info("Spawning plague beacon around " + entity.getName() + ", " + entity.getContainingLocation().getName());
	}
	
	@Override
	protected boolean weighByMarketSize() {
		return false;
	}
	
	@Override
	protected boolean isProcgenOnly() {
		return true;
	}
}
