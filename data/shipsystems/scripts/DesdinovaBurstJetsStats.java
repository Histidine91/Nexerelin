package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import data.scripts.plugins.CombatUtils;
import java.lang.Math;

public class DesdinovaBurstJetsStats implements ShipSystemStatsScript {

        public CombatEntityAPI ourShip;
        public ShipAPI ship;
        public CombatEngineAPI engine;
        CombatUtils utility = new CombatUtils();
        public Math math;
        
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
                ourShip = stats.getEntity();
                boolean fluxSpeedBoost = false;                
                ship = utility.getOwner(stats);
                if (ship != null) {
                    if (ship.getFluxTracker().getFluxLevel() <= stats.getZeroFluxMinimumFluxLevel().getModifiedValue()) { fluxSpeedBoost = true; }
                }
		if (state == ShipSystemStatsScript.State.OUT) {
                        if (effectLevel < 1f) {
                            stats.getMaxSpeed().modifyFlat(id, (effectLevel)*stats.getMaxSpeed().getModifiedValue()); // to slow down ship to its regular top speed while powering drive down
                            stats.getDeceleration().unmodify(id);
                            
                            float currentSpeed = (float) math.sqrt(math.pow(ourShip.getVelocity().getX(), 2) + math.pow(ourShip.getVelocity().getY(), 2));
                            float desiredSpeed = stats.getMaxSpeed().getModifiedValue();
                            if (fluxSpeedBoost) { desiredSpeed += stats.getZeroFluxSpeedBoost().getModifiedValue(); }
                            float multiplier =  desiredSpeed / currentSpeed;
                            
                            //start clamping our velocity
                            if (currentSpeed > desiredSpeed) {                                
                                ourShip.getVelocity().x *= multiplier;
                                ourShip.getVelocity().y *= multiplier;
                            }
                        }
		} else  {
                        stats.getMaxSpeed().modifyFlat(id, 350f);
			stats.getAcceleration().modifyFlat(id, 500f * effectLevel);
			stats.getDeceleration().modifyMult(id, 0.2f);
			stats.getTurnAcceleration().modifyFlat(id, 90f * effectLevel);
			stats.getTurnAcceleration().modifyPercent(id, 200f * effectLevel);
			stats.getMaxTurnRate().modifyMult(id, 1.5f * effectLevel);
			stats.getMaxTurnRate().modifyPercent(id, 130f * effectLevel);
                } 

	}
	public void unapply(MutableShipStatsAPI stats, String id) {
                stats.getMaxSpeed().unmodify(id); // to slow down ship to its regular top speed while powering drive down
                stats.getMaxTurnRate().unmodify(id);
                stats.getDeceleration().unmodify(id);
                stats.getMaxSpeed().unmodify(id);
                stats.getMaxTurnRate().unmodify(id);
                stats.getTurnAcceleration().unmodify(id);
                stats.getAcceleration().unmodify(id);
                stats.getDeceleration().unmodify(id);
                ourShip = stats.getEntity();
                boolean fluxSpeedBoost = false;
                ship = utility.getOwner(stats);
                if (ship != null) {
                    if (ship.getFluxTracker().getFluxLevel() <= stats.getZeroFluxMinimumFluxLevel().getModifiedValue()) { fluxSpeedBoost = true; }
                }
                
                float currentSpeed = (float) math.sqrt((ourShip.getVelocity().getX() * ourShip.getVelocity().getX()) + (ourShip.getVelocity().getY() * ourShip.getVelocity().getY()));
                float desiredSpeed = stats.getMaxSpeed().getBaseValue();
                if (fluxSpeedBoost) { desiredSpeed += stats.getZeroFluxSpeedBoost().getModifiedValue(); }
                float multiplier =  desiredSpeed / currentSpeed;
                
                if (currentSpeed > desiredSpeed) {                    
                    ourShip.getVelocity().x *= multiplier;
                    ourShip.getVelocity().y *= multiplier;
                }
                
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("improved maneuverability", false);
		} else if (index == 1) {
			return new StatusData("increased top speed", false);
		}
		return null;
	}
}
