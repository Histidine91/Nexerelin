package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DiplomacyTraits {
	
	public static final String DEF_PATH = "data/config/exerelin/factionTraits.json";
	protected static final List<TraitDef> TRAITS = new ArrayList<>();
	protected static final Map<String, TraitDef> TRAITS_BY_ID = new HashMap<>();
	
	static {
		try {
			loadTraits();
		} catch (IOException | JSONException ex) {
			throw new RuntimeException(ex);	// causes NoClassDefFoundError
		}
	}
	
	protected static void loadTraits() throws IOException, JSONException {
		JSONArray defJson = Global.getSettings().getMergedJSONForMod(DEF_PATH, ExerelinConstants.MOD_ID)
				.getJSONArray("traits");
		for (int i=0; i<defJson.length(); i++) {
			JSONObject entryJson = defJson.getJSONObject(i);
			String id = entryJson.getString("id");
			String name = entryJson.getString("name");
			String desc = entryJson.getString("desc");
			String icon = entryJson.optString("icon");
			JSONArray colorJson = entryJson.optJSONArray("color");
			int[] color = null;
			if (colorJson != null) {
				color = new int[] {colorJson.getInt(0), colorJson.getInt(1), colorJson.getInt(2)};
			}
			TraitDef trait = new TraitDef(id, name, desc, icon, color);
			TRAITS.add(trait);
			TRAITS_BY_ID.put(id, trait);
		}
	}
	
	public static List<TraitDef> getTraits() {
		return TRAITS;
	}
	
	public static TraitDef getTrait(String id) {
		return TRAITS_BY_ID.get(id);
	}
	
	public static class TraitDef {
		public final String id;
		public final String name;
		public final String desc;
		public final String icon;
		public final Color color;
		
		public TraitDef(String id, String name, String desc, String icon, int[] color) {
			this.id = id;
			this.name = name;
			this.desc = desc;
			this.icon = icon;
			if (color != null)
				this.color = new Color(color[0], color[1], color[2]);
			else
				this.color = Color.WHITE;
		}
	}
	
	public static class TraitIds {
		public static final String PARANOID			= "paranoid";
		public static final String PACIFIST			= "pacifist";
		public static final String HELPS_ALLIES		= "helps_allies";
		public static final String IRREDENTIST		= "irredentist";
		public static final String STALWART			= "stalwart";
		public static final String WEAK_WILLED		= "weak-willed";
		public static final String SELFRIGHTEOUS	= "selfrighteous";
		public static final String TEMPERAMENTAL	= "temperamental";
		public static final String DISLIKES_AI		= "dislikes_ai";
		public static final String HATES_AI			= "hates_ai";
		public static final String LIKES_AI			= "likes_ai";
		public static final String ENVIOUS			= "envious";
		public static final String SUBMISSIVE		= "submissive";
		public static final String NEUTRALIST		= "neutralist";
		public static final String MONOPOLIST		= "monopolist";
		public static final String LAW_AND_ORDER	= "law_and_order";
		public static final String ANARCHIST		= "anarchist";

	}
}
