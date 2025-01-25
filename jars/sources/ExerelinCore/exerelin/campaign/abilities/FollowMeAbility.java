package exerelin.campaign.abilities;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.abilities.BaseDurationAbility;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.intel.defensefleet.DefenseAssignmentAI;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FollowMeAbility extends BaseDurationAbility {

	protected static final String STRING_CATEGORY = "exerelin_abilities";
	public static final float FOLLOW_DURATION = 7;
	public static final float FOLLOW_DURATION_PASSIVE = 7;
	public static final float FOLLOW_FETCH_RANGE = 600;
	public static final Set<String> FOLLOW_VALID_FLEET_TYPES = new HashSet<>();
	public static final String BUSY_REASON = "nex_followMe";
	public static final List<String> ALLOWED_BUSY_REASONS = new ArrayList<>();
	public static final FleetAssignment FLEET_ASSIGNMENT = FleetAssignment.ORBIT_PASSIVE;
	
	static {
		FOLLOW_VALID_FLEET_TYPES.add(FleetTypes.PATROL_SMALL);
		FOLLOW_VALID_FLEET_TYPES.add(FleetTypes.PATROL_MEDIUM);
		FOLLOW_VALID_FLEET_TYPES.add(FleetTypes.PATROL_LARGE);
		FOLLOW_VALID_FLEET_TYPES.add("exerelinInvasionSupportFleet");
		FOLLOW_VALID_FLEET_TYPES.add("nex_defenseFleet");
		
		ALLOWED_BUSY_REASONS.add(BUSY_REASON);
		ALLOWED_BUSY_REASONS.add(DefenseAssignmentAI.BUSY_REASON);
	}
	
	protected boolean isBusyForAllowedReason(CampaignFleetAPI fleet) 
	{
		MemoryAPI mem = fleet.getMemoryWithoutUpdate();
		if (!mem.contains(MemFlags.FLEET_BUSY))
			return false;
		
		for (String reason : ALLOWED_BUSY_REASONS) 
		{
			if (Misc.flagHasReason(mem, MemFlags.FLEET_BUSY, reason)) 
			{
				return true;
			}
		}
		return false;
	}
	
	@Override
	protected String getActivationText() {
		return StringHelper.getString(STRING_CATEGORY, "followMeTitle");
	}
	
	@Override
	protected void activateImpl() {
		if (entity.isInCurrentLocation()) {
			entity.addFloatingText(StringHelper.getString(STRING_CATEGORY, "followMeFloatText"), entity.getFaction().getBaseUIColor(), 1f);
			
			VisibilityLevel visibility = entity.getVisibilityLevelToPlayerFleet();
			if (visibility != VisibilityLevel.NONE) {
				Global.getSector().addPing(entity, "follow_me_send");
			}
		}
		String myFactionId = entity.getFaction().getId();
		if (myFactionId.equals(Factions.PLAYER))
		{
			myFactionId = PlayerFactionStore.getPlayerFactionId();
		}
		
		List<CampaignFleetAPI> fleets = entity.getContainingLocation().getFleets();
		for (CampaignFleetAPI fleet : fleets) {
			if (fleet == entity) continue;
			
			String fleetFactionId = fleet.getFaction().getId();
			if (myFactionId.equals(fleetFactionId) || fleetFactionId.equals(Factions.PLAYER) || AllianceManager.areFactionsAllied(myFactionId, fleetFactionId))
			{
				float dist = Misc.getDistance(fleet.getLocation(), entity.getLocation());
				//log.info("Distance of fleet " + otherFleet.getName() + ": " + dist);
				if (dist <= FOLLOW_FETCH_RANGE) 
				{
					if (!fleet.knowsWhoPlayerIs()) continue;
					MemoryAPI mem = fleet.getMemoryWithoutUpdate();
					String type = NexUtilsFleet.getFleetType(fleet);
					if (!FOLLOW_VALID_FLEET_TYPES.contains(type)) continue;
					if (mem.contains(MemFlags.FLEET_BUSY) && !isBusyForAllowedReason(fleet)) continue;
					if (fleet.getBattle() != null) continue;
					if (fleet.isStationMode()) continue;
					if (true)
					{
						CampaignFleetAIAPI ai = fleet.getAI();
						if (ai == null) continue;
						
						// clear current follow assignments
						FleetAssignmentDataAPI currentAssignment = ai.getCurrentAssignment();
						/*
						if (currentAssignment != null && currentAssignment.getTarget() == entity)
						{
							ai.removeFirstAssignmentIfItIs(FleetAssignment.ORBIT_AGGRESSIVE);
							currentAssignment = ai.getCurrentAssignment();
						}
						*/
						if (currentAssignment != null && currentAssignment.getTarget() == entity)
						{
							ai.removeFirstAssignmentIfItIs(FLEET_ASSIGNMENT);
						}
						
						//ai.addAssignmentAtStart(FleetAssignment.ORBIT_AGGRESSIVE, entity, FOLLOW_DURATION - FOLLOW_DURATION_PASSIVE, null);
						ai.addAssignmentAtStart(FLEET_ASSIGNMENT, entity, FOLLOW_DURATION_PASSIVE, null);
						Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
								MemFlags.FLEET_BUSY, BUSY_REASON, true, FOLLOW_DURATION_PASSIVE);
						Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
								MemFlags.FLEET_IGNORES_OTHER_FLEETS, BUSY_REASON, true, FOLLOW_DURATION_PASSIVE);
						
						//fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true, FOLLOW_DURATION);
						Global.getSector().addPing(fleet, "follow_me_receive");
					}
				}
			}
		}
	}

	
	@Override
	protected void applyEffect(float amount, float level) {
	}
	
	@Override
	protected void deactivateImpl() {
	}

	@Override
	protected void cleanupImpl() {
	}
	
	@Override
	public float getActivationDays() {
		return 0f;
	}

	@Override
	public float getCooldownDays() {
		return 0.1f;
	}

	@Override
	public float getDeactivationDays() {
		return 0f;
	}

	@Override
	public float getDurationDays() {
		return 0f;
	}
	
	@Override
	public void createTooltip(TooltipMakerAPI tooltip, boolean expanded) {
		
		LabelAPI title = tooltip.addTitle(StringHelper.getString(STRING_CATEGORY, "followMeTitle"));
//		title.highlightLast(status);
//		title.setHighlightColor(gray);

		float pad = 10f;
		String highlight = (int)FOLLOW_DURATION + "";
		String tooltip1 = StringHelper.getString(STRING_CATEGORY, "followMeTooltip1");
		tooltip1 = StringHelper.substituteToken(tooltip1, "$numDays", highlight);
		tooltip.addPara(tooltip1, pad, Misc.getHighlightColor(), highlight);
		tooltip.addPara(StringHelper.getString(STRING_CATEGORY, "followMeTooltip2"), pad);
	}

	@Override
	public boolean hasTooltip() {
		return true;
	}
}
