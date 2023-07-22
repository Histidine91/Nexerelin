package exerelin.campaign.intel.merc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class MercDataManager {
	
	public static final String DEF_PATH = "data/config/exerelin/mercConfig.json";
	
	public static Set<String> enabledFactions = new HashSet<>();
	public static Set<String> disabledFactions = new HashSet<>();
	
	public static Map<String, MercCompanyDef> companyDefs = new HashMap<>();
	
	public static int companiesForHire;
	public static float companiesForHireMult;
	public static float valueDifferencePaymentMult;
	public static float feeUpfrontMult;
	public static float feeMonthlyMult;
	public static float feeUpfrontRefundMult;
	
	static {
		loadDefs();
	}
	
	public static MercCompanyDef getDef(String id) {
		return companyDefs.get(id);
	}
	
	public static boolean hasRequiredMods(List<String> mods) {
		for (String mod : mods) {
			if (!Global.getSettings().getModManager().isModEnabled(mod)) {
				return false;
			}
		}
		return true;
	}
	
	public static List<List<String>> getShipList(JSONObject entryJson) throws JSONException 
	{
		List<List<String>> results = new ArrayList<>();
		if (!entryJson.has("ships")) {
			return results;
		}
		
		JSONArray shipsJson = entryJson.getJSONArray("ships");
		for (int i=0; i<shipsJson.length(); i++) {
			JSONArray rowJson = shipsJson.getJSONArray(i);
			List<String> row = NexUtils.JSONArrayToArrayList(rowJson);
			
			// validate ships
			List<String> toAdd = new ArrayList<>();
			for (String variantId : row) {
				if (Global.getSettings().doesVariantExist(variantId))
					toAdd.add(variantId);
			}
			results.add(toAdd);
		}
		return results;
	}
	
	public static List<OfficerDef> getOfficerList(JSONObject entryJson) throws JSONException
	{
		List<OfficerDef> results = new ArrayList<>();
		if (!entryJson.has("officers")) {
			return results;
		}
		
		JSONArray officersJson = entryJson.getJSONArray("officers");
		for (int i=0; i<officersJson.length(); i++) {
			JSONObject thisJson = officersJson.getJSONObject(i);
			
			OfficerDef def = new OfficerDef();
			def.firstName = thisJson.optString("firstName", null);
			def.lastName = thisJson.optString("lastName", null);
			if (thisJson.has("gender")) {
				String genderStr = thisJson.optString("gender", "n").toLowerCase();
				if (genderStr.equals("m")) {
					def.gender = Gender.MALE;
				} else if (genderStr.equals("f")) {
					def.gender = Gender.FEMALE;
				} else {
					def.gender = Gender.ANY;
				}
			}
			
			def.level = thisJson.optInt("level", 1);
			def.portrait = thisJson.optString("portrait", null);
			def.rankId = thisJson.optString("rankId", null);
			def.skillsReplace = thisJson.optBoolean("skillsReplace", true);
			def.voice = thisJson.optString("voice", null);
			def.personality = thisJson.optString("personality", null);
			def.persistentId = thisJson.optString("persistentId", null);
			def.aiCoreId = thisJson.optString("aiCoreId", null);
			def.chatterCharacterId = thisJson.optString("chatterCharacterId", null);
			
			if (thisJson.has("skills")) {
				def.skills = new HashMap<>();
				JSONObject skillsJson = thisJson.getJSONObject("skills");
				Iterator skillsIter = skillsJson.keys();
				while (skillsIter.hasNext()) {
					String skillId = (String)skillsIter.next();
					int level = skillsJson.getInt(skillId);
					def.skills.put(skillId, level);
				}
			}
				
			
			results.add(def);
		}
		return results;
	}
	
	// runcode exerelin.campaign.intel.merc.MercDataManager.loadDefs();
	public static void loadDefs() {
		companyDefs.clear();
		try {
			Global.getLogger(MercDataManager.class).info("Loading mercenary data");
			JSONObject jsonBase = Global.getSettings().getMergedJSONForMod(DEF_PATH, ExerelinConstants.MOD_ID);
			
			// constant stuff
			companiesForHire = jsonBase.optInt("companiesForHire", 3);
			companiesForHireMult = (float)jsonBase.optDouble("companiesForHireMult", 0.2);
			valueDifferencePaymentMult = (float)jsonBase.optDouble("valueDifferencePaymentMult", 0.8f);
			feeUpfrontMult = (float)jsonBase.optDouble("feeUpfrontMult", 1);
			feeMonthlyMult = (float)jsonBase.optDouble("feeMonthlyMult", 1);
			feeUpfrontRefundMult = (float)jsonBase.optDouble("feeUpfrontRefundMult", 1);
			
			List<String> enabled = NexUtils.JSONArrayToArrayList(jsonBase.getJSONArray("enabledFactions"));
			List<String> disabled = NexUtils.JSONArrayToArrayList(jsonBase.getJSONArray("disabledFactions"));
			
			enabledFactions.addAll(enabled);
			disabledFactions.addAll(disabled);
			
			// merc defs
			JSONObject companiesJson = jsonBase.getJSONObject("companies");
			Iterator iter = companiesJson.keys();
			while (iter.hasNext()) {
				String id = (String)iter.next();
				Global.getLogger(MercDataManager.class).info("  Loading merc company: " + id);
				JSONObject entryJson = companiesJson.getJSONObject(id);
				MercCompanyDef def = new MercCompanyDef();
				
				if (entryJson.has("requiredMods")) {
					List<String> requiredMods = NexUtils.JSONArrayToArrayList(entryJson.getJSONArray("requiredMods"));
					if (!requiredMods.isEmpty()) {
						if (!hasRequiredMods(requiredMods))
							continue;
						
						def.requiredMods.addAll(requiredMods);
					}
				}
				def.id = id;
				def.name = entryJson.getString("name");
				def.desc = entryJson.getString("desc");
				def.logo = entryJson.optString("logo", null);
				def.shipNamePrefix = entryJson.optString("shipNamePrefix", null);
				def.feeUpfront = Math.round(entryJson.optInt("feeUpfront") * feeUpfrontMult);
				def.feeMonthly = Math.round(entryJson.optInt("feeMonthly") * feeMonthlyMult);
				def.fleetFeeMult = (float)entryJson.optDouble("fleetFeeMult");
				def.factionId = entryJson.optString("faction", Factions.INDEPENDENT);
				if (Global.getSector().getFaction(def.factionId) == null) {
					throw new RuntimeException("  Invalid faction for merc company " + id);
				}
				def.factionIdForShipPick = entryJson.optString("factionForShipPick", def.factionId);
				
				if (entryJson.has("minRep"))
					def.minRep = RepLevel.valueOf(entryJson.getString("minRep").toUpperCase());
				def.minLevel = entryJson.optInt("minLevel");
				def.minLevel *= Global.getSettings().getInt("playerMaxLevel")/15f;
				
				// load ships
				def.ships.addAll(getShipList(entryJson));
				if (entryJson.has("shipNames"))
					def.shipNames.addAll(NexUtils.JSONArrayToArrayList(entryJson.getJSONArray("shipNames")));
				def.extraFP = entryJson.optInt("extraFP");
				if (entryJson.has("doctrineSizeOverride"))
					def.doctrineSizeOverride = entryJson.getInt("doctrineSizeOverride");
				
				def.officers.addAll(getOfficerList(entryJson));
				
				def.noAutofit = entryJson.optBoolean("noAutofit", false);
				if (entryJson.has("averageSMods"))
					def.averageSMods = entryJson.getInt("averageSMods");
				
				def.plugin = entryJson.optString("plugin", null);
				def.pickChance = (float)entryJson.optDouble("pickChance", 1);
				def.canPatrol = entryJson.optBoolean("canPatrol", true);
				def.blockScuttle = entryJson.optBoolean("blockScuttle", false);
				
				if (entryJson.has("miscData")) {
					JSONObject miscJson = entryJson.getJSONObject("miscData");
					def.miscData.putAll(NexUtils.jsonToMap(miscJson));
				}
				
				companyDefs.put(id, def);
			}
			
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load merc definitions", ex);
		}
	}
	
	public static Collection<MercCompanyDef> getAllDefs() {
		return companyDefs.values();
	}
	
	public static boolean allowAtFaction(String factionId) {
		if (enabledFactions.contains(factionId)) return true;
		if (disabledFactions.contains(factionId)) return false;
		
		return Global.getSector().getFaction(Factions.INDEPENDENT)
				.getRelationshipLevel(factionId).isAtWorst(RepLevel.NEUTRAL);
	}
	
	public static class MercCompanyDef {
		public String id;
		public String name;
		public String desc;
		public String logo;
		public String shipNamePrefix;
		public int feeUpfront;
		public int feeMonthly;
		public float fleetFeeMult;
		public String factionId;
		public String factionIdForShipPick;
		public RepLevel minRep;
		public int minLevel;
		public List<List<String>> ships = new ArrayList<>();
		public List<String> shipNames = new ArrayList<>();
		public int extraFP;
		public Integer doctrineSizeOverride;
		public List<OfficerDef> officers = new ArrayList<>();
		public boolean noAutofit;
		public Integer averageSMods;
		public String plugin;	// make this the actual plugin instance instead?
		public float pickChance;
		public boolean canPatrol;
		public boolean blockScuttle;
		public Map<String, Object> miscData = new HashMap<>();
		public List<String> requiredMods = new ArrayList<>();
		
		public FactionAPI getFaction() {
			return Global.getSector().getFaction(factionId);
		}
		
		public String getLogo() {
			if (logo != null) return logo;
			return getFaction().getCrest();
		}
		
		public String getRandomShip(int index) {
			return NexUtils.getRandomListElement(ships.get(index));
		}
	}
	
	public static class OfficerDef {
		public String firstName;
		public String lastName;
		public Gender gender;
		public int level;
		public String portrait;
		public String rankId;
		public String voice;
		public String personality;
		public Map<String, Integer> skills;	// null if not set
		public boolean skillsReplace;
		public String aiCoreId;
		public String persistentId;
		public String chatterCharacterId;
	}
}
