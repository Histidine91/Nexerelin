package exerelin.combat.shipsystems;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

import java.awt.*;

public class SilverlightDashStats extends BaseShipSystemScript {

    public static final Object KEY_SHIP = new Object();
    public static final float SPEED_MULT = 2;
    public static final float TURN_MULT = 2;
    public static final float ACCEL_MULT = 2;
    public static final float TIMEFLOW_MULT = 2;
    public static final float DAMPER_MULT = 0.5f;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        //stats.getMaxSpeed().modifyMult(id, SPEED_MULT * effectLevel);
        stats.getMaxTurnRate().modifyMult(id, TURN_MULT * effectLevel);
        stats.getTurnAcceleration().modifyMult(id, ACCEL_MULT * effectLevel);
        stats.getAcceleration().modifyMult(id, TURN_MULT * effectLevel);

        float shipTimeMult = 1f + (TIMEFLOW_MULT - 1f) * effectLevel;
        stats.getTimeMult().modifyMult(id, shipTimeMult);

        stats.getZeroFluxMinimumFluxLevel().modifyFlat(id, 2);

        float damMult = 1f - (1f - DAMPER_MULT) * effectLevel;
        stats.getHullDamageTakenMult().modifyMult(id, damMult);
        stats.getArmorDamageTakenMult().modifyMult(id, damMult);
        stats.getEmpDamageTakenMult().modifyMult(id, damMult);

        applyGfx(stats, effectLevel);
    }

    public void applyGfx(MutableShipStatsAPI stats, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        } else {
            return;
        }

        ship.fadeToColor(KEY_SHIP, new Color(192,255,224,192), 0.1f, 0.1f, effectLevel);
        //ship.setWeaponGlow(effectLevel, new Color(100,165,255,255), EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY, WeaponAPI.WeaponType.MISSILE));
        ship.getEngineController().fadeToOtherColor(KEY_SHIP, new Color(0,0,0,0), new Color(0,0,0,0), effectLevel, 0.5f * effectLevel);
        ship.setJitterUnder(KEY_SHIP, new Color(168,255,192,255), effectLevel, 15, 0f, 15f);
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getAcceleration().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
        stats.getZeroFluxMinimumFluxLevel().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            float shipTimeMult = 1f + (TIMEFLOW_MULT - 1f) * effectLevel;
            return new StatusData(String.format("%.1fx time mult and mobility", shipTimeMult), false);
        }
        if (index == 1) {
            return new StatusData("always zero flux speed boost", false);
        }
        if (index == 2) {
            float percent = (1f - DAMPER_MULT) * effectLevel * 100;
            return new StatusData((int) percent + "% less damage taken", false);
        }
        return null;
    }
}


