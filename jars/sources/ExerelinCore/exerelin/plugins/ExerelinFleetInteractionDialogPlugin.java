package exerelin.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DisengageHarryAvailability;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.PursueAvailability;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.impl.campaign.DevMenuOptions;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import exerelin.campaign.PlayerFactionStore;

/**
 *
 * @author Rattlesnark
 */
public class ExerelinFleetInteractionDialogPlugin extends FleetInteractionDialogPluginImpl {
        
        // same as vanilla, but disallows fighting your own faction fleets (unless they're already hostile for some other reason)
        @Override
        protected void updateMainState() {
		options.clearOptions();
		
		if (isFightingOver()) {
			goToEncounterEndPath();
			return;
		}
		
		options.addOption("Open a comm link", OptionId.OPEN_COMM, null);
                
                FactionAPI otherFaction = otherFleet.getFaction();
		boolean isPlayerAlignedFaction = otherFaction.getId().equals(PlayerFactionStore.getPlayerFactionId());
                
		boolean otherWantsToRun = otherFleetWantsToDisengage() && otherCanDisengage();
		
		boolean playerHasReadyShips = !playerFleet.getFleetData().getCombatReadyMembersListCopy().isEmpty();
                
                CampaignFleetAIAPI ai = otherFleet.getAI();
                boolean hostile = false;
                if (ai != null) {
                        hostile = ai.isHostileTo(playerFleet) || context.isEngagedInHostilities();
                }
                
		if (otherWantsToRun && canDisengageCleanly(otherFleet)) {
			addText(getString("enemyCleanDisengage"));
			goToEncounterEndPath();
		} else if (otherWantsToRun) {
			String pursueTooltip = "tooltipPursue";
			String harassTooltip = "tooltipHarassRetreat";
			String letThemGoTooltip = "tooltipLetThemGo";
			if (!context.isEngagedInHostilities()) {
				letThemGoTooltip = "tooltipLetThemGoNoPenalty";
			}
			boolean canPursue = false;
			boolean canHasass = false;
			
			PursueAvailability pa = context.getPursuitAvailability(playerFleet, otherFleet);
			DisengageHarryAvailability dha = context.getDisengageHarryAvailability(playerFleet, otherFleet);
			
			switch (pa) {
			case AVAILABLE:
				canPursue = true;
				break;
			case LOST_LAST_ENGAGEMENT:
				pursueTooltip = "tooltipPursueLostLast";
				break;
			case NO_READY_SHIPS:
				pursueTooltip = "tooltipNoReadyShips";
				break;
			case STOOD_DOWN:
				pursueTooltip = "tooltipPursueStoodDown";
				break;
			case TOOK_SERIOUS_LOSSES:
				pursueTooltip = "tooltipPursueSeriousLosses";
				break;
			case TOO_SLOW:
				pursueTooltip = "tooltipPursueTooSlow";
				break;
			}
			switch (dha) {
			case AVAILABLE:
				canHasass = true;
				break;
			case NO_READY_SHIPS:
				harassTooltip = "tooltipNoReadyShips";
				break;
			}		
			
                        if (!isPlayerAlignedFaction || hostile)
                        {
                                options.addOption("Pursue them", OptionId.PURSUE, getString(pursueTooltip));
                                options.addOption("Harry their retreat", OptionId.HARRY_PURSUE, getString(harassTooltip));
                        }
			if (hostile) {
				options.addOption("Let them go", OptionId.LET_THEM_GO, getString(letThemGoTooltip));
			} else {
				options.addOption("Leave", OptionId.LEAVE, null);
			}
			
			if (!canPursue) {
				options.setEnabled(OptionId.PURSUE, false);
			}
			if (!canHasass) {
				options.setEnabled(OptionId.HARRY_PURSUE, false);
			}
		} else {
                        if (!isPlayerAlignedFaction || hostile || otherFleetWantsToFight())
                        {
                            if (playerHasReadyShips) {
                                    options.addOption("Move in to engage", OptionId.ENGAGE, getString("tooltipEngage"));
                            } else {
                                    options.addOption("Move in to engage", OptionId.ENGAGE, getString("tooltipNoReadyShips"));
                                    options.setEnabled(OptionId.ENGAGE, false);
                            }
                        }
			if (otherFleetWantsToFight() || (hostile && !otherFleetWantsToDisengage())) {
				if (canDisengageCleanly(playerFleet)) {
					options.addOption("Disengage", OptionId.DISENGAGE, getString("tooltipCleanDisengage"));
//				} else if (canDisengageWithoutPursuit(playerFleet) || !otherFleetWantsToFight()) {
//					options.addOption("Disengage", OptionId.DISENGAGE, getString("tooltipHarrassableDisengage"));
				} else {
					if (otherFleetHoldingVsStrongerEnemy() || (!otherFleetWantsToFight() && !otherFleetWantsToDisengage())) {
						options.addOption("Leave", OptionId.LEAVE, null);
					} else {
						if (canDisengage() || !playerHasReadyShips) {
							options.addOption("Attempt to disengage", OptionId.ATTEMPT_TO_DISENGAGE, getString("tootipAttemptToDisengage"));
						} else {
							addText(getString("playerTooLargeToDisengage"));
							//options.addOption("Scuttle some of your ships", OptionId.SCUTTLE, null);
						}
					}
				}
			} else {
				//goToEncounterEndPath();
				options.addOption("Leave", OptionId.LEAVE, null);
			}
		}
		
		if (Global.getSettings().isDevMode()) {
			DevMenuOptions.addOptions(dialog);
		}
	}
}
