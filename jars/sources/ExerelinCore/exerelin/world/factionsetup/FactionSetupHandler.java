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
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinFactionConfig.SpecialItemSet;
import exerelin.utilities.ExerelinUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FactionSetupHandler {
	
	public static final String CONFIG_PATH = "data/config/exerelin/factionSetupDefs.json";
		
	public static List<FactionSetupItemDef> defs = new ArrayList<>();
	public static Map<String, FactionSetupItemDef> defsById = new HashMap<>();
	public static List<SpecialItemData> selectedItems = new ArrayList<>();
	
	static {
		loadDefs();
	}
	
	public static void loadDefs() {
		try {
			JSONObject configJson = Global.getSettings().getMergedJSONForMod(CONFIG_PATH, ExerelinConstants.MOD_ID);
			JSONArray itemsListJson = configJson.getJSONArray("items");
			for (int i=0; i<itemsListJson.length(); i++) {
				JSONObject itemJson = itemsListJson.getJSONObject(i);
				FactionSetupItemDef item = new FactionSetupItemDef();
				item.id = itemJson.getString("id");
				item.name = itemJson.getString("name");
				item.desc = itemJson.optString("desc");
				item.cost = itemJson.optInt("cost");
				item.sprite = itemJson.optString("sprite");
				item.className = itemJson.getString("className");
				if (itemJson.has("params"))
					item.params = ExerelinUtils.jsonToMap(itemJson.getJSONObject("params"));
				
				defs.add(item);
				defsById.put(item.id, item);
			}
		} catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load faction setup config", ex);
		}
				
		
		loadStartingBlueprintsFromFactionConfigs();
	}
	
	public static void loadStartingBlueprintsFromFactionConfigs() {
		Set<String> alreadyAddedBPs = new HashSet<>();
		
		List<String> packagesToAdd = new ArrayList<>();
		List<Pair<String, String>> singleBPsToAdd = new ArrayList<>();
				
		List<String> factions = ExerelinConfig.getFactions(false, true);
		for (String factionId : factions) {
			ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
			for (SpecialItemSet itemSet : conf.startSpecialItems) {
				for (Pair<String, String> item : itemSet.items) {
					String id = item.one;
					SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(id);
					if (spec.hasTag(Tags.NO_DROP)) continue;
					if (spec.hasTag(Tags.NO_BP_DROP)) continue;
					if (spec.hasTag("package_bp")) 
					{
						if (alreadyAddedBPs.contains(spec.getId())) continue;
						packagesToAdd.add(id);
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
		
		Collections.sort(packagesToAdd);
		
		for (String pack : packagesToAdd) {
			SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(pack);
			FactionSetupItemDef def = new FactionSetupItemDef();
			def.id = "blueprint_" + pack;
			def.name = spec.getName();
			def.className = "exerelin.world.factionsetup.BlueprintItem";
			def.cost = (int)Math.round(Math.log10(spec.getBasePrice())) * 5;
			def.params.put("id", pack);
			def.params.put("params", null);

			defs.add(def);
			defsById.put(def.id, def);
		}
		
		for (Pair<String, String> bp : singleBPsToAdd) {
			SpecialItemSpecAPI spec = Global.getSettings().getSpecialItemSpec(bp.one);
			FactionSetupItemDef def = new FactionSetupItemDef();
			def.id = "blueprint_" + bp.one;
			def.name = spec.getName();	// TODO
			def.className = "exerelin.world.factionsetup.BlueprintItem";
			def.cost = 5;	// FIXME
			def.params.put("id", bp.one);
			def.params.put("params", bp.two);

			defs.add(def);
			defsById.put(def.id, def);
		}
	}
	
	public static FactionSetupItemDef getDef(String id) {
		return defsById.get(id);
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
	
	public static class FactionSetupItemDef {
		public String id;
		public String name;
		public String className;
		public String desc;
		public String sprite;
		public Map<String, Object> params = new HashMap<>();
		public int cost;
		public int count = 1;
	}
}
