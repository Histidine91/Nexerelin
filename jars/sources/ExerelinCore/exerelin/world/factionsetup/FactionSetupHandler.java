package exerelin.world.factionsetup;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.SpecialItemPlugin;
import com.fs.starfarer.api.campaign.SpecialItemSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Pair;
import exerelin.ExerelinConstants;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
import exerelin.utilities.NexFactionConfig.SpecialItemSet;
import exerelin.utilities.NexUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONException;
import org.json.JSONObject;

public class FactionSetupHandler {
	
	public static final String CONFIG_PATH = "data/config/exerelin/factionSetupDefs.json";
	public static final Map<String, Integer> BP_COST_OVERRIDES = new HashMap<>(); 
	public static final List<FactionSetupItemDef> DEFS = new ArrayList<>();
	public static final Map<String, FactionSetupItemDef> DEFS_BY_ID = new HashMap<>();
	public static List<SpecialItemData> selectedItems = new ArrayList<>();
	
	static {
		loadDefs();
	}
	
	// runcode exerelin.world.factionsetup.FactionSetupHandler.loadDefs()
	public static void loadDefs() {
		DEFS.clear();
		DEFS_BY_ID.clear();
		BP_COST_OVERRIDES.clear();
		
		try {
			JSONObject configJson = Global.getSettings().getMergedJSONForMod(CONFIG_PATH, ExerelinConstants.MOD_ID);
			JSONObject bpOverridesJson = configJson.getJSONObject("blueprint_cost_overrides");
			Iterator<String> keys = bpOverridesJson.sortedKeys();
			while (keys.hasNext()) {
				String bpId = keys.next();
				int value = bpOverridesJson.getInt(bpId);
				BP_COST_OVERRIDES.put(bpId, value);
			}			
			
			JSONObject itemsListJson = configJson.getJSONObject("items");
			keys = itemsListJson.keys();
			while (keys.hasNext()) {
				String id = keys.next();
				JSONObject itemJson = itemsListJson.getJSONObject(id);
				FactionSetupItemDef item = new FactionSetupItemDef();
				item.id = id;
				item.name = itemJson.getString("name");
				item.desc = itemJson.optString("desc");
				item.cost = itemJson.optInt("cost");
				item.sprite = itemJson.optString("sprite");
				item.className = itemJson.getString("className");
				if (itemJson.has("params"))
					item.params = NexUtils.jsonToMap(itemJson.getJSONObject("params"));
				item.sortOrder = (float)itemJson.optDouble("sortOrder", 100);
				
				DEFS.add(item);
				DEFS_BY_ID.put(item.id, item);
			}
		} catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load faction setup config", ex);
		}
		Collections.sort(DEFS);
		
		loadStartingBlueprintsFromFactionConfigs();
	}
	
	public static void loadStartingBlueprintsFromFactionConfigs() {
		Set<String> alreadyAddedBPs = new HashSet<>();
		
		List<String> bpsToAdd = new ArrayList<>();
		List<FactionSetupItemDef> bpsToAddPack = new ArrayList<>();
		List<FactionSetupItemDef> bpsToAddSingle = new ArrayList<>();
		List<Pair<String, String>> singleBPsToAdd = new ArrayList<>();
				
		List<String> factions = NexConfig.getFactions(false, true);
		for (String factionId : factions) {
			NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
			for (SpecialItemSet itemSet : conf.startSpecialItems) {
				for (Pair<String, String> item : itemSet.items) {
					String id = item.one;
					SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
					if (spec == null) {
						Global.getLogger(FactionSetupHandler.class).error(String.format("Invalid special item %s for faction %s", id, factionId));
						continue;
					}
					if (spec.hasTag("package_bp")) 
					{
						if (alreadyAddedBPs.contains(spec.getId())) continue;
						bpsToAdd.add(id);
						alreadyAddedBPs.add(id);
					}
					else if (spec.hasTag("single_bp"))
					{
						if (alreadyAddedBPs.contains(item.two)) continue;
						singleBPsToAdd.add(item);
						alreadyAddedBPs.add(item.two);
					}
				}
			}
		}
		
		Collections.sort(bpsToAdd);
		
		for (String pack : bpsToAdd) {
			SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(pack);
			int cost = getBPCost(spec, pack);
			if (cost < 0) continue;
			
			FactionSetupItemDef def = new FactionSetupItemDef();
			def.id = "blueprint_" + pack;
			def.name = spec.getName();
			def.className = "exerelin.world.factionsetup.BlueprintItem";
			def.cost = cost;
			def.params.put("id", pack);
			def.params.put("params", null);

			bpsToAddPack.add(def);
		}
		Collections.sort(bpsToAddPack);
		
		for (Pair<String, String> bp : singleBPsToAdd) {
			SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(bp.one);
			int cost = getBPCost(spec, bp.two);
			if (cost < 0) continue;
			
			FactionSetupItemDef def = new FactionSetupItemDef();
			def.id = "blueprint_" + bp.one;
			def.name = spec.getName();
			def.className = "exerelin.world.factionsetup.BlueprintItem";
			def.cost = cost;
			def.params.put("id", bp.one);
			def.params.put("params", bp.two);
			
			bpsToAddSingle.add(def);
		}
		Collections.sort(bpsToAddSingle);
		
		for (FactionSetupItemDef def : bpsToAddPack) {
			DEFS.add(def);
			DEFS_BY_ID.put(def.id, def);
		}
		for (FactionSetupItemDef def : bpsToAddSingle) {
			DEFS.add(def);
			DEFS_BY_ID.put(def.id, def);
		}
	}
	
	public static int getBPCost(SpecialItemSpecAPI spec, String id) {
		int cost; 
		if (BP_COST_OVERRIDES.containsKey(id))
			cost = BP_COST_OVERRIDES.get(id);
		else if (spec.hasTag(Tags.NO_DROP) || spec.hasTag(Tags.NO_BP_DROP))
			cost = -1;
		else {
			cost = (int)Math.round(Math.log10(spec.getBasePrice()));
			if (spec.hasTag("package_bp")) cost *= 5;
		}
			
		return cost;
	}
	
	public static FactionSetupItemDef getDef(String id) {
		return DEFS_BY_ID.get(id);
	}
	
	public static void clearSelectedItems() {
		selectedItems.clear();
	}
	
	public static void addSelectedItem(SpecialItemData item) {
		selectedItems.add(item);
	}
	
	public static void applyItems() {
		CargoAPI temp = Global.getFactory().createCargo(true);
		for (SpecialItemData data : selectedItems) {
			temp.addSpecial(data, 1);
			
		}
		for (CargoStackAPI stack : temp.getStacksCopy()) {
			SpecialItemPlugin plugin = stack.getPlugin();
			((FactionSetupItemPlugin)plugin).getItem().apply();
		}
	}
	
	public static class FactionSetupItemDef implements Comparable<FactionSetupItemDef> {
		public String id;
		public String name;
		public String className;
		public String desc;
		public String sprite;
		public Map<String, Object> params = new HashMap<>();
		public int cost;
		public int count = 1;
		public float sortOrder;

		@Override
		public int compareTo(FactionSetupItemDef other) {
			int result = Float.compare(this.sortOrder, other.sortOrder);
			if (result == 0) {
				result = Integer.compare(this.cost, other.cost);
			}
			if (result == 0) {
				result = name.compareToIgnoreCase(other.name);
			}
			return result;
		}
	}
}
