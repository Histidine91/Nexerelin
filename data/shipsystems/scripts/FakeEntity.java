package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.BoundsAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import org.lwjgl.util.vector.Vector2f;

final class FakeEntity implements CombatEntityAPI
{
    private final Vector2f location;

    public FakeEntity(Vector2f location)
    {
        this.location = location;
    }

    @Override
    public Vector2f getLocation()
    {
        return location;
    }

    @Override
    public Vector2f getVelocity()
    {
        return new Vector2f(0, 0);
    }

    @Override
    public float getFacing()
    {
        return 0;
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
        return 100;
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
        return CollisionClass.NONE;
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
        return 1f;
    }

    @Override
    public float getHitpoints()
    {
        return 1f;
    }

    @Override
    public float getMaxHitpoints()
    {
        return 1f;
    }

}