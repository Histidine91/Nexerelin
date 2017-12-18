package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.log4j.Logger;

public class BaseLandmarkDef extends LandmarkDef {
	
	protected static Logger log = Global.getLogger(BaseLandmarkDef.class);
	
	protected static final boolean WEIGH_BY_MARKET_SIZE = true;
	protected static final boolean PROCGEN_SYSTEMS_ONLY = false;
		
	@Override
	public boolean isApplicableToEntity(SectorEntityToken entity)
	{
		return true;
	}
		
	@Override
	public List<SectorEntityToken> getRandomLocations() {
		WeightedRandomPicker<SectorEntityToken> picker = new WeightedRandomPicker<>(random);
		for (StarSystemAPI system : Global.getSector().getStarSystems())
		{
			if (PROCGEN_SYSTEMS_ONLY && !system.isProcgen())
				continue;
			for (PlanetAPI planet : system.getPlanets())
			{
				if (!isApplicableToEntity(planet)) continue;
				float weight = 1;
				if (WEIGH_BY_MARKET_SIZE && planet.getMarket() != null)
					weight = planet.getMarket().getSize();
				picker.add(planet, weight);
			}
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
}
