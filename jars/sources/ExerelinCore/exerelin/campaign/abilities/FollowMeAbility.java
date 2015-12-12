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
import com.fs.starfarer.api.impl.campaign.ids.Pings;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Rattlesnark
 */
public class FollowMeAbility extends BaseDurationAbility {

	protected static final String STRING_CATEGORY = "exerelin_abilities";
	public static final float FOLLOW_DURATION = 10;
	public static final float FOLLOW_FETCH_RANGE = 600;
	public static final List<String> FOLLOW_VALID_FLEET_TYPES = new ArrayList<>();
	
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
	public String getSpriteName() {
		return Global.getSettings().getSpriteName("abilities", "exerelin_follow_me");
	}
	
	@Override
	protected void activateImpl() {
		if (entity.isInCurrentLocation()) {
			entity.addFloatingText(StringHelper.getString(STRING_CATEGORY, "followMeFloatText"), entity.getFaction().getBaseUIColor(), 0.5f);
			
			VisibilityLevel visibility = entity.getVisibilityLevelToPlayerFleet();
			if (visibility != VisibilityLevel.NONE) {
				Global.getSector().addPing(entity, Pings.COMMS);
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
			if (myFactionId.equals(fleetFactionId) || fleetFactionId.equals("player_npc") || AllianceManager.areFactionsAllied(myFactionId, fleetFactionId))
			{
				float dist = Misc.getDistance(fleet.getLocation(), entity.getLocation());
				//log.info("Distance of fleet " + otherFleet.getName() + ": " + dist);
				if (dist <= FOLLOW_FETCH_RANGE) 
				{
					if (!fleet.knowsWhoPlayerIs()) continue;
					MemoryAPI mem = fleet.getMemoryWithoutUpdate();
					String type = (String)mem.get(MemFlags.MEMORY_KEY_FLEET_TYPE);
					if (!FOLLOW_VALID_FLEET_TYPES.contains(type)) continue;
					if (mem.contains(MemFlags.FLEET_BUSY)) continue;
					if (fleet.getBattle() != null) continue;
					if (true)
					{
						CampaignFleetAIAPI ai = (CampaignFleetAIAPI) fleet.getAI();
						FleetAssignmentDataAPI currentAssignment = ai.getCurrentAssignment();
						if (currentAssignment.getAssignment() == FleetAssignment.ORBIT_AGGRESSIVE && currentAssignment.getTarget() == entity)
							ai.removeFirstAssignment();
						ai.addAssignmentAtStart(FleetAssignment.ORBIT_AGGRESSIVE, entity, FOLLOW_DURATION, null);
						//fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_BUSY, true, FOLLOW_DURATION);
						Global.getSector().addPing(fleet, Pings.COMMS);
					}
				}
			}
		}
	}

	@Override
	protected void applyEffect(float f) {
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

	public boolean hasTooltip() {
		return true;
	}
}
