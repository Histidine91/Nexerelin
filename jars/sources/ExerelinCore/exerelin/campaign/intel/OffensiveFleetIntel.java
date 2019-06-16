package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import static com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin.getDaysString;
import com.fs.starfarer.api.impl.campaign.intel.raid.ActionStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel.RaidDelegate;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;

public abstract class OffensiveFleetIntel extends RaidIntel implements RaidDelegate {
	
	public static final Object ENTERED_SYSTEM_UPDATE = new Object();
	public static final Object OUTCOME_UPDATE = new Object();
	public static final boolean DEBUG_MODE = true;
	public static final boolean INTEL_ALWAYS_VISIBLE = true;
	public static final float ALLY_GEAR_CHANCE = 0.5f;
	
	public static Logger log = Global.getLogger(OffensiveFleetIntel.class);
	
	protected MarketAPI from;
	protected MarketAPI target;
	protected FactionAPI targetFaction;
	protected OffensiveOutcome outcome;
	protected boolean isRespawn = false;
	protected boolean intelQueuedOrAdded;
	protected boolean playerSpawned;	// was this fleet spawned by player fleet request?
	protected float fp;
	protected float orgDur;
	protected boolean reported = false;
	
	protected ActionStage action;
	
	public static enum OffensiveOutcome {
		TASK_FORCE_DEFEATED,
		NOT_ENOUGH_REACHED,
		MARKET_NO_LONGER_EXISTS,
		SUCCESS,
		FAIL,
		NO_LONGER_HOSTILE,
		OTHER;
		
		public boolean isFailed() {
			return this == TASK_FORCE_DEFEATED || this == FAIL;
		}
	}
	
	public OffensiveFleetIntel(FactionAPI attacker, MarketAPI from, MarketAPI target, float fp, float orgDur) {
		super(target.getStarSystem(), attacker, null);
		
		this.target = target;
		this.delegate = this;
		this.from = from;
		this.target = target;
		this.fp = fp;
		this.orgDur = orgDur;
		targetFaction = target.getFaction();
	}
	
	public void init() {
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
		if (targetFaction.isPlayerFaction() || targetFaction == PlayerFactionStore.getPlayerFaction())
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
	
	public MarketAPI getMarketFrom() {
		return from;
	}
	
	public MarketAPI getTarget() {
		return target;
	}
	
	@Override
	public void notifyRaidEnded(RaidIntel raid, RaidStageStatus status) {
		log.info("Notifying raid ended: " + status + ", " + outcome);
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
	}

	public void sendOutcomeUpdate() {
		addIntelIfNeeded();
		if (!reported) {
			sendUpdateIfPlayerHasIntel(OUTCOME_UPDATE, false);
			reported = true;
		}
	}
	
	public void sendEnteredSystemUpdate() {
		queueIntelIfNeeded();
		sendUpdateIfPlayerHasIntel(ENTERED_SYSTEM_UPDATE, false);
	}
	
	@Override
	public void sendUpdateIfPlayerHasIntel(Object listInfoParam, boolean onlyIfImportant, boolean sendIfHidden) {
		if (listInfoParam == UPDATE_RETURNING) {
			// we're using sendOutcomeUpdate() to send an end-of-event update instead
			return;
		}
		super.sendUpdateIfPlayerHasIntel(listInfoParam, onlyIfImportant, sendIfHidden);
	}
	
	// for intel popup in campaign screen's message area
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		float pad = 3f;
		float opad = 10f;
		
		float initPad = pad;
		if (mode == ListInfoMode.IN_DESC) initPad = opad;
		
		Color tc = getBulletColorForMode(mode);
		
		bullet(info);
		
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
		}
		initPad = 0f;
		addETABullet(info, tc, h, initPad);
		
		unindent(info);
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
	public String getName() {
		String base = StringHelper.getString("nex_fleetIntel", "title");
		base = StringHelper.substituteToken(base, "$action", getActionName(), true);
		base = StringHelper.substituteToken(base, "$market", getTarget().getName());
		
		if (isEnding()) {
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

	public void setOutcome(OffensiveOutcome outcome) {
		this.outcome = outcome;
	}

	public boolean isPlayerSpawned() {
		return playerSpawned;
	}
	
	public void setPlayerSpawned(boolean playerSpawned) {
		this.playerSpawned = playerSpawned;
	}
		
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		//tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		if (targetFaction.isPlayerFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(getFaction().getId());
		tags.add(target.getFactionId());
		return tags;
	}
		
	public void terminateEvent(OffensiveOutcome outcome)
	{
		setOutcome(outcome);
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
		else if (!faction.isHostileTo(target.getFaction())) {
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
		if (InvasionFleetManager.USE_MARKET_FLEET_SIZE_MULT)
			return super.getRaidFPAdjusted();
				
		float raidFP = getRaidFP();
		float raidStr = raidFP * InvasionFleetManager.getFactionDoctrineFleetSizeMult(faction);
		return raidStr;
	}
	
	@Override
	public float getRaidStr() {
		if (InvasionFleetManager.USE_MARKET_FLEET_SIZE_MULT)
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
		String factionId = market.getFactionId();
		// randomly use an ally faction's fleet if applicable
		Alliance alliance = AllianceManager.getFactionAlliance(factionId);
		if (alliance != null && random.nextFloat() < ALLY_GEAR_CHANCE) {
			WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
			picker.addAll(alliance.getMembersCopy());
			factionId = picker.pick();
			log.info("Using allied gear from faction " + factionId);
		}
		
		CampaignFleetAPI fleet = createFleet(factionId, route, market, null, random);
		
		if (fleet == null || fleet.isEmpty()) return null;
		fleet.setFaction(market.getFactionId(), true);	// also changed from super
		handleAllyFleetNaming(fleet, factionId, random);
		
		//fleet.addEventListener(this);
		
		market.getContainingLocation().addEntity(fleet);
		fleet.setFacing((float) Math.random() * 360f);
		// this will get overridden by the patrol assignment AI, depending on route-time elapsed etc
		fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().x);
		
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
}
