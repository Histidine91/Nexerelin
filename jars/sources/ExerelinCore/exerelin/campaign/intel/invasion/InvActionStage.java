package exerelin.campaign.intel.invasion;

import com.fs.starfarer.api.Global;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript.MilitaryResponseParams;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.econ.impl.OrbitalStation;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidStageStatus;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseAssignmentAI.FleetActionDelegate;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.intel.InvasionIntel;
import exerelin.campaign.intel.OffensiveFleetIntel.OffensiveOutcome;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;

public class InvActionStage extends ActionStage implements FleetActionDelegate {
	
	public static Logger log = Global.getLogger(InvActionStage.class);
	
	protected MarketAPI target;
	protected boolean playerTargeted = false;
	protected List<MilitaryResponseScript> scripts = new ArrayList<MilitaryResponseScript>();
	protected boolean gaveOrders = true; // will be set to false in updateRoutes()
	protected float untilAutoresolve = 30f;
	
	public InvActionStage(InvasionIntel invasion, MarketAPI target) {
		super(invasion);
		this.target = target;
		playerTargeted = target.isPlayerOwned();
		
		untilAutoresolve = 15f + 5f * (float) Math.random();
	}
	

	@Override
	public void advance(float amount) {
		super.advance(amount);
		
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
	
	@Override
	protected void updateStatus() {
//		if (true) {
//			status = RaidStageStatus.SUCCESS;
//			return;
//		}
		
		abortIfNeededBasedOnFP(true);
		if (status != RaidStageStatus.ONGOING) return;
		
		boolean inSpawnRange = RouteManager.isPlayerInSpawnRange(target.getPrimaryEntity());
		if (!inSpawnRange && untilAutoresolve <= 0){
			autoresolve();
			return;
		}
		
		if (!target.isInEconomy() || !target.getFaction().isHostileTo(this.intel.getFaction())) {
			status = RaidStageStatus.FAILURE;
			((InvasionIntel)intel).setOutcome(OffensiveOutcome.NO_LONGER_HOSTILE);
			removeMilScripts();
			giveReturnOrdersToStragglers(getRoutes());
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
		log.info("Resolving invasion action against " + market.getName() + ": " + (fleet == null));
		
		removeMilScripts();
		
		InvasionIntel intel = ((InvasionIntel)this.intel);
		
		status = RaidStageStatus.SUCCESS;
		
		float atkStrength = (InvasionIntel.USE_REAL_MARINES && fleet != null) ? 
				InvasionRound.getAttackerStrength(fleet) 
				: InvasionRound.getAttackerStrength(intel.getFaction(), intel.getMarinesPerFleet());
		float defStrength = InvasionRound.getDefenderStrength(market, 1);
		
		log.info("\tStrength ratio: " + atkStrength + " : " + defStrength);
		
		boolean needBomb = atkStrength < defStrength;
		
		if (needBomb)
		{
			//float bombCost = Nex_MarketCMD.getBombardmentCost(market, fleet, BombardType.TACTICAL);
			float bombCost = Nex_MarketCMD.getBombardmentCost(market, fleet);
			float maxCost = intel.getRaidFP() / intel.getNumFleets() * Misc.FP_TO_BOMBARD_COST_APPROX_MULT;
			if (fleet != null) {
				maxCost = fleet.getCargo().getMaxFuel() * 0.25f;
			}
			Global.getLogger(this.getClass()).info("\tBombing target would cost " + bombCost + " of " + maxCost);
			
			/*
			if (bombCost <= maxCost) {
				Global.getLogger(this.getClass()).info("\tBombing target");
				Nex_MarketCMD cmd = new Nex_MarketCMD(market.getPrimaryEntity());
				cmd.doBombardment(intel.getFaction(), BombardType.TACTICAL);
				
				// force recompute of def strength
				market.reapplyConditions();
				market.reapplyIndustries();
				log.info("New strength is " + market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0));
			}
			*/
		}
		
		Global.getLogger(this.getClass()).info("\tInvading target");
		boolean success = InvasionRound.npcInvade(atkStrength, fleet, intel.getFaction(), market);
		if (success)
		{
			intel.setOutcome(OffensiveOutcome.SUCCESS);
		}
		
		// when FAILURE, gets sent by RaidIntel
		if (intel.getOutcome() != null) {
			if (status == RaidStageStatus.SUCCESS) {
				intel.sendOutcomeUpdate();
			} else {
				removeMilScripts();
				giveReturnOrdersToStragglers(getRoutes());
			}
		}
	}
	
	protected void autoresolve() {
		Global.getLogger(this.getClass()).info("Autoresolving invasion action");
		float str = WarSimScript.getFactionStrength(intel.getFaction(), target.getStarSystem());
		float enemyStr = WarSimScript.getFactionStrength(target.getFaction(), target.getStarSystem());
		
		float defensiveStr = enemyStr + WarSimScript.getStationStrength(target.getFaction(), 
							 target.getStarSystem(), target.getPrimaryEntity());
		InvasionIntel intel = ((InvasionIntel)this.intel);
		
		if (defensiveStr >= str) {
			status = RaidStageStatus.FAILURE;
			removeMilScripts();
			giveReturnOrdersToStragglers(getRoutes());
			
			intel.setOutcome(OffensiveOutcome.TASK_FORCE_DEFEATED);
			return;
		}
		
		Industry station = Misc.getStationIndustry(target);
		if (station != null) {
			OrbitalStation.disrupt(station);
		}
		
		List<RouteData> routes = getRoutes();
		for (RouteData route : routes)
		{
			performRaid(route.getActiveFleet(), target);
			if (intel.getOutcome() != null) break;	// stop attacking if event already over (e.g. already captured)
		}
		if (intel.getOutcome() != OffensiveOutcome.SUCCESS)
		{
			status = RaidStageStatus.FAILURE;
			intel.setOutcome(OffensiveOutcome.FAIL);
			intel.sendOutcomeUpdate();
		}
	}
	
	
	@Override
	protected void updateRoutes() {
		resetRoutes();
		
		gaveOrders = false;
		
		((InvasionIntel)intel).sendEnteredSystemUpdate();
		
		List<RouteData> routes = RouteManager.getInstance().getRoutesForSource(intel.getRouteSourceId());
		for (RouteData route : routes) {
			if (target.getStarSystem() != null) { // so that fleet may spawn NOT at the target
				route.addSegment(new RouteSegment(Math.min(5f, untilAutoresolve), target.getStarSystem().getCenter()));
			}
			route.addSegment(new RouteSegment(1000f, target.getPrimaryEntity()));
		}
	}
	
	@Override
	public void showStageInfo(TooltipMakerAPI info) {
		int curr = intel.getCurrentStage();
		int index = intel.getStageIndex(this);
		
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float pad = 3f;
		float opad = 10f;
		
		if (curr < index) return;
		
		if (status == RaidStageStatus.ONGOING && curr == index) {
			info.addPara(StringHelper.getString("exerelin_invasion", "intelStageAction"), opad);
			return;
		}
		
		InvasionIntel intel = ((InvasionIntel)this.intel);
		if (intel.getOutcome() != null) {
			String key = "intelStageAction";
			switch (intel.getOutcome()) {
			case FAIL:
				key += "DefeatedGround";
				break;
			case SUCCESS:
				key += "Success";
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
		InvasionIntel intel = ((InvasionIntel)this.intel);
		if (intel.getOutcome() != null) return false;
		return market == target;
	}
	
	@Override
	public String getRaidPrepText(CampaignFleetAPI fleet, SectorEntityToken from) {
		return StringHelper.getFleetAssignmentString("orbiting", from.getName());
	}
	
	@Override
	public String getRaidInSystemText(CampaignFleetAPI fleet) {
		return StringHelper.getFleetAssignmentString("travellingTo", target.getName());
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