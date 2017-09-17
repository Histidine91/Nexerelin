package com.fs.starfarer.api.impl.campaign.rulecmd;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Misc.Token;
import com.fs.starfarer.api.util.Misc.VarAndMemory;
import exerelin.campaign.fleets.ResponseFleetManager;
import java.util.List;
import java.util.Map;

// copied from BroadcastPlayerWaitAction
public class Exerelin_NotifyInvasion extends BaseCommandPlugin {
	public static final String RESPONSE_VARIABLE = "$exerelinRespondingToInvasion";
	public static final String RESPONSE_VARIABLE_RAID = "$exerelinRespondingToRaid";	
	private EveryFrameScript broadcastScript;
	private EveryFrameScript responseFleetScript;
	private VarAndMemory waitHandle;
	
	//BroadcastWaitAction <wait handle> <type> <range> <responseVariable>  
	@Override
	public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Token> params, Map<String, MemoryAPI> memoryMap) {
		waitHandle = params.get(0).getVarNameAndMemory(memoryMap);
		final boolean isRaid = params.get(2).getBoolean(memoryMap);

		//BroadcastPlayerWaitAction $global.exerelinInvasionWait HOSTILE 750 $exerelinRespondingToInvasion
		
		final ActionType type = ActionType.HOSTILE;
		final float range = Float.parseFloat(params.get(1).string);
		
		final SectorEntityToken target = dialog.getInteractionTarget();
		final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		
		final String responseVariable = isRaid ? RESPONSE_VARIABLE_RAID : RESPONSE_VARIABLE;
		
		broadcast(type, range, responseVariable, playerFleet, target);
		broadcastScript = new EveryFrameScript() {
			private IntervalUtil tracker = new IntervalUtil(0.05f, 0.15f);
			private boolean done = false;

			public boolean runWhilePaused() {
				return false;
			}
			public boolean isDone() {
				return done;
			}
			public void advance(float amount) {
				CampaignClockAPI clock = Global.getSector().getClock();
				
				float days = clock.convertToDays(amount);
				tracker.advance(days);
				
				if (tracker.intervalElapsed() && !done) {
					if (waitHandle.memory.contains(waitHandle.name)) {
						Wait wait = (Wait) waitHandle.memory.get(waitHandle.name);
						if (wait.getWaitScript().isDone()) {
							done = true;
							return;
						}
					} else {
						done = true;
						return;
					}
					broadcast(type, range, responseVariable, playerFleet, target);
				}
			}
		};
		
		Global.getSector().addScript(broadcastScript);
		final SectorEntityToken spawnFrom = dialog.getInteractionTarget();
		
		responseFleetScript = new EveryFrameScript() {
            private boolean done = false;
            private float timeElapsed = 0f;
            private final float RESPONSE_DELAY = isRaid ? 0.125f : 0.25f;
                        
            @Override
            public boolean runWhilePaused() {
                return false;
            }
            
			@Override
            public boolean isDone() {
                return done;
            }
            
			@Override
            public void advance(float amount) {
                float days = Global.getSector().getClock().convertToDays(amount);
                timeElapsed += days;
                if (timeElapsed >= RESPONSE_DELAY)
                {
                    CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                    ResponseFleetManager.requestResponseFleet(target.getMarket(), playerFleet, spawnFrom);
                    done = true;
                }
            }
        };
        Global.getSector().addScript(responseFleetScript);
		
		return true;
	}

	public static void broadcast(ActionType type, float range, String responseVariable, 
			SectorEntityToken actor, SectorEntityToken target) {
		broadcast(type, range, responseVariable, actor, target, null);
	}
	
	// same as vanilla one in BroadcastPlayerAction, except sets the hostile memory flag as well
	public static void broadcast(ActionType type, float range, String responseVariable, 
									SectorEntityToken actor, SectorEntityToken target, SectorEntityToken exclude) {
		List<CampaignFleetAPI> fleets = target.getContainingLocation().getFleets();
		for (CampaignFleetAPI fleet : fleets) {
			if (fleet == exclude) continue;
			if (!fleet.getFaction().isHostileTo(Factions.PLAYER)) continue;
			if (fleet.getAI() instanceof CampaignFleetAIAPI) {
				float dist = Misc.getDistance(target.getLocation(), fleet.getLocation());
				if (dist <= range) {
					CampaignFleetAIAPI ai = (CampaignFleetAIAPI) fleet.getAI();
					ai.reportNearbyAction(type, actor, target, responseVariable);
					fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true, 5);
					fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, true, 5);
				}
			}
		}
	}
}
