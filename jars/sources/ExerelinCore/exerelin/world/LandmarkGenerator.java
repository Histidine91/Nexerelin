package exerelin.world;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.world.landmarks.LandmarkDef;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Creates random mode landmarks
// Can create stuff in non-random mode too
public class LandmarkGenerator {
	
	public static final String LANDMARK_DEFS_PATH = "data/config/exerelin/landmarks.csv";
	protected static final Map<String, LandmarkStoreDef> landmarkDefs = new HashMap<>();
	
	protected static Logger log = Global.getLogger(LandmarkGenerator.class);
	
	// Cribbed from Console Commands' CommandStore.java
	public static void loadLandmarks() throws IOException, JSONException
	{
		final JSONArray landmarkDefsJson = Global.getSettings().getMergedSpreadsheetDataForMod(
				"id", LANDMARK_DEFS_PATH, ExerelinConstants.MOD_ID);
		final ClassLoader loader = Global.getSettings().getScriptClassLoader();
		for (int x = 0; x < landmarkDefsJson.length(); x++)
		{
			// Defined here so we can use them in the catch block
			String landmarkId = null;
			String className = null;
			String landmarkSource = null;	// the mod it's from
			
			try
			{
				final JSONObject row = landmarkDefsJson.getJSONObject(x);
				landmarkId = row.getString("id");
				landmarkSource = row.getString("fs_rowSource");
				boolean randomOnly = row.optBoolean("randomOnly", false);
				
				// Skip empty rows
				if (landmarkId.isEmpty())
				{
					continue;
				}

				// Load these first so we can display them if there's an error
				className = row.getString("class");

				// Check if the class is valid
				final Class landmarkClass = loader.loadClass(className);
				if (!LandmarkDef.class.isAssignableFrom(landmarkClass))
				{
					log.error(landmarkClass.getCanonicalName()
							+ " does not implement " + LandmarkDef.class.getCanonicalName());
					continue;
				}
								
				// Built landmark info, register it in the master list
				landmarkDefs.put(landmarkId, new LandmarkStoreDef(landmarkId, landmarkClass, randomOnly));
				log.debug("Loaded landmark def " + landmarkId + " (class: " + landmarkClass.getCanonicalName() + ") from " + landmarkSource);
			}
			catch (Exception ex)
			{
				log.error("Failed to load command " + landmarkId
						+ " (class: " + className + ") from " + landmarkSource, ex);
			}
		}
	}
	
	static {
		try {
			loadLandmarks();
		}
		catch (IOException | JSONException ex)
		{
			log.error("Failed to load landmarks", ex);
		}
	}

	
	public void generate(SectorAPI sector, boolean corvusMode)
	{
		if (corvusMode && !NexConfig.corvusModeLandmarks)
			return;
		
		Random random = new Random(NexUtils.getStartingSeed());
		
		Iterator<String> iterLandmarks = landmarkDefs.keySet().iterator();
		while (iterLandmarks.hasNext())
		{
			LandmarkStoreDef def = null;
			try {
				String id = iterLandmarks.next();
				def = landmarkDefs.get(id);
				if (corvusMode && def.randomOnly)
					continue;
				LandmarkDef landmark = def.landmarkClass.newInstance();
				landmark.setRandom(random);
				landmark.createAll();				
			} catch (InstantiationException | IllegalAccessException ex)	{
				log.error("Failed to create landmark " + def.id, ex);
			}
		}
	}
	
	public static class LandmarkStoreDef {
		public String id = null;
		public Class<? extends LandmarkDef> landmarkClass = null;
		public boolean randomOnly = false;
		
		public LandmarkStoreDef(String id, Class landmarkClass, boolean randomOnly)
		{
			this.id = id;
			this.landmarkClass = landmarkClass;
			this.randomOnly = randomOnly;
		}
	}
}
