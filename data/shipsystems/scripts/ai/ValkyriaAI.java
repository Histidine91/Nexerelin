package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import data.shipsystems.scripts.ValkyriaSystem;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class ValkyriaAI implements ShipSystemAIScript
{
    private static final float USE_AT_THREAT = 20f;
    private ShipAPI ship;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine)
    {
        this.ship = ship;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target)
    {
        if (!AIUtils.canUseSystemThisFrame(ship))
        {
            return;
        }

        ShipAPI tmp;
        float threatWeight = 0;

        // Consider nearby threats, reduce for nearby allies
        for (Iterator nearby = CombatUtils.getShipsWithinRange(ship.getLocation(),
                ValkyriaSystem.PULSE_RANGE).iterator(); nearby.hasNext();)
        {
            tmp = (ShipAPI) nearby.next();

            if (tmp.getOwner() != ship.getOwner())
            {
                threatWeight += (tmp.getHullSize().ordinal() * 2)
                        * (1f - MathUtils.getDistance(ship, tmp)
                        / ValkyriaSystem.PULSE_RANGE);
            }
            else
            {
                threatWeight -= (tmp.getHullSize().ordinal() * 2)
                        * (1f - MathUtils.getDistance(ship, tmp)
                        / ValkyriaSystem.PULSE_RANGE);
            }
        }

        // Take current flux into account
        threatWeight /= 1.0 - ship.getFluxTracker().getFluxLevel();
        // Also consider hull level
        threatWeight /= ship.getHullLevel();

        if (threatWeight > USE_AT_THREAT)
        {
            ship.useSystem();
        }
    }
}
