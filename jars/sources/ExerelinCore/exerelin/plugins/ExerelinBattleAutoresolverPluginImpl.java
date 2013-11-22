package exerelin.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAutoresolverPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;

public class ExerelinBattleAutoresolverPluginImpl implements BattleAutoresolverPlugin {

    private static class EngagementResultImpl implements EngagementResultAPI {
        private EngagementResultForFleetImpl winnerResult, loserResult;

        public EngagementResultImpl(CampaignFleetAPI winner, CampaignFleetAPI loser) {
            winnerResult = new EngagementResultForFleetImpl(winner);
            loserResult = new EngagementResultForFleetImpl(loser);
        }

        public boolean didPlayerWin() {
            return winnerResult.getFleet() != null && winnerResult.getFleet().isPlayerFleet();
        }

        public EngagementResultForFleetAPI getLoserResult() {
            return loserResult;
        }

        public EngagementResultForFleetAPI getWinnerResult() {
            return winnerResult;
        }
    }

    private static class EngagementResultForFleetImpl implements EngagementResultForFleetAPI {
        private CampaignFleetAPI fleet;
        private FleetGoal goal;
        private boolean winner = false;
        private List<FleetMemberAPI> deployed = new ArrayList<FleetMemberAPI>();
        private List<FleetMemberAPI> reserves = new ArrayList<FleetMemberAPI>();
        private List<FleetMemberAPI> destroyed = new ArrayList<FleetMemberAPI>();
        private List<FleetMemberAPI> disabled = new ArrayList<FleetMemberAPI>();
        private List<FleetMemberAPI> retreated = new ArrayList<FleetMemberAPI>();

        public EngagementResultForFleetImpl(CampaignFleetAPI fleet) {
            this.fleet = fleet;
        }

        public List<FleetMemberAPI> getDeployed() {
            return deployed;
        }
        public List<FleetMemberAPI> getDestroyed() {
            return destroyed;
        }
        public List<FleetMemberAPI> getDisabled() {
            return disabled;
        }
        public CampaignFleetAPI getFleet() {
            return fleet;
        }
        public FleetGoal getGoal() {
            return goal;
        }
        public List<FleetMemberAPI> getReserves() {
            return reserves;
        }
        public List<FleetMemberAPI> getRetreated() {
            return retreated;
        }
        public List<DeployedFleetMemberAPI> getAllEverDeployedCopy() {
            return null;
        }
        public boolean isWinner() {
            return winner;
        }
        public void setWinner(boolean winner) {
            this.winner = winner;
        }
        public void resetAllEverDeployed() {
        }
        public void setGoal(FleetGoal goal) {
            this.goal = goal;
        }
    }

    private static enum FleetMemberBattleOutcome {
        UNSCATHED,
        LIGHT_DAMAGE,
        MEDIUM_DAMAGE,
        HEAVY_DAMAGE,
        DISABLED,
    }

    private static final class FleetMemberAutoresolveData {
        private FleetMemberAPI member;
        private float strength;
        private float shieldRatio;
        private boolean combatReady;
    }

    private static final class FleetAutoresolveData {
        private CampaignFleetAPI fleet;
        private float fightingStrength;
        private List<FleetMemberAutoresolveData> members = new ArrayList<FleetMemberAutoresolveData>();

        private void report() {
            if (!report) return;

            ExerelinBattleAutoresolverPluginImpl.report(String.format("Fighting srength of %s: %f", fleet.getNameWithFaction(), fightingStrength));
            for (FleetMemberAutoresolveData data : members) {
                String str = String.format("%40s: CR % 3d%%    FP % 4d     STR % 3f   Shield %3.2f",
                        data.member.getVariant().getFullDesignationWithHullName(),
                        (int)(data.member.getRepairTracker().getCR() * 100f),
                        data.member.getFleetPointCost(),
                        data.strength,
                        data.shieldRatio);
                ExerelinBattleAutoresolverPluginImpl.report("  " + str);
            }

        }
    }

    private final CampaignFleetAPI one;
    private final CampaignFleetAPI two;

    private boolean playerPursuitAutoresolveMode = false;
    private List<FleetMemberAPI> playerShipsToDeploy;

    public ExerelinBattleAutoresolverPluginImpl(CampaignFleetAPI one, CampaignFleetAPI two) {
        this.one = one;
        this.two = two;

        setReport(Global.getSettings().isDevMode());
    }

    public void resolve() {
        // figure out battle type (escape vs engagement)

        report("***");
        report("***");
        report(String.format("Autoresolving %s vs %s", one.getNameWithFaction(), two.getNameWithFaction()));

        ExerelinFleetEncounterContext context = new ExerelinFleetEncounterContext();
        EncounterOption optionOne = one.getAI().pickEncounterOption(context, two);
        EncounterOption optionTwo = two.getAI().pickEncounterOption(context, one);

        if (optionOne == EncounterOption.DISENGAGE && optionTwo == EncounterOption.DISENGAGE) {
            report("Both exerelin.fleets want to disengage");
            report("Finished autoresolving engagement");
            report("***");
            report("***");
            return;
        }

        boolean oneEscaping = false;
        boolean twoEscaping = false;

        if (optionOne == EncounterOption.DISENGAGE && optionTwo == EncounterOption.ENGAGE) {
            report(String.format("%s wants to disengage", one.getNameWithFaction()));
            oneEscaping = true;
            if (context.canOutrunOtherFleet(one, two)) {
                report(String.format("%s can outrun other fleet", one.getNameWithFaction()));
                report("Finished autoresolving engagement");
                report("***");
                report("***");
                return;
            }
        }
        if (optionOne == EncounterOption.ENGAGE && optionTwo == EncounterOption.DISENGAGE) {
            report(String.format("%s wants to disengage", two.getNameWithFaction()));
            twoEscaping = true;
            if (context.canOutrunOtherFleet(two, one)) {
                report(String.format("%s can outrun other fleet", two.getNameWithFaction()));
                report("Finished autoresolving engagement");
                report("***");
                report("***");
                return;
            }
        }

        resolveEngagement(context, oneEscaping, twoEscaping);

        report("");
        report("Finished autoresolving engagement");
        report("***");
        report("***");
    }


    public void resolvePlayerPursuit(ExerelinFleetEncounterContext context, List<FleetMemberAPI> playerShipsToDeploy) {
        playerPursuitAutoresolveMode = true;
        this.playerShipsToDeploy = playerShipsToDeploy;
        resolveEngagement(context, false, true);
    }

    private void resolveEngagement(ExerelinFleetEncounterContext context, boolean oneEscaping, boolean twoEscaping) {

        FleetAutoresolveData dataOne = computeDataForFleet(one);
        FleetAutoresolveData dataTwo = computeDataForFleet(two);

        if (dataOne.fightingStrength <= 0 && dataTwo.fightingStrength <= 0) {
            return;
        }
        if (dataOne.fightingStrength <= 0.1f) {
            dataOne.fightingStrength = 0.1f;
        }
        if (dataTwo.fightingStrength <= 0.1f) {
            dataTwo.fightingStrength = 0.1f;
        }

        FleetAutoresolveData winner, loser;

        report("");
        report("--------------------------------------------");
        dataOne.report();

        report("");
        report("--------------------------------------------");
        dataTwo.report();

        report("");
        report("");

        boolean loserEscaping = false;
        if ((dataOne.fightingStrength > dataTwo.fightingStrength || twoEscaping) && !oneEscaping) {
            report(String.format("%s won engagement", one.getNameWithFaction()));
            winner = dataOne;
            loser = dataTwo;
            if (twoEscaping) {
                loserEscaping = true;
            }
        } else {
            report(String.format("%s won engagement", two.getNameWithFaction()));
            winner = dataTwo;
            loser = dataOne;
            if (oneEscaping) {
                loserEscaping = true;
            }
        }

        float winnerAdvantage = winner.fightingStrength / loser.fightingStrength;
        if (winnerAdvantage > 2f) winnerAdvantage = 2f;
        if (winnerAdvantage < 0.5f) winnerAdvantage = 0.5f;
        //if (winnerAdvantage < 0.1f) winnerAdvantage = 0.1f;

        float damageDealtToWinner =  loser.fightingStrength / winnerAdvantage;
        float damageDealtToLoser =  winner.fightingStrength * winnerAdvantage;
        if (playerPursuitAutoresolveMode) {
            damageDealtToWinner = 0f;
        }

        result = new EngagementResultImpl(winner.fleet, loser.fleet);

        report("");
        report("Applying damage to loser's ships");
        report("--------------------------------------------");
        Collections.shuffle(loser.members);
        boolean loserCarrierLeft = false;
        for (FleetMemberAutoresolveData data : loser.members) {
            report(String.format("Remaining damage to loser: %02.2f", damageDealtToLoser));
            computeOutcomeForFleetMember(data, 1f/winnerAdvantage, damageDealtToLoser, loserEscaping, false);
            damageDealtToLoser -= data.strength;
            if (damageDealtToLoser < 0) damageDealtToLoser = 0;
            if (data.member.isMothballed()) continue;
            if (data.member.getStatus().getHullFraction() > 0 && data.member.getNumFlightDecks() > 0) {
                loserCarrierLeft = true;
            }
        }

        for (FleetMemberAutoresolveData data : loser.members) {
            if (data.member.getStatus().getHullFraction() > 0 || (data.member.isFighterWing() && loserCarrierLeft)) {
                result.getLoserResult().getRetreated().add(data.member);
            } else {
                result.getLoserResult().getDisabled().add(data.member);
            }
        }

        report("");
        report("Applying damage to winner's ships");
        report("--------------------------------------------");
        Collections.shuffle(winner.members);

        boolean winnerCarrierLeft = false;
        for (FleetMemberAutoresolveData data : winner.members) {
            if (!data.combatReady) continue;
            report(String.format("Remaining damage to winner: %02.2f", damageDealtToWinner));
            computeOutcomeForFleetMember(data, winnerAdvantage, damageDealtToWinner, false, loserEscaping);
            damageDealtToWinner -= data.strength;
            if (damageDealtToWinner < 0) damageDealtToWinner = 0;

            if (data.member.isMothballed()) continue;
            if (data.member.getStatus().getHullFraction() > 0 && data.member.getNumFlightDecks() > 0) {
                winnerCarrierLeft = true;
            }
        }

        // which ships should count as "deployed" for CR loss purposes?
        // anything that was disabled, and then anything up to double the winner's strength
        float deployedStrength = 0f;
        float maxDeployedStrength = loser.fightingStrength;
        for (FleetMemberAutoresolveData data : winner.members) {
            if (!(data.member.getStatus().getHullFraction() > 0 || (data.member.isFighterWing() && winnerCarrierLeft))) {
                deployedStrength += data.strength;
            }
        }

        for (FleetMemberAutoresolveData data : winner.members) {
            if (playerPursuitAutoresolveMode) {
                if (playerShipsToDeploy.contains(data.member)) {
                    result.getWinnerResult().getDeployed().add(data.member);
                } else {
                    result.getWinnerResult().getReserves().add(data.member);
                }
            } else {
                if (data.member.getStatus().getHullFraction() > 0 || (data.member.isFighterWing() && winnerCarrierLeft)) {
                    if (deployedStrength < maxDeployedStrength) {
                        result.getWinnerResult().getDeployed().add(data.member);
                        deployedStrength += data.strength;
                    } else {
                        result.getWinnerResult().getReserves().add(data.member);
                    }
                } else {
                    result.getWinnerResult().getDisabled().add(data.member);
                }
            }
        }


        // CR hit, ship/crew losses get applied here
        ((EngagementResultForFleetImpl)result.getWinnerResult()).setGoal(FleetGoal.ATTACK);
        ((EngagementResultForFleetImpl)result.getWinnerResult()).setWinner(true);

        if (loserEscaping) {
            ((EngagementResultForFleetImpl)result.getLoserResult()).setGoal(FleetGoal.ESCAPE);
        } else {
            ((EngagementResultForFleetImpl)result.getLoserResult()).setGoal(FleetGoal.ATTACK);
        }
        ((EngagementResultForFleetImpl)result.getLoserResult()).setWinner(false);


        // will be handled inside the interaction dialog if it's the player auto-resolving pursuit
        if (!playerPursuitAutoresolveMode) {
            context.processEngagementResults(result);

            // need to set up one fleet as attacking, one fleet as losing + escaping/disengaging
            // for the scrapping/looting/etc to work properly
            context.getDataFor(winner.fleet).setDisengaged(false);
            context.getDataFor(winner.fleet).setWonLastEngagement(true);
            context.getDataFor(winner.fleet).setLastGoal(FleetGoal.ATTACK);

            context.getDataFor(loser.fleet).setDisengaged(true);
            context.getDataFor(loser.fleet).setWonLastEngagement(false);
            context.getDataFor(loser.fleet).setLastGoal(FleetGoal.ESCAPE);

            context.scrapDisabledShipsAndGenerateLoot();
            context.autoLoot();
            context.repairShips();
            context.recoverCrew(winner.fleet);

            context.applyAfterBattleEffectsIfThereWasABattle();
        }
    }

    private void computeOutcomeForFleetMember(FleetMemberAutoresolveData data, float advantageInBattle,
                                              float maxDamage, boolean escaping, boolean enemyEscaping) {
        ShipHullSpecAPI hullSpec = data.member.getHullSpec();

        float unscathed = 1f;
        float lightDamage = 0f;
        float mediumDamage = 0f;
        float heavyDamage = 0f;
        float disabled = 0f;

        switch (hullSpec.getHullSize()) {
            case CAPITAL_SHIP:
                unscathed = 5f;
                break;
            case CRUISER:
                unscathed = 10f;
                break;
            case DESTROYER:
                unscathed = 15;
                break;
            case FRIGATE:
            case FIGHTER:
                unscathed = 30f;
                break;
        }

        float maxDamageRatio = maxDamage / data.strength;
        if (maxDamageRatio > 1) maxDamageRatio = 1;
        if (maxDamageRatio <= 0) maxDamageRatio = 0;

        if (maxDamageRatio >= 0.8f) {
            disabled = 20f;
            heavyDamage = 10f;
            mediumDamage = 10f;
            lightDamage = 5f;
        } else if (maxDamageRatio >= 0.6f) {
            disabled = 5f;
            heavyDamage = 20f;
            mediumDamage = 10f;
            lightDamage = 5f;
        } else if (maxDamageRatio >= 0.4f) {
            disabled = 0f;
            heavyDamage = 10f;
            mediumDamage = 20f;
            lightDamage = 10f;
        } else if (maxDamageRatio >= 0.2f) {
            disabled = 0f;
            heavyDamage = 0f;
            mediumDamage = 10f;
            lightDamage = 20f;
        } else if (maxDamageRatio > 0) {
            disabled = 0f;
            heavyDamage = 0f;
            mediumDamage = 5f;
            lightDamage = 10f;
        }

        if (escaping) {
            unscathed *= 2f;
            lightDamage *= 1.5f;
        }

        if (enemyEscaping) {
            disabled *= 0.5f;
            heavyDamage *= 0.6f;
            mediumDamage *= 0.7f;
            lightDamage *= 0.8f;
            unscathed *= 1f;
        }

        // advantageInBattle goes from 0.5 (bad) to 2 (good)
        unscathed *= advantageInBattle;
        lightDamage *= advantageInBattle;

        float shieldRatio = data.shieldRatio;

        // shieldRatio goes from 0 (no shields/no flux) to 1 (shields dominate hull/armor)
        // shieldRatio at 0.5 roughly indicates balanced shields and hull/armor effectiveness

        disabled *= 1.5f - shieldRatio * 1f;
        heavyDamage *= 1.4f - shieldRatio * 0.8f;
        mediumDamage *= 1.3f - shieldRatio * 0.6f;
        lightDamage *= 1.2f - shieldRatio * 0.4f;
        unscathed *= 0.9f + shieldRatio * 0.2f;


        WeightedRandomPicker<FleetMemberBattleOutcome> picker = new WeightedRandomPicker<FleetMemberBattleOutcome>();

        picker.add(FleetMemberBattleOutcome.DISABLED, disabled);
        picker.add(FleetMemberBattleOutcome.HEAVY_DAMAGE, heavyDamage);
        picker.add(FleetMemberBattleOutcome.MEDIUM_DAMAGE, mediumDamage);
        picker.add(FleetMemberBattleOutcome.LIGHT_DAMAGE, lightDamage);
        picker.add(FleetMemberBattleOutcome.UNSCATHED, unscathed);


        report(String.format("Disabled: %d, Heavy: %d, Medium: %d, Light: %d, Unscathed: %d (Shield ratio: %3.2f)",
                (int) disabled, (int) heavyDamage, (int) mediumDamage, (int) lightDamage, (int) unscathed, shieldRatio));

        FleetMemberBattleOutcome outcome = picker.pick();


        float damage = 0f;

        data.member.getStatus().resetDamageTaken();


        switch (outcome) {
            case DISABLED:
                report(String.format("%40s: disabled", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 1f;
                for (int i = 0; i < data.member.getStatus().getNumStatuses(); i++) {
                    if (data.member.isFighterWing()) {
                        data.member.getStatus().applyHullFractionDamage(damage, i);
                    } else {
                        data.member.getStatus().applyHullFractionDamage(damage/3f);
                        data.member.getStatus().applyHullFractionDamage(damage/3f);
                        data.member.getStatus().applyHullFractionDamage(damage/3f);
                    }
                }
                break;
            case HEAVY_DAMAGE:
                report(String.format("%40s: heavy damage", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0.7f + (float) Math.random() * 0.1f;
                for (int i = 0; i < data.member.getStatus().getNumStatuses(); i++) {
                    if (data.member.isFighterWing()) {
                        data.member.getStatus().applyHullFractionDamage(damage, i);
                    } else {
                        data.member.getStatus().applyHullFractionDamage(damage/3f);
                        data.member.getStatus().applyHullFractionDamage(damage/3f);
                        data.member.getStatus().applyHullFractionDamage(damage/3f);
                    }
                }
                break;
            case MEDIUM_DAMAGE:
                report(String.format("%40s: medium damage", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0.45f + (float) Math.random() * 0.1f;
                for (int i = 0; i < data.member.getStatus().getNumStatuses(); i++) {
                    if (data.member.isFighterWing()) {
                        data.member.getStatus().applyHullFractionDamage(damage, i);
                    } else {
                        data.member.getStatus().applyHullFractionDamage(damage/2f);
                        data.member.getStatus().applyHullFractionDamage(damage/2f);
                    }
                }
                break;
            case LIGHT_DAMAGE:
                report(String.format("%40s: light damage", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0.2f + (float) Math.random() * 0.1f;
                for (int i = 0; i < data.member.getStatus().getNumStatuses(); i++) {
                    if (data.member.isFighterWing()) {
                        data.member.getStatus().applyHullFractionDamage(damage, i);
                    } else {
                        data.member.getStatus().applyHullFractionDamage(damage);
                    }
                }
                break;
            case UNSCATHED:
                report(String.format("%40s: unscathed", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0f;
                break;
        }

    }

    private FleetAutoresolveData computeDataForFleet(CampaignFleetAPI fleet) {
        FleetAutoresolveData fleetData = new FleetAutoresolveData();
        fleetData.fleet = fleet;

        fleetData.fightingStrength = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            FleetMemberAutoresolveData data = computeDataForMember(member);
            fleetData.members.add(data);
            boolean okToDeployIfInPlayerPursuit = playerPursuitAutoresolveMode && fleet == one && playerShipsToDeploy.contains(member);
            if (data.combatReady && (!playerPursuitAutoresolveMode || fleet != one || okToDeployIfInPlayerPursuit)) {
                fleetData.fightingStrength += data.strength;
            }
        }

        return fleetData;
    }

    private FleetMemberAutoresolveData computeDataForMember(FleetMemberAPI member) {
        FleetMemberAutoresolveData data = new FleetMemberAutoresolveData();

        data.member = member;
        if ((member.isCivilian() && !playerPursuitAutoresolveMode) || !member.canBeDeployedForCombat()) {
            data.strength = 1f;
            data.combatReady = false;
            return data;
        }

        data.combatReady = true;

//		if (data.member.getHullId().contains("astral")) {
//			System.out.println("testtesttest");
//		}

        ShipHullSpecAPI hullSpec = data.member.getHullSpec();
        MutableShipStatsAPI stats = data.member.getStats();

        float normalizedHullStr = stats.getHullBonus().computeEffective(hullSpec.getHitpoints()) +
                stats.getArmorBonus().computeEffective(hullSpec.getArmorRating()) * 10f;

        float normalizedShieldStr = stats.getFluxCapacity().getModifiedValue() +
                stats.getFluxDissipation().getModifiedValue() * 10f;


        if (hullSpec.getShieldType() == ShieldType.NONE) {
            normalizedShieldStr = 0;
        } else {
            float shieldMult = stats.getShieldAbsorptionMult().getModifiedValue() * stats.getShieldDamageTakenMult().getModifiedValue();
            normalizedShieldStr *= shieldMult;
        }

        if (normalizedHullStr < 1) normalizedHullStr = 1;
        if (normalizedShieldStr < 1) normalizedShieldStr = 1;

        data.shieldRatio = normalizedShieldStr / (normalizedShieldStr + normalizedHullStr);



        float baseOP = member.getHullSpec().getOrdnancePoints(null);
        float strength;
        if (baseOP <= 0) {
            strength = (float) member.getFleetPointCost();
        } else {
            strength = (float) member.getFleetPointCost() *
                    member.getVariant().computeOPCost(null) / baseOP;
        }

        float captainMult = 0.5f;
        if (member.getCaptain() != null) {
            captainMult = (10f + member.getCaptain().getStats().getAptitudeLevel("combat")) / 20f;
        }

        strength *= captainMult;
        strength *= 0.5f + 0.5f * member.getStatus().getHullFraction();
        strength *= 0.5f + 0.5f * member.getRepairTracker().getCR();


        strength *= 0.85f + 0.3f * (float) Math.random();

        data.strength = Math.max(strength, 1f);

        return data;
    }


    private static void report(String str) {
        if (report) {
            System.out.println(str);
        }
    }

    private static boolean report = false;
    private EngagementResultAPI result;
    public void setReport(boolean report) {
        ExerelinBattleAutoresolverPluginImpl.report = report;
    }

    public EngagementResultAPI getResult() {
        return result;
    }

}










