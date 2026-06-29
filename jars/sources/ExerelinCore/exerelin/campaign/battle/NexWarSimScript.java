package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.BattleAutoresolverPluginImpl;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.intel.specialforces.SpecialForcesIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.fs.starfarer.api.impl.campaign.fleets.RouteManager.*;

/**
 * Campaign-layer autoresolve handler. Not actually particularly related to vanilla {@code WarSimScript}.
 */
@Log4j
public class NexWarSimScript {

    // because of how many fleets are involved, the damage in any one round can sum to brutal amounts
    // and because of how damage recipients are selected, one or more fleets could eat most of that damage and just die
    // so this multiplier could stand to be lower than the one for fleet-level autoresolve
    public static final float AUTORESOLVE_DAMAGE_MULT = 0.1f;
    public static final float WITHDRAW_ON_DAMAGE_THRESHOLD = 0.4f;
    public static final int MAX_ROUNDS = 10;

    @Getter @Setter protected FactionAPI side1;
    @Getter @Setter protected FactionAPI side2;
    @Getter @Setter protected FactionStrengthReport fsr1;
    @Getter @Setter protected FactionStrengthReport fsr2;
    @Getter @Setter protected LocationAPI loc;
    @Getter @Setter protected MarketAPI target;
    @Getter @Setter protected Random random;

    public static boolean createAndExecute(LocationAPI loc, FactionAPI side1, FactionAPI side2, @Nullable MarketAPI target, Random random) {
        boolean result = new NexWarSimScript(loc, side1, side2, target, random).resolveAction();
        report(String.format("Result of %s vs. %s action in %s: %s", side1.getDisplayName(), side2.getDisplayName(), loc.getNameWithLowercaseType(), result));
        return result;
    }

    public NexWarSimScript(LocationAPI loc, FactionAPI side1, FactionAPI side2, @Nullable MarketAPI target, Random random) {
        this.loc = loc;
        this.side1 = side1;
        this.side2 = side2;
        fsr1 = getFactionStrengthReport(side1, side2, loc, false);
        fsr2 = getFactionStrengthReport(side2, side1, loc, false);
        this.target = target;
        this.random = random;

        CampaignFleetAPI station = Misc.getStationFleet(target);
        if (station != null) {
            if (willFactionSideWithUs(target.getFaction(), side1, side2)) {
                fsr1.addEntry(new FactionStrengthReportEntry(station));
            }
            else if (willFactionSideWithUs(target.getFaction(), side2, side1)) {
                float str1 = Misc.getMemberStrength(station.getFlagship());
                float str2 = station.getEffectiveStrength();
                fsr2.addEntry(new FactionStrengthReportEntry(station));
            }
        }
    }

    public Boolean resolveAction() {
        Boolean result = resolveActionImpl();
        for (NexWarSimScriptListener listener : Global.getSector().getListenerManager().getListeners(NexWarSimScriptListener.class)) {
            listener.reportBattleResolved(this, result);
        }

        return result;
    }

    /**
     * Resolves an action in the current location, damaging all participants (fleets and routes) proportionally. Logic largely taken from
     * @return True if side 1 won, False if side 2 won, null for an undefined result (currently it should not do this)
     */
    public Boolean resolveActionImpl() {
        report(String.format("Autoresolving %s vs. %s action in %s", side1.getDisplayName(), side2.getDisplayName(), loc.getNameWithLowercaseType()));
        report(StringHelper.HR);

        boolean side1Retreat = false, side2Retreat = false;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            //report(String.format("Engagement round %s", round));
            executeAutoresolveRound(round);

            side1Retreat = fsr1.totalStrength <= 0 || fsr1.totalStrength/fsr1.startingStrength < WITHDRAW_ON_DAMAGE_THRESHOLD;
            side2Retreat = fsr2.totalStrength <= 0 || fsr2.totalStrength/fsr2.startingStrength < WITHDRAW_ON_DAMAGE_THRESHOLD;
            if (side1Retreat || side2Retreat) break;
        }

        if (side1Retreat && !side2Retreat) return false;
        if (side2Retreat && !side1Retreat) return true;

        return fsr1.totalStrength > fsr2.totalStrength;
    }

    public void executeAutoresolveRound(int round) {
        FactionStrengthReport winner = null;
        FactionStrengthReport loser = null;

        if (fsr1.totalStrength > fsr2.totalStrength) {
            report(String.format("%s won engagement round %s with strength %.1f vs. %.1f", side1.getDisplayName(), round, fsr1.totalStrength, fsr2.totalStrength));
            winner = fsr1;
            loser = fsr2;
        } else {
            report(String.format("%s won engagement round %s with strength %.1f vs. %.1f", side2.getDisplayName(), round, fsr2.totalStrength, fsr1.totalStrength));
            winner = fsr2;
            loser = fsr1;
        }

        float winnerAdvantage = winner.totalStrength / loser.totalStrength;
        winnerAdvantage = (winnerAdvantage - 1)/2 + 1;
//		if (winnerAdvantage > 2f) winnerAdvantage = 2f;
//		if (winnerAdvantage < 0.5f) winnerAdvantage = 0.5f;
        if (winnerAdvantage > 10f) winnerAdvantage = 10f;
        if (winnerAdvantage < 0.1f) winnerAdvantage = 0.1f;
        //if (winnerAdvantage < 0.1f) winnerAdvantage = 0.1f;

        float damageDealtToWinner = loser.totalStrength / winnerAdvantage;
        float damageDealtToLoser = winner.totalStrength * winnerAdvantage;

        float damMult = AUTORESOLVE_DAMAGE_MULT;
        damageDealtToWinner *= damMult;
        damageDealtToLoser *= damMult;
        report(String.format("Damage to winner: %.1f; damage to loser: %.1f", damageDealtToWinner, damageDealtToLoser));

        // apply damage to random participants, and random members of those participants if they are a fleet
        applyDamageToSide(winner, damageDealtToWinner, winnerAdvantage);
        applyDamageToSide(loser, damageDealtToLoser, 1/winnerAdvantage);

        for (NexWarSimScriptListener listener : Global.getSector().getListenerManager().getListeners(NexWarSimScriptListener.class)) {
            listener.reportRoundResolved(this, round);
        }
    }

    public void applyDamageToSide(FactionStrengthReport side, float damage, float advantageInBattle) {
        List<FactionStrengthReportEntry> participants = new ArrayList<>(side.entries);
        Collections.shuffle(participants, random);
        for (FactionStrengthReportEntry entry : participants) {
            if (damage > 0) {
                report(String.format("  Remaining damage to side %s: %02.2f", side.factionId, damage));
                applyDamageToParticipant(entry, damage, advantageInBattle);

                damage -= entry.strength;
                if (damage < 0) damage = 0;

                entry.recomputeStrength();
            }

            report(String.format("  Participant %s now at strength: %02.2f / %02.2f", entry.name, entry.strength, entry.startingStrength));
            if (entry.strength/ entry.startingStrength < WITHDRAW_ON_DAMAGE_THRESHOLD) {
                side.entries.remove(entry);
            }
        }

        side.recomputeStrength();
    }

    public void applyDamageToParticipant(FactionStrengthReportEntry entry, float maxDamage, float advantageInBattle) {
        if (entry.fleet != null) {
            // Behavior should match BattleAutoresolverPluginImpl
            List<FleetMemberAPI> members = new ArrayList<>(entry.fleet.getFleetData().getMembersListCopy());
            Collections.shuffle(members, random);
            for (FleetMemberAPI member : members) {
                //report(String.format("    Remaining damage to fleet %s: %02.2f", entry.fleet.getNameWithFaction(), maxDamage));
                float thisStrength = Misc.getMemberStrength(member);
                applyDamageToFleetMember(member, thisStrength, maxDamage, advantageInBattle);
                maxDamage -= thisStrength;
                if (maxDamage < 0) maxDamage = 0;
            }

            return;
        }

        float unscathed = 1f;
        float lightDamage = 0f;
        float mediumDamage = 0f;
        float heavyDamage = 0f;
        float disabled = 0f;

        float maxDamageRatio = maxDamage / entry.strength;
        if (maxDamageRatio > 1) maxDamageRatio = 1;

        float mitigation = (random.nextFloat() + random.nextFloat()) * 0.25f;
        float damage = maxDamageRatio * (1 - mitigation);

        Float routeDam = entry.route.getExtra().damage;
        if (routeDam == null) routeDam = 0f;
        routeDam += damage;
        if (routeDam > 1) routeDam = 1f;
        entry.route.getExtra().damage = routeDam;
    }


    public void applyDamageToFleetMember(FleetMemberAPI member, float strength, float maxDamage, float advantageInBattle) {
        ShipHullSpecAPI hullSpec = member.getHullSpec();

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

        float maxDamageRatio = maxDamage / strength;
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

        // advantageInBattle goes from 0.5 (bad) to 2 (good)
        unscathed *= advantageInBattle;
        lightDamage *= advantageInBattle;

        float shieldRatio = 0.5f;   // shortcut

        // shieldRatio goes from 0 (no shields/no flux) to 1 (shields dominate hull/armor)
        // shieldRatio at 0.5 roughly indicates balanced shields and hull/armor effectiveness

        disabled *= 1.5f - shieldRatio * 1f;
        heavyDamage *= 1.4f - shieldRatio * 0.8f;
        mediumDamage *= 1.3f - shieldRatio * 0.6f;
        lightDamage *= 1.2f - shieldRatio * 0.4f;
        unscathed *= 0.9f + shieldRatio * 0.2f;


        if (member.isStation()) {
            heavyDamage += disabled;
            disabled = 0f; // only disabled when heavy damage takes out all modules
        }


        WeightedRandomPicker<BattleAutoresolverPluginImpl.FleetMemberBattleOutcome> picker = new WeightedRandomPicker<BattleAutoresolverPluginImpl.FleetMemberBattleOutcome>();

        picker.add(BattleAutoresolverPluginImpl.FleetMemberBattleOutcome.DISABLED, disabled);
        picker.add(BattleAutoresolverPluginImpl.FleetMemberBattleOutcome.HEAVY_DAMAGE, heavyDamage);
        picker.add(BattleAutoresolverPluginImpl.FleetMemberBattleOutcome.MEDIUM_DAMAGE, mediumDamage);
        picker.add(BattleAutoresolverPluginImpl.FleetMemberBattleOutcome.LIGHT_DAMAGE, lightDamage);
        picker.add(BattleAutoresolverPluginImpl.FleetMemberBattleOutcome.UNSCATHED, unscathed);


        //report(String.format("Disabled: %d, Heavy: %d, Medium: %d, Light: %d, Unscathed: %d (Shield ratio: %3.2f)",
        //        (int) disabled, (int) heavyDamage, (int) mediumDamage, (int) lightDamage, (int) unscathed, shieldRatio));

        BattleAutoresolverPluginImpl.FleetMemberBattleOutcome outcome = picker.pick();

        float damage = 0f;

        member.getStatus().resetDamageTaken();


        switch (outcome) {
            case DISABLED:
                //report(String.format("    %40s: disabled", member.getVariant().getFullDesignationWithHullName()));
                damage = 1f;
                break;
            case HEAVY_DAMAGE:
                //report(String.format("    %40s: heavy damage", member.getVariant().getFullDesignationWithHullName()));
                damage = 0.7f + random.nextFloat() * 0.1f;
                break;
            case MEDIUM_DAMAGE:
                //report(String.format("    %40s: medium damage", member.getVariant().getFullDesignationWithHullName()));
                damage = 0.45f + random.nextFloat() * 0.1f;
                break;
            case LIGHT_DAMAGE:
                //report(String.format("    %40s: light damage", member.getVariant().getFullDesignationWithHullName()));
                damage = 0.2f + random.nextFloat() * 0.1f;
                break;
            case UNSCATHED:
                //report(String.format("    %40s: unscathed", member.getVariant().getFullDesignationWithHullName()));
                damage = 0f;
                break;
        }

        //damage = 0.8f;
        BattleAutoresolverPluginImpl.applyDamageToFleetMember(member, damage);
    }

    protected static void report(String str) {
        if (!ExerelinModPlugin.isNexDev) return;
        log.info(str);
    }

    public static FactionStrengthReport getFactionStrengthReport(FactionAPI faction, FactionAPI enemy, LocationAPI loc) {
        return getFactionStrengthReport(faction, enemy, loc, true);
    }

    /**
     * Gets a list of all the military forces present in a system that will side with our faction against the enemy, and their total strength.
     * @param faction
     * @param enemy
     * @param loc
     * @return
     */
    public static FactionStrengthReport getFactionStrengthReport(FactionAPI faction, FactionAPI enemy, LocationAPI loc, boolean includeSFNotInLoc) {
        FactionStrengthReport report = new FactionStrengthReport(faction.getId());

        Set<CampaignFleetAPI> seenFleets = new HashSet<>();
        Set<RouteData> seenRoutes = new HashSet<>();
        for (CampaignFleetAPI fleet : loc.getFleets()) {
            if (!willFactionSideWithUs(fleet.getFaction(), faction, enemy)) continue;
            if (fleet.isStationMode()) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET)) continue;
            report.addEntry(new FactionStrengthReportEntry(fleet));
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SMUGGLER)) continue;
            if (fleet.isPlayerFleet()) continue;

            seenFleets.add(fleet);
        }

        for (RouteData route : getInstance().getRoutesInLocation(loc)) {
            if (route.getActiveFleet() != null && seenFleets.contains(route.getActiveFleet())) continue;

            OptionalFleetData data = route.getExtra();
            if (data == null) continue;
            if (route.getFactionId() == null) continue;
            FactionAPI routeFac = Global.getSector().getFaction(route.getFactionId());
            if (!willFactionSideWithUs(routeFac, faction, enemy)) continue;

            if (data.strength != null) {
                report.addEntry(new FactionStrengthReportEntry(route));
                seenRoutes.add(route);
            }
        }

        if (!includeSFNotInLoc) return report;

        for (IntelInfoPlugin iip : Global.getSector().getIntelManager().getIntel(SpecialForcesIntel.class)) {
            SpecialForcesIntel sf = (SpecialForcesIntel)iip;
            RouteData route = sf.getRoute();
            CampaignFleetAPI fleet = route.getActiveFleet();
            if (seenFleets.contains(fleet) || seenRoutes.contains(route)) {
                continue;
            }
            if (sf.getFaction() != faction) continue;

            if (sf.getRouteAI().getCurrentTask() == null) continue;
            if (sf.getRouteAI().getCurrentTask().getSystem() != loc) continue;

            if (fleet != null) {
                report.addEntry(new FactionStrengthReportEntry(fleet));
                seenFleets.add(fleet);
            } else {
                report.addEntry(new FactionStrengthReportEntry(sf.getName(), route));
                seenRoutes.add(route);
            }
        }

        return report;
    }

    public static float getFactionAndAlliedStrength(String factionId, String enemyFactionId, StarSystemAPI system) {
        return getFactionAndAlliedStrength(Global.getSector().getFaction(factionId), Global.getSector().getFaction(enemyFactionId), system);
    }

    public static float getFactionAndAlliedStrength(FactionAPI faction, FactionAPI enemyFaction, StarSystemAPI system) {
        float strength = 0f;

//		if (system.getName().toLowerCase().contains("naraka") && Factions.PIRATES.equals(faction.getId())) {
//			System.out.println("wefwefwe");
//		}

        Set<CampaignFleetAPI> seenFleets = new HashSet<CampaignFleetAPI>();
        for (CampaignFleetAPI fleet : system.getFleets()) {
            FactionAPI fleetFaction = fleet.getFaction();
            if (fleet.isStationMode()) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET)) continue;
            if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SMUGGLER)) continue;

            if (fleet.isPlayerFleet()) continue;

            if (!willFactionSideWithUs(fleetFaction, faction, enemyFaction)) continue;

            strength += fleet.getEffectiveStrength();

            seenFleets.add(fleet);
        }

        for (RouteData route : RouteManager.getInstance().getRoutesInLocation(system)) {
            if (route.getActiveFleet() != null && seenFleets.contains(route.getActiveFleet())) continue;

            OptionalFleetData data = route.getExtra();
            if (data == null) continue;
            if (route.getFactionId() == null) continue;
            FactionAPI routeFaction = Global.getSector().getFaction(route.getFactionId());
            if (!willFactionSideWithUs(routeFaction, faction, enemyFaction)) continue;

            strength += data.getStrengthModifiedByDamage();
        }

        return strength;
    }

    /**
     * @param factionToConsider
     * @param us
     * @param them Can be null, but this may lead to inaccurate results.
     * @return
     */
    public static boolean willFactionSideWithUs(FactionAPI factionToConsider, FactionAPI us, @Nullable FactionAPI them) {
        if (factionToConsider == us) return true;
        if (factionToConsider == them) return false;

        // player will always side with their commissioning faction and vice-versa
        FactionAPI commFaction = Misc.getCommissionFaction();
        if (commFaction != null) {
            if (factionToConsider.isPlayerFaction() && commFaction == us) return true;
            if (us.isPlayerFaction() && factionToConsider == commFaction) return true;
        }

        if (them == null) {
            return AllianceManager.areFactionsAllied(factionToConsider.getId(), us.getId());
        }

        boolean hostileToUs = factionToConsider.isHostileTo(us);
        boolean hostileToThem = factionToConsider.isHostileTo(them);
        return !hostileToUs && hostileToThem;
    }


    // =================================================================================================================
    // static classes

    public static class FactionStrengthReport {
        public String factionId;
        public List<FactionStrengthReportEntry> entries = new ArrayList<>();
        public float totalStrength;
        public float startingStrength;

        public void addEntry(FactionStrengthReportEntry entry) {
            entries.add(entry);
            if (startingStrength == totalStrength) startingStrength += entry.strength;
            totalStrength += entry.strength;
        }

        public void removeEntry(FactionStrengthReportEntry entry) {
            entries.remove(entry);
            totalStrength -= entry.strength;
        }

        public void recomputeStrength() {
            totalStrength = 0;
            for (FactionStrengthReportEntry entry : entries) {
                totalStrength += entry.strength;
            }
        }

        public FactionStrengthReport(String factionId) {
            this.factionId = factionId;
        }
    }

    public static class FactionStrengthReportEntry {
        public String name;
        public CampaignFleetAPI fleet;
        public RouteData route;
        public float strength;
        public float startingStrength;

        public FactionStrengthReportEntry(CampaignFleetAPI fleet) {
            this.fleet = fleet;
            name = fleet.getFullName();
            strength = fleet.getEffectiveStrength();
            startingStrength = strength;
        }

        public FactionStrengthReportEntry(RouteData route) {
            this(String.format("%s route from %s", route.getFactionId(), route.getMarket() != null? route.getMarket().getName() : ""), route);
        }

        public FactionStrengthReportEntry(String name, RouteData route) {
            this.name = name;
            this.route = route;

            float strength = route.getExtra().strength;
            if (route.getExtra().damage != null) strength *= (1f - route.getExtra().damage);
            this.strength = strength;
            startingStrength = strength;
        }

        public void recomputeStrength() {
            if (fleet != null) {
                strength = fleet.getEffectiveStrength();
            }
            else if (route != null) {
                float strength = route.getExtra().strength;
                if (route.getExtra().damage != null) strength *= (1f - route.getExtra().damage);
                this.strength = strength;
            }
        }
    }
}
