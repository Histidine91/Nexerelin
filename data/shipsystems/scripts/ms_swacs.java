package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import data.scripts.plugins.CombatUtils;
import java.util.Iterator;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.Map;

public class ms_swacs implements ShipSystemStatsScript {


	//Just some global variables.
	public static final float RANGE = 3000f;
	public static final float ACCURACY_BONUS = 0.75f;
	public static final float RANGE_BONUS = 50f;
	
	//Creates a hashmap that keeps track of what ships are receiving the benefits.
	private static Map receiving = new HashMap();	
	
	@Override
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {

		
		//Declares two objects of type ShipAPI. 'ship' is just a generic holder for ships that are cycled through. 'host_ship' is the ship that is using the system.	
		ShipAPI ship;	
		ShipAPI host_ship = CombatUtils.getOwner(stats);	
		
		//This loop iterates through all ships active on the field.
		for (Iterator iter = CombatUtils.getCombatEngine().getShips().iterator(); iter.hasNext();)
		{
			
			ship = (ShipAPI) iter.next(); //Loads the current ship the iterator is on into 'ship'

			if (ship.isHulk()) continue; //We don't want to bother modifying stats of the ship if it's disabled.
                        if (ship == host_ship) continue; //Doesn't let the host ship receive the benefits it's giving to others.  Probably redundant.
			
			//If the ship is on the same team as the host ship, and it's within range, and its a fighter...
			if ((host_ship.getOwner() == ship.getOwner()) && ship.isFighter() && ship.isDrone() && (CombatUtils.getDistance(ship, host_ship) <= (RANGE)))  {
				
				//Modify this ship's stats.
                                ship.getWingMembers();
				ship.getMutableStats().getAutofireAimAccuracy().modifyFlat(id, ACCURACY_BONUS);
				ship.getMutableStats().getBallisticWeaponRangeBonus().modifyPercent(id, RANGE_BONUS);	
				ship.getMutableStats().getEnergyWeaponRangeBonus().modifyPercent(id, RANGE_BONUS);	
				
				
				//Speed is helpful when determining what range to use, since its effects are quite obvious.
				stats.getAcceleration().modifyFlat(id, 100f * effectLevel);
                                stats.getDeceleration().modifyFlat(id, 50f * effectLevel);
				stats.getTurnAcceleration().modifyFlat(id, 150f * effectLevel);
                                stats.getMaxTurnRate().modifyFlat(id, 100f * effectLevel);
				
				//Adds the ship to the hashmap, and associates it with the host ship.
				receiving.put(ship, host_ship);
                                System.out.println();
				//If the ship isn't in range but is contained in the hashmap, and the host ship of the ship is indeed this one...
			} else if ((receiving.containsKey(ship)) && (receiving.get(ship) == host_ship)){

				//removes all benefits
				ship.getMutableStats().getAutofireAimAccuracy().unmodify(id);	
				ship.getMutableStats().getBallisticWeaponRangeBonus().unmodify(id);	
				ship.getMutableStats().getEnergyWeaponRangeBonus().unmodify(id);	
				
				
				ship.getMutableStats().getMaxSpeed().unmodify(id);
				ship.getMutableStats().getAcceleration().unmodify(id);
				
				//Removes the ship from the hashmap.
				receiving.remove(ship);
				
			}
		}
	}
	
        @Override
	public void unapply(MutableShipStatsAPI stats, String id) {

		//Removes the effects from the host ship.
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);	
		
		//same objects as before.
		ShipAPI ship;	
		ShipAPI host_ship = CombatUtils.getOwner(stats);	
		System.out.println();
		//Loops through all the ships in the hashmap.
            for (Iterator iter = receiving.keySet().iterator(); iter.hasNext();)
            {
                ship = (ShipAPI) iter.next();
		
			//If the ship in the hash map is receiving benefits from this host ship (which is currently powering-down its system):
			//(This makes it so that one host ship bringing down its system doesn't remove benefits that are being applied to other ships by host ships elsewhere.
			if (receiving.get(ship) == host_ship) {
			
				//removes all benefits
				ship.getMutableStats().getAutofireAimAccuracy().unmodify(id);	
				ship.getMutableStats().getBallisticWeaponRangeBonus().unmodify(id);	
				ship.getMutableStats().getEnergyWeaponRangeBonus().unmodify(id);	

				ship.getMutableStats().getMaxSpeed().unmodify(id);
				ship.getMutableStats().getAcceleration().unmodify(id);
			}	
            }
	}
	
        @Override
	public StatusData getStatusData(int index, State state, float effectLevel) {

		if (index == 0) {
			return new StatusData("Forwarding tactical feed.", false);
		}
		return null;
	}
}
