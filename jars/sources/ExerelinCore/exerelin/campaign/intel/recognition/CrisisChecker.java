package exerelin.campaign.intel.recognition;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.PerseanLeagueMembership;
import com.fs.starfarer.api.impl.campaign.intel.events.*;
import exerelin.campaign.intel.hostileactivity.PoliceHostileActivityFactor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CrisisChecker {

	public static final Map<String, CrisisCheckerEntry> CRISES = new HashMap<>();
	public static final int BASE_RECOGNITION = 400;

	static {
		addCrisisEntry(Factions.HEGEMONY, Factions.HEGEMONY, HegemonyHostileActivityFactor.DEFEATED_HEGEMONY, 1000, 0);
		addCrisisEntry(Factions.LUDDIC_CHURCH, Factions.LUDDIC_CHURCH, LuddicChurchHostileActivityFactor.DEFEATED_LUDDIC_CHURCH_EXPEDITION, BASE_RECOGNITION, 0);
		addCrisisEntry(Factions.LUDDIC_PATH, Factions.LUDDIC_CHURCH, LuddicPathHostileActivityFactor.DEFEATED_PATHER_EXPEDITION, BASE_RECOGNITION, 0);
		addCrisisEntry(Factions.PIRATES, Factions.PIRATES, PirateHostileActivityFactor.DEFEATED_LARGE_PIRATE_RAID, BASE_RECOGNITION, 0);
		addCrisisEntry(Factions.DIKTAT, Factions.DIKTAT, SindrianDiktatHostileActivityFactor.DEFEATED_DIKTAT_ATTACK, BASE_RECOGNITION, 0);
		addCrisisEntry("sectorpol", Factions.INDEPENDENT, PoliceHostileActivityFactor.MEM_KEY_DEFEATED_EXPEDITION, BASE_RECOGNITION, -75);

		CrisisCheckerEntry pl = new CrisisCheckerEntry(Factions.PERSEAN, Factions.PERSEAN, null, BASE_RECOGNITION, 100) {
			@Override
			public boolean isCrisisDone() {
				return PerseanLeagueMembership.isDefeatedBlockadeOrPunEx();
			}
		};
		CRISES.put(Factions.PERSEAN, pl);
	}

	public static void addCrisisEntry(String crisisId, String factionId, @Nullable String memKeyToCheck, int recogPoints, int respectPoints) {
		CrisisCheckerEntry entry = new CrisisCheckerEntry(crisisId, factionId, memKeyToCheck, recogPoints, respectPoints);
		CRISES.put(crisisId, entry);
	}

	public static void checkCrises(FactionRecognitionIntel intel) {
		for (String id : CRISES.keySet()) {
			if (intel.getCompletedCrises().contains(id)) continue;
			CrisisCheckerEntry entry = CRISES.get(id);

			if (entry.memKeyToCheck != null && Global.getSector().getPlayerMemoryWithoutUpdate().getBoolean(entry.memKeyToCheck)) {
				intel.reportCrisisCompleted(entry);
				continue;
			}
			if (entry.isCrisisDone()) {
				intel.reportCrisisCompleted(entry);
			}
		}
	}

	public static class CrisisCheckerEntry {
		public String crisisId;
		public String factionId;
		@Nullable public String memKeyToCheck;
		public int recogPoints;
		public int respectPoints;

		public CrisisCheckerEntry(String crisisId, String factionId, @Nullable String memKeyToCheck, int recogPoints, int respectPoints) {
			this.crisisId = crisisId;
			this.factionId = factionId;
			this.memKeyToCheck = memKeyToCheck;
			this.recogPoints = recogPoints;
			this.respectPoints = respectPoints;
		}

		public boolean isCrisisDone() {
			return false;
		}
	}
}
