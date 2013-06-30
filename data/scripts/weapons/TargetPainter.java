package data.scripts.weapons;

import java.util.List;
import com.fs.starfarer.api.combat.BeamAPI;
import com.fs.starfarer.api.combat.BeamEffectPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;


/**
 * @author EnderNerdcore
 */
public class TargetPainter implements BeamEffectPlugin {
    
    
    private MutableShipStatsAPI shipStats;
    private ShipAPI theShip;
    private IntervalUtil fireInterval = new IntervalUtil(0.2f, 0.3f);
    private boolean haveAppliedFullDebuff = false;
    private String targetId;
    private float damageAmountCounter = 0f;
    
    public void advance(float amount, CombatEngineAPI engine, BeamAPI beam) {

        CombatEntityAPI target = beam.getDamageTarget(); //identify who our beam is hitting

        List shipList = engine.getShips(); //get a list of all of the ships in play
                    for (int i = 0; i < shipList.size(); i++) { //loop through, and identify which ship is our target so we can get the ID
                        if ((target != null) && (shipList.get(i) == target)){
                            theShip = (ShipAPI) shipList.get(i);
                        }
                    }
                    
        if (target != null && target instanceof ShipAPI) { //make sure that there is a target before we apply any effects, and that our target is a ship rather than an asteroid, missile, or other object
            /* && 
				(target.getShield() == null || !target.getShield().isWithinArc(beam.getTo()))
             */
            
                    targetId = theShip.getFleetMemberId(); //we need the fleet member id for the string for identifying who our debuff goes on to
                    shipStats = theShip.getMutableStats(); //we also need to set up an instance of mutable stats for that ship to debuff it
                    
			if (beam.getBrightness() >= 1f) { //only apply the effects while our beam is full strength
                            if (!haveAppliedFullDebuff) { //only add to the debuff until we've hit the max
                                shipStats.getArmorDamageTakenMult().unmodify(targetId); //remove the current debuffs
                                shipStats.getShieldDamageTakenMult().unmodify(targetId);
                                shipStats.getHullDamageTakenMult().unmodify(targetId);
                                shipStats.getArmorDamageTakenMult().modifyMult(targetId, (1f + (damageAmountCounter/100f))); //modify the debuff to the new values, to a max of 200%
                                shipStats.getShieldDamageTakenMult().modifyMult(targetId, (1f + (damageAmountCounter/100f)));
                                shipStats.getHullDamageTakenMult().modifyMult(targetId, (1f + (damageAmountCounter/100f)));
                                if (damageAmountCounter >= 100) { //if we've applied the entire debuff, mark it down so we don't enter this if statement again
                                    haveAppliedFullDebuff = true;
                                } else { //otherwise, advance the counter
                                    damageAmountCounter++;
                                }
                                
                            }
                            
                            fireInterval.advance(amount); //this is a frame counter, it keeps track of our time
                                
                            if (fireInterval.intervalElapsed()) { //once our beam is no longer hitting the target, we need to remove the debuffs
                                shipStats.getArmorDamageTakenMult().unmodify(targetId);
                                shipStats.getShieldDamageTakenMult().unmodify(targetId);
                                shipStats.getHullDamageTakenMult().unmodify(targetId);
                            }
                                }
                        
                        
        }
        
        
        
    }
}
