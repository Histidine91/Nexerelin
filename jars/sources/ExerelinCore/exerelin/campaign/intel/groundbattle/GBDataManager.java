package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public class GBDataManager {
	
	public static final String CONFIG_PATH = "data/config/exerelin/groundBattleDefs.json";
	public static final List<String> NATO_ALPHABET = new ArrayList<>();
	
	protected static List<IndustryDef> defs = new ArrayList<>();
	protected static Map<String, IndustryDef> defsById = new HashMap<>();
	
	static {
		loadDefs();
	}
	
	protected static void loadDefs() {
		IndustryDef defaultDef = new IndustryDef("default");
		defs.add(defaultDef);
		defsById.put("default", defaultDef);
		
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(CONFIG_PATH, ExerelinConstants.MOD_ID);
			JSONObject jsonInd = json.getJSONObject("industries");
			Iterator iter = jsonInd.keys();
			while (iter.hasNext()) {
				String indId = (String)iter.next();
				JSONObject jsonIndEntry = jsonInd.getJSONObject(indId);
				
				IndustryDef def = new IndustryDef(indId);
				def.strengthMult = (float)jsonIndEntry.optDouble("strengthMult", 1);
				def.enemyDropCostMult = (float)jsonIndEntry.optDouble("enemyDropCostMult", 1);
				def.dropAttritionFactor = (float)jsonIndEntry.optDouble("dropAttritionFactor", 0);
				def.dropAttritionMult = (float)jsonIndEntry.optDouble("dropAttritionMult", 1);
				def.enemyBombardmentCostMult = (float)jsonIndEntry.optDouble("enemyBombardmentCostMult", 1);
				if (jsonIndEntry.has("troopCounts")) {
					JSONObject jsonTroopCounts = jsonIndEntry.getJSONObject("troopCounts");
					Iterator iter2 = jsonTroopCounts.keys();
					while (iter2.hasNext()) {
						String troopId = (String)iter2.next();
						def.troopCounts.put(troopId, (float)jsonTroopCounts.getDouble(troopId));
					}
				}
				if (jsonIndEntry.has("tags")) {
					def.tags.addAll(NexUtils.JSONArrayToArrayList(jsonIndEntry.getJSONArray("tags")));
				}
				def.icon = jsonIndEntry.optString("icon", null);
				def.plugin = jsonIndEntry.optString("plugin", null);
				
				defs.add(def);
				defsById.put(indId, def);
			}
			
			JSONArray jsonAlphabet = json.getJSONArray("natoAlphabet");
			NATO_ALPHABET.addAll(NexUtils.JSONArrayToArrayList(jsonAlphabet));			
		} catch (Exception ex) {
			Global.getLogger(GBDataManager.class).error(ex);
		}
	}
	
	public static IndustryDef getDef(String id) {
		if (!defsById.containsKey(id)) return defsById.get("default");
		return defsById.get(id);
	}
	
	
	public static class IndustryDef {
		public final String industryId;
		
		public float strengthMult = 1;
		public float enemyDropCostMult = 1;
		public float enemyBombardmentCostMult = 1;
		public float dropAttritionFactor = 0;
		public float dropAttritionMult = 1;
		public Set<String> tags = new HashSet<>();
		public Map<String, Float> troopCounts = new HashMap<>();
		public String icon;
		public String plugin;
				
		public IndustryDef(String industryId) {
			this.industryId = industryId;
		}
	}
}
