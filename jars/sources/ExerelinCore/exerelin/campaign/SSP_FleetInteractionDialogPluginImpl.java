package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.EngagementOutcome;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.FleetMemberData;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.util.Misc;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SSP_FleetInteractionDialogPluginImpl extends FleetInteractionDialogPluginImpl {

    protected static final Color NEUTRAL_COLOR = Global.getSettings().getColor("textNeutralColor");
    protected boolean recoveredOfficers = false;

    public SSP_FleetInteractionDialogPluginImpl() {
        super();
        context = new SSP_FleetEncounterContext();
    }

    public SSP_FleetInteractionDialogPluginImpl(FIDConfig params) {
        super(params);
        context = new SSP_FleetEncounterContext();
    }

    @Override
    public void backFromEngagement(EngagementResultAPI result) {
        super.backFromEngagement(result);

        boolean totalDefeat = !playerFleet.isValidPlayerFleet();
        boolean mutualDestruction = context.getLastEngagementOutcome() == EngagementOutcome.MUTUAL_DESTRUCTION;

        List<OfficerDataAPI> officersEscaped = ((SSP_FleetEncounterContext) context).getPlayerOfficersEscaped();
        List<OfficerDataAPI> officersMIA = ((SSP_FleetEncounterContext) context).getPlayerOfficersMIA();
        List<OfficerDataAPI> officersKIA = ((SSP_FleetEncounterContext) context).getPlayerOfficersKIA();
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
                                 ((SSP_FleetEncounterContext) context).getPlayerRecoverableOfficers();
            List<OfficerDataAPI> lostOfficers = ((SSP_FleetEncounterContext) context).getPlayerLostOfficers();
            List<OfficerDataAPI> unconfirmedOfficers =
                                 ((SSP_FleetEncounterContext) context).getPlayerUnconfirmedOfficers();
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
                                 ((SSP_FleetEncounterContext) context).getPlayerRecoverableOfficers();
            List<OfficerDataAPI> lostOfficers = ((SSP_FleetEncounterContext) context).getPlayerLostOfficers();
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

                ((SSP_FleetEncounterContext) context).recoverPlayerOfficers();
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
        }

        super.winningPath();
    }
}
