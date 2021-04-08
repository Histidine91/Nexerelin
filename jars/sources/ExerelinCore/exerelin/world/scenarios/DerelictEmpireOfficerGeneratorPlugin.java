package exerelin.world.scenarios;

import com.fs.starfarer.api.campaign.GenericPluginManagerAPI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RemnantOfficerGeneratorPlugin;

public class DerelictEmpireOfficerGeneratorPlugin extends RemnantOfficerGeneratorPlugin {
	
	@Override
	public int getHandlingPriority(Object params) {
		if (!(params instanceof GenerateFleetOfficersPickData)) return -1;
		GenerateFleetOfficersPickData data = (GenerateFleetOfficersPickData) params;
		
		if (data.params != null && !data.params.withOfficers) return -1;		
		if (data.fleet == null || !data.fleet.getFaction().getId().equals("nex_derelict")) return -1;
		
		return GenericPluginManagerAPI.MOD_SUBSET;
	}
}
