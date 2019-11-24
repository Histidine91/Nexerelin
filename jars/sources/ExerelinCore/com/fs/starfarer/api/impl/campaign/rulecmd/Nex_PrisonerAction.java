package com.fs.starfarer.api.impl.campaign.rulecmd;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.NexUtilsReputation;

public class Nex_PrisonerAction extends AgentActionBase {

	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		String arg = params.get(0).getString(memoryMap);
		MemoryAPI local = memoryMap.get(MemKeys.LOCAL);
		
		switch (arg) {
			case "ransom":
				return ransom(local, dialog.getInteractionTarget(), false);
			case "repatriate":
				return repatriate(dialog);
			case "sellSlave":
				return ransom(local, dialog.getInteractionTarget(), true);
			case "getCooldown":
				float cooldown = memoryMap.get(MemKeys.FACTION).getExpire("$nex_recentlyInvaded");
				String text = Misc.getAtLeastStringForDays((int)cooldown);
				local.set("$nex_recentInvasionCooldown", text, 0);
				break;
			case "getValue":
				int level = Global.getSector().getPlayerPerson().getStats().getLevel();
				int ransomValue = (int)(ExerelinConfig.prisonerBaseRansomValue + ExerelinConfig.prisonerRansomValueIncrementPerLevel * (level - 1));
				int slaveValue = (int)(ExerelinConfig.prisonerBaseSlaveValue + ExerelinConfig.prisonerSlaveValueIncrementPerLevel * (level - 1));
				
				local.set("$ransomValue", Misc.getWithDGS(ransomValue), 0);
				local.set("$slaveValue", Misc.getWithDGS(slaveValue), 0);
				break;
		}
		return true;
	}
	
	public boolean ransom(MemoryAPI mem, SectorEntityToken target, boolean isSlave) {
		if (!usePrisoner()) return false;
		
		float baseValue = isSlave? ExerelinConfig.prisonerBaseSlaveValue 
				: ExerelinConfig.prisonerBaseRansomValue;
		float increment = isSlave? ExerelinConfig.prisonerSlaveValueIncrementPerLevel 
				: ExerelinConfig.prisonerRansomValueIncrementPerLevel;
		String key = isSlave ? "$slaveValue" : "$ransomValue";
		
		int level = Global.getSector().getPlayerPerson().getStats().getLevel();
		int ransomValue = (int)(baseValue + increment * (level - 1));
		
		Global.getSector().getPlayerFleet().getCargo().getCredits().add(ransomValue);
		if (isSlave)
			SectorManager.notifySlavesSold(target.getMarket(), 1);
		else
			StatsTracker.getStatsTracker().notifyPrisonersRansomed(1);
		mem.set(key, Misc.getWithDGS(ransomValue), 0);
		
		return true;
	}
	
	public boolean repatriate(InteractionDialogAPI dialog) {
		if (!usePrisoner()) return false;
		
		SectorEntityToken target = dialog.getInteractionTarget();
		FactionAPI faction = target.getFaction();
		TextPanelAPI text = dialog.getTextPanel();
		
		NexUtilsReputation.adjustPlayerReputation(faction, target.getActivePerson(), ExerelinConfig.prisonerRepatriateRepValue,
						ExerelinConfig.prisonerRepatriateRepValue, null, text);
		DiplomacyManager.getManager().getDiplomacyBrain(faction.getId()).reportDiplomacyEvent(
						PlayerFactionStore.getPlayerFactionId(), ExerelinConfig.prisonerRepatriateRepValue);
		StatsTracker.getStatsTracker().notifyPrisonersRepatriated(1);
		
		return true;
	}
	
	protected boolean usePrisoner() {
		return useSpecialPerson("prisoner", 1);
	}
}
