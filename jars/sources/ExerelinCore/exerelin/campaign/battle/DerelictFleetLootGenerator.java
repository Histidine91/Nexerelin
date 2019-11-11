package exerelin.campaign.battle;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Drops;
import com.fs.starfarer.api.impl.campaign.procgen.SalvageEntityGenDataSpec.DropData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageEntity;
import com.fs.starfarer.api.util.Misc;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DerelictFleetLootGenerator extends FleetLootGenerator {
	
	public DerelictFleetLootGenerator() {
		super("derelict");
	}

	@Override
	public CargoAPI getLoot(CampaignFleetAPI fleet) 
	{
		List<DropData> dropRandom = new ArrayList<>();
		for (FleetMemberAPI member : Misc.getSnapshotMembersLost(fleet)) {
			if (member.isStation()) {
				dropRandom.add(createDropData(Drops.REM_DESTROYER, 1));
				dropRandom.add(createDropData(Drops.REM_FRIGATE, 1));
			} else if (member.isCapital()) {
				dropRandom.add(createDropData(Drops.REM_FRIGATE, 1));
			}
		}
		
		Random salvageRandom = new Random(Misc.getSalvageSeed(fleet));
		CargoAPI extra = SalvageEntity.generateSalvage(salvageRandom, 1f, 1f, 1f, 1f, null, dropRandom);
		
		return extra;
	}
}