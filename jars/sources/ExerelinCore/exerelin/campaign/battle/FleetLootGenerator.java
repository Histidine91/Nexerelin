package exerelin.campaign.battle;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec;

public abstract class FleetLootGenerator {
	public String id;
	
	public FleetLootGenerator(String id) {
		this.id = id;
	}
	
	public String getId() {
		return id;
	}

	public abstract CargoAPI getLoot(CampaignFleetAPI fleet);
	
	public static SalvageEntityGenDataSpec.DropData createDropData(String group, int chances) {
		SalvageEntityGenDataSpec.DropData d = new SalvageEntityGenDataSpec.DropData();
		d.group = group;
		d.chances = chances;
		return d;
	}
}