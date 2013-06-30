//Credit goes to Psiyon for his firecontrol AI script.

package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import data.scripts.plugins.CombatUtils;
import java.util.Iterator;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

public class ms_drivechargerai implements ShipSystemAIScript {
    private ShipSystemAPI system;
    private ShipAPI ship;
    private float sinceLast = 0f;	
    private IntervalUtil tracker = new IntervalUtil(1f, 2f);
    
    //This just initializes the script.
    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine)
    {
        this.ship = ship;
        this.system = system;
    }
    
    //So here we will tell the ship how to make use of the system.
    @Override
    public void advance (float amount, Vector2f position, Vector2f collisionDanger, ShipAPI target)
    {
        tracker.advance(amount);
        
        sinceLast += amount;
        //Once the interval has elapsed...
	if (tracker.intervalElapsed()) {
            //First we check if the ship is within closing distance of a target, or imminent danger of colliding with something:
            float check_combat = 1000f;
            float check_close = 750f;
            float cutoff_chance = 0f;
            
            //Then we set it up so that we can track ships
            int ships = 0;
            ShipAPI ship_tmp;
            
            //Now we iterate through all ships on the map
            for (Iterator iter = CombatUtils.getCombatEngine().getShips().iterator();
                iter.hasNext();)
            {
                ship_tmp = (ShipAPI) iter.next();
                
                //We can't collide with fighters, so ignore 'em
                if (ship_tmp.isFighter()) continue;
                if (ship_tmp.isHulk()) continue;
                
                if (CombatUtils.getDistance(ship_tmp, ship) <= (check_combat)) {
                    	
                    if (ship_tmp.getOwner() != ship.getOwner())	{		
                        ships++;
                    }
                }
                    
                if ((CombatUtils.getDistance(ship_tmp, ship) <= (check_close))) {
                    cutoff_chance += 1f;
                }
                
                float fluxLevel = ship.getFluxTracker().getFluxLevel();
                
                //First, lets make it so if no hostile ships are nearby, and the system isn't active, the vessel will turn on the system:
                if (cutoff_chance <= 1 && !system.isActive() && (ships <= 2) && fluxLevel <=0.35 && ((float) Math.random() > 0.1f)) {
                    ship.useSystem();
                }
                //If the ship is only near a few enemies and the system is active, she has moderate chance of turning the system off:
                else if  (cutoff_chance >= 2 && system.isActive() && (ships >= 2) && ((float) Math.random() > 0.6f)) {
                    ship.useSystem();
                }
                //If there are too many ships in the area the system is too risky to use, so the ship will just shut it down period:
                else if  (cutoff_chance >= 4 && system.isActive() && (ships >= 4)) {
                    ship.useSystem();
                }
                //Also, if the ships flux gets too high and the ships system is active, shut it down:
                else if  (system.isActive() && fluxLevel >=0.85) {
                    ship.useSystem();
                }
                    
                else { return; }
            }
        }
    }
}