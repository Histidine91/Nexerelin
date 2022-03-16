package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.DiplomacyManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMath;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class DiplomacyTraits {
	
	public static final String DEF_PATH = "data/config/exerelin/factionTraits.json";
	public static final String MEM_KEY_RANDOM_TRAITS = "$nex_random_diplomacy_traits";
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
			boolean noRandom = entryJson.optBoolean("noRandom", false);
			JSONArray colorJson = entryJson.optJSONArray("color");
			int[] color = null;
			if (colorJson != null) {
				color = new int[] {colorJson.getInt(0), colorJson.getInt(1), colorJson.getInt(2)};
			}
			
			TraitDef trait = new TraitDef(id, name, desc, icon, color, noRandom);
			
			if (entryJson.has("incompatible")) {
				JSONArray incompatJson = entryJson.getJSONArray("incompatible");
				trait.incompatibilities.addAll(NexUtils.JSONArrayToArrayList(incompatJson));
			}
			
			TRAITS.add(trait);
			TRAITS_BY_ID.put(id, trait);
		}
	}
	
	/**
	 * Generates a random set of diplomacy traits for a faction, without conflicting traits 
	 * (e.g.it won't have Likes AI and Dislikes AI at the same time).
	 * @param random
	 * @return
	 */
	public static List<String> generateRandomTraits(Random random) 
	{
		if (random == null) random = new Random();
		
		List<String> picks = new ArrayList<>();
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
		for (TraitDef trait : TRAITS) {
			if (trait.noRandom) continue;
			picker.add(trait.id);
		}
		int count = 3 + NexUtilsMath.randomNextIntInclusive(random, 3);
		for (int i=0; i<count; i++) {
			picks.add(picker.pickAndRemove());
			if (picker.isEmpty()) break;
		}
		
		// remove any traits that conflict with each other
		// traits picked earlier get priority
		Iterator<String> iter = picks.iterator();
		Set<String> toRemove = new HashSet<>();
		while (iter.hasNext()) 
		{
			String trait = iter.next();
			// Did an earlier trait specify that this one should be removed?
			if (toRemove.contains(trait)) {
				iter.remove();
				continue;
			}
			
			TraitDef def = getTrait(trait);
			toRemove.addAll(def.incompatibilities);
		}
		
		return picks;
	}
	
	public static List<String> getFactionTraits(String factionId) {
		if (NexConfig.allowRandomDiplomacyTraits && DiplomacyManager.getManager().getStartRelationsMode().isRandom()
				&& NexConfig.getFactionConfig(factionId).allowRandomDiplomacyTraits) 
		{
			MemoryAPI mem = Global.getSector().getFaction(factionId).getMemoryWithoutUpdate();
			if (!mem.contains(MEM_KEY_RANDOM_TRAITS)) {
				mem.set(MEM_KEY_RANDOM_TRAITS, generateRandomTraits(null));
			}
			return (List<String>)mem.get(MEM_KEY_RANDOM_TRAITS);
		}
		
		List<String> traits = new ArrayList<>(NexConfig.getFactionConfig(factionId).diplomacyTraits);
		
		return traits;
	}
	
	public static boolean hasTrait(String factionId, String trait) {
		return getFactionTraits(factionId).contains(trait);
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
		public final boolean noRandom;
		public final Set<String> incompatibilities = new HashSet<>();
		
		public TraitDef(String id, String name, String desc, String icon, int[] color, boolean noRandom) 
		{
			this.id = id;
			this.name = name;
			this.desc = desc;
			this.icon = icon;
			if (color != null)
				this.color = new Color(color[0], color[1], color[2]);
			else
				this.color = Color.WHITE;
			this.noRandom = noRandom;
		}
	}
	
	public static class TraitIds {
		public static final String PARANOID			= "paranoid";
		public static final String PACIFIST			= "pacifist";
		public static final String HELPS_ALLIES		= "helps_allies";
		public static final String IRREDENTIST		= "irredentist";
		public static final String STALWART			= "stalwart";
		public static final String WEAK_WILLED		= "weak-willed";
		public static final String FOREVERWAR		= "foreverwar";
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
		public static final String LOWPROFILE		= "lowprofile";
		public static final String DEVIOUS			= "devious";
		public static final String MONSTROUS		= "monstrous";
		public static final String PREDATORY		= "predatory";
	}
}
