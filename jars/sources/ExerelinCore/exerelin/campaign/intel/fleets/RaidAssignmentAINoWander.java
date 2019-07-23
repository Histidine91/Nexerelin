package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;

// vanilla, but replaces some assignment types to keep fleets from wandering off
// see http://fractalsoftworks.com/forum/index.php?topic=5061.msg253229#msg253229
public class RaidAssignmentAINoWander extends RaidAssignmentAI {
	
	public RaidAssignmentAINoWander(CampaignFleetAPI fleet, RouteData route, FleetActionDelegate delegate) {
		super(fleet, route, delegate);
	}
	
	@Override
	protected void addLocalAssignment(final RouteManager.RouteSegment current, boolean justSpawned) {
		if (justSpawned) {
			float progress = current.getProgress();
			RouteLocationCalculator.setLocation(fleet, progress, 
									current.from, current.getDestination());
		}
		if (current.from != null && current.to == null && !current.isFromSystemCenter()) {
//			if (justSpawned) {
//				Vector2f loc = Misc.getPointWithinRadius(current.from.getLocation(), 500);
//				fleet.setLocation(loc.x, loc.y);
//			}
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, current.from, 
					current.daysMax - current.elapsed, getInSystemActionText(current),
					goNextScript(current));		
			return;
		}
		
//		if (justSpawned) {
//			Vector2f loc = Misc.getPointAtRadius(new Vector2f(), 8000);
//			fleet.setLocation(loc.x, loc.y);
//		}
		
		SectorEntityToken target = null;
		if (current.from.getContainingLocation() instanceof StarSystemAPI) {
			target = ((StarSystemAPI)current.from.getContainingLocation()).getCenter();
		} else {
			target = Global.getSector().getHyperspace().createToken(current.from.getLocation().x, current.from.getLocation().y);
		}
		
		fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, target, 
						    current.daysMax - current.elapsed, getInSystemActionText(current));
	}
	
	@Override
	protected void addTravelAssignment(final RouteManager.RouteSegment current, boolean justSpawned) {
		if (justSpawned) {
			TravelState state = getTravelState(current);
			if (state == TravelState.LEAVING_SYSTEM) {
				float p = current.getLeaveProgress();
				JumpPointAPI jp = RouteLocationCalculator.findJumpPointToUse(fleet, current.from);
				
				RouteLocationCalculator.setLocation(fleet, p, 
						current.from, jp);
				
//				JumpPointAPI jp = Misc.findNearestJumpPointTo(current.from);
//				if (jp != null) {
//					Vector2f loc = Misc.interpolateVector(current.from.getLocation(),
//														  jp.getLocation(),
//														  p);
//					fleet.setLocation(loc.x, loc.y);
//				} else {
//					fleet.setLocation(current.from.getLocation().x, current.from.getLocation().y);
//				}
//				randomizeFleetLocation(p);
			}
			else if (state == TravelState.ENTERING_SYSTEM) {
				float p = current.getEnterProgress();
				JumpPointAPI jp = RouteLocationCalculator.findJumpPointToUse(fleet, current.to);
				RouteLocationCalculator.setLocation(fleet, p, 
													jp, current.to);
				
//				JumpPointAPI jp = Misc.findNearestJumpPointTo(current.to);
//				if (jp != null) {
//					Vector2f loc = Misc.interpolateVector(jp.getLocation(),
//														  current.to.getLocation(),
//														  p);
//					fleet.setLocation(loc.x, loc.y);
//				} else {
//					fleet.setLocation(current.to.getLocation().x, current.to.getLocation().y);
//				}
//				randomizeFleetLocation(p);
			}
			else if (state == TravelState.IN_SYSTEM) {
				float p = current.getTransitProgress();
				RouteLocationCalculator.setLocation(fleet, p, 
													current.from, current.to);
//				Vector2f loc = Misc.interpolateVector(current.from.getLocation(),
//													  current.to.getLocation(),
//													  p);
//				fleet.setLocation(loc.x, loc.y);
//				randomizeFleetLocation(p);
			}
			else if (state == TravelState.IN_HYPER_TRANSIT) {
				float p = current.getTransitProgress();
				SectorEntityToken t1 = Global.getSector().getHyperspace().createToken(
															   current.from.getLocationInHyperspace().x, 
															   current.from.getLocationInHyperspace().y);
				SectorEntityToken t2 = Global.getSector().getHyperspace().createToken(
															   current.to.getLocationInHyperspace().x, 
						   									   current.to.getLocationInHyperspace().y);				
				RouteLocationCalculator.setLocation(fleet, p, t1, t2);
				
//				Vector2f loc = Misc.interpolateVector(current.getContainingLocationFrom().getLocation(),
//													  current.getContainingLocationTo().getLocation(),
//													  p);
//				fleet.setLocation(loc.x, loc.y);
//				randomizeFleetLocation(p);
			}
			
//			
//			Vector2f loc = route.getInterpolatedLocation();
//			Random random = new Random();
//			if (route.getSeed() != null) {
//				random = Misc.getRandom(route.getSeed(), 1);
//			}
//			loc = Misc.getPointWithinRadius(loc, 2000f, random);
//			fleet.setLocation(loc.x, loc.y);
		}
		
		fleet.addAssignment(FleetAssignment.DELIVER_MARINES, current.to, 10000f, getTravelActionText(current), 
				goNextScript(current));
		
//		if (current.isInSystem()) {
//			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, current.to, 10000f, getTravelActionText(current), 
//					goNextScript(current));
//		} else {
//			SectorEntityToken target = current.to;
////			if (current.to.getContainingLocation() instanceof StarSystemAPI) {
////				target = ((StarSystemAPI)current.to.getContainingLocation()).getCenter();
////			}
//			fleet.addAssignment(FleetAssignment.GO_TO_LOCATION, target, 10000f, getTravelActionText(current), 
//					goNextScript(current));
//		}
	}
	
}
