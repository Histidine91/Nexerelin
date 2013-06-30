package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

/*
 * Use system when:
 * - Enemy overloaded. no others ships nearby
 * - missiles inc. no ships nearby. (to save ammo by increasing the damage)
 * - When not under fire
 * - when enemy ship's guns are disabled
 * - when enemy weapons can't hit this ship
 * - target has very high flux
 */
public class IncendiaryAmmoAI implements ShipSystemAIScript
{
    /** The system will never be activated if there are more than this many
     * non-helpless enemies nearby */
    private static final int MAX_THREATS_NEARBY = 2;
    /** Never use the system if your hull is below this level */
    private static final float DONT_USE_IF_HULL_BELOW = .50f;
    /** Never consider a non-strike enemy a threat if they are this
     * many hull sizes smaller */
    private static final int NOT_THREAT_IF_SMALLER_BY = 3;
    /** The radius to search for enemies within */
    private static final float CONSIDER_ENEMIES_WITHIN_SU = 3000f;
    /** If true, wings count as one enemy, and are always considered a threat */
    private static final boolean CONSIDER_WINGS_AS_ONE_ENEMY = true;
    /** Flux percentage above which to consider someone helpless */
    private static final float CONSIDER_HELPLESS_ABOVE_FLUX_LEVEL = .9f;
    /** Percentage of weapons that need to be disabled to be considered helpless */
    private static final float CONSIDER_HELPLESS_AT_WEAPON_DISABLED_LEVEL = .6f;
    /** How often to check whether to activate/deactivate the system */
    private static final float SCAN_INTERVAL = .5f;
    private IntervalUtil tracker = new IntervalUtil(SCAN_INTERVAL, SCAN_INTERVAL);
    private ShipAPI ship;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine)
    {
        this.ship = ship;
    }

    /**
     * Checks if an enemy is considered helpless for the purposes of this system
     *
     * @param enemy The ShipAPI to check
     * @return true if the enemy is helpless, false otherwise
     */
    private boolean isThreat(ShipAPI enemy)
    {
        // Don't consider enemies who are venting or overloaded as threats
        FluxTrackerAPI flux = enemy.getFluxTracker();
        if ((flux.isOverloadedOrVenting()
                && (flux.getOverloadTimeRemaining() > SCAN_INTERVAL
                || flux.getTimeToVent() > SCAN_INTERVAL))
                || flux.getFluxLevel() > CONSIDER_HELPLESS_ABOVE_FLUX_LEVEL)
        {
            return false;
        }

        // Always consider larger enemies a threat
        if (enemy.getHullSize().ordinal() > ship.getHullSize().ordinal())
        {
            return true;
        }

        List weapons = enemy.getAllWeapons();
        WeaponAPI weapon;
        int disabled = 0, inRange = 0, strike = 0;

        // Check the status of weapons on the enemy ship
        for (int x = 0; x < weapons.size(); x++)
        {
            weapon = (WeaponAPI) weapons.get(x);

            // Ignore disabled weapons
            if (weapon.isDisabled() && weapon.getDisabledDuration() > SCAN_INTERVAL)
            {
                disabled++;
            }
            else
            {
                // Check if this weapon is aimed at the ship
                if (weapon.distanceFromArc(ship.getLocation()) == 0
                        && weapon.getRange()
                        > (MathUtils.getDistance(ship, weapon.getLocation())
                        - ship.getCollisionRadius()))
                {
                    inRange++;
                }
                // Check if this weapon counts as a strike weapon
                if (weapon.getDerivedStats().getDamagePerShot() > 225
                        || weapon.getDerivedStats().getDps() > 500)
                {
                    strike++;
                }
            }
        }

        // Don't consider small, non-strike equipped enemies a threat
        if (ship.getHullSize().ordinal() - enemy.getHullSize().ordinal()
                >= NOT_THREAT_IF_SMALLER_BY && strike == 0)
        {
            return false;
        }

        // No weapons can fire at this ship
        if (inRange == 0)
        {
            return false;
        }

        // Check percentage of weapons disabled
        if (disabled == 0 || disabled / (float) weapons.size()
                < CONSIDER_HELPLESS_AT_WEAPON_DISABLED_LEVEL)
        {
            return false;
        }

        return true;
    }

    /**
     * Considers nearby threats to determine whether this system should be active
     *
     * @return true if the system should be active, false otherwise
     */
    public boolean shouldUseSystem()
    {
        if (ship.getHullLevel() < DONT_USE_IF_HULL_BELOW)
        {
            return false;
        }

        List weapons = ship.getAllWeapons();
        WeaponAPI weapon;
        int disabled = 0;

        // Count how many weapons have been disabled on this ship
        for (int x = 0; x < weapons.size(); x++)
        {
            weapon = (WeaponAPI) weapons.get(x);
            if (weapon.isDisabled() && weapon.getDisabledDuration() > SCAN_INTERVAL)
            {
                disabled++;
            }
        }

        // Check percentage of weapons disabled
        if (weapons.isEmpty() || disabled / (float) weapons.size()
                > CONSIDER_HELPLESS_AT_WEAPON_DISABLED_LEVEL)
        {
            return false;
        }


        // Get all enemies within range
        List nearbyEnemies = AIUtils.getNearbyEnemies(ship, CONSIDER_ENEMIES_WITHIN_SU);
        Set tokens = new HashSet();
        int totalEnemies = 0;
        ShipAPI enemy;

        // Go through all enemies and determine how many are a threat
        for (int x = 0; x < nearbyEnemies.size(); x++)
        {
            enemy = (ShipAPI) nearbyEnemies.get(x);

            // Consider wings as one enemy, always a threat
            if (CONSIDER_WINGS_AS_ONE_ENEMY && enemy.isFighter())
            {
                if (!tokens.contains(enemy.getWingToken()))
                {
                    tokens.add(enemy.getWingToken());
                    totalEnemies++;
                }
            }
            else
            {
                if (isThreat(enemy))
                {
                    totalEnemies++;
                }
            }
        }

        // Don't activate if there are too many enemies nearby
        if (totalEnemies > MAX_THREATS_NEARBY)
        {
            return false;
        }

        // Prefer the targetted ship for calculations
        if (nearbyEnemies.contains(ship.getShipTarget()))
        {
            enemy = ship.getShipTarget();
        }
        // Otherwise, use the nearest enemy
        else
        {
            enemy = AIUtils.getNearestEnemy(ship);
        }

        // Use the system if the enemy is helpless
        if (enemy != null && !isThreat(enemy)
                && enemy.getFluxTracker().isOverloadedOrVenting())
        {
            return true;
        }

        // Otherwise, don't risk it
        return false;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target)
    {
        tracker.advance(amount);

        // Check if we should use this system every SCAN_INTERVAL seconds
        if (tracker.intervalElapsed())
        {
            // Can we even use the system right now?
            if (!AIUtils.canUseSystemThisFrame(ship))
            {
                return;
            }

            // If system is inactive and should be active, enable it
            // If system is active and shouldn't be, disable it
            if (ship.getSystem().isActive() ^ shouldUseSystem())
            {
                ship.useSystem();
            }
        }
    }
}
