package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.ExerelinUtilsAstro;
import exerelin.utilities.StringHelper;
import java.util.Random;

public class AbandonedStation extends BaseLandmarkDef {
	
	public static final boolean WEIGH_BY_MARKET_SIZE = false;
	public static int count = 0;	// just to make sure it has a unique ID

	public AbandonedStation(Random random) {
		super(random);
	}
	
	@Override
	public int getCount() {
		if (Global.getSector().getEconomy().getMarketsCopy().size() > 75)
			return 2;
		return 1;
	}
	
	@Override
	public void createAt(SectorEntityToken entity)
	{
		LocationAPI system = entity.getContainingLocation();
		float orbitRadius = entity.getRadius() + 200;
		float orbitPeriod = ExerelinUtilsAstro.getOrbitalPeriod(entity, orbitRadius);
		SectorEntityToken neutralStation = system.addOrbitalStation("nex_abandoned_station_" + count, 
																	entity,
																	ExerelinUtilsAstro.getRandomAngle(random),
																	orbitRadius, 
																	orbitPeriod,
																	StringHelper.getString("exerelin_landmarks", "abandonedStation"),
																	"neutral");
		
		// Hey it should orbit facing down and stuff.
		neutralStation.setCircularOrbitPointingDown(entity, 45, 300, 30);
			
		Misc.setAbandonedStationMarket("nex_abandoned_station_market_" + count, neutralStation);

		neutralStation.setCustomDescriptionId("station_abandoned_mining");
		neutralStation.setInteractionImage("illustrations", "abandoned_station2");
				
		log.info("Spawning abandoned station around " + entity.getName() + ", " + entity.getContainingLocation().getName());
		count++;
	}
}
