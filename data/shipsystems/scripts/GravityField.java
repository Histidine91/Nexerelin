package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import data.scripts.plugins.CombatUtils;
import java.awt.Color;
import java.util.Iterator;
import org.lwjgl.util.vector.Vector2f;

public class GravityField implements ShipSystemStatsScript
{
    private static float FIELD_RANGE = 600f;
    //private static Color FIELD_COLOR = Color.CYAN;
    private static Color FIELD_COLOR = new Color(142, 112, 248); // some color idk
    
    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)
    {
        ShipAPI ship = CombatUtils.getOwner(stats);

        if (ship == null)
        {
            return;
        }

        Vector2f velocity;
        DamagingProjectileAPI proj;
        for (Iterator iter = CombatUtils.getCombatEngine().getProjectiles().iterator();
                iter.hasNext();)
        {
            proj = (DamagingProjectileAPI) iter.next();

            if (proj.getOwner() == ship.getOwner())
            {
                // Don't affect friendly projectiles
                continue;
            }

            if (CombatUtils.getDistance(proj, ship) > FIELD_RANGE)
            {
                // Ignore projectiles that are out of range of the field
                continue;
            }

            float velocityMod = 1 - ((CombatUtils.getDistance(proj, ship) / FIELD_RANGE)/25);
            
            velocity = proj.getVelocity();
            
            velocity.set(velocity.x * velocityMod, velocity.y * velocityMod);


            // This looks really ugly with missiles
            if (!(proj instanceof MissileAPI))
            {
                CombatUtils.getCombatEngine().addSmokeParticle(proj.getLocation(),
                        velocity, 8f, .25f, .12f, FIELD_COLOR);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id)
    {
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel)
    {
        if (index == 0)
        {
            return new StatusData("gravity field active", false);
        }
        return null;
    }
}