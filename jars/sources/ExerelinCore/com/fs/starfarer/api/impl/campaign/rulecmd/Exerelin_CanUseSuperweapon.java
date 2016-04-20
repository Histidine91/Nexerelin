package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import static com.fs.starfarer.api.impl.campaign.rulecmd.Exerelin_UseSuperweapon.CR_COSTS;
import com.fs.starfarer.api.util.Misc;
import java.util.List;
import java.util.Map;

public class Exerelin_CanUseSuperweapon extends BaseCommandPlugin {
		
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		
		CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
		FleetMemberAPI bestMember = null;
		float bestCR = 0;
		
		List<FleetMemberAPI> members = fleet.getFleetData().getMembersListCopy();
		for (FleetMemberAPI member : members) {
			float cr = member.getRepairTracker().getCR();
			if (cr <= 0) continue;
			if (member.getHullId().equals("ii_boss_olympus")) {
				bestMember = member;
				break;
			}
			else if (member.getHullId().equals("ii_olympus_t")) {
				if (cr > bestCR) {
					bestMember = member;
					bestCR = cr;
				}
			}
		}
		if (bestMember == null) return false;
		
		// deduct CR
		float crCost = 0;
		String hullId = bestMember.getHullId();
		
		if (CR_COSTS.containsKey(hullId))
			crCost = CR_COSTS.get(hullId);
		if (crCost != 0) {
			if (crCost > bestCR) return false;
		}
		
		memoryMap.get(MemKeys.LOCAL).set("$bestSuperweapon", bestMember, 0);
		return true;
	}
}
