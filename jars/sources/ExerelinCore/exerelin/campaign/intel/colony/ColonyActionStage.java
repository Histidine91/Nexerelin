package exerelin.campaign.intel.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript.MilitaryResponseParams;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI.FleetActionDelegate;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.battle.NexWarSimScript;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel.ColonyOutcome;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.utilities.NexUtilsReputation;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ColonyActionStage extends ActionStage implements FleetActionDelegate {
	
	public static final String MEM_KEY_COLONIZATION_ATTEMPTED = "$nex_colonizationAttempted";
	
	public static Logger log = Global.getLogger(ColonyActionStage.class);
	
	protected boolean playerTargeted = false;
	protected List<MilitaryResponseScript> scripts = new ArrayList<MilitaryResponseScript>();
	protected boolean gaveOrders = true; // will be set to false in updateRoutes()
	protected float untilAutoresolve = 5f;
	
	ColonyExpeditionIntel colonyFleetIntel;
	
	public ColonyActionStage(ColonyExpeditionIntel colFleet) {
		super(colFleet);
		colonyFleetIntel = colFleet;
		playerTargeted = getTarget().isPlayerOwned();
		
		untilAutoresolve = 5f;
	}
	
	public MarketAPI getTarget() {
		return colonyFleetIntel.getTarget();
	}

	@Override
	public void advance(float amount) {
		super.advance(amount);
		checkIfAlreadyColonized();
		
		float days = Misc.getDays(amount);
		untilAutoresolve -= days;
		if (DebugFlags.PUNITIVE_EXPEDITION_DEBUG) {
			untilAutoresolve -= days * 100f;
		}
		
		if (!gaveOrders) {
			gaveOrders = true;
		
			removeMilScripts();

			// getMaxDays() is always 1 here
			// scripts get removed anyway so we don't care about when they expire naturally
			// just make sure they're around for long enough
			float duration = 100f;
			
			MilitaryResponseParams params = new MilitaryResponseParams(ActionType.HOSTILE, 
					"colony_" + Misc.genUID() + getTarget().getId(), 
					intel.getFaction(),
					getTarget().getPrimaryEntity(),
					1f,
					duration);
			MilitaryResponseScript script = new MilitaryResponseScript(params);
			getTarget().getContainingLocation().addScript(script);
			scripts.add(script);
			
			MilitaryResponseParams defParams = new MilitaryResponseParams(ActionType.HOSTILE, 
					"defColony_" + Misc.genUID() + getTarget().getId(), 
					getTarget().getFaction(),
					getTarget().getPrimaryEntity(),
					1f,
					duration);
			MilitaryResponseScript defScript = new MilitaryResponseScript(defParams);
			getTarget().getContainingLocation().addScript(defScript);
			scripts.add(defScript);
		}
	}

	protected void removeMilScripts() {
		if (scripts != null) {
			for (MilitaryResponseScript s : scripts) {
				s.forceDone();
			}
		}
	}
	
	// check for queue jumping	
	@Override
	public void notifyStarted() {
		super.notifyStarted();
	}
	
	protected void checkIfAlreadyColonized() {
		if (colonyFleetIntel.hostileMode || colonyFleetIntel.getColonyOutcome() != null)
			return;
		
		if (getTarget().isInEconomy()) {
			// if queue jumper is player, lower reputation, prepare for hostile action if needed
			if (getTarget().getFaction().isPlayerFaction()) {
				NexUtilsReputation.adjustPlayerReputation(colonyFleetIntel.getFaction(), 
						-ColonyExpeditionIntel.QUEUE_JUMP_REP_PENALTY);
				if (colonyFleetIntel.getFaction().isAtBest(Factions.PLAYER, RepLevel.INHOSPITABLE))
					colonyFleetIntel.hostileMode = true;
			}
			// if queue-jumped and not hostile, we failed
			if (!colonyFleetIntel.hostileMode) {
				colonyFleetIntel.reportOutcome(OffensiveOutcome.FAIL);
				colonyFleetIntel.setColonyOutcome(ColonyOutcome.QUEUE_JUMPED);
				status = RaidStageStatus.FAILURE;
			} else {	// mark fleets as hostile and extend autoresolve time
				List<RouteData> routes = getRoutes();
				for (RouteData route : routes)
				{
					if (route.getActiveFleet() != null)
						route.getActiveFleet().getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
				}
				untilAutoresolve += 5f + 3f * (float) Math.random();
			}
		}
	}
	
	protected void checkIfExpeditionFailed() {
		// check if we have a colony fleet that hasn't "raided" yet
		if (colonyFleetIntel.getOutcome() != null)
			return;
		
		List<RouteData> routes = getRoutes();
		boolean anyRaidersRemaining = false;
		for (RouteData route : routes)
		{
			if (route.getActiveFleet() == null)
			{
				anyRaidersRemaining = true;
				break;
			}
			else {
				MemoryAPI mem = route.getActiveFleet().getMemoryWithoutUpdate();
				if (mem.getBoolean(MemFlags.MEMORY_KEY_RAIDER) && !mem.getBoolean(MEM_KEY_COLONIZATION_ATTEMPTED)) {
					anyRaidersRemaining = true;
					break;
				}
			}
		}
		if (!anyRaidersRemaining) {
			if (getTarget().getFaction() != intel.getFaction()) {
				// colonization failed
				if (getTarget().getFaction().isHostileTo(intel.getFaction())) {
					colonyFleetIntel.reportOutcome(OffensiveOutcome.FAIL);
					colonyFleetIntel.setColonyOutcome(ColonyOutcome.FAIL);
					status = RaidStageStatus.FAILURE;
					log.info("Colonization failed, no colony fleets remaining");
				} else {
					colonyFleetIntel.reportOutcome(OffensiveOutcome.NO_LONGER_HOSTILE);
					status = RaidStageStatus.FAILURE;
				}
				//offFltIntel.sendOutcomeUpdate();	// don't send for failure, advanceImpl() handles that
			}
		}
	}
	
	@Override
	protected void updateStatus() {
		abortIfNeededBasedOnFP(true);
		if (status != RaidStageStatus.ONGOING) return;
		
		boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(getTarget().getPrimaryEntity());
		if (!inSpawnRange) inSpawnRange |= colonyFleetIntel.anyActionRoutesHaveLiveFleets();
		if (!inSpawnRange && untilAutoresolve <= 0){
			autoresolve();
			return;
		}
		
		checkIfExpeditionFailed();
	}
	
	// same as parent, but also set correct outcome
	@Override
	protected void abortIfNeededBasedOnFP(boolean giveReturnOrders) {
		List<RouteData> routes = getRoutes();
		List<RouteData> stragglers = new ArrayList<RouteData>();
		
		boolean enoughMadeIt = enoughMadeIt(routes, stragglers);
		//enoughMadeIt = false;
		if (!enoughMadeIt) {
			log.info("Not enough made it, fail");
			colonyFleetIntel.reportOutcome(OffensiveOutcome.TASK_FORCE_DEFEATED);
			status = RaidStageStatus.FAILURE;
			if (giveReturnOrders) {
				giveReturnOrdersToStragglers(routes);
			}
			return;
		}
	}
	
	@Override
	public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("colonizing", market.getName());
	}

	@Override
	public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("movingInToColonize", market.getName());
	}
	
	protected boolean anyHostile() {
		if (getTarget().isInEconomy() && colonyFleetIntel.hostileMode) {
			return true;
		}
		for (MarketAPI market : Global.getSector().getEconomy().getMarkets(getTarget().getContainingLocation()))
		{
			if (market.getFaction().isHostileTo(colonyFleetIntel.getFaction())) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		if (fleet != null && fleet.getMemoryWithoutUpdate().getBoolean(MEM_KEY_COLONIZATION_ATTEMPTED)) {
			log.warn(fleet.getName() + " is attempting colonization twice");
			return;
		}
		if (fleet != null && MathUtils.getDistance(fleet, market.getPrimaryEntity()) > 300) {
			log.warn(fleet.getName() + " attempting colonization while too far away");
			return;
		}
		
		colonyFleetIntel.lastOwner = market.getFaction();
		
		// try to colonize, if not, invade if allowed
		if (!market.isInEconomy()) {
			colonyFleetIntel.createColony();
			colonyFleetIntel.colonyOutcome = ColonyOutcome.SUCCESS;
			colonyFleetIntel.reportOutcome(OffensiveOutcome.SUCCESS);
			status = RaidStageStatus.SUCCESS;
			colonyFleetIntel.sendOutcomeUpdate();
		}
		else {
			Global.getLogger(this.getClass()).info("\tColonists invading target");
			float atkStrength = InvasionRound.getAttackerStrength(intel.getFaction(), 300);
			boolean success = InvasionRound.npcInvade(atkStrength, fleet, intel.getFaction(), market);
			if (success) {
				colonyFleetIntel.reportOutcome(OffensiveOutcome.SUCCESS);
				colonyFleetIntel.colonyOutcome = ColonyOutcome.INVADE_SUCCESS;
				status = RaidStageStatus.SUCCESS;
				colonyFleetIntel.sendOutcomeUpdate();
			}
			else {
				colonyFleetIntel.colonyOutcome = ColonyOutcome.INVADE_FAILED;
			}
		}
		
		if (fleet != null)
			fleet.getMemoryWithoutUpdate().set(MEM_KEY_COLONIZATION_ATTEMPTED, true);
	}
	
	protected void autoresolve() {
		Global.getLogger(this.getClass()).info("Autoresolving colony expedition action");
		
		if (getTarget().isInEconomy() && !colonyFleetIntel.hostileMode)
		{
			status = RaidStageStatus.FAILURE;
			colonyFleetIntel.reportOutcome(OffensiveOutcome.FAIL);
			colonyFleetIntel.setColonyOutcome(ColonyOutcome.QUEUE_JUMPED);
		}
		
		//if (anyHostile()) {
		if (colonyFleetIntel.hostileMode) {
			float str = NexWarSimScript.getFactionAndAlliedStrength(intel.getFaction(), getTarget().getFaction(), getTarget().getStarSystem());
			float enemyStr = NexWarSimScript.getFactionAndAlliedStrength(getTarget().getFaction(), intel.getFaction(), getTarget().getStarSystem());

			float defensiveStr = enemyStr + WarSimScript.getStationStrength(getTarget().getFaction(), 
								 getTarget().getStarSystem(), getTarget().getPrimaryEntity());

			if (defensiveStr >= str) {
				status = RaidStageStatus.FAILURE;
				removeMilScripts();
				giveReturnOrdersToStragglers(getRoutes());

				colonyFleetIntel.reportOutcome(OffensiveOutcome.TASK_FORCE_DEFEATED);
				colonyFleetIntel.setColonyOutcome(ColonyOutcome.INVADE_FAILED);
				return;
			}

			Industry station = Misc.getStationIndustry(getTarget());
			if (station != null) {
				OrbitalStation.disrupt(station);
			}
		}
		
		List<RouteData> routes = getRoutes();
		for (RouteData route : routes)
		{
			performRaid(route.getActiveFleet(), getTarget());
		}
		if (colonyFleetIntel.getOutcome() != OffensiveOutcome.SUCCESS)
		{
			status = RaidStageStatus.FAILURE;
			colonyFleetIntel.reportOutcome(OffensiveOutcome.FAIL);
			//offFltIntel.sendOutcomeUpdate();	// don't send for failure, advanceImpl() handles that
		}
	}
	
	@Override
	protected void updateRoutes() {
		resetRoutes();
		
		gaveOrders = false;
		
		checkIfAlreadyColonized();
		((OffensiveFleetIntel)intel).sendEnteredSystemUpdate();
		
		List<RouteData> routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
		for (RouteData route : routes) {
			if (getTarget().getStarSystem() != null) { // so that fleet may spawn NOT at the target
				route.addSegment(new RouteSegment(Math.min(5f, untilAutoresolve), getTarget().getStarSystem().getCenter()));
			}
			route.addSegment(new RouteSegment(1000f, getTarget().getPrimaryEntity()));
		}
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		float opad = 10f;
		
		if (curr < index) return;
		
		if (status == RaidStageStatus.ONGOING && curr == index) {
			info.addPara(StringHelper.getString("nex_colonyFleet", "intelStageAction"), opad);
			
			if (Global.getSettings().isDevMode()) {
				info.addPara("DEBUG: Autoresolving in %s days", opad, h, 
						String.format("%.1f", untilAutoresolve));
			}
			
			return;
		}
		
		if (colonyFleetIntel.getColonyOutcome() != null) {
			// let intel class display outcome
		} else if (status == RaidStageStatus.SUCCESS) {			
			info.addPara("The expeditionary force has succeeded.", opad); // shouldn't happen?
		} else {
			info.addPara("The expeditionary force has failed.", opad); // shouldn't happen?
		}
	}

	@Override
	public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
		log.info(fleet.getName() + " checking if can raid: " + colonyFleetIntel.getColonyOutcome() + ", " 
				+ fleet.getMemoryWithoutUpdate().getBoolean(MEM_KEY_COLONIZATION_ATTEMPTED) + ", "
				+ market.getName() + ", " + getTarget().getName());
		if (colonyFleetIntel.getColonyOutcome() != null) return false;
		if (fleet.getMemoryWithoutUpdate().getBoolean(MEM_KEY_COLONIZATION_ATTEMPTED)) return false;
		
		return market == getTarget();
	}
	
	@Override
	public String getRaidPrepText(CampaignFleetAPI fleet, SectorEntityToken from) {
		return StringHelper.getFleetAssignmentString("orbiting", from.getName());
	}
	
	@Override
	public String getRaidInSystemText(CampaignFleetAPI fleet) {
		return StringHelper.getFleetAssignmentString("wandering", getTarget().getStarSystem().getName());
	}
	
	@Override
	public String getRaidDefaultText(CampaignFleetAPI fleet) {
		return StringHelper.getFleetAssignmentString("onColonyExpedition", null);
	}
	
	@Override
	public boolean isPlayerTargeted() {
		return playerTargeted;
	}
}