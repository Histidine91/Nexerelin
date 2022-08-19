package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import data.scripts.crewReplacer_Main;

/**
 * Handles interaction with the CrewReplacer mod.
 * https://github.com/Alaricdragon/Crew_Replacer
 */
public class CrewReplacerUtils {
	
	public static boolean enabled = Global.getSettings().getModManager().isModEnabled("aaacrew_replacer");
	
	public static float getMarines(CampaignFleetAPI fleet, String job) {
		if (!enabled) return fleet.getCargo().getMarines();
		
		return crewReplacer_Main.getJob(job).getAvailableCrewPower(fleet);
	}
}
