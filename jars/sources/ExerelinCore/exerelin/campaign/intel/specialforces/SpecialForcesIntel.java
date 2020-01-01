package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.SpecialForcesTask;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.TaskType;
import exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.util.vector.Vector2f;

public class SpecialForcesIntel extends BaseIntelPlugin implements RouteFleetSpawner 
{
	public static Logger log = Global.getLogger(SpecialForcesIntel.class);
	
	public static final String SOURCE_ID = "nex_specialForces";
	public static final String FLEET_TYPE = "nex_specialForces";
	protected static final String BUTTON_DEBUG = "debug";
	public static final Object ENDED_UPDATE = new Object();
	public static final Object NEW_ORDERS_UPDATE = new Object();
	public static final float DAMAGE_TO_REBUILD = 0.4f;
	public static final float DAMAGE_TO_TERMINATE = 0.9f;
	
	protected MarketAPI origin;
	protected MarketAPI lastSpawnedFrom;	// updated when fleet rebuilds
	protected FactionAPI faction;
	protected float startingFP;		// as stored in route extra data
	protected float trueStartingFP;	// from actually generated fleet; reset on fleet rebuild
	protected RouteData route;
	protected long spawnSeed = new Random().nextLong();
	protected String fleetName;
	protected SpecialForcesRouteAI routeAI;
	protected IntervalUtil interval = new IntervalUtil(0.2f, 0.3f);
	
	// These are preserved between fleet regenerations
	protected PersonAPI commander;
	protected FleetMemberAPI flagship;
	
	protected float rebuildCheckCooldown = 0;
	
	protected transient InteractionDialogAPI debugDialog;
	
	/*runcode 
	MarketAPI market = Global.getSector().getEconomy().getMarket("culann");
	FactionAPI faction = market.getFaction();
	new exerelin.campaign.intel.specialforces.SpecialForcesIntel(market, faction, 300).init(null);
	*/

	public SpecialForcesIntel(MarketAPI origin, FactionAPI faction, float startingFP) 
	{
		this.origin = origin;
		this.faction = faction;
		this.startingFP = startingFP;
		lastSpawnedFrom = origin;
	}
	
	public void init(PersonAPI commander) {
		this.commander = commander;
		
		OptionalFleetData extra = new OptionalFleetData(origin);
		extra.factionId = faction.getId();
		extra.fp = startingFP;
		extra.fleetType = FLEET_TYPE;
		route = RouteManager.getInstance().addRoute(SOURCE_ID, origin, spawnSeed, extra, this);
		routeAI = new SpecialForcesRouteAI(this);
		routeAI.addInitialTask();
		generateFlagshipAndCommanderIfNeeded(route);
		
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
	}
	
	public RouteData getRoute() {
		return route;
	}
	
	public SpecialForcesRouteAI getRouteAI() {
		return routeAI;
	}
	
	// Create fleet and add to star system/hyperspace when player is near
	@Override
	public CampaignFleetAPI spawnFleet(RouteData route) 
	{
		MarketAPI market = route.getMarket();
		CampaignFleetAPI fleet = createFleet(route);
		
		if (fleet == null || fleet.isEmpty()) return null;
		
		market.getContainingLocation().addEntity(fleet);
		fleet.setFacing((float) Math.random() * 360f);
		// this will get overridden by the assignment AI, depending on route-time elapsed etc
		fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
		
		fleet.addScript(createAssignmentAI(fleet, route));
		
		trueStartingFP = fleet.getFleetPoints();
		return fleet;
	}
	
	/**
	 * Generates fleet params and makes a fleet from them. May not actually do anything with the fleet.
	 * @param thisRoute
	 * @param seed
	 * @return
	 */
	public CampaignFleetAPI createFleetFromParams(RouteData thisRoute, long seed) 
	{
		String factionId = faction.getId();
		ExerelinFactionConfig conf = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (conf.factionIdForHqResponse != null) 
			factionId = conf.factionIdForHqResponse;
		
		Float damage = thisRoute.getExtra().damage;
		if (damage == null) damage = 0f;		
		float fp = thisRoute.getExtra().fp * (1 - damage) * conf.specialForcesSizeMult
				* ExerelinConfig.specialForcesSizeMult;
		
		FleetParamsV3 params = new FleetParamsV3(
				lastSpawnedFrom,
				null, // locInHyper
				factionId,
				thisRoute.getQualityOverride(), // qualityOverride
				FLEET_TYPE,
				fp, // combatPts
				fp * 0.25f, // freighterPts 
				fp * 0.25f, // tankerPts
				fp * 0.1f, // transportPts
				0, // linerPts
				fp * 0.1f, // utilityPts
				0.25f
		);
		params.timestamp = thisRoute.getTimestamp();
		params.officerLevelBonus = 3;
		params.officerNumberMult = 1.5f;
		params.random = new Random(seed);
		params.ignoreMarketFleetSizeMult = true;
		//params.doNotPrune = true;
		
		return FleetFactoryV3.createFleet(params);
	}
	
	/**
	 * Generates a fleet for adding to star system/hyperspace.
	 * @param thisRoute
	 * @return
	 */
	public CampaignFleetAPI createFleet(RouteData thisRoute) 
	{
		CampaignFleetAPI fleet = createFleetFromParams(thisRoute, spawnSeed);
		if (fleet == null) return null;
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		
		replaceCommander(fleet, false);
		injectFlagship(fleet);
		
		syncFleet(fleet);
		
		if (fleetName == null) {
			fleetName = pickFleetName(fleet, origin, commander);
		}
		
		fleet.setName(faction.getFleetTypeName(FLEET_TYPE) + " – " + fleetName);
		fleet.setNoFactionInName(true);
		
		fleet.addEventListener(new SFFleetEventListener(this));
		
		return fleet;
	}
	
	/**
	 * Creates a flagship and a commander by grabbing them from a temporary fleet.
	 * @param thisRoute
	 */
	protected void generateFlagshipAndCommanderIfNeeded(RouteData thisRoute) {
		// generate commander and/or flagship from a temporary fleet, if needed
		if (commander == null || flagship == null) {
			CampaignFleetAPI temp = createFleetFromParams(thisRoute, new Random().nextLong());
			if (temp == null) return;
			
			if (commander == null) commander = temp.getCommander();
			if (flagship == null) flagship = temp.getFlagship();
		}
	}
	
	/**
	 * Replaces all fleet members and officers with those from a newly created fleet,
	 * then reinserts the commander and flagship.
	 */
	protected void rebuildFleet() {
		CampaignFleetAPI fleet = route.getActiveFleet();
		if (fleet == null)
			return;
		
		spawnSeed = new Random().nextLong();
		CampaignFleetAPI temp = createFleetFromParams(route, spawnSeed);
		if (temp == null) return;
		
		temp.inflateIfNeeded();	// needed to apply D-mods and such
		fleet.getFleetData().clear();
		for (FleetMemberAPI member : temp.getFleetData().getMembersListCopy())
		{
			temp.getFleetData().removeFleetMember(member);
			fleet.getFleetData().addFleetMember(member);
			if (!member.getCaptain().isDefault())
				fleet.getFleetData().addOfficer(member.getCaptain());
		}
				
		replaceCommander(fleet, false);
		injectFlagship(fleet);
		syncFleet(fleet);
		
		trueStartingFP = fleet.getFleetPoints();
	}
	
	protected boolean checkRebuild(float damage) {
		//log.info("Checking if " + fleetName + " needs rebuild");
		if (damage > DAMAGE_TO_TERMINATE) {
			log.info("Fleet took catastrophic damage, ending event");
			endEvent();
			return true;
		}
		else if (damage > DAMAGE_TO_REBUILD || flagship == null) {
			orderFleetRebuild(false);
			return true;
		}
		return false;
	}
	
	protected void syncFleet(CampaignFleetAPI fleet) {
		fleet.getFleetData().sort();
		fleet.getFleetData().setSyncNeeded();
		fleet.getFleetData().syncIfNeeded();
		fleet.forceSync();
	}
	
	/**
	 * Orders the task group to go to a suitable market to rebuild the fleet.
	 * @param force
	 */
	public void orderFleetRebuild(boolean force) {		
		if (rebuildCheckCooldown > 0 && !force) {
			return;
		}
		rebuildCheckCooldown = 10;
		
		if (routeAI.currentTask != null && routeAI.currentTask.type == TaskType.REBUILD) {
			debugMsg("Already have reconstitution order", false);
			return;
		}
		
		Float damage = route.getExtra().damage;
		if ((damage == null || damage <= 0) && flagship != null) {
			debugMsg("Fleet is undamaged and needs no reconstitution", false);
			return;
		}
		
		WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<>();
		Vector2f loc = getCurrentHyperspaceLoc();
		
		for (MarketAPI market : Misc.getFactionMarkets(faction)) {
			if (market.isHidden()) continue;
			if (!market.hasSpaceport()) continue;
			
			
			float dist = Misc.getDistance(loc, market.getLocationInHyperspace());
			if (dist < 2000) dist = 2000;
			if (dist > 25000) continue;
			float weight = market.getSize() * market.getSize();
			weight *= (market.getStabilityValue() + 5)/15;
			weight *= 2000/dist;
			if (market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY))
				weight *= 2;
			
			marketPicker.add(market, weight);
		}
		
		MarketAPI market = marketPicker.pick();
		if (market == null) {
			debugMsg("No market found for reconstitution", false);
			return;
		}
		debugMsg("Reconstituting fleet at " + market.getName(), false);
		
		float fpWanted = startingFP * damage;
		float time = 1 + fpWanted * 0.01f * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
		
		SpecialForcesTask task = new SpecialForcesTask(TaskType.REBUILD, 99999);
		task.market = market;
		task.time = time;
		//task.params.put("fpToResupply", fpWanted);
		task.system = market.getStarSystem();
		routeAI.assignTask(task);
	}
	
	public void executeRebuildOrder(MarketAPI market) {
		lastSpawnedFrom = market;
		Float damage = route.getExtra().damage;
		if (damage == null) damage = 0f;
		float fp = damage * startingFP;
		
		route.getExtra().damage = 0f;
		generateFlagshipAndCommanderIfNeeded(route);
		if (route.getActiveFleet() != null) {
			rebuildFleet();
		}
		SpecialForcesManager.getManager().incrementPoints(faction.getId(), -fp);
	}
	
	/**
	 * Inserts the persistent flagship into the specified fleet.
	 * @param fleet
	 */
	protected void injectFlagship(CampaignFleetAPI fleet) {
		if (flagship == null) return;
		if (fleet.getFleetData().getMembersListCopy().contains(flagship))
			return;	// no action needed
		
		fleet.getFleetData().addFleetMember(flagship);
		fleet.getFleetData().setFlagship(flagship);
		
		if (commander != null)
			flagship.setCaptain(commander);
	}
	
	/**
	 * Replaces the fleet's existing commander with the persistent one. 
	 * Does not assign them to captain a ship.
	 * @param fleet
	 * @param removeExisting
	 */
	protected void replaceCommander(CampaignFleetAPI fleet, boolean removeExisting) 
	{
		if (commander == null) return;
		if (fleet.getCommander() == commander) return;
		
		if (removeExisting) {
			PersonAPI origCommander = fleet.getCommander();
			fleet.getFleetData().removeOfficer(origCommander);
		}
		
		fleet.setCommander(commander);
		fleet.getFleetData().addOfficer(commander);
		
	}
	
	protected EveryFrameScript createAssignmentAI(CampaignFleetAPI fleet, RouteData route) {
		return new SpecialForcesAssignmentAI(this, fleet, route, null);
	}
	
	public Vector2f getCurrentHyperspaceLoc() {
		if (route.getActiveFleet() != null)
			return route.getActiveFleet().getLocationInHyperspace();
		else
			return route.getInterpolatedHyperLocation();
	}
	
	public FactionAPI getFaction() {
		return faction;
	}
	
	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		Color c = getTitleColor(mode);
		Color tc = getBulletColorForMode(mode);
		info.addPara(getSmallDescriptionTitle(), c, 0);
		if (isEnding() || isEnded()) return;
		if (routeAI.currentTask != null) {
			bullet(info);
			info.addPara(Misc.ucFirst(routeAI.currentTask.getText()), tc, 3);
			unindent(info);
		}
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		String str = getString("intelTitle");
		
		if (fleetName != null) {
			str += ": " + fleetName;
		}
		else
		{
			str = faction.getDisplayName() + " " + str;
		}
		
		if (isEnding() || isEnded()) {
			str += " - " + StringHelper.getString("over", true);
		}
		
		return str;
	}
	
	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		
		Color h = Misc.getHighlightColor();
		Color c = getFactionForUIColors().getBaseUIColor();
		Color d = getFactionForUIColors().getDarkUIColor();
		
		// Images
		if (commander != null) {
			info.addImages(width, 128, opad, opad, commander.getPortraitSprite(), faction.getCrest());
		}
		else {
			info.addImage(faction.getCrest(), 128, 128);
		}
		
		String str;
		
		// Event over?
		if (route == null || isEnding() || isEnded()) {
			str = getString("intelDescOver");
			str = StringHelper.substituteToken(str, "$faction", faction.getPersonNamePrefix());
			String fleetName = this.fleetName != null ? this.fleetName 
					: "<" + StringHelper.getString("unknown") + ">";
			str = StringHelper.substituteToken(str, "$fleetName", fleetName);

			info.addPara(str, opad);
			return;
		}
		
		// Intro paragraph
		str = getString(fleetName != null? "intelDesc1" : "intelDesc1NoName");
		str = StringHelper.substituteToken(str, "$faction", faction.getPersonNamePrefix());
		if (fleetName != null) str = StringHelper.substituteToken(str, "$fleetName", fleetName);
		
		LabelAPI label = info.addPara(str, opad);
		if (fleetName != null) {
			label.setHighlight(fleetName);
			label.setHighlightColor(faction.getBaseUIColor());
		}
		
		// Commander info
		if (commander != null) {
			str = getString(flagship == null ? "intelDescCommanderNoFlagship" : "intelDescCommander");
			str = StringHelper.substituteToken(str, "$rank", commander.getRank());
			str = StringHelper.substituteToken(str, "$name", commander.getNameString());
			
			// "highly capable officer" etc.
			int personLevel = commander.getStats().getLevel();
			int tier;
			if (personLevel <= 5) {
				tier = 1;
			} else if (personLevel <= 10) {
				tier = 2;
			} else if (personLevel <= 15) {
				tier = 3;
			} else {
				tier = 4;
			}
			str = StringHelper.substituteToken(str, "$levelDesc", StringHelper.getString(
					"exerelin_officers", "intelSkillLevel" + tier));
			
			// Include flagship details if needed
			if (flagship != null) {
				String flagshipName = flagship.getShipName();
				String flagshipType = flagship.getHullSpec().getNameWithDesignationWithDashClass();
				str = StringHelper.substituteToken(str, "$flagship", flagshipType + " " + flagshipName);
				label = info.addPara(str, opad);
				label.setHighlight(commander.getNameString(), flagshipType, flagshipName);
				label.setHighlightColors(h, h, c);
			}
			else {
				info.addPara(str, opad, h, commander.getNameString());
			}
			
		}
		
		// Fleet strength
		str = getString("intelDescStr");
		String fp = Math.round(route.getExtra().fp) + "";
		if (route.getActiveFleet() != null)
			fp = route.getActiveFleet().getFleetPoints() + "/" + fp;
		int damage = 0;
		if (route.getExtra().damage != null)
			damage = (int)(route.getExtra().damage * 100);
		
		info.addPara(str, opad, h, fp, damage + "%");
		
		if (route.getActiveFleet() != null) {
			str = getString("intelDescFleetStatus");
			String loc = route.getActiveFleet().getContainingLocation() != null ?
					route.getActiveFleet().getContainingLocation().getName() : " <null location>";
			info.addPara(str, opad, h, loc);
		}
		
		// Current action
		str = getString("intelDescAction");
		String actionStr = "idling";
		if (routeAI.currentTask != null)
			actionStr = routeAI.currentTask.getText();
		str = StringHelper.substituteToken(str, "$action", actionStr);
		info.addPara(str, opad, h, actionStr);
		
		RouteSegment segment = route.getCurrent();
		bullet(info);
		if (segment != null) {
			str = getString("intelDescRouteSegmentInfo");
			String none = "<" + StringHelper.getString("none") + ">";
			
			String from = segment.from != null? segment.from.getName() : none;
			String to = segment.to != null? segment.to.getName() : none;
			String elapsed = String.format("%.1f", segment.elapsed);
			String max = String.format("%.1f", segment.daysMax);
			
			
			info.addPara(str, 3, h, from, to, elapsed, max);
		}
		if (routeAI.currentTask != null) {
			str = getString("intelDescActionPriority");
			info.addPara(str, opad, h, String.format("%.1f", routeAI.currentTask.priority));
		}
		unindent(info);
		
		str = getString("intelDescDebug");
		info.addPara(str, Misc.getGrayColor(), opad);
		
		ButtonAPI button = info.addButton(getString("intelButtonDebug"), 
					BUTTON_DEBUG, faction.getBaseUIColor(), faction.getDarkUIColor(),
					(int)(width), 20f, opad);
		//button.setShortcut(Keyboard.KEY_D, true);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		ui.showDialog(null, new SpecialForcesDebugDialog(this, ui));
	}
	
	@Override
	protected void advanceImpl(float amount) {	
		// End event if fleet is dead
		Float damage = route.getExtra().damage, fp = route.getExtra().fp;
		if (damage == null) damage = 0f;
		
		if (route.getActiveFleet() != null && !route.getActiveFleet().isAlive()) 
		{
			log.info("Fleet is dead, ending event");
			endEvent();
			return;
		}
		/*
		else if (damage >= 1 || (fp != null && fp <= 0))
		{
			log.info("Route has zero FP, ending event");
			endEvent();
			return;
		}
		*/
		
		//log.info("advancing");
		routeAI.advance(amount);
		
		float days = Global.getSector().getClock().convertToDays(amount);
		if (rebuildCheckCooldown > 0)
			rebuildCheckCooldown -= days;
		else {			
			boolean chk = checkRebuild(damage);
			if (chk) return;
		}
		
		interval.advance(days);
		if (!interval.intervalElapsed()) return;
		
		if (route.isExpired()) {
			log.info("Route expired, ending event");
			endEvent();
			return;
		}
		
		// If ran out of route segments, have AI give us something else to do
		if (route.getCurrent() != null && SpecialForcesRouteAI.ROUTE_IDLE_SEGMENT.equals(route.getCurrent().custom)) 
		{
			routeAI.notifyRouteFinished();
		}
	}
	
	protected void endEvent() {
		routeAI.resetRoute(route);
		endAfterDelay();
		sendUpdateIfPlayerHasIntel(ENDED_UPDATE, false, false);
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(faction.getId());
		tags.add(getString("intelTag"));
		return tags;
	}
	
	@Override
	public String getSortString() {
		return faction.getDisplayName() + " " + fleetName;
	}
	
	@Override
	public String getIcon() {
		return getFactionForUIColors().getCrest();
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		return faction;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (route.getActiveFleet() != null) {
			StarSystemAPI sys = route.getActiveFleet().getStarSystem();
			if (sys != null) return sys.getCenter();
		}
		return null;
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_specialForces", id, ucFirst);
	}
	
	@Override
	public boolean isHidden() {
		return !Global.getSettings().isDevMode() && !ExerelinModPlugin.isNexDev;
	}

	@Override
	public boolean shouldCancelRouteAfterDelayCheck(RouteData route) {
		return false;
	}

	@Override
	public boolean shouldRepeat(RouteData route) {
		return false;
	}

	@Override
	public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
		
	}
	
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle)
	{
		log.info("Fleet " + fleet.getName() + " in battle");
		
		/*
			NOTE: this causes calculation errors if a fleet is damage, despawned, then respawned
			e.g. if it loses half its FP, despawns, then respawns, trueStartingFP will now be half its original value
			If half this reduced fleet is then lost, damage is only 0.5 even though the fleet is now 1/4 its original size
			But I don't know how to fix this
		*/
		
		float healthMult = ((float)fleet.getFleetPoints()/(float)trueStartingFP);
		float damage = 1 - 1 * healthMult;
		route.getExtra().damage = damage;
		
		List<FleetMemberAPI> losses = Misc.getSnapshotMembersLost(fleet);
		if (losses.contains(flagship)) {
			flagship = null;
			// TODO: maybe chance of commander being killed?
		}
		if (!losses.isEmpty()) {
			checkRebuild(damage);
		}
	}
	
	public String pickFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander)
	{
		String name = "error";
		
		String className = ExerelinConfig.getExerelinFactionConfig(faction.getId()).specialForcesNamerClass;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			SpecialForcesNamer namer = (SpecialForcesNamer)clazz.newInstance();
			name = namer.getFleetName(fleet, origin, commander);
		} catch (Throwable t) {
			log.error("Failed to load special forces namer " + className, t);
		}

		return name;
	}
	
	public String getFleetNameForDebugging() {
		if (fleetName != null) return fleetName;
		return faction.getDisplayName() + " task group"; 
	}
	
	public void debugMsg(String msg, boolean small) {
		log.info(msg);
		if (debugDialog != null) {// && dialog.getPlugin() instanceof SpecialForcesDebugDialog) {
			TextPanelAPI text = debugDialog.getTextPanel();
			if (small) text.setFontSmallInsignia();
			text.addPara(msg);
			if (small) text.setFontInsignia();
		}
	}
	
	public static class SFFleetEventListener implements FleetEventListener {
		
		protected SpecialForcesIntel sf;
		
		public SFFleetEventListener(SpecialForcesIntel sf) {
			this.sf = sf;
		}
		
		@Override
		public void reportFleetDespawnedToListener(CampaignFleetAPI fleet, FleetDespawnReason reason, Object param) 
		{
			
		}

		@Override
		public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle)
		{
			sf.reportBattleOccurred(fleet, primaryWinner, battle);
		}
		
	}
}
