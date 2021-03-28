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

public class DiktatNamer implements SpecialForcesNamer {
	
	public static final String FILE_PATH = "data/config/exerelin/specialForcesNames.json";
	
	public static final List<String> NAMES_FIRST = new ArrayList<>();
	public static final List<String> NAMES_SECOND = new ArrayList<>();
	public static final List<String> PREPOSITIONS = new ArrayList<>();
	public static final String FORMAT;
	
	static {
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(FILE_PATH, ExerelinConstants.MOD_ID);
			JSONArray names1 = json.getJSONArray("diktat_names1");
			JSONArray names2 = json.getJSONArray("diktat_names2");
			JSONArray prepos = json.getJSONArray("diktat_prepositions");
			NAMES_FIRST.addAll(NexUtils.JSONArrayToArrayList(names1));
			NAMES_SECOND.addAll(NexUtils.JSONArrayToArrayList(names2));
			PREPOSITIONS.addAll(NexUtils.JSONArrayToArrayList(prepos));
			FORMAT = json.getString("diktat_nameFormat");
		}
		catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load Diktat special forces namer", ex);
		}
	}

	@Override
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
		String one = NexUtils.getRandomListElement(NAMES_FIRST);
		String two = NexUtils.getRandomListElement(NAMES_SECOND);
		String prepos = NexUtils.getRandomListElement(PREPOSITIONS);
		
		String name = StringHelper.substituteToken(FORMAT, "$one", one);
		name = StringHelper.substituteToken(name, "$preposition", prepos);
		name = StringHelper.substituteToken(name, "$two", two);
		
		return name;
	}
}
