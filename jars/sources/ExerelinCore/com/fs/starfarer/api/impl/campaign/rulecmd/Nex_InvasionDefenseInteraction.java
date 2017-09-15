package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.NexFleetInteractionDialogPluginImpl;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.utilities.ExerelinUtilsFleet;
import java.util.List;
import java.util.Map;

// based on SalvageDefenderInteraction
public class Nex_InvasionDefenseInteraction extends BaseCommandPlugin {
	
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, final Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		
		final boolean isRaid = params.get(0).getBoolean(memoryMap);
		final SectorEntityToken entity = dialog.getInteractionTarget();
		final MarketAPI market = entity.getMarket();
		final MemoryAPI memory = memoryMap.get(MemKeys.MARKET);
		final String defenderMemFlag = isRaid ? "$nex_raidResponseFleet" : "$nex_invasionResponseFleet";
		final String defenderDefeatedMemFlag = isRaid ? "$nex_raidResponseDefeated" : "$nex_invasionResponseDefeated";

		final CampaignFleetAPI defenders = memory.getFleet(defenderMemFlag);
		if (defenders == null) return false;
		
		dialog.setInteractionTarget(defenders);
		defenders.setLocation(entity.getLocation().x, entity.getLocation().y);
		
		final FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
		config.leaveAlwaysAvailable = true;
		config.showFleetAttitude = false;
		config.showTransponderStatus = false;
		config.showWarningDialogWhenNotHostile = false;
		config.alwaysAttackVsAttack = false;
		//config.pullInEnemies = false;	// DEBUG
		//config.pullInAllies = false;
		
		//config.firstTimeEngageOptionText = "Engage the response fleet";
		//config.afterFirstTimeEngageOptionText = "Re-engage the response fleet";
		
		config.dismissOnLeave = false;
		config.printXPToDialog = true;
		
		//long seed = memory.getLong(MemFlags.SALVAGE_SEED);
		//config.salvageRandom = Misc.getRandom(seed, 75);
		
		final NexFleetInteractionDialogPluginImpl plugin = new NexFleetInteractionDialogPluginImpl(config);
		
		final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
		config.delegate = new FleetInteractionDialogPluginImpl.BaseFIDDelegate() {
			@Override
			public void notifyLeave(InteractionDialogAPI dialog) {
				
				dialog.setPlugin(originalPlugin);
				dialog.setInteractionTarget(entity);
				
				//Global.getSector().getCampaignUI().clearMessages();
				
				if (plugin.getContext() instanceof FleetEncounterContext) {
					FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
					if (context.didPlayerWinEncounter()) {
						
						SalvageGenFromSeed.SDMParams p = new SalvageGenFromSeed.SDMParams();
						p.entity = entity;
						p.factionId = defenders.getFaction().getId();
						
						SalvageGenFromSeed.SalvageDefenderModificationPlugin plugin = Global.getSector().getGenericPlugins().pickPlugin(
												SalvageGenFromSeed.SalvageDefenderModificationPlugin.class, p);
						if (plugin != null) {
							plugin.reportDefeated(p, entity, defenders);
						}
						
						memory.unset("$hasDefenders");
						memory.unset(defenderMemFlag);
						memory.set(defenderDefeatedMemFlag, true, isRaid ? 3f : 0.5f);
						FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
					} else {
						if (isRaid)
						{
							/*
							InvasionFleetManager.InvasionFleetData data = new InvasionFleetManager.InvasionFleetData(defenders);
							data.startingFleetPoints = defenders.getFleetPoints();
							data.sourceMarket = market;
							data.source = market.getPrimaryEntity();
							data.targetMarket = market;
							data.target = market.getPrimaryEntity();
							
							defenders.addScript(new DefenceFleetAI(defenders, data));
							*/
							memory.expire(defenderMemFlag, 3);
						}
						else
						{
							defenders.setContainingLocation(entity.getContainingLocation());
							defenders.setLocation(entity.getLocation().x, entity.getLocation().y);
							ResponseFleetManager.getManager().registerResponseFleetAndSetAI(defenders, market, 
									Global.getSector().getPlayerFleet());
							memory.unset(defenderMemFlag);
						}
						dialog.dismiss();
					}
					// deduct response fleet points now that fleet has spawned and is permanent (or dead)
					if (!isRaid)
					{
						float pointsToDeduct = ExerelinUtilsFleet.getFleetGenPoints(defenders);
						Global.getLogger(this.getClass()).info("Removing " + pointsToDeduct + " reserve points from " + market.getName());
						ResponseFleetManager.modifyReserveSize(market, pointsToDeduct);
					}
					// TODO: figure out what to do with stations
					
				} else {
					dialog.dismiss();
				}
			}
			@Override
			public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
				bcc.aiRetreatAllowed = false;
				//bcc.objectivesAllowed = false;
				bcc.enemyDeployAll = true;
			}
		};
		
		
		dialog.setPlugin(plugin);
		plugin.init(dialog);
	
		return true;
	}
}
