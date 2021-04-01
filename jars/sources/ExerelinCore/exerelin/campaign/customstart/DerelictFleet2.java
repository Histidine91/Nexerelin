package exerelin.campaign.customstart;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;

@Deprecated
public class DerelictFleet2 extends DerelictFleet {
	
	@Override
	protected void addDerelicts() {
		ships.add("shepherd_Frontier");
		FactionAPI faction = Global.getSector().getFaction(Factions.DERELICT);
		ships.add(getShip(faction, ShipRoles.COMBAT_LARGE));
		//ships.add(getShip(faction, ShipRoles.COMBAT_LARGE));
		ships.add(getShip(faction, ShipRoles.COMBAT_MEDIUM));
		ships.add(getShip(faction, ShipRoles.COMBAT_MEDIUM));
		ships.add(getShip(faction, ShipRoles.COMBAT_SMALL));
		ships.add(getShip(faction, ShipRoles.COMBAT_SMALL));
		ships.add(getShip(faction, ShipRoles.COMBAT_SMALL));
	}
}
