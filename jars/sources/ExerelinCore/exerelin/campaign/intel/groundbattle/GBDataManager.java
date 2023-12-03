package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import exerelin.ExerelinConstants;
import exerelin.campaign.intel.groundbattle.plugins.GroundUnitPlugin;
import exerelin.utilities.NexUtils;
import lombok.Getter;
import lombok.extern.log4j.Log4j;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.util.List;
import java.util.*;

@Log4j
public class GBDataManager {
	
	public static final String CONFIG_PATH = "data/config/exerelin/groundBattleDefs.json";
	public static final List<String> NATO_ALPHABET = new ArrayList<>();
	
	@Getter	protected static List<IndustryDef> industryDefs = new ArrayList<>();
	@Getter	protected static Map<String, IndustryDef> industryDefsById = new HashMap<>();

	@Getter	protected static List<ConditionDef> conditionDefs = new ArrayList<>();
	@Getter	protected static Map<String, ConditionDef> conditionDefsById = new HashMap<>();

	@Getter	protected static List<AbilityDef> abilityDefs = new ArrayList<>();
	@Getter	protected static Map<String, AbilityDef> abilityDefsById = new HashMap<>();

	@Getter	protected static List<String> plugins = new ArrayList<>();
	@Getter	protected static Map<String, String> pluginsById = new HashMap<>();
	
	static {
		loadDefs();
	}
	
	// runcode exerelin.campaign.intel.groundbattle.GBDataManager.loadDefs();
	public static void loadDefs() {
		industryDefs.clear();
		industryDefsById.clear();
		conditionDefs.clear();
		conditionDefsById.clear();
		abilityDefs.clear();
		abilityDefsById.clear();
		GroundUnitDef.UNIT_DEFS.clear();
		GroundUnitDef.UNIT_DEFS_BY_ID.clear();
		
		IndustryDef defaultDef = new IndustryDef("default");
		industryDefs.add(defaultDef);
		industryDefsById.put("default", defaultDef);
		
		try {
			JSONObject json = Global.getSettings().getMergedJSONForMod(CONFIG_PATH, ExerelinConstants.MOD_ID);

			// load industries
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
				
				industryDefs.add(def);
				industryDefsById.put(indId, def);
			}

			// load conditions
			JSONObject jsonCond = json.getJSONObject("conditions");
			iter = jsonCond.keys();
			while (iter.hasNext()) {
				String condId = (String)iter.next();
				JSONObject jsonCondEntry = jsonCond.getJSONObject(condId);
				
				ConditionDef def = new ConditionDef(condId);
				if (jsonCondEntry.has("tags")) {
					def.tags.addAll(NexUtils.JSONArrayToArrayList(jsonCondEntry.getJSONArray("tags")));
				}
				def.plugin = jsonCondEntry.optString("plugin", null);
				def.desc = jsonCondEntry.optString("desc", "");
				if (jsonCondEntry.has("highlights"))
					def.highlights = NexUtils.JSONArrayToArrayList(jsonCondEntry.getJSONArray("highlights"));
				if (jsonCondEntry.has("color"))
					def.color = NexUtils.JSONArrayToColor(jsonCondEntry.getJSONArray("color"));
				
				conditionDefs.add(def);
				conditionDefsById.put(condId, def);
			}

			// load abilities
			JSONObject jsonAbility = json.getJSONObject("abilities");
			iter = jsonAbility.keys();
			while (iter.hasNext()) {
				String id = (String)iter.next();
				JSONObject jsonAbilityEntry = jsonAbility.getJSONObject(id);
				
				AbilityDef def = new AbilityDef(id);
				
				def.name = jsonAbilityEntry.getString("name");
				def.icon = jsonAbilityEntry.optString("icon", null);
				def.plugin = jsonAbilityEntry.getString("plugin");
				def.cooldown = jsonAbilityEntry.optInt("cooldown");
				def.cooldownGlobal = jsonAbilityEntry.optInt("cooldownGlobal");
				def.sound = jsonAbilityEntry.optString("sound", null);
				def.illustration = jsonAbilityEntry.optString("illustration", null);
				def.order = jsonAbilityEntry.optInt("order", 100);
				if (jsonAbilityEntry.has("color"))
					def.color = NexUtils.JSONArrayToColor(jsonAbilityEntry.getJSONArray("color"));
				
				abilityDefs.add(def);
				abilityDefsById.put(id, def);
			}
			Collections.sort(abilityDefs);

			// load units
			JSONObject jsonUnit = json.getJSONObject("unitTypes");
			iter = jsonUnit.keys();
			while (iter.hasNext()) {
				String id = (String) iter.next();
				JSONObject jsonUnitEntry = jsonUnit.getJSONObject(id);
				String name = jsonUnitEntry.getString("name");
				GroundUnit.ForceType type = GroundUnit.ForceType.valueOf(jsonUnitEntry.getString("type"));
				GroundUnitDef def = new GroundUnitDef(id, name, type);
				def.playerCanCreate = jsonUnitEntry.optBoolean("playerCanCreate", false);
				def.strength = (float)jsonUnitEntry.optDouble("strength", 1);
				def.pluginClass = jsonUnitEntry.optString("pluginClass", GroundUnitPlugin.class.getName());
				def.playerMemKeyToShow = jsonUnitEntry.optString("playerMemKeyToShow", null);
				def.unitSizeMult = (float)jsonUnitEntry.optDouble("unitSizeMult", 1);
				def.dropCostMult = (float)jsonUnitEntry.optDouble("dropCostMult", 1);
				def.offensiveStrMult = (float)jsonUnitEntry.optDouble("offensiveStrMult", 1);
				def.damageTakenMult = (float)jsonUnitEntry.optDouble("damageTakenMult", 1);
				def.moraleMult = (float)jsonUnitEntry.optDouble("moraleMult", 1);
				def.crampedStrMult = (float)jsonUnitEntry.optDouble("crampedStrMult", 1);
				def.sprite = jsonUnitEntry.optString("sprite", null);
				def.sortOrder = (float)jsonUnitEntry.optDouble("sortOrder", 10);
				if (jsonUnitEntry.has("personnel")) {
					JSONObject jper = jsonUnitEntry.getJSONObject("personnel");
					def.personnel = new GroundUnitDef.GroundUnitCommodity(jper.getString("commodityId"),
							jper.getString("crewReplacerJobId"), jper.optInt("mult", 1));
				}
				if (jsonUnitEntry.has("equipment")) {
					JSONObject jeqp = jsonUnitEntry.getJSONObject("equipment");
					def.equipment = new GroundUnitDef.GroundUnitCommodity(jeqp.getString("commodityId"),
							jeqp.getString("crewReplacerJobId"), jeqp.optInt("mult", 1));
				}
				if (jsonUnitEntry.has("tags")) {
					def.tags.addAll(NexUtils.JSONArrayToArrayList(jsonUnitEntry.getJSONArray("tags")));
				}

				GroundUnitDef.addUnitDef(def);
			}

			// load plugins
			JSONObject jsonPlugin = json.getJSONObject("plugins");
			iter = jsonPlugin.keys();
			while (iter.hasNext()) {
				String id = (String) iter.next();
				String plugin = jsonPlugin.getString(id);
				plugins.add(plugin);
				pluginsById.put(id, plugin);
			}
			
			JSONArray jsonAlphabet = json.getJSONArray("natoAlphabet");
			NATO_ALPHABET.addAll(NexUtils.JSONArrayToArrayList(jsonAlphabet));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load ground battle defs", ex);
			//Global.getLogger(GBDataManager.class).error(ex);
		}
	}	
	
	public static List<IndustryDef> getIndustryDefs() {
		return industryDefs;
	}
	
	public static IndustryDef getIndustryDef(String id) {
		if (!industryDefsById.containsKey(id)) return industryDefsById.get("default");
		return industryDefsById.get(id);
	}
	
	public static List<ConditionDef> getConditionDefs() {
		return conditionDefs;
	}
	
	public static ConditionDef getConditionDef(String id) {
		return conditionDefsById.get(id);
	}
	
	public static List<AbilityDef> getAbilityDefs() {
		return abilityDefs;
	}
	
	public static AbilityDef getAbilityDef(String id) {
		return abilityDefsById.get(id);
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
	
		public boolean hasTag(String tag) {
			return tags.contains(tag);
		}
	}
	
	public static class ConditionDef {
		public final String conditionId;
		public String plugin;
		public Set<String> tags = new HashSet<>();
		public String desc;
		public Color color;	// not set yet
		public List<String> highlights;
		public List<Color> highlightColors;	// not set yet
		
		public ConditionDef(String conditionId) {
			this.conditionId = conditionId;
		}
	}
	
	public static class AbilityDef implements Comparable<AbilityDef> {
		public final String id;
		public String name;
		public String icon;
		public Color color;	// not set yet
		public String plugin;
		public int cooldown;
		public int cooldownGlobal;
		public String sound;
		public String illustration;
		public int order;
		
		public AbilityDef(String id) {
			this.id = id;
		}

		@Override
		public int compareTo(AbilityDef other) {
			return Integer.compare(this.order, other.order);
		}
	}
}
