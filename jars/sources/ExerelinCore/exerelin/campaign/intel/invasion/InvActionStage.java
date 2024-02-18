package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript.MilitaryResponseParams;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI.FleetActionDelegate;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.battle.NexWarSimScript;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel;
import exerelin.campaign.intel.fleets.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.campaign.intel.groundbattle.GroundBattleIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class InvActionStage extends ActionStage implements FleetActionDelegate {
	
	public static Logger log = Global.getLogger(InvActionStage.class);
	
	protected MarketAPI target;
	protected boolean playerTargeted = false;
	protected List<MilitaryResponseScript> scripts = new ArrayList<MilitaryResponseScript>();
	protected boolean gaveOrders = true; // will be set to false in updateRoutes()
	protected float untilAutoresolve = 30f;
	
	protected OffensiveFleetIntel offFltIntel;
	protected transient RouteData currRouteForAutoresolve;
	
	public InvActionStage(OffensiveFleetIntel invasion, MarketAPI target) {
		super(invasion);
		this.target = target;
		playerTargeted = target.isPlayerOwned();
		offFltIntel = invasion;
		
		untilAutoresolve = 15f + 5f * (float) Math.random();
	}
	
	public MarketAPI getTarget() {
		return target;
	}
	
	protected InvasionIntel getInvasionIntel() {
		if (offFltIntel instanceof InvasionIntel) {
			return (InvasionIntel)offFltIntel;
		}
		return null;
	}
	
	protected boolean pauseDueToOngoingGroundBattle() {
		InvasionIntel inv = getInvasionIntel();
		if (inv != null && inv.hasOngoingNonJoinableBattle()) {
			return true;
		}
		return false;
	}

	@Override
	public void advance(float amount) {
		super.advance(amount);
		
		float days = Misc.getDays(amount);
		
		if (!pauseDueToOngoingGroundBattle()) {
			untilAutoresolve -= days;
		}
		
		if (!gaveOrders) {
			gaveOrders = true;
		
			removeMilScripts();

			// getMaxDays() is always 1 here
			// scripts get removed anyway so we don't care about when they expire naturally
			// just make sure they're around for long enough
			float duration = 100f;
			
			MilitaryResponseParams params = new MilitaryResponseParams(ActionType.HOSTILE, 
					"inv_" + Misc.genUID() + target.getId(), 
					intel.getFaction(),
					target.getPrimaryEntity(),
					1f,
					duration);
			MilitaryResponseScript script = new MilitaryResponseScript(params);
			target.getContainingLocation().addScript(script);
			scripts.add(script);
			
			MilitaryResponseParams defParams = new MilitaryResponseParams(ActionType.HOSTILE, 
					"defInv_" + Misc.genUID() + target.getId(), 
					target.getFaction(),
					target.getPrimaryEntity(),
					1f,
					duration);
			MilitaryResponseScript defScript = new MilitaryResponseScript(defParams);
			target.getContainingLocation().addScript(defScript);
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
	
	protected void checkIfInvasionFailed() {
		// check if all invasion fleets have failed
		if (offFltIntel.getOutcome() != null)
			return;
		
		List<RouteData> routes = getRoutes();
		boolean anyRaidersRemaining = false;
		for (RouteData route : routes)
		{
			if (!offFltIntel.isRouteActionDone(route))
			{
				anyRaidersRemaining = true;
				break;
			}
		}
		if (!anyRaidersRemaining) {
			log.info("No raiders remaining, running check");
			if (offFltIntel instanceof InvasionIntel && ((InvasionIntel)offFltIntel).getGroundBattle() != null) 
			{
				// we've delivered our load, go ahead and proceed to next stage
				succeed(true);
			}
			else if (target.getFaction() != intel.getFaction()) {
				// target not captured yet				
				if (target.getFaction().isHostileTo(intel.getFaction())) {
					offFltIntel.reportOutcome(OffensiveOutcome.FAIL);
					status = RaidStageStatus.FAILURE;
					log.info("Invasion failed, no raiders remaining");
				} else {
					offFltIntel.reportOutcome(OffensiveOutcome.NO_LONGER_HOSTILE);
					status = RaidStageStatus.FAILURE;
				}
				//offFltIntel.sendOutcomeUpdate();	// don't send for failure, advanceImpl() handles that
			}
		}
	}
	
	public void succeed(boolean sendUpdate) {
		offFltIntel.reportOutcome(OffensiveOutcome.SUCCESS);
		status = RaidStageStatus.SUCCESS;
		if (sendUpdate) offFltIntel.sendOutcomeUpdate();
	}
	
	@Override
	protected void updateStatus() {
		abortIfNeededBasedOnFP(true);
		if (status != RaidStageStatus.ONGOING) return;
		
		boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(target.getPrimaryEntity());
		if (!inSpawnRange) inSpawnRange |= offFltIntel.anyActionRoutesHaveLiveFleets();
		if (!inSpawnRange && untilAutoresolve <= 0){
			autoresolve();
			return;
		}
		
		checkIfInvasionFailed();
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
			offFltIntel.reportOutcome(OffensiveOutcome.TASK_FORCE_DEFEATED);
			status = RaidStageStatus.FAILURE;
			if (giveReturnOrders) {
				giveReturnOrdersToStragglers(routes);
			}
			return;
		}
	}
	
	// TODO: only primary invasion fleet should have invasion action text
	// if primary invasion fleets are still a thing
	@Override
	public String getRaidActionText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("invading", market.getName());
	}

	@Override
	public String getRaidApproachText(CampaignFleetAPI fleet, MarketAPI market) {
		return StringHelper.getFleetAssignmentString("movingInToInvade", market.getName());
	}
	
	// get attacker strength and defender strength
	// if latter is too much higher than former, bomb target if possible
	// call NPC invade method, await results
	@Override
	public void performRaid(CampaignFleetAPI fleet, MarketAPI market) {
		if (offFltIntel.getOutcome() != null)
			return;
		
		// no double raiding
		if (fleet != null && offFltIntel.isRouteActionDone(fleet)) {
			log.warn(fleet.getName() + " is attempting invasion twice");
			return;
		}
		
		log.info("Resolving invasion action against " + market.getName() + ": " + (fleet == null));
		
		removeMilScripts();
		
		boolean needBomb = false;
		float atkStrength = 0, defStrength = 0;	// only for legacy invasions
		
		RouteData route = offFltIntel.getRouteFromFleet(fleet);
		if (route == null) route = currRouteForAutoresolve;
		
		if (NexConfig.legacyInvasions) {
			int marines = ((InvasionIntel)intel).getMarinesPerFleetV2(route);
			atkStrength = (InvasionIntel.USE_REAL_MARINES && fleet != null) ? 
				InvasionRound.getAttackerStrength(fleet) 
				: InvasionRound.getAttackerStrength(offFltIntel.getFaction(), marines);
			
			log.info(String.format("Debug: Fleet has %s marines in total, under the old system it would have %s",
						marines, ((InvasionIntel)intel).getMarinesPerFleet())
					);
			
			defStrength = InvasionRound.getDefenderStrength(market, 1);

			log.info("\tStrength ratio: " + atkStrength + " : " + defStrength);

			//needBomb = atkStrength < defStrength;
		} else {
			needBomb = true;
		}
		
		// don't cause an environmental disaster
		if (target.hasCondition(Conditions.HABITABLE) && !target.hasCondition(Conditions.POLLUTION))
			needBomb = false;
		
		// don't bomb if we're raiding own planet for whatever reason
		if (!target.getFaction().isHostileTo(intel.getFaction())) {
			needBomb = false;
		}
		
		// check whether there's actually anything to bomb
		List<Industry> targets = Nex_MarketCMD.getTacticalBombardmentTargets(market);
		if (targets.isEmpty()) needBomb = false;
		
		if (needBomb)
		{
			//float bombCost = Nex_MarketCMD.getBombardmentCost(market, fleet, BombardType.TACTICAL);
			float bombCost = Nex_MarketCMD.getBombardmentCost(market, fleet);
			float maxCost = route.getExtra().fp * Misc.FP_TO_BOMBARD_COST_APPROX_MULT;
			if (fleet != null) {
				maxCost = fleet.getCargo().getMaxFuel() * 0.25f;
			}
			Global.getLogger(this.getClass()).info("\tBombing target would cost " + bombCost + " of " + maxCost);
			
			if (bombCost <= maxCost) {
				Global.getLogger(this.getClass()).info("\tBombing target");
				Nex_MarketCMD cmd = new Nex_MarketCMD(market.getPrimaryEntity());
				cmd.doBombardment(intel.getFaction(), BombardType.TACTICAL);
				
				// force recompute of def strength
				market.reapplyConditions();
				market.reapplyIndustries();
				log.info("New strength is " + market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0));
			}
		}
		
		Global.getLogger(this.getClass()).info("\tInvading target");
		if (NexConfig.legacyInvasions) {
			boolean success = InvasionRound.npcInvade(atkStrength, fleet, offFltIntel.getFaction(), market);
			if (success)
			{
				offFltIntel.reportOutcome(OffensiveOutcome.SUCCESS);
				status = RaidStageStatus.SUCCESS;
				//offFltIntel.endAfterDelay();	// can't end now, it breaks the subsequent wait stage
			}
		} else {
			// create ground battle if doesn't exist
			// add troops to it
			InvasionIntel inv = (InvasionIntel)offFltIntel;
			GroundBattleIntel gb = inv.getGroundBattle();
			if (gb == null) {
				gb = inv.initGroundBattle();
			}
			if (gb == null)	{ // failed to generate, possibly because there's an existing battle we couldn't join
				// don't abort our invasion, wait for the existing battle to finish
				//offFltIntel.setRouteActionDone(fleet);
				return;
			}
			
			// honestly we could tag action as successful here, since the troops were dropped off
			// ...no we can't, the other routes still need to make their attempt
			if (fleet != null) inv.deployToGroundBattle(fleet);
			else inv.deployToGroundBattle(currRouteForAutoresolve);
		}
		
		// when FAILURE, gets sent by RaidIntel
		if (offFltIntel.getOutcome() != null) {
			if (status == RaidStageStatus.SUCCESS) {
				offFltIntel.sendOutcomeUpdate();
			} else {
				removeMilScripts();
				giveReturnOrdersToStragglers(getRoutes());
			}
		}
		
		offFltIntel.setRouteActionDone(fleet);
	}
	
	protected void autoresolve() {
		Global.getLogger(this.getClass()).info("Autoresolving invasion action vs. " + target.getId());
		float str = NexWarSimScript.getFactionAndAlliedStrength(intel.getFaction(), getTarget().getFaction(), getTarget().getStarSystem());
		float enemyStr = NexWarSimScript.getFactionAndAlliedStrength(getTarget().getFaction(), intel.getFaction(), getTarget().getStarSystem());
		
		float defensiveStr = enemyStr + WarSimScript.getStationStrength(target.getFaction(), 
							 target.getStarSystem(), target.getPrimaryEntity());
		
		if (defensiveStr >= str) {
			status = RaidStageStatus.FAILURE;
			removeMilScripts();
			giveReturnOrdersToStragglers(getRoutes());
			
			offFltIntel.reportOutcome(OffensiveOutcome.TASK_FORCE_DEFEATED);
			return;
		}
		
		Industry station = Misc.getStationIndustry(target);
		if (station != null) {
			OrbitalStation.disrupt(station);
		}
		
		List<RouteData> routes = getRoutes();
		for (RouteData route : routes)
		{
			if (offFltIntel.isRouteActionDone(route))
				continue;
			// untested: keep brawl mode strike fleets from invading
			// shouldn't be needed with the route check, but just in case
			if ("exerelinInvasionSupportFleet".equals(route.getExtra().fleetType))
				continue;
			
			currRouteForAutoresolve = route;
			performRaid(route.getActiveFleet(), target);
			offFltIntel.setRouteActionDone(route);
			log.info("Autoresolving for route " + route.toString());
			if (NexConfig.legacyInvasions && offFltIntel.getOutcome() != null) break;	// stop attacking if event already over (e.g. already captured)
		}
		boolean fail;
		boolean isNewInvade = offFltIntel instanceof InvasionIntel && !NexConfig.legacyInvasions;
		if (isNewInvade) {
			fail = ((InvasionIntel)offFltIntel).getGroundBattle() == null;
		} else {
			fail = offFltIntel.getOutcome() != OffensiveOutcome.SUCCESS;
		}
		if (fail)
		{
			status = RaidStageStatus.FAILURE;
			offFltIntel.reportOutcome(OffensiveOutcome.FAIL);
		}
		else if (isNewInvade) {
			succeed(false);
		}
	}
	
	@Override
	protected void updateRoutes() {
		resetRoutes();
		
		gaveOrders = false;
		
		((OffensiveFleetIntel)intel).sendEnteredSystemUpdate();
		
		List<RouteData> routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
		for (RouteData route : routes) {
			if (target.getStarSystem() != null) { // so that fleet may spawn NOT at the target
				RouteSegment segment = new RouteSegment(Math.min(2f, untilAutoresolve), target.getStarSystem().getCenter());
				segment.custom = target;	// read by RaidAssignmentAINoWander to identify our goal
				route.addSegment(segment);
			}
			RouteSegment segment = new RouteSegment(1000f, target.getPrimaryEntity());
			segment.custom = target;
			route.addSegment(segment);
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
			info.addPara(StringHelper.getString("exerelin_invasion", "intelStageAction"), opad);
			
			if (Global.getSettings().isDevMode() || ExerelinModPlugin.isNexDev) {
				info.addPara("DEBUG: Autoresolving in %s days", opad, h, 
						String.format("%.1f", untilAutoresolve));
				if (pauseDueToOngoingGroundBattle()) {
					info.addPara("Autoresolve suspended, someone else's ground battle is ongoing", 0, h, 
							String.format("%.1f", untilAutoresolve));
				}
			}
			
			return;
		}
		
		OffensiveFleetIntel intel = ((OffensiveFleetIntel)this.intel);
		if (intel.getOutcome() != null) {
			String key = "intelStageAction";
			switch (intel.getOutcome()) {
			case FAIL:
				key += "DefeatedGround";
				break;
			case SUCCESS:
				if (NexConfig.legacyInvasions)
					key += "Success";
				else
					key += "SuccessV2";
				break;
			case TASK_FORCE_DEFEATED:
				key += "DefeatedSpace";
				break;
			case NO_LONGER_HOSTILE:
			case MARKET_NO_LONGER_EXISTS:
			case OTHER:
				key += "Aborted";
				break;
			}
			info.addPara(StringHelper.getStringAndSubstituteToken("exerelin_invasion",
						key, "$market", target.getName()), opad);
		} else if (status == RaidStageStatus.SUCCESS) {			
			info.addPara("The expeditionary force has succeeded.", opad); // shouldn't happen?
		} else {
			info.addPara("The expeditionary force has failed.", opad); // shouldn't happen?
		}
	}

	@Override
	public boolean canRaid(CampaignFleetAPI fleet, MarketAPI market) {
		if (offFltIntel.getOutcome() != null) return false;
		if (offFltIntel.isRouteActionDone(fleet)) return false;
		InvasionIntel inv = getInvasionIntel();
		if (inv != null && inv.hasOngoingNonJoinableBattle()) {
			return false;
		}
		
		return market == target;
	}
	
	@Override
	public String getRaidPrepText(CampaignFleetAPI fleet, SectorEntityToken from) {
		return StringHelper.getFleetAssignmentString("orbiting", from.getName());
	}
	
	@Override
	public String getRaidInSystemText(CampaignFleetAPI fleet) {
		return StringHelper.getFleetAssignmentString("attackingAroundStarSystem", target.getContainingLocation().getNameWithLowercaseType());
	}
	
	@Override
	public String getRaidDefaultText(CampaignFleetAPI fleet) {
		return StringHelper.getFleetAssignmentString("travellingTo", target.getName());
	}
	
	@Override
	public boolean isPlayerTargeted() {
		return playerTargeted;
	}
}