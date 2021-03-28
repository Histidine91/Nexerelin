package exerelin.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexUtils;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Creates names like "Sword of Justice" or "Gauntlet of Charity".
 */
public class LuddicNamer implements SpecialForcesNamer {
	
	public static final String FILE_PATH = "data/config/exerelin/specialForcesNames.json";
	
	public static final List<String> NAMES_FIRST = new ArrayList<>();
	public static final List<String> NAMES_SECOND = new ArrayList<>();
	
	static {
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(FILE_PATH, ExerelinConstants.MOD_ID);
			JSONArray names1 = json.getJSONArray("luddic_names1");
			JSONArray names2 = json.getJSONArray("luddic_names2");
			NAMES_FIRST.addAll(NexUtils.JSONArrayToArrayList(names1));
			NAMES_SECOND.addAll(NexUtils.JSONArrayToArrayList(names2));
		}
		catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load Luddic special forces namer", ex);
		}
	}

	@Override
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
		String one = NexUtils.getRandomListElement(NAMES_FIRST);
		String two = NexUtils.getRandomListElement(NAMES_SECOND);
		return one + " " + StringHelper.getString("of") + " " + two;
	}
}
