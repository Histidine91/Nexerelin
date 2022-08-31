package exerelin.utilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import data.scripts.crewReplacer_Job;
import data.scripts.crewReplacer_Main;

/**
 * Handles interaction with the CrewReplacer mod.
 * https://github.com/Alaricdragon/Crew_Replacer
 */
public class CrewReplacerUtils {
	
	public static boolean enabled = Global.getSettings().getModManager().isModEnabled("aaacrew_replacer");
	
	public static float getMarines(CampaignFleetAPI fleet, String jobId) {
		if (!enabled) return fleet.getCargo().getMarines();
		
		return crewReplacer_Main.getJob(jobId).getAvailableCrewPower(fleet);
	}
	
	public static void removeMarines (CampaignFleetAPI fleet, String jobId, int count, TextPanelAPI text) {
		if (!enabled) {
			fleet.getCargo().removeMarines(count);
			if (text != null) AddRemoveCommodity.addCommodityLossText(Commodities.MARINES, count, text);
			return;
		}
		crewReplacer_Job job = crewReplacer_Main.getJob(jobId);
		crewReplacer_Main.getJob(jobId).automaticlyGetDisplayAndApplyCrewLost(
				fleet, (int)job.getAvailableCrewPower(fleet), count, text);
	}
}
