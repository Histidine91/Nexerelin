package exerelin.campaign.intel.specialforces.namer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import java.io.IOException;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

/**
 * Names the fleet for a randomly picked planet belonging to that faction, e.g. "7th Fikenhild".
 */
public class PlanetNamer implements SpecialForcesNamer {
	
	public static final String FILE_PATH = "data/config/exerelin/specialForcesNames.json";
	
	public static String st;
	public static String nd;
	public static String rd;
	public static String th;
	
	static {
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(FILE_PATH, ExerelinConstants.MOD_ID);
			JSONObject suffixes = json.getJSONObject("numSuffixes");
			st = suffixes.getString("st");
			nd = suffixes.getString("nd");
			rd = suffixes.getString("rd");
			th = suffixes.getString("th");
		}
		catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load planet-based special forces namer", ex);
		}
	}

	@Override
	public String getFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander) {
		int num = MathUtils.getRandomNumberInRange(1, 33);
		String suffix = getSuffix(num);
		
		MarketAPI market = getMarket(fleet.getFaction());
		if (market == null) market = origin;
		
		return num + suffix + " " + market.getName();
	}
	
	protected MarketAPI getMarket(FactionAPI faction) {
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		for (MarketAPI market : Misc.getFactionMarkets(faction)) {
			if (market.isHidden()) continue;
			if (market.getPlanetEntity() == null) continue;
			picker.add(market, market.getSize());
		}
		return picker.pick();
	}
	
	public static String getSuffix(int num) {
		if (num == 11 || num == 12 || num == 13)
			return th;
		
		int lastDigit = num % 10;
		switch (lastDigit) {
			case 1:
				return st;
			case 2:
				return nd;
			case 3:
				return rd;
			default:
				return th;
		}
	}
}
