package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;

/**
 * Response script for fleets responding to an invasion.
 * @author Histidine
 */
public class GBMilitaryResponseScript extends MilitaryResponseScript {
	
	public GBMilitaryResponseScript(MilitaryResponseParams params) {
		super(params);
	}
	
	@Override
	protected void respond(CampaignFleetAPI fleet) {
		super.respond(fleet);
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, "invasionResponse", true, 4f);
	}
	
	// change from vanilla: allow response by allied factions
	protected boolean couldRespond(CampaignFleetAPI fleet) {
		if (fleet.getAI() == null) return false;
		if (fleet.isPlayerFleet()) return false;
		if (fleet.isStationMode()) return false;
		if (AllianceManager.areFactionsAllied(fleet.getFaction().getId(), params.faction.getId())) 
			return false;
		
		// don't check for this here as it would skew proportiions of what's assigned where if a fleet is busy for a bit
		//if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.FLEET_BUSY)) return false;
		
		if (fleet.getAI() instanceof ModularFleetAIAPI) {
			ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
			if (ai.getAssignmentModule().areAssignmentsFrozen()) return false;
		}
		
		if (fleet.getCurrentAssignment() != null && 
				fleet.getCurrentAssignment().getAssignment() == FleetAssignment.GO_TO_LOCATION_AND_DESPAWN) {
			return false;
		}
		
		MemoryAPI memory = fleet.getMemoryWithoutUpdate();
		
		boolean patrol = memory.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET);
		boolean warFleet = memory.getBoolean(MemFlags.MEMORY_KEY_WAR_FLEET);
		boolean pirate = memory.getBoolean(MemFlags.MEMORY_KEY_PIRATE);
		boolean noMilitary = memory.getBoolean(MemFlags.FLEET_NO_MILITARY_RESPONSE);
		if (!(patrol || warFleet || pirate) || noMilitary) return false;
		
		return true;
	}
}
