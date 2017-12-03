package exerelin.world;

import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.world.landmarks.BeholderStation;
import exerelin.world.landmarks.GraveyardWithMemorial;
import exerelin.world.landmarks.MuseumShip;
import exerelin.world.landmarks.PlagueBeacon;
import java.util.Random;

// Creates random mode landmarks
// Can create stuff in non-random mode too
public class LandmarkGenerator {
		
	public void generate(SectorAPI sector, boolean corvusMode)
	{
		Random random = new Random(ExerelinUtils.getStartingSeed());
		
		if (!corvusMode || ExerelinConfig.corvusModeLandmarks)
		{
			new MuseumShip(random).createAll();
			new PlagueBeacon(random).createAll();
			new GraveyardWithMemorial(random).createAll();
		}
		
		if (!corvusMode)
			new BeholderStation(random).createAll();
	}
}
