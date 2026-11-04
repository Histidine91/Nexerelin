package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel.AntiInspectionOrders;
import com.fs.starfarer.api.loading.AbilitySpecAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.fleets.InvasionFleetManager;
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
				return hasSierra();
			case "hasMidnightContact":
				return ContactIntel.playerHasIntelItemForContact(Global.getSector().getImportantPeople().getPerson(RemnantQuestUtils.PERSON_DISSONANT));
			case "isRemoteConnection":
				return isRemote(dialog.getInteractionTarget());
			case "hasOngoingInspection":
				return hasOngoingInspection(dialog.getInteractionTarget().getMarket());
			case "hasAnyColoniesForQuartermasterSupplies":
				return hasAnyColoniesForQuartermasterSupplies();
			case "saveFactionColor":
				saveFactionColor(params.get(1).getString(memoryMap), memoryMap.get(MemKeys.LOCAL));
				return true;
			case "enableRemnantRaids":
				toggleRemnantRaids(dialog, true);
				return true;
			case "disableRemnantRaids":
				toggleRemnantRaids(dialog, false);
				return true;
			case "setRelColorForSierraConvo":
				memoryMap.get(MemKeys.LOCAL).set("$nex_relColor", NexUtilsReputation.getRelColor(-0.2f), 0);
				return true;
			case "readdAbilitiesAfterTutorial":
				readdAbilitiesAfterTutorial(dialog);
				return true;
			default:
				return false;
		}
	}

	public static void readdAbilitiesAfterTutorial(InteractionDialogAPI dialog) {
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		CharacterDataAPI player = Global.getSector().getCharacterData();
		for (String abId : Global.getSettings().getSortedAbilityIds()) {
			boolean fleetHave = playerFleet.hasAbility(abId);    // player.getAbilities().contains(abId)
			if (fleetHave) continue;
			//boolean charHave = player.getAbilities().contains(abId);  // shouldn't need to add to character data, since if our fleet gets replaced the new one should still reference the same character
			AbilitySpecAPI spec = Global.getSettings().getAbilitySpec(abId);
			if (!spec.isUnlockedAtStart()) continue;
			player.addAbility(abId);
			AddRemoveCommodity.addAbilityGainText(abId, dialog.getTextPanel());
		}
	}
	
	public static boolean hasSierra() {
		if (Global.getSector().getPlayerFleet() != null) {
			for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
				if (member.getVariant().hasHullMod("sotf_sierrasconcord") && !member.getVariant().hasTag("sotf_inert")) {
					return true;
				}
			}
		}
		return false;
	}

	public static void toggleRemnantRaids(InteractionDialogAPI dialog, boolean enable) {
		MemoryAPI mem = Global.getSector().getFaction(Factions.REMNANTS).getMemoryWithoutUpdate();
		boolean result = Misc.setFlagWithReason(mem,
				InvasionFleetManager.MEMORY_KEY_DISABLE_RAID_FROM_BASES, "remnantContact", !enable, -1f);
		Global.getLogger(Nex_MiscCMD.class).info(result);
		((RuleBasedDialog)dialog.getPlugin()).updateMemory();
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

	public static boolean hasAnyColoniesForQuartermasterSupplies() {
		for (MarketAPI market : Misc.getPlayerMarkets(true)) {
			if (market.isHidden()) continue;
			return true;
		}
		return false;
	}

	public static void saveFactionColor(String factionId, MemoryAPI local) {
		local.set("$factionColor", Global.getSector().getFaction(factionId).getBaseUIColor(), 0);
	}
}
