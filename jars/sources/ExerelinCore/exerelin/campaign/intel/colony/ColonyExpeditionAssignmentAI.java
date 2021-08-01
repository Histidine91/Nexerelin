package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetActionTextProvider;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.util.Misc;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;

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
		
		if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER)) {
			checkRaid(amount);
		}
	}
	
	// Try not to chase other stuff
	@Override
	protected void addLocalAssignment(RouteSegment current, boolean justSpawned) {
		if (justSpawned) {
			float progress = current.getProgress();
			RouteLocationCalculator.setLocation(fleet, progress, 
									current.from, current.getDestination());
		}
		if (current.from != null && current.to == null && !current.isFromSystemCenter()) {
			// changed from vanilla: was ORBIT_AGGRESSIVE
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, current.from, 
					current.daysMax - current.elapsed, getInSystemActionText(current),
					goNextScript(current));	
			return;
		}
		
		if (delegate instanceof ColonyActionStage) {
			ColonyActionStage action = (ColonyActionStage)delegate;
			if (fleet.getContainingLocation() == action.getTarget().getContainingLocation())
			{
				fleet.addAssignment(FleetAssignment.DELIVER_CREW, action.getTarget().getPrimaryEntity(), 
								Math.max(current.daysMax - current.elapsed, 3), 
								StringHelper.getFleetAssignmentString("travellingTo", action.getTarget().getName()));
				return;
			}
		}
		
		SectorEntityToken target = null;
		if (current.from.getContainingLocation() instanceof StarSystemAPI) {
			target = ((StarSystemAPI)current.from.getContainingLocation()).getCenter();
		} else {
			target = Global.getSector().getHyperspace().createToken(current.from.getLocation().x, current.from.getLocation().y);
		}
		
		fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, target, 
							current.daysMax - current.elapsed, getInSystemActionText(current));
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
			
			float dist = MathUtils.getDistance(fleet, action.getTarget().getPrimaryEntity());
			if (dist > 2000) return;
			
			giveRaidOrder(action.getTarget());
		}
	}
}
