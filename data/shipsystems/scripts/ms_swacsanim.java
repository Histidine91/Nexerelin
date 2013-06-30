package data.shipsystems.scripts;

//import com.fs.starfarer.api.combat.MutableShipStatsAPI;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
//import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
//import com.fs.starfarer.api.input.InputEventAPI;
//import data.scripts.plugins.CombatUtils;
import java.awt.Color;
import java.util.*;
//import org.lazywizard.lazylib.MathUtils;
//import org.lwjgl.util.vector.Vector2f;

//import java.util.HashMap;
//import java.util.Map;

public class ms_swacsanim implements EveryFrameWeaponEffectPlugin {
    
    // How many effect particles the carrier creates create per frame
    //private static final int sysActivPPF = 2;
   // private static final float sysActivRadius = 60f;
    // How many particles the receiving fighters create per frame
    //private static final int sysReceivePPF = 1;
    // How opaque the particles should be
    //private static final float sysActivPOp = .5f;
    //private static final float sysFade = .3f;
    // The color of the particle effects 
    //private static final Color sysActivPCol = new Color(93, 36, 145);
    //private CombatEngineAPI engine;
   // private static Map receiving = new HashMap();

    // So... thoughts.  Particle spread, from the carrier and effected fighters.  Carrier a slow, even pulse, fighters sort of just trailing partiles from approximately their center.
    //private void checkActiv(MutableShipStatsAPI emitter, String id, Vector2f center, float remaining) {
    //    
//        ShipAPI ship;
//        ShipAPI host_ship = CombatUtils.getOwner(emitter);
//        
//        for (Iterator iter = CombatUtils.getCombatEngine().getShips().iterator(); iter.hasNext();)
//	{
//            ship = (ShipAPI) iter.next();
//            if (ship == host_ship); 
//                //ship.getLocation(host_ship); 
//                
//                float glowStrength = (remaining > sysFade ? 1f : remaining / sysFade);
//                float fraction = .5f + ((float) Math.random() / 2f);
//                Vector2f particlePos, particleVel;
//        
                // Render particles for gravity well
//                for (int x = 0; x < sysActivPPF; x++)
 //               {
//                    particlePos = MathUtils.getRandomPointOnCircumference(center,
//                        sysActivRadius * fraction);
//                    particleVel = Vector2f.sub(center, particlePos, null);
//                    engine.addHitParticle(particlePos, particleVel, 5f,
//                        sysActivPOp * glowStrength,
//                        fraction, sysActivPCol);
//                }
                
                //receiving.put(host_ship);
                
//        }
//    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
		if (engine.isPaused()) return;
		
		AnimationAPI anim = weapon.getAnimation();

		//anim.pause();
		
		if (weapon.getShip().isHulk())	{
		anim.setFrame(0);		
		} else if (!weapon.getShip().getSystem().isActive()) {		
		anim.setFrame(0);
		} else {
		anim.setFrame(1);
		}
		anim.pause();		
	}
    
    //@Override
    //public void advance(float f, List<InputEventAPI> list) {
    //    throw new UnsupportedOperationException("Not supported yet.");
    //}

    //@Override
    //public void init(CombatEngineAPI engine) {
    //    throw new UnsupportedOperationException("Not supported yet.");
    //}
}
