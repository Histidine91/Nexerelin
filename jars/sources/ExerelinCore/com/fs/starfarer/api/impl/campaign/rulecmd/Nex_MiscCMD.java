package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel.AntiInspectionOrders;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.missions.remnant.RemnantQuestUtils;
import exerelin.utilities.NexUtilsReputation;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;
import java.util.Map;

public class Nex_MiscCMD extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) 
	{
		String arg = params.get(0).getString(memoryMap);
		
		switch(arg)
		{
			case "hasSierra":
				return hasSierra(memoryMap.get(MemKeys.LOCAL));
			case "hasMidnightContact":
				return ContactIntel.playerHasIntelItemForContact(Global.getSector().getImportantPeople().getPerson(RemnantQuestUtils.PERSON_DISSONANT));
			case "isRemoteConnection":
				return isRemote(dialog.getInteractionTarget());
			case "hasOngoingInspection":
				return hasOngoingInspection(dialog.getInteractionTarget().getMarket());
			case "saveFactionColor":
				saveFactionColor(params.get(1).getString(memoryMap), memoryMap.get(MemKeys.LOCAL));
				return true;
			default:
				return false;
		}
	}
	
	public static boolean hasSierra(MemoryAPI mem) {
		if (Global.getSector().getPlayerFleet() != null) {
			for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
				if (member.getVariant().hasHullMod("sotf_sierrasconcord") && !member.getVariant().hasTag("sotf_inert")) {
					mem.set("$nex_relColor", NexUtilsReputation.getRelColor(-0.2f), 0);
					return true;
				}
			}
		}
		return false;
	}
	
	public static boolean isRemote(SectorEntityToken target) {
		if (target.getContainingLocation() != Global.getSector().getCurrentLocation()) return true;
		float dist = MathUtils.getDistance(target, Global.getSector().getPlayerFleet());
		return dist > 10;
	}
	
	public static boolean hasOngoingInspection(MarketAPI market) {
		for (IntelInfoPlugin iip : Global.getSector().getIntelManager().getIntel(HegemonyInspectionIntel.class)) {
			if (iip.isEnding() || iip.isEnded()) continue;
			HegemonyInspectionIntel inspect = (HegemonyInspectionIntel)iip;
			if (inspect.getTarget() != market) continue;
			if (inspect.getOrders() == AntiInspectionOrders.BRIBE) continue;
			return true;
		}
		return false;
	}

	public static void saveFactionColor(String factionId, MemoryAPI local) {
		local.set("$factionColor", Global.getSector().getFaction(factionId).getBaseUIColor(), 0);
	}
}
