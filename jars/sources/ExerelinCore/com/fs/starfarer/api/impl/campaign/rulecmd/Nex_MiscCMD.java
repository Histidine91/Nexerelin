package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.NexUtilsReputation;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

public class Nex_MiscCMD extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String arg = params.get(0).getString(memoryMap);
		
		switch(arg)
		{
			case "hasSierra":
				return hasSierra(memoryMap.get(MemKeys.LOCAL));
			case "isRemoteConnection":
				return isRemote(dialog.getInteractionTarget());
			default:
				return false;
		}
	}
	
	public boolean hasSierra(MemoryAPI mem) {
		if (Global.getSector().getPlayerFleet() != null) {
			for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
				if (member.getVariant().hasHullMod("fronsec_sierrasconcord")) {
					//Global.getLogger(Nex_MiscCMD.class).info("Has sierra");
					mem.set("$nex_relColor", NexUtilsReputation.getRelColor(-0.2f), 0);
					return true;
				}
			}
		}
		return false;
	}
	
	public boolean isRemote(SectorEntityToken target) {
		if (target.getContainingLocation() != Global.getSector().getCurrentLocation()) return true;
		float dist = MathUtils.getDistance(target, Global.getSector().getPlayerFleet());
		return dist > 10;
	}
}
