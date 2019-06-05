package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetActionTextProvider;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;

// by Vayra, modded with external strings + some other stuff
public class ColonyExpeditionAssignmentAI extends RouteFleetAssignmentAI implements FleetActionTextProvider {

	public ColonyExpeditionAssignmentAI(CampaignFleetAPI fleet, RouteData route, BaseAssignmentAI.FleetActionDelegate delegate) {
		super(fleet, route, delegate);
		fleet.getAI().setActionTextProvider(this);
	}
	
	@Override
	public void advance(float amount) {
		super.advance(amount, false);
		
		RouteSegment curr = route.getCurrent();
		if (curr != null && 
				(
					BaseRaidStage.STRAGGLER.equals(route.getCustom()) || 
					AssembleStage.WAIT_STAGE.equals(curr.custom) || 
					curr.isTravel())) {
			Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), MemFlags.FLEET_BUSY, "raid_wait", true, 1);
		}
		
		checkCapture(amount);
		
		// // don't limit raids to RAIDER fleets
		if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER)) {
			checkRaid(amount);
		}
	}

	@Override
	protected String getInSystemActionText(RouteSegment segment) {
		if (AssembleStage.WAIT_STAGE.equals(segment.custom)) {
			return StringHelper.getFleetAssignmentString("rendezvous", null);
		}
		String s = null;
		if (delegate != null) s = delegate.getRaidInSystemText(fleet);
		if (s == null) s = StringHelper.getFleetAssignmentString("onColonyExpedition", null);
		return s;
	}

	@Override
	protected String getEndingActionText(RouteSegment segment) {
		return super.getEndingActionText(segment);
	}

	@Override
	protected String getStartingActionText(RouteSegment segment) {
		if (AssembleStage.PREP_STAGE.equals(segment.custom)) {
			String s = null;
			if (delegate != null) s = delegate.getRaidPrepText(fleet, segment.from);
			if (s == null) s = StringHelper.getFleetAssignmentString("preparingForShort", null, "missionColony");
			return s;
		}
		if (segment.from == route.getMarket().getPrimaryEntity()) {
			return StringHelper.getFleetAssignmentString("orbiting", route.getMarket().getName());
		}
		
		String s = null;
		if (delegate != null) s = delegate.getRaidDefaultText(fleet);
		if (s == null) s = StringHelper.getFleetAssignmentString("onColonyExpedition", null);
		return s;
	}

	@Override
	protected String getTravelActionText(RouteSegment segment) {
		return super.getTravelActionText(segment);
	}

	@Override
	public String getActionText(CampaignFleetAPI fleet) {
		FleetAssignmentDataAPI curr = fleet.getCurrentAssignment();
		if (curr != null && curr.getAssignment() == FleetAssignment.PATROL_SYSTEM &&
				curr.getActionText() == null) {
			
			String s = null;
			if (delegate != null) s = delegate.getRaidDefaultText(fleet);
			if (s == null) s = StringHelper.getFleetAssignmentString("onColonyExpedition", null);
			return s;
			
		}
		return null;
	}
	
	// handle colonization action properly
	@Override
	protected void checkColonyAction() {
		if (!canTakeAction()) return;
		
		if (delegate instanceof ColonyActionStage) {
			ColonyActionStage action = (ColonyActionStage)delegate;
			if (fleet.getContainingLocation() != action.getTarget().getContainingLocation())
				return;
			
			giveRaidOrder(action.getTarget());
		}
	}
}
