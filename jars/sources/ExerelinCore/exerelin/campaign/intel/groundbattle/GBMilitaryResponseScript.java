package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import static com.fs.starfarer.api.impl.campaign.MilitaryResponseScript.RESPONSE_ASSIGNMENT;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;

/**
 * Response script for fleets responding to an invasion.
 * @author Histidine
 */
public class GBMilitaryResponseScript extends MilitaryResponseScript {
	
	protected GroundBattleIntel intel;
	
	public GBMilitaryResponseScript(MilitaryResponseParams params, GroundBattleIntel intel) 
	{
		super(params);
		this.intel = intel;
	}
	
	// Changes from vanilla: assignment is aggressive orbit instead of patrol, hostile-while-transponder-off memory key
	@Override
	protected void respond(CampaignFleetAPI fleet) {
		unrespond(fleet);
		
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
								MemFlags.FLEET_MILITARY_RESPONSE, params.responseReason, true, (1.5f + (float) Math.random()) * 0.2f);
		
		fleet.addAssignmentAtStart(FleetAssignment.ORBIT_AGGRESSIVE, params.target, 3f, params.actionText, null);
		FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
		if (curr != null) {
			curr.setCustom(RESPONSE_ASSIGNMENT);
		}
		
		float dist = Misc.getDistance(params.target, fleet);
		if (dist > 2000f) {
			fleet.addAssignmentAtStart(FleetAssignment.GO_TO_LOCATION, params.target, 3f, params.travelText, null);
			//fleet.addAssignmentAtStart(FleetAssignment.DELIVER_CREW, params.target, 3f, params.travelText, null);
			curr = fleet.getCurrentAssignment();
			if (curr != null) {
				curr.setCustom(RESPONSE_ASSIGNMENT);
			}
		}
		
		if (intel != null && Boolean.TRUE.equals(intel.playerIsAttacker)) {
			MemoryAPI mem = fleet.getMemoryWithoutUpdate();
			Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, "invasionResponse", true, 4f);
		}
		Global.getLogger(this.getClass()).info(String.format("%s responding to invasion", fleet.getNameWithFaction()));
	}
	
	protected void seeIfFleetShouldRespond(CampaignFleetAPI fleet) {
		/*
		if (!couldRespond(fleet)) return;
		
		if (isTemporarilyNotResponding(fleet)) return;
		
		respond(fleet);	// send EVERYONE
		*/
		super.seeIfFleetShouldRespond(fleet);
	}
	
	@Override
	public void initiateResponse() {
		super.initiateResponse();
		//float fraction = params.responseFraction / getResponseTotal();
		//Global.getLogger(this.getClass()).info(String.format("Responding fraction: %.3f", fraction));
	}
	
	// change from vanilla: allow response by allied factions
	protected boolean couldRespond(CampaignFleetAPI fleet) {
		if (fleet.getAI() == null) return false;
		if (fleet.isPlayerFleet()) return false;
		if (fleet.isStationMode()) return false;
		
		//Global.getLogger(this.getClass()).info(String.format("Checking %s fleet response to invasion", fleet.getNameWithFaction()));
		
		if (!AllianceManager.areFactionsAllied(fleet.getFaction().getId(), params.faction.getId())) 
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
		
		//Global.getLogger(this.getClass()).info(String.format("Fleet %s can respond to invasion", fleet.getNameWithFaction()));
		
		return true;
	}
}
