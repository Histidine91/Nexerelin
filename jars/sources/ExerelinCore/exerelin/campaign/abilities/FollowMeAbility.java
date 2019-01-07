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
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FollowMeAbility extends BaseDurationAbility {

	protected static final String STRING_CATEGORY = "exerelin_abilities";
	public static final float FOLLOW_DURATION = 15;
	public static final float FOLLOW_DURATION_PASSIVE = 15;
	public static final float FOLLOW_FETCH_RANGE = 600;
	public static final Set<String> FOLLOW_VALID_FLEET_TYPES = new HashSet<>();
	
	static {
		FOLLOW_VALID_FLEET_TYPES.add(FleetTypes.PATROL_SMALL);
		FOLLOW_VALID_FLEET_TYPES.add(FleetTypes.PATROL_MEDIUM);
		FOLLOW_VALID_FLEET_TYPES.add(FleetTypes.PATROL_LARGE);
		FOLLOW_VALID_FLEET_TYPES.add("exerelinInvasionSupportFleet");
		FOLLOW_VALID_FLEET_TYPES.add("exerelinDefenceFleet");
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
					String type = ExerelinUtilsFleet.getFleetType(fleet);
					if (!FOLLOW_VALID_FLEET_TYPES.contains(type)) continue;
					if (mem.contains(MemFlags.FLEET_BUSY)) continue;
					if (fleet.getBattle() != null) continue;
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
							ai.removeFirstAssignmentIfItIs(FleetAssignment.ORBIT_PASSIVE);
						}
						
						//ai.addAssignmentAtStart(FleetAssignment.ORBIT_AGGRESSIVE, entity, FOLLOW_DURATION - FOLLOW_DURATION_PASSIVE, null);
						ai.addAssignmentAtStart(FleetAssignment.ORBIT_PASSIVE, entity, FOLLOW_DURATION_PASSIVE, null);
						
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
