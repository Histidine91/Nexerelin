package exerelin.combat.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.impl.hullmods.ShardSpawner;
import com.fs.starfarer.api.impl.hullmods.ShardSpawner.ShardType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.StringHelper;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SilverlightDashStats extends BaseShipSystemScript {

    public static final Object KEY_SHIP = new Object();
    public static final float SPEED_MULT = 2;
    public static final float TURN_MULT = 2;
    public static final float ACCEL_MULT = 2;
    public static final float TIMEFLOW_MULT = 2;
    public static final float DAMPER_MULT = 0.5f;
    public static final int MAX_ASPECTS = 7;
    public static final float SHARD_BURN_TIME = 0.75f;
    public static final Color JITTER_COLOR = new Color(168,255,192,55);
    public static final Color JITTER_UNDER_COLOR = new Color(168,255,192,155);
    public static final Color WARP_COLOR = new Color(192,208,255,224);
    public static final boolean USE_SHARD_EVERYFRAME = true;

    protected boolean triedAspectSpawnThisSystemUse;
    protected boolean registeredPlugin = !USE_SHARD_EVERYFRAME;

    protected Set<ShipAPI> aspects = new HashSet<>();

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        stats.getMaxSpeed().modifyMult(id, SPEED_MULT * effectLevel);
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

        applyGfx(stats, state, effectLevel);

        if (state == State.ACTIVE) {
            detachShardsIfNeeded((ShipAPI)stats.getEntity(), true);
            processAspectSpawn((ShipAPI)stats.getEntity());
        }

        if (!registeredPlugin) {
            registeredPlugin = true;
            Global.getCombatEngine().addPlugin(new ShardMonitorPlugin((ShipAPI)stats.getEntity()));
        }
    }

    public void unapply(MutableShipStatsAPI stats, String id) {
        stats.getMaxSpeed().unmodify(id);
        stats.getMaxTurnRate().unmodify(id);
        stats.getAcceleration().unmodify(id);
        stats.getTurnAcceleration().unmodify(id);
        stats.getTimeMult().unmodify(id);
        stats.getZeroFluxMinimumFluxLevel().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getEmpDamageTakenMult().unmodify(id);
        triedAspectSpawnThisSystemUse = false;
    }

    /**
     * Check if we should summon an Aspect wing, and if so, pick the right one and spawn it.
     * @param ship
     */
    public void processAspectSpawn(ShipAPI ship) {
        if (ship == null) return;
        if (triedAspectSpawnThisSystemUse) return;
        triedAspectSpawnThisSystemUse = true;

        CombatEngineAPI engine = Global.getCombatEngine();
        if (engine == null) return; // I don't trust Alex

        pruneAspectList(engine);
        if (aspects.size() >= MAX_ASPECTS) return;

        String wingId = pickWingId(ship);
        if (wingId == null) return; //wingId = "aspect_attack_wing";
        ShipAPI leader = engine.getFleetManager(ship.getOwner()).spawnShipOrWing(wingId, ship.getLocation(), ship.getFacing(), 3f);
        FighterWingAPI myWing = leader.getWing();
        aspects.addAll(myWing.getWingMembers());
        //Global.getLogger(this.getClass()).info("Now have " + aspects.size() + " Aspects");

        // we really don't need the extra FX
        for (ShipAPI fighter : myWing.getWingMembers()) {
            //engine.addSmoothParticle(fighter.getLocation(), new Vector2f(), 60, 1, 2, WARP_COLOR);
            //engine.addNegativeSwirlyNebulaParticle(fighter.getLocation(), fighter.getVelocity(), 20, 2,
            //                    1,2, 3, WARP_COLOR);
            //engine.spawnExplosion(fighter.getLocation(), fighter.getVelocity(), WARP_COLOR, 60, 2);
        }
        engine.addPlugin(new AspectLauncherPlugin(myWing));
    }

    /**
     * Purge the Aspects that are no longer alive from the list, to count the number of live Aspects.
     * @param engine
     */
    protected void pruneAspectList(CombatEngineAPI engine) {
        List<ShipAPI> toRemove = new ArrayList<>();
        for (ShipAPI aspect : aspects) {
            if (!aspect.isAlive()) toRemove.add(aspect);
        }
        //Global.getLogger(this.getClass()).info("Removing " + toRemove.size() + " Aspects");
        aspects.removeAll(toRemove);
    }

    /**
     * Gets the wing ID of the Aspects we should spawn (similar to Shard Spawner).
     * @param ship
     * @return
     */
    protected String pickWingId(ShipAPI ship) {
        WeightedRandomPicker<ShardType> typePicker = new ShardSpawner().getTypePickerBasedOnLocalConditions(ship);
        ShardType type = typePicker.pick();
        if (type == null) return null;
        WeightedRandomPicker<String> wingPicker = ShardSpawner.variantData.get(ShipAPI.HullSize.FIGHTER).variants.get(type);
        if (wingPicker == null) return null;
        return wingPicker.pick();
    }

    public void applyGfx(MutableShipStatsAPI stats, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
        } else {
            return;
        }

        ship.fadeToColor(KEY_SHIP, new Color(192,255,224,192), 0.1f, 0.1f, effectLevel);
        //ship.setWeaponGlow(effectLevel, new Color(100,165,255,255), EnumSet.of(WeaponAPI.WeaponType.BALLISTIC, WeaponAPI.WeaponType.ENERGY, WeaponAPI.WeaponType.MISSILE));
        ship.getEngineController().fadeToOtherColor(KEY_SHIP, new Color(0,0,0,0), new Color(0,0,0,0), effectLevel, 0.5f * effectLevel);

        // temporal shell copypasta
        float jitterLevel = effectLevel;
        float jitterRangeBonus = 0;
        float maxRangeBonus = 10f;
        if (state == State.IN) {
            jitterLevel = effectLevel / (1f / ship.getSystem().getChargeUpDur());
            if (jitterLevel > 1) {
                jitterLevel = 1f;
            }
            jitterRangeBonus = jitterLevel * maxRangeBonus;
        } else if (state == State.ACTIVE) {
            jitterLevel = 1f;
            jitterRangeBonus = maxRangeBonus;
        } else if (state == State.OUT) {
            jitterRangeBonus = jitterLevel * maxRangeBonus;
        }
        jitterLevel = (float) Math.sqrt(jitterLevel);
        effectLevel *= effectLevel;

        ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        String key = "silverlightDash_desc";
        String str;
        if (index == 0) {
            float shipTimeMult = 1f + (TIMEFLOW_MULT - 1f) * effectLevel;
            str = StringHelper.getString("nex_ships", key + "TimeMob");
            return new StatusData(String.format(str, shipTimeMult), false);
        }
        if (index == 1) {
            str = StringHelper.getString("nex_ships", key + "ZFSB");
            return new StatusData(str, false);
        }
        if (index == 2) {
            float percent = (1f - DAMPER_MULT) * effectLevel * 100;
            str = StringHelper.getString("nex_ships", key + "Damper");
            return new StatusData(String.format(str, percent), false);
        }
        return null;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        CombatEngineAPI engine = Global.getCombatEngine();
        //if (engine == null) return null;
        pruneAspectList(engine);
        String str = StringHelper.getString("nex_ships", "silverlightDash_descAspects");
        return (String.format(str, aspects.size(), MAX_ASPECTS));
    }

    public static boolean detachShardsIfNeeded(ShipAPI ship, boolean fromSystemUse) {
        if (!ship.isAlive()) {
            return true;
        }

        // launch if main ship or any of the modules are at 40% or higher hard flux
        // or if significant enemy presence?
        boolean wantLaunch = ship.getHardFluxLevel() > 0.4f || ship.getFluxLevel() > 0.8f;
        if (!USE_SHARD_EVERYFRAME || fromSystemUse) {
            wantLaunch |= ship.areSignificantEnemiesInRange();
        }
        if (!wantLaunch && !fromSystemUse) {
            for (ShipAPI module : ship.getChildModulesCopy()) {
                if (module.getHardFluxLevel() > 0.4f || module.getHullLevel() < 1) {
                    wantLaunch = true;
                    break;
                }
            }
        }

        if (wantLaunch) {
            CombatEngineAPI engine = Global.getCombatEngine();
            for (ShipAPI module : ship.getChildModulesCopy()) {
                module.setStationSlot(null);
                //module.getVelocity().set(ship.getVelocity());   // breaks the travel drive?
                module.turnOnTravelDrive(SHARD_BURN_TIME);
                engine.addPlugin(new NoCollidePlugin(module, SHARD_BURN_TIME));
            }
            return true;
        }

        return false;
    }

    /**
     * Handles the GFX and no-collide etc. for the spawned Aspects.
     */
    public static class AspectLauncherPlugin extends BaseEveryFrameCombatPlugin {

        public static final Color JITTER_COLOR = ShardSpawner.JITTER_COLOR;
        public static final float EFFECT_TIME = 2;

        protected FighterWingAPI wing;
        protected CollisionClass col;
        protected float time = 0;

        public AspectLauncherPlugin(FighterWingAPI wing) {
            this.wing = wing;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            time += amount;
            if (time > EFFECT_TIME) {
                for (ShipAPI ship : wing.getWingMembers()) {
                    ship.setAlphaMult(1f);
                    ship.setHoldFire(false);
                    ship.setCollisionClass(col);
                    ship.getMutableStats().getHullDamageTakenMult().unmodifyMult("ShardSpawnerInvuln");
                }
                Global.getCombatEngine().removePlugin(this);
                return;
            }

            float progress = 1 - (time/EFFECT_TIME);
            float jitterLevel = progress;
            if (jitterLevel < 0.5f) {
                jitterLevel *= 2f;
            } else {
                jitterLevel = (1f - jitterLevel) * 2f;
            }

            float jitterRange = 1f - progress;
            float maxRangeBonus = 50f;
            float jitterRangeBonus = jitterRange * maxRangeBonus;
            Color c = JITTER_COLOR;

            for (ShipAPI ship : wing.getWingMembers()) {
                if (col == null) {
                    col = ship.getCollisionClass();
                }
                ship.setCollisionClass(CollisionClass.NONE);

                ship.getMutableStats().getHullDamageTakenMult().modifyMult("ShardSpawnerInvuln", 0f);
                if (progress > 0.75f){
                    ship.setCollisionClass(col);
                    ship.getMutableStats().getHullDamageTakenMult().unmodifyMult("ShardSpawnerInvuln");
                }
                ship.setJitter(this, c, jitterLevel, 25, 0f, jitterRangeBonus);
                ship.setAlphaMult(1f - progress);
            }
        }
    }

    public static class ShardMonitorPlugin extends BaseEveryFrameCombatPlugin {

        protected ShipAPI ship;

        public ShardMonitorPlugin(ShipAPI ship) {
            this.ship = ship;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            if (detachShardsIfNeeded(ship, false)) {
                Global.getCombatEngine().removePlugin(this);
            }
        }
    }

    public static class NoCollidePlugin extends BaseEveryFrameCombatPlugin {

        protected ShipAPI ship;
        protected float timer;
        protected CollisionClass col;
        protected float elapsed;

        public NoCollidePlugin(ShipAPI ship, float timer) {
            this.ship = ship;
            col = CollisionClass.SHIP;   //ship.getCollisionClass();
            this.timer = timer;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            elapsed += amount;
            if (elapsed >= timer) {
                ship.setCollisionClass(col);
                Global.getCombatEngine().removePlugin(this);
                return;
            }
            ship.setCollisionClass(CollisionClass.FIGHTER);
        }
    }
}


