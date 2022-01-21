package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.StringHelper;

public class AbandonedStation extends BaseLandmarkDef {
	
	public static int count = 0;	// just to make sure it has a unique ID
	
	@Override
	public int getCount() {
		if (Global.getSector().getEconomy().getMarketsCopy().size() > 50)
			return 3;
		return 2;
	}
	
	@Override
	public boolean isApplicableToEntity(SectorEntityToken entity) {
		if (entity instanceof PlanetAPI) {
			PlanetAPI planet = (PlanetAPI)entity;
			if (planet.isStar()) {
				return false;
			}
		}
		if (entity.getMarket() == null || entity.getMarket().isPlanetConditionMarketOnly())
			return false;
		
		return true;
	}
	
	@Override
	public void createAt(SectorEntityToken entity)
	{
		LocationAPI system = entity.getContainingLocation();
		float orbitRadius = entity.getRadius() + 200;
		if (entity instanceof PlanetAPI) {
			PlanetAPI planet = (PlanetAPI)entity;
			if (planet.isStar()) {
				orbitRadius += entity.getRadius();
			}
		}
		
		float orbitPeriod = NexUtilsAstro.getOrbitalPeriod(entity, orbitRadius);
		SectorEntityToken neutralStation = system.addCustomEntity("nex_abandoned_station_" + count, 
																	StringHelper.getString("exerelin_landmarks", "abandonedStation"),
																	"station_side06", Factions.NEUTRAL);
		neutralStation.setCircularOrbitPointingDown(entity, NexUtilsAstro.getRandomAngle(random),
				orbitRadius, orbitPeriod);
			
		Misc.setAbandonedStationMarket("nex_abandoned_station_market_" + count, neutralStation);

		neutralStation.setCustomDescriptionId("station_abandoned_mining");
		neutralStation.setInteractionImage("illustrations", "abandoned_station2");
				
		log.info("Spawning abandoned station around " + entity.getName() + ", " + entity.getContainingLocation().getName());
		
		count++;
	}
	
	@Override
	protected boolean weighByMarketSize() {
		return true;
	}
}
