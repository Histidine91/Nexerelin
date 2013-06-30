package data.scripts.weapons;

import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.awt.Color;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class ms_panZapFX implements EveryFrameWeaponEffectPlugin
{
    private static final String WEAPON_ID = "ms_arc";
    private static final float EMITTER_THICKNESS = 2f;
	private static final Color EMITTER_FRINGE = new Color(125,155,115,150);
    private static final Color EMITTER_CORE = new Color(165,215,145,255);
    private boolean hasChecked = false, hasEye = false;
    private WeaponAPI closestEye = null;
    private FollowWeaponCombatEntity eyeFollower = null;
    CombatEntityAPI activeArc = null;
    
    private static float getDistanceSquared(Vector2f vector1, Vector2f vector2)
    {
        float a = vector1.x - vector2.x, b = vector1.y - vector2.y;
        return (a * a) + (b * b);
    }

    public boolean checkForSeeingEye(WeaponAPI emitter)
    {
        ShipAPI ship = emitter.getShip();
        WeaponAPI weapon;
        List weapons = ship.getAllWeapons();
        float closestDistance = Float.MAX_VALUE;

        for (int x = 0; x < weapons.size(); x++)
        {
            weapon = (WeaponAPI) weapons.get(x);
            if (WEAPON_ID.equals(weapon.getId()))
            {
                if (closestEye == null || getDistanceSquared(weapon.getLocation(),
                        emitter.getLocation()) < closestDistance)
                {
                    closestEye = weapon;
                    closestDistance = getDistanceSquared(emitter.getLocation(),
                            weapon.getLocation());
                }
            }
        }

        if (closestEye != null)
        {
            eyeFollower = new FollowWeaponCombatEntity(closestEye);
            return true;
        }

        return false;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon)
    {
        if (!hasChecked)
        {
            hasEye = checkForSeeingEye(weapon);
            hasChecked = true;
        }

        if (!hasEye || engine.isPaused() || (activeArc != null
                && engine.isEntityInPlay(activeArc)))
        {
            return;
        }

        if (closestEye.isFiring())
        {
            activeArc = engine.spawnEmpArc(weapon.getShip(),
                    weapon.getLocation(), weapon.getShip(),
                    eyeFollower, DamageType.OTHER, 0f, 0f, 5000f,
                    null, EMITTER_THICKNESS, EMITTER_FRINGE, EMITTER_CORE);
        }
    }

    //<editor-fold defaultstate="collapsed" desc="FollowWeaponCombatEntity">
    public static class FollowWeaponCombatEntity implements CombatEntityAPI
    {
        private WeaponAPI weapon;

        public FollowWeaponCombatEntity(WeaponAPI weapon)
        {
            this.weapon = weapon;
        }

        @Override
        public Vector2f getLocation()
        {
            return weapon.getLocation();
        }

        @Override
        public Vector2f getVelocity()
        {
            return null;
        }

        @Override
        public float getFacing()
        {
            return weapon.getArcFacing();
        }

        @Override
        public void setFacing(float facing)
        {
        }

        @Override
        public float getAngularVelocity()
        {
            return 0f;
        }

        @Override
        public void setAngularVelocity(float angVel)
        {
        }

        @Override
        public int getOwner()
        {
            return weapon.getShip().getOwner();
        }

        @Override
        public void setOwner(int owner)
        {
        }

        @Override
        public float getCollisionRadius()
        {
            return 0f;
        }

        @Override
        public CollisionClass getCollisionClass()
        {
            return null;
        }

        @Override
        public void setCollisionClass(CollisionClass collisionClass)
        {
        }

        @Override
        public float getMass()
        {
            return 0f;
        }

        @Override
        public void setMass(float mass)
        {
        }

        @Override
        public BoundsAPI getExactBounds()
        {
            return null;
        }

        @Override
        public ShieldAPI getShield()
        {
            return null;
        }

        @Override
        public float getHullLevel()
        {
            return 0f;
        }

        @Override
        public float getHitpoints()
        {
            return 0f;
        }

        @Override
        public float getMaxHitpoints()
        {
            return 0f;
        }
    }
    //</editor-fold>
}
