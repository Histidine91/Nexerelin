package exerelin.campaign.intel.defensefleet;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidAssignmentAI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;


public class DefenseAssignmentAI extends RaidAssignmentAI {
	
	public static final String BUSY_REASON = "nex_busy_defense";
	public static Logger log = Global.getLogger(DefenseAssignmentAI.class);
	
	protected DefenseFleetIntel intel;
	
	public static void printDebug(String str)
	{
		Global.getSector().getCampaignUI().addMessage(str);
		log.info(str);
	}
	
	public DefenseAssignmentAI(DefenseFleetIntel intel, CampaignFleetAPI fleet, RouteData route, FleetActionDelegate delegate) {
		super(fleet, route, delegate);
		this.intel = intel;
	}
	
	// vanilla, but replaces some assignment types to keep fleets from wandering off
	// see http://fractalsoftworks.com/forum/index.php?topic=5061.msg253229#msg253229
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
				/*
				float p = current.getTransitProgress();
				RouteLocationCalculator.setLocation(fleet, p, 
													current.from, current.to);
//				Vector2f loc = Misc.interpolateVector(current.from.getLocation(),
//													  current.to.getLocation(),
//													  p);
//				fleet.setLocation(loc.x, loc.y);
//				randomizeFleetLocation(p);
				*/
				return;	// don't do random stuff in system
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
		
		fleet.addAssignment(FleetAssignment.DELIVER_CREW, current.to, 10000f, getTravelActionText(current), 
				goNextScript(current));
	}
	
	// don't interrupt fleet if we're already doing other stuff
	@Override
	protected boolean canTakeAction() {
		if (!super.canTakeAction())
			return false;
		
		FleetAssignmentDataAPI currAssign = fleet.getAI().getCurrentAssignment();
		
		if (currAssign != null) {
			FleetAssignment ass = currAssign.getAssignment();
			if (ass == FleetAssignment.DEFEND_LOCATION 
					|| ass == FleetAssignment.GO_TO_LOCATION
					|| ass == FleetAssignment.DELIVER_CREW
					|| ass == FleetAssignment.ORBIT_AGGRESSIVE)
				return false;
		}
		
		return true;
	}
	
	@Override
	protected void checkColonyAction() {
		if (!canTakeAction()) return;
		
		MarketAPI best = null;
		float bestScore = 0;
		
		if (intel.singleTarget) {
			best = intel.getTarget();
		}
		else	// TODO: distribute among markets based on value and density of existing defenses, not just nearness
		{
			WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
			for (MarketAPI market : Misc.getMarketsInLocation(fleet.getContainingLocation())) 
			{
				if (market.getFaction().isHostileTo(fleet.getFaction())) continue;

				float dist = Misc.getDistance(fleet, market.getPrimaryEntity());
				if (dist < 5000) dist = 5000;
				float distMult = 20000/dist;
				
				float value = NexUtilsMarket.getMarketIndustryValue(market) * market.getSize();
				
				//float existingDef = Misc.getNearbyFleets(market.getPrimaryEntity(), 1000);
				
				float score = value * distMult;
				
				picker.add(market, score);
			}
			best = picker.pick();
		}
		
		if (best == null) return;
		//printDebug("Taking action towards market " + closest.getName());
		giveRaidOrder(best);
	}
	
	@Override
	protected void giveRaidOrder(final MarketAPI target) {		
		//printDebug(fleet.getNameWithFaction() + " received raid order");
		
		float busyTime = 7;
		
		String name = target.getName();
		String capText = StringHelper.getFleetAssignmentString("defending", name);
		String moveText = StringHelper.getFleetAssignmentString("movingToDefend", name);
		if (delegate != null) {
			String s = delegate.getRaidApproachText(fleet, target);
			if (s != null) moveText = s;
			
			s = delegate.getRaidActionText(fleet, target);
			if (s != null) capText = s;
		}
		
		fleet.addAssignmentAtStart(FleetAssignment.DEFEND_LOCATION, target.getPrimaryEntity(), busyTime, capText, null);
		
		float dist = Misc.getDistance(target.getPrimaryEntity(), fleet);
		//if (dist > fleet.getRadius() + target.getPrimaryEntity().getRadius() + 300f) {
		if (dist > fleet.getRadius() + target.getPrimaryEntity().getRadius()) {
			fleet.addAssignmentAtStart(FleetAssignment.DELIVER_CREW, target.getPrimaryEntity(), 3f, moveText, null);
			busyTime += 3;
		}
		
		Misc.setFlagWithReason(fleet.getMemoryWithoutUpdate(), 
								MemFlags.FLEET_BUSY, BUSY_REASON, true, busyTime);
	}
}
