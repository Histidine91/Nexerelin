package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.BattleAPI.BattleSide;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.EngagementOutcome;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.events.FactionInsuranceEvent;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/*
Changes from vanilla:
- Officer death mechanic
- Modified pull in of fleets for ally battles
- Fleet encounter context contains DS's fix for custom variant salvaging
*/

public class NexFleetInteractionDialogPluginImpl extends FleetInteractionDialogPluginImpl {

    protected static final Color NEUTRAL_COLOR = Global.getSettings().getColor("textNeutralColor");
    protected boolean recoveredOfficers = false;
	protected FIDConfig config;	// vanilla one is private

    public NexFleetInteractionDialogPluginImpl() {
        super();
        context = new NexFleetEncounterContext();
    }

    public NexFleetInteractionDialogPluginImpl(FIDConfig params) {
        super(params);
        this.config = params;
        context = new NexFleetEncounterContext();
    }

    @Override
    public void backFromEngagement(EngagementResultAPI result) {
        super.backFromEngagement(result);

        boolean totalDefeat = !playerFleet.isValidPlayerFleet();
        boolean mutualDestruction = context.getLastEngagementOutcome() == EngagementOutcome.MUTUAL_DESTRUCTION;

        List<OfficerDataAPI> officersEscaped = ((NexFleetEncounterContext) context).getPlayerOfficersEscaped();
        List<OfficerDataAPI> officersMIA = ((NexFleetEncounterContext) context).getPlayerOfficersMIA();
        List<OfficerDataAPI> officersKIA = ((NexFleetEncounterContext) context).getPlayerOfficersKIA();
        if (!officersEscaped.isEmpty() && !totalDefeat && !mutualDestruction) {
            List<String> escaped = new ArrayList<>(officersEscaped.size());
            for (OfficerDataAPI officer : officersEscaped) {
                escaped.add(officer.getPerson().getName().getFullName());
            }

            String s1, s2;
            if (escaped.size() == 1) {
                s1 = "officer";
                if (officersEscaped.get(0).getPerson().getGender() == Gender.MALE) {
                    s2 = "his ship";
                } else if (officersEscaped.get(0).getPerson().getGender() == Gender.FEMALE) {
                    s2 = "her ship";
                } else {
                    s2 = "its ship";
                }
            } else {
                s1 = "officers";
                s2 = "their ships";
            }
            addText("Your " + s1 + " " + Misc.getAndJoined(escaped.toArray(new String[escaped.size()])) +
                    " escaped the destruction of " + s2 + ".");
        }
        if ((!officersMIA.isEmpty() || !officersKIA.isEmpty()) && !totalDefeat && !mutualDestruction) {
            int lost = officersMIA.size() + officersKIA.size();

            String text;
            String s1;
            if (officersKIA.isEmpty()) {
                if (lost == 1) {
                    s1 = "Your officer";
                } else {
                    s1 = "Your officers";
                }
                text = s1 + " did not report in after the battle:";
            } else {
                if (lost == 1) {
                    s1 = "Your officer was";
                } else {
                    s1 = "Your officers were";
                }
                text = s1 + " listed among the casualties:";
            }

            List<String> highlights = new ArrayList<>((officersMIA.size() + officersKIA.size()) * 2);
            List<Color> highlightColors = new ArrayList<>((officersMIA.size() + officersKIA.size()) * 2);
            for (OfficerDataAPI officer : officersMIA) {
                s1 = officer.getPerson().getName().getFullName();
                String s2 = "missing in action";
                text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
                highlights.add(s1);
                highlights.add(s2);
                highlightColors.add(NEUTRAL_COLOR);
                highlightColors.add(ENEMY_COLOR);
            }

            for (OfficerDataAPI officer : officersKIA) {
                s1 = officer.getPerson().getName().getFullName();
                String s2 = "killed in action";
                text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
                highlights.add(s1);
                highlights.add(s2);
                highlightColors.add(NEUTRAL_COLOR);
                highlightColors.add(ENEMY_COLOR);
            }

            addText(text);
            textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
            textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));
        }
    }

    @Override
    protected boolean fleetHoldingVsStrongerEnemy(CampaignFleetAPI fleet, CampaignFleetAPI other) {
        if (fleet.getFaction().getId().contentEquals("famous_bounty")) {
            fleet.updateCounts();
            FleetMemberAPI flagship = (FleetMemberAPI) fleet.getMemoryWithoutUpdate().get("$famousFlagship");
            if (flagship == fleet.getFlagship()) {
                if (flagship.getRepairTracker().getCR() < 0.5f) {
                    return super.fleetHoldingVsStrongerEnemy(fleet, other);
                }
                List<FleetMemberData> casualties = context.getDataFor(fleet).getOwnCasualties();
                for (FleetMemberData casualty : casualties) {
                    if (casualty.getMember() == flagship) {
                        return super.fleetHoldingVsStrongerEnemy(fleet, other);
                    }
                }
                return !super.fleetWantsToFight(fleet, other);
            } else {
                return super.fleetHoldingVsStrongerEnemy(fleet, other);
            }
        } else {
            return super.fleetHoldingVsStrongerEnemy(fleet, other);
        }
    }

    @Override
    protected boolean fleetWantsToDisengage(CampaignFleetAPI fleet, CampaignFleetAPI other) {
        if (fleet.getFaction().getId().contentEquals("famous_bounty")) {
            fleet.updateCounts();
            FleetMemberAPI flagship = (FleetMemberAPI) fleet.getMemoryWithoutUpdate().get("$famousFlagship");
            if (flagship == fleet.getFlagship()) {
                if (flagship.getRepairTracker().getCR() < 0.5f) {
                    if (possibleToEngage(fleet, other)) {
                        return super.fleetWantsToDisengage(fleet, other);
                    } else {
                        return true;
                    }
                }
                List<FleetMemberData> casualties = context.getDataFor(fleet).getOwnCasualties();
                for (FleetMemberData casualty : casualties) {
                    if (casualty.getMember() == flagship) {
                        if (possibleToEngage(fleet, other)) {
                            return super.fleetWantsToDisengage(fleet, other);
                        } else {
                            return true;
                        }
                    }
                }
                return false;
            } else {
                if (possibleToEngage(fleet, other)) {
                    return super.fleetWantsToDisengage(fleet, other);
                } else {
                    return true;
                }
            }
        } else {
            if (possibleToEngage(fleet, other)) {
                return super.fleetWantsToDisengage(fleet, other);
            } else {
                return true;
            }
        }
    }

    @Override
    protected boolean fleetWantsToFight(CampaignFleetAPI fleet, CampaignFleetAPI other) {
        if (possibleToEngage(fleet, other)) {
            return super.fleetWantsToFight(fleet, other);
        } else {
            return false;
        }
    }

    @Override
    protected void losingPath() {
        if (!recoveredOfficers) {
            recoveredOfficers = true;

            List<OfficerDataAPI> recoverableOfficers =
                                 ((NexFleetEncounterContext) context).getPlayerRecoverableOfficers();
            List<OfficerDataAPI> lostOfficers = ((NexFleetEncounterContext) context).getPlayerLostOfficers();
            List<OfficerDataAPI> unconfirmedOfficers =
                                 ((NexFleetEncounterContext) context).getPlayerUnconfirmedOfficers();
            if (!lostOfficers.isEmpty() || !recoverableOfficers.isEmpty()) {
                String s1;
                if (lostOfficers.size() + recoverableOfficers.size() == 1) {
                    s1 = "your officer didn't make it";
                } else {
                    s1 = "your officers didn't make it";
                }
                String text = "The post-action report confirms that " + s1 + ":";

                List<String> highlights = new ArrayList<>((lostOfficers.size() + recoverableOfficers.size()) * 2);
                List<Color> highlightColors = new ArrayList<>((lostOfficers.size() + recoverableOfficers.size()) * 2);
                for (OfficerDataAPI officer : lostOfficers) {
                    s1 = officer.getPerson().getName().getFullName();
                    String s2;
                    if (unconfirmedOfficers.contains(officer)) {
                        s2 = "MIA, presumed dead";
                    } else {
                        s2 = "killed in action";
                    }
                    text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
                    highlights.add(s1);
                    highlights.add(s2);
                    highlightColors.add(NEUTRAL_COLOR);
                    highlightColors.add(ENEMY_COLOR);
                }
                for (OfficerDataAPI officer : recoverableOfficers) {
                    s1 = officer.getPerson().getName().getFullName();
                    String s2 = "MIA, presumed dead";
                    text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
                    highlights.add(s1);
                    highlights.add(s2);
                    highlightColors.add(NEUTRAL_COLOR);
                    highlightColors.add(ENEMY_COLOR);
                }

                addText(text);
                textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
                textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));
				
				if (Global.getSector().getEventManager().isOngoing(null, "exerelin_faction_insurance"))
				{
					FactionInsuranceEvent event = (FactionInsuranceEvent)Global.getSector().getEventManager().getOngoingEvent(
							null, "exerelin_faction_insurance");
					event.addDeadOfficers(lostOfficers);
					event.addDeadOfficers(recoverableOfficers);
				}
            }
        }

        super.losingPath();
    }

    protected boolean possibleToEngage(CampaignFleetAPI fleet, CampaignFleetAPI other) {
        boolean hasSomethingToFightWith = false;
        for (FleetMemberAPI member : fleet.getFleetData().getCombatReadyMembersListCopy()) {
            if (member.isCivilian() || member.isMothballed() || member.getRepairTracker().getBaseCR() <= 0.1f) {
                continue;
            }
            hasSomethingToFightWith = true;
            break;
        }
        return hasSomethingToFightWith;
    }

    @Override
    protected void winningPath() {
        if (!recoveredOfficers) {
            recoveredOfficers = true;

            List<OfficerDataAPI> recoverableOfficers =
                                 ((NexFleetEncounterContext) context).getPlayerRecoverableOfficers();
            List<OfficerDataAPI> lostOfficers = ((NexFleetEncounterContext) context).getPlayerLostOfficers();
            if (!recoverableOfficers.isEmpty()) {
                String s1;
                if (recoverableOfficers.size() == 1) {
                    s1 = "Your officer was";
                } else {
                    s1 = "Your officers were";
                }
                String text = s1 + " saved from the wreckage:";

                List<String> highlights = new ArrayList<>(recoverableOfficers.size() * 2);
                List<Color> highlightColors = new ArrayList<>(recoverableOfficers.size() * 2);
                for (OfficerDataAPI officer : recoverableOfficers) {
                    s1 = officer.getPerson().getName().getFullName();
                    String s2 = "rescued";
                    text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
                    highlights.add(s1);
                    highlights.add(s2);
                    highlightColors.add(NEUTRAL_COLOR);
                    highlightColors.add(FRIEND_COLOR);
                }

                addText(text);
                textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
                textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));

                ((NexFleetEncounterContext) context).recoverPlayerOfficers();
            }

            if (!lostOfficers.isEmpty()) {
                String s1;
                if (lostOfficers.size() == 1) {
                    s1 = "your officer didn't make it";
                } else {
                    s1 = "your officers didn't make it";
                }
                String text = "The post-action report confirms that " + s1 + ":";

                List<String> highlights = new ArrayList<>(lostOfficers.size() * 2);
                List<Color> highlightColors = new ArrayList<>(lostOfficers.size() * 2);
                for (OfficerDataAPI officer : lostOfficers) {
                    s1 = officer.getPerson().getName().getFullName();
                    String s2 = "killed in action";
                    text += "\n" + s1 + " (" + officer.getPerson().getStats().getLevel() + ") - " + s2;
                    highlights.add(s1);
                    highlights.add(s2);
                    highlightColors.add(NEUTRAL_COLOR);
                    highlightColors.add(ENEMY_COLOR);
                }

                addText(text);
                textPanel.highlightInLastPara(highlights.toArray(new String[highlights.size()]));
                textPanel.setHighlightColorsInLastPara(highlightColors.toArray(new Color[highlightColors.size()]));
            }
			
			if (Global.getSector().getEventManager().isOngoing(null, "exerelin_faction_insurance"))
			{
				FactionInsuranceEvent event = (FactionInsuranceEvent)Global.getSector().getEventManager().getOngoingEvent(
						null, "exerelin_faction_insurance");
				event.addDeadOfficers(lostOfficers);
			}
        }

        super.winningPath();
    }
    
	@Override
	public void init(InteractionDialogAPI dialog) {		
		if (this.config == null) {
			MemoryAPI memory = dialog.getInteractionTarget().getMemoryWithoutUpdate();
//			if (memory.contains(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE)) {
//				this.config = (FIDConfig) memory.get(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE);
//			} else 
			if (memory.contains(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN)) {
				this.config = ((FIDConfigGen) memory.get(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN)).createConfig();
			} else {
				this.config = new FIDConfig();
			}
		}
		super.init(dialog);
	}
	
	protected boolean getOngoingBattle()
	{
		if (context.getBattle() == null) {
			if (otherFleet.getBattle() == null || otherFleet.getBattle().isDone()) {
				return false;
			} else {
				return true;
			}
		}
		return false;
	}
	
	// same as vanilla, except stations don't get pulled + anything pursuing a participating fleet gets pulled
	protected boolean shouldPullInFleet(BattleAPI battle, CampaignFleetAPI fleet, float dist)
	{
		Global.getLogger(this.getClass()).info("Testing fleet " + fleet.getName());
		float baseSensorRange = playerFleet.getBaseSensorRangeToDetect(fleet.getSensorProfile());
		boolean visible = fleet.isVisibleToPlayerFleet();
		VisibilityLevel level = fleet.getVisibilityLevelToPlayerFleet();
//			if (dist < Misc.getBattleJoinRange() && 
//					(dist < baseSensorRange || (visible && level != VisibilityLevel.SENSOR_CONTACT))) {
//				System.out.println("2380dfwef");
//			}
		if (dist > Misc.getBattleJoinRange())
			return false;
		if (dist > baseSensorRange && !(visible && level != VisibilityLevel.SENSOR_CONTACT))
			return false;
		if (fleet.getAI() != null && fleet.getAI().wantsToJoin(battle, true))
			return true;
		if (fleet.isStationMode())
			return false;	// screw Alex's logic on this
		
		if (fleet.getAI() != null)
		{
			FleetAssignmentDataAPI assignment = fleet.getAI().getCurrentAssignment();
			SectorEntityToken target = assignment.getTarget();
			if (target != null && target instanceof CampaignFleetAPI)
			{
				List<CampaignFleetAPI> fleets = battle.getBothSides();
				for (CampaignFleetAPI inBattle : fleets)
				{
					if (inBattle == target)
						return true;
				}
			}
		}
		return false;
	}
	
	// vanilla with modified pulling in of nearby fleets
	@Override
	protected void pullInNearbyFleets() {
		BattleAPI b = context.getBattle();
		
		BattleSide playerSide = b.pickSide(Global.getSector().getPlayerFleet());
		
		boolean hostile = otherFleet.getAI() != null && otherFleet.getAI().isHostileTo(playerFleet);
		boolean ongoingBattle = getOngoingBattle();
		if (ongoingBattle) hostile = true;
		
		//canDecline = otherFleet.getAI() != null && other
		
//		boolean someJoined = false;
		CampaignFleetAPI actualPlayer = Global.getSector().getPlayerFleet();
		CampaignFleetAPI actualOther = (CampaignFleetAPI) (dialog.getInteractionTarget());
		
		//textPanel.addParagraph("Projecting nearby fleet movements:");
		//textPanel.addParagraph("You encounter a ");
		pulledIn.clear();
		
		for (CampaignFleetAPI fleet : actualPlayer.getContainingLocation().getFleets()) {
			if (b == fleet.getBattle()) continue;
			if (fleet.getBattle() != null) continue;
			
			float dist = Misc.getDistance(actualOther.getLocation(), fleet.getLocation());
			dist -= actualOther.getRadius();
			dist -= fleet.getRadius();
//			if (dist < Misc.getBattleJoinRange()) {
//				System.out.println("Checking: " + fleet.getNameWithFaction());
//			}
			
			if (shouldPullInFleet(b, fleet, dist)) {
				
				BattleSide joiningSide = b.pickSide(fleet, true);
				if (!config.pullInAllies && joiningSide == playerSide) continue;
				if (!config.pullInEnemies && joiningSide != playerSide) continue;
				
				b.join(fleet);
				pulledIn.add(fleet);
				//if (b.isPlayerSide(b.getSideFor(fleet))) {
				if (b.getSide(playerSide) == b.getSideFor(fleet)) {
					textPanel.addParagraph(Misc.ucFirst(fleet.getNameWithFactionKeepCase()) + ": supporting your forces.");//, FRIEND_COLOR);
				} else {
					if (hostile) {
						textPanel.addParagraph(Misc.ucFirst(fleet.getNameWithFactionKeepCase()) + ": joining the enemy.");//, ENEMY_COLOR);
					} else {
						textPanel.addParagraph(Misc.ucFirst(fleet.getNameWithFactionKeepCase()) + ": supporting the opposing side.");
					}
				}
				textPanel.highlightFirstInLastPara(fleet.getNameWithFactionKeepCase() + ":", fleet.getFaction().getBaseUIColor());
//				someJoined = true;
			}
		}
//		if (!someJoined) {
//			addText("No nearby fleets will join the battle.");
//		}
		if (!ongoingBattle) {
			b.genCombined();
			b.takeSnapshots();
			playerFleet = b.getPlayerCombined();
			otherFleet = b.getNonPlayerCombined();
			showFleetInfo();
		}
		
	}
}
