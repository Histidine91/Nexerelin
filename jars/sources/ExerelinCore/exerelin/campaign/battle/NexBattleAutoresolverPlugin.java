package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Literally just {@code BattleAutoresolverPluginImpl} except with public data classes and a few new behaviors
 * Namely, strength is adjusted based on CR, and by an optional fleet memory key
 */
public class NexBattleAutoresolverPlugin implements BattleAutoresolverPlugin {

    public static final String MEM_KEY_STRENGTH_MULT = "$nex_autoresolve_strMult";

    public static class EngagementResultImpl implements EngagementResultAPI {
        public BattleAPI battle;
        public EngagementResultForFleetImpl winnerResult, loserResult;

        public EngagementResultImpl(BattleAPI battle, CampaignFleetAPI winner, CampaignFleetAPI loser) {
            this.battle = battle;
            winnerResult = new EngagementResultForFleetImpl(winner);
            loserResult = new EngagementResultForFleetImpl(loser);
        }

        public BattleAPI getBattle() {
            return battle;
        }

        public boolean didPlayerWin() {
            //return winnerResult.getFleet() != null && winnerResult.getFleet().isPlayerFleet();
            return winnerResult.getFleet() != null && Misc.isPlayerOrCombinedContainingPlayer(winnerResult.getFleet());
        }

        public EngagementResultForFleetAPI getLoserResult() {
            return loserResult;
        }

        public EngagementResultForFleetAPI getWinnerResult() {
            return winnerResult;
        }

        public boolean isPlayerOutBeforeEnd() {
            return false;
        }

        public void setPlayerOutBeforeEnd(boolean playerOutBeforeEnd) {
        }

        public void setBattle(BattleAPI battle) {
            this.battle = battle;
        }

        public CombatDamageData getLastCombatDamageData() {
            return null;
        }

        public void setLastCombatDamageData(CombatDamageData lastCombatData) {

        }
    }

    protected static class EngagementResultForFleetImpl implements EngagementResultForFleetAPI {
        protected CampaignFleetAPI fleet;
        protected FleetGoal goal;
        protected boolean winner = false;
        protected List<FleetMemberAPI> deployed = new ArrayList<FleetMemberAPI>();
        protected List<FleetMemberAPI> reserves = new ArrayList<FleetMemberAPI>();
        protected List<FleetMemberAPI> destroyed = new ArrayList<FleetMemberAPI>();
        protected List<FleetMemberAPI> disabled = new ArrayList<FleetMemberAPI>();
        protected List<FleetMemberAPI> retreated = new ArrayList<FleetMemberAPI>();

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
        public boolean isPlayer() {
            return false;
        }

        public boolean enemyCanCleanDisengage() {
            // TODO Auto-generated method stub
            return false;
        }
    }

    protected static enum FleetMemberBattleOutcome {
        UNSCATHED,
        LIGHT_DAMAGE,
        MEDIUM_DAMAGE,
        HEAVY_DAMAGE,
        DISABLED,
    }

    public static final class FleetMemberAutoresolveData {
        public FleetMemberAPI member;
        public float strength;
        public float shieldRatio;
        public boolean combatReady;
    }

    public static final class FleetAutoresolveData {
        public CampaignFleetAPI fleet;
        public float fightingStrength;
        public List<FleetMemberAutoresolveData> members = new ArrayList<FleetMemberAutoresolveData>();

        public void report() {
            if (!report) return;

            NexBattleAutoresolverPlugin.report(String.format("Fighting srength of %s: %f", fleet.getNameWithFaction(), fightingStrength));
            for (FleetMemberAutoresolveData data : members) {
                String str = String.format("%40s: CR % 3d%%    FP % 4d     STR % 3f   Shield %3.2f",
                        data.member.getVariant().getFullDesignationWithHullName(),
                        (int)(data.member.getRepairTracker().getCR() * 100f),
                        data.member.getFleetPointCost(),
                        data.strength,
                        data.shieldRatio);
                NexBattleAutoresolverPlugin.report("  " + str);
            }

        }
    }

    protected CampaignFleetAPI one;
    protected CampaignFleetAPI two;
    protected final BattleAPI battle;

    protected boolean playerPursuitAutoresolveMode = false;
    protected List<FleetMemberAPI> playerShipsToDeploy;

    public NexBattleAutoresolverPlugin(BattleAPI battle) {
        this.battle = battle;

        one = battle.getCombinedOne();
        two = battle.getCombinedTwo();
        if (battle.isPlayerInvolved()) {
            one = battle.getPlayerCombined();
            two = battle.getNonPlayerCombined();
        }

        setReport(Global.getSettings().isDevMode());
        setReport(false);
    }

    public void resolve() {
        // figure out battle type (escape vs engagement)

        report("***");
        report("***");
        report(String.format("Autoresolving %s vs %s", one.getNameWithFaction(), two.getNameWithFaction()));

        context = new FleetEncounterContext();
        context.setAutoresolve(true);
        context.setBattle(battle);
        CampaignFleetAIAPI.EncounterOption optionOne = one.getAI().pickEncounterOption(context, two);
        CampaignFleetAIAPI.EncounterOption optionTwo = two.getAI().pickEncounterOption(context, one);

        if (optionOne == CampaignFleetAIAPI.EncounterOption.DISENGAGE && optionTwo == CampaignFleetAIAPI.EncounterOption.DISENGAGE) {
            report("Both fleets want to disengage");
            report("Finished autoresolving engagement");
            report("***");
            report("***");
            return;
        }

        boolean oneEscaping = false;
        boolean twoEscaping = false;

        boolean freeDisengageIfCanOutrun = false;

        if (optionOne == CampaignFleetAIAPI.EncounterOption.DISENGAGE && optionTwo == CampaignFleetAIAPI.EncounterOption.ENGAGE) {
            report(String.format("%s wants to disengage", one.getNameWithFaction()));
            oneEscaping = true;
            if (freeDisengageIfCanOutrun && context.canOutrunOtherFleet(one, two)) {
                report(String.format("%s can outrun other fleet", one.getNameWithFaction()));
                report("Finished autoresolving engagement");
                report("***");
                report("***");
                return;
            }
        }
        if (optionOne == CampaignFleetAIAPI.EncounterOption.ENGAGE && optionTwo == CampaignFleetAIAPI.EncounterOption.DISENGAGE) {
            report(String.format("%s wants to disengage", two.getNameWithFaction()));
            twoEscaping = true;
            if (freeDisengageIfCanOutrun && context.canOutrunOtherFleet(two, one)) {
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


    public void resolvePlayerPursuit(FleetEncounterContext context, List<FleetMemberAPI> playerShipsToDeploy) {
        this.context = context;
        playerPursuitAutoresolveMode = true;
        this.playerShipsToDeploy = playerShipsToDeploy;
        resolveEngagement(context, false, true);
    }

    protected void resolveEngagement(FleetEncounterContext context, boolean oneEscaping, boolean twoEscaping) {

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
//		if (winnerAdvantage > 2f) winnerAdvantage = 2f;
//		if (winnerAdvantage < 0.5f) winnerAdvantage = 0.5f;
        if (winnerAdvantage > 10f) winnerAdvantage = 10f;
        if (winnerAdvantage < 0.1f) winnerAdvantage = 0.1f;
        //if (winnerAdvantage < 0.1f) winnerAdvantage = 0.1f;

        float damageDealtToWinner = loser.fightingStrength / winnerAdvantage;
        float damageDealtToLoser = winner.fightingStrength * winnerAdvantage;
        if (playerPursuitAutoresolveMode) {
            damageDealtToWinner = 0f;
        }

        float damMult = Global.getSettings().getFloat("autoresolveDamageMult");
        damageDealtToWinner *= damMult;
        damageDealtToLoser *= damMult;

        //result = new EngagementResultImpl(winner.fleet, loser.fleet);
        result = new EngagementResultImpl(context.getBattle(),
                context.getBattle().getCombinedFor(winner.fleet),
                context.getBattle().getCombinedFor(loser.fleet));

        report("");
        report("Applying damage to loser's ships");
        report("--------------------------------------------");
        Collections.shuffle(loser.members);
        //boolean loserCarrierLeft = false;
        for (FleetMemberAutoresolveData data : loser.members) {
            report(String.format("Remaining damage to loser: %02.2f", damageDealtToLoser));
            FleetMemberBattleOutcome outcome = computeOutcomeForFleetMember(data, 1f/winnerAdvantage, damageDealtToLoser, loserEscaping, false);
            damageDealtToLoser -= data.strength;
            if (damageDealtToLoser < 0) damageDealtToLoser = 0;
        }

        for (FleetMemberAutoresolveData data : loser.members) {
            if (data.member.getStatus().getHullFraction() > 0) { // || (data.member.isFighterWing() && loserCarrierLeft)) {
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
            FleetMemberBattleOutcome outcome = computeOutcomeForFleetMember(data, winnerAdvantage, damageDealtToWinner, false, loserEscaping);
            damageDealtToWinner -= data.strength;
            if (damageDealtToWinner < 0) damageDealtToWinner = 0;

            if (data.member.isMothballed()) continue;
            if (data.member.getStatus().getHullFraction() > 0 && data.member.getNumFlightDecks() > 0) {
                winnerCarrierLeft = true;
            }
        }

        // which ships should count as "deployed" for CR loss purposes?
        // anything that was disabled, and then anything up to double the loser's strength
        float deployedStrength = 0f;
        float maxDeployedStrength = loser.fightingStrength * 2f;
        for (FleetMemberAutoresolveData data : winner.members) {
            if (!(data.member.getStatus().getHullFraction() > 0 || (data.member.isFighterWing() && winnerCarrierLeft))) {
                deployedStrength += data.strength;
            }
        }

        for (FleetMemberAutoresolveData data : winner.members) {
            if (playerPursuitAutoresolveMode) {
                if (playerShipsToDeploy.contains(data.member) || data.member.isAlly()) {
                    result.getWinnerResult().getDeployed().add(data.member);
                } else {
                    result.getWinnerResult().getReserves().add(data.member);
                }
            } else {
                if (data.member.getStatus().getHullFraction() > 0) {
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
            //context.applyPostEngagementOption(result);
            context.performPostVictoryRecovery(result);

            // need to set up one fleet as attacking, one fleet as losing + escaping/disengaging
            // for the scrapping/looting/etc to work properly
            context.getDataFor(winner.fleet).setDisengaged(false);
            context.getDataFor(winner.fleet).setWonLastEngagement(true);
            context.getDataFor(winner.fleet).setLastGoal(FleetGoal.ATTACK);

            context.getDataFor(loser.fleet).setDisengaged(true);
            context.getDataFor(loser.fleet).setWonLastEngagement(false);
            context.getDataFor(loser.fleet).setLastGoal(FleetGoal.ESCAPE);

            if (!winner.fleet.isAIMode()) {
                context.generateLoot(null, true);
                context.autoLoot();
                context.recoverCrew(winner.fleet);
            }
            //context.repairShips();

            context.applyAfterBattleEffectsIfThereWasABattle();
        } else {
            // for ship recovery to recognize these ships as non-player
//			DataForEncounterSide data = context.getDataFor(loser.fleet);
//			for (FleetMemberAPI member : data.getDisabledInLastEngagement()) {
//				member.setOwner(1);
//			}
//			for (FleetMemberAPI member : data.getDestroyedInLastEngagement()) {
//				member.setOwner(1);
//			}
            for (FleetMemberAutoresolveData data : loser.members) {
                data.member.setOwner(1);
            }
        }

//		context.getBattle().uncombine();
//		context.getBattle().finish();
    }


    public static void applyDamageToFleetMember(FleetMemberAPI member,
                                                float hullFraction) {
        if (member.isFighterWing()) return;
        if (hullFraction <= 0) return;

        float num = member.getStatus().getNumStatuses();
        boolean someActiveRemaining = false;
        for (int i = 0; i < num; i++) {
            ShipVariantAPI variant = member.getVariant();
            if (i > 0) {
                String slotId = member.getVariant().getModuleSlots().get(i - 1);
                variant = variant.getModuleVariant(slotId);
            }

            if (variant.hasHullMod(HullMods.VASTBULK)) {
                float dam = Math.min(hullFraction, 0.9f);
                float hits = Math.min(5f, dam / 0.1f);
                if (hits < 1) hits = 1;
                //hits = 10;
                for (int j = 0; j < hits; j++) {
                    member.getStatus().applyHullFractionDamage(dam / hits, i);
                    member.getStatus().setHullFraction(i, 1f);
                }
                continue;
            }

            if (i > 0 && !Misc.isActiveModule(variant)) continue;

            float damageMult = 1f;
            if (i > 0) {
                damageMult = (float) Math.random();
                damageMult *= 2f;
            }

            float damage = hullFraction * damageMult;
            if (damage <= 0) continue;

            member.getStatus().applyHullFractionDamage(damage, i);

            float hits = Math.min(5f, damage / 0.1f);
            if (hits < 1) hits = 1;
            for (int j = 0; j < hits; j++) {
                member.getStatus().applyHullFractionDamage((damage / hits) + 0.001f, i);
            }

            if (i > 0 && member.getStatus().getHullFraction(i) <= 0) {
                member.getStatus().setDetached(i, true);
            }

            if (Misc.isActiveModule(variant) && (!member.getStatus().isDetached(i) || member.getStatus().getHullFraction(i) > 0)) {
                someActiveRemaining = true;
            }
        }

        if (num > 1) {
            float farthestDetached = 0;
            for (int i = 1; i < num; i++) {
                ShipVariantAPI variant = member.getVariant();
                if (member.getStatus().isDetached(i)) {
                    String slotId = variant.getModuleSlots().get(i - 1);
                    ShipVariantAPI mv = member.getVariant().getModuleVariant(slotId);
                    if (!Misc.isActiveModule(mv)) continue;

                    WeaponSlotAPI slot = variant.getHullSpec().getWeaponSlotAPI(slotId);
                    float dist = slot.getLocation().length();
                    if (dist > farthestDetached) {
                        farthestDetached = dist;
                    }
                }
            }

            for (int i = 1; i < num; i++) {
                ShipVariantAPI variant = member.getVariant();
                if (!member.getStatus().isDetached(i)) {
                    String slotId = variant.getModuleSlots().get(i - 1);
                    ShipVariantAPI mv = member.getVariant().getModuleVariant(slotId);
                    if (mv.hasHullMod(HullMods.VASTBULK)) continue;
                    if (!Misc.isActiveModule(mv)) {
                        WeaponSlotAPI slot = variant.getHullSpec().getWeaponSlotAPI(slotId);
                        float dist = slot.getLocation().length();
                        if (dist <= farthestDetached + 200f) {
                            member.getStatus().setHullFraction(i, 0f);
                            member.getStatus().setDetached(i, true);
                        }
                    }
                }
            }
        }


        if (!someActiveRemaining || hullFraction >= 1f) {
            for (int i = 0; i < num; i++) {
                member.getStatus().setHullFraction(i, 0f);
                if (i > 0) {
                    member.getStatus().setDetached(i, true);
                }
            }
        }
    }


    protected FleetMemberBattleOutcome computeOutcomeForFleetMember(FleetMemberAutoresolveData data, float advantageInBattle,
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
                unscathed = 15;;
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


        if (data.member.isStation()) {
            heavyDamage += disabled;
            disabled = 0f; // only disabled when heavy damage takes out all modules
        }


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
                break;
            case HEAVY_DAMAGE:
                report(String.format("%40s: heavy damage", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0.7f + (float) Math.random() * 0.1f;
                break;
            case MEDIUM_DAMAGE:
                report(String.format("%40s: medium damage", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0.45f + (float) Math.random() * 0.1f;
                break;
            case LIGHT_DAMAGE:
                report(String.format("%40s: light damage", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0.2f + (float) Math.random() * 0.1f;
                break;
            case UNSCATHED:
                report(String.format("%40s: unscathed", data.member.getVariant().getFullDesignationWithHullName()));
                damage = 0f;
                break;
        }

        //damage = 0.8f;
        applyDamageToFleetMember(data.member, damage);

        return outcome;
    }

    protected FleetAutoresolveData computeDataForFleet(CampaignFleetAPI fleet) {
        FleetAutoresolveData fleetData = new FleetAutoresolveData();
        fleetData.fleet = fleet;

        // MODIFIED
        float fleetMult = 1;
        if (fleet.getMemoryWithoutUpdate().contains(MEM_KEY_STRENGTH_MULT)) {
            fleetMult = fleet.getMemoryWithoutUpdate().getFloat(MEM_KEY_STRENGTH_MULT);
        }

        fleetData.fightingStrength = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
            FleetMemberAutoresolveData data = computeDataForMember(member);

            // MODIFIED
            data.strength *= fleetMult;

            fleetData.members.add(data);
            boolean okToDeployIfInPlayerPursuit = playerPursuitAutoresolveMode && playerShipsToDeploy != null && fleet == one && (playerShipsToDeploy.contains(member) || member.isAlly());
            if (data.combatReady && (!playerPursuitAutoresolveMode || fleet != one || okToDeployIfInPlayerPursuit)) {
                float mult = 1f;
                if (playerShipsToDeploy != null && playerShipsToDeploy.contains(member)) {
                    mult = 8f;
                }
                fleetData.fightingStrength += data.strength * mult;
            }
        }
        return fleetData;
    }

    protected FleetMemberAutoresolveData computeDataForMember(FleetMemberAPI member) {
        FleetMemberAutoresolveData data = new FleetMemberAutoresolveData();

        data.member = member;
        ShipHullSpecAPI hullSpec = data.member.getHullSpec();
        if ((member.isCivilian() && !playerPursuitAutoresolveMode) || !member.canBeDeployedForCombat()) {
            data.strength = 0.25f;
            if (hullSpec.getShieldType() != ShieldAPI.ShieldType.NONE) {
                data.shieldRatio = 0.5f;
            }
            data.combatReady = false;
            return data;
        }

        data.combatReady = true;

//		if (data.member.getHullId().contains("astral")) {
//			System.out.println("testtesttest");
//		}

        MutableShipStatsAPI stats = data.member.getStats();

        float normalizedHullStr = stats.getHullBonus().computeEffective(hullSpec.getHitpoints()) +
                stats.getArmorBonus().computeEffective(hullSpec.getArmorRating()) * 10f;

        float normalizedShieldStr = stats.getFluxCapacity().getModifiedValue() +
                stats.getFluxDissipation().getModifiedValue() * 10f;


        if (hullSpec.getShieldType() == ShieldAPI.ShieldType.NONE) {
            normalizedShieldStr = 0;
        } else {
            float shieldFluxPerDamage = hullSpec.getBaseShieldFluxPerDamageAbsorbed();
            shieldFluxPerDamage *= stats.getShieldAbsorptionMult().getModifiedValue() * stats.getShieldDamageTakenMult().getModifiedValue();;
            if (shieldFluxPerDamage < 0.1f) shieldFluxPerDamage = 0.1f;
            float shieldMult = 1f / shieldFluxPerDamage;
            normalizedShieldStr *= shieldMult;
        }

        if (normalizedHullStr < 1) normalizedHullStr = 1;
        if (normalizedShieldStr < 1) normalizedShieldStr = 1;

        data.shieldRatio = normalizedShieldStr / (normalizedShieldStr + normalizedHullStr);
        if (member.isStation()) {
            data.shieldRatio = 0.5f;
        }

//		float strength = member.getMemberStrength();
//		
//		strength *= 0.5f + 0.5f * member.getStatus().getHullFraction();
//		float captainMult = 0.5f;
//		if (member.getCaptain() != null) {
//			//captainMult = (10f + member.getCaptain().getStats().getAptitudeLevel("combat")) / 20f;
//			float captainLevel = member.getCaptain().getStats().getLevel();
//			captainMult += captainLevel / 20f;
//		}
//		
//		strength *= captainMult;

        float strength = Misc.getMemberStrength(member, true, true, true);

        // MODIFIED
        strength *= (0.5f + 0.5f * member.getRepairTracker().getCR() / 0.7f);

        strength *= 0.85f + 0.3f * (float) Math.random();

        data.strength = Math.max(strength, 0.25f);

        return data;
    }


    protected static void report(String str) {
        if (report) {
            System.out.println(str);
        }
    }

    protected static boolean report = false;
    protected EngagementResultAPI result;
    protected FleetEncounterContext context;

    public void setReport(boolean report) {
        report = report;
    }

    public EngagementResultAPI getResult() {
        return result;
    }

    public FleetEncounterContextPlugin getContext() {
        return context;
    }

}
