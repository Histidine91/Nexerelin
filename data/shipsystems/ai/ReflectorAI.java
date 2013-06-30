package data.shipsystems.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import data.scripts.plugins.CombatUtils;
import java.util.Iterator;
import org.lwjgl.util.vector.Vector2f;

public class ReflectorAI implements ShipSystemAIScript
{
    private ShipSystemAPI system;
    private ShipAPI ship;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine)
    {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target)
    {
        float field_range = 600f; //the AI needs to know how big the field is, so it knows which projectiles will be affected
        float fuzzyLogic = 0f; //set our logic counter to 0
        DamagingProjectileAPI proj; //we need to initialize our projectile object
        for (Iterator iter = CombatUtils.getCombatEngine().getProjectiles().iterator();
                iter.hasNext();) //iterate through every projectile on the map
        {
            proj = (DamagingProjectileAPI) iter.next(); //set proj to be a specific projectile

            if ((proj.getOwner() != ship.getOwner()) && (CombatUtils.getDistance(proj, ship) >= (field_range * 1.05f))) //here we're doing two checks: is the projectile hostile? Is it in range? Notice that we're also counting projectiles just outside the distance so we can predict them
            {
                //now what we know it is nearby and can hurt us, let's see how big of a threat it is
                fuzzyLogic += proj.getDamageAmount(); //how much damage will it do to us?
                fuzzyLogic += proj.getAngularVelocity(); //how fast is it moving?
                fuzzyLogic += proj.getHitpoints(); //how hard will it be to destroy?
            }
            
        }
        float fluxLevel = ship.getFluxTracker().getFluxLevel(); //what is our flux at?
        
        if (fuzzyLogic > 500f && !system.isActive() && fluxLevel < 0.95f) {
            //compare our counter to a magic number, make sure the system is already off, and that our flux level isn't too high
            ship.useSystem();
        } else if ((fuzzyLogic <= 50f || fluxLevel >= 0.95f ) && system.isActive()) {
            //if there is little-to-no threat OR if our flux level is too high, turn the system off if it is currently active
            ship.useSystem();
        } else { return; }    //this actually shouldn't ever be triggered   
    }
}
