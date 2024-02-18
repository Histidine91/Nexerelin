package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.command.WarSimScript;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.ai.action.StrategicAction;
import exerelin.campaign.ai.action.StrategicActionDelegate;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.battle.NexWarSimScript;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.econ.GroundPoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.raid.NexRaidActionStage;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.StringHelper;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.List;
import java.util.*;

import static exerelin.campaign.battle.NexWarSimScript.*;
import static exerelin.campaign.fleets.InvasionFleetManager.TANKER_FP_PER_FLEET_FP_PER_10K_DIST;

public abstract class OffensiveFleetIntel extends RaidIntel implements RaidDelegate, StrategicActionDelegate {
	
	public static final String MEM_KEY_ACTION_DONE = "$nex_raidActionDone";
	public static final Object ENTERED_SYSTEM_UPDATE = new Object();
	public static final Object OUTCOME_UPDATE = new Object();
	public static final boolean INTEL_ALWAYS_VISIBLE = true;
	public static final float ALLY_GEAR_CHANCE = 0.5f;
	public static final float FP_MULT = 0.7f;
	public static final float ROUTE_STRENGTH_MULT = 1/FP_MULT;
	public static final List<String> NO_ALLY_SHARE_FACTIONS = new ArrayList<>(Arrays.asList("tahlan_legioinfernalis"));
	
	public static Logger log = Global.getLogger(OffensiveFleetIntel.class);
	
	@Getter protected MarketAPI from;
	@Getter protected MarketAPI target;
	@Getter protected FactionAPI targetFaction;
	@Getter @Setter protected FactionAPI proxyForFaction;
	@Getter @Setter protected OffensiveOutcome outcome;
	@Getter protected boolean isRespawn = false;
	protected boolean intelQueuedOrAdded;
	protected boolean outcomeUpdateSent;
	@Getter protected boolean playerSpawned;	// was this fleet spawned by player fleet request?
	@Getter @Setter protected int playerFee;
	@Getter @Setter protected int invPointsSpent;
	@Getter @Setter protected int fleetPoolPointsSpent;
	@Getter @Setter protected int groundPoolPointsSpent;
	@Getter protected float fp;
	@Getter protected float baseFP;
	protected float orgDur;
	protected boolean useMarketFleetSizeMult = InvasionFleetManager.USE_MARKET_FLEET_SIZE_MULT;
	protected boolean requiresSpaceportOrBase = true;
	@Getter @Setter protected Float qualityOverride = null;
	@Getter @Setter protected StrategicAction strategicAction;
	
	@Getter @Setter protected boolean abortIfNonHostile = true;
	
	protected boolean brawlMode;
	protected float brawlMult = -1;
	protected boolean reportedRaid = false;
		
	protected Set<RouteData> alreadyActionedRoutes = new HashSet<>();
	
	protected ActionStage action;
	
	public static enum OffensiveOutcome {
		TASK_FORCE_DEFEATED,
		NOT_ENOUGH_REACHED,
		MARKET_NO_LONGER_EXISTS,
		RETREAT_BEFORE_ACTION,
		SUCCESS,
		FAIL,
		NO_LONGER_HOSTILE,
		OTHER;
		
		public boolean isFailed() {
			return this == TASK_FORCE_DEFEATED || this == FAIL || this == RETREAT_BEFORE_ACTION;
		}

		public boolean isCancelled() {
			return this == MARKET_NO_LONGER_EXISTS || this == NOT_ENOUGH_REACHED || this == NO_LONGER_HOSTILE || this == OTHER;
		}
	}
	
	public OffensiveFleetIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(target.getStarSystem(), attacker, null);
		
		this.target = target;
		this.delegate = this;
		this.from = from;
		this.fp = fp * FP_MULT;
		baseFP = fp;
		this.orgDur = orgDur;
		targetFaction = target.getFaction();
	}
	
	protected Object readResolve() {
		if (alreadyActionedRoutes == null) {
			alreadyActionedRoutes = new HashSet<>();
		}
		
		return this;
	}
	
	public void init() {
	}
	
	public void setSilent() {
		this.intelQueuedOrAdded = true;
	}
	
	public boolean isUsingMarketSizeMult() {
		return useMarketFleetSizeMult;
	}
	
	public boolean isBrawlMode() {
		return brawlMode;
	}
	
	public void setBrawlMode(boolean brawl) {
		brawlMode = brawl;
	}
	
	public void setRequiresSpaceportOrBase(boolean requiresSpaceport) {
		this.requiresSpaceportOrBase = requiresSpaceport;
	}
	
	public boolean isRequiresSpaceportOrBase() {
		return requiresSpaceportOrBase;
	}

	/**
	 * If true, call raid listeners' {@code reportRaidEnded} on outcome being set in {@code reportOutcome}. If not, wait for {@code notifyRaidEnded}.
	 * @return
	 */
	public boolean shouldCallListenerOnOutcome() { return true; }
	
	public RouteData getRouteFromFleet(CampaignFleetAPI fleet) {
		if (fleet == null) return null;
		RouteData route = (RouteData)fleet.getMemoryWithoutUpdate().get("$nex_routeData");
		return route;
	}
	
	/**
	 * Has this fleet already conducted its raid action?<br/>
	 * Note: Only some raid actions bother setting this. Can be true from the start
	 * for fleets that should not take action, such as the strike fleets in a brawl mode invasions.
	 * @param route
	 * @return
	 */
	public boolean isRouteActionDone(RouteData route) {
		if (route == null) return false;
		// reverse compat safety
		if (alreadyActionedRoutes == null) {
			alreadyActionedRoutes = new HashSet<>();
		}
		return alreadyActionedRoutes.contains(route);
	}
	
	/**
	 * Has this fleet already conducted its raid action?<br/>
	 * Note: Only some raid actions bother setting this. Can be true from the start
	 * for fleets that should not take action, such as the strike fleets in a brawl mode invasions.
	 * @param fleet
	 * @return
	 */
	public boolean isRouteActionDone(CampaignFleetAPI fleet) {
		if (fleet.getMemoryWithoutUpdate().getBoolean(MEM_KEY_ACTION_DONE)) return true;
		return isRouteActionDone(getRouteFromFleet(fleet));
	}
	
	/**
	 * Call this when a route conducts its raid action.<br/>
	 * Autoresolve might call this twice per route; from when the route's active fleet 
	 * (if it exists) performs the raid, and then for the route itself.
	 * @param route
	 */
	public void setRouteActionDone(RouteData route) {
		if (route == null) return;
		// reverse compat safety
		if (alreadyActionedRoutes == null) {
			alreadyActionedRoutes = new HashSet<>();
		}
		alreadyActionedRoutes.add(route);
		if (route.getActiveFleet() != null)
			route.getActiveFleet().getMemoryWithoutUpdate().set(MEM_KEY_ACTION_DONE, true);
	}
	
	/**
	 * Call this when a fleet conducts its raid action.
	 * @param fleet
	 */
	public void setRouteActionDone(CampaignFleetAPI fleet) {
		RouteData route = getRouteFromFleet(fleet);
		setRouteActionDone(route);
	}
	
	public boolean shouldMakeImportantIfTargetingPlayer() {
		return false;
	}
	
	protected void queueIntelIfNeeded()
	{
		if (intelQueuedOrAdded) return;
		if (faction.isPlayerFaction())
			Global.getSector().getIntelManager().addIntel(this);
		else
			Global.getSector().getIntelManager().queueIntel(this);
		intelQueuedOrAdded = true;
	}
	
	protected void addIntelIfNeeded()
	{
		if (intelQueuedOrAdded) return;
		if (shouldMakeImportantIfTargetingPlayer() 
				&& (targetFaction.isPlayerFaction() || targetFaction == PlayerFactionStore.getPlayerFaction()))
			setImportant(true);
		Global.getSector().getIntelManager().addIntel(this);
		intelQueuedOrAdded = true;
	}
	
	protected boolean shouldDisplayIntel()
	{
		if (INTEL_ALWAYS_VISIBLE) return true;
		if (Global.getSettings().isDevMode()) return true;
		LocationAPI loc = from.getContainingLocation();
		if (faction.isPlayerFaction()) return true;		
		if (AllianceManager.areFactionsAllied(faction.getId(), PlayerFactionStore.getPlayerFactionId()))
			return true;
		
		List<SectorEntityToken> sniffers = Global.getSector().getIntel().getCommSnifferLocations();
		for (SectorEntityToken relay : sniffers)
		{
			if (relay.getContainingLocation() == loc)
				return true;
		}
		return false;
	}
	
	public OffensiveOutcome getOutcome() {
		return outcome;
	}
	
	public float getFP() {
		return fp;
	}
	
	public float getBaseFP() {
		return baseFP;
	}
	
	public MarketAPI getMarketFrom() {
		return from;
	}
	
	public MarketAPI getTarget() {
		return target;
	}

	public abstract String getType();
	
	@Override
	public void notifyRaidEnded(RaidIntel raid, RaidStageStatus status) {
		if (ExerelinModPlugin.isNexDev) {
			Global.getSector().getCampaignUI().addMessage("notifyRaidEnded() called for " + getName());
		}
		if (outcome == null) {
			if (status == RaidStageStatus.SUCCESS)
				outcome = OffensiveOutcome.SUCCESS;
			else
				outcome = OffensiveOutcome.FAIL;
		}
		
		if (outcome.isFailed())
		{
			float impact = fp/2;
			if (this.getCurrentStage() >= 2) impact *= 2;
			DiplomacyManager.getManager().modifyWarWeariness(faction.getId(), impact);
		}

		reportRaidIfNeeded();
	}

	@Override
	protected void notifyEnding() {
		refundInvasionAndFleetPoints();
		refundPlayerFeeIfNeeded();
	}

	@Override
	public boolean shouldSendUpdate() {
		// FIXME: super version always sends updates if player is targeted,
		// maybe we should also make it obey NexIntelQueued level 2?
		
		if (this.outcome != null)
			return true;
		return super.shouldSendUpdate();
	}

	public void sendOutcomeUpdate() {
		if (outcomeUpdateSent) {
			if (ExerelinModPlugin.isNexDev) {
				Global.getSector().getCampaignUI().addMessage("sendOutcomeUpdate() called twice for " + getName());
			}
			return;
		}
		addIntelIfNeeded();
		sendUpdateIfPlayerHasIntel(OUTCOME_UPDATE, false);
		outcomeUpdateSent = true;
	}
	
	public void sendEnteredSystemUpdate() {
		queueIntelIfNeeded();
		sendUpdateIfPlayerHasIntel(ENTERED_SYSTEM_UPDATE, false);
	}
	
	@Override
	public void sendUpdateIfPlayerHasIntel(Object listInfoParam, boolean onlyIfImportant, boolean sendIfHidden) {
		if (listInfoParam == UPDATE_RETURNING && !(action instanceof NexRaidActionStage)) {
			// we're using sendOutcomeUpdate() to send an end-of-event update instead
			return;
		}
		super.sendUpdateIfPlayerHasIntel(listInfoParam, onlyIfImportant, sendIfHidden);
	}
	
	// override RaidIntel's primitive form of addBulletPoints
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
		float pad = 3f;
		float opad = 10f;
		
		float initPad = pad;
		if (mode == ListInfoMode.IN_DESC) initPad = opad;
		
		Color tc = getBulletColorForMode(mode);
	
		boolean isUpdate = getListInfoParam() != null;
		
		bullet(info);
		addBulletPoints(info, mode, isUpdate, tc, initPad);
		unindent(info);
	}
	
	// for intel popup in campaign screen's message area
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		
		FactionAPI other = targetFaction;
		if (target != null) other = target.getFaction();	// target is null for colony expeditions
		if (other == faction) other = targetFaction;
		
		info.addPara(StringHelper.getString("faction", true) + ": " + faction.getDisplayName(), initPad, tc,
				 	 faction.getBaseUIColor(), faction.getDisplayName());
		initPad = 0f;
		
		if (outcome == null && !other.isNeutralFaction())
		{
			String str = StringHelper.getStringAndSubstituteToken("nex_fleetIntel",
					"bulletTarget", "$targetFaction", other.getDisplayName());
			info.addPara(str, initPad, tc,
						 other.getBaseUIColor(), other.getDisplayName());
		}
		
		if (getListInfoParam() == ENTERED_SYSTEM_UPDATE) {
			addArrivedBullet(info, tc, initPad);
			return;
		}
		
		if (outcome != null) {
			addOutcomeBullet(info, tc, initPad);
		} else {
			info.addPara(system.getNameWithLowercaseType(), tc, initPad);
			initPad = 0f;
			addETABullet(info, tc, Misc.getHighlightColor(), initPad);
		}
	}
	
	protected void addArrivedBullet(TooltipMakerAPI info, Color color, float pad) 
	{
		String str = StringHelper.getString("nex_fleetIntel", "bulletArrived");
		str = StringHelper.substituteToken(str, "$forceType", getForceType(), true);
		info.addPara(str, color, pad);
	}
	
	protected void addOutcomeBullet(TooltipMakerAPI info, Color color, float pad) 
	{
		String key = "bulletCancelled";
		switch (outcome) {
			case SUCCESS:
				key = "bulletSuccess";
				break;
			case TASK_FORCE_DEFEATED:
			case FAIL:
			case NOT_ENOUGH_REACHED:
				key = "bulletFailed";
				break;
			case RETREAT_BEFORE_ACTION:
				key = "bulletRetreated";
				break;
			case MARKET_NO_LONGER_EXISTS:
				key = "bulletNoLongerExists";
				break;
			case NO_LONGER_HOSTILE:
				key = "bulletNoLongerHostile";
				break;
		}
		//String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", 
		//		key, "$target", target.getName());
		//info.addPara(str, initPad, tc, other.getBaseUIColor(), target.getName());
		String str = StringHelper.getString("nex_fleetIntel", key);
		str = StringHelper.substituteToken(str, "$forceType", getForceType(), true);
		str = StringHelper.substituteToken(str, "$action", getActionName(), true);
		info.addPara(str, color, pad);
	}
	
	protected void addETABullet(TooltipMakerAPI info, Color color, Color hl, float pad) 
	{
		float eta = getETA();
		if (eta > 1 && failStage < 0) {
			String days = getDaysString(eta);
			String str = StringHelper.getStringAndSubstituteToken("nex_fleetIntel", "bulletETA", "$days", days);
			info.addPara(str, pad, color, hl, "" + (int)Math.round(eta));
		}
	}

	@Override
	public ActionStatus getStrategicActionStatus() {
		if (outcome != null) {
			if (outcome == OffensiveOutcome.SUCCESS) return ActionStatus.SUCCESS;
			else if (outcome == OffensiveOutcome.FAIL) return ActionStatus.FAILURE;
			else return ActionStatus.CANCELLED;
		}
		if (getCurrentStage() == 0) return ActionStatus.STARTING;
		if (isEnding() || isEnded()) {
			if (currentStage >= stages.size()) return ActionStatus.SUCCESS;
			RaidStage stage = stages.get(currentStage);
			RaidStageStatus status = stage.getStatus();
			return status == RaidStageStatus.SUCCESS ? ActionStatus.SUCCESS : ActionStatus.FAILURE;
		}
		return ActionStatus.IN_PROGRESS;
	}

	@Override
	public String getName() {
		String base = getBaseName();
		
		if (isEnding() || outcome != null) {
			if (outcome == OffensiveOutcome.SUCCESS) {
				return base + " - " + StringHelper.getString("successful", true);
			}
			else if (outcome != null && outcome.isFailed()) {
				return base + " - " + StringHelper.getString("failed", true);
			}
			return base + " - " + StringHelper.getString("over", true);
		}
		return base;
	}

	public String getBaseName() {
		String base = StringHelper.getString("nex_fleetIntel", "title");
		base = StringHelper.substituteToken(base, "$action", getActionName(), true);
		base = StringHelper.substituteToken(base, "$market", getTarget().getName());
		return base;
	}
	
	public String getActionName() {
		return StringHelper.getString("expedition");
	}
	
	public String getActionNameWithArticle() {
		return StringHelper.getString("theExpedition");
	}
	
	public String getForceType() {
		return StringHelper.getString("force");
	}
	
	public String getForceTypeWithArticle() {
		return StringHelper.getString("theForce");
	}
	
	public String getForceTypeIsOrAre() {
		return StringHelper.getString("is");
	}
	
	public String getForceTypeHasOrHave() {
		return StringHelper.getString("has");
	}

	public boolean isPlayerSpawned() {
		return playerSpawned;
	}
	
	public void setPlayerSpawned(boolean playerSpawned) {
		this.playerSpawned = playerSpawned;
	}

	public void reportOutcome(OffensiveOutcome outcome) {
		this.outcome = outcome;
		if (this.shouldCallListenerOnOutcome()) {
			reportRaidIfNeeded();
		}
	}
		
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		//tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		tags.remove(Tags.INTEL_COLONIES);
		if (targetFaction.isPlayerFaction()) {
			tags.add(Tags.INTEL_COLONIES);
		}
		tags.add(getFaction().getId());
		tags.add(target.getFactionId());
		return tags;
	}

	protected void refundInvasionAndFleetPoints() {
		// 0 = organize; 1 = assemble; 2 = travel, 3 = action
		if (outcome != null && !outcome.isCancelled()) return;

		float refundMult = 0;
		if (currentStage <= 1) refundMult = 1;
		else if (currentStage == 2) refundMult = 0.67f;
		else if (currentStage == 3) refundMult = 0.33f;

		try {
			String fid = proxyForFaction != null ? proxyForFaction.getId() : faction.getId();
			InvasionFleetManager.getManager().modifySpawnCounterV2(fid, invPointsSpent);
			FleetPoolManager.getManager().modifyPool(fid, fleetPoolPointsSpent);
			GroundPoolManager.getManager().modifyPool(fid, groundPoolPointsSpent);
		} catch (NullPointerException npe) {
			// do nothing
		}
	}

	protected void refundPlayerFeeIfNeeded() {
		// 0 = organize; 1 = assemble; 2 = travel, 3 = action
		if (playerFee <= 0) return;
		if (outcome != null && !outcome.isCancelled()) return;

		float refundMult = 0;
		if (currentStage <= 1) refundMult = 1;
		else if (currentStage == 2) refundMult = 0.67f;
		else if (currentStage == 3) refundMult = 0.33f;

		if (refundMult <= 0) return;
		int refund = Math.round(playerFee * refundMult);
		log.info(String.format("Refunding %s credits for action cancellation (mult %.1f)", refund, refundMult));
		Global.getSector().getPlayerFleet().getCargo().getCredits().add(refund);
	}
		
	public void terminateEvent(OffensiveOutcome outcome)
	{
		reportOutcome(outcome);
		forceFail(true);
	}
	
	public void checkForTermination() {
		if (outcome != null) return;
		
		// source captured before launch
		if (getCurrentStage() <= 0 && from.getFaction() != faction) {
			terminateEvent(OffensiveOutcome.FAIL);
		}
		else if (!target.isInEconomy()) {
			terminateEvent(OffensiveOutcome.MARKET_NO_LONGER_EXISTS);
		}
		else if (abortIfNonHostile && !faction.isHostileTo(target.getFaction())) {
			terminateEvent(OffensiveOutcome.NO_LONGER_HOSTILE);
		}
	}
	
	// check if market should still be attacked
	@Override
	protected void advanceImpl(float amount) {
		checkForTermination();
		super.advanceImpl(amount);
	}
	
	// send fleets home
	@Override
	protected void failedAtStage(RaidStage stage) {
		BaseRaidStage stage2 = (BaseRaidStage)stage;
		stage2.giveReturnOrdersToStragglers(stage2.getRoutes());
	}
	
	// disregard market fleet size mult if needed
	@Override
	public float getRaidFPAdjusted() {
		if (useMarketFleetSizeMult)
			return super.getRaidFPAdjusted();
				
		float raidFP = getRaidFP();
		float raidStr = raidFP * InvasionFleetManager.getFactionDoctrineFleetSizeMult(faction);
		return raidStr;
	}
	
	public float getWantedFreighterFP(float baseFP, Random random) {
		return baseFP * (0.15f + random.nextFloat() * 0.05f);
	}
	
	public float getWantedTankerFP(float baseFP, float distance, Random random) {
		float tanker = baseFP * (0.1f + random.nextFloat() * 0.05f)
				+ baseFP * TANKER_FP_PER_FLEET_FP_PER_10K_DIST * distance/10000;
		if (tanker > baseFP * 0.2f) tanker = baseFP * 0.2f;
		
		return tanker;
	}
	
	@Override
	public float getRaidStr() {
		if (useMarketFleetSizeMult)
			return super.getRaidStr();
		
		MarketAPI source = getFirstSource();
		
		float raidStr = getRaidFPAdjusted();
		raidStr *= Math.max(0.25f, 0.5f + Math.min(1f, Misc.getShipQuality(source)));
		
		float pts = faction.getDoctrine().getOfficerQuality();
		raidStr *= 1f + (pts - 1f) / 4f;
		
		return raidStr;
	}
	
	// same as superclass, except with randomly using ally factions
	@Override
	public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
		Random random = route.getRandom();
		
		MarketAPI market = route.getMarket();
		String factionId = faction.getId();
		// randomly use an ally faction's fleet if applicable
		Alliance alliance = AllianceManager.getFactionAlliance(factionId);
		if (brawlMode && random.nextFloat() < Global.getSettings().getFloat("nex_brawlMode_randomFactionGearChance")) 
		{
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
			picker.addAll(SectorManager.getLiveFactionIdsCopy());
			factionId = picker.pick();
			log.info("Brawl mode: Using gear from faction " + factionId);
		}
		else if (alliance != null && random.nextFloat() < ALLY_GEAR_CHANCE) {
			Set<String> allyFactions = alliance.getMembersCopy();
			allyFactions.removeAll(NO_ALLY_SHARE_FACTIONS);
			if (!allyFactions.isEmpty()) {
				WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
				picker.addAll(allyFactions);
				factionId = picker.pick();
				log.info("Using allied gear from faction " + factionId);
			}

		}
		
		CampaignFleetAPI fleet = createFleet(factionId, route, market, null, random);
		
		if (fleet == null || fleet.isEmpty()) return null;
		fleet.setFaction(faction.getId(), true);	// added here, not in super
		handleAllyFleetNaming(fleet, factionId, random);
		
		//fleet.addEventListener(this);
		if (isRouteActionDone(route)) {
			fleet.getMemoryWithoutUpdate().set(MEM_KEY_ACTION_DONE, true);
		}
		if (proxyForFaction != null) {
			fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE + "_" + proxyForFaction.getId(), true);
		}

		
		market.getContainingLocation().addEntity(fleet);
		fleet.setFacing((float) Math.random() * 360f);
		// this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
		fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
		
		fleet.addScript(createAssignmentAI(fleet, route));
		
		return fleet;
	}
	
	protected void handleAllyFleetNaming(CampaignFleetAPI fleet, String sourceFactionId, Random rand) {
		if (sourceFactionId.equals(fleet.getFaction().getId()))
			return;
		
		FactionAPI source = Global.getSector().getFaction(sourceFactionId);
		FactionAPI curr = fleet.getFaction();
		
		fleet.setName(fleet.getName() + " (" + Misc.ucFirst(source.getPersonNamePrefix()) + ")");
		
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			member.setShipName(curr.pickRandomShipName(rand));
		}
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		super.createSmallDescription(info, width, height);
		addStrategicActionInfo(info, width);
	}

	@Override
	public void addStandardStrengthComparisons(TooltipMakerAPI info, MarketAPI target, FactionAPI targetFaction, boolean withGround,
											   boolean withBombard, String raid, String raids) {
		float pad = 3, opad = 10;
		Color hl = Misc.getHighlightColor();
		//super.addStandardStrengthComparisons(info, target, targetFaction, withGround, withBombard, raid, raids);
		info.addPara(StringHelper.getString("nex_fleetIntel", "strCompareV2Header"), opad);

		bullet(info);

		FactionStrengthReport attackerRpt = NexWarSimScript.getFactionStrengthReport(faction, target.getStarSystem());
		FactionStrengthReport defenderRpt = NexWarSimScript.getFactionStrengthReport(targetFaction, target.getStarSystem());
		float raidStr = getRaidStr();
		float attackerStr = attackerRpt.totalStrength + raidStr;
		float stationStr = WarSimScript.getStationStrength(targetFaction, system, target.getPrimaryEntity());
		float defenderStr = NexWarSimScript.getFactionAndAlliedStrength(targetFaction, this.faction, system);
		float defenderStr2 = defenderRpt.totalStrength + stationStr;

		String spaceStr = "";
		String spaceStr2 = "";
		String groundStr = "";

		int spaceWin = 0;
		int spaceWin2 = 0;
		int groundWin = 0;

		// our raid strength vs. immediate defenders
		if (raidStr < defenderStr * 0.75f) {
			spaceStr = StringHelper.getString("outmatched");
			spaceWin = -1;
		} else if (raidStr < defenderStr * 1.25f) {
			spaceStr = StringHelper.getString("evenlyMatched");
		} else {
			spaceStr = StringHelper.getString("superior");
			spaceWin = 1;
		}
		{
			String bullet = StringHelper.getStringAndSubstituteToken("nex_fleetIntel", "strCompareSpace",
					"$Action", Misc.ucFirst(getActionName()));
			bullet += ": " + spaceStr;
			info.addPara(bullet, 0, hl, spaceStr);
		}

		if (attackerStr < defenderStr2 * 0.75f) {
			spaceStr2 = StringHelper.getString("outmatched");
			spaceWin2 = -1;
		} else if (attackerStr < defenderStr2 * 1.25f) {
			spaceStr2 = StringHelper.getString("evenlyMatched");
		} else {
			spaceStr2 = StringHelper.getString("superior");
			spaceWin2 = 1;
		}
		{
			String bullet = StringHelper.getStringAndSubstituteToken("nex_fleetIntel", "strCompareSpace2",
					"$Action", Misc.ucFirst(getActionName()));
			bullet += ": " + spaceStr2;
			info.addPara(bullet, 0, hl, spaceStr2);
		}

		float re = getEstimatedRaidEffectiveness(target);
		if (re < 0.33f) {
			groundStr = StringHelper.getString("outmatched");
			groundWin = -1;
		} else if (re < 0.66f) {
			groundStr = StringHelper.getString("evenlyMatched");
		} else {
			groundStr = StringHelper.getString("superior");
			groundWin = 1;
		}
		{
			String bullet = StringHelper.getStringAndSubstituteToken("nex_fleetIntel", "strCompareGround",
					"$Action", Misc.ucFirst(getActionName()));
			bullet += ": " + groundStr;
			info.addPara(bullet, 0, hl, groundStr);
		}

		unindent(info);

		String key = "Successful";
		if (spaceWin == -1)
			key = "DefeatInOrbit";
		else if (groundWin == -1)
			key = "DefeatOnGround";
		else if (spaceWin < 1 || groundWin < 1)
			key = "Uncertain";
		String outcomeDesc = StringHelper.getString("nex_fleetIntel", "prediction" + key);
		outcomeDesc = StringHelper.substituteToken(outcomeDesc, "$theAction", getActionNameWithArticle(), true);
		info.addPara(outcomeDesc, pad);

		addDebugStrengthBreakdown(info, target, targetFaction);
		printFleetCountDebug(info);
	}

	public float getEstimatedRaidEffectiveness(MarketAPI target) {
		float raidFP = getRaidFPAdjusted() / getNumFleets();
		float assumedRaidGroundStr = raidFP * Misc.FP_TO_GROUND_RAID_STR_APPROX_MULT;
		float re = MarketCMD.getRaidEffectiveness(target, assumedRaidGroundStr);

		return re;
	}
	
	public void printFleetCountDebug(TooltipMakerAPI info) {
		if (ExerelinModPlugin.isNexDev) {
			int predicted = (int)Math.round(getOrigNumFleets());
			int active = getRouteCount();
			int total = getRouteCountAll();
			String str = String.format("DEBUG: Expected routes %s, active %s, total %s", predicted, active, total);
			
			info.addPara(str, 10, Misc.getHighlightColor(), predicted + "", active + "", total + "");
		}
	}

	public void addDebugStrengthBreakdown(TooltipMakerAPI info, MarketAPI target, FactionAPI targetFaction) {
		if (!ExerelinModPlugin.isNexDev) return;
		float pad = 3, opad = 10;

		FactionStrengthReport attacker = getFactionStrengthReport(faction, target.getStarSystem());
		FactionStrengthReport defender = getFactionStrengthReport(targetFaction, target.getStarSystem());

		info.addPara("Attacker breakdown", faction.getBaseUIColor(), opad);
		bullet(info);
		Set<Object> seenRoutesOrFleets = new HashSet<>();
		for (FactionStrengthReportEntry entry : attacker.entries) {
			printDebugStrengthRow(info, entry.name, entry.strength);
			if (entry.fleet != null) seenRoutesOrFleets.add(entry.fleet);
			else seenRoutesOrFleets.add(entry.route);
		}
		// add the routes from this intel, if they're not already in-system
		for (RouteData route : getRoutes()) {
			if (seenRoutesOrFleets.contains(route)) continue;
			CampaignFleetAPI fleet = route.getActiveFleet();
			if (fleet != null && seenRoutesOrFleets.contains(fleet)) continue;

			if (fleet != null) {
				printDebugStrengthRow(info, fleet.getFullName(), fleet.getEffectiveStrength());
			} else {
				RouteManager.OptionalFleetData data = route.getExtra();
				if (data == null) continue;
				if (data.strength != null) {
					float mult = 1f;
					if (data.damage != null) mult *= (1f - data.damage);
					printDebugStrengthRow(info, "Route " + route.toString(), (float)Math.round(data.strength * mult));
				}
			}
		}
		unindent(info);

		info.addPara("Defender breakdown", targetFaction.getBaseUIColor(),  opad);
		bullet(info);
		for (FactionStrengthReportEntry entry : defender.entries) {
			printDebugStrengthRow(info, entry.name, entry.strength);
		}
		unindent(info);
	}

	protected void printDebugStrengthRow(TooltipMakerAPI info, String name, float strength) {
		String strengthStr = String.format("%.1f", strength);
		info.addPara(String.format("%s: %s", name, strengthStr), 0, Misc.getHighlightColor(), strengthStr);
	}

	protected void addStrategicActionInfo(TooltipMakerAPI info, float width) {
		if (strategicAction == null) return;
		info.addPara(StrategicAI.getString("intelPara_actionDelegateDesc"), 10, Misc.getHighlightColor(), strategicAction.getConcern().getName());
		info.addButton(StrategicAI.getString("btnGoIntel"), StrategicActionDelegate.BUTTON_GO_INTEL, width, 24, 3);
	}
	
	protected int getRouteCount() {
		int currStage = getCurrentStage();
		BaseRaidStage stage = (BaseRaidStage) stages.get(currStage);
		
		return stage.getRoutes().size();
	}

	protected List<RouteData> getRoutes() {
		int currStage = getCurrentStage();
		BaseRaidStage stage = (BaseRaidStage) stages.get(currStage);

		return stage.getRoutes();
	}

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == StrategicActionDelegate.BUTTON_GO_INTEL && strategicAction != null) {
			Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.INTEL, strategicAction.getAI());
		}
	}

	/**
	 * Includes stragglers, unlike the regular route count.
	 * @return
	 */
	protected int getRouteCountAll() {
		List<RouteData> routes = RouteManager.getInstance().getRoutesForSource(this.getRouteSourceId());
		return routes.size();
	}
	
	public boolean anyActionRoutesHaveLiveFleets() {
		if (action == null || action.getRoutes() == null) return false;
		
		for (RouteData route : action.getRoutes()) {
			if (route.getActiveFleet() != null) return true;
		}
		return false;
	}

	/**
	 * @return False for fleet events that do not have an originating market (e.g. Remnant raids), true otherwise.
	 */
	public boolean hasMarket() {
		return true;
	}

	public InvasionFleetManager.EventType getEventType() {
		return InvasionFleetManager.EventType.RAID;
	}

	@Override
	public float getStrategicActionDaysRemaining() {
		return getETA();
	}

	@Override
	public void abortStrategicAction() {
		terminateEvent(OffensiveOutcome.OTHER);
	}

	@Override
	public String getStrategicActionName() {
		return getBaseName();
	}

	protected void reportRaidIfNeeded() {
		if (reportedRaid) return;
		reportedRaid = true;
		reportRaid(this);
	}

	public static void reportRaid(OffensiveFleetIntel intel)
	{
		log.info("Reporting raid: " + intel.getName());
		for (RaidListener x : Global.getSector().getListenerManager().getListeners(RaidListener.class)) {
			x.reportRaidEnded(intel, intel.faction, intel.targetFaction, intel.target, intel.outcome == OffensiveOutcome.SUCCESS);
		}
	}
}
