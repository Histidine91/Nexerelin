package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class ArmorPiercePlugin implements EveryFrameCombatPlugin
{
    // Sound to play while piercing a target's armor (should be loopable!)
    private static final String PIERCE_SOUND = "explosion_flak"; // TEMPORARY
    // Projectile ID (String), pierces shields (boolean)
    private static final Map PROJ_IDS = new HashMap();
    // Keep track of the original collision class (used for shield hits)
    private static final Map originalCollisionClasses = new HashMap();

    static
    {
        // Add all projectiles that should pierce armor here
        // Format: Projectile ID (String), pierces shields (boolean)
        PROJ_IDS.put("ms_rhpcblast", false);
    }

    @Override
    public void advance(float amount, List events)
    {
        DamagingProjectileAPI proj;
        CombatEntityAPI entity;
        String spec;
        
        if (CombatUtils.getCombatEngine().isPaused())
            return;

        // Scan all shots on the map for armor piercing projectiles
        for (Iterator iter = CombatUtils.getCombatEngine()
                .getProjectiles().iterator(); iter.hasNext();)
        {
            proj = (DamagingProjectileAPI) iter.next();
            spec = proj.getProjectileSpecId();

            // Is this projectile armor piercing?
            if (!PROJ_IDS.containsKey(spec))
            {
                continue;
            }

            // Register the original collision class (used for shield hits)
            if (!originalCollisionClasses.containsKey(spec))
            {
                originalCollisionClasses.put(spec, proj.getCollisionClass());
            }

            // We'll do collision checks manually
            proj.setCollisionClass(CollisionClass.NONE);

            // Find nearby ships, missiles and asteroids
            List toCheck = CombatUtils.getShipsWithinRange(proj.getLocation(),
                    proj.getCollisionRadius() + 5f);
            toCheck.addAll(CombatUtils.getMissilesWithinRange(proj.getLocation(),
                    proj.getCollisionRadius() + 5f));
            toCheck.addAll(CombatUtils.getAsteroidsWithinRange(proj.getLocation(),
                    proj.getCollisionRadius() + 5f));
            // Don't include the ship that fired this projectile!
            toCheck.remove(proj.getSource());
            for (Iterator iter2 = toCheck.iterator(); iter2.hasNext();)
            {
                entity = (CombatEntityAPI) iter2.next();

                // Check for an active phase cloak
                if (entity instanceof ShipAPI)
                {
                    ShipSystemAPI cloak = ((ShipAPI) entity).getPhaseCloak();

                    if (cloak != null && cloak.isActive())
                    {
                        continue;
                    }
                }

                // Check for a shield hit
                if ((Boolean) PROJ_IDS.get(spec) != true
                        && (entity.getShield() != null
                        && entity.getShield().isOn()
                        && entity.getShield().isWithinArc(proj.getLocation())))
                {
                    // If we hit a shield, enable collision
                    proj.setCollisionClass(
                            (CollisionClass) originalCollisionClasses.get(spec));
                    // Stop the projectile (ensures a hit for fast projectiles)
                    proj.getVelocity().set(entity.getVelocity());
                    // Then move the projectile inside the ship's shield bounds
                    Vector2f.add((Vector2f) MathUtils.getDirectionalVector(proj,
                            entity).scale(5f), proj.getLocation(),
                            proj.getLocation());
                }
                // Check if the projectile is inside the entity's bounds
                else if (CollisionUtils.isPointWithinBounds(
                        proj.getLocation(), entity))
                {
                    // Calculate projectile speed
                    float speed = proj.getVelocity().length();

                    // Damage per frame is based on how long it would take
                    // the projectile to travel through the entity
                    float modifier = 1.0f / ((entity.getCollisionRadius()
                            * 2f) / speed);
                    float damage = (proj.getDamageAmount() * amount) * modifier;
                    float emp = (proj.getEmpAmount() * amount) * modifier;

                    // Apply damage and slow the projectile
                    // Note: BALLISTIC_AS_BEAM projectiles won't be slowed!
                    CombatUtils.getCombatEngine().applyDamage(entity,
                            proj.getLocation(), damage, proj.getDamageType(),
                            emp, true, true, proj.getSource());
                    proj.getVelocity().scale(1.0f - (amount * 1.5f));

                    // Render the hit
                    CombatUtils.getCombatEngine().spawnExplosion(
                            proj.getLocation(), entity.getVelocity(),
                            Color.ORANGE, speed * amount * .65f, .5f);

                    // Play piercing sound (only one sound active per projectile)
                    Global.getSoundPlayer().playLoop(PIERCE_SOUND, proj, 1f, 1f,
                            proj.getLocation(), entity.getVelocity());
                }
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
    }
}
