package exerelin.world;

import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.utilities.ExerelinConfig;
import exerelin.world.landmarks.BeholderStation;
import exerelin.world.landmarks.LandmarkDef;
import exerelin.world.landmarks.MuseumShip;
import exerelin.world.landmarks.PlagueBeacon;
import java.util.HashMap;
import java.util.Map;

// Creates random mode landmarks
// Can create stuff in non-random mode too
public class LandmarkGenerator {
	
	public static final Map<String, LandmarkDef> landmarkDefs = new HashMap<>();
	
	// TODO
	static {
		landmarkDefs.put(MuseumShip.id, new MuseumShip());
		landmarkDefs.put(PlagueBeacon.id, new PlagueBeacon());
		landmarkDefs.put(BeholderStation.id, new BeholderStation());
	}
	
	public void generate(SectorAPI sector, boolean corvusMode)
	{
		if (!corvusMode || ExerelinConfig.corvusModeLandmarks)
		{
			landmarkDefs.get(MuseumShip.id).createAll();
			landmarkDefs.get(PlagueBeacon.id).createAll();
		}
		
		if (!corvusMode)
			landmarkDefs.get(BeholderStation.id).createAll();
		
		// old debug spawner
		/*
		SectorEntityToken jangala = Global.getSector().getEntityById("jangala");
		landmarkDefs.get(MuseumShip.id).createAt(jangala);
		landmarkDefs.get(PlagueBeacon.id).createAt(jangala);
		landmarkDefs.get(BeholderStation.id).createAt(jangala);
		*/
	}
}
