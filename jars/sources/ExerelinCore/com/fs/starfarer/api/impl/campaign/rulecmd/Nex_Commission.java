package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.missions.Commission;
import static com.fs.starfarer.api.impl.campaign.rulecmd.missions.Commission.COMMISSION_REQ;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.intel.Nex_FactionCommissionIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.NexUtilsReputation;

public class Nex_Commission extends Commission {
	
	// replace the "faction issues commissions" check with "playable faction" check
	@Override
	protected boolean personCanGiveCommission() {
		if (person == null) return false;
		if (person.getFaction().isPlayerFaction()) return false;
		
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(person.getFaction().getId());
		if (!conf.playableFaction) return false;
		
		Alliance ally = AllianceManager.getPlayerAlliance(false);
		if (ally != null) return false;
		
		//if (Misc.getCommissionFactionId() != null) return false;
		
		return Ranks.POST_BASE_COMMANDER.equals(person.getPostId()) ||
			   Ranks.POST_STATION_COMMANDER.equals(person.getPostId()) ||
			   Ranks.POST_ADMINISTRATOR.equals(person.getPostId()) ||
			   Ranks.POST_OUTPOST_COMMANDER.equals(person.getPostId());
	}
	
	@Override
	protected void accept() {
		if (Misc.getCommissionFactionId() == null) {
			PlayerFactionStore.saveIndependentPlayerRelations();
			NexUtilsReputation.syncPlayerRelationshipsToFaction(faction.getId());
			
			Nex_FactionCommissionIntel intel = new Nex_FactionCommissionIntel(faction);
			intel.missionAccepted();
			intel.sendUpdate(Nex_FactionCommissionIntel.UPDATE_PARAM_ACCEPTED, dialog.getTextPanel());
			intel.makeRepChanges(dialog);
		}
	}
	
	
	@Override
	protected boolean playerMeetsCriteria() {
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(person.getFaction().getId());
		if (conf.pirateFaction)
			return faction.getRelToPlayer().isAtWorst(RepLevel.SUSPICIOUS);
		
		return super.playerMeetsCriteria();
	}
	
	@Override
	protected void printRequirements() {
		RepLevel required = COMMISSION_REQ;
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(person.getFaction().getId());
		if (conf.pirateFaction)
			required = RepLevel.SUSPICIOUS;
		
		CoreReputationPlugin.addRequiredStanding(entityFaction, required, null, dialog.getTextPanel(), null, null, 0f, true);
		CoreReputationPlugin.addCurrentStanding(entityFaction, null, dialog.getTextPanel(), null, null, 0f);
	}
}
