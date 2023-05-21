package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BeholderStation extends BaseLandmarkDef {
	
	@Override
	public List<SectorEntityToken> getEligibleLocations() {
		List<SectorEntityToken> results = new ArrayList<>();
		Set<StarSystemAPI> luddicSystems = new HashSet<>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			if (NexUtilsFaction.isLuddicFaction(market.getFactionId()))
				luddicSystems.add(market.getStarSystem());
		}
		
		for (StarSystemAPI system : luddicSystems)
		{
			for (PlanetAPI planet : system.getPlanets())
			{
				if (!planet.isGasGiant()) continue;
				results.add(planet);
			}
		}
		return results;
	}
	
	@Override
	protected boolean weighByMarketSize() {
		return false;
	}
		
	@Override
	public void createAt(SectorEntityToken entity)
	{
		float orbitRadius = entity.getRadius() + 200;
		float orbitPeriod = NexUtilsAstro.getOrbitalPeriod(entity, orbitRadius);
		SectorEntityToken beholder_station = entity.getContainingLocation().addCustomEntity("beholder_station", 
				StringHelper.getString("exerelin_landmarks", "beholderStation"), "station_side05", Factions.LUDDIC_CHURCH);
		beholder_station.setCircularOrbitPointingDown(entity, NexUtilsAstro.getRandomAngle(random), orbitRadius, orbitPeriod);		
		beholder_station.setCustomDescriptionId("station_beholder");
		beholder_station.setInteractionImage("illustrations", "luddic_shrine");
		beholder_station.addTag(Tags.LUDDIC_SHRINE);
		
		log.info("Spawning Beholder Station around " + entity.getName() + ", " + entity.getContainingLocation().getName());
	}
}
