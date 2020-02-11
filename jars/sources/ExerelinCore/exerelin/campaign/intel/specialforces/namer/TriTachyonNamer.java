package exerelin.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.ExerelinConstants;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Names fleets after mathematicians and astronomers.
 */
public class TriTachyonNamer implements SpecialForcesNamer {
	
	public static final String FILE_PATH = "data/config/exerelin/specialForcesNames.json";
	
	public static final List<String> NAMES = new ArrayList<>();
	
	static {
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(FILE_PATH, ExerelinConstants.MOD_ID);
			JSONArray names = json.getJSONArray("tt_names");
			NAMES.addAll(ExerelinUtils.JSONArrayToArrayList(names));
		}
		catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load Tri-Tachyon special forces namer", ex);
		}
	}

	@Override
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
		String name = ExerelinUtils.getRandomListElement(NAMES);
		//return StringHelper.getString("fleet", true) + " " + name;
		return name;
	}
}
