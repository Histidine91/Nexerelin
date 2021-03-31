package exerelin.campaign.abilities.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.abilities.ai.SustainedBurnAbilityAI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

// same as vanilla, except:
//	use sustained burn if our target is also using sustained burn
//  minimum distance for SB now 1000 (was 2000)
public class Nex_SustainedBurnAbilityAI extends SustainedBurnAbilityAI {
	private IntervalUtil interval = new IntervalUtil(0.05f, 0.15f);

	@Override
	public void advance(float days) {
		interval.advance(days * SustainedBurnAbilityAI.AI_FREQUENCY_MULT);
		if (!interval.intervalElapsed()) return;
		
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		if (ability.isActiveOrInProgress()) {
			mem.set(FleetAIFlags.HAS_SPEED_BONUS, true, 0.2f);
			mem.set(FleetAIFlags.HAS_HIGHER_DETECTABILITY, true, 0.2f);
		}
		
		boolean smuggler = mem.getBoolean(MemFlags.MEMORY_KEY_SMUGGLER);
		if (smuggler) {
			if (ability.isActive()) ability.deactivate();
			return;
		}
		
		
		if (fleet.getAI() instanceof ModularFleetAIAPI) {
			ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
			if (ai.getTacticalModule().isMaintainingContact()) {
				if (ability.isActive()) ability.deactivate();
				return;
			}
		}
		
		if (mem.getBoolean(FleetAIFlags.HAS_LOWER_DETECTABILITY) && !ability.isActive()) {
			return;
		}
		
//		if (true) {
//			if (!ability.isActive()) {
//				ability.activate();
//			} else {
//				ability.deactivate();
//			}
//			return;
//		}
//		if (fleet.getAI() != null && fleet.getAI().getCurrentAssignmentType() == FleetAssignment.STANDING_DOWN) {
//			return;
//		}
		
		CampaignFleetAPI pursueTarget = mem.getFleet(FleetAIFlags.PURSUIT_TARGET);
		CampaignFleetAPI fleeingFrom = mem.getFleet(FleetAIFlags.NEAREST_FLEEING_FROM);

//		float moveDir = Misc.getDesiredMoveDir(fleet);
		
		float burn = Misc.getBurnLevelForSpeed(fleet.getVelocity().length());
//		if (ability.isActive() && burn >= 5f) {
//			float diff = Misc.getAngleDiff(moveDir, Misc.getAngleInDegrees(fleet.getVelocity()));
//			if (diff > 90f) {
//				ability.deactivate();
//				return;
//			}
//		}
		
		
		float activationTime =  ability.getSpec().getActivationDays() * Global.getSector().getClock().getSecondsPerDay();
		if (fleeingFrom != null) {
			float dist = Misc.getDistance(fleet.getLocation(), fleeingFrom.getLocation());
			float speed = Math.max(1f, fleeingFrom.getTravelSpeed());
			float time = dist / speed;
			if (!ability.isActive()) { // far enough to wind up and get away
				if (time >= activationTime + 5f) {
					ability.activate();
				}
			} else { // too close to wind up, better chance of getting away by turning SB off
				if (burn <= 3 && time < 5f) {
					ability.deactivate();
				}
			}
			return;
		}
		
		if (pursueTarget != null) {
//			if (pursueTarget.isPlayerFleet()) {
//				System.out.println("fwefewwe");
//			}
			if (ability.isActive()) {
				float toTarget = Misc.getAngleInDegrees(fleet.getLocation(), pursueTarget.getLocation());
				float velDir = Misc.getAngleInDegrees(fleet.getVelocity());
				float diff = Misc.getAngleDiff(toTarget, velDir);
				if (diff > 60f) {
					ability.deactivate();
				}
			}
			return;
		}
		
		
		if (fleet.getAI() != null && fleet.getAI().getCurrentAssignment() != null) {
			FleetAssignment curr = fleet.getAI().getCurrentAssignmentType();
			SectorEntityToken target = fleet.getAI().getCurrentAssignment().getTarget();
			
			// burn if our target is also burning
			if (target instanceof CampaignFleetAPI) {
				CampaignFleetAPI fleet = (CampaignFleetAPI)target;
				AbilityPlugin sb = fleet.getAbility(Abilities.SUSTAINED_BURN);
				
				if (sb != null && sb.isActive()) {
					if (!ability.isActive()) {
						ability.activate();
					}
					return;
				}
			}

			boolean inSameLocation = target != null && target.getContainingLocation() == fleet.getContainingLocation();
			float distToTarget = 100000f;
			if (inSameLocation) {
				distToTarget = Misc.getDistance(target.getLocation(), fleet.getLocation());
			}
			// modified from vanilla
			boolean close = distToTarget < 1000;	// 2000;

			if (close && 
					(curr == FleetAssignment.ORBIT_PASSIVE ||
					 curr == FleetAssignment.ORBIT_AGGRESSIVE ||
					 curr == FleetAssignment.DELIVER_CREW ||
					 curr == FleetAssignment.DELIVER_FUEL ||
					 curr == FleetAssignment.DELIVER_MARINES ||
					 curr == FleetAssignment.DELIVER_PERSONNEL ||
					 curr == FleetAssignment.DELIVER_RESOURCES ||
					 curr == FleetAssignment.DELIVER_SUPPLIES ||
					 curr == FleetAssignment.RESUPPLY ||
					 curr == FleetAssignment.GO_TO_LOCATION ||
					 curr == FleetAssignment.GO_TO_LOCATION_AND_DESPAWN)
					 ) {
				if (ability.isActive()) ability.deactivate();
				return;
			}
			if (inSameLocation && (
					curr == FleetAssignment.RAID_SYSTEM ||
					curr == FleetAssignment.PATROL_SYSTEM)) {
				if (ability.isActive()) ability.deactivate();
				return;
			}
		}
		
		
		Vector2f travelDest = mem.getVector2f(FleetAIFlags.TRAVEL_DEST);
		if (travelDest != null) {
			float dist = Misc.getDistance(fleet.getLocation(), travelDest);
			float speed = Math.max(1f, fleet.getTravelSpeed());
			float time = dist / speed;
			if (!ability.isActive()) {
				if (time > activationTime * 2f) {
					ability.activate();
				}
			}
			return;
		}
	}
}






