package exerelin.campaign.intel.groundbattle.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.groundbattle.*;
import exerelin.campaign.intel.specialforces.namer.PlanetNamer;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMath;

import java.util.ArrayList;

import static exerelin.campaign.intel.groundbattle.GroundBattleIntel.getString;

public class GroundUnitPlugin extends BaseGroundBattlePlugin {

    protected GroundUnit unit;

    /**
     * Should only be used when instantiating the class by name.
     */
    public GroundUnitPlugin() {}

    public GroundUnitPlugin(GroundUnit unit) {
        this.unit = unit;
        this.intel = unit.getIntel();
    }

    public String generateName() {
        GroundUnit.UnitSize size = unit.getIntel().getUnitSize();
        String name = Misc.ucFirst(size.getName());
        int num = unit.getIndex() + 1;
        switch (size) {
            case PLATOON:
            case COMPANY:
                int alphabetIndex = (num - 1) % 26;
                return GBDataManager.NATO_ALPHABET.get(alphabetIndex) + " " + name;
            case BATTALION:
                return num + PlanetNamer.getSuffix(num) + " " + name;
            case REGIMENT:
                return Global.getSettings().getRoman(num) + " " + name;
            default:
                return name + " " + num;
        }
    }

    public float setStartingMorale() {
        float morale = GBConstants.BASE_MORALE;
        if (unit.isPlayer()) {
            float xp = intel.getPlayerData().getXpTracker().data.getXPLevel();
            morale += xp * GBConstants.XP_MORALE_BONUS;
        }

        return morale;
    }

    public MutableStat getAttackStat() {
        MutableStat stat = new MutableStat(0);

        if (unit.isAttackPrevented()) {
            stat.modifyMult("disabled", 0, getString("unitCard_tooltip_atkbreakdown_disabled"));
            return stat;
        }

        float baseStr = unit.getBaseStrength();
        stat.modifyFlat("base", baseStr, getString("unitCard_tooltip_atkbreakdown_base"));

        IndustryForBattle ifb = unit.getLocation();

        if (ifb != null)
        {
            if (ifb.heldByAttacker == unit.isAttacker()) {
                // apply strength modifiers to defense instead, not attack
				/*
				float industryMult = ifb.getPlugin().getStrengthMult();
				if (industryMult != 1) {
					stat.modifyMult("industry", industryMult, ifb.ind.getCurrentName());
				}
				*/
            }
        }
        GroundUnitDef unitDef = unit.getUnitDef();
        if (unitDef.offensiveStrMult != 1) {	// heavy unit bonus on offensive
            boolean offensiveBonus = ifb == null && unit.isAttacker();
            offensiveBonus |= ifb != null && ifb.heldByAttacker != unit.isAttacker();
            if (offensiveBonus) {
                modifyAttackStatWithDesc(stat, "heavy_offensive", unitDef.offensiveStrMult);
            }
        }

        if (intel.isCramped() && unitDef.crampedStrMult != 1) {
            modifyAttackStatWithDesc(stat, "heavy_cramped", unitDef.crampedStrMult);
        }

        float moraleMult = NexUtilsMath.lerp(1 - GBConstants.MORALE_ATTACK_MOD,
                1 + GBConstants.MORALE_ATTACK_MOD, unit.getMorale());
        modifyAttackStatWithDesc(stat, "morale", moraleMult);

        if (unit.isReorganizing()) {
            modifyAttackStatWithDesc(stat, "reorganizing", GBConstants.REORGANIZING_DMG_MULT);
        }

        for (GroundBattlePlugin plugin : intel.getPlugins()) {
            plugin.modifyAttackStat(unit, stat);
        }

        return stat;
    }

    public StatBonus getAttackStatBonus() {
        StatBonus bonus = new StatBonus();
        GroundUnitDef unitDef = unit.getUnitDef();

        // unit from a fleet: apply fleet planetary operations bonuses, if fleet is in range
        // note: a side effect of the in-range requirement is that the Ground Operations skill stops applying if player is out of range
        if (unit.getFleet() != null && unit.isFleetInRange()) {
            bonus = NexUtils.cloneStatBonus(unit.getFleet().getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD));
        }
        // attacker unit not from a fleet (usually a rebel?)
        // do nothing
        else if (unit.isAttacker()) {

        }
        // defender unit not from a fleet
        else {
            // start by applying vanilla-type ground defense stat bonuses
            bonus = NexUtils.cloneStatBonus(intel.getMarket().getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD));

            // purge all flat bonuses
            bonus.getFlatBonuses().clear();
            // purge all mult and percent bonuses that come from an industry
            for (MutableStat.StatMod mod : new ArrayList<>(bonus.getMultBonuses().values()))
            {
                if (mod.getSource().startsWith("ind_")) {
                    bonus.unmodifyMult(mod.getSource());
                }
            }
            for (MutableStat.StatMod mod : new ArrayList<>(bonus.getPercentBonuses().values()))
            {
                if (mod.getSource().startsWith("ind_")) {
                    bonus.unmodifyPercent(mod.getSource());
                }
            }
        }

        // apply XP bonuses
        if (unit.isPlayer()) {
            unit.substituteLocalXPBonus(bonus, true);
        }
        else if (unit.isAttacker()) {
            // generic XP bonus for non-player attacker units (assumes 50% XP), except rebels which use the defender bonus
            if (unitDef.hasTag(GBConstants.TAG_REBEL)) {
                unit.injectXPBonus(bonus, GBConstants.DEFENSE_STAT, true);
            } else {
                unit.injectXPBonus(bonus, GBConstants.OFFENSE_STAT, true);
            }
        }
        else {
            // generic XP bonus for defender units (assumes 25% XP)
            unit.injectXPBonus(bonus, GBConstants.DEFENSE_STAT, true);
        }

        for (GroundBattlePlugin plugin : intel.getPlugins()) {
            plugin.modifyAttackStatBonus(unit, bonus);
        }

        return bonus;
    }

    public float getAttackStrength() {
        if (unit.isAttackPrevented()) return 0;

        MutableStat stat = getAttackStat();

        float output = stat.getModifiedValue();

        StatBonus bonus = getAttackStatBonus();
        if (bonus != null) {
            output = bonus.computeEffective(output);
        }

        output = intel.getSide(unit.isAttacker()).getDamageDealtMod().computeEffective(output);

        return output;
    }

    public float getAdjustedMoraleDamageTaken(float dmg) {
        float mult = 1;
        if (unit.isPlayer()) {
            //mult -= GBConstants.MORALE_DAM_XP_REDUCTION_MULT * PlayerFleetPersonnelTracker.getInstance().getMarineData().getXPLevel();
        }
        if (!unit.isAttacker()) {
            dmg = intel.getMarket().getStats().getDynamic().getMod(GBConstants.STAT_MARKET_MORALE_DAMAGE).computeEffective(dmg);
        }

        mult /= unit.getUnitDef().moraleMult;
        dmg = intel.getSide(unit.isAttacker()).getMoraleDamTakenMod().computeEffective(dmg);
        dmg *= mult;

        if (!unit.isAttacker()) dmg *= GBConstants.DEFENDER_MORALE_DMG_MULT;

        for (GroundBattlePlugin plugin : intel.getPlugins()) {
            dmg = plugin.modifyMoraleDamageReceived(unit, dmg);
        }
        return dmg;
    }

    public MutableStat getDefenseStat() {
        MutableStat stat = new MutableStat(0);
        IndustryForBattle location = unit.getLocation();
        GroundUnitDef unitDef = unit.getUnitDef();

        if (location != null && unit.isAttacker() == location.heldByAttacker)
            stat.modifyMult(location.getIndustry().getId(), 1/location.getPlugin().getStrengthMult(),
                    location.getIndustry().getCurrentName());

        if (unitDef.damageTakenMult != 1) {
            stat.modifyMult("unitDefMult", unitDef.damageTakenMult,
                    getString("unitCard_tooltip_defbreakdown_defMult"));
        }

        for (GroundBattlePlugin plugin : intel.getPlugins()) {
            stat = plugin.modifyDamageReceived(unit, stat);
        }
        return stat;
    }

    public StatBonus getDefenseStatBonus() {
        StatBonus bonus = new StatBonus();
        if (unit.getFleet() != null && unit.isFleetInRange()) {
            bonus.applyMods(unit.getFleet().getStats().getDynamic().getStat(Stats.PLANETARY_OPERATIONS_CASUALTIES_MULT));
        }
        if (unit.isPlayer()) {
            unit.substituteLocalXPBonus(bonus, false);
        }
        else if (unit.isAttacker()) {
            if (unit.getUnitDef().hasTag(GBConstants.TAG_REBEL)) {
                unit.injectXPBonus(bonus, GBConstants.DEFENSE_STAT, false);
            }
            else if (!unit.isPlayer()) {
                unit.injectXPBonus(bonus, GBConstants.OFFENSE_STAT, false);
            }
        }
        else {
            unit.injectXPBonus(bonus, GBConstants.DEFENSE_STAT, false);
        }

        return bonus;
    }

    public float getAdjustedDamageTaken(float dmg) {
        MutableStat stat = getDefenseStat();
        stat.setBaseValue(dmg);

        dmg = stat.getModifiedValue();

        StatBonus bonus = getDefenseStatBonus();
        if (bonus != null) {
            dmg = bonus.computeEffective(stat.getModifiedValue());
        }

        dmg = intel.getSide(unit.isAttacker()).getDamageTakenMod().computeEffective(dmg);
        return dmg;
    }

    protected void modifyAttackStatWithDesc(MutableStat stat, String id, float mult)
    {
        String desc = getString("unitCard_tooltip_atkbreakdown_" + id);
        stat.modifyMult(id, mult, desc);
    }

    public String getBackgroundIcon() {
        return unit.getUnitDef().getSprite();
    }

    public static GroundUnitPlugin initPlugin(GroundUnit unit) {
        GroundUnitPlugin plugin = (GroundUnitPlugin) NexUtils.instantiateClassByName(unit.getUnitDef().pluginClass);
        plugin.unit = unit;
        plugin.intel = unit.getIntel();

        return plugin;
    }
}
