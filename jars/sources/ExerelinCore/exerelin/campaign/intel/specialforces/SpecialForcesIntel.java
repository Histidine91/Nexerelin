package exerelin.campaign.intel.specialforces;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.FleetEventListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.OptionalFleetData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteFleetSpawner;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteSegment;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.DelayedActionScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.SectorManager;
import exerelin.campaign.econ.FleetPoolManager;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.SpecialForcesTask;
import exerelin.campaign.intel.specialforces.SpecialForcesRouteAI.TaskType;
import exerelin.campaign.intel.specialforces.namer.SpecialForcesNamer;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static exerelin.campaign.intel.fleets.NexAssembleStage.getAdjustedStrength;

public class SpecialForcesIntel extends BaseIntelPlugin implements RouteFleetSpawner 
{
	public static Logger log = Global.getLogger(SpecialForcesIntel.class);
	
	public static final String SOURCE_ID = "nex_specialForces";
	public static final String FLEET_TYPE = "nex_specialForces";
	public static final String FLEET_MEM_KEY_INTEL = "$nex_sfIntel";
	protected static final String BUTTON_DEBUG = "debug";
	protected static final Object BUTTON_RENAME = new Object();
	public static final Object ENDED_UPDATE = new Object();
	public static final Object NEW_ORDERS_UPDATE = new Object();
	public static final Object ARRIVED_UPDATE = new Object();
	public static final float DAMAGE_TO_REBUILD = 0.4f;
	public static final float DAMAGE_TO_TERMINATE = 0.9f;
	public static final boolean ALLOW_GO_ROGUE = true;
	
	protected boolean isPlayer;	// just checked for some stuff; most player-specific logic is handled in PlayerSpecialForcesIntel
	
	protected MarketAPI origin;
	protected MarketAPI lastSpawnedFrom;	// updated when fleet rebuilds
	protected FactionAPI faction;
	protected FactionAPI factionForGear;
	protected float startingFP;		// as stored in route extra data
	protected float trueStartingFP;	// from actually generated fleet; reset on fleet rebuild
	protected RouteData route;
	protected long spawnSeed = new Random().nextLong();
	protected String fleetName;
	protected SpecialForcesRouteAI routeAI;
	protected IntervalUtil interval = new IntervalUtil(0.2f, 0.3f);
	
	// These are preserved between fleet regenerations
	@Getter @Setter protected PersonAPI commander;
	@Getter @Setter protected FleetMemberAPI flagship;
	
	protected float rebuildCheckCooldown = 0;
	
	protected transient InteractionDialogAPI debugDialog;
	protected transient TextFieldAPI nameField;
	
	/*runcode 
	MarketAPI market = Global.getSector().getEconomy().getMarket("culann");
	FactionAPI faction = market.getFaction();
	new exerelin.campaign.intel.specialforces.SpecialForcesIntel(market, faction, 300).init(null);
	*/

	public SpecialForcesIntel(MarketAPI origin, FactionAPI faction, float startingFP) 
	{
		this.origin = origin;
		this.faction = faction;
		factionForGear = faction;
		this.startingFP = startingFP;
		lastSpawnedFrom = origin;
	}
	
	protected Object readResolve() {
		if (factionForGear == null)
			factionForGear = faction;
		
		return this;
	}

	// instead of using a Lombok annotation, because I cba to figure out how to get Kotlin to play with Lombok
	public boolean isPlayer() {
		return isPlayer;
	}

	public void init(PersonAPI commander) {
		this.commander = commander;
		
		OptionalFleetData extra = new OptionalFleetData(origin);
		extra.factionId = faction.getId();
		extra.fp = startingFP;
		extra.fleetType = FLEET_TYPE;
		extra.strength = getAdjustedStrength(startingFP, origin);
		route = RouteManager.getInstance().addRoute(SOURCE_ID, origin, spawnSeed, extra, this);
		if (isPlayer) {
			routeAI = new PlayerSpecialForcesRouteAI((PlayerSpecialForcesIntel)this);
		} else {
			routeAI = new SpecialForcesRouteAI(this);
		}
		
		routeAI.addInitialTask();
		generateFlagshipAndCommanderIfNeeded(route);

		if (NexConfig.nexIntelQueued <= 1) {
			Global.getSector().getIntelManager().addIntel(this);
		}

		else {
			Global.getSector().getIntelManager().queueIntel(this);
		}
		Global.getSector().addScript(this);

		SpecialForcesManager.getManager().registerIntel(this);
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
		
		fleet.setFaction(faction.getId());
		
		market.getContainingLocation().addEntity(fleet);
		fleet.setFacing((float) Math.random() * 360f);
		// this will get overridden by the assignment AI, depending on route-time elapsed etc
		fleet.setLocation(market.getPrimaryEntity().getLocation().x, market.getPrimaryEntity().getLocation().y);
		
		fleet.addScript(createAssignmentAI(fleet, route));
		
		if (faction.isPlayerFaction()) {
			fleet.setNoAutoDespawn(true);
		}
		
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
		String factionId = factionForGear.getId();
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		if (conf.factionIdForHqResponse != null) 
			factionId = conf.factionIdForHqResponse;
		
		Float damage = thisRoute.getExtra().damage;
		if (damage == null) damage = 0f;		
		float fp = thisRoute.getExtra().fp * (1 - damage) * conf.specialForcesSizeMult
				* NexConfig.specialForcesSizeMult;
		
		log.info(String.format("Preparing fleet params for special task group of faction %s, origin %s, thisRoute %s", 
				this.faction.getId(), origin.getId(), thisRoute));
		FleetParamsV3 params = new FleetParamsV3(
				lastSpawnedFrom,
				null, // locInHyper
				factionId,
				thisRoute.getQualityOverride(), // qualityOverride
				FLEET_TYPE,
				fp, // combatPts
				fp * 0.15f, // freighterPts 
				fp * 0.15f, // tankerPts
				fp * 0.05f, // transportPts
				0, // linerPts
				fp * 0.07f, // utilityPts
				0.25f	// qualityMod
		);
		params.timestamp = thisRoute.getTimestamp();
		params.officerLevelBonus = 1;
		params.officerNumberMult = 1.33f;
		//int maxShips = Global.getSettings().getInt("maxShipsInAIFleet");
		//params.maxNumShips = maxShips + Math.min(maxShips/6, 5);
		params.random = new Random(seed);
		params.ignoreMarketFleetSizeMult = true;
		params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL;
		params.averageSMods = 2;
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
		
		fleet.setFaction(faction.getId(), true);
		
		replaceCommander(fleet, false);
		injectFlagship(fleet);
		
		syncFleet(fleet);
		
		if (fleetName == null) {
			fleetName = pickFleetName(fleet, origin, commander);
		}
		
		updateFleetName(fleet);
		fleet.setNoFactionInName(true);
		
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_WAR_FLEET, true);
		fleet.getMemoryWithoutUpdate().set("$clearCommands_no_remove", true);
		fleet.getMemoryWithoutUpdate().set("$nex_specialforces_npc", true);
		fleet.getMemoryWithoutUpdate().set(FLEET_MEM_KEY_INTEL, this);
		
		fleet.addEventListener(new SFFleetEventListener(this));		
		
		return fleet;
	}
	
	public void updateFleetName(CampaignFleetAPI fleet) {
		if (fleet == null) return;
		String name = faction.getFleetTypeName(FLEET_TYPE);
		if (!name.isEmpty()) name += " - ";
		name += fleetName;
		fleet.setName(name);
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
			if (flagship == null) 
				flagship = pickFlagship();
			if (flagship == null)
				flagship = temp.getFlagship();
		}
	}
	
	protected FleetMemberAPI pickFlagship() {
		String factionId = faction.getId();
		NexFactionConfig conf = NexConfig.getFactionConfig(factionId);
		if (conf.factionIdForHqResponse != null) 
			factionId = conf.factionIdForHqResponse;
		
		String variantId = NexConfig.getFactionConfig(factionId).getRandomSpecialForcesFlagship(route.getRandom());
		if (variantId == null) return null;
		FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
		member.setShipName(faction.pickRandomShipName());
		return member;
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
		
		if (route.getActiveFleet() != null && route.getActiveFleet().getBattle() != null)
			return;
		
		if (routeAI.currentTask != null && routeAI.currentTask.type == TaskType.REBUILD) {
			debugMsg("Already have reconstitution order", false);
			return;
		}
		
		Float damage = route.getExtra().damage;
		if (damage == null) damage = 0f;
		if (damage <= 0 && flagship != null) {
			debugMsg("Fleet is undamaged and needs no reconstitution", false);
			return;
		}
		
		WeightedRandomPicker<MarketAPI> marketPicker = new WeightedRandomPicker<>();
		Vector2f loc = getCurrentHyperspaceLoc();
		
		for (MarketAPI market : Misc.getFactionMarkets(faction)) {
			if (market.isHidden()) continue;
			if (!NexUtilsMarket.hasWorkingSpaceport(market)) continue;
			
			
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
		task.setMarket(market);
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
		FleetPoolManager.getManager().modifyPool(faction.getId(), -fp);
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
	
	public void setFaction(FactionAPI faction) {
		this.faction = faction;
		route.getExtra().factionId = faction.getId();
		if (route.getActiveFleet() != null)
			route.getActiveFleet().setFaction(faction.getId(), true);
	}
	
	/**
	 * Called when the task group is idle for too long (a sign that its faction has been eliminated); 
	 * transfers it to pirates or Pathers, or just deletes it.
	 */
	public void goRogueOrExpire() {
		
		// I wanted to make it check alliance as well, but if the faction has been eliminated it's no longer in the alliance
		if (ALLOW_GO_ROGUE) {
			FactionAPI toDefect = null;
			
			if (SectorManager.isFactionAlive(Factions.LUDDIC_PATH) && NexUtilsFaction.isLuddicFaction(faction.getId()))
				toDefect = Global.getSector().getFaction(Factions.LUDDIC_PATH);
			else if (SectorManager.isFactionAlive(Factions.PIRATES))
				toDefect = Global.getSector().getFaction(Factions.PIRATES);

			if (toDefect != null) {
				log.info("Orphaned special task group " + getName() + " defecting to " + toDefect.getDisplayName());
				setFaction(toDefect);
				routeAI.pickTask(false);
				return;
			}
		}		
		
		if (route.getActiveFleet() == null) {
			log.info("Ending orphaned special task group " + getName());
			endEvent();
		}
	}
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad) {
		if (isEnding() || isEnded()) return;
		if (isUpdate && listInfoParam == ARRIVED_UPDATE && routeAI.currentTask != null 
				&& routeAI.currentTask.getEntity() != null) 
		{
			info.addPara(getString("intelBulletArrived"), 3, tc, 
					routeAI.currentTask.getEntity().getFaction().getBaseUIColor(), 
					routeAI.currentTask.getEntity().getName());
			return;
		}
		if (routeAI.currentTask != null) {
			info.addPara(Misc.ucFirst(routeAI.currentTask.getText()), tc, 3);
		}
	}
	
	@Override
	public String getName() {
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
		// do it this way to simplify PlayerSpecialForcesIntel's override
		createSmallDescriptionPart1(info, width);
		createSmallDescriptionPart2(info, width);
	}
	
	protected void createSmallDescriptionPart1(TooltipMakerAPI info, float width) {
		float opad = 10f;
		
		// Images
		if (commander != null) {
			info.addImages(width, 128, opad, opad, commander.getPortraitSprite(), faction.getCrest());
		}
		else {
			info.addImage(faction.getCrest(), 128, 128);
		}
		
		String str;
		
		// Event over?
		boolean over = route == null || isEnding() || isEnded();
		if (this instanceof PlayerSpecialForcesIntel) {
			boolean live = ((PlayerSpecialForcesIntel)this).isAlive || ((PlayerSpecialForcesIntel)this).waitingForSpawn;
			over |= !live;
		}
		if (over) {
			str = getString("intelDescOver");
			str = StringHelper.substituteToken(str, "$faction", faction.getPersonNamePrefix());
			String fleetName = this.fleetName != null ? this.fleetName 
					: "<" + StringHelper.getString("unknown") + ">";
			str = StringHelper.substituteToken(str, "$fleetName", fleetName);
			
			info.addPara(str, opad);
			return;
		}
		
		printIntro(info, opad);
		printCommanderInfo(info, opad);
		
		if (Nex_IsFactionRuler.isRuler(faction.getId())) {
			nameField = info.addTextField(width - 8, Fonts.DEFAULT_SMALL, opad);
			if (fleetName != null) nameField.setText(fleetName);
			ButtonAPI button = info.addButton(StringHelper.getString("rename", true), BUTTON_RENAME,
					Misc.getBasePlayerColor(), Misc.getDarkPlayerColor(), Alignment.RMID, CutStyle.BL_TR, 80, 20, 3);
		}
	}
	
	protected void createSmallDescriptionPart2(TooltipMakerAPI info, float width) {
		if (route == null || isEnding() || isEnded()) {
			return;
		}
		
		float opad = 10;
		String str;
		
		printFleetStrengthInfo(info, opad);
		printCurrentAction(info, opad);
		
		if (isDebugVisible()) {
			str = getString("intelDescDebug");
			info.addPara(str, Misc.getGrayColor(), opad);

			ButtonAPI button = info.addButton(getString("intelButtonDebug"), 
						BUTTON_DEBUG, faction.getBaseUIColor(), faction.getDarkUIColor(),
						(int)(width), 20f, opad);
			//button.setShortcut(Keyboard.KEY_D, true);
		}
	}
	
	protected void printIntro(TooltipMakerAPI info, float opad) {
		// Intro paragraph
		String str = getString(fleetName != null? "intelDesc1" : "intelDesc1NoName");
		str = StringHelper.substituteToken(str, "$faction", faction.getPersonNamePrefix());
		if (fleetName != null) str = StringHelper.substituteToken(str, "$fleetName", fleetName);
		
		LabelAPI label = info.addPara(str, opad);
		if (fleetName != null) {
			label.setHighlight(fleetName);
			label.setHighlightColor(faction.getBaseUIColor());
		}
	}
	
	protected void printCommanderInfo(TooltipMakerAPI info, float opad) {
		Color h = Misc.getHighlightColor();
		Color c = getFactionForUIColors().getBaseUIColor();
		
		if (commander != null) {
			String str = getString(flagship == null ? "intelDescCommanderNoFlagship" : "intelDescCommander");
			str = StringHelper.substituteToken(str, "$rank", commander.getRank());
			str = StringHelper.substituteToken(str, "$name", commander.getNameString());
			
			// Include flagship details if needed
			if (flagship != null) {
				String flagshipName = flagship.getShipName();
				String flagshipType = flagship.getHullSpec().getNameWithDesignationWithDashClass();
				str = StringHelper.substituteToken(str, "$flagship", flagshipType + " " + flagshipName);
				LabelAPI label = info.addPara(str, opad);
				label.setHighlight(commander.getNameString(), flagshipType, flagshipName);
				label.setHighlightColors(h, h, c);
			}
			else {
				info.addPara(str, opad, h, commander.getNameString());
			}
		}
	}
	
	protected void printFleetStrengthInfo(TooltipMakerAPI info, float opad) {
		Color h = Misc.getHighlightColor();
		if (true || isDebugVisible()) {
			String str = getString("intelDescStr");
			String fp = Math.round(route.getExtra().fp) + "";
			if (route.getActiveFleet() != null) {
				String routeFP = fp;
				fp = route.getActiveFleet().getFleetPoints() + "";
				if (!this.isPlayer || ExerelinModPlugin.isNexDev) fp += "/" + routeFP;
			}
			int damage = getDamage();

			info.addPara(str, opad, h, fp, damage + "%");

			if (route.getActiveFleet() != null) {
				str = getString("intelDescFleetStatus");
				String loc = route.getActiveFleet().getContainingLocation() != null ?
						route.getActiveFleet().getContainingLocation().getName() : " <null location>";
				info.addPara(str, opad, h, loc);
				
				FleetAssignmentDataAPI assign = route.getActiveFleet().getCurrentAssignment();
				if (assign != null) {
					str = String.format("Fleet current assignment: %s (time %s of %s)", 
							assign.getActionText(),
							assign.getElapsedDays(),
							assign.getMaxDurationInDays());
					//info.addPara(str, opad, h, loc);
				}
				
			}
		}
	}
	
	public void printCurrentAction(TooltipMakerAPI info, float opad) {
		Color h = Misc.getHighlightColor();		
		String str = getString("intelDescAction");
		String actionStr = StringHelper.getString("exerelin_fleetAssignments", "idle");
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
			int eta = getETA();
			if (eta > -1) {
				str = getString("intelDescETA");
				info.addPara(str, 3, h, eta + "");
			}
			str = getString("intelDescActionPriority");
			info.addPara(str, 3, h, String.format("%.1f", routeAI.currentTask.priority));
		}
		unindent(info);
	}
	
	protected int getDamage() {
		if (route.getExtra().damage != null)
			return (int)(route.getExtra().damage * 100);
		return 0;
	}
	
	protected int getETA() {
		float eta = 0;
		CampaignFleetAPI fleet = route.getActiveFleet();
		float distHyper;
		if (fleet != null && fleet.isAlive() && fleet.getAI().getCurrentAssignment() != null) 
		{
			SectorEntityToken target = fleet.getAI().getCurrentAssignment().getTarget();
			if (target == null) return -1;
			if (target.getContainingLocation() == fleet.getContainingLocation())
				return -1;
			distHyper = Misc.getDistanceLY(fleet.getLocationInHyperspace(), target.getLocationInHyperspace());
		}
		else {
			SectorEntityToken target = route.getCurrent().from;
			if (target == null) target = route.getCurrent().to;
			if (target == null) return -1;
			distHyper = Misc.getDistanceLY(route.getInterpolatedHyperLocation(), target.getLocationInHyperspace());
		}
		eta += distHyper/2;
		return Math.round(eta);
	}
	
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (buttonId == BUTTON_DEBUG) {
			ui.showDialog(null, new SpecialForcesDebugDialog(this, ui));
		} else if (buttonId == BUTTON_RENAME) {
			fleetName = nameField.getText();
			updateFleetName(route.getActiveFleet());
			ui.updateUIForItem(this);
		}
	}
	
	@Override
	protected void advanceImpl(float amount) {	
		// End event if fleet is dead
		Float damage = route.getExtra().damage, fp = route.getExtra().fp;
		if (damage == null) damage = 0f;
		
		if (route.getActiveFleet() != null && !route.getActiveFleet().isAlive()) 
		{
			//log.info("Fleet is dead, ending event");
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
		if (!isEnding() && !isEnded()) {
			endAfterDelay();
			sendUpdateIfPlayerHasIntel(ENDED_UPDATE, false, false);
		}
	}
	
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(faction.getId());
		if (isPlayer) tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"));
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
			else return route.getActiveFleet();
		}
		return null;
	}

	@Override
	public List<ArrowData> getArrowData(SectorMapAPI map) {
		SectorEntityToken origin = getMapLocation(map);
		if (origin == null) return null;
		SectorEntityToken dest = null;
		RouteSegment segment = route.getCurrent();
		if (segment != null) dest = segment.getDestination();
		if (dest == null) return null;

		List<ArrowData> result = new ArrayList<ArrowData>();
		ArrowData arrow = new ArrowData(origin, dest);
		arrow.color = Global.getSector().getPlayerFaction().getColor();
		arrow.width = 10f;
		result.add(arrow);

		return result;
	}

	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_specialForces", id, ucFirst);
	}
	
	@Override
	public boolean isHidden() {
		if (faction.isPlayerFaction()) return false;
		return !isDebugVisible();
	}
	
	public boolean isDebugVisible() {
		return NexUtils.isNonPlaytestDevMode() || ExerelinModPlugin.isNexDev;
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
	protected void notifyEnding() {
		faction.getMemoryWithoutUpdate().set(SpecialForcesManager.MEM_KEY_RESPAWN_DELAY, 
				true, SpecialForcesManager.RESPAWN_DELAY);
	}

	@Override
	public void reportAboutToBeDespawnedByRouteManager(RouteData route) {
		
	}
	
	/**
	 * Forces the fleet to generate. Used for e.g. bounties. Doesn't actually assign the active fleet, don't use.
	 * @param preventAutoDespawn Sets {@code setNoAutoDespawn(true)} on the resulting fleet.
	 */
	@Deprecated
	public void forceSpawn(boolean preventAutoDespawn) {
		if (route.getActiveFleet() == null)
			route.getSpawner().spawnFleet(route);
		if (preventAutoDespawn && route.getActiveFleet() != null) 
			route.getActiveFleet().setNoAutoDespawn(true);
	}
	
	/**
	 * Use to revert the effects of {@code forceSpawn(true)}.
	 */
	public void allowDespawn() {
		if (route.getActiveFleet() != null) 
			route.getActiveFleet().setNoAutoDespawn(null);
	}
	
	public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle)
	{
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
		if (losses.contains(flagship) && !isPlayer) {
			flagship = null;
			// TODO: maybe chance of commander being killed?
		}
		if (!losses.isEmpty()) {
			checkRebuild(damage);
		}
	}
	
	public void reportFleetDespawned(FleetDespawnReason reason, Object param) {
		
	}
	
	public String pickFleetName(CampaignFleetAPI fleet, MarketAPI origin, PersonAPI commander)
	{
		String name = "error";
		
		String className = NexConfig.getFactionConfig(faction.getId()).specialForcesNamerClass;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			SpecialForcesNamer namer = (SpecialForcesNamer)clazz.newInstance();
			
			boolean allow = false;
			int tries = 0;
			while (!allow && tries < 25) {
				name = namer.getFleetName(fleet, origin, commander);
				allow = name != null && !hasDuplicateName(name);
				tries++;
			}
		} catch (Throwable t) {
			log.error("Failed to load special forces namer " + className, t);
		}

		return name;
	}
	
	protected boolean hasDuplicateName(String name) {
		for (SpecialForcesIntel intel : SpecialForcesManager.getManager().activeIntel)
		{
			if (name.equals(intel.fleetName))
				return true;
		}
		return false;
	}
	
	public String getFleetName() {
		if (fleetName != null) return fleetName;
		return String.format(getString("fleetNameGeneric"), faction.getDisplayName());
	}
	
	public static SpecialForcesIntel getIntelFromMemory(CampaignFleetAPI fleet) {
		return (SpecialForcesIntel)fleet.getMemoryWithoutUpdate().get(FLEET_MEM_KEY_INTEL);
	}
	
	public void debugMsg(String msg, boolean small) {
		//log.info(msg);
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
			log.info("Despawning fleet " + fleet.getName());
			sf.reportFleetDespawned(reason, param);
			Global.getSector().addScript(new RemoveListenerScript(fleet, this));
		}

		@Override
		public void reportBattleOccurred(CampaignFleetAPI fleet, CampaignFleetAPI primaryWinner, BattleAPI battle)
		{
			sf.reportBattleOccurred(fleet, primaryWinner, battle);
		}
	}
	
	// make it a non-anonymous class to avoid an extremely minor chance of unintentional save breakage
	public static class RemoveListenerScript extends DelayedActionScript {

		protected CampaignFleetAPI fleet;
		protected FleetEventListener listener;

		public RemoveListenerScript(CampaignFleetAPI fleet, FleetEventListener listener) 
		{
			super(1);
			this.fleet = fleet;
			this.listener = listener;
		}

		@Override
		public void doAction() {
			fleet.removeEventListener(listener);
		}
	}
}
