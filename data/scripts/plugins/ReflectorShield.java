package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;

public class ReflectorShield implements ShipSystemStatsScript, EveryFrameCombatPlugin
{
    private static final String REFLECTION_SOUND = "hit_shield_light_gun";
    // How far beyond the shield radius will this reflection field extend?
    private static final float REFLECTION_MARGIN = 30f;
    private static CombatEngineAPI activeEngine;
    // <ShipAPI, Float>
    private static Map reflecting = new HashMap();
    // You can remove all references to this once the next hotfix lands
    // This is just to prevent infinite loops due to the missing setOwner()
    private static Set reflected = new HashSet();

    private void playReflectionSound(DamagingProjectileAPI source)
    {
        float volume = Math.max(.2f, Math.min(3.5f,
                source.getDamageAmount() / 100));
        Global.getSoundPlayer().playSound(REFLECTION_SOUND,
                1.5f + (float) Math.random(), volume,
                source.getLocation(), new Vector2f(0, 0));
    }

    private Vector2f getNormal(DamagingProjectileAPI proj, ShipAPI reflecting)
    {
        return (Vector2f) Vector2f.sub(proj.getLocation(),
                reflecting.getLocation(), null).normalise();
    }

    private void reflectProjectile(DamagingProjectileAPI proj, ShipAPI reflecting)
    {
        // Temporary
        reflected.add(proj);

        Vector2f newVelocity = new Vector2f(proj.getVelocity());

        // Modify newVelocity however you want (reverse it, bounce randomly,
        // slow it down, etc) - in this case, reflect the projectile
        Vector2f vNorm = getNormal(proj, reflecting);
        float dot = Vector2f.dot(newVelocity, vNorm);
        vNorm.set(vNorm.x * dot, vNorm.y * dot);
        Vector2f vTan = Vector2f.sub(newVelocity, vNorm, null);
        newVelocity = Vector2f.sub(vTan, vNorm, null);

        // Tell the projectile to use the new velocity
        proj.getVelocity().set(newVelocity);

        // For debug purposes
        //proj.getVelocity().set(500, 500);

        // Uncomment below after the next hotfix
        //proj.setOwner(reflecting.getOwner());
        //proj.setSource(reflecting);
        playReflectionSound(proj);
    }

    private void checkProjectiles()
    {
        DamagingProjectileAPI proj;
        ShipAPI ship;
        float radius;
        for (Iterator allProj = activeEngine.getProjectiles().iterator(); allProj.hasNext();)
        {
            proj = (DamagingProjectileAPI) allProj.next();

            if (reflected.contains(proj))
            {
                continue;
            }

            for (Iterator iter = reflecting.entrySet().iterator(); iter.hasNext();)
            {
                Map.Entry tmp = (Map.Entry) iter.next();
                ship = (ShipAPI) tmp.getKey();
                radius = (Float) tmp.getValue();

                // Only reflect enemy projectiles (avoids infinite loop)
                if (proj.getOwner() == ship.getOwner())
                {
                    continue;
                }

                if (getDistance(proj.getLocation(), ship.getLocation()) < radius)
                {
                    reflectProjectile(proj, ship);
                }
            }
        }

        // Temporary
        for (Iterator ref = reflected.iterator(); ref.hasNext();)
        {
            proj = (DamagingProjectileAPI) ref.next();

            if (proj == null || proj.didDamage())
            {
                ref.remove();
            }
        }
    }

    public static float getDistance(Vector2f vector1, Vector2f vector2)
    {
        float a = vector1.x - vector2.x;
        float b = vector1.y - vector2.y;
        return (float) Math.hypot(a, b);
    }

    // You can remove this after the next hotfix and uncomment the line in apply()
    private ShipAPI getOwner(MutableShipStatsAPI stats)
    {
        if (activeEngine == null)
        {
            return null;
        }

        ShipAPI tmp;
        for (Iterator allShips = activeEngine.getShips().iterator(); allShips.hasNext();)
        {
            tmp = (ShipAPI) allShips.next();

            if (tmp.getMutableStats() == stats)
            {
                return tmp;
            }
        }

        return null;
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)
    {
        // Will work after the next Starfarer hotfix
        //ShipAPI owner = (ShipAPI) stats.getEntity();

        ShipAPI owner = getOwner(stats);
        if (owner != null && !reflecting.containsKey(owner))
        {
            // Should use shield radius - collision radius is backup in case
            // the ship builder didn't put a shield radius for the hull
            if (owner.getShield() != null)
            {
                reflecting.put(owner,
                        Math.max(owner.getCollisionRadius() + REFLECTION_MARGIN,
                        owner.getShield().getRadius() + REFLECTION_MARGIN));
            }
            else
            {
                reflecting.put(owner, owner.getCollisionRadius() + REFLECTION_MARGIN);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id)
    {
        reflecting.remove(getOwner(stats));
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel)
    {
        if (index == 0)
        {
            return new StatusData("reflecting projectiles", false);
        }

        return null;
    }

    @Override
    public void advance(float amount, List events)
    {
        if (!reflecting.isEmpty())
        {
            checkProjectiles();
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        activeEngine = engine;
        reflecting.clear();
        // Can remove after next hotfix
        reflected.clear();
    }
}