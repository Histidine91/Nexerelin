package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.NexUtilsMarket;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class BaseLandmarkDef extends LandmarkDef {
	
	protected static Logger log = Global.getLogger(BaseLandmarkDef.class);
		
	@Override
	public boolean isApplicableToEntity(SectorEntityToken entity)
	{
		return true;
	}
	
	/**
	 * Gets a list of all {@code SectorEntityToken}s that can have this landmark.
	 * @return
	 */
	public List<SectorEntityToken> getEligibleLocations() {
		List<SectorEntityToken> results = new ArrayList<>();
		for (StarSystemAPI system : Global.getSector().getStarSystems())
		{
			if (isProcgenOnly() && !system.isProcgen())
				continue;
			for (PlanetAPI planet : system.getPlanets())
			{
				if (!isApplicableToEntity(planet)) continue;
				results.add(planet);
			}
		}
		
		return results;
	}
	
	/**
	 * Gets a list of randomly picked {@code SectorEntityToken}s 
	 * where the landmark will actually spawn.
	 * This will usually be a subset of the results of {@code getEligibleLocations()}.
	 * @return
	 */
	@Override
	public List<SectorEntityToken> getRandomLocations() {
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
		
		for (SectorEntityToken token : getEligibleLocations())
		{
			float weight = 1;
			if (weighByMarketSize() && token.getMarket() != null)
				weight = token.getMarket().getSize();
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
	public boolean isFreeFloating()
	{
		return false;
	}
	
	@Override
	public void createAt(SectorEntityToken entity)
	{
		
	}
	
	
	@Override
	public void createAll() {
		List<SectorEntityToken> locations = getRandomLocations();
		for (SectorEntityToken entity : locations)
			createAt(entity);
	}
	

	@Override
	public int getCount() {
		return 1;
	}
	
	@Override
	public Random getRandom() {
		return random;
	}
	
	@Override
	public void setRandom(Random random) {
		this.random = random;
	}
	
	protected boolean weighByMarketSize() {
		return true;
	}
	
	protected boolean isProcgenOnly() {
		return false;
	}
	
	public String getNonDerelictFaction(MarketAPI market) {
		String factionId = market.getFactionId();
		if (factionId.equals(Factions.DERELICT) || factionId.equals("nex_derelict"))
		{
			String origOwnerId = NexUtilsMarket.getOriginalOwner(market);
			if (origOwnerId != null) factionId = origOwnerId;
		}
		return factionId;
	}

	protected boolean isNearAnotherMarket(SectorEntityToken planet, Collection<MarketAPI> markets)
	{
		for (MarketAPI market : markets) {
			if (MathUtils.isWithinRange(planet, market.getPrimaryEntity(), 800)) {
				return true;
			}
		}
		return false;
	}
}
