package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemKeys;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.BattleCreationContext;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.NexFleetInteractionDialogPluginImpl;
import exerelin.campaign.fleets.DefenceFleetAI;
import exerelin.campaign.fleets.InvasionFleetManager;
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
		if (!defenders.getMemoryWithoutUpdate().getBoolean("$nex_defstation"))
		{
			entity.getContainingLocation().addEntity(defenders);
			defenders.setLocation(entity.getLocation().x, entity.getLocation().y);
		}
		
		final FleetInteractionDialogPluginImpl.FIDConfig config = new FleetInteractionDialogPluginImpl.FIDConfig();
		config.leaveAlwaysAvailable = true;	//isRaid;
		//config.showFleetAttitude = false;
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
			
			public void handleDefenders(InteractionDialogAPI dialog)
			{
				boolean isStation = defenders.getMemoryWithoutUpdate().getBoolean("$nex_defstation");
				
				if (!isStation && defenders.getContainingLocation() != null)
					defenders.getContainingLocation().removeEntity(defenders);
					
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

					//entity.removeScriptsOfClass(FleetAdvanceScript.class);
					memory.unset("$hasDefenders");
					memory.unset(defenderMemFlag);
					memory.set(defenderDefeatedMemFlag, true, isRaid ? 3f : 1f);
					FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
				} else {
					// we fought a battle (and did not win), spawn the defence fleets into world where the usual stuff can handle them
					if (context.isEngagedInHostilities())
					{
						if (isStation)
						{
							// do nothing
						}
						else if (isRaid)
						{
							entity.getContainingLocation().addEntity(defenders);
							defenders.setLocation(entity.getLocation().x, entity.getLocation().y);

							InvasionFleetManager.InvasionFleetData data = new InvasionFleetManager.InvasionFleetData(defenders);
							data.startingFleetPoints = defenders.getFleetPoints();
							data.sourceMarket = market;
							data.source = market.getPrimaryEntity();
							data.targetMarket = market;
							data.target = market.getPrimaryEntity();

							// don't; we don't actually want to count towards the fleet limit
							//InvasionFleetManager.getManager().addActiveFleet(data);
							DefenceFleetAI ai = new DefenceFleetAI(defenders, data);
							defenders.addScript(ai);
							ai.giveStandDownOrders();
							defenders.addAssignmentAtStart(FleetAssignment.STANDING_DOWN, entity, 1f, null);
						}
						else
						{
							entity.getContainingLocation().addEntity(defenders);
							defenders.setLocation(entity.getLocation().x, entity.getLocation().y);
							ResponseFleetManager.getManager().registerResponseFleetAndSetAI(defenders, market, 
									Global.getSector().getPlayerFleet());
							defenders.addAssignmentAtStart(FleetAssignment.STANDING_DOWN, entity, 1f, null);
						}
						memory.unset("$hasDefenders");
						memory.unset(defenderMemFlag);
					}
					dialog.dismiss();
				}
				// deduct response fleet points now that fleet has spawned and is permanent (or dead)
				if (!isStation && !isRaid && context.isEngagedInHostilities())
				{
					float pointsToDeduct = 0;
					List<FleetMemberAPI> snapshot = defenders.getFleetData().getSnapshot();
					for (FleetMemberAPI member : snapshot)
					{
						float memberPts = ExerelinUtilsFleet.getFleetGenPoints(member);

						// fleet spawned, or member killed
						if (!context.didPlayerWinEncounter() || !defenders.getMembersWithFightersCopy().contains(member))
						{
							//Global.getLogger(this.getClass()).info(member.getShipName() + " is spawned/dead, worth " + memberPts);
							pointsToDeduct += memberPts;
						}
						// member survived, fleet did not spawn
						else
						{
							//Global.getLogger(this.getClass()).info(member.getShipName() + " is unspawned, worth " + (1 - ResponseFleetAI.RESERVE_RESTORE_EFFICIENCY) * memberPts);
							pointsToDeduct += memberPts;	//(1 - ResponseFleetAI.RESERVE_RESTORE_EFFICIENCY) * memberPts;
						}
					}				

					Global.getLogger(this.getClass()).info("Removing " + pointsToDeduct + " reserve points from " + market.getName());
					ResponseFleetManager.modifyReserveSize(market, -pointsToDeduct);
				}
			}
			
			@Override
			public void notifyLeave(InteractionDialogAPI dialog) {
				
				dialog.setPlugin(originalPlugin);
				dialog.setInteractionTarget(entity);
				
				//Global.getSector().getCampaignUI().clearMessages();
				
				if (plugin.getContext() instanceof FleetEncounterContext) {
					handleDefenders(dialog);
					
				} else {
					dialog.dismiss();
				}
			}
			@Override
			public void battleContextCreated(InteractionDialogAPI dialog, BattleCreationContext bcc) {
				//bcc.aiRetreatAllowed = false;
				//bcc.objectivesAllowed = false;
				//bcc.enemyDeployAll = true;
			}
		};
		
		
		dialog.setPlugin(plugin);
		plugin.init(dialog);
	
		return true;
	}
}
