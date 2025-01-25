package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.group.*;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionIntel;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.PlayerInSystemTracker;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.raid.BaseStrikeIntel;
import exerelin.campaign.intel.raid.NexRaidIntel;
import exerelin.campaign.intel.satbomb.SatBombIntel;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

/**
 * Strategic level AI for special forces fleets. Handles perhaps everything to do with the managed route.
 */
public class SpecialForcesRouteAI {
	
	public static Logger log = Global.getLogger(SpecialForcesRouteAI.class);

	public static final boolean FGI_ENABLED = true;
	public static final float MAX_RAID_ETA_TO_CARE = 60;
	// only defend against player if they're at least this strong
	public static final float MIN_PLAYER_STR_TO_DEFEND = 300;
	public static final float PLAYER_STR_DIVISOR = 400;
	// don't defend against player attacking a location more than this many light-years away
	public static final float MAX_PLAYER_DISTANCE_TO_DEFEND = 10;
	public static final float MAX_DISTANCE_FOR_TASK = 250;	// in-system distance to target entity
	
	// can't be a plain Object because when loaded the one in segment will no longer equal the static value
	public static final String ROUTE_IDLE_SEGMENT = "SF_idleSeg";
	public static final String ROUTE_PURSUIT_SEGMENT = "SF_pursueSeg";
	
	protected SpecialForcesIntel sf;
	@Getter protected SpecialForcesTask currentTask;
	
	protected int idleCount = 0;
	
	protected IntervalUtil recheckTaskInterval = new IntervalUtil(3, 5);
	
	public SpecialForcesRouteAI(SpecialForcesIntel sf) {
		this.sf = sf;
	}
	
	public TaskType getCurrentTaskType() {
		if (currentTask != null) return currentTask.type;
		return null;
	}
	
	protected List<IntelInfoPlugin> getActiveEvents() {
		List<IntelInfoPlugin> raids = new ArrayList<>();
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel()) 
		{
			if (intel.isEnding() || intel.isEnded()) continue;

			if (intel instanceof RaidIntel) {

			}
			else if (intel instanceof FleetGroupIntel) {

			}
			else {
				continue;
			}

			
			raids.add(intel);
		}
		return raids;
	}
	
	// runcode exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.debugMercy();
	public static void debugMercy() {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(OffensiveFleetIntel.class)) {
			OffensiveFleetIntel raid = (OffensiveFleetIntel)intel;
			log.info(raid.getName() + ": " + shouldShowPlayerMercy(raid));
		}
	}
	
	/**
	 * Cut player some slack early on: don't join raids against size 3-4 markets.
	 * (except Starfarer mode)
	 * @param event
	 * @return
	 */
	public static boolean shouldShowPlayerMercy(IntelInfoPlugin event) {
		if (SectorManager.getManager().isHardMode()) return false;
		
		FactionAPI player = Global.getSector().getPlayerFaction();
		try {
			if (event instanceof OffensiveFleetIntel) {
				OffensiveFleetIntel ofi = (OffensiveFleetIntel) event;
				MarketAPI target = ofi.getTarget();
				if (target != null && target.getFaction() == player
						&& target.getSize() < 5)
					return true;
			} else if (event instanceof HegemonyInspectionIntel) {
				HegemonyInspectionIntel insp = (HegemonyInspectionIntel) event;
				if (insp.getTarget().getSize() < 5)
					return true;
			} else if (event instanceof BlockadeFGI) {
				BlockadeFGI block = (BlockadeFGI) event;
				for (MarketAPI target : Global.getSector().getEconomy().getMarkets(block.getBlockadeParams().where)) {
					if (!target.getFaction().isPlayerFaction()) continue;
					if (target.getSize() >= 5)
						return false;
				}
				return true;
			} else if (event instanceof GenericRaidFGI) {
				GenericRaidFGI raid = (GenericRaidFGI) event;
				for (MarketAPI target : Global.getSector().getEconomy().getMarkets(raid.getParams().raidParams.where)) {
					if (!target.getFaction().isPlayerFaction()) continue;
					if (target.getSize() >= 5)
						return false;
				}
				return true;
			} else if (event instanceof RaidIntel) {
				StarSystemAPI sys = ((RaidIntel) event).getSystem();
				if (sys != null) {
					MarketAPI owner = NexUtilsFaction.getSystemOwningMarket(sys);
					if (owner != null && owner.getFaction() == player && owner.getSize() < 5)
						return true;
				}
			}
		} catch (Exception ex) {
			log.error("Error in show player mercy check", ex);
		}
		return false;
	}
	
	protected boolean isAssistableFriendlyEvent(IntelInfoPlugin event) {
		FactionAPI faction;
		if (event instanceof RaidIntel) {
			faction = ((RaidIntel)event).getFaction();
		} else if (FGI_ENABLED && event instanceof FleetGroupIntel) {
			faction = ((FleetGroupIntel)event).getFaction();
		} else return false;

		if (!AllianceManager.areFactionsAllied(faction.getId(), sf.faction.getId()))
			return false;

		if (event instanceof OffensiveFleetIntel) {
			if (event instanceof BaseStrikeIntel) return false;
			if (event instanceof ColonyExpeditionIntel) return false;
			if (((OffensiveFleetIntel)event).getOutcome() != null) return false;
		}
		else {	// probably a pirate raid
			if (event instanceof PunitiveExpeditionIntel) return false;
		}
		if (shouldShowPlayerMercy(event)) return false;
		
		return true;
	}
	
	/**
	 * Gets active raid-type events by us against hostile factions.
	 * @return
	 */
	public List<IntelInfoPlugin> getActiveEventsFriendly() {
		List<IntelInfoPlugin> events = getActiveEvents();
		List<IntelInfoPlugin> eventsFiltered = new ArrayList<>();
		for (IntelInfoPlugin event : events) {
			if (!isAssistableFriendlyEvent(event))
				continue;			
			
			eventsFiltered.add(event);
		}
		return eventsFiltered;
	}
	
	protected boolean hasMarketInSystem(StarSystemAPI system, FactionAPI faction) 
	{
		if (system == null) return false;
		for (MarketAPI market : Misc.getMarketsInLocation(system))
		{
			if (market.getFaction() == faction)
				return true;
		}
		return false;
	}
	
	protected boolean isDefendableEnemyEvent(IntelInfoPlugin event) {
		FactionAPI faction;
		StarSystemAPI sys = null;
		if (event instanceof RaidIntel) {
			faction = ((RaidIntel)event).getFaction();
			sys = ((RaidIntel)event).getSystem();
		} else if (FGI_ENABLED && event instanceof FleetGroupIntel) {
			faction = ((FleetGroupIntel)event).getFaction();
		} else return false;

		if (!faction.isHostileTo(sf.faction))
			return false;
		
		if (event instanceof OffensiveFleetIntel) {
			//sf.debugMsg("Testing raid intel " + raid.getName(), false);
			if (event instanceof BaseStrikeIntel) return false;
			if (event instanceof ColonyExpeditionIntel) return false;
			
			// Nex raid, valid if one of the targeted markets is ours
			if (event instanceof NexRaidIntel) {
				if (hasMarketInSystem(sys, sf.faction))
					return true;
			}
			
			OffensiveFleetIntel ofi = (OffensiveFleetIntel)event;
			if (ofi.getOutcome() != null) {
				//sf.debugMsg("  Outcome already happened", true);
				return false;
			}
			
			// Only count the raid if we are the target
			FactionAPI targetFaction = ofi.getTarget().getFaction();
			if (targetFaction != sf.faction){
				//sf.debugMsg("  Wrong faction: " + targetFaction.getId() + ", " + sf.faction.getId(), true);
				return false;
			}
			return true;
		}
		else {	// probably a pirate raid
			// Only count the raid if we have a market in target system
			if (hasMarketInSystem(sys, sf.faction))
				return true;			
		}
		return false;
	}
	
	/**
	 * Gets active raid-type events by hostile factions against us.
	 * @return
	 */
	public List<IntelInfoPlugin> getActiveRaidsHostile() {
		List<IntelInfoPlugin> raids = getActiveEvents();
		List<IntelInfoPlugin> raidsFiltered = new ArrayList<>();
		for (IntelInfoPlugin raid : raids) {
			if (!isDefendableEnemyEvent(raid))
				continue;		
			
			raidsFiltered.add(raid);
		}
		return raidsFiltered;
	}
	
	public void resetRoute(RouteManager.RouteData route) {
		CampaignFleetAPI fleet = route.getActiveFleet();
		if (fleet != null) {
			fleet.clearAssignments();
		}
		route.getSegments().clear();
		route.setCurrent(null);
	}
	
	protected void addIdleSegment(RouteData route, SectorEntityToken destination) {
		RouteManager.RouteSegment idle = new RouteManager.RouteSegment(365, destination);
		idle.custom = ROUTE_IDLE_SEGMENT;
		route.addSegment(idle);
	}
	
	/**
	 * Get a {@code SectorEntityToken} for the task's route segment to originate from.
	 * @return
	 */
	protected SectorEntityToken getRouteFrom() {
		SectorEntityToken from;
		CampaignFleetAPI fleet = sf.route.getActiveFleet();
		if (fleet != null) {
			from = fleet.getContainingLocation().createToken(fleet.getLocation());
		}
		else from = Global.getSector().getHyperspace().createToken(sf.route.getInterpolatedHyperLocation());
		
		return from;
	}
	
	public void assignTask(SpecialForcesTask task) {
		assignTask(task, false);
	}
	
	/**
	 * Set task as current, updating routes and the like.
	 * @param task
	 */
	public void assignTask(SpecialForcesTask task, boolean silent) 
	{
		if (task.type == TaskType.IDLE && currentTask != null && currentTask.type == TaskType.IDLE)
			return;
		
		RouteData route = sf.route;
		currentTask = task;
		sf.debugMsg("Assigning task of type " + task.type + "; priority " 
				+ String.format("%.1f", task.priority), false);
		if (task.getMarket() != null) 
			sf.debugMsg("  Target: " + task.getMarket().getName(), true);
		else if (task.system != null)
			sf.debugMsg("  Target: " + task.system.getNameWithLowercaseType(), true);
		
		CampaignFleetAPI fleet = route.getActiveFleet();	
		SectorEntityToken from = getRouteFrom();
		
		resetRoute(route);
		
		// get time for assignment, estimate travel time needed
		float travelTime = 0;
		
		// setup a travel segment and an action segment
		RouteManager.RouteSegment actionSeg = null, travelSeg = null;
		SectorEntityToken destination = null;
		switch (task.type) {
			case REBUILD:
			case DEFEND_RAID:
			case DEFEND_VS_PLAYER:
			case ASSIST_RAID:
			case PATROL:
			case RAID:
			case COUNTER_GROUND_BATTLE:
			case WAIT_ORBIT:
			case ASSEMBLE:
				if (task.getEntity() != null) destination = task.getEntity();
				else
					destination = task.getMarket() == null ? task.system.getCenter() : task.getMarket().getPrimaryEntity();
				travelTime = RouteLocationCalculator.getTravelDays(from, destination);
				sf.debugMsg("Travel time: " + travelTime + "; from " + from.getLocation(), false);
				travelSeg = new RouteManager.RouteSegment(travelTime, from, destination);
				actionSeg = new RouteManager.RouteSegment(task.time, destination);
				break;
			case HUNT_PLAYER:
				// TODO
			case FOLLOW_PLAYER:
				destination = task.getEntity();
				travelSeg = new RouteManager.RouteSegment(99999 + 1, from, destination);
				travelSeg.custom = ROUTE_PURSUIT_SEGMENT;
				break;
			case IDLE:
				// go to nearest star system and just bum around it for a bit
				StarSystemAPI system;
				if (fleet != null) {
					system = route.getActiveFleet().getStarSystem();
					if (system == null)
					{
						system = Misc.getNearestStarSystem(from);
					}
				}
				else {
					system = Misc.getNearestStarSystem(from);
				}
				if (system == null) break;
				destination = system.getCenter();
				
				travelTime = RouteLocationCalculator.getTravelDays(from, destination);
				travelSeg = new RouteManager.RouteSegment(task.time + travelTime, destination);
				actionSeg = new RouteManager.RouteSegment(task.time, destination);
				
				idleCount++;
				if (idleCount > 5) {
					sf.goRogueOrExpire();
				}
				
				break;
		}
		
		if (task.type != TaskType.IDLE) {
			idleCount = 0;
		}
		
		// if joining a raid, try to make sure we arrive at the same time as them
		// instead of showing up super early and potentially getting whacked
		if (task.type == TaskType.ASSIST_RAID) {
			float delay = getETA(task.raid) - travelTime;
			if (delay > 0) {
				RouteManager.RouteSegment wait = new RouteManager.RouteSegment(delay, from);
				wait.custom = SpecialForcesAssignmentAI.CUSTOM_DELAY_BEFORE_RAID;
				route.addSegment(wait);
			}
		}
		
		// don't have a travel segment if fleet is already in target system
		//if (destination != null && fleet != null && fleet.getContainingLocation() == destination.getContainingLocation())
		//	travelSeg = null;
		
		if (task.type == TaskType.ASSEMBLE)
			travelSeg = null;
		
		if (travelSeg != null) {
			route.addSegment(travelSeg);
		}
		if (actionSeg != null) {
			route.addSegment(actionSeg);
		}
		
		// placeholder to keep the route from expiring
		addIdleSegment(route, destination);
		
		if (fleet != null) {
			fleet.clearAssignments();
		}
		
		if (!silent) sf.sendUpdateIfPlayerHasIntel(SpecialForcesIntel.NEW_ORDERS_UPDATE, false, false);
	}
	
	public SpecialForcesTask generateRaidDefenseTask(IntelInfoPlugin event, float priority) {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.DEFEND_RAID, priority);
		task.raid = event;
		task.system = getSystemForEvent(event);
		task.setMarket(getMarketForEvent(event));

		task.time = 30;
		if (event instanceof BlockadeFGI) task.time *= 3;
		task.time += getETA(event);

		return task;
	}
	
	public SpecialForcesTask generateRaidAssistTask(IntelInfoPlugin event, float priority) {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.ASSIST_RAID, priority);
		task.raid = event;
		task.system = getSystemForEvent(event);
		task.setMarket(getMarketForEvent(event));
		task.time = 30;	// don't add ETA here, apply it as a delay instead
		if (event instanceof BlockadeFGI) task.time *= 3;
		return task;
	}
	
	public SpecialForcesTask generatePatrolTask(MarketAPI market, float priority) {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.PATROL, priority);
		task.system = market.getStarSystem();
		task.setMarket(market);
		return task;
	}
	
	public SpecialForcesTask generateCounterGroundBattleTask(MarketAPI market, float priority) {
		SpecialForcesTask task = generatePatrolTask(market, priority);
		task.type = TaskType.COUNTER_GROUND_BATTLE;
		return task;
	}
	
	public SpecialForcesTask generateDefendVsPlayerTask(LocationAPI loc, float priority) {
		SpecialForcesTask task = new SpecialForcesTask(TaskType.DEFEND_VS_PLAYER, priority);
		
		task.setMarket(getLargestMarketInSystem(loc, sf.faction));
		task.system = task.getMarket().getStarSystem();
		
		return task;
	}
	
	public static MarketAPI getLargestMarketInSystem(LocationAPI loc, FactionAPI faction) {
		List<MarketAPI> all = Global.getSector().getEconomy().getMarkets(loc);
		if (all.isEmpty()) return null;
		MarketAPI largest = all.get(0);
		int largestSize = 0;
		
		for (MarketAPI market : all)
		{
			if (market.getFaction() != faction)
				continue;
			int size = market.getSize();
			if (size > largestSize) {
				largest = market;
				largestSize = size;
			}
		}
		return largest;
	}

	public SpecialForcesTask pickTask(boolean priorityDefenseOnly) {
		return pickTask(priorityDefenseOnly, false);
	}

	/**
	 * Picks a task for the task force to do.
	 * @param priorityDefenseOnly Only check for any urgent defense tasks that 
	 * should take priority over what we're currently doing.
	 * @param isManualOrder Was 'pick task' instruction manually given by player? Only relevant for player task groups.
	 * @return 
	 */
	public SpecialForcesTask pickTask(boolean priorityDefenseOnly, boolean isManualOrder)
	{
		sf.debugMsg("Picking task for " + sf.getFleetName(), false);
		
		boolean isBusy = currentTask != null && currentTask.type.isBusyTask();
		
		// check for priority counter-ground-battle tasks
		if (getCurrentTaskType() != TaskType.COUNTER_GROUND_BATTLE) {
			for (GroundBattleIntel gbi : GroundBattleIntel.getOngoing()) {
				
				MarketAPI market = gbi.getMarket();
				if (!AllianceManager.areFactionsAllied(market.getFaction().getId(), sf.getFaction().getId()))
					continue;
				if (!sf.getFaction().isHostileTo(gbi.getSide(true).getFaction()))
					continue;
				
				float priority = this.getCounterGroundBattlePriority(market);
				float toBeat = currentTask != null ? currentTask.priority : 0;
				if (isBusy) toBeat *= 2;
				//sf.debugMsg("Priority defense task comparison: " + priority + " / " + toBeat, false);

				if (toBeat < priority) {
					SpecialForcesTask task = generateCounterGroundBattleTask(market, priority);
					return task;
				}
			}
		}
		
		// check for priority defense against player operating in one of our systems
		if (getCurrentTaskType() != TaskType.DEFEND_VS_PLAYER) {
			LocationAPI loc = Global.getSector().getPlayerFleet().getContainingLocation();
			float priority = getDefendVsPlayerPriority(loc);
			float toBeat = currentTask != null ? currentTask.priority : 0;
			if (isBusy) toBeat *= 2;
			//sf.debugMsg("Priority defense task comparison: " + priority + " / " + toBeat, false);
			
			if (toBeat < priority) {
				SpecialForcesTask task = generateDefendVsPlayerTask(loc, priority);
				return task;
			}
		}
		
		// check for priority raid defense missions
		List<Pair<IntelInfoPlugin, Float>> hostileRaids = new ArrayList<>();
		for (IntelInfoPlugin event : getActiveRaidsHostile()) {
			if (getETA(event) > MAX_RAID_ETA_TO_CARE) continue;
			hostileRaids.add(new Pair<>(event, getEventDefendPriority(event)));
		}
		//sf.debugMsg("Hostile raid count: " + hostileRaids.size(), false);
		
		Pair<IntelInfoPlugin, Float> priorityDefense = pickPriorityDefendTask(hostileRaids, isBusy);
		if (priorityDefense != null) {
			SpecialForcesTask task = generateRaidDefenseTask(priorityDefense.one, priorityDefense.two);
			return task;
		}
				
		// no high priority defense, look for another task
		if (priorityDefenseOnly)
			return null;
		
		WeightedRandomPicker<SpecialForcesTask> picker = new WeightedRandomPicker<>();
		
		// Defend vs. raid
		for (Pair<IntelInfoPlugin, Float> raid : hostileRaids) {
			picker.add(generateRaidDefenseTask(raid.one, raid.two), raid.two);
		}
		
		// Assist raid
		for (IntelInfoPlugin raid : getActiveEventsFriendly()) {
			if (getETA(raid) > MAX_RAID_ETA_TO_CARE) continue;
			float priority = getEventAttackPriority(raid);
			picker.add(generateRaidAssistTask(raid, priority), priority);
		}
		
		// Patrol
		List<MarketAPI> alliedMarkets = getAlliedMarkets();
		int numMarkets = alliedMarkets.size();
		for (MarketAPI market : alliedMarkets) {
			float priority = getPatrolPriority(market);
			priority /= numMarkets;
			picker.add(generatePatrolTask(market, priority), priority);
		}
		
		// idle
		if (picker.isEmpty()) {
			SpecialForcesTask task = new SpecialForcesTask(TaskType.IDLE, 0);
			task.time = 15;
			return task;
		}
		
		return picker.pick();
	}
	
	public List<MarketAPI> getAlliedMarkets() {
		String factionId = sf.faction.getId();
		List<MarketAPI> alliedMarkets;
		if (AllianceManager.getFactionAlliance(factionId) != null) {
			alliedMarkets = AllianceManager.getFactionAlliance(factionId).getAllianceMarkets();
		}
		else
			alliedMarkets = NexUtilsFaction.getFactionMarkets(factionId);
		
		return alliedMarkets;
	}

	protected float getETA(IntelInfoPlugin event) {
		if (event instanceof RaidIntel) {
			return ((RaidIntel)event).getETA();
		}
		if (event instanceof GenericRaidFGI) {
			FleetGroupIntel fgi = ((GenericRaidFGI)event);
			return fgi.getETAUntil(GenericRaidFGI.PAYLOAD_ACTION);
		}
		return 0;
	}

	protected StarSystemAPI getSystemForEvent(IntelInfoPlugin event) {
		if (event instanceof RaidIntel) {
			return ((RaidIntel)event).getSystem();
		}
		else if (event instanceof BlockadeFGI) {
			BlockadeFGI fgi = ((BlockadeFGI)event);
			return fgi.getBlockadeParams().where;
		}
		else if (event instanceof GenericRaidFGI) {
			GenericRaidFGI fgi = ((GenericRaidFGI)event);
			return fgi.getParams().raidParams.where;
		}
		return null;
	}

	protected MarketAPI getMarketForEvent(IntelInfoPlugin event) {
		if (event instanceof OffensiveFleetIntel) {
			return ((OffensiveFleetIntel)event).getTarget();
		}
		else if (event instanceof BlockadeFGI) {
			BlockadeFGI fgi = ((BlockadeFGI)event);
			return fgi.getBlockadeParams().specificMarket;
		}
		else if (event instanceof GenericRaidFGI) {
			GenericRaidFGI fgi = ((GenericRaidFGI)event);
			if (fgi.getParams().raidParams.allowedTargets.isEmpty()) return null;
			return fgi.getParams().raidParams.allowedTargets.get(0);
		}
		return null;
	}
	
	/**
	 * Picks the highest-priority raid for a priority defense assignment, if any exceed the needed priority threshold.
	 * If no raid is picked, the task force may still randomly pick a raid to defend against.
	 * @param raids
	 * @param isBusy
	 * @return
	 */
	protected Pair<IntelInfoPlugin, Float> pickPriorityDefendTask(List<Pair<IntelInfoPlugin, Float>> raids, boolean isBusy)
	{
		Pair<IntelInfoPlugin, Float> highest = null;
		float highestScore = currentTask != null ? currentTask.priority : 0;
		if (isBusy) highestScore *= 2;
		float minimum = getPriorityNeededForUrgentDefense(isBusy);
		
		for (Pair<IntelInfoPlugin, Float> entry : raids) {
			float score = entry.two;
			if (score < minimum) continue;
			if (score > highestScore) {
				highestScore = score;
				highest = entry;
			}
		}
		
		return highest;
	}
	
	protected boolean wantNewTask() {
		TaskType taskType = currentTask == null ? TaskType.IDLE : currentTask.type;
				
		if (taskType == TaskType.REBUILD || taskType == TaskType.ASSEMBLE)
			return false;
		
		if (taskType == TaskType.IDLE) {
			return true;
		}
		// We were assigned to assist or defend against a raid, but it's already ended
		// or otherwise no longer applicable
		else if (currentTask.raid != null) {
			IntelInfoPlugin event = currentTask.raid;
			if (event.isEnding() || event.isEnded()) {
				return true;
			}
			else if (taskType == TaskType.ASSIST_RAID && !isAssistableFriendlyEvent(event))
			{
				return true;
			}
			else if (taskType == TaskType.DEFEND_RAID && !isDefendableEnemyEvent(event))
			{
				return true;
			}
		}
		else if (taskType == TaskType.COUNTER_GROUND_BATTLE
				&& GroundBattleIntel.getOngoing(currentTask.getMarket()) == null) 
		{
			return true;
		}
		// defending vs. player in system, but player already left
		else if (taskType == TaskType.DEFEND_VS_PLAYER 
				&& Global.getSector().getPlayerFleet().getContainingLocation() != currentTask.system) 
		{
			return true;
		}
		
		return false;
	}
	
	/**
	 * Check if we should be doing something else.
	 */
	public void updateTaskIfNeeded() 
	{
		if (sf.route.getActiveFleet() != null && sf.route.getActiveFleet().getBattle() != null)
			return;
		
		//sf.debugMsg("Checking " + sf.getFleetNameForDebugging() + " for task change", false);
		boolean wantNewTask = wantNewTask();
		
		// don't want a new task, but check if there's a defend task we should divert to
		if (!wantNewTask)
		{
			// don't reassign from player-issued tasks
			if (currentTask != null && currentTask.playerIssued)
				return;
			
			TaskType taskType = currentTask == null ? null : currentTask.type;
			
			if (taskType == TaskType.RAID 
				|| taskType == TaskType.ASSIST_RAID
				|| taskType == TaskType.DEFEND_VS_PLAYER
				|| taskType == TaskType.PATROL) 
			{
				SpecialForcesTask task = pickTask(true);
				if (task != null) {
					assignTask(task);
					return;
				}
			}
		}
		// want a new task
		else {
			SpecialForcesTask task = pickTask(false);
			if (task != null) {
				assignTask(task);
			}
		}
	}
	
	/**
	 * Are we actually close enough to the target entity to execute the ordered task?
	 * @return
	 */
	public boolean isCloseEnoughForTask() {
		CampaignFleetAPI fleet = sf.route.getActiveFleet();
		if (fleet == null) return true;
		
		SectorEntityToken target = currentTask.getMarket().getPrimaryEntity();
		return MathUtils.getDistance(fleet, target) < MAX_DISTANCE_FOR_TASK;
	}
	
	public void notifyRouteFinished() {
		sf.debugMsg("Route finished, looking for new task", false);
		
		if (currentTask == null) {
			SpecialForcesTask task = pickTask(false);
			if (task != null) assignTask(task);
			return;
		}
		
		if (currentTask.type == TaskType.REBUILD) 
		{
			sf.debugMsg("Attempting to rebuild fleet " + sf.getFleetName(), false);
			// Not close enough, wait a while longer
			if (!isCloseEnoughForTask()) {
				sf.debugMsg("Not close enough, retrying", true);
				sf.route.getSegments().clear();
				sf.route.setCurrent(null);
				sf.route.addSegment(new RouteManager.RouteSegment(currentTask.time * 0.5f, 
						currentTask.getMarket().getPrimaryEntity()));
				addIdleSegment(sf.route, currentTask.getMarket().getPrimaryEntity());
				return;
			}
			
			sf.executeRebuildOrder(currentTask.getMarket());
			// spend a few days orbiting the planet, to shake down the new members
			SpecialForcesTask task = new SpecialForcesTask(TaskType.ASSEMBLE, 100);
			task.setMarket(currentTask.getMarket());
			task.time = 2;
			assignTask(task);
			return;
		}
		
		currentTask = null;
		SpecialForcesTask task = pickTask(false);
		if (task != null) assignTask(task);
	}
	
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		recheckTaskInterval.advance(days);
		if (recheckTaskInterval.intervalElapsed()) {
			updateTaskIfNeeded();
		}
	}

	/**
	 * Get a list of the markets targeted by the provided {@code RaidIntel} or {@code FleetGroupIntel}, that we care about.
	 * @param event
	 * @param isDefender Is the special task group checking for raid defend jobs, or raid assist ones?
	 * @return
	 */
	public List<MarketAPI> getEventTargets(IntelInfoPlugin event, boolean isDefender) {
		List<MarketAPI> targets = new ArrayList<>();
		if (event instanceof OffensiveFleetIntel) {
			OffensiveFleetIntel ofi = (OffensiveFleetIntel)event;

			// raid: assign values for all allied markets contained in system
			if (event instanceof NexRaidIntel) {
				for (MarketAPI market : Global.getSector().getEconomy().getMarkets(ofi.getTarget().getContainingLocation()))
				{
					targets.add(market);
				}
			}
			// other OFI types, assume single target
			else {
				targets.add(ofi.getTarget());
			}
		} else if (event instanceof RaidIntel) {
			RaidIntel raid = ((RaidIntel)event);
			for (MarketAPI market : Global.getSector().getEconomy().getMarkets(raid.getSystem()))
			{
				targets.add(market);
			}
		} else if (event instanceof BlockadeFGI) {
			BlockadeFGI fgi = (BlockadeFGI)event;
			FGBlockadeAction.FGBlockadeParams params = fgi.getBlockadeParams();
			if (params.specificMarket != null) targets.add(params.specificMarket);
			else targets.addAll(Global.getSector().getEconomy().getMarkets(params.where));
		} else if (event instanceof GenericRaidFGI) {
			GenericRaidFGI fgi = (GenericRaidFGI)event;
			FGRaidAction.FGRaidParams params = fgi.getParams().raidParams;
			if (params.allowAnyHostileMarket) {
				targets.addAll(Global.getSector().getEconomy().getMarkets(params.where));
			} else {
				targets.addAll(params.allowedTargets);
			}
		}

		// filter out the markets we don't actually care about
		Iterator<MarketAPI> targetIter = targets.iterator();
		while (targetIter.hasNext()) {
			MarketAPI target = targetIter.next();
			boolean alliedToUs = AllianceManager.areFactionsAllied(target.getFactionId(), sf.faction.getId());
			boolean hostileToUs = target.getFaction().isHostileTo(sf.faction);

			if (isDefender && !alliedToUs) targetIter.remove();
			else if (!isDefender && !hostileToUs) targetIter.remove();
		}

		return targets;
	}

	/**
	 * Gets the priority level for defending against the specified raid-type event.
	 * @param event
	 * @return
	 */
	public float getEventDefendPriority(IntelInfoPlugin event) {
		List<MarketAPI> targets = getEventTargets(event, true);
		float mult = 1;

		if (event instanceof InvasionIntel)
			mult = 6;
		else if (event instanceof SatBombIntel)
			mult = 8;
		
		float priority = 0;
		for (MarketAPI market : targets) {
			priority += getEventDefendPriority(market);
		}
		priority *= mult;
		
		return priority;
	}
	
	/**
	 * Gets the priority level for assisting the specified raid-type event.
	 * @param event
	 * @return
	 */
	public float getEventAttackPriority(IntelInfoPlugin event) {
		List<MarketAPI> targets = getEventTargets(event, false);
		float mult = 1;

		if (event instanceof InvasionIntel)
			mult = 3;
		else if (event instanceof SatBombIntel)
			mult = 3;

		float priority = 0;
		for (MarketAPI market : targets) {
			priority += getEventAttackPriority(market);
		}
		priority *= mult;
		
		return priority;
	}
	
	/**
	 * Gets the priority for defending the specified market against a raid-type event.
	 * @param market
	 * @return
	 */
	public float getEventDefendPriority(MarketAPI market) {
		float priority = market.getSize() * market.getSize();
		if (NexUtilsMarket.hasHeavyIndustry(market))
			priority *= 4;
		
		sf.debugMsg("  Defending market " + market.getName() + " has priority " + String.format("%.1f", priority), true);
		return priority;
	}
	
	/**
	 * Gets the priority for attacking the specified market during a raid-type event.
	 * @param market
	 * @return
	 */
	public float getEventAttackPriority(MarketAPI market) {
		float priority = market.getSize() * market.getSize();
		if (NexUtilsMarket.hasHeavyIndustry(market))
			priority *= 3;
		
		sf.debugMsg("  Attacking market " + market.getName() + " has priority " + String.format("%.1f", priority), true);
		return priority;
	}
	
	/**
	 * Gets the priority for patrolling this market in the absence of raiding activity.
	 * @param market
	 * @return
	 */
	public float getPatrolPriority(MarketAPI market) {
		if (market.isHidden()) return -1;
		
		float priority = market.getSize() * market.getSize();
		if (NexUtilsMarket.hasHeavyIndustry(market))
			priority *= 4;
		if (market.getFaction() != sf.faction)	// lower priority for allies' markets
			priority *= 0.75f;
		
		// TODO: include term for distance
		
		// pirate, Stormhawk, etc. activity
		if (market.hasCondition(Conditions.PIRATE_ACTIVITY))
			priority *= 2;
		if (market.hasCondition("vayra_raider_activity"))
			priority *= 2;
		
		// high interest in patrolling locations where a hostile player is
		if (Global.getSector().getPlayerFaction().isHostileTo(sf.faction) 
				&& Global.getSector().getPlayerFleet().getContainingLocation() == market.getContainingLocation())
			priority *= 3;
		
		float def = InvasionFleetManager.estimatePatrolStrength(market, 0.5f) 
				+ InvasionFleetManager.estimateStationStrength(market);
		priority *= 100/def;
		sf.debugMsg("  Patrolling market " + market.getName() + " has priority " 
				+ String.format("%.1f", priority) + "; defensive rating " + String.format("%.1f", def), true);
		return priority;
	}
	
	/**
	 * Gets the priority for defending one of our star systems if player is present.
	 * @param loc
	 * @return
	 */
	public float getDefendVsPlayerPriority(LocationAPI loc) {
		if (loc.isHyperspace()) return 0;
		if (!sf.faction.isHostileTo(Factions.PLAYER))
			return -1;
		if (!PlayerInSystemTracker.hasFactionSeenPlayer(loc, sf.faction.getId()))
			return -1;
		
		float playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().getPlayerFleet());
		if (playerStr < MIN_PLAYER_STR_TO_DEFEND) return 0;
		
		// don't bother if too far away
		float distLY = Misc.getDistanceLY(loc.getLocation(), getRouteFrom().getLocationInHyperspace());
		if (distLY > MAX_PLAYER_DISTANCE_TO_DEFEND) return 0;
		
		float priority = 0;
		for (MarketAPI market : Global.getSector().getEconomy().getMarkets(loc))
		{
			if (market.getFaction() != sf.faction) continue;
			priority += getPatrolPriority(market);
		}
		priority *= playerStr / PLAYER_STR_DIVISOR;
		
		sf.debugMsg("  Defending " + loc.getName() + " from player has priority " 
				+ String.format("%.1f", priority), false);
		return priority;
	}
	
	public float getCounterGroundBattlePriority(MarketAPI market) {
		float distLY = Misc.getDistanceLY(market.getLocationInHyperspace(), getRouteFrom().getLocationInHyperspace());
		float travelTime = distLY/2;
		
		// estimate that battle will last (size ^ 2)/2 days
		float battleLength = (float)Math.pow(market.getSize(), 2)/2;
		if (battleLength < travelTime) return 0;
		
		return getEventDefendPriority(market) * 1.5f;
	}
	
	/**
	 * A raid must have at least this much defend priority for the special forces unit to be tasked
	 * to defend against it. Required priority will be increased if we already have another task.
	 * @param isBusy True if we're checking whether to cancel an existing busy-type assignment.
	 * @return
	 */
	public float getPriorityNeededForUrgentDefense(boolean isBusy) {
		int aggro = sf.faction.getDoctrine().getAggression();
		switch (aggro) {
			case 5:
				if (isBusy) return 9999999;	// higher means less likely to defend
				return 50;
			default:
				return 8 * aggro * (isBusy ? 2 : 1);
		}
	}
	
	public void addInitialTask() {
		float orbitDays = 1 + sf.startingFP * 0.02f * (0.75f + (float) Math.random() * 0.5f);
		
		SpecialForcesTask task = new SpecialForcesTask(TaskType.ASSEMBLE, 100);
		task.setMarket(sf.origin);
		task.time = orbitDays;
		assignTask(task);
	}
	
	public enum TaskType {
		RAID(true, false),
		PATROL(false, true),
		ASSIST_RAID(true, false),
		DEFEND_RAID(true, true),
		REBUILD(false, false),
		DEFEND_VS_PLAYER(true, true),
		HUNT_PLAYER(false, false),
		COUNTER_GROUND_BATTLE(true, false),
		ASSEMBLE(false, false),
		RESUPPLY(true, false),
		FOLLOW_PLAYER(false, false),
		WAIT_ORBIT(false, false),
		IDLE(false, true);
		
		protected final boolean busyTask;
		protected final boolean allowMilitaryResponse;
		
		TaskType(boolean busyTask, boolean allowMilitaryResponse) {
			this.busyTask = busyTask;
			this.allowMilitaryResponse = allowMilitaryResponse;
		}
		
		/**
		 * Returns true for tasks we don't like to "put down", i.e. reassignment would be considered inconvenient.
		 * @return
		 */
		public boolean isBusyTask() {
			return busyTask;
		}

		/**
		 * Return true for tasks that {@code MilitaryResponseScript} should be allowed to override.
		 * @return
		 */
		public boolean canMilitaryResponse() {
			return allowMilitaryResponse;
		}
	}
	
	public static class SpecialForcesTask implements Cloneable {
		public TaskType type;
		public float priority;
		public IntelInfoPlugin raid;
		public float time = 45f;	// controls how long the action segment lasts
		@Getter private MarketAPI market;
		@Getter @Setter private SectorEntityToken entity;
		public StarSystemAPI system;
		public boolean playerIssued;
		public Map<String, Object> params = new HashMap<>();
		
		public SpecialForcesTask(TaskType type, float priority) {
			this.type = type;
			this.priority = priority;
		}
		
		public SpecialForcesTask(String type, float priority) {
			this.type = TaskType.valueOf(type.toUpperCase(Locale.ENGLISH));
			this.priority = priority;
		}
		
		public void setMarket(MarketAPI market) {
			this.market = market;
			if (market != null) this.entity = market.getPrimaryEntity();
		}

		public StarSystemAPI getSystem() {
			if (system != null) return system;
			if (market != null) return market.getStarSystem();
			return null;
		}
		
		public SpecialForcesTask clone() throws CloneNotSupportedException {
			return (SpecialForcesTask)super.clone();
		}

		/**
		 * Returns a string describing the task.
		 * @return
		 */
		public String getText() {
			switch (type) {
				case RAID:
					return StringHelper.getFleetAssignmentString("raiding", market.getName());
				case ASSIST_RAID:
					return StringHelper.getFleetAssignmentString("assisting", ((BaseIntelPlugin)raid).getSmallDescriptionTitle());
				case DEFEND_RAID:
					return StringHelper.getFleetAssignmentString("defendingVs", ((BaseIntelPlugin)raid).getSmallDescriptionTitle());
				case COUNTER_GROUND_BATTLE:
					return StringHelper.getFleetAssignmentString("counteringGroundBattle", market.getName());
				case PATROL:
					return StringHelper.getFleetAssignmentString("patrolling", (market != null? market.getName() : system.getNameWithLowercaseType()));
				case DEFEND_VS_PLAYER:
					return StringHelper.getFleetAssignmentString("defendingVsPlayer", Global.getSector().getPlayerFleet().getContainingLocation().getName());
				case REBUILD:
					return StringHelper.getFleetAssignmentString("reconstituting", market.getName());
				case ASSEMBLE:
					return StringHelper.getFleetAssignmentString("assembling", market.getName());
				case RESUPPLY:
					return StringHelper.getFleetAssignmentString("resupplying", market.getName());
				case WAIT_ORBIT:
					return StringHelper.getFleetAssignmentString("orbiting", entity.getName());
				case FOLLOW_PLAYER:
					return StringHelper.getFleetAssignmentString("following", entity.getName());
				case IDLE:
					return StringHelper.getFleetAssignmentString("idle", null);
				default:
					return StringHelper.getString("unknown");
			}
		}
	}
}
