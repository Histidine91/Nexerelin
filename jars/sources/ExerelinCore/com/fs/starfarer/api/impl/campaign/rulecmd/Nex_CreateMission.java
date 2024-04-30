package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMission;
import com.fs.starfarer.api.loading.PersonMissionSpec;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Same as BeginMission except for not accepting the mission immediately.
 * Obsolete, just use BeginMission with {@code false} for the {@code accept} arg.
 */
@Deprecated
public class Nex_CreateMission extends BaseCommandPlugin {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		String missionId = params.get(0).getString(memoryMap);
		
		PersonMissionSpec spec = Global.getSettings().getMissionSpec(missionId);
		if (spec == null) {
			throw new RuntimeException("Mission with spec [" + missionId + "] not found");
		}
		
		HubMission mission = spec.createMission();
		
		SectorEntityToken entity = dialog.getInteractionTarget();
		PersonAPI person = entity.getActivePerson();
		
		if (person == null) {
//			throw new RuntimeException("Attempting to BeginMission " + missionId + 
//									   " in interaction with entity.getActivePerson() == null");
//			String key = "$beginMission_seedExtra";
//			String extra = person.getMemoryWithoutUpdate().getString(key);
			String extra = "";
			long seed = BarEventManager.getInstance().getSeed(null, person, extra);
//			person.getMemoryWithoutUpdate().set(key, "" + seed); // so it's not the same seed for multiple missions
			mission.setGenRandom(new Random(seed));
			
		} else {
			mission.setPersonOverride(person);
			//mission.setGenRandom(new Random(Misc.getSalvageSeed(entity)));
			String key = "$beginMission_seedExtra";
			String extra = person.getMemoryWithoutUpdate().getString(key);
			long seed = BarEventManager.getInstance().getSeed(null, person, extra);
			person.getMemoryWithoutUpdate().set(key, "" + seed); // so it's not the same seed for multiple missions
			mission.setGenRandom(new Random(seed));
		}
		
		mission.createAndAbortIfFailed(entity.getMarket(), false);

		if (mission.isMissionCreationAborted()) {
			return false;
		}
		
		return true;
	}
}
