package com.fs.starfarer.api.impl.campaign.rulecmd.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.missions.GroundBattleTutorial;
import exerelin.utilities.NexConfig;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;


public class Nex_GBTutMission extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg) {
			case "generateAgent":
				generateAgent(dialog);
				return true;
			case "isAllowed":
				return isAllowed(dialog.getInteractionTarget().getMarket());
			case "isEnemyFleetAlive":
				return isEnemyFleetAlive(dialog.getInteractionTarget().getContainingLocation());
			case "isYesodAvailable":
				return isYesodAvailable();
			case "repCheck":
				return repCheck(dialog);
		}
		
		return false;
	}
	
	public static boolean isAllowed(MarketAPI market) {
		if (NexConfig.legacyInvasions) return false;
		
		FactionAPI persean = Global.getSector().getFaction(Factions.PERSEAN);
		if (persean == null) {
			//log.info("No League faction");
			return false;
		}
		
		MarketAPI target = Global.getSector().getEconomy().getMarket(GroundBattleTutorial.PLANET_ID);
		if (target == null) {
			//log.info("Ilm not found");
			return false;
		}
		
		if (target == market) return false;
		if (target.getContainingLocation() == market.getContainingLocation()) return false;
		
		if (target.getFaction() != Global.getSector().getFaction(Factions.INDEPENDENT)) {
			//log.info("Ilm not an independent planet");
			return false;
		}
		
		if (!SectorManager.isFactionAlive(Factions.PERSEAN)) {
			//log.info("Persean League not alive");
			return false;
		}
		
		//if (persean.getRelToPlayer().isAtBest(RepLevel.INHOSPITABLE))
		//	return false;
		
		if (market.getFaction().isHostileTo(persean)) {
			//log.info("Current market hostile to League");
			return false;
		}
		
		//log.info("Can spawn tutorial here");
		return true;
	}
	
	// modified from base game's GenGAIntroAcademician
	protected void generateAgent(InteractionDialogAPI dialog) {
		PersonAPI person = Global.getSector().getFaction(Factions.INDEPENDENT).createRandomPerson();
		person.setFaction(Factions.PERSEAN);
		person.setRankId(Ranks.AGENT);
		person.setPostId(Ranks.POST_AGENT);
		
		// so that $herOrShe tokens work
		dialog.getInteractionTarget().setActivePerson(person);
		
		dialog.getVisualPanel().showPersonInfo(person, true, false);
	}
	
	protected boolean isEnemyFleetAlive(LocationAPI loc) {
		for (CampaignFleetAPI fleet : loc.getFleets()) {
			if (fleet.getMemoryWithoutUpdate().contains(GroundBattleTutorial.MEM_KEY_ENEMY_FLEET)) 
			{
				if (!fleet.isAlive()) continue;
				if (Misc.isInsignificant(fleet)) continue;
				return true;
			}
		}
		return false;
	}
	
	protected boolean isYesodAvailable() {
		MarketAPI yesod = Global.getSector().getEconomy().getMarket("yesod");
		if (yesod == null || !yesod.isInEconomy()) return false;
		if (!AllianceManager.areFactionsAllied(yesod.getFactionId(), Factions.PERSEAN)) return false;
		
		return true;
	}
	
	protected boolean repCheck(InteractionDialogAPI dialog) {
		FactionAPI persean = Global.getSector().getFaction(Factions.PERSEAN);
		if (persean == Misc.getCommissionFaction())
			return true;
		
		if (dialog.getInteractionTarget().getActivePerson().getRelToPlayer().isAtWorst(RepLevel.FAVORABLE))
			return true;
		
		return persean.getRelToPlayer().isAtWorst(RepLevel.FRIENDLY);
	}
}
