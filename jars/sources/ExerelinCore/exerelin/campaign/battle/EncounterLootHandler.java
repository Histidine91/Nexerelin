package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;

public class EncounterLootHandler extends BaseCampaignEventListener {
	
	public static final String FLEET_MEMORY_KEY = "$nex_encounterLootGenerator";
	
	public static Logger log = Global.getLogger(EncounterLootHandler.class);
	
	public static Map<String, FleetLootGenerator> lootGens = new HashMap<>();
	
	static {
		lootGens.put("derelict", new DerelictFleetLootGenerator());
		lootGens.put("remnant", new RemnantFleetLootGenerator());
	}
	
	public EncounterLootHandler() {
		super(false);
	}
	
	@Override
	public void reportEncounterLootGenerated(FleetEncounterContextPlugin plugin, CargoAPI loot) {
		if (!plugin.getBattle().isPlayerInvolved())
			return;
		
		Set<CampaignFleetAPI> loserFleets = new HashSet<>();
		loserFleets.addAll(plugin.getBattle().getNonPlayerSideSnapshot());
		
		for (CampaignFleetAPI fleet : loserFleets) {
			MemoryAPI mem = fleet.getMemoryWithoutUpdate();
			if (!mem.contains(FLEET_MEMORY_KEY)) continue;
			String genId = mem.getString(FLEET_MEMORY_KEY);
			if (!lootGens.containsKey(genId)) {
				continue;
			}
			CargoAPI fleetLoot = lootGens.get(genId).getLoot(fleet);
			loot.addAll(fleetLoot);
		}
	}
}
