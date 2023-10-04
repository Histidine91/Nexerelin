package exerelin.campaign.intel.raid;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.battle.EncounterLootHandler;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtils;
import org.lazywizard.lazylib.MathUtils;

import java.util.List;
import java.util.Random;

import static com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage.STRAGGLER;

public class RemnantRaidIntel extends NexRaidIntel {
	
	protected CampaignFleetAPI base;
	protected int numPrevious;
	
	public RemnantRaidIntel(FactionAPI attacker, CampaignFleetAPI base, MarketAPI target, 
			float fp, float orgDur, int numPrevious) {
		super(attacker, null, target, fp, orgDur);
		this.base = base;
		this.numPrevious = numPrevious;
	}
	
	public CampaignFleetAPI getBase() {
		return base;
	}
	
	public int getNumPrevious() {
		return numPrevious;
	}
	
	@Override
	public void init() {
		log.info("Creating Remnant raid intel");
		
		SectorEntityToken gather = base;
		
		addStage(new RemnantRaidOrganizeStage(this, InvasionFleetManager.getManager().getFakeMarketForRemnantRaids(), orgDur));
		
		float successMult = 0.4f;
		RemnantRaidAssembleStage assemble = new RemnantRaidAssembleStage(this, gather);
		assemble.setSpawnFP(fp);
		assemble.setAbortFP(fp * successMult);
		addStage(assemble);
		
		SectorEntityToken raidJump = RouteLocationCalculator.findJumpPointToUse(getFactionForUIColors(), target.getPrimaryEntity());
		if (raidJump == null) {
			endImmediately();
			return;
		}

		RemnantRaidTravelStage travel = new RemnantRaidTravelStage(this, gather, raidJump, false);
		travel.setAbortFP(fp * successMult);
		addStage(travel);
		
		action = new RemnantRaidActionStage(this, system);
		action.setAbortFP(fp * successMult);
		addStage(action);
		
		addStage(new RemnantRaidReturnStage(this));

		int nexIntelQueued = NexConfig.nexIntelQueued;
		switch (nexIntelQueued) {

			case 0:
				addIntelIfNeeded();
				break;
			case 1:
				if ((isPlayerTargeted() || targetFaction == Misc.getCommissionFaction()))
					addIntelIfNeeded();
				else if (shouldDisplayIntel())
					queueIntelIfNeeded();
				break;
			case 2:
				if (shouldDisplayIntel()) {
					Global.getSector().getIntelManager().queueIntel(this);
					intelQueuedOrAdded = true;
				}
				break;

			default:
				addIntelIfNeeded();
				Global.getSector().getCampaignUI().addMessage("Switch statement within init(), in RemnantRaidIntel, " +
						"defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
						"is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!");
		}
	}
	
	@Override
	public CampaignFleetAPI spawnFleet(RouteData route) {
		
		Random random = route.getRandom();
		
		CampaignFleetAPI fleet = createFleet(getFaction().getId(), route, null, null, random);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		//fleet.addEventListener(this);
		
		base.getContainingLocation().addEntity(fleet);
		fleet.setFacing((float) Math.random() * 360f);
		// this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
		fleet.setLocation(base.getLocation().x, base.getLocation().y);
		
		fleet.addScript(createAssignmentAI(fleet, route));
		
		if (faction.getId().equals(Factions.REMNANTS)) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN, 
				   new RemnantRaidFleetInteractionConfigGen());
			fleet.getMemoryWithoutUpdate().set(EncounterLootHandler.FLEET_MEMORY_KEY, "remnant");
		}
		
		return fleet;
	}
		
	@Override
	protected float getDistanceToTarget(MarketAPI market) {
		return MathUtils.getDistance(base.getLocationInHyperspace(), target.getLocationInHyperspace());
	}
	
	@Override
	protected boolean shouldDisplayIntel()
	{
		if (INTEL_ALWAYS_VISIBLE) return true;
		if (Global.getSettings().isDevMode()) return true;
		if (faction.getRelationshipLevel(Factions.PLAYER).isAtWorst(RepLevel.FAVORABLE))
			return true;
		if (getBase().isVisibleToPlayerFleet()) return true;
		LocationAPI loc = base.getContainingLocation();
		
		List<SectorEntityToken> sniffers = Global.getSector().getIntel().getCommSnifferLocations();
		for (SectorEntityToken relay : sniffers)
		{
			if (relay.getContainingLocation() == loc)
				return true;
		}
		return false;
	}
	
	public boolean isSourceKnown() {
		if (NexUtils.isNonPlaytestDevMode()) return true;
		if (getFaction().getRelationshipLevel(Factions.PLAYER).isAtWorst(RepLevel.FRIENDLY))
			return true;
		return getBase().isVisibleToPlayerFleet();
	}
	
	public void giveReturnOrdersToStragglers(BaseRaidStage stage, List<RouteManager.RouteData> stragglers) 
	{
		CampaignFleetAPI homeBase = this.base;
		if (homeBase == null || !homeBase.isAlive()) {
			Global.getLogger(this.getClass()).info("Base dead, redirecting");
			// no base, try to find a new one
			homeBase = InvasionFleetManager.findBase(faction);
			
			// still no base either? Just give up and loiter forever
			if (homeBase == null || !homeBase.isAlive()) {
				Global.getLogger(this.getClass()).info("No remaining bases, loiter");
				return;
			}
				
		}
		
		for (RouteManager.RouteData route : stragglers) {
			SectorEntityToken from = Global.getSector().getHyperspace().createToken(route.getInterpolatedHyperLocation());
			
			route.setCustom(STRAGGLER);
			stage.resetRoute(route);

			float travelDays = RouteLocationCalculator.getTravelDays(from, homeBase);
			if (DebugFlags.RAID_DEBUG) {
				travelDays *= 0.1f;
			}
			
			float orbitDays = 1f + 1f * (float) Math.random();
			route.addSegment(new RouteManager.RouteSegment(travelDays, from, homeBase));
			route.addSegment(new RouteManager.RouteSegment(orbitDays, homeBase));
			
			//route.addSegment(new RouteSegment(2f + (float) Math.random() * 1f, homeBase));
		}
	}
	
	@Override
	public void checkForTermination() {
		if (outcome != null) return;
		
		if (getCurrentStage() <= 1 && (base == null || !base.isAlive()))
			terminateEvent(OffensiveOutcome.FAIL);
	}

	public boolean hasMarket() {
		return false;
	}
}
