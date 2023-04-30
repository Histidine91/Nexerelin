package exerelin.combat.shipsystems;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.CollectionUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;


/**
 * AI for the Silverlight's ship system. Adapted from Templars' (Heavy) Schism Drive AI with permission.
 */
public class SilverlightDashAI implements ShipSystemAIScript {

    protected static final float SECONDS_TO_LOOK_AHEAD = 1f;
    protected static final float MAX_ANGLE_TO_DESTINATION = 75f;

    protected CombatEngineAPI engine;

    protected final CollectionUtils.CollectionFilter<DamagingProjectileAPI> filterMisses = new CollectionUtils.CollectionFilter<DamagingProjectileAPI>() {
        @Override
        public boolean accept(DamagingProjectileAPI proj) {
            if (proj.getOwner() == ship.getOwner() && (!(proj instanceof MissileAPI) || !((MissileAPI) proj).isFizzling())) {
                return false;
            }

            if (proj instanceof MissileAPI) {
                MissileAPI missile = (MissileAPI) proj;
                if (missile.isFlare()) {
                    return false;
                }
            }

            return (CollisionUtils.getCollides(proj.getLocation(), Vector2f.add(proj.getLocation(), (Vector2f) new Vector2f(proj.getVelocity()).scale(
                    SECONDS_TO_LOOK_AHEAD), null), ship.getLocation(), ship.getCollisionRadius()
                    + 50f) && Math.abs(
                            MathUtils.getShortestRotation(proj.getFacing(), VectorUtils.getAngle(proj.getLocation(), ship.getLocation()))) <= 90f);
        }
    };

    private ShipwideAIFlags flags;
    private ShipAPI ship;

    private final IntervalUtil tracker = new IntervalUtil(0.1f, 0.2f);

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        if (engine == null) {
            return;
        }

        if (engine.isPaused()) {
            return;
        }

        if (ship.getSystem().isActive()) {
            flags.setFlag(AIFlags.DO_NOT_VENT);
        }

        tracker.advance(amount);

        if (tracker.intervalElapsed()) {
            if (!AIUtils.canUseSystemThisFrame(ship)) {
                return;
            }

            List<DamagingProjectileAPI> nearbyThreats = new ArrayList<>(500);
            for (DamagingProjectileAPI tmp : engine.getProjectiles()) {
                if (MathUtils.isWithinRange(tmp.getLocation(), ship.getLocation(), ship.getCollisionRadius() * 3f)) {
                    nearbyThreats.add(tmp);
                }
            }
            nearbyThreats = CollectionUtils.filter(nearbyThreats, filterMisses);
            List<MissileAPI> nearbyMissiles = AIUtils.getNearbyEnemyMissiles(ship, ship.getCollisionRadius() * 2f);
            for (MissileAPI missile : nearbyMissiles) {
                if (!missile.getEngineController().isTurningLeft() && !missile.getEngineController().isTurningRight()) {
                    continue;
                }

                nearbyThreats.add(missile);
            }

            float decisionLevel = getDecisionLevel(nearbyThreats);

            if (decisionLevel >= 15f) {
                ship.useSystem();
                flags.setFlag(AIFlags.DO_NOT_VENT);
            }
        }
    }

    protected float getDecisionLevel(List<DamagingProjectileAPI> nearbyThreats) {
        float decisionLevel = 0f;
        for (DamagingProjectileAPI threat : nearbyThreats) {
            if (threat.getDamageType() == DamageType.FRAGMENTATION) {
                decisionLevel += Math.pow((threat.getDamageAmount() + threat.getEmpAmount() * 0.25f) / 800f, 1.2f);
            } else {
                decisionLevel += Math.pow((threat.getDamageAmount() + threat.getEmpAmount() * 0.25f) / 200f, 1.2f);
            }
        }
        List<BeamAPI> nearbyBeams = engine.getBeams();
        for (BeamAPI beam : nearbyBeams) {
            if (beam.getDamageTarget() == ship) {
                float damage;
                float emp = beam.getWeapon().getDerivedStats().getEmpPerSecond();
                if (beam.getWeapon().getDerivedStats().getSustainedDps() < beam.getWeapon().getDerivedStats().getDps()) {
                    damage = beam.getWeapon().getDerivedStats().getBurstDamage() / beam.getWeapon().getDerivedStats().getBurstFireDuration();
                } else {
                    damage = beam.getWeapon().getDerivedStats().getDps();
                }
                decisionLevel += Math.pow(
                        ((damage * ((beam.getWeapon().getDamageType() == DamageType.FRAGMENTATION) ? 0.25f : 1f))
                                + (0.25f * emp)) / 100f, 1.3f);
            }
        }

        float shipDir = ship.getFacing();

        AssignmentInfo assignment = engine.getFleetManager(ship.getOwner()).getTaskManager(ship.isAlly()).getAssignmentFor(ship);
        Vector2f targetSpot;
        if (assignment != null && assignment.getTarget() != null) {
            targetSpot = assignment.getTarget().getLocation();
        } else {
            targetSpot = null;
        }

        if (assignment != null && assignment.getType() == CombatAssignmentType.RETREAT) {
            float retreatDirection = (ship.getOwner() == 0) ? 270f : 90f;
            if (Math.abs(MathUtils.getShortestRotation(shipDir, retreatDirection)) <= 60f) {
                decisionLevel += 25f;
            } else if (Math.abs(MathUtils.getShortestRotation(shipDir, retreatDirection)) > 90f) {
                decisionLevel -= 25f;
            }
        }

        if (flags.hasFlag(AIFlags.RUN_QUICKLY)) {
            decisionLevel += 20f;
        } else if (flags.hasFlag(AIFlags.PURSUING)) {
            decisionLevel *= 1.25f;
            decisionLevel += 10f;
        } else if (targetSpot != null && Math.abs(MathUtils.getShortestRotation(shipDir, VectorUtils.getAngle(ship.getLocation(), targetSpot))) <= MAX_ANGLE_TO_DESTINATION
                && Math.abs(MathUtils.getShortestRotation(ship.getFacing(), VectorUtils.getAngle(ship.getLocation(), targetSpot))) <= MAX_ANGLE_TO_DESTINATION
                && MathUtils.getDistance(ship, targetSpot) >= 1000f) {
            decisionLevel += 17.5f;
        } else if (ship.getShipTarget() != null && MathUtils.getDistance(ship.getShipTarget(), ship) >= 2000f) {
            decisionLevel += 8f;
            if (Math.abs(MathUtils.getShortestRotation(shipDir, VectorUtils.getAngle(ship.getLocation(), ship.getShipTarget().getLocation()))) <= MAX_ANGLE_TO_DESTINATION) {
                decisionLevel += 8f;
            }
        }
        decisionLevel *= 1f - 0.5f * (ship.getFluxTracker().getCurrFlux() + ship.getFluxTracker().getHardFlux()) / ship.getFluxTracker().getMaxFlux();
        // unlike Schism Drive, this ship system allows turning very quickly, so having a TURN_QUICKLY flag raises rather than lowers priority
        if (flags.hasFlag(AIFlags.TURN_QUICKLY)) {
            decisionLevel *= 1.5f;
            decisionLevel += 10f;
        }
        if (flags.hasFlag(AIFlags.BACK_OFF) || flags.hasFlag(AIFlags.BACK_OFF_MIN_RANGE) || flags.hasFlag(AIFlags.BACKING_OFF)) {
            decisionLevel *= 0.75f;
        }
        if (flags.hasFlag(AIFlags.DO_NOT_USE_FLUX)) {
            //decisionLevel *= 0.25f;
        }

        return decisionLevel;
    }

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
    }

}
