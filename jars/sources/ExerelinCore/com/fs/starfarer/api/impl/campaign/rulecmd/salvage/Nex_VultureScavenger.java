package com.fs.starfarer.api.impl.campaign.rulecmd.salvage;

import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.BaseFIDDelegate;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.FIDConfig;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireBest;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed.SDMParams;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageGenFromSeed.SalvageDefenderModificationPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.Random;
import org.lazywizard.lazylib.MathUtils;

/**
 *	Adapted from SalvageDefenderInteraction
 */
public class Nex_VultureScavenger extends BaseCommandPlugin {
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, final Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		String arg = params.get(0).getString(memoryMap);
		
		switch (arg) {
			case "hasNearby":
				return getNearby(dialog.getInteractionTarget()) != null;
			case "battle":
				return initBattle(dialog, memoryMap);
		}
		return false;
	}
	
	public CampaignFleetAPI getNearby(SectorEntityToken target) 
	{
		if (target instanceof PlanetAPI) return null;
		
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		for (CampaignFleetAPI fleet : target.getContainingLocation().getFleets())
		{
			if ("nex_vultureFleet".equals(NexUtilsFleet.getFleetType(fleet))) 
			{
				//Global.getLogger(this.getClass()).info("Checking vulture " + fleet.getNameWithFaction());
				if (fleet.getBattle() != null) {
					continue;
				}
				
				// vultures must be able to see player
				if (!player.isVisibleToSensorsOf(fleet)) {
					//Global.getLogger(this.getClass()).info("Cannot see");
					continue;
				}					
				
				// check join range
				if (MathUtils.getDistance(target, fleet) > Misc.getBattleJoinRange()) {
					//Global.getLogger(this.getClass()).info("Out of range");
					continue;
				}					
				
				// don't pick a fight with a much stronger player
				CampaignFleetAIAPI ai = (CampaignFleetAIAPI) fleet.getAI();
				if (ai == null) continue;
				EncounterOption option = ai.pickEncounterOption(null, player, true);
				if (option != EncounterOption.ENGAGE)
					continue;
				
				return fleet;
			}
		}
		return null;
	}
	
	public boolean initBattle(InteractionDialogAPI dialog, final Map<String, MemoryAPI> memoryMap) {
		if (dialog == null) return false;
		final SectorEntityToken entity = dialog.getInteractionTarget();

		final CampaignFleetAPI defenders = getNearby(entity);
		if (defenders == null) return false;
		
		dialog.setInteractionTarget(defenders);
		defenders.getMemoryWithoutUpdate().set("$nex_isVultureDefending", true, 0);
		
		final FIDConfig config = new FIDConfig();
		config.leaveAlwaysAvailable = true;
		//config.showCommLinkOption = false;
		//config.showEngageText = false;
		config.showFleetAttitude = false;
		//config.alwaysAttackVsAttack = true;
		config.pullInStations = false;
		config.pullInEnemies = false;
		//config.pullInAllies = false;
		config.noSalvageLeaveOptionText = Misc.ucFirst(StringHelper.getString("continue"));
		
		config.dismissOnLeave = false;
		config.printXPToDialog = true;
		
		config.salvageRandom = new Random();
		
		final FleetInteractionDialogPluginImpl plugin = new FleetInteractionDialogPluginImpl(config);
		
		final InteractionDialogPlugin originalPlugin = dialog.getPlugin();
		config.delegate = new BaseFIDDelegate() {
			@Override
			public void notifyLeave(InteractionDialogAPI dialog) {
								
				dialog.setPlugin(originalPlugin);
				dialog.setInteractionTarget(entity);
				
				//Global.getSector().getCampaignUI().clearMessages();
				
				if (plugin.getContext() instanceof FleetEncounterContext) {
					FleetEncounterContext context = (FleetEncounterContext) plugin.getContext();
					if (context.didPlayerWinMostRecentBattleOfEncounter()) {
						
						SDMParams p = new SDMParams();
						p.entity = entity;
						p.factionId = defenders.getFaction().getId();
						
						SalvageDefenderModificationPlugin plugin = Global.getSector().getGenericPlugins().pickPlugin(
												SalvageDefenderModificationPlugin.class, p);
						if (plugin != null) {
							plugin.reportDefeated(p, entity, defenders);
						}
						FireBest.fire(null, dialog, memoryMap, "BeatDefendersContinue");
					} else {
						dialog.dismiss();
					}
				} else {
					dialog.dismiss();
				}
			}
		};
		
		dialog.setPlugin(plugin);
		plugin.init(dialog);
	
		return true;
	}
}