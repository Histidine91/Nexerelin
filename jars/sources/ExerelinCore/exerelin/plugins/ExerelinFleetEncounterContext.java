package exerelin.plugins;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.PostEngagementOption;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.PursuitOption;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinUtils;
import exerelin.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinMessageManager;
import exerelin.utilities.ExerelinUtilsMessaging;
import org.lwjgl.Sys;

public class ExerelinFleetEncounterContext implements FleetEncounterContextPlugin {

    private List<DataForEncounterSide> sideData = new ArrayList<DataForEncounterSide>();
    private boolean engagedInHostilities = false;

    public DataForEncounterSide getDataFor(CampaignFleetAPI fleet) {
        for (DataForEncounterSide curr : sideData) {
            if (curr.getFleet() == fleet) return curr;
        }
        DataForEncounterSide dfes = new DataForEncounterSide(fleet);
        sideData.add(dfes);

        return dfes;
    }

    public DataForEncounterSide getWinnerData() {
        for (DataForEncounterSide curr : sideData) {
            if (!curr.disengaged()) {
                return curr;
            }
        }
        return null;
    }

    public DataForEncounterSide getLoserData() {
        for (DataForEncounterSide curr : sideData) {
            if (curr.disengaged()) {
                return curr;
            }
        }
        return null;
    }

    public boolean isEngagedInHostilities() {
        return engagedInHostilities;
    }

    public void setEngagedInHostilities(boolean engagedInHostilities) {
        this.engagedInHostilities = engagedInHostilities;
    }

    private void updateDeployedMap(EngagementResultForFleetAPI result) {
        DataForEncounterSide data = getDataFor(result.getFleet());
        data.getMemberToDeployedMap().clear();

        List<DeployedFleetMemberAPI> deployed = result.getAllEverDeployedCopy();
        if (deployed != null && !deployed.isEmpty()) {
            for (DeployedFleetMemberAPI dfm : deployed) {
                if (dfm.getMember() != null) {
                    data.getMemberToDeployedMap().put(dfm.getMember(), dfm);
                }
            }
        }
    }

    public void processEngagementResults(EngagementResultAPI result) {
        engagedInHostilities = true;

        EngagementResultForFleetAPI winnerResult = result.getWinnerResult();
        EngagementResultForFleetAPI loserResult = result.getLoserResult();
        updateDeployedMap(winnerResult);
        updateDeployedMap(loserResult);

        //result.applyToFleets();
        applyResultToFleets(result);




        DataForEncounterSide winnerData = getDataFor(winnerResult.getFleet());
        DataForEncounterSide loserData = getDataFor(loserResult.getFleet());

        winnerData.setWonLastEngagement(true);
        loserData.setWonLastEngagement(false);

        winnerData.setDidEnoughToDisengage(true);
        float damageInFP = 0f;
        for (FleetMemberAPI member : winnerResult.getDisabled()) {
            damageInFP += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : winnerResult.getDestroyed()) {
            damageInFP += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : winnerResult.getRetreated()) {
            damageInFP += member.getFleetPointCost();
        }

        float remaining = 0f;
        for (FleetMemberAPI member : winnerResult.getFleet().getFleetData().getCombatReadyMembersListCopy()) {
            remaining += member.getFleetPointCost();
        }
        loserData.setDidEnoughToDisengage(damageInFP >= remaining);


        winnerData.setLastGoal(winnerResult.getGoal());
        loserData.setLastGoal(loserResult.getGoal());

        winnerData.getDeployedInLastEngagement().clear();
        winnerData.getRetreatedFromLastEngagement().clear();
        winnerData.getInReserveDuringLastEngagement().clear();
        winnerData.getDisabledInLastEngagement().clear();
        winnerData.getDestroyedInLastEngagement().clear();
        winnerData.getDeployedInLastEngagement().addAll(winnerResult.getDeployed());
        winnerData.getRetreatedFromLastEngagement().addAll(winnerResult.getRetreated());
        winnerData.getInReserveDuringLastEngagement().addAll(winnerResult.getReserves());
        winnerData.getDisabledInLastEngagement().addAll(winnerResult.getDisabled());
        winnerData.getDestroyedInLastEngagement().addAll(winnerResult.getDestroyed());

        loserData.getDeployedInLastEngagement().clear();
        loserData.getRetreatedFromLastEngagement().clear();
        loserData.getInReserveDuringLastEngagement().clear();
        loserData.getDisabledInLastEngagement().clear();
        loserData.getDestroyedInLastEngagement().clear();
        loserData.getDeployedInLastEngagement().addAll(loserResult.getDeployed());
        loserData.getRetreatedFromLastEngagement().addAll(loserResult.getRetreated());
        loserData.getInReserveDuringLastEngagement().addAll(loserResult.getReserves());
        loserData.getDisabledInLastEngagement().addAll(loserResult.getDisabled());
        loserData.getDestroyedInLastEngagement().addAll(loserResult.getDestroyed());

        for (FleetMemberAPI member : loserResult.getDestroyed()) {
            loserData.addOwn(member, Status.DESTROYED);
        }

        for (FleetMemberAPI member : loserResult.getDisabled()) {
            loserData.addOwn(member, Status.DISABLED);
        }

        for (FleetMemberAPI member : winnerResult.getDestroyed()) {
            winnerData.addOwn(member, Status.DESTROYED);
        }

        for (FleetMemberAPI member : winnerResult.getDisabled()) {
            winnerData.addOwn(member, Status.DISABLED);
        }

        standDownRecoveryFraction = computeStandDownRecoveryFraction(result);

        // important, so that in-combat Ship objects can be garbage collected.
        // Probably some combat engine references in there, too.
        winnerResult.resetAllEverDeployed();
        getDataFor(winnerResult.getFleet()).getMemberToDeployedMap().clear();
        loserResult.resetAllEverDeployed();
        getDataFor(loserResult.getFleet()).getMemberToDeployedMap().clear();



        FleetGoal winnerGoal = winnerResult.getGoal();
        FleetGoal loserGoal = loserResult.getGoal();
        boolean totalWin = loserData.getFleet().getFleetData().getMembersListCopy().isEmpty();
        if (totalWin && winnerData.getFleet().getFleetData().getMembersListCopy().isEmpty()) {
            lastOutcome = EngagementOutcome.MUTUAL_DESTRUCTION;
        } else {
            if (winnerResult.getFleet() == Global.getSector().getPlayerFleet()) {
                if (winnerGoal == FleetGoal.ATTACK && loserGoal == FleetGoal.ATTACK) {
                    if (totalWin) {
                        lastOutcome = EngagementOutcome.BATTLE_PLAYER_WIN_TOTAL;
                    } else {
                        lastOutcome = EngagementOutcome.BATTLE_PLAYER_WIN;
                    }
                } else if (winnerGoal == FleetGoal.ESCAPE) {
                    if (totalWin) {
                        lastOutcome = EngagementOutcome.ESCAPE_PLAYER_WIN_TOTAL;
                    } else {
                        lastOutcome = EngagementOutcome.ESCAPE_PLAYER_WIN;
                    }
                } else if (loserGoal == FleetGoal.ESCAPE) {
                    if (totalWin) {
                        lastOutcome = EngagementOutcome.ESCAPE_ENEMY_LOSS_TOTAL;
                    } else {
                        lastOutcome = EngagementOutcome.ESCAPE_ENEMY_SUCCESS;
                    }
                }
            } else {
                if (winnerGoal == FleetGoal.ATTACK && loserGoal == FleetGoal.ATTACK) {
                    if (totalWin) {
                        lastOutcome = EngagementOutcome.BATTLE_ENEMY_WIN_TOTAL;
                    } else {
                        lastOutcome = EngagementOutcome.BATTLE_ENEMY_WIN;
                    }
                } else if (winnerGoal == FleetGoal.ESCAPE) {
                    if (totalWin) {
                        lastOutcome = EngagementOutcome.ESCAPE_ENEMY_WIN_TOTAL;
                    } else {
                        lastOutcome = EngagementOutcome.ESCAPE_ENEMY_WIN;
                    }
                } else if (loserGoal == FleetGoal.ESCAPE) {
                    if (totalWin) {
                        lastOutcome = EngagementOutcome.ESCAPE_PLAYER_LOSS_TOTAL;
                    } else {
                        lastOutcome = EngagementOutcome.ESCAPE_PLAYER_SUCCESS;
                    }
                }
            }
        }
    }

    private float standDownRecoveryFraction = 0f;

//	public boolean canPursue(CampaignFleetAPI fleet) {
//		return !fleetStoodDownAfterLastEngagement(fleet);
//	}

    public PursueAvailability getPursuitAvailability(CampaignFleetAPI fleet, CampaignFleetAPI otherFleet) {
        DataForEncounterSide otherData = getDataFor(otherFleet);

        if (otherData.isWonLastEngagement()) return PursueAvailability.LOST_LAST_ENGAGEMENT;
        if (fleetStoodDownAfterLastEngagement(fleet)) return PursueAvailability.STOOD_DOWN;
        if (canOutrunOtherFleet(otherFleet, fleet)) return PursueAvailability.TOO_SLOW;
        if (fleet.getFleetData().getCombatReadyMembersListCopy().isEmpty()) return PursueAvailability.NO_READY_SHIPS;
        if (otherData.isDidEnoughToDisengage()) return PursueAvailability.TOOK_SERIOUS_LOSSES;
        return PursueAvailability.AVAILABLE;
    }

    public DisengageHarryAvailability getDisengageHarryAvailability(CampaignFleetAPI fleet, CampaignFleetAPI otherFleet) {
        DataForEncounterSide otherData = getDataFor(otherFleet);
        if (otherData.isWonLastEngagement()) return DisengageHarryAvailability.LOST_LAST_ENGAGEMENT;
        if (fleet.getFleetData().getCombatReadyMembersListCopy().isEmpty()) return DisengageHarryAvailability.NO_READY_SHIPS;
        return DisengageHarryAvailability.AVAILABLE;
    }

    public boolean fleetStoodDownAfterLastEngagement(CampaignFleetAPI fleet) {
        return fleet == stoodDownAfterEngagement;
    }

    public float getStandDownRecoveryFraction() {
        return standDownRecoveryFraction;
    }

    private CampaignFleetAPI stoodDownAfterEngagement = null;

    public void applyPostEngagementOption(EngagementResultAPI result, PostEngagementOption winnerOption) {
        EngagementResultForFleetAPI winnerResult = result.getWinnerResult();
        EngagementResultForFleetAPI loserResult = result.getLoserResult();

        DataForEncounterSide winnerData = getDataFor(winnerResult.getFleet());
        DataForEncounterSide loserData = getDataFor(loserResult.getFleet());

        winnerData.setLastWinOption(winnerOption);
        loserData.setLastWinOption(null);

        stoodDownAfterEngagement = null;

        if (winnerOption == PostEngagementOption.SALVAGE) {
            for (FleetMemberAPI member : loserResult.getDestroyed()) {
                winnerData.addEnemy(member, Status.SALVAGE_DESTROYED);
            }
            for (FleetMemberAPI member : loserResult.getDisabled()) {
                winnerData.addEnemy(member, Status.SALVAGE_DISABLED);
            }

            for (FleetMemberAPI member : winnerResult.getDestroyed()) {
                winnerData.changeOwn(member, Status.SALVAGE_DESTROYED);
            }

            for (FleetMemberAPI member : winnerResult.getDisabled()) {
                winnerData.changeOwn(member, Status.SALVAGE_DISABLED);
            }
        } else {
            for (FleetMemberAPI member : loserResult.getDestroyed()) {
                winnerData.addEnemy(member, Status.DESTROYED);
            }
            for (FleetMemberAPI member : loserResult.getDisabled()) {
                winnerData.addEnemy(member, Status.DISABLED);
            }
        }

        if (winnerOption == PostEngagementOption.HARRY) {
            for (FleetMemberAPI member : loserData.getInReserveDuringLastEngagement()) {
                float deployCost = getDeployCost(member);

                float harryCost = deployCost * 1f;
                member.getRepairTracker().applyCREvent(-harryCost, "harried during retreat from lost engagement");
            }
        } else if (winnerOption == PostEngagementOption.STAND_DOWN) {
            stoodDownAfterEngagement = winnerData.getFleet();

            //float recoveryFraction = getStandDownRecoveryFraction(result);

            for (FleetMemberAPI member : winnerData.getDeployedInLastEngagement()) {
                float deployCost = getDeployCost(member);

                float recoveryAmount = deployCost * standDownRecoveryFraction;

                float prevCR = 1f;
                if (preEngagementCRForWinner.containsKey(member)) {
                    prevCR = preEngagementCRForWinner.get(member);
                }

                if (prevCR < deployCost) {
                    recoveryAmount = prevCR * standDownRecoveryFraction;
                }

//				float noDeployThreshold = Global.getSettings().getFloat("noDeployCRPercent") * 0.01f;
//				float cr = member.getRepairTracker().getCR();
//				if (cr + recoveryAmount > prevCR) {
//					recoveryAmount = noDeployThreshold - cr;
//				}

                member.getRepairTracker().applyCREvent(recoveryAmount, "stood down immediately after engagement");
            }
        }
    }

    public float getDeployCost(FleetMemberAPI member) {
        return member.getDeployCost();
//		float deployCost = member.getStats().getCRPerDeploymentPercent().computeEffective(member.getHullSpec().getCRToDeploy()) / 100f;
//		if (member.isFighterWing()) {
//			deployCost *= (float) member.getNumFightersInWing();
//		}
//		return deployCost;
    }


    public void applyAfterBattleEffectsIfThereWasABattle() {
        if (!hasWinnerAndLoser() || !engagedInHostilities) {
            return;
        }

        gainXP();

        CampaignFleetAPI winner = getWinnerData().getFleet();
        CampaignFleetAPI loser = getLoserData().getFleet();

        loser.setNoEngaging(3f);
        winner.setNoEngaging(3f);

        loser.getVelocity().set(0, 0);
        winner.getVelocity().set(0, 0);


        float maxBurn = winner.getFleetData().getBurnLevel();
        if (maxBurn >= 2) {
            winner.getStats().addTemporaryModFlat(0.5f, "winner_slowdown", "Reorganizing",
                    -(maxBurn - 1), winner.getStats().getFleetwideMaxBurnMod());
        }

        loser.getStats().addTemporaryModFlat(0.5f, "loser_speedup", "Emergency speed",
                5f, loser.getStats().getFleetwideMaxBurnMod());



        if (loser == Global.getSector().getPlayerFleet()) {
            Global.getSector().getCampaignUI().addMessage("Your fleet puts on emergency speed to get away");
        }

        if (loser == Global.getSector().getPlayerFleet() || winner == Global.getSector().getPlayerFleet()) {
            CampaignFleetAPI playerFleet, enemyFleet;
            if (didPlayerWinEncounter()) {
                playerFleet = getWinner();
                enemyFleet = getLoser();
            } else {
                enemyFleet = getWinner();
                playerFleet = getLoser();
            }

            playerFleet.setMoveDestination(playerFleet.getLocation().x, playerFleet.getLocation().y);

            float repChange = -0.05f;
            if (didPlayerWinEncounter()) repChange = -0.1f;

            playerFleet.getFaction().adjustRelationship(enemyFleet.getFaction().getId(), repChange);
            float rep = playerFleet.getFaction().getRelationship(enemyFleet.getFaction().getId());
            if (rep >= 0) {
                playerFleet.getFaction().setRelationship(enemyFleet.getFaction().getId(), -0.01f);
            }

            Color color = Global.getSettings().getColor("textEnemyColor");
            Global.getSector().getCampaignUI().addMessage("Your relationship with the " + enemyFleet.getFaction().getDisplayName() + " faction has deteriorated", color);
        }

        //Global.getSector().setPaused(true);
    }


    public float computeStandDownRecoveryFraction(EngagementResultAPI result) {
        EngagementResultForFleetAPI winnerResult = result.getWinnerResult();
        EngagementResultForFleetAPI loserResult = result.getLoserResult();

        DataForEncounterSide winnerData = getDataFor(winnerResult.getFleet());
        DataForEncounterSide loserData = getDataFor(loserResult.getFleet());

        float totalFpUsed = 0f;
        for (FleetMemberAPI member : winnerData.getDeployedInLastEngagement()) {
            totalFpUsed += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : winnerData.getRetreatedFromLastEngagement()) {
            totalFpUsed += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : winnerData.getDisabledInLastEngagement()) {
            totalFpUsed += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : winnerData.getDestroyedInLastEngagement()) {
            totalFpUsed += member.getFleetPointCost();
        }

        float totalFpDestroyed = 0f;
        for (FleetMemberAPI member : loserData.getDestroyedInLastEngagement()) {
            totalFpDestroyed += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : loserData.getDisabledInLastEngagement()) {
            totalFpDestroyed += member.getFleetPointCost();
        }
        for (FleetMemberAPI member : loserData.getRetreatedFromLastEngagement()) {
            if (member.isFighterWing()) {
                DeployedFleetMemberAPI dfm = getDataFor(loserData.getFleet()).getMemberToDeployedMap().get(member);
                if (dfm != null && dfm.getMember() == member) {
                    float deploymentCR = dfm.getShip().getWingCRAtDeployment();
                    float finalCR = dfm.getShip().getRemainingWingCR();
                    //float deployCost = getDeployCost(member);
                    if (deploymentCR > finalCR) {
                        float crPer = dfm.getMember().getStats().getCRPerDeploymentPercent().computeEffective(dfm.getMember().getVariant().getHullSpec().getCRToDeploy()) / 100f;
                        float extraCraftLost = (deploymentCR - finalCR) / crPer;
                        float wingSize = dfm.getMember().getNumFightersInWing();
                        if (extraCraftLost >= 1) {
                            totalFpDestroyed += Math.min(1f, extraCraftLost / wingSize) * member.getFleetPointCost();
                        }
                    }
                }
            }
        }

        if (totalFpUsed < 1) totalFpUsed = 1;
        float f = 1f - Math.min(1f, totalFpDestroyed / totalFpUsed);
        f = Math.round(f * 10f) / 10f;
        return f;
    }

    public void applyPursuitOption(CampaignFleetAPI pursuingFleet, CampaignFleetAPI otherFleet, PursuitOption pursuitOption) {

        DataForEncounterSide pursuer = getDataFor(pursuingFleet);
        DataForEncounterSide other = getDataFor(otherFleet);

        if (pursuitOption == PursuitOption.HARRY) {
            for (FleetMemberAPI member : otherFleet.getFleetData().getMembersListCopy()) {
                float deployCost = getDeployCost(member);

                float harryCost = deployCost * 1f;
                member.getRepairTracker().applyCREvent(-harryCost, "harried while disengaging");
            }
        }
    }



    private EngagementOutcome lastOutcome = null;
    public EngagementOutcome getLastEngagementOutcome() {
        return lastOutcome;
    }

    public boolean isBattleOver() {
        if (hasWinnerAndLoser()) return true;

        return lastOutcome != null &&
                lastOutcome != EngagementOutcome.BATTLE_ENEMY_WIN &&
                lastOutcome != EngagementOutcome.BATTLE_PLAYER_WIN;
    }

    public boolean wasLastEngagementEscape() {
        return lastOutcome != null &&
                lastOutcome != EngagementOutcome.BATTLE_ENEMY_WIN &&
                lastOutcome != EngagementOutcome.BATTLE_PLAYER_WIN;
    }

    public boolean didPlayerWinLastEngagement() {
        return lastOutcome == EngagementOutcome.BATTLE_PLAYER_WIN ||
                lastOutcome == EngagementOutcome.BATTLE_PLAYER_WIN_TOTAL ||
                lastOutcome == EngagementOutcome.ESCAPE_ENEMY_LOSS_TOTAL ||
                lastOutcome == EngagementOutcome.ESCAPE_ENEMY_SUCCESS ||
                lastOutcome == EngagementOutcome.ESCAPE_PLAYER_WIN ||
                lastOutcome == EngagementOutcome.ESCAPE_PLAYER_WIN_TOTAL;
    }

    public boolean didPlayerWinEncounter() {
        if (getDataFor(Global.getSector().getPlayerFleet()).disengaged()) return false;

        // non-fighting "win", i.e. harrying a weaker enemy
        if (lastOutcome == null && getWinner() == Global.getSector().getPlayerFleet()) {
            return true;
        }

        return lastOutcome == EngagementOutcome.BATTLE_PLAYER_WIN_TOTAL ||
                lastOutcome == EngagementOutcome.BATTLE_PLAYER_WIN ||
                lastOutcome == EngagementOutcome.ESCAPE_ENEMY_LOSS_TOTAL ||
                lastOutcome == EngagementOutcome.ESCAPE_ENEMY_SUCCESS ||
                lastOutcome == EngagementOutcome.ESCAPE_PLAYER_WIN ||
                lastOutcome == EngagementOutcome.ESCAPE_PLAYER_WIN_TOTAL;
    }


    public int computeCreditsLooted() {
        DataForEncounterSide winner = getWinnerData();
        DataForEncounterSide loser = getLoserData();

        if (winner == null || loser == null) return 0;

        float fp = 0;
        //for (FleetMemberData data : loser.getOwnCasualties()) {
        for (FleetMemberData data : winner.getEnemyCasualties()) {
            float mult = getSalvageMult(data.getStatus());
            fp += (float) data.getMember().getFleetPointCost() * mult;
        }

        return (int)(fp * 130f * (1.5f + 0.5f * (float) Math.random()));
    }

    public float getSalvageMult(Status status) {
        float mult = 1f;
        switch (status) {
            case DESTROYED:
                mult = 0.25f;
                break;
            case DISABLED:
                mult = 1f;
                break;
            case SALVAGE_DESTROYED:
                mult = 0.5f;
                break;
            case SALVAGE_DISABLED:
                mult = 2.0f;
                break;
            case REPAIRED:
                mult = 2f;
            case CAPTURED:
                mult = 2f;
                break;
        }
        return mult;
    }

    public List<FleetMemberAPI> repairShips() {
        DataForEncounterSide winner = getWinnerData();
        return repairShips(winner);

//		DataForEncounterSide loser = getLoserData();
//		repairShips(loser);
    }

    public static enum EngageBoardableOutcome {
        ESCAPED,
        DISABLED,
        DESTROYED,
    }

    public EngageBoardableOutcome engageBoardableShip(FleetMemberAPI toBoard,
                                                      CampaignFleetAPI fleetItBelongsTo,
                                                      CampaignFleetAPI attackingFleet) {
        float r = (float) Math.random();
        if (r < ENGAGE_ESCAPE_CHANCE && !attackingFleet.isPlayerFleet()) {
            // escaped
            letBoardableGo(toBoard, fleetItBelongsTo, attackingFleet);

            return EngageBoardableOutcome.ESCAPED;
        } else if (r < ENGAGE_ESCAPE_CHANCE + ENGAGE_DISABLE_CHANCE) {
            // disabled
            DataForEncounterSide attackerSide = getDataFor(attackingFleet);
            attackerSide.changeEnemy(toBoard, Status.DISABLED);
            toBoard.getStatus().disable();
            return EngageBoardableOutcome.DISABLED;
        } else {
            DataForEncounterSide attackerSide = getDataFor(attackingFleet);
            attackerSide.changeEnemy(toBoard, Status.DESTROYED);
            toBoard.getStatus().disable();
            return EngageBoardableOutcome.DESTROYED;
        }
    }


    public static enum BoardingAttackType {
        SHIP_TO_SHIP,
        LAUNCH_FROM_DISTANCE,
    }
    public static enum BoardingOutcome {
        SUCCESS,
        SELF_DESTRUCT,
        SUCCESS_TOO_DAMAGED,
        SHIP_ESCAPED,
        SHIP_ESCAPED_CLEAN,
    }

    public static class BoardingResult {
        private BoardingOutcome outcome;
        private CrewCompositionAPI attackerLosses = Global.getFactory().createCrewComposition();
        private CrewCompositionAPI defenderLosses = Global.getFactory().createCrewComposition();
        private FleetMemberAPI member;
        private List<FleetMemberAPI> lostInSelfDestruct = new ArrayList<FleetMemberAPI>();

        public BoardingOutcome getOutcome() {
            return outcome;
        }
        public List<FleetMemberAPI> getLostInSelfDestruct() {
            return lostInSelfDestruct;
        }
        public void setOutcome(BoardingOutcome outcome) {
            this.outcome = outcome;
        }
        public CrewCompositionAPI getAttackerLosses() {
            return attackerLosses;
        }
        public void setAttackerLosses(CrewCompositionAPI attackerLosses) {
            this.attackerLosses = attackerLosses;
        }
        public CrewCompositionAPI getDefenderLosses() {
            return defenderLosses;
        }
        public void setDefenderLosses(CrewCompositionAPI defenderLosses) {
            this.defenderLosses = defenderLosses;
        }
        public FleetMemberAPI getMember() {
            return member;
        }
        public void setMember(FleetMemberAPI member) {
            this.member = member;
        }

    }

    public static final float SELF_DESTRUCT_CHANCE = 0.25f;
    public static final float CIV_SELF_DESTRUCT_CHANCE = 0.05f;

    public static final float ENGAGE_ESCAPE_CHANCE = 0.25f;
    public static final float ENGAGE_DISABLE_CHANCE = 0.5f;
    public static final float ENGAGE_DESTROY_CHANCE = 0.25f;

    public static final float LAUNCH_CLEAN_ESCAPE_CHANCE = 0.5f;
    public static final float DOCK_SUCCESS_CHANCE = 0.5f;
    public static final float LAUNCH_SUCCESS_CHANCE = 0.25f;

    public static final float DEFENDER_BONUS = 1.5f;
    public static final float DEFENDER_VS_LAUNCH_BONUS = 3f;

    public BoardingResult boardShip(FleetMemberAPI member, CrewCompositionAPI boardingParty, BoardingAttackType attackType,
                                    List<FleetMemberAPI> boardingTaskForce,
                                    CampaignFleetAPI attacker, CampaignFleetAPI defender) {


        DataForEncounterSide attackerSide = getDataFor(attacker);
        DataForEncounterSide defenderSide = getDataFor(defender);

        float attackerMarineMult = attacker.getCommanderStats().getMarineEffectivnessMult().getModifiedValue();
        float defenderMarineMult = defender.getCommanderStats().getMarineEffectivnessMult().getModifiedValue();

        float greenMult = 1f;
        float regularMult = 1.25f;
        float veteranMult = 1.5f;
        float eliteMult = 2f;
        float marineMult = 7f;


        float attackerStr = boardingParty.getGreen() * greenMult +
                boardingParty.getRegular() * regularMult +
                boardingParty.getVeteran() * veteranMult +
                boardingParty.getElite() * eliteMult +
                boardingParty.getMarines() * marineMult;
        attackerStr *= attackerMarineMult;

        CrewCompositionAPI defenderCrew = member.getCrewComposition();
        float defenderStr = defenderCrew.getGreen() * greenMult +
                defenderCrew.getRegular() * regularMult +
                defenderCrew.getVeteran() * veteranMult +
                defenderCrew.getElite() * eliteMult +
                defenderCrew.getMarines() * marineMult;
        defenderStr *= defenderMarineMult;

        if (attackType == BoardingAttackType.LAUNCH_FROM_DISTANCE) {
            defenderStr *= DEFENDER_VS_LAUNCH_BONUS;
        } else {
            defenderStr *= DEFENDER_BONUS;
        }

        attackerStr *= 0.75f + 0.25f * (float) Math.random();
        defenderStr *= 0.75f + 0.25f * (float) Math.random();

        boolean attackerWin = attackerStr > defenderStr;
        boolean defenderWin = !attackerWin;

        float r = (float) Math.random();

        BoardingResult result = new BoardingResult();
        result.setMember(member);


        for (FleetMemberAPI fm: boardingTaskForce) {
            float deployCost = getDeployCost(member);
            fm.getRepairTracker().applyCREvent(-deployCost, "engaged in boarding action");
        }

        BoardingOutcome outcome = null;

        //outcome = BoardingOutcome.SELF_DESTRUCT;

        if (outcome == null && attackType == BoardingAttackType.LAUNCH_FROM_DISTANCE && (float) Math.random() < LAUNCH_CLEAN_ESCAPE_CHANCE) {
            outcome = BoardingOutcome.SHIP_ESCAPED_CLEAN;
        }

        if (outcome == null && defenderWin) {
            outcome = BoardingOutcome.SHIP_ESCAPED;
        }

        boolean civ = member.getVariant().getHints().contains(ShipTypeHints.CIVILIAN);
        if (outcome == null && (float) Math.random() < SELF_DESTRUCT_CHANCE && !civ) {
            outcome = BoardingOutcome.SELF_DESTRUCT;
        }
        if (outcome == null && (float) Math.random() < CIV_SELF_DESTRUCT_CHANCE && civ) {
            outcome = BoardingOutcome.SELF_DESTRUCT;
        }


        if (outcome == null) {
            float threshold = DOCK_SUCCESS_CHANCE;
            if (attackType == BoardingAttackType.LAUNCH_FROM_DISTANCE) {
                threshold = LAUNCH_SUCCESS_CHANCE;
            }
            if ((float) Math.random() < threshold) {
                outcome = BoardingOutcome.SUCCESS;
            } else {
                outcome = BoardingOutcome.SUCCESS_TOO_DAMAGED;
            }
        }

        result.setOutcome(outcome);

        switch (outcome) {
            case SELF_DESTRUCT:
                applyBoardingSelfDestruct(member, boardingParty, attackType, boardingTaskForce, attacker, defender, result);
                result.getAttackerLosses().removeFromCargo(attacker.getCargo());
                member.getStatus().disable();
                break;
            case SHIP_ESCAPED:
                applyCrewLossFromBoarding(result, member, boardingParty, attackerStr, defenderStr);
                result.getAttackerLosses().removeFromCargo(attacker.getCargo());
                member.getCrewComposition().removeAll(result.getDefenderLosses());

                letBoardableGo(member, defender, attacker);
                break;
            case SHIP_ESCAPED_CLEAN:
                calculatedMissedLaunchLosses(result, boardingParty);
                result.getAttackerLosses().removeFromCargo(attacker.getCargo());

                letBoardableGo(member, defender, attacker);
                break;
            case SUCCESS:
                applyCrewLossFromBoarding(result, member, boardingParty, attackerStr, defenderStr);
                result.getAttackerLosses().removeFromCargo(attacker.getCargo());
                member.getCrewComposition().removeAll(result.getDefenderLosses());

                //attackerSide.removeEnemyCasualty(member);
                attacker.getFleetData().addFleetMember(member);
                attackerSide.changeEnemy(member, Status.CAPTURED);

                attackerSide.getInReserveDuringLastEngagement().add(member);
                defenderSide.getDestroyedInLastEngagement().remove(member);
                defenderSide.getDisabledInLastEngagement().remove(member);
                break;
            case SUCCESS_TOO_DAMAGED:
                applyCrewLossFromBoarding(result, member, boardingParty, attackerStr, defenderStr);
                result.getAttackerLosses().removeFromCargo(attacker.getCargo());
                member.getCrewComposition().removeAll(result.getDefenderLosses());
                member.getStatus().disable();
                break;
        }

        return result;
//		boolean hasPD = false;
//		for (String slotId : member.getVariant().getFittedWeaponSlots()) {
//			WeaponSpecAPI spec = member.getVariant().getWeaponSpec(slotId);
//			if (spec.getAIHints().contains(AIHints.PD)) {
//				hasPD = true;
//			}
//		}
    }

    private void calculatedMissedLaunchLosses(BoardingResult result, CrewCompositionAPI boardingParty) {
        result.getAttackerLosses().addAll(boardingParty);
        result.getAttackerLosses().multiplyBy((float) Math.random() * 0.2f);
    }

    private void applyCrewLossFromBoarding(BoardingResult result,
                                           FleetMemberAPI member, CrewCompositionAPI boardingParty,
                                           float attackerStr, float defenderStr) {

        if (attackerStr < 1) attackerStr = 1;
        if (defenderStr < 1) defenderStr = 1;

        float attackerLosses = defenderStr / (attackerStr + defenderStr);
        float defenderLosses = attackerStr / (attackerStr + defenderStr);

        if (attackerStr > defenderStr) {
            result.getAttackerLosses().addAll(boardingParty);
            result.getAttackerLosses().multiplyBy(attackerLosses);
            result.getDefenderLosses().addAll(member.getCrewComposition());
            result.getDefenderLosses().multiplyBy(defenderLosses);
        } else {
            result.getAttackerLosses().addAll(boardingParty);
            result.getAttackerLosses().multiplyBy(attackerLosses);
            result.getDefenderLosses().addAll(member.getCrewComposition());
            result.getDefenderLosses().multiplyBy(defenderLosses);
        }
        member.getCrewComposition().removeAll(result.getDefenderLosses());
    }


    private void applyBoardingSelfDestruct(FleetMemberAPI member,
                                           CrewCompositionAPI boardingParty, BoardingAttackType attackType,
                                           List<FleetMemberAPI> boardingTaskForce,
                                           CampaignFleetAPI attacker, CampaignFleetAPI defender,
                                           BoardingResult result) {

        DataForEncounterSide attackerSide = getDataFor(attacker);
        DataForEncounterSide defenderSide = getDataFor(defender);

        attackerSide.changeEnemy(member, Status.DESTROYED);
        defenderSide.changeOwn(member, Status.DESTROYED);


        CrewCompositionAPI total = Global.getFactory().createCrewComposition();

        if (attackType == BoardingAttackType.SHIP_TO_SHIP) {
            for (FleetMemberAPI fm : boardingTaskForce) {
                float damage = member.getStats().getFluxCapacity().getModifiedValue() * (1f + (float) Math.random() * 0.5f);
                float hull = fm.getStatus().getHullFraction();
                float hullDamageFactor = 0f;
                fm.getStatus().applyDamage(damage);
                if (fm.getStatus().getHullFraction() <= 0) {
                    fm.getStatus().disable();
                    attacker.getFleetData().removeFleetMember(fm);
                    attackerSide.addOwn(fm, Status.DESTROYED);
                    //total.addAll(fm.getCrewComposition());

                    attackerSide.getRetreatedFromLastEngagement().remove(fm);
                    attackerSide.getInReserveDuringLastEngagement().remove(fm);
                    attackerSide.getDeployedInLastEngagement().remove(fm);
                    attackerSide.getDestroyedInLastEngagement().add(fm);

                    result.getLostInSelfDestruct().add(fm);

                    hullDamageFactor = 1f;
                } else {
                    float newHull = fm.getStatus().getHullFraction();
                    float diff = hull - newHull;
                    if (diff < 0) diff = 0;
                    hullDamageFactor = diff;
                }
                CrewCompositionAPI temp = Global.getFactory().createCrewComposition();
                temp.addAll(fm.getCrewComposition());
                float lossFraction = computeLossFraction(fm, null, fm.getStatus().getHullFraction(), hullDamageFactor);
                temp.multiplyBy(lossFraction);
                total.addAll(temp);
            }
            //total.removeAll(boardingParty);
        }

        float lossFraction = computeLossFraction(null, null, 0f, 1f);
        total.setMarines(Math.round(Math.max(total.getMarines(), boardingParty.getMarines() * lossFraction)));
        total.setGreen(Math.round(Math.max(total.getGreen(), boardingParty.getGreen() * lossFraction)));
        total.setRegular(Math.round(Math.max(total.getRegular(), boardingParty.getRegular() * lossFraction)));
        total.setVeteran(Math.round(Math.max(total.getVeteran(), boardingParty.getVeteran() * lossFraction)));
        total.setElite(Math.round(Math.max(total.getElite(), boardingParty.getElite() * lossFraction)));

        //result.getAttackerLosses().addAll(boardingParty);
//		float lossFraction = computeLossFraction(boardingTaskForce.get(0), 0f, 1f);
//		total.multiplyBy(lossFraction);

        result.getAttackerLosses().addAll(total);
        result.getDefenderLosses().addAll(member.getCrewComposition());
    }

    public void letBoardableGo(FleetMemberAPI toBoard, CampaignFleetAPI fleetItBelongsTo, CampaignFleetAPI attackingFleet) {
        DataForEncounterSide attackerSide = getDataFor(attackingFleet);
        attackerSide.removeEnemyCasualty(toBoard);

        DataForEncounterSide defenderSide = getDataFor(fleetItBelongsTo);
        defenderSide.removeOwnCasualty(toBoard);


        defenderSide.getDestroyedInLastEngagement().remove(toBoard);
        defenderSide.getDisabledInLastEngagement().remove(toBoard);
        defenderSide.getRetreatedFromLastEngagement().add(toBoard);

        if (!fleetItBelongsTo.isValidPlayerFleet()) {
            fleetItBelongsTo.getCargo().removeCrew(CrewXPLevel.GREEN, fleetItBelongsTo.getCargo().getCrew(CrewXPLevel.GREEN));
            fleetItBelongsTo.getCargo().removeCrew(CrewXPLevel.REGULAR, fleetItBelongsTo.getCargo().getCrew(CrewXPLevel.REGULAR));
            fleetItBelongsTo.getCargo().removeCrew(CrewXPLevel.VETERAN, fleetItBelongsTo.getCargo().getCrew(CrewXPLevel.VETERAN));
            fleetItBelongsTo.getCargo().removeCrew(CrewXPLevel.ELITE, fleetItBelongsTo.getCargo().getCrew(CrewXPLevel.ELITE));
            fleetItBelongsTo.getCargo().removeMarines(fleetItBelongsTo.getCargo().getMarines());
        }

        FleetDataAPI data = fleetItBelongsTo.getFleetData();
        data.addFleetMember(toBoard);

        toBoard.getCrewComposition().addToCargo(fleetItBelongsTo.getCargo());
    }

    public CrewCompositionAPI getMaxBoarders(CampaignFleetAPI fleet, List<FleetMemberAPI> boardingTaskForce) {
        CrewCompositionAPI c = Global.getFactory().createCrewComposition();
        float totalCapacity = 0;
        for (FleetMemberAPI member : boardingTaskForce) {
            c.addMarines(Math.max(0, member.getMaxCrew() - member.getMinCrew()));
            c.addGreen(member.getCrewComposition().getGreen());
            c.addRegular(member.getCrewComposition().getRegular());
            c.addVeteran(member.getCrewComposition().getVeteran());
            c.addElite(member.getCrewComposition().getElite());
            totalCapacity += member.getMaxCrew();
        }
        c.setMarines(Math.min(fleet.getCargo().getMarines(), c.getMarines()));

        CrewCompositionAPI temp = Global.getFactory().createCrewComposition();
        temp.addGreen(fleet.getCargo().getCrew(CrewXPLevel.GREEN));
        temp.addRegular(fleet.getCargo().getCrew(CrewXPLevel.REGULAR));
        temp.addVeteran(fleet.getCargo().getCrew(CrewXPLevel.VETERAN));
        temp.addElite(fleet.getCargo().getCrew(CrewXPLevel.ELITE));
        temp.removeAll(c);

        for (CrewXPLevel level : EnumSet.allOf(CrewXPLevel.class)) {
            float diff = totalCapacity - (c.getTotalCrew() + c.getMarines());
            if (diff > 0) {
                float crew = temp.getCrew(level);
                float add = Math.min(diff, crew);
                if (add > 0) {
                    c.addCrew(level, add);
                }
            } else {
                break;
            }
        }
        return c;
    }


    public FleetMemberAPI pickShipToBoard(CampaignFleetAPI winningFleet, CampaignFleetAPI otherFleet) {
        //if ((float) Math.random() > 0.5f) return null;

        DataForEncounterSide winnerData = getDataFor(winningFleet);
        DataForEncounterSide loserData = getDataFor(otherFleet);

        WeightedRandomPicker<FleetMemberAPI> candidates = new WeightedRandomPicker<FleetMemberAPI>();
        float crewCapacityLost = 0f;
        float fpLost = 0f;
        for (FleetMemberData data : winnerData.getEnemyCasualties()) {
            crewCapacityLost += data.getMember().getMaxCrew();

            if (data.getMember().isFighterWing()) continue;
            if (data.getStatus() != Status.DISABLED && data.getStatus() != Status.SALVAGE_DISABLED) continue;

            fpLost += data.getMember().getFleetPointCost();

            float threshold = 1f - getRepairChance(data.getMember(), otherFleet);
            if ((float) Math.random() > threshold) {
                //if ((float) Math.random() > threshold || true) {
                candidates.add(data.getMember(), 1f);
            }
        }
        if (fpLost < 1) fpLost = 1;

        if (candidates.isEmpty()) return null;

        float crewCapacityRemaining = 0f;
        for (FleetMemberAPI member : otherFleet.getFleetData().getMembersListCopy()) {
            crewCapacityRemaining += member.getMaxCrew();
        }
        if (crewCapacityRemaining < 1) crewCapacityRemaining = 1;

        FleetMemberAPI pick = candidates.pick();
        CrewCompositionAPI crew = Global.getFactory().createCrewComposition();

        float f = computeRecoverableFraction(pick, null, 0f, 1f);

        float marineFactor = crewCapacityLost / (crewCapacityLost + crewCapacityRemaining);
        marineFactor *= f * (float) pick.getFleetPointCost() / fpLost;
        if (marineFactor > 1) marineFactor = 1;

        float marinesOnBoard = (int) (otherFleet.getCargo().getMarines() * marineFactor);
        crew.setMarines(marinesOnBoard);
        crew.addAll(pick.getCrewComposition());

        repairFleetMember(pick);

        //ShipToBoard result = new ShipToBoard(pick, crew);
        pick.getCrewComposition().removeAllCrew();
        pick.getCrewComposition().addAll(crew);

        if (pick.getCrewComposition().getTotalCrew() < 2) {
            pick.getCrewComposition().addCrew(pick.getCrewXPLevel(), 2);
        }
        return pick;
    }


    private List<FleetMemberAPI> repairShips(DataForEncounterSide side) {
        if (side == null || side.disengaged()) return new ArrayList<FleetMemberAPI>();
        //if (lostBattle) return;

        List<FleetMemberAPI> result = new ArrayList<FleetMemberAPI>();

        for (FleetMemberData data : side.getOwnCasualties()) {
            FleetMemberAPI member = data.getMember();
            if (member.isFighterWing()) continue;

            float threshold = 1f - getRepairChance(member, side.getFleet());


            if ((float) Math.random() > threshold) {
                repairFleetMember(member);

                data.setStatus(Status.REPAIRED);
                side.getFleet().getFleetData().addFleetMember(member);
                side.getInReserveDuringLastEngagement().add(member);
                result.add(member);
            }
        }

        for (FleetMemberAPI member : result) {
            side.removeOwnCasualty(member);
        }

        return result;
    }

    private void repairFleetMember(FleetMemberAPI member) {
        member.getStatus().repairDisabledABit();

        ShipVariantAPI variant = member.getVariant().clone();
        variant.clearHullMods();
        variant.setNumFluxCapacitors(0);
        variant.setNumFluxVents(0);
        variant.setSource(VariantSource.REFIT);
        member.setVariant(variant, false, false);
        List<String> remove = new ArrayList<String>();
        if (!member.isFighterWing()) {
            for (String slotId : variant.getNonBuiltInWeaponSlots()) {
                if ((float) Math.random() > getWeaponSurvivialOnRepairChance()) {
                    remove.add(slotId);
                }
            }
            for (String slotId : remove) {
                variant.clearSlot(slotId);
            }
        }
    }


    public void gainXP() {
        if (sideData.size() != 2) return;

        DataForEncounterSide sideOne = sideData.get(0);
        DataForEncounterSide sideTwo= sideData.get(1);
        gainXP(sideOne, sideTwo);
        gainXP(sideTwo, sideOne);
    }


    private void gainXP(DataForEncounterSide side, DataForEncounterSide otherSide) {
        CampaignFleetAPI fleet = side.getFleet();
        int fpTotal = 0;
        for (FleetMemberData data : otherSide.getOwnCasualties()) {
            fpTotal += data.getMember().getFleetPointCost();
        }

        int lostFp = 0;
        for (FleetMemberData data : side.getOwnCasualties()) {
            lostFp += data.getMember().getFleetPointCost() * 2;
        }

        if ((float) fleet.getFleetPoints() + (float) lostFp / 2f <= 7) lostFp = 0;

        fpTotal += lostFp;

        float lossXP = lostFp * 250;
        float xp = (float) fpTotal * 250;
        if (side.disengaged()) xp += lossXP; // yes, losing nets you more XP

        if (xp > 0) {
            if (lostFp > 0 && Global.getSector().getPlayerFleet() == side.getFleet()) {
                Global.getSector().getCampaignUI().addMessage("You've gained valuable experience from the losses suffered in the last engagement");
            }
            fleet.getCargo().gainCrewXP(xp);
            fleet.getCommander().getStats().addXP((long) xp);
            fleet.getCommander().getStats().levelUpIfNeeded();
        }
    }



    private CargoAPI loot = Global.getFactory().createCargo(false);
    public void scrapDisabledShipsAndGenerateLoot() {
        DataForEncounterSide winner = getWinnerData();
        DataForEncounterSide loser = getLoserData();

        if (winner == null || loser == null) return;

        loot.clear();

        CargoAPI loserCargo = (CargoAPI) loser.getFleet().getCargo();
        float maxCapacity = loserCargo.getMaxCapacity();

        float suppliesSalvaged = 0f;
        for (FleetMemberData data : winner.getEnemyCasualties()) {
            if (data.getStatus() == Status.CAPTURED || data.getStatus() == Status.REPAIRED) {
                continue;
            }

            float mult = getSalvageMult(data.getStatus());
            lootWeapons(data.getMember(), mult);
            suppliesSalvaged += data.getMember().getRepairTracker().getSuppliesFromSalvage() * mult;
            maxCapacity += data.getMember().getCargoCapacity();
        }
        for (FleetMemberData data : winner.getOwnCasualties()) {
            if (data.getStatus() == Status.CAPTURED || data.getStatus() == Status.REPAIRED) {
                continue;
            }

            float mult = getSalvageMult(data.getStatus());
            lootWeapons(data.getMember(), mult);
            suppliesSalvaged += data.getMember().getRepairTracker().getSuppliesFromSalvage() * mult;
        }

        // EXERELIN edit
        if(ExerelinConfig.reduceSupplies)
        {
            float suppliesToAdd = suppliesSalvaged;
            if(ExerelinConfig.capSupplyDropToCargo)
                suppliesToAdd = Math.min(suppliesToAdd, (float)(this.getWinner().getCargo().getMaxCapacity()*1.5));

            suppliesToAdd = suppliesToAdd*(float)ExerelinConfig.reduceSuppliesFactor;
            suppliesSalvaged = suppliesToAdd;
        }

        // EXERELIN edit
        if(didPlayerWinEncounter())
        {
            int influenceChange = 0;
            influenceChange = influenceChange +  getLoserData().getDisabledInLastEngagement().size();
            influenceChange = influenceChange +  getLoserData().getDestroyedInLastEngagement().size();

            SectorManager.getCurrentSectorManager().getDiplomacyManager().applyInfluenceChangeForWonEncounter(loser.getFleet().getFaction().getId(), influenceChange, SectorManager.getCurrentSectorManager().getSystemManager((StarSystemAPI) winner.getFleet().getContainingLocation()).getFactionInSystemAsList());

            float totalCrewLost = getLoserData().getCrewLossesDuringLastEngagement().getTotalCrew();
            if(ExerelinUtils.getRandomInRange(0, 10) <= totalCrewLost/100)
            {
                // 100% guarentee for 1000 killed crew
                // Add a high value prisoner
                loot.addItems(CargoAPI.CargoItemType.RESOURCES, "prisoner", 1) ;
            }

            if(ExerelinUtils.getRandomInRange(0, 1) == 0)
            {
                // 50% chance to add 1 crew to loot per 50 or 100 lost by enemy
                int crew = (int)totalCrewLost/(50 * ExerelinUtils.getRandomInRange(1, 2));
                loot.addCrew(CrewXPLevel.GREEN, crew);
            }
        }

        loot.addSupplies((int)suppliesSalvaged);


        float scrappedCapacity = 0f;
        float lootedCapacity = 0f;
        for (FleetMemberData data : winner.getEnemyCasualties()) {
            float mult = getSalvageMult(data.getStatus());
            scrappedCapacity += data.getMember().getCargoCapacity();
            lootedCapacity += data.getMember().getCargoCapacity() * mult * 0.5f;
        }

        float resourceFractionTaken = lootedCapacity / maxCapacity;
        if (lootedCapacity > maxCapacity) resourceFractionTaken = 1f; // failsafe...

        // for now, just take supplies and fuel
        float fuelLost = loserCargo.getFuel() * resourceFractionTaken;
        float suppliesLost = loserCargo.getSupplies() * resourceFractionTaken;

        loserCargo.removeFuel(fuelLost);
        loserCargo.removeSupplies(suppliesLost);

        float lootedFraction = 0.5f;
        loot.addFuel(Math.round(fuelLost * lootedFraction));

        // EXERELIN EDIT
        if(ExerelinConfig.reduceSupplies)
        {
            float suppliesToAdd = Math.round(suppliesLost * lootedFraction);

            if(ExerelinConfig.capSupplyDropToCargo)
                suppliesToAdd = Math.min(suppliesToAdd, (float)(this.getWinner().getCargo().getMaxCapacity()*1.5));

            suppliesToAdd = suppliesToAdd*(float)ExerelinConfig.reduceSuppliesFactor;
            suppliesToAdd = suppliesToAdd - suppliesSalvaged; // Reduce by how many we have already added

            if(suppliesToAdd > 0)
                loot.addSupplies(suppliesToAdd);
        }
        else
            loot.addSupplies(Math.round(suppliesLost * lootedFraction));

        for (CargoStackAPI stack : loserCargo.getStacksCopy()) {
            if (stack.isNull()) continue;
            if (stack.isSupplyStack() || stack.isFuelStack() || stack.isCrewStack() || stack.isMarineStack()) continue;
            //if (stack.isWeaponStack()) {
            float numLooted = Math.round(resourceFractionTaken * (float) Math.random() * stack.getSize());
            if (numLooted >= 1) {
                stack.add(-numLooted);
                if (numLooted >= 2) {
                    numLooted *= lootedFraction;
                }
                if (numLooted >= 1) {
                    loot.addItems(stack.getType(), stack.getData(), numLooted);
                }
                //loserCargo.removeItems(stack.getType(), stack.getData(), numLooted);
            }
            //}
        }

        loot.sort();
    }

    public CargoAPI getLoot() {
        return loot;
    }

    private void lootWeapons(FleetMemberAPI member, float mult) {
        ShipVariantAPI variant = member.getVariant();
        List<String> remove = new ArrayList<String>();
        for (String slotId : variant.getNonBuiltInWeaponSlots()) {
            if ((float) Math.random() * mult > 0.75f) {
                String weaponId = variant.getWeaponId(slotId);
                loot.addItems(CargoAPI.CargoItemType.WEAPONS, weaponId, 1);
                remove.add(slotId);
            }
        }
        // DO NOT DO THIS - no point in removing them here since the ship is scrapped
        // and would need to clone the variant to do this right
//		for (String slotId : remove) {
//			variant.clearSlot(slotId);
//		}
        //System.out.println("Cleared variant: " + variant.getHullVariantId());
    }

    public void autoLoot() {
        DataForEncounterSide winner = getWinnerData();
        DataForEncounterSide loser = getLoserData();

        if (winner == null || loser == null) return;

        CargoAPI winnerCargo = winner.getFleet().getCargo();

        float fuelToLoot = loot.getFuel();
        float max = winnerCargo.getMaxFuel() - winnerCargo.getFuel();
        if (fuelToLoot > max) fuelToLoot = max;
        if (fuelToLoot > 0) {
            loot.removeFuel(fuelToLoot);
            winnerCargo.addFuel(fuelToLoot);
        }

        for (CargoStackAPI stack : loot.getStacksCopy()) {
            if (stack.isNull() || stack.isFuelStack()) continue;
            float spaceLeft = winnerCargo.getSpaceLeft();

            if ((float) Math.random() > 0.5f) {
                float spacePerUnit = stack.getCargoSpacePerUnit();
                float maxUnits = (int) (spaceLeft / spacePerUnit);
                if (maxUnits > stack.getSize()) maxUnits = stack.getSize();
                maxUnits = Math.round(maxUnits * (Math.random() * 0.5f + 0.5f));
                winnerCargo.addItems(stack.getType(), stack.getData(), maxUnits);
            }
        }

        // actually transfer loot to winner cargo, whatever is needed
    }

    public boolean hasWinnerAndLoser() {
        return getWinner() != null && getLoser() != null;
    }

    public CampaignFleetAPI getWinner() {
        return getWinnerData() != null ? getWinnerData().getFleet() : null;
    }
    public CampaignFleetAPI getLoser() {
        return getLoserData() != null ? getLoserData().getFleet() : null;
    }

    public boolean canOutrunOtherFleet(CampaignFleetAPI fleet, CampaignFleetAPI other) {
        return fleet.getFleetData().getMinBurnLevel() >= other.getFleetData().getMaxBurnLevel() + 1f;
    }

    public static float getWeaponSurvivialOnRepairChance() {
        return 0.1f;
    }

    public static float getRepairChance(FleetMemberAPI member, CampaignFleetAPI fleet) {
        float result;
        if (fleet.getCommander() != null) {
            result = fleet.getCommander().getStats().getShipRepairChance().getModifiedValue() * 0.01f;
        } else {
            result = Global.getSettings().getFloat("baseShipRepairChance") * 0.01f;
        }

        //if (member.isFighterWing()) result += 0.5f;
        return result;
//		if (member.isFighterWing()) return 0.6f;
//		return 0.1f;
    }




    private void applyResultToFleets(EngagementResultAPI result) {
//		applyCrewAndShipLosses(result);
        applyCrewLosses(result);
        fixFighters(result.getWinnerResult());
        fixFighters(result.getLoserResult());
        applyShipLosses(result);
    }


    public static void fixFighters(EngagementResultForFleetAPI result) {
        boolean replaceLost = false;
        for (FleetMemberAPI curr : result.getReserves()) {
            if (curr.isMothballed()) continue;
            if (curr.getNumFlightDecks() > 0) {
                replaceLost = true;
                break;
            }
        }
        if (!replaceLost) {
            for (FleetMemberAPI curr : result.getDeployed()) {
                if (curr.isMothballed()) continue;
                if (curr.getNumFlightDecks() > 0) {
                    replaceLost = true;
                    break;
                }
            }
        }
        if (!replaceLost) {
            for (FleetMemberAPI curr : result.getRetreated()) {
                if (curr.isMothballed()) continue;
                if (curr.getNumFlightDecks() > 0) {
                    replaceLost = true;
                    break;
                }
            }
        }


        List<FleetMemberAPI> saved = new ArrayList<FleetMemberAPI>();
        if (replaceLost) {
            for (FleetMemberAPI curr : result.getDestroyed()) {
                if (curr.isFighterWing()) {
                    saved.add(curr);
                }
            }
        }

        result.getDestroyed().removeAll(saved);
        result.getRetreated().addAll(saved);


        List<FleetMemberAPI> toRepair = new ArrayList<FleetMemberAPI>();
        toRepair.addAll(result.getDeployed());
        toRepair.addAll(result.getRetreated());
        for (FleetMemberAPI curr : toRepair) {
            if (curr.isFighterWing()) {
                if (replaceLost) {
                    curr.getStatus().repairFully();
                } else {
                    curr.getStatus().repairFullyNoNewFighters();
                }
            }
        }

    }

//	private void applyCrewAndShipLosses(EngagementResultAPI result) {
//		applyCrewLosses(result);
//		applyShipLosses(result);
//	}

    private void applyCrewLosses(EngagementResultAPI result) {
        EngagementResultForFleetAPI winner = result.getWinnerResult();
        EngagementResultForFleetAPI loser = result.getLoserResult();

        calculateCrewLosses(winner);
        calculateCrewLosses(loser);

        applyCrewLosses(winner);
        applyCrewLosses(loser);
    }

    private void applyShipLosses(EngagementResultAPI result) {
        EngagementResultForFleetAPI winner = result.getWinnerResult();
        EngagementResultForFleetAPI loser = result.getLoserResult();

        applyShipLosses(winner);
        applyShipLosses(loser);

        applyCREffect(winner);
        applyCREffect(loser);
    }

    private Map<FleetMemberAPI, Float> preEngagementCRForWinner = new HashMap<FleetMemberAPI, Float>();
    private void applyCREffect(EngagementResultForFleetAPI result) {
        boolean wonBattle = result.isWinner();
        if (wonBattle) {
            preEngagementCRForWinner.clear();
        }
        for (FleetMemberAPI member : result.getFleet().getFleetData().getMembersListCopy()) {
            preEngagementCRForWinner.put(member, member.getRepairTracker().getCR());
        }

        for (FleetMemberAPI member : result.getDisabled()) {
            member.getRepairTracker().applyCREvent(-1, "disabled in combat");
        }
        for (FleetMemberAPI member : result.getDestroyed()) {
            member.getRepairTracker().applyCREvent(-1, "disabled in combat");
        }

        for (FleetMemberAPI member : result.getDeployed()) {
            float deployCost = getDeployCost(member);
            if (member.isFighterWing()) {
                member.getRepairTracker().applyCREvent(-deployCost, "wing deployed in combat");
            } else {
                member.getRepairTracker().applyCREvent(-deployCost, "deployed in combat");
            }

            applyExtendedCRLossIfNeeded(result, member);
        }

        //float retreatLossMult = StarfarerSettings.getCRLossMultForRetreatInLoss();
        float retreatLossMult = Global.getSettings().getFloat("crLossMultForRetreatInLoss");
        for (FleetMemberAPI member : result.getRetreated()) {
            float deployCost = getDeployCost(member);
            if (member.isFighterWing()) {
                member.getRepairTracker().applyCREvent(-deployCost, "wing deployed in combat");
            } else {
                member.getRepairTracker().applyCREvent(-deployCost, "deployed in combat");
            }

            applyExtendedCRLossIfNeeded(result, member);

            if (!wonBattle && result.getGoal() != FleetGoal.ESCAPE) {
                float retreatCost = deployCost * retreatLossMult;
                member.getRepairTracker().applyCREvent(-retreatCost, "retreated from lost engagement");
            }
        }

//		// important, so that in-combat Ship objects can be garbage collected.
//		// Probably some combat engine references in there, too.
//		// NOTE: moved this elsewhere in this class
//		result.resetAllEverDeployed();
//		getDataFor(result.getFleet()).getMemberToDeployedMap().clear();
    }


    /**
     * Only matters in non-auto-resolved battles.
     * @param member
     */
    private void applyExtendedCRLossIfNeeded(EngagementResultForFleetAPI result, FleetMemberAPI member) {
        DeployedFleetMemberAPI dfm = getDataFor(result.getFleet()).getMemberToDeployedMap().get(member);
        if (dfm == null) return;

        if (dfm.getMember() == member && dfm.isFighterWing()) {
            float finalCR = dfm.getShip().getRemainingWingCR();
            float cr = member.getRepairTracker().getCR();
            if (cr > finalCR) {
                member.getRepairTracker().applyCREvent(-(cr - finalCR), "deployed replacement chassis in combat");
            }
            return;
        }
        if (dfm.getMember() == member && !dfm.isFighterWing()) {
            float endOfCombatCR = dfm.getShip().getCurrentCR();
            float cr = member.getRepairTracker().getCR();
            if (cr > endOfCombatCR) {
                member.getRepairTracker().applyCREvent(-(cr - endOfCombatCR), "extended deployment");
            }


            ShipAPI ship = dfm.getShip();
            if (dfm.getShip() != null && !dfm.isFighterWing()) {
                float wMult = Global.getSettings().getFloat("crLossMultForWeaponDisabled");
                float eMult = Global.getSettings().getFloat("crLossMultForFlameout");
                float mMult = Global.getSettings().getFloat("crLossMultForMissilesFired");
                float hMult = Global.getSettings().getFloat("crLossMultForHullDamage");

                float hullDamageFraction = ship.getHullLevelAtDeployment() - ship.getHullLevel();
                float hullDamageCRLoss = hullDamageFraction * hMult;
                if (hullDamageCRLoss > 0) {
                    member.getRepairTracker().applyCREvent(-hullDamageCRLoss, "hull damage sustained");
                }

                float totalDisabled = 0f;
                MutableCharacterStatsAPI stats = member.getFleetCommander().getStats();
                float maxOP = ship.getVariant().getHullSpec().getOrdnancePoints(stats);
                if (maxOP <= 1) maxOP = 1;

                for (WeaponAPI w : ship.getDisabledWeapons()) {
                    totalDisabled += w.getSpec().getOrdnancePointCost(stats) * wMult;
                }
                if (ship.getNumFlameouts() > 0) {
                    totalDisabled += maxOP * eMult;
                }

                float damageBasedCRLoss = Math.min(1f, totalDisabled / maxOP);
                if (damageBasedCRLoss > 0) {
                    member.getRepairTracker().applyCREvent(-damageBasedCRLoss, "weapon and engine damage sustained");
                }

                float missileReloadOP = 0f;
                for (WeaponAPI w : ship.getAllWeapons()) {
                    if (w.getType() == WeaponType.MISSILE && w.usesAmmo()) {
                        missileReloadOP += (1f - (float) w.getAmmo() / (float) w.getMaxAmmo()) * w.getSpec().getOrdnancePointCost(stats) * mMult;
                    }
                }

                float missileReloadLoss = Math.min(1f, missileReloadOP / maxOP);
                if (missileReloadLoss > 0) {
                    member.getRepairTracker().applyCREvent(-missileReloadLoss, "missile weapons used in combat");
                }
            }

            return;
        }
    }


    private void applyShipLosses(EngagementResultForFleetAPI result) {
        for (FleetMemberAPI member : result.getDestroyed()) {
            result.getFleet().removeFleetMemberWithDestructionFlash(member);
        }
        for (FleetMemberAPI member : result.getDisabled()) {
            result.getFleet().removeFleetMemberWithDestructionFlash(member);
        }
    }

    private void applyCrewLosses(EngagementResultForFleetAPI result) {
        CargoAPI cargo = result.getFleet().getCargo();
        DataForEncounterSide data = getDataFor(result.getFleet());
        CrewCompositionAPI crewLosses = data.getCrewLossesDuringLastEngagement();

        cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.GREEN.getId(), crewLosses.getGreen());
        cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.REGULAR.getId(), crewLosses.getRegular());
        cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.VETERAN.getId(), crewLosses.getVeteran());
        cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.ELITE.getId(), crewLosses.getElite());

        cargo.removeMarines((int) crewLosses.getMarines());
    }

    private float computeLossFraction(FleetMemberAPI member, EngagementResultForFleetAPI result, float hullFraction, float hullDamage) {
        if (member == null && hullFraction == 0) {
            return (0.75f + (float) Math.random() * 0.25f);
        }

        //System.out.println("hullDamage: " + hullDamage);
        if (member.isFighterWing() && result != null) {
            //System.out.println("Fighter hullDamage: " + hullDamage);
            float extraLossMult = hullDamage;
            DeployedFleetMemberAPI dfm = getDataFor(result.getFleet()).getMemberToDeployedMap().get(member);
            if (dfm != null && dfm.getMember() == member) {
                float finalCR = dfm.getShip().getRemainingWingCR();
                float cr = member.getRepairTracker().getCR();
                if (cr > finalCR) {
                    float crPer = dfm.getMember().getStats().getCRPerDeploymentPercent().computeEffective(dfm.getMember().getVariant().getHullSpec().getCRToDeploy()) / 100f;
                    float extraCraftLost = (cr - finalCR) / crPer;
                    float wingSize = dfm.getMember().getNumFightersInWing();
                    if (extraCraftLost >= 1) {
                        extraLossMult = hullDamage + extraCraftLost / wingSize;
                    }
                }
            }
            return (0.25f + (float) Math.random() * 0.75f * (float) Math.random()) * member.getStats().getCrewLossMult().getModifiedValue() * extraLossMult;
        }

        if (hullFraction == 0) {
            return (0.75f + (float) Math.random() * 0.25f) * member.getStats().getCrewLossMult().getModifiedValue();
        }
        return hullDamage * hullDamage * (0.5f + (float) Math.random() * 0.5f) * member.getStats().getCrewLossMult().getModifiedValue();
    }

    private float computeRecoverableFraction(FleetMemberAPI member, EngagementResultForFleetAPI result, float hullFraction, float hullDamage) {
        float f = 1f - computeLossFraction(member, result, hullFraction, hullDamage);
        if (f < 0) f = 0;
        return f;
    }

    public void calculateCrewLosses(EngagementResultForFleetAPI result) {
        boolean wonBattle = result.isWinner();

        DataForEncounterSide data = getDataFor(result.getFleet());
        CrewCompositionAPI crewLosses = data.getCrewLossesDuringLastEngagement();
        crewLosses.removeAllCrew();

        CrewCompositionAPI recoverable = data.getRecoverableCrewLosses();

        List<FleetMemberAPI> all = new ArrayList<FleetMemberAPI>();
        all.addAll(result.getDisabled());
        //all.addAll(captured);
        all.addAll(result.getDeployed());
        all.addAll(result.getDestroyed());
        all.addAll(result.getRetreated());
        all.addAll(result.getReserves());
        for (FleetMemberAPI member  : all) {
            CrewCompositionAPI c = member.getCrewComposition();
            //float hull = member.getStatus().getHullFraction();
            float hullDamage = member.getStatus().getHullDamageTaken();
            float hullFraction = member.getStatus().getHullFraction();
            member.getStatus().resetDamageTaken();

//			if (lostBattle) {
//				System.out.println("HERE");
//			}

            float f1 = computeLossFraction(member, result, hullFraction, hullDamage);
            float f2 = computeLossFraction(member, result, hullFraction, hullDamage);
            float f3 = computeLossFraction(member, result, hullFraction, hullDamage);
            float f4 = computeLossFraction(member, result, hullFraction, hullDamage);

            // ship is disabled or destroyed, lose all crew for now, but it may be recovered later
            if (result.getDisabled().contains(member) || result.getDestroyed().contains(member)) {
                if (f1 < 1) {
                    recoverable.addGreen((1f - f1) * c.getGreen());
                }
                if (f2 < 1) {
                    recoverable.addRegular((1f - f2) * c.getRegular());
                }
                if (f3 < 1) {
                    recoverable.addVeteran((1f - f3) * c.getVeteran());
                }
                if (f4 < 1) {
                    recoverable.addElite((1f - f4) * c.getElite());
                }

                crewLosses.addGreen(c.getGreen() * 1f);
                crewLosses.addRegular(c.getRegular() * 1f);
                crewLosses.addVeteran(c.getVeteran() * 1f);
                crewLosses.addElite(c.getElite() * 1f);

                c.addGreen(-c.getGreen() * f1);
                c.addRegular(-c.getRegular() * f1);
                c.addVeteran(-c.getVeteran() * f1);
                c.addElite(-c.getElite() * f1);

                // c should now be left with the appropriate crew composition (base minus losses) to use
                // as a starting point for boarding actions

            } else {
                // the ship is still ok, only lose the non-recoverable casualties

                // for fighters, which can lose more than their actual max crew
                if (f1 > 1) {
                    crewLosses.addGreen((f1 - 1) * c.getGreen());
                }
                if (f2 > 1) {
                    crewLosses.addGreen((f2 - 1) * c.getRegular());
                }
                if (f3 > 1) {
                    crewLosses.addGreen((f3 - 1) * c.getVeteran());
                }
                if (f4 > 1) {
                    crewLosses.addGreen((f4 - 1) * c.getElite());
                }

                // both fighters and normal ships
                c.transfer(CargoAPI.CrewXPLevel.GREEN, c.getGreen() * f1, crewLosses);
                c.transfer(CargoAPI.CrewXPLevel.REGULAR, c.getRegular() * f2, crewLosses);
                c.transfer(CargoAPI.CrewXPLevel.VETERAN, c.getVeteran() * f3, crewLosses);
                c.transfer(CargoAPI.CrewXPLevel.ELITE, c.getElite() * f4, crewLosses);

            }

        }
    }

    public void recoverCrew(CampaignFleetAPI fleet) {
        DataForEncounterSide data = getDataFor(fleet);
        CargoAPI cargo = fleet.getCargo();
        CrewCompositionAPI rec = data.getRecoverableCrewLosses();

        cargo.addItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.GREEN.getId(), rec.getGreen());
        cargo.addItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.REGULAR.getId(), rec.getRegular());
        cargo.addItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.VETERAN.getId(), rec.getVeteran());
        cargo.addItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.ELITE.getId(), rec.getElite());

        cargo.addMarines((int) rec.getMarines());
    }
}











