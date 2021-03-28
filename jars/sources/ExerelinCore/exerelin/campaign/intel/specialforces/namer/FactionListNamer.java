package exerelin.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexUtils;
import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Picks a name from a list of strings. 
 * To use, specify a list named "factionNames_[factionId]" in specialForcesNames.json.
 */
public class FactionListNamer implements SpecialForcesNamer {
	
	public static final String FILE_PATH = "data/config/exerelin/specialForcesNames.json";
	
	@Override
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
		String name = "<error>";
		
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(FILE_PATH, ExerelinConstants.MOD_ID);
			JSONArray names = json.getJSONArray("factionNames_" + fleet.getFaction().getId());
			name = NexUtils.getRandomListElement(NexUtils.JSONArrayToArrayList(names));
		}
		catch (IOException | JSONException ex) {
			Global.getLogger(this.getClass()).info("Failed to load special forces name list for faction " + fleet.getFaction().getId(), ex);
		}
		
		return name;
	}
}
