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

public class RemnantFleetLootGenerator extends FleetLootGenerator {
	
	public RemnantFleetLootGenerator() {
		super("remnant");
	}

	@Override
	public CargoAPI getLoot(CampaignFleetAPI fleet) 
	{
		List<DropData> dropRandom = new ArrayList<>();
		
		int [] counts = new int[5];
		String [] groups = new String [] {Drops.REM_FRIGATE, Drops.REM_DESTROYER, 
										  Drops.REM_CRUISER, Drops.REM_CAPITAL,
										  Drops.GUARANTEED_ALPHA};
		
		for (FleetMemberAPI member : Misc.getSnapshotMembersLost(fleet)) {
			if (member.isStation()) {
				counts[4] += 1;
				counts[3] += 1;
			} else if (member.isCapital()) {
				counts[3] += 1;
			} else if (member.isCruiser()) {
				counts[2] += 1;
			} else if (member.isDestroyer()) {
				counts[1] += 1;
			} else if (member.isFrigate()) {
				counts[0] += 1;
			}
		}
		
		for (int i = 0; i < counts.length; i++) {
			int count = counts[i];
			if (count <= 0) continue;
			
			dropRandom.add(createDropData(groups[i], (int) Math.ceil(count * 1f)));
		}
		
		Random salvageRandom = new Random(Misc.getSalvageSeed(fleet));
		CargoAPI extra = SalvageEntity.generateSalvage(salvageRandom, 1f, 1f, 1f, 1f, null, dropRandom);
		
		return extra;
	}
}