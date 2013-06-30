package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import java.awt.Color;
import java.util.Iterator;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class ValkyriaSystem implements ShipSystemStatsScript
{
    private static final float PUSH_FORCE = 1500f;
    public static final float PULSE_RANGE = 3000f;
    private static final Color PULSE_COLOR = Color.CYAN;
    private static final Color EMP_COLOR = Color.RED;
    private static final float STRENGTH_VS_FIGHTER = 1.5f;
    private static final float STRENGTH_VS_FRIGATE = 1f;
    private static final float STRENGTH_VS_DESTROYER = .8f;
    private static final float STRENGTH_VS_CRUISER = .5f;
    private static final float STRENGTH_VS_CAPITAL = .3f;
    private static final Vector2f NULLVEL = new Vector2f(0, 0);
    private boolean isActive = false;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)
    {
        if (!state.equals(State.OUT))
        {
            return;
        }

        if (!isActive)
        {
            isActive = true;
            CombatEngineAPI engine = CombatUtils.getCombatEngine();
            ShipAPI source = (ShipAPI) stats.getEntity();

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
                    Color.BLUE, PULSE_RANGE / 2, 1f);
            engine.spawnExplosion(source.getLocation(), source.getVelocity(),
                    Color.CYAN, PULSE_RANGE / 4, 1f);

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
