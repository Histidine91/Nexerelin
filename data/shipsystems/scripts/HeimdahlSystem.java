package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class HeimdahlSystem implements ShipSystemStatsScript
{
    private static final float PUSH_FORCE = 1800f;
    public static final float PULSE_RANGE = 2500f;
    private static final Color PULSE_COLOR = new Color(155, 25, 225, 255);
    private static final Color EMP_COLOR = new Color(255, 75, 255, 255);
    private static final Color ELECTRICAL_ARC_FRINGE = new Color(85, 25, 215, 255);
    private static final Color ELECTRICAL_ARC_CORE = new Color(255, 255, 255, 255);
    private static final float ELECTRICAL_ARC_SIZE = 1.5f;
    private static final int MAX_ELECTRICAL_ARCS = 15;
    private static final float STRENGTH_VS_FIGHTER = 1.6f;
    private static final float STRENGTH_VS_FRIGATE = 1.1f;
    private static final float STRENGTH_VS_DESTROYER = .9f;
    private static final float STRENGTH_VS_CRUISER = .6f;
    private static final float STRENGTH_VS_CAPITAL = .4f;
    private static final Vector2f NULLVEL = new Vector2f(0, 0);
    private Set activeArcs = new HashSet();
    private boolean isActive = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)
    {
        ShipAPI source = (ShipAPI) stats.getEntity();
        CombatEngineAPI engine = CombatUtils.getCombatEngine();

        if (state.equals(State.IN))
        {
            // Remove inactive arcs
            for (Iterator arcs = activeArcs.iterator(); arcs.hasNext();)
            {
                if (!engine.isEntityInPlay((CombatEntityAPI) arcs.next()))
                {
                    arcs.remove();
                }
            }

            // How many arcs should be active right now
            float currentArcs = 3 + (effectLevel * MAX_ELECTRICAL_ARCS);
            float maxArcLength = Math.max(source.getCollisionRadius() / 5f, 25f);
            Vector2f point1, point2;

            // Since it only tries to get a point in bounds once per frame,
            // the number of arcs increases gradually but unpredictably
            for (int x = 0; x < (currentArcs - activeArcs.size()); x++)
            {
                point1 = MathUtils.getRandomPointInCircle(source.getLocation(),
                        source.getCollisionRadius());
                if (CollisionUtils.isPointWithinBounds(point1, source))
                {
                    point2 = MathUtils.getRandomPointOnCircumference(point1,
                            maxArcLength);
                    activeArcs.add(engine.spawnEmpArc(source, point1, source,
                            new FakeEntity(point2), DamageType.ENERGY, 0f, 0f,
                            maxArcLength, null, ELECTRICAL_ARC_SIZE,
                            ELECTRICAL_ARC_FRINGE, ELECTRICAL_ARC_CORE));
                }
            }
        }
        else if (state.equals(State.OUT) && !isActive)
        {
            // Remove electrical arcs once system has fired
            for (Iterator arcs = activeArcs.iterator(); arcs.hasNext();)
            {
                engine.removeEntity((CombatEntityAPI) arcs.next());
            }

            isActive = true;

            // Render three concentric rings of force
            for (int x = 0; x < 1500; x++)
            {
                engine.addSmokeParticle(source.getLocation(),
                        Vector2f.add(source.getVelocity(),
                        MathUtils.getRandomPointOnCircumference(NULLVEL,
                        PULSE_RANGE + 100), null), 7.5f, .3f, 1f, PULSE_COLOR);
                engine.addSmokeParticle(source.getLocation(),
                        Vector2f.add(source.getVelocity(),
                        MathUtils.getRandomPointOnCircumference(NULLVEL,
                        PULSE_RANGE), null), 7.5f, .2f, 1, PULSE_COLOR);
                engine.addSmokeParticle(source.getLocation(),
                        Vector2f.add(source.getVelocity(),
                        MathUtils.getRandomPointOnCircumference(NULLVEL,
                        PULSE_RANGE - 100), null), 7.5f, .1f, 1f, PULSE_COLOR);
            }
            engine.spawnExplosion(source.getLocation(), source.getVelocity(),
                    new Color(155, 45, 255, 255), PULSE_RANGE / 2, 1f);
            engine.spawnExplosion(source.getLocation(), source.getVelocity(),
                    new Color(225, 195, 255, 255), PULSE_RANGE / 3, 1f);

            float mod = source.getFluxTracker().getFluxLevel() + .5f;
            source.getFluxTracker().setCurrFlux(0f);
			source.getFluxTracker().setHardFlux(0f);

            CombatEntityAPI tmp;
            ShipAPI ship;
            Vector2f dir;
            float force, damage;
            for (Iterator pushed = CombatUtils.getEntitiesWithinRange(
                    source.getLocation(), PULSE_RANGE).iterator(); pushed.hasNext();)
            {
                tmp = (CombatEntityAPI) pushed.next();

                if (tmp == source)
                {
                    continue;
                }

                force = (1f - MathUtils.getDistance(source, tmp) / PULSE_RANGE) * PUSH_FORCE;
                force *= mod;
                damage = force;

                if (tmp instanceof ShipAPI)
                {
                    ship = (ShipAPI) tmp;

                    // Modify push strength based on ship class
                    if (ship.getHullSize() == ShipAPI.HullSize.FIGHTER)
                    {
                        force *= STRENGTH_VS_FIGHTER;
                        damage /= STRENGTH_VS_FIGHTER;
                    }
                    else if (ship.getHullSize() == ShipAPI.HullSize.FRIGATE)
                    {
                        force *= STRENGTH_VS_FRIGATE;
                        damage /= STRENGTH_VS_FRIGATE;
                    }
                    else if (ship.getHullSize() == ShipAPI.HullSize.DESTROYER)
                    {
                        force *= STRENGTH_VS_DESTROYER;
                        damage /= STRENGTH_VS_DESTROYER;
                    }
                    else if (ship.getHullSize() == ShipAPI.HullSize.CRUISER)
                    {
                        force *= STRENGTH_VS_CRUISER;
                        damage /= STRENGTH_VS_CRUISER;
                    }
                    else if (ship.getHullSize() == ShipAPI.HullSize.CAPITAL_SHIP)
                    {
                        force *= STRENGTH_VS_CAPITAL;
                        damage /= STRENGTH_VS_CAPITAL;
                    }

                    if (ship.getShield() != null && ship.getShield().isOn()
                            && ship.getShield().isWithinArc(source.getLocation()))
                    {
                        ship.getFluxTracker().increaseFlux(damage * 2, true);
                    }
                    else
                    {
                        for (int x = 0; x < 5; x++)
                        {
                            engine.spawnEmpArc(source,
                                    MathUtils.getRandomPointInCircle(
                                    ship.getLocation(), ship.getCollisionRadius()),
                                    ship, ship, DamageType.ENERGY, damage / 10,
                                    damage / 5, PULSE_RANGE, null, 2f,
                                    EMP_COLOR, EMP_COLOR);
                        }
                    }
                }

                force = Math.min(force / (tmp.getMass() / 1000), PUSH_FORCE * 1.5f);
                dir = (Vector2f) MathUtils.getDirectionalVector(source, tmp).scale(force);
                Vector2f.add(tmp.getVelocity(), dir, tmp.getVelocity());
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id)
    {
        isActive = false;
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel)
    {
        return null;
    }
}
