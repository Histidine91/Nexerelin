package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.lang.reflect.Field;
import java.util.ArrayList;


public final class ExerelinHacks
{
	@SuppressWarnings("rawtypes")
	public static ArrayList getSpawnPoints (StarSystemAPI system)
	{
		try
		{
			Class c = Class.forName("com.fs.starfarer.campaign.BaseLocation");
			Field f = c.getDeclaredField("spawnPoints");
			f.setAccessible(true);
			ArrayList spawnPoints = (ArrayList)f.get(system);

			return spawnPoints;
		}
		catch (Exception e)
		{
			e.printStackTrace();

			return null;
		}
	}
}
