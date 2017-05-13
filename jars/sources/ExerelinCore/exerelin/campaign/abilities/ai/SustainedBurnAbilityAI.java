package exerelin.campaign.abilities.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.ai.FleetAIFlags;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.abilities.ai.BaseAbilityAI;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class SustainedBurnAbilityAI extends BaseAbilityAI {

	public static final float AI_FREQUENCY_MULT = 1f;
	public static final float DISTANCE_TO_STOP = 800f;
	public static final float DISTANCE_TO_START = 2000f;
	public static final Set<FleetAssignment> NO_BURN_ASSIGNMENTS = new HashSet<>(Arrays.asList(new FleetAssignment[]{
		FleetAssignment.DEFEND_LOCATION, FleetAssignment.PATROL_SYSTEM, 
		FleetAssignment.RAID_SYSTEM, FleetAssignment.HOLD,
		FleetAssignment.STANDING_DOWN, FleetAssignment.GO_TO_LOCATION_AND_DESPAWN
	}));
	
	protected final IntervalUtil interval = new IntervalUtil(0.2f, 0.25f);
	
	protected boolean isFleetUsingAbility(CampaignFleetAPI target, String ability)
	{
		if (target.getAbility(ability) == null)
			return false;
		return target.getAbility(ability).isActiveOrInProgress();
	}
	
	protected boolean shouldStop()
	{
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		CampaignFleetAPI fleeingFrom = mem.getFleet(FleetAIFlags.NEAREST_FLEEING_FROM);
		if (fleeingFrom != null)
		{
			writeDebugMessage(fleet.getName() + " is running for its life, don't stop");
			return false;
		}
			
		if (fleet.getAI() == null) 
		{
			writeDebugMessage(fleet.getName() + " hass null AI, stop");
			return true;
		}
		FleetAssignmentDataAPI assignmentData = fleet.getAI().getCurrentAssignment();
		if (assignmentData == null) 
		{
			writeDebugMessage(fleet.getName() + " has null assignment, stop");
			return true;
		}
		
		
		FleetAssignment assignment = assignmentData.getAssignment();
		if (NO_BURN_ASSIGNMENTS.contains(assignment)) 
		{
			writeDebugMessage(fleet.getName() + " non-burn assignment, stop");
			return true;
		}
		
		SectorEntityToken target = assignmentData.getTarget();
		if (target instanceof CampaignFleetAPI)
		{
			CampaignFleetAPI targetFleet = (CampaignFleetAPI)target;
			if (target.getContainingLocation() != fleet.getContainingLocation())
			{
				writeDebugMessage(fleet.getName() + " target in another location, don't stop");
				return false;
			}
				
			float closingSpeed = Misc.getClosingSpeed(fleet.getLocation(), targetFleet.getLocation(), 
													  fleet.getVelocity(), targetFleet.getVelocity());
			
			// we're facing the wrong way and already moving, turning to catch them is going to be a huge pain, may as well stop
			//if (closingSpeed < 0 && fleet.getVelocity().x != 0 && fleet.getVelocity().y != 0)
			//	return true;
			
			// target is using S burn, so will we
			if (isFleetUsingAbility(targetFleet, Abilities.SUSTAINED_BURN))
			{
				writeDebugMessage(fleet.getName() + " target fleet is burning, don't stop");
				return false;
			}
			
			// following target, don't S burn if we're already close
			if (assignment == FleetAssignment.ORBIT_AGGRESSIVE 
					|| assignment == FleetAssignment.ORBIT_PASSIVE)
			{
				writeDebugMessage(fleet.getName() + " is close enough to escortee?");
				return Misc.getDistance(fleet.getLocation(), target.getLocation()) <= DISTANCE_TO_STOP;
			}
		}
		else
		{
			// destination in other system, don't turn off S burn
			if (target.getContainingLocation() != fleet.getContainingLocation())
			{
				writeDebugMessage(fleet.getName() + " wants to go to another location, don't stop");
				return false;
			}
			else
			{
				// if close, turn off S burn
				float dist = Misc.getDistance(fleet.getLocation(), target.getLocation());
				if (dist <= DISTANCE_TO_STOP)
				{
					writeDebugMessage(fleet.getName() + " is near destination, stop");
					return true;
				}
			}
		}
		return false;
	}
	
	protected boolean shouldStart()
	{
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		
		CampaignFleetAPI pursueTarget = mem.getFleet(FleetAIFlags.PURSUIT_TARGET);
		CampaignFleetAPI fleeingFrom = mem.getFleet(FleetAIFlags.NEAREST_FLEEING_FROM);
		
		// being pursued by a faster enemy that's not too close: turn on
		if (fleeingFrom != null) {
			VisibilityLevel level = fleet.getVisibilityLevelTo(fleeingFrom);
			if (level == VisibilityLevel.NONE) 
			{
				writeDebugMessage(fleet.getName() + " pursuer can't see us, don't burn");
				return false;
			}
			
			boolean hopelessFight = isGreatlyOutmatchedBy(fleeingFrom);
			float dist = Misc.getDistance(fleet.getLocation(), fleeingFrom.getLocation()) - fleet.getRadius() + fleeingFrom.getRadius();
			float ourSpeed = fleet.getFleetData().getBurnLevel();
			float theirSpeed = fleeingFrom.getFleetData().getBurnLevel();
			
			if (ourSpeed >= theirSpeed) return false;	// no need (yet)
			if (hopelessFight && dist > 400)
			{
				writeDebugMessage(fleet.getName() + " is running for its life, burn");
				return true;
			}
		}
		
		// pursuing a faster enemy, and would be faster then them with SB on: turn on
		if (pursueTarget != null) {
			if (fleet.getAI() instanceof ModularFleetAIAPI) {
				ModularFleetAIAPI ai = (ModularFleetAIAPI) fleet.getAI();
				if (ai.getTacticalModule().isMaintainingContact()) // do we really want to catch them? meh
				{
					writeDebugMessage(fleet.getName() + " maintaining contact with target, don't burn");
					return false;
				}
			}
			
			VisibilityLevel level = pursueTarget.getVisibilityLevelTo(fleet);
			if (level == VisibilityLevel.NONE) 
			{
				writeDebugMessage(fleet.getName() + " can't see target, don't burn");
				return false;
			}
			
			boolean targetInsignificant = otherInsignificant(pursueTarget);
			if (targetInsignificant) 
			{
				writeDebugMessage(fleet.getName() + " is chasing small fry, don't burn");
				return false;
			}

			float dist = Misc.getDistance(fleet.getLocation(), pursueTarget.getLocation()) - fleet.getRadius() - pursueTarget.getRadius();
			if (dist < 0) return false;	// already on top of them
			
			float ourSpeed = fleet.getFleetData().getBurnLevel();
			float theirSpeed = pursueTarget.getFleetData().getBurnLevel();
			if (ourSpeed > theirSpeed) 
			{
				writeDebugMessage(fleet.getName() + " is already faster than target, don't burn");	// no need (yet)
				return false;
			}	
			
			// they're trying to S-burn away, use our own
			if (isFleetUsingAbility(pursueTarget, Abilities.SUSTAINED_BURN))
			{
				writeDebugMessage(fleet.getName() + " target fleet is burning, burn");
				return true;
			}
		}
		
		FleetAssignmentDataAPI assignmentData = fleet.getAI().getCurrentAssignment();
		if (assignmentData != null)
		{
			FleetAssignment assignment = assignmentData.getAssignment();
			if (NO_BURN_ASSIGNMENTS.contains(assignment)) return true;
			
			SectorEntityToken target = assignmentData.getTarget();
			if (target instanceof CampaignFleetAPI)
			{
				CampaignFleetAPI targetFleet = (CampaignFleetAPI)target;
				// target in another system, use S burn
				if (target.getContainingLocation() != fleet.getContainingLocation())
				{
					writeDebugMessage(fleet.getName() + " target fleet is in another system, burn");
					return true;
				}

				// target is using S burn, so will we
				if (isFleetUsingAbility(targetFleet, Abilities.SUSTAINED_BURN))
				{
					writeDebugMessage(fleet.getName() + " target fleet is burning, burn");
					return true;
				}
				
				// following target, S burn to get closer?
				if (assignment == FleetAssignment.ORBIT_AGGRESSIVE 
						|| assignment == FleetAssignment.ORBIT_PASSIVE)
				{
					if (Misc.getDistance(fleet.getLocation(), targetFleet.getLocation()) >= DISTANCE_TO_START)
					{
						writeDebugMessage(fleet.getName() + " target fleet is far away, burn");
						return true;
					}
				}
			}
			else
			{
				// destination in other system, use S burn
				if (target.getContainingLocation() != fleet.getContainingLocation())
				{
					writeDebugMessage(fleet.getName() + " wants to go to another location, start");
					return true;
				}
				else
				{
					float dist = Misc.getDistance(fleet.getLocation(), target.getLocation());
					if (dist >= DISTANCE_TO_START)
					{
						writeDebugMessage(fleet.getName() + " destination is far enough away, burn");
						return true;
					}
				}
			}
		}
		return false;
	}

	boolean wasActive = false;
	
	@Override
	public void advance(float days) {
		boolean active = ability.isActiveOrInProgress();
		if (active != wasActive)
		{
			writeDebugMessage(fleet.getName() + " burn state changed, now " + active);
			wasActive = active;
		}
		
		interval.advance(days * AI_FREQUENCY_MULT);
		if (!interval.intervalElapsed()) return;
		
		if (ability.isActiveOrInProgress()) {
			// should we stop?
			if (shouldStop())
			{
				writeDebugMessage(fleet.getName() + " stopping burn");
				ability.deactivate();
				return;
			}
			MemoryAPI mem = fleet.getMemoryWithoutUpdate();
			mem.set(FleetAIFlags.HAS_SPEED_BONUS, true, 0.4f);
			mem.set(FleetAIFlags.HAS_VISION_PENALTY, true, 0.4f);
			writeDebugMessage(fleet.getName() + " is already burning, do nothing");
			return;
		}
		
		if (fleet.getAI() != null && NO_BURN_ASSIGNMENTS.contains(fleet.getAI().getCurrentAssignmentType())) {
			return;
		}
		
		if (!ability.isUsable()) return;
		
		if (shouldStart())
		{
			writeDebugMessage(fleet.getName() + " starting burn");
			ability.activate();
		}
	}
	
	protected boolean isGreatlyOutmatchedBy(CampaignFleetAPI other) {
		float us = getStrength(fleet);
		float them = getStrength(other);
		
		if (us < 0.1f) us = 0.1f;
		if (them < 0.1f) them = 0.1f;
		return them > us * 3f;
	}
	
	protected boolean otherInsignificant(CampaignFleetAPI other) {
		float us = getStrength(fleet);
		float them = getStrength(other);
		
		if (us < 0.1f) us = 0.1f;
		if (them < 0.1f) them = 0.1f;
		return us > them * 3f;
	}
	
	public static float getStrength(CampaignFleetAPI fleet) {
		float str = 0f;
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			if (member.canBeDeployedForCombat()) {
				float strength = member.getMemberStrength();
				str += strength;
			}
		}
		return str;
	}
	
	protected void writeDebugMessage(String str)
	{
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		if (fleet.getContainingLocation() == player.getContainingLocation()
					&& Misc.getDistance(fleet.getLocation(), player.getLocation()) <= 700)
		{
			//Global.getSector().getCampaignUI().addMessage(str);
		}
	}
}






