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
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexFactionConfig;
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
				int ransomValue = (int)(NexConfig.prisonerBaseRansomValue + NexConfig.prisonerRansomValueIncrementPerLevel * (level - 1));
				
				local.set("$ransomValue", Misc.getWithDGS(ransomValue), 0);
				break;
			case "isAtMaxRep":
				return isAtMaxRep(dialog.getInteractionTarget());
		}
		return true;
	}
	
	public boolean ransom(MemoryAPI mem, SectorEntityToken target, boolean isSlave) {
		if (!usePrisoner()) return false;
		
		float baseValue = NexConfig.prisonerBaseRansomValue;
		float increment = NexConfig.prisonerRansomValueIncrementPerLevel;
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
		
		//boolean wasInhosp = faction.getRelToPlayer().isAtBest(RepLevel.INHOSPITABLE);
		
		NexUtilsReputation.adjustPlayerReputation(faction, target.getActivePerson(), NexConfig.prisonerRepatriateRepValue,
						NexConfig.prisonerRepatriateRepValue, null, text);
		DiplomacyManager.getManager().getDiplomacyBrain(faction.getId()).reportDiplomacyEvent(PlayerFactionStore.getPlayerFactionId(), NexConfig.prisonerRepatriateRepValue);
		StatsTracker.getStatsTracker().notifyPrisonersRepatriated(1);
		
		//boolean isInhosp = faction.getRelToPlayer().isAtBest(RepLevel.INHOSPITABLE);
		
		// Too much trouble right now, maybe next time
		/* 
		// refresh memory now that we have the rep level to dock
		if (wasInhosp && isInhosp) {
			// TODO: check if we're locked out due to recent commotion?
			// or just let us go in anyway
			boolean transp = Global.getSector().getPlayerFleet().isTransponderOn();
			
			dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$tradeMode", transp ? "OPEN" : "SNEAK", 0);
			((RuleBasedDialog)dialog.getPlugin()).updateMemory();
		}
		*/
		
		return true;
	}
	
	protected boolean usePrisoner() {
		return useSpecialPerson("prisoner", 1);
	}
	
	protected boolean isAtMaxRep(SectorEntityToken target) {
		FactionAPI faction1 = PlayerFactionStore.getPlayerFaction();
		FactionAPI faction2 = target.getFaction();
		if (faction1 == faction2) faction1 = Global.getSector().getPlayerFaction();
		float max = DiplomacyManager.getManager().getMaxRelationship(faction1.getId(), faction2.getId());
		float curr = faction1.getRelationship(faction2.getId()); 
		//Global.getLogger(this.getClass()).info(String.format("Current: %s, max: %s", curr, max));
		return max - curr <= 0.001f;	// floating point bullshit
	}
}
