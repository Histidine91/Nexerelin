package exerelin.combat.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI;
import lombok.extern.log4j.Log4j;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

@Log4j
public class AlicornEffect implements OnFireEffectPlugin, OnHitEffectPlugin, EveryFrameWeaponEffectPlugin {

    public static final boolean DEBUG_MODE = true;

    public static final String DATA_KEY_PREFIX = "nex_alicorn_data";
    public static final int MAIN_PROJ_DAMAGE_DIVISOR = 2;
    public static final int NUM_SUBMUNITIONS = 8;
    public static final float SPREAD_RADIUS = 300;
    public static final float SPREAD_INCREMENT_DEGREES = 8;
    public static final Color DETONATE_COLOR = new Color(160, 192, 255, 255);
    protected static final Vector2f ZERO = new Vector2f();

    protected List<MissileAPI> debugMissiles = new ArrayList<>();

    @Override
    public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        WeaponData data = getOrCreateWeaponData(weapon, engine);
        float timer = computeFuzeTimer(projectile, weapon, engine);
        data.projectiles.add(new ProjectileData(projectile, timer));
        projectile.setDamageAmount(projectile.getDamageAmount()/MAIN_PROJ_DAMAGE_DIVISOR);
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point,
                      boolean shieldHit, ApplyDamageResultAPI damageResult, CombatEngineAPI engine) {
        boolean small = projectile.getProjectileSpecId().contains("_sub");
        float sizeMult = small ? 0.7f : 1f;
        float duration = small ? 0.9f : 1.2f;
        engine.spawnExplosion(point, ZERO, DETONATE_COLOR, 300 * sizeMult, duration);
        engine.spawnExplosion(point, ZERO, Color.white, 120 * sizeMult, duration);
        Global.getSoundPlayer().playSound("nex_alicorn_impact" + (small ? "_light" : ""), 1, 1, point, ZERO);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        WeaponData wd = getOrCreateWeaponData(weapon, engine);
        Iterator<ProjectileData> projIter = wd.projectiles.iterator();
        while (projIter.hasNext()) {
            ProjectileData data = projIter.next();
            DamagingProjectileAPI proj = data.projectile;
            if (proj.didDamage() || !engine.isInPlay(proj)) {
                //log.info("Terminating projectile watch as already did damage or no longer in play");
                projIter.remove();
                continue;
            }
            data.ttl -= amount;
            if (data.ttl <= 0) {
                splitProjectile(data.projectile, weapon, engine);
                projIter.remove();
            }
        }

        checkDebugMissiles(engine);
    }

    protected void checkDebugMissiles(CombatEngineAPI engine) {
        if (!DEBUG_MODE) return;

        Iterator<MissileAPI> iter = debugMissiles.iterator();
        if (iter.hasNext()) {
            MissileAPI missile = iter.next();
            if (missile.didDamage()) {

            }
        }

    }

    public Vector2f getSubmunitionVector(int index, Vector2f baseVel) {

        // random angle + random velocity mult mode
        float increment = SPREAD_INCREMENT_DEGREES * (index)/2 + MathUtils.getRandomNumberInRange(-3, 3);
        if (index < 2) increment *= 1.5f;
        boolean left = index%2 == 0;
        if (left) increment = -increment;
        Vector2f thisVel = new Vector2f(baseVel);
        VectorUtils.rotate(thisVel, increment);
        thisVel.scale(MathUtils.getRandomNumberInRange(0.5f, 1.3f));

        // random velocity in circle mode
        //Vector2f thisVel = new Vector2f(baseVel);
        //Vector2f spread = MathUtils.getRandomPointInCircle(ZERO, SPREAD_RADIUS);
        //thisVel.translate(spread.x, spread.y);

        return thisVel;
    }

    public void splitProjectile(DamagingProjectileAPI dam, WeaponAPI weapon, CombatEngineAPI engine) {
        Vector2f loc = dam.getLocation();
        Vector2f vel = dam.getVelocity();
        engine.spawnExplosion(loc, ZERO, DETONATE_COLOR, 250, 1f);
        engine.spawnExplosion(loc, ZERO, Color.white, 100, 1f);
        Global.getSoundPlayer().playSound("nex_alicorn_split", 1, 1, loc, ZERO);

        //List<DamagingProjectileAPI> submunitions = new ArrayList<>(NUM_SUBMUNITIONS);

        for (int i=0; i<NUM_SUBMUNITIONS; i++) {

            Vector2f thisVel = getSubmunitionVector(i, vel);
            try {
                // this overload is broken, do not use (https://fractalsoftworks.com/forum/index.php?topic=5061.msg389139#msg389139)
                //DamagingProjectileAPI submun = (DamagingProjectileAPI) engine.spawnProjectile(weapon.getShip(), weapon, "nex_alicorn_sub",
                //        "nex_alicorn_sub_proj", loc, VectorUtils.getFacing(thisVel), weapon.getShip().getVelocity());
                DamagingProjectileAPI submun = (DamagingProjectileAPI) engine.spawnProjectile(weapon.getShip(), weapon, "nex_alicorn_sub",
                        loc, VectorUtils.getFacing(thisVel), weapon.getShip().getVelocity());
                submun.getVelocity().set(thisVel);

                if (submun instanceof MissileAPI) {
                    MissileAPI missile = (MissileAPI) submun;
                    missile.setFlightTime((float)Math.random() * missile.getMaxFlightTime() * 0.75f);
                    missile.setEmpResistance(999);
                    missile.setEccmChanceOverride(1);
                    missile.setArmedWhileFizzling(true);
                    if (DEBUG_MODE) debugMissiles.add(missile);
                }
                //submunitions.add(submun);
                //engine.addFloatingText(loc, (int)submun.getDamageAmount() + "", 32, Color.white, submun, 0, 2);
            } catch (Exception ex) {
                log.error("Failed to spawn submunition", ex);
                break;
            }

        }
        engine.removeEntity(dam);
    }

    public float computeFuzeTimer(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
        float range = weapon.getRange();
        Vector2f projStart = projectile.getLocation();
        Vector2f vel = projectile.getVelocity();
        float speed = vel.length();
        float travelTime = range/speed;

        // get all ships in a circle of radius == range/2, centered on the 75% travel point of the projectile
        Vector2f pointToCheck = new Vector2f(projStart).translate(vel.x * travelTime * 0.75f, vel.y * travelTime * 0.75f);
        List<ShipAPI> ships = CombatUtils.getShipsWithinRange(pointToCheck, range/2);

        Vector2f closest = null;
        float closestDistSq = 99999999;

        for (ShipAPI ship : ships) {
            if (ship.isFighter()) continue;
            Vector2f col = CollisionUtils.getCollisionPoint(projStart, pointToCheck, ship);
            if (col == null) continue;

            float distSq = MathUtils.getDistanceSquared(projStart, col);

            if (distSq < closestDistSq) {
                closest = col;
                closestDistSq = distSq;
            }
        }

        float closestDist;

        if (closest == null) {
            closestDist = range * 0.75f;
            engine.addSmoothParticle(pointToCheck, ZERO, 25, 1, 3, Color.RED);
        } else {
            closestDist = (float)Math.sqrt(closestDistSq) * 0.6f;
            engine.addSmoothParticle(closest, ZERO, 25, 1, 3, Color.RED);
        }
        float time = closestDist/speed;
        return time;
    }

    public WeaponData getOrCreateWeaponData(WeaponAPI weapon, CombatEngineAPI engine) {
        final String DATA_KEY = getDataKeyForWeapon(weapon);
        WeaponData data = (WeaponData) engine.getCustomData().get(DATA_KEY);
        if (data == null) {
            data = new WeaponData();
            engine.getCustomData().put(DATA_KEY, data);
        }
        return data;
    }

    protected String getDataKeyForWeapon(WeaponAPI weapon) {
        return DATA_KEY_PREFIX + weapon.getShip().getId() + "_" + weapon.getSlot().getId();
    }

    public static class WeaponData {
        public HashSet<ProjectileData> projectiles = new HashSet<>(3);
    }

    public static class ProjectileData {

        public DamagingProjectileAPI projectile;
        public float ttl;

        public ProjectileData(DamagingProjectileAPI projectile, float ttl) {
            this.projectile = projectile;
            this.ttl = ttl;
        }
    }
}
