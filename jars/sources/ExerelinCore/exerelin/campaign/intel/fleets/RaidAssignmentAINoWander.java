package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.intel.invasion.InvActionStage;
import exerelin.utilities.StringHelper;
import org.lazywizard.lazylib.MathUtils;

// vanilla, but replaces some assignment types and hacks addLocalAssignment to keep fleets from wandering off
// see http://fractalsoftworks.com/forum/index.php?topic=5061.msg253229#msg253229
// Used for invasion and sat bomb fleets
public class RaidAssignmentAINoWander extends RaidAssignmentAI {
	
	protected transient IntervalUtil assistCheckInterval = new IntervalUtil(0.15f, 0.15f);
	protected OffensiveFleetIntel ofi;
	
	public RaidAssignmentAINoWander(OffensiveFleetIntel ofi, CampaignFleetAPI fleet, RouteData route, FleetActionDelegate delegate) {
		super(fleet, route, delegate);
		this.ofi = ofi;
	}
	
	protected Object readResolve() {
		if (assistCheckInterval == null) {
			assistCheckInterval = new IntervalUtil(0.15f, 0.15f);
		}
		return this;
	}
	
	protected boolean allowWander() {
		if (delegate instanceof WaitStage) {
			return ((WaitStage)delegate).isAggressive();
		}
		return false;
	}
	
	@Override
	public void advance(float amount) {
		super.advance(amount);
		
		assistCheckInterval.advance(amount);
		if (assistCheckInterval.intervalElapsed()) {
			//fleet.addFloatingText("Checking assist", fleet.getFaction().getBaseUIColor(), 2);
			CampaignFleetAPI nearbyToHelp = getNearbyNeedsAssist();
			if (nearbyToHelp != null) {
				fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, nearbyToHelp, 1, 
						StringHelper.getFleetAssignmentString("assisting", nearbyToHelp.getName().toLowerCase()), 
						null);
				setTempAssignment();
				return;
			}
		}
	}
	
	@Override
	protected void addLocalAssignment(final RouteManager.RouteSegment current, boolean justSpawned) {
		if (allowWander()) {
			super.addLocalAssignment(current, justSpawned);
			return;
		}
		
		if (justSpawned) {
			float progress = current.getProgress();
			RouteLocationCalculator.setLocation(fleet, progress, 
									current.from, current.getDestination());
		}
		
		// used for invasion fleets hanging around market after capturing it
		if (current.custom != null && current.custom.equals(WaitStage.ROUTE_CUSTOM_NO_WANDER)) {
			
			fleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, current.from, 
					current.daysMax - current.elapsed, getInSystemActionText(current),
					goNextScript(current));
			return;
		}
		
		// This occurs when e.g. dropping into a system and causing the raid fleets to materialize around the target
		// so needs to be non-passive to keep fleets from sitting around doing nothing
		if (current.from != null && current.to == null && !current.isFromSystemCenter()) {
			fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, current.from, 
					current.daysMax - current.elapsed, getInSystemActionText(current),
					goNextScript(current));		
			return;
		}
		
		SectorEntityToken target = null;
		if (current.from.getContainingLocation() instanceof StarSystemAPI) 
		{
			CampaignFleetAPI nearbyToHelp = getNearbyNeedsAssist();
			if (nearbyToHelp != null) {
				fleet.addAssignmentAtStart(FleetAssignment.INTERCEPT, nearbyToHelp, 1, 
						StringHelper.getFleetAssignmentString("assisting", nearbyToHelp.getName().toLowerCase()), 
						null);
				setTempAssignment();
				return;
			}
			
			// force the fleet to go straight to target instead of pissing around system
			if (current.custom instanceof MarketAPI && !fleet.getMemoryWithoutUpdate().getBoolean(OffensiveFleetIntel.MEM_KEY_ACTION_DONE))
			{
				final MarketAPI market = ((MarketAPI)current.custom);
				target = market.getPrimaryEntity();
				
				boolean giveOrder = true;
				
				FleetAssignmentDataAPI currAssign = fleet.getCurrentAssignment();
				if (MathUtils.getDistance(fleet, target) <= 300) {
					//fleet.addFloatingText("Close enough, checking colony action", fleet.getFaction().getBaseUIColor(), 2);
					checkColonyAction();
					giveOrder = false;
				}
				if (currAssign != null && market.getConnectedEntities().contains(currAssign.getTarget())) 
				{
					giveOrder = false;
				}
				else if (currAssign != null && currAssign.getCustom() == TEMP_ASSIGNMENT) 
				{
					giveOrder = false;
				}
				
				String prev = currAssign != null ? currAssign.getAssignment().toString() : "none";
				
				if (giveOrder) {
					//fleet.addFloatingText("Forcing deliver assignment, previously " + prev, fleet.getFaction().getBaseUIColor(), 2);
					fleet.clearAssignments();
					fleet.addAssignment(FleetAssignment.DELIVER_MARINES, target, 
								0.5f, getTravelActionText(current));
					setTempAssignment();
					return;
				}
			}
			target = ((StarSystemAPI)current.from.getContainingLocation()).getCenter();
		} else {
			target = Global.getSector().getHyperspace().createToken(current.from.getLocation().x, current.from.getLocation().y);
		}
		
		fleet.addAssignment(FleetAssignment.PATROL_SYSTEM, target, 
						    current.daysMax - current.elapsed, getInSystemActionText(current));
	}
	
	protected void setTempAssignment() {
		FleetAssignmentDataAPI currAssign = fleet.getCurrentAssignment();
		if (currAssign != null) {
			currAssign.setCustom(TEMP_ASSIGNMENT);
		}
	}
	
	protected CampaignFleetAPI getNearbyNeedsAssist() {
		if (fleet.getBattle() != null) return null;	// already in our own battle
		if (fleet.getAI().getCurrentAssignmentType() == FleetAssignment.INTERCEPT) return null;	// already assisting?
		for (CampaignFleetAPI otherFleet : Misc.getNearbyFleets(fleet, 500)) 
		{
			if (otherFleet == fleet) continue;
			if (otherFleet.getFaction() != fleet.getFaction()) continue;
			BattleAPI otherBattle = otherFleet.getBattle();
			if (otherBattle != null) {
				boolean aiWantAssist = fleet.getAI().wantsToJoin(otherBattle, otherBattle.isPlayerInvolved());
				if (!aiWantAssist) {
					//fleet.addFloatingText("No assistance wanted", fleet.getFaction().getBaseUIColor(), 1);
					continue;
				}
				
				return otherFleet;
			}
		}
		return null;
	}
	
	@Override
	protected void addTravelAssignment(final RouteManager.RouteSegment current, boolean justSpawned) {
		if (allowWander()) {
			super.addTravelAssignment(current, justSpawned);
			return;
		}
		
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
	
	// Changes from vanilla: Simplified target check, doesn't stop the action if a nearby friendly fleet is closer to target
	@Override
	protected void checkColonyAction() {
		if (!canTakeAction()) return;
		
		MarketAPI closest = null;
		if (delegate instanceof InvActionStage) {
			closest = ((InvActionStage)delegate).getTarget();
		}
		
		if (closest == null) return;
		float minDist = Misc.getDistance(fleet, closest.getPrimaryEntity());
		if (minDist > 2000f) return;
		
		for (CampaignFleetAPI other : Misc.getNearbyFleets(closest.getPrimaryEntity(), 2000f)) {
			if (other == fleet) continue;
			
			if (other.isHostileTo(fleet)) {
				SectorEntityToken.VisibilityLevel vis = other.getVisibilityLevelTo(fleet);
				boolean canSee = vis == SectorEntityToken.VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS || vis == SectorEntityToken.VisibilityLevel.COMPOSITION_DETAILS;
				if (!canSee && other.getFaction() != fleet.getFaction()) continue;
				
				if (other.getAI() instanceof ModularFleetAIAPI) {
					ModularFleetAIAPI ai = (ModularFleetAIAPI) other.getAI();
					if (ai.isFleeing()) continue;
					if (ai.isMaintainingContact()) continue;
					
					if (ai.getTacticalModule().getTarget() == fleet) return;
					
					MemoryAPI mem = other.getMemoryWithoutUpdate();
					boolean smuggler = mem.getBoolean(MemFlags.MEMORY_KEY_SMUGGLER);
					boolean trader = mem.getBoolean(MemFlags.MEMORY_KEY_TRADE_FLEET);
					if (smuggler || trader) continue;
				}
				if (other.getFleetPoints() > fleet.getFleetPoints() * 0.25f || other.isStationMode()) {
					
					return;
				}
			}
		}
		
		giveRaidOrder(closest);
	}
	
	@Override
	protected void giveRaidOrder(MarketAPI target) {
		if (target.getFaction() == fleet.getFaction() && (ofi == null || ofi.abortIfNonHostile))
			return;
		super.giveRaidOrder(target);
	}
	
	@Override
	protected void addStartingAssignment(RouteManager.RouteSegment current, boolean justSpawned) 
	{
		//fleet.addFloatingText("Executing starting assignment: " + justSpawned, fleet.getFaction().getBaseUIColor(), 2);
		super.addStartingAssignment(current, justSpawned);
	}
}
