package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelAtEntity;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager.RouteData;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_BuyColony;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.intel.groundbattle.GBDataManager.ConditionDef;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.GroundUnit.UnitQuickMoveHax;
import exerelin.campaign.intel.groundbattle.GroundUnit.UnitSize;
import exerelin.campaign.intel.groundbattle.dialog.AbilityDialogPlugin;
import exerelin.campaign.intel.groundbattle.dialog.UnitOrderDialogPlugin;
import exerelin.campaign.intel.groundbattle.plugins.*;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.rebellion.RebellionIntel;
import exerelin.campaign.ui.ProgressBar;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import lombok.Getter;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

/**
 * Primary class for handling ground battles.
 */
public class GroundBattleIntel extends BaseIntelPlugin implements 
		ColonyPlayerHostileActListener, ColonyNPCHostileActListener {
	
	public static int MAX_PLAYER_UNITS = 16;
	public static final boolean ALWAYS_RETURN_TO_FLEET = false;
	
	public static final float VIEW_BUTTON_WIDTH = 128;
	public static final float VIEW_BUTTON_HEIGHT = 24;
	public static final float MODIFIER_PANEL_HEIGHT = 160;
	public static final float ABILITY_PANEL_HEIGHT = 160;
	public static final int LOG_MAX_TURNS_AGO = 50;
	
	public static final Object UPDATE_TURN = new Object();
	public static final Object UPDATE_VICTORY = new Object();
	public static final Object UPDATE_NON_VICTORY_END = new Object();
	public static final Object BUTTON_CANCEL_MOVES = new Object();
	public static final Object BUTTON_AUTO_MOVE = new Object();
	public static final Object BUTTON_AUTO_MOVE_TOGGLE = new Object();
	public static final Object BUTTON_AUTO_MOVE_CAN_DROP = new Object();
	public static final Object BUTTON_ANDRADA = new Object();
	public static final Object BUTTON_GOVERNORSHIP = new Object();
	public static final Object BUTTON_JOIN_ATTACKER = new Object();
	public static final Object BUTTON_JOIN_DEFENDER = new Object();
	
	public static Logger log = Global.getLogger(GroundBattleIntel.class);
	
	protected UnitSize unitSize;
	
	protected int turnNum;
	protected BattleOutcome outcome;
	protected int recentUnrest = 0;
	protected Float timerForDecision;
	
	protected MarketAPI market;
	protected InvasionIntel invasionIntel;
	protected boolean playerInitiated;
	protected Boolean playerIsAttacker;
	
	protected GroundBattleSide attacker;
	protected GroundBattleSide defender;
	protected GBPlayerData playerData;
		
	protected transient ViewMode viewMode;
	protected static transient boolean showAllUnits;	// debug only
	
	protected List<GroundBattleLog> battleLog = new LinkedList<>();
	//protected transient List<String> rawLog;
	protected Map<GroundUnit, IndustryForBattle> movedFromLastTurn = new HashMap<>();	// value is the industry the unit was on last turn
	
	protected List<IndustryForBattle> industries = new ArrayList<>();
	protected List<GroundBattlePlugin> marketConditionPlugins = new LinkedList<>();
	protected List<GroundBattlePlugin> otherPlugins = new LinkedList<>();
	
	protected Map<String, Object> data = new HashMap<>();
	
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	protected IntervalUtil intervalShort = new IntervalUtil(0.2f, 0.2f);
	protected MilitaryResponseScript responseScript;
	@Getter protected int turnsSinceLastAction = 0;
	
	protected InvasionIntel responseIntel;

	protected boolean rebelsArose = false;
	protected boolean noTransfer = false;
	protected boolean endIfPeace = true;
	
	/**
	 * Set to true at the start of {@code advanceTurn()}, and false when it ends.
	 */
	protected transient boolean resolving;
	
	protected transient List<Pair<Boolean, AbilityPlugin>> abilitiesUsedLastTurn = new ArrayList<>();
	
	// =========================================================================
	// setup, getters/setters and other logic
	
	public GroundBattleIntel(MarketAPI market, FactionAPI attacker, FactionAPI defender)
	{
		this.market = market;
		
		int size = market.getSize();
		if (size <= 3)
			unitSize = UnitSize.PLATOON;
		else if (size <= 5)
			unitSize = UnitSize.COMPANY;
		else if (size <= 7)
			unitSize = UnitSize.BATTALION;
		else
			unitSize = UnitSize.REGIMENT;
		
		this.attacker = new GroundBattleSide(this, true);
		this.defender = new GroundBattleSide(this, false);
		this.attacker.faction = attacker;
		this.defender.faction = defender;
		
		playerData = new GBPlayerData(this);
		
		updateIntervals();
	}
	
	protected Object readResolve() {
		if (marketConditionPlugins == null)
			marketConditionPlugins = new ArrayList<>();
		if (abilitiesUsedLastTurn == null)
			abilitiesUsedLastTurn = new ArrayList<>();
		if (movedFromLastTurn == null)
			movedFromLastTurn = new HashMap<>();
		
		return this;
	}
	
	protected void generateDebugUnits() 
	{
		for (int i=0; i<6; i++) {
			String type = i >= 4 ? GroundUnitDef.HEAVY : GroundUnitDef.MARINE;
			GroundUnit unit = new GroundUnit(this, type, 0, i);
			unit.faction = Global.getSector().getPlayerFaction();
			unit.isPlayer = true;
			unit.isAttacker = this.playerIsAttacker;
			
			if (GroundUnitDef.HEAVY.equals(type)) {
				int numHeavies = Math.round(unitSize.getAverageSizeForType(GroundUnitDef.HEAVY) * MathUtils.getRandomNumberInRange(1, 1.4f));
				unit.setSize(numHeavies, false);
			} else {
				int numMarines = Math.round(unitSize.getAverageSizeForType(GroundUnitDef.MARINE) * MathUtils.getRandomNumberInRange(1, 1.4f));
				unit.setSize(numMarines, false);
			}
			
			IndustryForBattle loc = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			//unit.setLocation(loc);
			if (unit.morale < GBConstants.REORGANIZE_AT_MORALE) {
				unit.reorganize(1);
			}
			else if (Math.random() > 0.5f) {
				//unit.dest = industries.get(MathUtils.getRandomNumberInRange(0, industries.size() - 1));
			}
			attacker.units.add(unit);
			playerData.units.add(unit);
		}
	}
	
	public void initPlugins() {
		// moved to definition file
		/*
		GeneralPlugin general = new GeneralPlugin();
		addOtherPlugin(general);
		
		FactionBonusPlugin fb = new FactionBonusPlugin();
		addOtherPlugin(fb);
		
		PlanetHazardPlugin hazard = new PlanetHazardPlugin();
		addOtherPlugin(hazard);
		
		FleetSupportPlugin fSupport = new FleetSupportPlugin();
		addOtherPlugin(fSupport);
		*/

		for (String pluginId : GBDataManager.getPlugins()) {
			//log.info("Preparing to add ground battle plugin " + pluginId);
			GroundBattlePlugin plugin = (GroundBattlePlugin)NexUtils.instantiateClassByName(pluginId);
			if (plugin == null) {
				log.error("Failed to instantiate ground battle plugin " + pluginId);
				continue;
			}
			addOtherPlugin(plugin);
		}

		Collections.sort(otherPlugins);
	}
	
	public List<GroundBattlePlugin> getOtherPlugins() {
		return otherPlugins;
	}
	
	public void addOtherPlugin(GroundBattlePlugin plugin) {
		otherPlugins.add(plugin);
		plugin.init(this);
	}
	
	public void removePlugin(GroundBattlePlugin plugin) {
		if (marketConditionPlugins.contains(plugin))
			marketConditionPlugins.remove(plugin);
		if (otherPlugins.contains(plugin))
			otherPlugins.remove(plugin);
	}
	
	public void initMarketConditions() {
		for (MarketConditionAPI cond : market.getConditions()) {
			String condId = cond.getId();
			ConditionDef def = GBDataManager.getConditionDef(condId);
			if (def != null) {
				log.info("Processing condition " + condId);
				if (def.tags.contains("cramped"))
					data.put("cramped", true);
				if (def.plugin != null) {
					MarketConditionPlugin plugin = MarketConditionPlugin.loadPlugin(this, condId);
					marketConditionPlugins.add(plugin);
				}
			}
		}
	}
	
	public void updateIntervals() {
		float days = Global.getSettings().getFloat(isPlayerAttacker() != null ? "nex_gbTurnDaysPlayer" : "nex_gbTurnDaysNPC");
		interval.setInterval(days, days);
		days = days * 0.2f;
		intervalShort.setInterval(days, days);
		
		interval.setElapsed(0);
	}
	
	/**
	 * Inits industries, plugins and such. Should be called after relevant parameters are set.<br/>
	 * Does not actually commence the battle, so can be used for temporary instances (e.g. to determine garrison size).
	 */
	public void init() {
		turnNum = 1;
		
		List<Industry> mktIndustries = new ArrayList<>(market.getIndustries());
		Collections.sort(mktIndustries, INDUSTRY_COMPARATOR);
		for (Industry ind : mktIndustries) {
			if (ind.getId().equals(Industries.POPULATION)) continue;
			if (ind.isHidden()) continue;
			if (ind.getSpec().hasTag(Industries.TAG_STATION)) continue;
			
			addIndustry(ind.getId());
		}
		if (industries.isEmpty()) {
			addIndustry(Industries.POPULATION);
		}
		
		if (playerInitiated) {
			attacker.commander = Global.getSector().getPlayerPerson();
		}
		defender.commander = market.getAdmin();
		
		if (market.getPlanetEntity() == null) {
			data.put("cramped", true);
		}
		
		initPlugins();
		initMarketConditions();		
		
		reapply();
	}
	
	/**
	 * Called when the player actually decides to proceed with the invasion.
	 */
	public void start() {
		if (playerInitiated) { // || defender.getFaction().isPlayerFaction() || defender.getFaction() == Misc.getCommissionFaction()) {
			this.setImportant(true);
		}

		defender.generateDefenders();
		if (playerInitiated) {
			autoGeneratePlayerUnits();
		}

		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.onBattleStart();
			if (playerInitiated) plugin.onPlayerJoinBattle();
		}
		
		addMilitaryResponse();
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
		Global.getSector().getListenerManager().addListener(this);
		
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleStarted(this);
			if (playerInitiated) {
				try {
					x.reportPlayerJoinedBattle(this);
				} catch (Error er) {
					log.warn("Failed to report player joined battle", er);
				}
			}
		}
		reapply();
	}
	
	
	
	public void initDebug() {
		playerInitiated = true;
		playerIsAttacker = true;
		init();
		generateDebugUnits();
	}
	
	/**
	 * Registers an industry on the market as an {@code IndustryForBattle} participating in the battle. 
	 * @param industry The industry ID.
	 * @return
	 */
	public IndustryForBattle addIndustry(String industry) 
	{
		Industry ind = market.getIndustry(industry);
		IndustryForBattle ifb = new IndustryForBattle(this, ind);
		
		industries.add(ifb);
		return ifb;
	}
	
	public List<GroundBattlePlugin> getMarketConditionPlugins() {
		return marketConditionPlugins;
	}
	
	public List<GroundBattlePlugin> getPlugins() {
		List<GroundBattlePlugin> list = new ArrayList<>();
		list.addAll(otherPlugins);
		list.addAll(marketConditionPlugins);
		for (IndustryForBattle ifb : industries) {
			if (ifb.getPlugin() == null) {
				log.warn("Null plugin for " + ifb.ind.getId());
				continue;
			}
			list.add(ifb.getPlugin());
		}
		for (GroundUnit unit : getAllUnits()) {
			list.add(unit.getPlugin());
		}
		
		return list;
	}
	
	public MarketAPI getMarket() {
		return market;
	}
	
	public List<IndustryForBattle> getIndustries() {
		return industries;
	}
	
	public UnitSize getUnitSize() {
		return unitSize;
	}
	
	public IndustryForBattle getIndustryForBattleByIndustry(Industry ind) {
		for (IndustryForBattle ifb : industries) {
			if (ifb.getIndustry() == ind) {
				return ifb;
			}
		}
		return null;
	}
	
	public GroundBattleSide getSide(boolean isAttacker) {
		if (isAttacker) return attacker;
		else return defender;
	}
	
	public GBPlayerData getPlayerData() {
		return playerData;
	}
	
	public Map<GroundUnit, IndustryForBattle> getMovedFromLastTurn() {
		return movedFromLastTurn;
	}
	
	public Map<String, Object> getCustomData() {
		return data;
	}
	
	public BattleOutcome getOutcome() {
		return outcome;
	}
	
	/**
	 * Set to true at the start of {@code advanceTurn()}, and false when it ends.
	 * @return 
	 */
	public boolean isResolving() {
		return resolving;
	}
	
	public boolean isNoTransfer() {
		return noTransfer;
	}

	public void setNoTransfer(boolean noTransfer) {
		this.noTransfer = noTransfer;
	}
	
	public boolean isEndIfPeace() {
		return endIfPeace;
	}
	
	public void setEndIfPeace(boolean endIfPeace) {
		this.endIfPeace = endIfPeace;
	}
	
	public Boolean isPlayerAttacker() {
		return playerIsAttacker;
	}
	
	public boolean isPlayerAttackerForGUI() {
		Boolean isAttacker = this.isPlayerAttacker();
		if (isAttacker != null) return isAttacker;
		return true;
	}
	
	public void setPlayerIsAttacker(Boolean bool) {
		playerIsAttacker = bool;
	}
	
	public boolean isPlayerInitiated() {
		return playerInitiated;
	}
	
	public void playerJoinBattle(boolean isAttacker, boolean repChange) {
		setPlayerIsAttacker(isAttacker);
		
		// lose rep with enemy now (gain rep with friend at end of battle)
		FactionAPI enemy = getSide(!isAttacker).getFaction();
		
		float currFriendStr = getSide(isAttacker).getStrength();
		float currEnemyStr = getSide(!isAttacker).getStrength();
		float strFractionAtJoinTime = currFriendStr/(currFriendStr + currEnemyStr);
		
		playerData.strFractionAtJoinTime = strFractionAtJoinTime;
		
		FactionAPI joinSide = getSide(playerIsAttacker).getFaction();
		boolean alreadyOurSide = joinSide.isPlayerFaction() || joinSide == Misc.getCommissionFaction()
				|| AllianceManager.areFactionsAllied(joinSide.getId(), PlayerFactionStore.getPlayerFactionId());
		if (repChange && !alreadyOurSide) {
			CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
			impact.delta = market.getSize() * -0.02f;
			// not now, we also need to look at requested fleets
			//impact.ensureAtBest = tempInvasion.success ? RepLevel.VENGEFUL : RepLevel.HOSTILE;
			impact.ensureAtBest = RepLevel.HOSTILE;
			Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.CUSTOM, 
						impact, null, null, true, true),
						enemy.getId());
		}
		
		if (playerIsAttacker) {
			Misc.increaseMarketHostileTimeout(market, 60f);
		}
		
		autoGeneratePlayerUnits();

		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.onPlayerJoinBattle();
		}

		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class))
		{
			try {
				x.reportPlayerJoinedBattle(this);
			} catch (Error er) {
				log.warn("Failed to report player joined battle", er);
			}
		}
	}
	
	
	public void setPlayerInitiated(boolean playerInitiated) {
		this.playerInitiated = playerInitiated;
	}
		
	public Boolean isPlayerFriendly(boolean isAttacker) {
		if (playerIsAttacker == null) return null;
		return (playerIsAttacker == isAttacker);
	}
	
	public int getTurnNum() {
		return turnNum;
	}
	
	public boolean isCramped() {
		Boolean cramped = (Boolean)data.get("cramped");
		return Boolean.TRUE.equals(cramped);
	}
	
	public void setInvasionIntel(InvasionIntel intel) {
		this.invasionIntel = intel;
	}
	
	public Color getHighlightColorForSide(boolean isAttacker) {
		Boolean friendly = isPlayerFriendly(isAttacker);
		if (friendly == null) return Misc.getHighlightColor();
		else if (friendly == true) return Misc.getPositiveHighlightColor();
		else return Misc.getNegativeHighlightColor();
	}
	
	public List<GroundUnit> getAllUnits() {
		List<GroundUnit> results = new ArrayList<>(attacker.units);
		results.addAll(defender.units);
		return results;
	}
	
	public boolean hasStationFleet() {
		CampaignFleetAPI station = Misc.getStationFleet(market);
		if (station == null) return false;
		
		if (station.getFleetData().getMembersListCopy().isEmpty()) return false;
		
		return true;
	}
	
	/**
	 * Determine whether {@code faction} should assist the attacker or the defender.
	 * @param faction
	 * @return True if the faction should join the attacker, false to join the defender, null for neither.
	 */
	public Boolean getSideToSupport(FactionAPI faction) {
		return getSideToSupport(faction, true);
	}
	
	/**
	 * Determine whether {@code faction} should assist the attacker or the defender.
	 * @param faction
	 * @param fleetSupportOnly False when checking whether an invasion event should actually contribute ground troops.
	 * @return True if the faction should join the attacker, false to join the defender, null for neither.
	 */
	public Boolean getSideToSupport(FactionAPI faction, boolean fleetSupportOnly) {
		// to help either side, we must be hostile to the other side, while being no worse than suspicious to our side
		// to actually join with ground troops, need allied state
		boolean canHelpAttacker, canHelpDefender;
		
		if (fleetSupportOnly) {
			canHelpAttacker = faction.isAtWorst(attacker.getFaction(), RepLevel.SUSPICIOUS) 
					&& faction.isHostileTo(defender.getFaction());
			canHelpDefender = faction.isAtWorst(defender.getFaction(), RepLevel.SUSPICIOUS) 
					&& faction.isHostileTo(attacker.getFaction());
		} else {
			canHelpAttacker = AllianceManager.areFactionsAllied(faction.getId(), attacker.getFaction().getId()) 
					&& faction.isHostileTo(defender.getFaction());
			canHelpDefender = AllianceManager.areFactionsAllied(faction.getId(), defender.getFaction().getId()) 
					&& faction.isHostileTo(attacker.getFaction());
		}
		
		// friendly to both, or hostile to both
		if (canHelpAttacker && canHelpDefender) return null;
		if (!canHelpAttacker && !canHelpDefender) return null;
		
		if (canHelpAttacker) return true;
		else return false;
	}
	
	/**
	 * Can {@code faction} support the specified side in this ground battle? 
	 * @param faction
	 * @param isAttacker
	 * @return
	 */
	protected boolean canSupport(FactionAPI faction, boolean isAttacker) {		
		FactionAPI supportee = getSide(isAttacker).faction;
		if (faction == supportee) return true;
		if (faction.isPlayerFaction() && Misc.getCommissionFaction() == supportee)
			return true;
		
		Boolean sideToSupport = getSideToSupport(faction);
		if (sideToSupport == null) return false;
		return sideToSupport == isAttacker;
	}
	
	public boolean fleetCanSupport(CampaignFleetAPI fleet, boolean isAttacker) 
	{
		if (fleet.getBattle() != null) return false;
		if (isAttacker && hasStationFleet()) return false;
		if (fleet.isStationMode()) return false;
		if (fleet.isPlayerFleet()) {
			return playerIsAttacker != null && isAttacker == playerIsAttacker;
		}
		MemoryAPI mem = fleet.getMemory();
		if (mem.contains(MemFlags.MEMORY_KEY_TRADE_FLEET)) return false;
		
		return canSupport(fleet.getFaction(), isAttacker);
	}
	
	public List<CampaignFleetAPI> getSupportingFleets(boolean isAttacker) {
		List<CampaignFleetAPI> fleets = new ArrayList<>();
		SectorEntityToken token = market.getPrimaryEntity();
		for (CampaignFleetAPI fleet : token.getContainingLocation().getFleets()) 
		{
			if (!fleetCanSupport(fleet, isAttacker))
				continue;			
			
			if (MathUtils.getDistance(fleet, token) > GBConstants.MAX_SUPPORT_DIST)
				continue;
			
			fleets.add(fleet);
		}
		
		return fleets;
	}
	
	public boolean isPlayerInRange() {
		return isFleetInRange(Global.getSector().getPlayerFleet());
	}
	
	public boolean isFleetInRange(CampaignFleetAPI fleet) {
		if (fleet == null) return true;
		return MathUtils.getDistance(fleet, 
				market.getPrimaryEntity()) <= GBConstants.MAX_SUPPORT_DIST;
	}
	
	public boolean isRouteInRange(RouteData route) {
		if (route == null) return true;
		
		try {
			LocationAPI curr = route.getSegments().get(route.getCurrentSegmentId()).getCurrentContainingLocation();
			if (curr != market.getContainingLocation()) return false; 

			return MathUtils.getDistance(route.getInterpolatedHyperLocation(), 
					market.getPrimaryEntity().getLocationInHyperspace()) 
					<= GBConstants.MAX_SUPPORT_DIST / Misc.getUnitsPerLightYear();
		} catch (Exception ex) {}
		return true;
	}
	
	public int getMilitiaUnleashTurn() {
		return market.getSize() * 2;
	}
	
	public static void applyTagWithReason(Map<String, Object> data, String tag, String reason) {
		Object param = data.get(tag);
		if (param != null && !(param instanceof Collection)) {
			log.error("Attempt to add a tag-with-reason to invalid collection: " + tag + ", " + reason);
			return;
		}
		Collection<String> reasons;
		if (param != null) 
			reasons = (Collection<String>)param;
		else
			reasons = new HashSet<>();
		
		reasons.add(reason);
		data.put(tag, reasons);
	}
	
	public static void unapplyTagWithReason(Map<String, Object> data, String tag, String reason) {
		Object param = data.get(tag);
		if (param != null && !(param instanceof Collection)) {
			log.error("Attempt to add a tag-with-reason to invalid collection: " + tag + ", " + reason);
			return;
		}
		if (param == null) return;
		
		Collection<String> reasons = (Collection<String>)param;
		reasons.remove(reason);
		if (reasons.isEmpty()) data.remove(tag);
	}

	public GroundUnit createUnit(String unitDefId, FactionAPI faction, boolean isAttacker, int size, CampaignFleetAPI fleet, int index) {
		GroundUnit unit = new GroundUnit(this, unitDefId, size, index);
		unit.setFaction(faction);
		unit.setAttacker(isAttacker);
		unit.setFleet(fleet);
		for (GroundBattlePlugin plugin : this.getPlugins()) {
			plugin.reportUnitCreated(unit);
		}
		return unit;
	}

	public GroundUnit createPlayerUnit(String unitDefId) {
		return createPlayerUnit(unitDefId, null);
	}
	
	public GroundUnit createPlayerUnit(String unitDefId, Integer wantedSize) {
		int index = 0;
		if (!playerData.getUnits().isEmpty()) {
			index = playerData.getUnits().get(playerData.getUnits().size() - 1).index + 1;
		}
		boolean autosize = wantedSize == null;
		if (autosize) wantedSize = 0;

		GroundUnit unit = this.createUnit(unitDefId, PlayerFactionStore.getPlayerFaction(), Boolean.TRUE.equals(this.playerIsAttacker),
				0, Global.getSector().getPlayerFleet(), index);
		unit.setPlayer(true);

		if (autosize) {
			wantedSize = UnitOrderDialogPlugin.getMaxCountForResize(unit, 0, unitSize.getAverageSizeForType(unitDefId));
		}
		unit.setSize(wantedSize, true);
		
		unit.setStartingMorale();
		
		playerData.getUnits().add(unit);
		getSide(playerIsAttacker).units.add(unit);
		return unit;
	}
	
	/**
	 * Creates units for player based on available marines and heavy armaments.<br/>
	 * Attempts to create the minimum number of units that will hold 100% of 
	 * player forces of each type, then distributes marines and heavy arms equally
	 * between each.
	 */
	public void autoGeneratePlayerUnits() {
		CampaignFleetAPI player = Global.getSector().getPlayerFleet();
		// we could try getting crew replacements for marines as well, but kinda jank since marines and tank crew may not have fully overlapping commodities
		int marines = player.getCargo().getMarines();
		int heavyArms = (int)CrewReplacerUtils.getHeavyArms(player, GBConstants.CREW_REPLACER_JOB_HEAVYARMS);
		
		// add heavy units
		int usableHeavyArms = Math.min(heavyArms, marines/GroundUnit.CREW_PER_MECH);
		if (isCramped()) {
			//log.info("Cramped conditions, halving heavy unit count");
			usableHeavyArms /= 2;
		}
		
		autoGenerateUnits(marines, usableHeavyArms, null, null, true);
	}

	public void autoGenerateUnits(int marines, int heavyArms, FactionAPI faction, Boolean isAttacker, boolean player) {
		CampaignFleetAPI fleet = player ? Global.getSector().getPlayerFleet() : null;
		autoGenerateUnits(marines, heavyArms, faction, isAttacker, player, fleet);
	}
	
	/**
	 * Creates units for the specified side from a given number of marines and heavy armaments.<br/>
	 * Attempts to create the minimum number of units that will hold 100% of 
	 * the forces of each type, then distributes marines and heavy arms equally
	 * between each.
	 * @param marines
	 * @param heavyArms
	 * @param faction Does not need to be set if {@code player} is true.
	 * @param isAttacker Does not need to be set if {@code player} is true.
	 * @param player
	 * @param fleet
	 */
	public void autoGenerateUnits(int marines, int heavyArms, FactionAPI faction, Boolean isAttacker, boolean player, CampaignFleetAPI fleet) {
		float perUnitSize = unitSize.getMaxSizeForType(GroundUnitDef.HEAVY);
		int numCreatable = 0;
		
		if (heavyArms >= unitSize.getMinSizeForType(GroundUnitDef.HEAVY)) {
			numCreatable = (int)Math.ceil(heavyArms / perUnitSize);
			numCreatable = Math.min(numCreatable, MAX_PLAYER_UNITS);
			numCreatable = (int)Math.ceil(numCreatable * 0.75f);
		}		
		
		int numPerUnit = 0;
		if (numCreatable > 0) numPerUnit = heavyArms/numCreatable;
		numPerUnit = (int)Math.min(numPerUnit, perUnitSize);
		
		//log.info(String.format("Can create %s heavies, %s units each, have %s heavies", numCreatable, numPerUnit, heavyArms));
		for (int i=0; i<numCreatable; i++) {
			GroundUnit unit;
			if (player) unit = createPlayerUnit(GroundUnitDef.HEAVY, numPerUnit);
			else unit = getSide(isAttacker).createUnit(GroundUnitDef.HEAVY, faction, numPerUnit, fleet);
		}
		
		// add marines
		marines -= heavyArms * GroundUnit.CREW_PER_MECH;
		int remainingSlots = MAX_PLAYER_UNITS - playerData.getUnits().size();
		perUnitSize = unitSize.getMaxSizeForType(GroundUnitDef.MARINE);
		
		if (marines >= unitSize.getMinSizeForType(GroundUnitDef.MARINE)) {
			numCreatable = (int)Math.ceil(marines / perUnitSize);
			numCreatable = Math.min(numCreatable, remainingSlots);
			numPerUnit = 0;
		} else {
			numCreatable = 0;
		}
		
		if (numCreatable > 0) numPerUnit = marines/numCreatable;
		numPerUnit = (int)Math.min(numPerUnit, perUnitSize);
		
		//log.info(String.format("Can create %s marines, %s units each, have %s marines", numCreatable, numPerUnit, marines));
		for (int i=0; i<numCreatable; i++) {
			GroundUnit unit;
			if (player) unit = createPlayerUnit(GroundUnitDef.MARINE, numPerUnit);
			else unit = getSide(isAttacker).createUnit(GroundUnitDef.MARINE, faction, numPerUnit, fleet);
		}
	}
	
	public void updateStability() {
		// not yet started, or already ended
		if (!Global.getSector().getIntelManager().hasIntel(this) || outcome != null) 
		{
			market.getStability().removeTemporaryMod("invasion");
			return;
		}
		int total = 0, attacker = 0;
		for (IndustryForBattle ifb : industries) {
			total++;
			if (ifb.heldByAttacker) attacker++;
		}
		String desc = getString("stabilityDesc");
		market.getStability().addTemporaryModFlat(3, "invasion", desc, 
				-GBConstants.STABILITY_PENALTY_BASE - GBConstants.STABILITY_PENALTY_OCCUPATION * (float)attacker/total);
	}
	
	public void reapply() {
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.unapply();
			plugin.apply();
		}
		updateStability();
	}

	// FIXME: ForceType is deprecated
	protected int countPersonnelFromMap(Map<ForceType, Integer> map) {
		int num = 0;
		for (ForceType type : map.keySet()) {
			int thisNum = map.get(type);
			if (type == ForceType.HEAVY)
				num += thisNum * GroundUnit.CREW_PER_MECH;
			else
				num += thisNum;
		}
		return num;
	}
	
	public void runAI(boolean isAttacker, boolean isPlayer) {
		GroundBattleAI ai = new GroundBattleAI(this, isAttacker, isPlayer);
		ai.giveOrders();
	}

	public void runAI(boolean isAttacker, boolean isPlayer, boolean allowDrop) {
		GroundBattleAI ai = new GroundBattleAI(this, isAttacker, isPlayer, allowDrop);
		ai.giveOrders();
	}
	
	public void runAI() {
		if (playerIsAttacker != null) {
			// run player AI if automove is enabled
			if (playerData.autoMoveAtEndTurn)
				runAI(playerIsAttacker, true, playerData.autoMoveAllowDrop);
			
			// run friendly non-player AI if any such units exist
			for (GroundUnit unit : getSide(playerIsAttacker).getUnits()) {
				if (!unit.isPlayer) {
					runAI(playerIsAttacker, false);
					break;
				}
			}
			
			// run enemy AI
			runAI(!playerIsAttacker, false);		
		} else {
			runAI(true, false);
			runAI(false, false);
		}
	}
	
	/**
	 * Post-battle XP for player units, both those moved to fleet and to local storage.
	 * @param storage
	 */
	public void addXPToDeployedUnits(SubmarketAPI storage) 
	{
		// calc the number of marines involved
		Integer losses = playerData.getLossesV2().get(Commodities.MARINES);
		if (losses == null) losses = 0;
		Integer inFleet = playerData.getDisbandedV2().get(Commodities.MARINES);
		if (inFleet == null) inFleet = 0;
		Integer inStorage = playerData.getSentToStorage().get(Commodities.MARINES);
		if (inStorage == null) inStorage = 0;

		float total = inFleet + inStorage;
		if (total == 0) {
			log.info(String.format("No XP action to take"));
			return;
		}
		
		// calc XP to apply
		float sizeFactor = (float)Math.pow(2, market.getSize());
		float xp = GBConstants.XP_MARKET_SIZE_MULT * sizeFactor;
		log.info(String.format("%s xp from market size", xp));
		float xpFromLosses = losses * GBConstants.XP_CASUALTY_MULT;
		log.info(String.format("%s xp from losses", xpFromLosses));
		xp += xpFromLosses;
		xp = Math.min(xp, total/2);
		
		// apply the XP
		float fleetXP = (inFleet/total) * xp;
		if (fleetXP > 0) {
			log.info("Adding " + fleetXP + " XP for " + inFleet + " marines in fleet");
			PlayerFleetPersonnelTracker.getInstance().getMarineData().addXP(fleetXP);
			
			GroundBattleLog xpLog = new GroundBattleLog(this, GroundBattleLog.TYPE_XP_GAINED);
			xpLog.params.put("xp", fleetXP);
			xpLog.params.put("marines", inFleet);
			xpLog.params.put("isStorage", false);
			addLogEvent(xpLog);
		}
		
		float storageXP = (inStorage/total) * xp;
		if (storage != null && storageXP > 0) {
			// hack to make it apply XP properly: clear existing instance
			PlayerFleetPersonnelTracker.getInstance().reportCargoScreenOpened();
			
			log.info("Adding " + storageXP + " XP for " + inStorage + " marines in storage");
			//playerData.xpTracker.data.num = storage.getCargo().getMarines();
			//playerData.xpTracker.data.addXP(storageXP);
			
			PersonnelAtEntity local = PlayerFleetPersonnelTracker.getInstance().getDroppedOffAt(
				Commodities.MARINES, market.getPrimaryEntity(), 
				market.getSubmarket(Submarkets.SUBMARKET_STORAGE), true);
			local.data.num = storage.getCargo().getMarines();
			local.data.addXP(storageXP + playerData.xpTracker.data.xp);
			
			GroundBattleLog xpLog = new GroundBattleLog(this, GroundBattleLog.TYPE_XP_GAINED);
			xpLog.params.put("xp", storageXP);
			xpLog.params.put("marines", inStorage);
			xpLog.params.put("isStorage", true);
			addLogEvent(xpLog);
		}
		
		Global.getSector().getPlayerPerson().getStats().addXP(Math.round(xp * 1000));
	}
	
	/**
	 * Breaks up player units after the battle ends.
	 */
	public void disbandPlayerUnits() {
		SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		boolean anyInStorage = false;
		
		boolean playerInRangeForReturn = MathUtils.getDistance(Global.getSector().getPlayerFleet(), market.getPrimaryEntity()) < GBConstants.MAX_SUPPORT_DIST;
		boolean returnToFleet = ALWAYS_RETURN_TO_FLEET || storage == null 
				|| playerInRangeForReturn || outcome == BattleOutcome.PEACE;
		
		for (GroundUnit unit : new ArrayList<>(playerData.getUnits())) {
			// if any player units are on market, send them to storage
			// but only if player is out of range, else send them back to fleet?
			if (unit.getLocation() != null) {
				if (!returnToFleet) {
					unit.sendUnitToStorage(storage);
					anyInStorage = true;
				}
				else {
					// no storage? teleport to cargo
					unit.removeUnit(true);
				}
				
			}
			// the ones in fleet we disband directly to player cargo
			else {
				unit.removeUnit(true);
			}
		}
		if (storage != null && (Boolean.TRUE.equals(playerIsAttacker) || anyInStorage)) {
			StoragePlugin plugin = (StoragePlugin)market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin();
			plugin.setPlayerPaidToUnlock(true);
		}
		
		if (outcome != BattleOutcome.CANCELLED) {
			addXPToDeployedUnits(storage);
		}
		
		PlayerFleetPersonnelTracker.getInstance().getDroppedOff().remove(playerData.xpTracker);
	}
	
	public void handleTransfer() {
		if (noTransfer) return;
		
		if (outcome == BattleOutcome.ATTACKER_VICTORY) {
			FactionAPI newOwner = attacker.getFaction();
			if (this.playerInitiated && wasPlayerMarket()) {
				newOwner = Global.getSector().getPlayerFaction();
			}

			InvasionRound.conquerMarket(market, newOwner, playerInitiated);
			
			market.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET);
			market.getMemoryWithoutUpdate().set("$tradeMode", "OPEN", 0);
			
			InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
			if (dialog != null && dialog instanceof RuleBasedDialog) {
				((RuleBasedDialog)dialog.getPlugin()).updateMemory();
				FireAll.fire(null, dialog, dialog.getPlugin().getMemoryMap(), "PopulateOptions");
			}
		}
	}
	
	public void endBattle(BattleOutcome outcome) {
		this.outcome = outcome;
		market.getStability().removeTemporaryMod("invasion");
		
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		
		if (outcome == BattleOutcome.ATTACKER_VICTORY || outcome == BattleOutcome.DEFENDER_VICTORY
				|| outcome == BattleOutcome.PEACE) {
			
			recentUnrest = 1 + (turnNum/Math.max(market.getSize() - 1, 1));
			
			String origOwner = NexUtilsMarket.getOriginalOwner(market);
			// taking a market from its original owner adds extra unrest
			if (outcome == BattleOutcome.ATTACKER_VICTORY && origOwner != null 
					&& defender.getFaction().getId().equals(origOwner))
				recentUnrest += 2;
			
			int existingUnrest = RecentUnrest.getPenalty(market);
			recentUnrest -= existingUnrest/GBConstants.EXISTING_UNREST_DIVISOR;
			if (recentUnrest < 0) recentUnrest = 0;
			
			RecentUnrest.get(market, true).add(recentUnrest, String.format(getString("unrestReason"), 
					attacker.getFaction().getDisplayName()));
		}
					
		if (Boolean.TRUE.equals(playerIsAttacker) && outcome == BattleOutcome.ATTACKER_VICTORY) 
		{
			// loot multiplier based on our deployed attack strength as fraction of total attack strength
			// this is relevant if we joined someone else's attack or vice-versa
			float lootMult = 1;
			{
				float deployedPlayerStr = 0;
				for (GroundUnit unit : playerData.getUnits()) {
					if (unit.isDeployed()) deployedPlayerStr += unit.getAttackStrength();
				}				
				float deployedAttackerStr = 0;
				for (GroundUnit unit : attacker.getUnits()) {
					if (unit.isDeployed()) deployedAttackerStr += unit.getAttackStrength();
				}
				if (deployedAttackerStr < deployedPlayerStr) 
					deployedAttackerStr = deployedPlayerStr;
				
				if (deployedAttackerStr <= 0) lootMult = 0;
				else lootMult = deployedPlayerStr/deployedAttackerStr;
			}
			if (lootMult > 0)
				playerData.setLoot(GroundBattleRoundResolve.lootMarket(market, lootMult));
		}
		boolean startedByPlayer = playerInitiated || (invasionIntel != null && invasionIntel.isPlayerSpawned());
		if (startedByPlayer && outcome == BattleOutcome.ATTACKER_VICTORY && Misc.getCommissionFaction() != null && !wasPlayerMarket())
		{
			timerForDecision = 7f;
			mem.set(GBConstants.MEMKEY_AWAIT_DECISION, true, timerForDecision);
		}
		
		for (IndustryForBattle ifb : industries) {
			if (!ifb.isIndustryTrueDisrupted())
				ifb.ind.setDisrupted(0);
		}
		
		disbandPlayerUnits();
		
		if (outcome == BattleOutcome.PEACE || outcome == BattleOutcome.OTHER) {
			
		}

		endRebellionIfNeeded();

		// reset garrison strength
		if (outcome == BattleOutcome.ATTACKER_VICTORY && playerInitiated) {
			// reset to 25% health for a player victory
			GBUtils.setGarrisonDamageMemory(market, 0.75f);
		}
		else if (outcome != BattleOutcome.DESTROYED) {
			// set health based on surviving units
			// if attacker won, use half the surviving attackers, else use all surviving defenders
			float currStrength = outcome == BattleOutcome.ATTACKER_VICTORY ? attacker.getBaseStrength() * 0.5f : defender.getBaseStrength();
			float strRatio = currStrength/defender.currNormalBaseStrength;
			if (strRatio > 1) strRatio = 1;
			GBUtils.setGarrisonDamageMemory(market, 1 - strRatio);
		}
		
		handleTransfer();
		
		responseScript.forceDone();
		
		GroundBattleLog log = new GroundBattleLog(this, GroundBattleLog.TYPE_BATTLE_END, turnNum);
		if (outcome == BattleOutcome.ATTACKER_VICTORY)
			log.params.put("attackerIsWinner", true);
		else if (outcome == BattleOutcome.DEFENDER_VICTORY)
			log.params.put("attackerIsWinner", false);
		addLogEvent(log);
		
		if (playerIsAttacker != null) {
			log = new GroundBattleLog(this, GroundBattleLog.TYPE_LOSS_REPORT, turnNum);
			Map<String, Integer> losses = playerData.getLossesV2();
			log.params.put("lostCommodities", losses.keySet());
			for (String commodityId : losses.keySet()) {
				log.params.put("lost_" + commodityId, losses.get(commodityId));
			}
			addLogEvent(log);
			
			log = new GroundBattleLog(this, GroundBattleLog.TYPE_COMMODITIES_USED_REPORT, turnNum);
			log.params.put("supplies", playerData.suppliesUsed);
			log.params.put("fuel", playerData.fuelUsed);
			addLogEvent(log);
		}
		
		// reputation gain from helping
		if (!playerInitiated && playerIsAttacker != null) {
			float strFraction = playerData.strFractionAtJoinTime;
			float contribMult = (1 - strFraction) * 0.8f + 0.2f;
			
			FactionAPI friend = getSide(playerIsAttacker).getFaction();
			float rep = market.getSize() * 0.02f * contribMult;
			if (rep >= 0.01f) {
				NexUtilsReputation.adjustPlayerReputation(friend, rep);
			}
		}		
		
		if (outcome == BattleOutcome.ATTACKER_VICTORY || outcome == BattleOutcome.DEFENDER_VICTORY)
			sendUpdateIfPlayerHasIntel(UPDATE_VICTORY, false);
		else
			sendUpdateIfPlayerHasIntel(UPDATE_NON_VICTORY_END, false);

		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleEnded(this);
		}
		
		if (outcome == BattleOutcome.ATTACKER_VICTORY) {
			mem.unset(GBConstants.MEMKEY_INVASION_FAIL_STREAK);
		} else if (outcome == BattleOutcome.DEFENDER_VICTORY) {
			NexUtilsMarket.incrementInvasionFailStreak(market, attacker.getFaction(), true);
		}
		
		updateStability();
		endAfterDelay();
	}

	protected void endRebellionIfNeeded() {
		if (!rebelsArose) return;
		RebellionIntel reb = RebellionIntel.getOngoingEvent(market);
		if (reb == null) return;

		if (outcome == BattleOutcome.DEFENDER_VICTORY) {
			reb.endEvent(RebellionIntel.RebellionResult.GOVERNMENT_VICTORY);
		} else if (outcome == BattleOutcome.ATTACKER_VICTORY) {
			reb.setLiberatorFaction(attacker.faction);
			reb.endEvent(RebellionIntel.RebellionResult.LIBERATED);
		}
	}
	
	public boolean hasAnyDeployedUnits(boolean attacker) {
		for (GroundUnit unit : getSide(attacker).getUnits()) {
			if (unit.getLocation() != null)
				return true;
		}
		return false;
	}
	
	public boolean checkAnyAttackers(boolean checkUndeployed) {
		// any units on ground?
		if (checkUndeployed) {
			if (!attacker.getUnits().isEmpty()) return true;
		}
		else {
			if (hasAnyDeployedUnits(true)) return true;
		}
		// guess not, end the battle
		boolean shouldCancel = turnNum <= 1 && attacker.getLossesV2().isEmpty();
		endBattle(shouldCancel ? BattleOutcome.CANCELLED : BattleOutcome.DEFENDER_VICTORY);
		return false;
	}
	
	public void checkForVictory() {
		if (outcome != null) return;
		if (!hasAnyDeployedUnits(false)) {
			endBattle(BattleOutcome.ATTACKER_VICTORY);
		}
	}
	
	public void advanceTurn(boolean force) {
		resolving = true;
		if (force) {
			doShortIntervalStuff(interval.getIntervalDuration() - interval.getElapsed());
		}
		movedFromLastTurn.clear();
		
		reapply();
		runAI();
		boolean anyAttackers = checkAnyAttackers(false);
		if (!anyAttackers) {
			
			resolving = false;
			return;
		}
		
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleBeforeTurn(this, turnNum);
		}
		new GroundBattleRoundResolve(this).resolveRound();
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleAfterTurn(this, turnNum);
		}
		
		anyAttackers = checkAnyAttackers(true);
		checkForVictory();
		playerData.updateXPTrackerNum();
		
		if (outcome != null) {
			return;
		}
		
		if (turnNum == getMilitiaUnleashTurn() - 1) {
			GroundBattleLog log = new GroundBattleLog(this, GroundBattleLog.TYPE_MILITIA_UNLEASHED, turnNum);
			addLogEvent(log);
		}
		
		attacker.getMovementPointsPerTurn().unmodify("sneakAttack");
		
		if (shouldNotify())
			sendUpdateIfPlayerHasIntel(UPDATE_TURN, false);
		updateIntervals();
		turnNum++;
		reapply();
		attacker.reportTurn();
		defender.reportTurn();
		abilitiesUsedLastTurn.clear();
		resolving = false;
		
		checkForResponse();
	}
	
	public boolean shouldNotify() {
		return playerIsAttacker != null || isImportant();
	}
	
	public void checkForResponse() {
		if (outcome != null) return;
		boolean startedByPlayer = playerInitiated || (invasionIntel != null && invasionIntel.isPlayerSpawned());
		if (!startedByPlayer) return;
		
		if (turnNum < 3) return;
		if (responseIntel != null) return;	// already spawned

		// don't allow counter-invasion if this market was recently taken
		if (market.getMemoryWithoutUpdate().contains(SectorManager.MEMORY_KEY_RECENTLY_CAPTURED)) return;
		
		// requires non-negative invasion point charge to spawn
		float points = InvasionFleetManager.getManager().getSpawnCounter(market.getFactionId());
		if (points < 0) return;

		MarketAPI origin = GBUtils.getMarketForCounterInvasion(market);
		if (origin == null) return;
		
		responseIntel = GBUtils.generateCounterInvasion(this, origin, market);
	}
	
	/**
	 * Was this market originally owned by the player?
	 * @return
	 */
	public boolean wasPlayerMarket() {
		return NexUtilsMarket.wasOriginalOwner(market, Factions.PLAYER);
	}
	
	/**
	 * If the player decides to keep the market for themselves rather than 
	 * transferring it to commissioning faction.
	 */
	public void handleAndradaOption() {
		if (attacker.getFaction().isPlayerFaction()) return;
		SectorManager.transferMarket(market, Global.getSector().getPlayerFaction(), 
				market.getFaction(), true, false, new ArrayList<String>(), 0, true);
		
		if (!wasPlayerMarket()) {
			CoreReputationPlugin.CustomRepImpact impact = new CoreReputationPlugin.CustomRepImpact();
			impact.delta = -0.05f * market.getSize();
			//impact.ensureAtBest = RepLevel.SUSPICIOUS;
			impact.limit = RepLevel.INHOSPITABLE;
			ReputationAdjustmentResult result = Global.getSector().adjustPlayerReputation(
					new CoreReputationPlugin.RepActionEnvelope(
					CoreReputationPlugin.RepActions.CUSTOM, impact, null, null, true), 
					PlayerFactionStore.getPlayerFactionId());
			playerData.andradaRepChange = result;
			playerData.andradaRepAfter = Global.getSector().getPlayerFaction().getRelationship(PlayerFactionStore.getPlayerFactionId());
		}
		
		timerForDecision = null;
	}
	
	public void handleGovernorshipPurchase() {
		MutableStat cost = Nex_BuyColony.getValue(market, false, true);
		int curr = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		if (curr > cost.getModifiedValue()) {
			Nex_BuyColony.buy(market, null);
			playerData.governorshipPrice = cost.getModifiedValue();
		}
		
		timerForDecision = null;
	}
	
	/**
	 * Briefly, periodically disrupt all industries occupied by attacker.
	 */
	public void disruptIndustries() {
		for (IndustryForBattle ifb : industries) {
			if (ifb.heldByAttacker) {
				float currDisruptionTime = ifb.getIndustry().getDisruptedDays();
				if (currDisruptionTime < GBConstants.DISRUPT_WHEN_CAPTURED_TIME)
					ifb.getIndustry().setDisrupted(GBConstants.DISRUPT_WHEN_CAPTURED_TIME);
			}
		}
	}
	
	/**
	 * Adds the response script that attracts patrols and such in the system to the scene of the battle.
	 */
	protected void addMilitaryResponse() {
		if (!market.getFaction().getCustomBoolean(Factions.CUSTOM_NO_WAR_SIM)) {
			MilitaryResponseScript.MilitaryResponseParams params = new MilitaryResponseScript.MilitaryResponseParams(CampaignFleetAIAPI.ActionType.HOSTILE, 
					"nex_player_invasion_" + market.getId(), 
					market.getFaction(),
					market.getPrimaryEntity(),
					0.75f,
					900);
			params.actionText = getString("responseStr");
			params.travelText = getString("responseTravelStr");
			responseScript = new GBMilitaryResponseScript(params, this);
			market.getContainingLocation().addScript(responseScript);
		}
		List<CampaignFleetAPI> fleets = market.getContainingLocation().getFleets();
		for (CampaignFleetAPI other : fleets) {
			if (other.getFaction() == market.getFaction() && Boolean.TRUE.equals(playerIsAttacker)) 
			{
				MemoryAPI mem = other.getMemoryWithoutUpdate();
				Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, "raidAlarm", true, 1f);
			}
		}
	}
	
	/**
	 * Called when someone launches a tactical bombardment or saturation bombardment on the market.
	 * @param ifb
	 */
	public void reportExternalBombardment(IndustryForBattle ifb) {
		if (ifb == null) return;
		InteractionDialogAPI dialog = null;	//Global.getSector().getCampaignUI().getCurrentInteractionDialog();
		for (GroundUnit unit : ifb.units) {
			unit.inflictAttrition(GBConstants.EXTERNAL_BOMBARDMENT_DAMAGE, null, dialog);
			unit.reorganize(1);
			unit.preventAttack(1);
		}
		reapply();
	}
	
	/**
	 * Loot action on a specific industry, sends its AI core and special item (if any) to player cargo.
	 * @param ifb
	 */
	public void loot(IndustryForBattle ifb) {
		String aiCore = ifb.getIndustry().getAICoreId();
		SpecialItemData special = ifb.getIndustry().getSpecialItem();
		if (aiCore != null) {
			Global.getSector().getPlayerFleet().getCargo().addCommodity(aiCore, 1);
			ifb.getIndustry().setAICoreId(null);
		}
		if (special != null) {
			Global.getSector().getPlayerFleet().getCargo().addSpecial(special, 1);
			ifb.getIndustry().setSpecialItem(null);
		}
		ifb.looted = true;
		
		Global.getSoundPlayer().playUISound("ui_cargo_special_military_drop", 1, 1);
	}
	
	public void applySneakAttack() {
		for (GroundUnit unit : defender.getUnits()) {
			unit.reorganize(1);
		}
		attacker.getMovementPointsPerTurn().modifyMult("sneakAttack", GBConstants.SNEAK_ATTACK_MOVE_MULT, 
				getString("modifierMovementPointsSneakAttack"));
	}
	
	public void reportAbilityUsed(AbilityPlugin ability, GroundBattleSide side, PersonAPI person) 
	{
		resetTurnsSinceLastAction();
		
		if (person != null && person.isPlayer()) return;
		Pair<Boolean, AbilityPlugin> entry = new Pair<>(side.isAttacker(), ability);
		abilitiesUsedLastTurn.add(entry);
	}
	
	public void doShortIntervalStuff(float days) {
		disruptIndustries();
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.advance(days);
		}
	}
	
	public void incrementTurnsSinceLastAction() {
		turnsSinceLastAction++;
	}
	
	public void resetTurnsSinceLastAction() {
		turnsSinceLastAction = 0;
	}
	
	// =========================================================================
	// callins
	
	@Override
	public void advance(float amount) {
		super.advance(amount);
		// needs to be in advance rather than advanceImpl so it runs after event ends
		float days = Global.getSector().getClock().convertToDays(amount);
		if (timerForDecision != null) {
			//log.info(String.format("Timer for decision: curr %s, subtracting %s", timerForDecision, days));
			timerForDecision -= days;
			if (timerForDecision <= 0) {
				timerForDecision = null;
			}
		}
	}
	
	@Override
	protected void advanceImpl(float amount) {		
		if (outcome != null) {
			return;
		}
		
		if (!market.isInEconomy()) {
			endBattle(BattleOutcome.DESTROYED);
			return;
		}
		if (endIfPeace && !attacker.getFaction().isHostileTo(defender.getFaction())) {
			endBattle(BattleOutcome.PEACE);
			return;
		}
		
		float days = Global.getSector().getClock().convertToDays(amount);
		intervalShort.advance(days);
		if (intervalShort.intervalElapsed()) {
			doShortIntervalStuff(intervalShort.getElapsed());
		}
		
		interval.advance(days);
		if (interval.intervalElapsed()) {
			advanceTurn(false);
		}
	}
	
	@Override
	protected void notifyEnding() {
		Global.getSector().getListenerManager().removeListener(this);
	}

	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, 
			MarketCMD.TempData actionData, Industry industry) {}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
		
		if (market != this.market) return;
		
		List<Industry> industries = actionData.bombardmentTargets;
		List<String> indNames = new ArrayList<>();
		for (Industry industry : industries) {
			reportExternalBombardment(getIndustryForBattleByIndustry(industry));
			indNames.add(industry.getCurrentName());
		}
		
		GroundBattleLog log = new GroundBattleLog(this, GroundBattleLog.TYPE_EXTERNAL_BOMBARDMENT, turnNum);
		log.params.put("isSaturation", false);
		log.params.put("industries", indNames);
		addLogEvent(log);
	}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
		
		if (market != this.market) return;
		
		List<Industry> industries = actionData.bombardmentTargets;
		for (Industry industry : industries) {
			reportExternalBombardment(getIndustryForBattleByIndustry(industry));
		}
		GroundBattleLog log = new GroundBattleLog(this, GroundBattleLog.TYPE_EXTERNAL_BOMBARDMENT, turnNum);
		log.params.put("isSaturation", true);
		addLogEvent(log);
	}
	
	@Override
	public void reportNPCGenericRaid(MarketAPI market, MarketCMD.TempData actionData) {}

	@Override
	public void reportNPCIndustryRaid(MarketAPI market, MarketCMD.TempData actionData, Industry industry) {}

	@Override
	public void reportNPCTacticalBombardment(MarketAPI market, MarketCMD.TempData actionData) 
	{
		reportTacticalBombardmentFinished(null, market, actionData);
	}

	@Override
	public void reportNPCSaturationBombardment(MarketAPI market, MarketCMD.TempData actionData) 
	{
		reportSaturationBombardmentFinished(null, market, actionData);
	}
	
	// =========================================================================
	// GUI stuff 
	
	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate, 
									Color tc, float initPad){
		info.addPara(Misc.ucFirst(attacker.faction.getDisplayName()), attacker.faction.getBaseUIColor(), 3);
		info.addPara(Misc.ucFirst(defender.faction.getDisplayName()), defender.faction.getBaseUIColor(), 0);
		
		if (listInfoParam == UPDATE_TURN) {
			info.addPara(getString("intelDesc_round"), 0, Misc.getHighlightColor(), turnNum + "");
			writeTurnBullets(info);
		}
		
		if (outcome != null) {
			String id = "outcome";
			switch (outcome) {
				case ATTACKER_VICTORY:
					id += "AttackerVictory";
					break;
				case DEFENDER_VICTORY:
					id += "DefenderVictory";
					break;
				case PEACE:
					id += "Peace";
					break;
				case DESTROYED:
					id += "Destroyed";
					break;
				case CANCELLED:
					id += "Cancelled";
					break;
				default:
					id += "Other";
					break;
			}
			info.addPara(getString(id), getBulletColorForMode(mode), 0);
			
			if (timerForDecision != null) {
				String str = getString("bulletDaysToTakeControl");
				info.addPara(str, 0, getBulletColorForMode(mode), Misc.getHighlightColor(), String.format("%.0f", timerForDecision));;
			}
		}
	}
	
	public Map<String, String> getFactionSubs() {
		Map<String, String> sub = new HashMap<>();
		sub.put("$attacker", attacker.faction.getDisplayName());
		sub.put("$theAttacker", attacker.faction.getDisplayNameWithArticle());
		sub.put("$defender", defender.faction.getDisplayNameWithArticle());
		sub.put("$theDefender", defender.faction.getDisplayNameWithArticle());
		sub.put("$market", market.getName());
		sub.put("$location", market.getContainingLocation().getNameWithLowercaseType());
		return sub;
	}
	
	public TooltipMakerAPI generateViewModeButton(CustomPanelAPI buttonRow, String nameId, ViewMode mode,
			Color base, Color bg, Color bright, TooltipMakerAPI rightOf) 
	{
		TooltipMakerAPI holder = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		
		ButtonAPI button = holder.addAreaCheckbox(getString(nameId), mode, base, bg, bright,
				VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		button.setChecked(mode == this.viewMode);
		
		if (rightOf != null) {
			buttonRow.addUIElement(holder).rightOfTop(rightOf, 4);
		} else {
			buttonRow.addUIElement(holder).inTL(0, 3);
		}
		
		return holder;
	}
	
	public void generateIntro(CustomPanelAPI outer, TooltipMakerAPI info, float width, float pad) {
		int buttonWidth = 160;
		
		info.addImages(width, 128, pad, pad, attacker.faction.getLogo(), defender.faction.getLogo());
				
		FactionAPI fc = getFactionForUIColors();
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor(), bright = fc.getBrightUIColor();
		
		String str = getString("intelDesc_intro");
		Map<String, String> sub = getFactionSubs();
		
		str = StringHelper.substituteTokens(str, sub);
		
		LabelAPI label = info.addPara(str, pad);
		label.setHighlight(attacker.faction.getDisplayNameWithArticleWithoutArticle(),
				market.getName(),
				defender.faction.getDisplayNameWithArticleWithoutArticle());
		label.setHighlightColors(attacker.faction.getBaseUIColor(),
				Misc.getHighlightColor(),
				defender.faction.getBaseUIColor());
		
		str = getString("intelDesc_unitSize");
		info.addPara(str, pad, Misc.getHighlightColor(), Misc.ucFirst(unitSize.getName()), 
				unitSize.minSize + "", unitSize.avgSize + "", unitSize.maxSize + "", 
				String.format("%.2f", unitSize.damMult) + "×");
		
		// turn num and progress row
		{
			float barHeight = 18;
			float barWidth = 240;
			str = getString("intelDesc_round");
			CustomPanelAPI turnRow = outer.createCustomPanel(width, barHeight, null);
			TooltipMakerAPI turnText = turnRow.createUIElement(100, barHeight, false);
						
			turnText.addPara(str, 0, Misc.getHighlightColor(), turnNum + "");
			turnRow.addUIElement(turnText).inTL(0, 0);
			
			float timePassed = interval.getElapsed();
			float timeNeeded = interval.getIntervalDuration();
			
			float progress = (timePassed/timeNeeded) * 100;
			float remainingHours = (timeNeeded - timePassed)*24;
			str = String.format(getString("intelDesc_roundDaysRemaining"), remainingHours);
			
			TooltipMakerAPI turnBarHolder = turnRow.createUIElement(barWidth, barHeight, false);		
			ProgressBar.addBarLTR(turnBarHolder, str, Alignment.MID, null, barWidth, barHeight, 1, 1, progress, 0, null, 
					Misc.getGrayColor(), Color.black, Misc.getDarkPlayerColor());
			turnRow.addUIElement(turnBarHolder).rightOfTop(turnText, 0);
			
			info.addCustom(turnRow, pad);
		}
		
		
		// join battle options
		if (playerIsAttacker == null && isFleetInRange(Global.getSector().getPlayerFleet())) 
		{
			int height = 32;
			
			str = getString("btnJoinAttacker");
			CustomPanelAPI joinAttackerPanel = outer.createCustomPanel(width, height, null);
			TooltipMakerAPI image = joinAttackerPanel.createUIElement(height, height, false);
			image.addImage(attacker.getFaction().getCrest(), height, 0);
			joinAttackerPanel.addUIElement(image).inTL(0, 0);
			TooltipMakerAPI buttonHolder = joinAttackerPanel.createUIElement(buttonWidth, height, false);
			ButtonAPI button = buttonHolder.addButton(str, BUTTON_JOIN_ATTACKER, 
					attacker.getFaction().getBaseUIColor(),
					attacker.getFaction().getDarkUIColor(),
					buttonWidth, height - 8 , 4);
			if (attacker.getFaction().isHostileTo(Factions.PLAYER))
				button.setEnabled(false);
			joinAttackerPanel.addUIElement(buttonHolder).rightOfTop(image, 4);
			info.addCustom(joinAttackerPanel, 10);
			
			str = getString("btnJoinDefender");
			CustomPanelAPI joinDefenderPanel = outer.createCustomPanel(width, height, null);
			image = joinDefenderPanel.createUIElement(height, height, false);
			image.addImage(defender.getFaction().getCrest(), height, 0);
			joinDefenderPanel.addUIElement(image).inTL(0, 0);
			buttonHolder = joinDefenderPanel.createUIElement(buttonWidth, height, false);
			button = buttonHolder.addButton(str, BUTTON_JOIN_DEFENDER, 
					defender.getFaction().getBaseUIColor(),
					defender.getFaction().getDarkUIColor(),
					buttonWidth, height - 8 , 4);
			if (defender.getFaction().isHostileTo(Factions.PLAYER))
				button.setEnabled(false);
			joinDefenderPanel.addUIElement(buttonHolder).rightOfTop(image, 4);
			info.addCustom(joinDefenderPanel, 0);
		}
		
		if (ExerelinModPlugin.isNexDev) {
			GBDebugCommands.addDebugButtons(this, outer, info, width, buttonWidth);
		}
		
		// view mode buttons
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		
		TooltipMakerAPI btnHolder1 = generateViewModeButton(buttonRow, "btnViewUnits", 
				ViewMode.UNITS, base, bg, bright, null);		
		TooltipMakerAPI btnHolder2 = generateViewModeButton(buttonRow, "btnViewAbilities", 
				ViewMode.ABILITIES, base, bg, bright, btnHolder1);		
		TooltipMakerAPI btnHolder3 = generateViewModeButton(buttonRow, "btnViewInfo", 
				ViewMode.INFO, base, bg, bright, btnHolder2);		
		TooltipMakerAPI btnHolder4 = generateViewModeButton(buttonRow, "btnViewLog", 
				ViewMode.LOG, base, bg, bright, btnHolder3);
		TooltipMakerAPI btnHolder5 = generateViewModeButton(buttonRow, "btnViewHelp", 
				ViewMode.HELP, base, bg, bright, btnHolder4);
		
		info.addCustom(buttonRow, 3);
	}
	
	protected static String getCommoditySprite(String commodityId) {
		return Global.getSettings().getCommoditySpec(commodityId).getIconName();
	}

	protected static String getCommodityName(String commodityId) {
		return Global.getSettings().getCommoditySpec(commodityId).getLowerCaseName();
	}
	
	public TooltipMakerAPI addResourceSubpanel(String commodityId, CustomPanelAPI resourcePanel,
			List<TooltipMakerAPI> elements, float width, int maxPerRow)
	{
		TooltipMakerAPI subpanel = resourcePanel.createUIElement(width, 32, false);
		TooltipMakerAPI image = subpanel.beginImageWithText(getCommoditySprite(commodityId), 32);
		float amount = Global.getSector().getPlayerFleet().getCargo().getCommodityQuantity(commodityId);
		image.addPara((int)(amount) + "", 0);
		subpanel.addImageWithText(0);
		NexUtilsGUI.placeElementInRows(resourcePanel, subpanel, elements, maxPerRow, 0);
		elements.add(subpanel);
		
		return subpanel;
	}

	public CustomPanelAPI generateResourcePanel(CustomPanelAPI outer, float width) {
		int subWidth = 108;
		int numPerRow = (int)(width/subWidth);

		Set<String> commodities = new LinkedHashSet<>();
		commodities.add(Commodities.SUPPLIES);
		commodities.add(Commodities.FUEL);
		for (GroundUnitDef def : GroundUnitDef.UNIT_DEFS) {
			if (!def.playerCanCreate) continue;
			if (!def.shouldShow()) continue;
			if (def.personnel != null) commodities.addAll(CrewReplacerUtils.getAllCommodityIdsForJob(def.personnel.crewReplacerJobId, def.personnel.commodityId));
			if (def.equipment != null) commodities.addAll(CrewReplacerUtils.getAllCommodityIdsForJob(def.equipment.crewReplacerJobId, def.equipment.commodityId));
		}

		int numRows = (int)Math.ceil(commodities.size()/(float)numPerRow);

		CustomPanelAPI resourcePanel = outer.createCustomPanel(width, 32 * numRows, null);

		List<TooltipMakerAPI> elements = new ArrayList<>();

		for (String commodity : commodities) {
			addResourceSubpanel(commodity, resourcePanel, elements, subWidth, numPerRow);
		}

		return resourcePanel;
	}
	
	/**
	 * Draws the subpanel listing player units.
	 * @param info
	 * @param outer
	 * @param width
	 * @param opad
	 */
	public void generateUnitDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width, float opad) 
	{
		float pad = 3;
		info.addSectionHeading(getString("unitPanel_header"), Alignment.MID, opad);
		
		// movement points display
		if (playerIsAttacker != null) {
			TooltipMakerAPI movementPoints = info.beginImageWithText(Global.getSettings().
					getCommoditySpec(Commodities.SUPPLIES).getIconName(), 24);
			int maxPoints = getSide(playerIsAttacker).getMovementPointsPerTurn().getModifiedInt();
			int available = maxPoints - getSide(playerIsAttacker).getMovementPointsSpent().getModifiedInt();
			Color h = Misc.getHighlightColor();
			if (available <= 0) h = Misc.getNegativeHighlightColor();
			else if (available >= maxPoints) h = Misc.getPositiveHighlightColor();

			String str = getString("unitPanel_movementPoints") + ": %s / %s";
			movementPoints.addPara(str, pad, h, available + "", maxPoints + "");

			info.addImageWithText(pad);
			info.addTooltipToPrevious(new TooltipCreator() {
				@Override
				public boolean isTooltipExpandable(Object tooltipParam) {
						return false;
					}
					public float getTooltipWidth(Object tooltipParam) {
						return 400;	// FIXME magic number
					}
					public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) 
					{
						String str = getString("unitPanel_movementPoints_tooltip1");
						tooltip.addPara(str, 0);

						str = getString("unitPanel_movementPoints_tooltip2");
						tooltip.addPara(str, 10);

						str = getString("unitPanel_movementPoints_tooltip3");
						tooltip.addPara(str, 10);

						tooltip.addStatModGrid(360, 60, 10, 3, getSide(playerIsAttacker).getMovementPointsPerTurn(), 
								true, NexUtils.getStatModValueGetter(true, 0));

					}			
			}, TooltipMakerAPI.TooltipLocation.BELOW);

			// cargo display
			info.addPara(getString("unitPanel_resources"), pad);

			CustomPanelAPI resourcePanel = generateResourcePanel(outer, width);
			info.addCustom(resourcePanel, pad);

			// player AI buttons
			FactionAPI fc = getFactionForUIColors();
			Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor();

			CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);

			TooltipMakerAPI btnHolder1 = buttonRow.createUIElement(180, 
					VIEW_BUTTON_HEIGHT, false);
			btnHolder1.addButton(getString("btnCancelMoves"), BUTTON_CANCEL_MOVES, base,
					bg, 180, VIEW_BUTTON_HEIGHT, 0);
			buttonRow.addUIElement(btnHolder1).inTL(0, 3);

			TooltipMakerAPI btnHolder2 = buttonRow.createUIElement(160, 
					VIEW_BUTTON_HEIGHT, false);
			btnHolder2.addButton(getString("btnRunPlayerAI"), BUTTON_AUTO_MOVE,	base,
					bg, 160, VIEW_BUTTON_HEIGHT, 0);
			String tooltipStr = getString("btnRunPlayerAI_tooltip");
			TooltipCreator tt = NexUtilsGUI.createSimpleTextTooltip(tooltipStr, 360);
			btnHolder2.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
			buttonRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);

			TooltipMakerAPI btnHolder3 = buttonRow.createUIElement(240, 
					VIEW_BUTTON_HEIGHT, false);
			ButtonAPI check = btnHolder3.addAreaCheckbox(getString("btnTogglePlayerAI"), BUTTON_AUTO_MOVE_TOGGLE, 
					base, bg, fc.getBrightUIColor(),
					240, VIEW_BUTTON_HEIGHT, 0);
			check.setChecked(playerData.autoMoveAtEndTurn);
			btnHolder3.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
			buttonRow.addUIElement(btnHolder3).rightOfTop(btnHolder2, 4);

			TooltipMakerAPI btnHolder4 = buttonRow.createUIElement(240,
					VIEW_BUTTON_HEIGHT, false);
			check = btnHolder4.addAreaCheckbox(getString("btnPlayerAICanDrop"), BUTTON_AUTO_MOVE_CAN_DROP,
					base, bg, fc.getBrightUIColor(),
					240, VIEW_BUTTON_HEIGHT, 0);
			check.setChecked(playerData.autoMoveAllowDrop);
			tooltipStr = getString("btnPlayerAICanDrop_tooltip");
			tt = NexUtilsGUI.createSimpleTextTooltip(tooltipStr, 360);
			btnHolder4.addTooltipToPrevious(tt, TooltipMakerAPI.TooltipLocation.BELOW);
			buttonRow.addUIElement(btnHolder4).rightOfTop(btnHolder3, 4);

			info.addCustom(buttonRow, 0);
		}
				
		// unit cards
		int CARDS_PER_ROW = (int)(width/(GroundUnit.PANEL_WIDTH + GroundUnit.PADDING_X));
		List<GroundUnit> listToRead = playerData.getUnits();	// units whose cards should be shown
		if (showAllUnits) {
			listToRead = getAllUnits();
		}
		int numCards = listToRead.size();
		if (listToRead.size() < MAX_PLAYER_UNITS)
			numCards++;	// for the "create unit" card
		
		int NUM_ROWS = (int)Math.ceil((float)numCards/CARDS_PER_ROW);
		//log.info("Number of rows: " + NUM_ROWS);
		//log.info("Cards per row: " + CARDS_PER_ROW);
		
		CustomPanelAPI unitPanel = outer.createCustomPanel(width, NUM_ROWS * (GroundUnit.PANEL_HEIGHT + pad), null);
		
		//TooltipMakerAPI test = unitPanel.createUIElement(64, 64, true);
		//test.addPara("wololo", 0);
		//unitPanel.addUIElement(test).inTL(0, 0);
		
		List<CustomPanelAPI> unitCards = new ArrayList<>();
		try {
			for (GroundUnit unit : listToRead) {
				CustomPanelAPI unitCard = unit.createUnitCard(unitPanel, false);
				//log.info("Created card for " + unit.name);

				NexUtilsGUI.placeElementInRows(unitPanel, unitCard, unitCards, CARDS_PER_ROW, GroundUnit.PADDING_X);
				unitCards.add(unitCard);
			}
			if (playerIsAttacker != null && listToRead.size() < MAX_PLAYER_UNITS) {
				CustomPanelAPI newCard = GroundUnit.createBlankCard(unitPanel, unitSize);
				NexUtilsGUI.placeElementInRows(unitPanel, newCard, unitCards, CARDS_PER_ROW, GroundUnit.PADDING_X);
			}
			
		} catch (Exception ex) {
			log.error("Failed to display unit cards", ex);
		}
				
		info.addCustom(unitPanel, pad);
	}
	
	public void populateModifiersDisplay(CustomPanelAPI outer, TooltipMakerAPI disp, 
			float width, float pad, Boolean isAttacker) 
	{
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.addModifierEntry(disp, outer, width, pad, isAttacker);
		}
	}
	
	/**
	 * Draws the subpanel listing modifier effects applying to the battle.
	 * @param info
	 * @param panel
	 * @param width
	 * @param pad
	 */
	public void generateModifiersDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{		
		// Holds the display for each faction, added to 'info'
		CustomPanelAPI strPanel = panel.createCustomPanel(width, MODIFIER_PANEL_HEIGHT, null);
		
		float subWidth = width/3;
		try {
			TooltipMakerAPI dispAtk = strPanel.createUIElement(subWidth, MODIFIER_PANEL_HEIGHT, true);
			strPanel.addUIElement(dispAtk).inTL(0, 0);
			TooltipMakerAPI dispCom = strPanel.createUIElement(subWidth, MODIFIER_PANEL_HEIGHT, true);
			strPanel.addUIElement(dispCom).inTMid(0);
			TooltipMakerAPI dispDef = strPanel.createUIElement(subWidth, MODIFIER_PANEL_HEIGHT, true);
			strPanel.addUIElement(dispDef).inTR(0, 0);

			FactionAPI neutral = Global.getSector().getFaction(Factions.NEUTRAL);

			dispAtk.addSectionHeading(getString("intelDesc_headerAttackerMod"), 
					attacker.faction.getBaseUIColor(), attacker.faction.getDarkUIColor(), Alignment.MID, pad);
			dispCom.addSectionHeading(getString("intelDesc_headerCommonMod"), 
					neutral.getBaseUIColor(), neutral.getDarkUIColor(), Alignment.MID, pad);
			dispDef.addSectionHeading(getString("intelDesc_headerDefenderMod"),
					defender.faction.getBaseUIColor(), defender.faction.getDarkUIColor(), Alignment.MID, pad);
			
		
			populateModifiersDisplay(strPanel, dispAtk, subWidth, 3, true);
			populateModifiersDisplay(strPanel, dispCom, subWidth, 3, null);
			populateModifiersDisplay(strPanel, dispDef, subWidth, 3, false);
		} catch (Exception ex) {
			log.error("Failed to display modifiers", ex);
		}
		
		info.addCustom(strPanel, pad);
	}
	
	/**
	 * Draws the subpanel with the player abilities.
	 * @param info
	 * @param outer
	 * @param width
	 * @param pad
	 */
	public void generateAbilityDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width, float pad) 
	{
		if (playerIsAttacker == null) return;
		
		float opad = 10;
		FactionAPI fc = getFactionForUIColors();
		PersonAPI player = Global.getSector().getPlayerPerson();
		
		Color base = fc.getBaseUIColor(), bg = fc.getDarkUIColor();
		info.addSectionHeading(getString("commandPanel_header1"), base, bg, Alignment.MID, opad);
				
		// abilities
		int CARDS_PER_ROW = (int)(width/(AbilityPlugin.PANEL_WIDTH + GroundUnit.PADDING_X));
		List<AbilityPlugin> abilities = getSide(playerIsAttacker).abilities;
		List<AbilityPlugin> showableAbilities = new ArrayList<>();
		
		for (AbilityPlugin plugin : abilities) {
			Pair<String, Map<String, Object>> disableReason = plugin.getDisabledReason(player);
			if (disableReason != null && !plugin.showIfDisabled(disableReason))
				continue;
			showableAbilities.add(plugin);
		}
		
		int numCards = showableAbilities.size();
		
		int NUM_ROWS = (int)Math.ceil((float)numCards/CARDS_PER_ROW);
		CustomPanelAPI abilityPanel = outer.createCustomPanel(width, NUM_ROWS * AbilityPlugin.PANEL_HEIGHT, null);
				
		List<CustomPanelAPI> abilityCards = new ArrayList<>();
		try {
			for (AbilityPlugin plugin : showableAbilities) {
				Pair<String, Map<String, Object>> disableReason = plugin.getDisabledReason(player);
				if (disableReason != null && !plugin.showIfDisabled(disableReason))
					continue;
				
				CustomPanelAPI abilityCard = plugin.createAbilityCard(abilityPanel);
				//log.info("Created card for " + unit.name);

				NexUtilsGUI.placeElementInRows(abilityPanel, abilityCard, abilityCards, CARDS_PER_ROW, 3);
				abilityCards.add(abilityCard);
			}			
		} catch (Exception ex) {
			log.error("Failed to display ability cards", ex);
		}
		info.addCustom(abilityPanel, pad);
	}
	
	/**
	 * Draws the subpanel with the list of industries and the forces on them.
	 * @param info
	 * @param panel
	 * @param width
	 */
	public void generateIndustryDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width) 
	{
		try {
			if (Global.getSettings().getBoolean("nex_useGroundBattleIndustryMap")) { // && industries.size() <= 16) {
				// render map
				MarketMapDrawer map = new MarketMapDrawer(this, panel, width - 12);
				map.init();
				CustomPanelAPI mapPanel = map.getPanel();
				info.addCustom(mapPanel, 10);
			}
			else {
				info.beginTable(Global.getSector().getPlayerFaction(), 0,
						getString("industryPanel_header_industry"), IndustryForBattle.COLUMN_WIDTH_INDUSTRY,
						//getString("industryPanel_header_heldBy"), IndustryForBattle.COLUMN_WIDTH_CONTROLLED_BY,
						getString("industryPanel_header_attacker"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL,
						getString("industryPanel_header_defender"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL
				);
				info.addTable("", 0, 10);
				info.addSpacer(4);

				for (IndustryForBattle ifb : industries) {
					ifb.renderPanel(panel, info, width);
				}
			}
		} catch (Exception ex) {
			log.info("Failed to generate industry display", ex);
		}
	}
	
	public void generateLogDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width) 
	{
		info.addSectionHeading(getString("logHeader"), Alignment.MID, 10);
		try {
			float logPanelHeight = 240;
			//if (this.outcome != null) // endgame display
			//	logPanelHeight = 600;
			CustomPanelAPI logPanel = outer.createCustomPanel(width, logPanelHeight, null);
			TooltipMakerAPI scroll = logPanel.createUIElement(width, logPanelHeight, true);
			for (int i=battleLog.size() - 1; i>=0; i--) {
				int logTurn = battleLog.get(i).turn;
				battleLog.get(i).writeLog(logPanel, scroll, width - 4);
				if (turnNum - logTurn > LOG_MAX_TURNS_AGO) break;
			}

			logPanel.addUIElement(scroll);
			info.addCustom(logPanel, 3);
		} catch (Exception ex) {
			log.error("Failed to create log display", ex);
		}		
	}
	
	/**
	 * Draws the help screen.
	 * @param info
	 * @param outer
	 * @param width
	 */
	protected void generateHelpDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width)
	{
		float opad = 10;
		float pad = 3;
		Color h = Misc.getHighlightColor();
		String bullet = " - ";
		
		info.addSectionHeading(getString("helpHeader"), Alignment.MID, opad);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara0Title"), opad);
		info.setParaFontDefault();
		TooltipMakerAPI section = info.beginImageWithText("graphics/factions/crest_player_flag.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara0-1"), pad);
		section.addPara(getString("helpPara0-2"), pad);
		section.addPara(getString("helpPara0-3"), pad);
		info.addImageWithText(pad);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara1Title"), opad);
		info.setParaFontDefault();
		section = info.beginImageWithText("graphics/exerelin/icons/intel/invasion.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara1-1"), pad);
		section.addPara(getString("helpPara1-2"), pad);
		section.addPara(getString("helpPara1-3"), pad);
		section.addPara(getString("helpPara1-4"), pad);
		info.addImageWithText(pad);
		
		CustomPanelAPI help2Holder = outer.createCustomPanel(width, 123, null);
		TooltipMakerAPI help2Text = help2Holder.createUIElement(500, 123, false);
		help2Text.setParaInsigniaLarge();
		help2Text.addPara(getString("helpPara2Title"), 0);
		help2Text.setParaFontDefault();
		section = help2Text.beginImageWithText("graphics/icons/cargo/supplies.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara2-1"), pad);
		section.addPara(getString("helpPara2-2"), pad);
		section.addPara(getString("helpPara2-3"), pad);
		section.addPara(getString("helpPara2-4"), pad);
		help2Text.addImageWithText(pad);
		help2Holder.addUIElement(help2Text).inTL(0, 0);
		TooltipMakerAPI help2Img = help2Holder.createUIElement(223, 123, false);
		help2Img.addImage(Global.getSettings().getSpriteName("nex_groundbattle", "help_unitCard"), pad * 2);
		help2Holder.addUIElement(help2Img).rightOfTop(help2Text, 8);
		info.addCustom(help2Holder, opad);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara3Title"), opad * 3);
		info.setParaFontDefault();
		
		for (int i=1; i<=3; i++) {
			ForceType type = ForceType.values()[i - 1];
			info.setBulletedListMode(bullet);
			section = info.beginImageWithText(type.getCommoditySprite(), 32);
			String name = Misc.ucFirst(type.getName());
			section.addPara(getString("helpPara3-" + i), pad, h, name);
			info.addImageWithText(0);
		}
		unindent(info);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara4Title"), opad);
		info.setParaFontDefault();
		section = info.beginImageWithText("graphics/exerelin/icons/intel/swiss_flag.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara4-1"), pad);
		section.addPara(getString("helpPara4-2"), pad);
		section.addPara(getString("helpPara4-3"), pad);
		info.addImageWithText(pad);
		unindent(info);
		
		info.setParaInsigniaLarge();
		info.addPara(getString("helpPara5Title"), opad);
		info.setParaFontDefault();
		section = info.beginImageWithText("graphics/icons/skills/leadership.png", 32);
		section.setBulletedListMode(bullet);
		section.addPara(getString("helpPara5-1"), pad);
		section.addPara(getString("helpPara5-2"), pad);
		info.addImageWithText(pad);
		unindent(info);
	}
	
	public void addLogEvent(GroundBattleLog log) {
		battleLog.add(log);
	}
	
	public void writeTurnBullets(TooltipMakerAPI info) {
		List<Pair<String, Integer>> lossesSortable = new ArrayList<>();
		
		if (!playerData.getLossesLastTurnV2().isEmpty()) {
			String str = getString("bulletLossesLastTurn");
			for (String commodityId : playerData.getLossesLastTurnV2().keySet()) {
				int thisLoss = playerData.getLossesLastTurnV2().get(commodityId);
				lossesSortable.add(new Pair<>(commodityId, thisLoss));
			}
			Collections.sort(lossesSortable, new NexUtils.PairWithIntegerComparator(true));

			List<String> strings = new ArrayList<>();
			for (Pair<String, Integer> loss : lossesSortable) {
				strings.add(loss.two + " " + GroundBattleIntel.getCommodityName(loss.one).toLowerCase());
			}
			str = String.format(str, StringHelper.writeStringCollection(strings, false, true));
			info.addPara(str, 0);
		}
		if (!abilitiesUsedLastTurn.isEmpty()) {
			for (Pair<Boolean, AbilityPlugin> entry : abilitiesUsedLastTurn) {
				String str = getString("bulletAbilityUsed");
				String user = StringHelper.getString(entry.one ? "attacker" : "defender", true);
				String abilityName = entry.two.getDef().name;
				
				LabelAPI label = info.addPara(str, 0, Misc.getHighlightColor(), user, abilityName);
				label.setHighlightColors(Misc.getHighlightColor(), entry.two.getDef().color);
			}
		}
		
		if (turnNum == getMilitiaUnleashTurn() - 1) {
			String str = getString("bulletMilitiaUnleashed");
			info.addPara(str, 0, Misc.getHighlightColor(), str);
		}
	}
	
	public void addPostVictoryButtons(CustomPanelAPI outer, TooltipMakerAPI info, float width) 
	{
		float opad = 10;
		String str = StringHelper.substituteToken(getString("intelDesc_postVictoryOptions"), "$market", market.getName());
		info.addPara(str, opad);
		
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		TooltipMakerAPI btnHolder1 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH * 2, 
				VIEW_BUTTON_HEIGHT, false);
		str = StringHelper.substituteToken(getString("btnAndrada"), "$market", market.getName());
		btnHolder1.addButton(str, BUTTON_ANDRADA, VIEW_BUTTON_WIDTH * 2, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder1).inTL(0, 0);
		
		TooltipMakerAPI btnHolder2 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH * 2, 
				VIEW_BUTTON_HEIGHT, false);
		ButtonAPI btn = btnHolder2.addButton(getString("btnGovernorship"), 
				BUTTON_GOVERNORSHIP, VIEW_BUTTON_WIDTH * 2, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);
		info.addCustom(buttonRow, opad);
		
		if (market.getMemoryWithoutUpdate().getBoolean(Nex_BuyColony.MEMORY_KEY_NO_BUY)) {
			btn.setEnabled(false);
			info.addPara(getString("intelDesc_postVictoryOptionsNoBuy"), 3);
		}
		
		str = getString("intelDesc_postVictoryOptionsTime");
		info.addPara(str, opad, Misc.getHighlightColor(), String.format("%.0f", timerForDecision));
	}
	
	public void generatePostBattleDisplay(CustomPanelAPI outer, TooltipMakerAPI info, float width, float height) {
		float pad = 3;
		float opad = 10;
		
		info.addImages(width, 128, pad, pad, attacker.faction.getLogo(), defender.faction.getLogo());
		if (outcome != null) {
			String id = "descOutcome";
			switch (outcome) {
				case ATTACKER_VICTORY:
					id += "AttackerVictory";
					break;
				case DEFENDER_VICTORY:
					id += "DefenderVictory";
					break;
				case PEACE:
					id += "Peace";
					break;
				case DESTROYED:
					id += "Destroyed";
					break;
				case CANCELLED:
					id += "Cancelled";
					break;
				default:
					id += "Other";
					break;
			}
			String str = getString(id);
			str = StringHelper.substituteTokens(str, getFactionSubs());
			info.addPara(str, opad);
		}
		if (outcome == BattleOutcome.ATTACKER_VICTORY) {
			info.addSectionHeading(StringHelper.getString("exerelin_markets", "intelTransferFactionSizeHeader"),
				attacker.getFaction().getBaseUIColor(), attacker.getFaction().getDarkUIColor(), Alignment.MID, opad);
			
			MarketTransferIntel.addFactionCurrentInfoPara(info, attacker.getFaction().getId(), opad);
			MarketTransferIntel.addFactionCurrentInfoPara(info, defender.getFaction().getId(), opad);
		}
		
		info.addSectionHeading(getString("intelDesc_otherNotes"), attacker.getFaction().getBaseUIColor(), 
					attacker.getFaction().getDarkUIColor(), Alignment.MID, opad);
		
		if (outcome != BattleOutcome.CANCELLED && (playerIsAttacker != null || !playerData.getSentToStorage().isEmpty())) 
		{
			SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
			if (storage != null) {
				info.addPara(getString("intelDesc_lootAndSurvivors"), opad);
				if (playerData.getLoot() != null) {
					info.showCargo(playerData.getLoot(), 10, true, opad);
				}					
				else {
					info.addPara(getString("intelDesc_localStorage"), opad);
					info.showCargo(storage.getCargo(), 10, true, opad);
				}
			} else {
				info.addPara(getString("intelDesc_lootAndSurvivorsDirect"), opad);
			}
		}
		FactionAPI commission = Misc.getCommissionFaction();
		if (playerInitiated && outcome == BattleOutcome.ATTACKER_VICTORY && commission != null) 
		{
			// Andrada and governorship buttons here
			if (playerData.andradaRepChange != null) {
				String str = getString("intelDesc_andrada");
				str = StringHelper.substituteToken(str, "$market", market.getName());
				str = StringHelper.substituteFactionTokens(str, commission);
				info.addPara(str, opad);
				CoreReputationPlugin.addAdjustmentMessage(playerData.andradaRepChange.delta, 
						commission, null, null, null, info, Misc.getTextColor(), true, 3);
			} else if (playerData.governorshipPrice != null) {
				String str = getString("intelDesc_governorship");
				str = StringHelper.substituteToken(str, "$market", market.getName());
				info.addPara(str, opad, Misc.getHighlightColor(), Misc.getDGSCredits(playerData.governorshipPrice));
			} 
			// the extra conditions check if player already owns the market somehow, or purchased governorship through another channel
			else if (timerForDecision != null && !market.getFaction().isPlayerFaction() && !market.isPlayerOwned()) 
			{
				addPostVictoryButtons(outer, info, width);
			}
		}
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return buttonId == BUTTON_ANDRADA || buttonId == BUTTON_GOVERNORSHIP 
				|| buttonId == BUTTON_JOIN_ATTACKER || buttonId == BUTTON_JOIN_DEFENDER
				|| buttonId instanceof Pair;
	}
	
	@Override
	public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
		String str;
		if (buttonId == BUTTON_ANDRADA) {
			if (wasPlayerMarket()) {
				str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "takeForSelfNoWarning",
						"$market", market.getName());
			} else {
				str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "takeForSelfWarning",
						"$market", market.getName());
			}
			prompt.addPara(str, 0);
			return;
		}
		if (buttonId == BUTTON_GOVERNORSHIP) {
			MutableStat cost = Nex_BuyColony.getValue(market, false, true);
			str = getString("btnGovernorshipConfirmPrompt");
			str = StringHelper.substituteToken(str, "$market", market.getName());
			int curr = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
			Color hl = cost.getModifiedValue() > curr ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
			prompt.addPara(str, 0, hl, Misc.getDGSCredits(cost.getModifiedValue()), Misc.getDGSCredits(curr));
			prompt.addStatModGrid(480, 80, 100, 10, cost, true, NexUtils.getStatModValueGetter(true, 0));
			return;
		}
		if (buttonId == BUTTON_JOIN_ATTACKER || buttonId == BUTTON_JOIN_DEFENDER) {
			boolean isAttacker = buttonId == BUTTON_JOIN_ATTACKER;
			
			FactionAPI friend = getSide(isAttacker).getFaction();
			FactionAPI enemy = getSide(!isAttacker).getFaction();
			String friendName = friend.getDisplayNameWithArticleWithoutArticle();
			String enemyName = enemy.getDisplayNameWithArticleWithoutArticle();
			String theFriendName = friend.getDisplayNameWithArticle();
			String theEnemyName = enemy.getDisplayNameWithArticle();
			
			// no stealth joining for now
			boolean tOn = true;	//Global.getSector().getPlayerFleet().isTransponderOn();
			
			str = getString(tOn ? "btnJoinConfirmPrompt" : "btnJoinTOffConfirmPrompt");
			str = StringHelper.substituteToken(str, "$theFriend", theFriendName);
			str = StringHelper.substituteToken(str, "$theEnemy", theEnemyName);
			
			LabelAPI label = prompt.addPara(str, 0);
			label.setHighlight(friendName, enemyName);
			label.setHighlightColors(friend.getBaseUIColor(), enemy.getBaseUIColor());
		}
		
		if (buttonId instanceof Pair) {
			try {
				Pair pair = (Pair)buttonId;
				String action = (String)pair.one;
				if (pair.two instanceof IndustryForBattle) {
					IndustryForBattle ifb = (IndustryForBattle)pair.two;
					switch (action) {
						case "loot":
							str = getString("btnLootConfirmPrompt");
							prompt.addPara(str, 0);
							String aiCore = ifb.getIndustry().getAICoreId();
							SpecialItemData special = ifb.getIndustry().getSpecialItem();
							if (aiCore != null)
								prompt.addPara(" - " + StringHelper.getCommodityName(aiCore), 0);
							if (special != null) {
								str = Global.getSettings().getSpecialItemSpec(special.getId()).getName();
								prompt.addPara(" - " + str, 0);
							}
							break;
					}
				}
			} catch (Exception ex) {
				// do nothing?
			}
		}
	}
		
	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		
		if (GBDebugCommands.processDebugButtons(this, ui, buttonId)) {
			return;
		}
		
		if (buttonId instanceof ViewMode) {
			viewMode = (ViewMode)buttonId;
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId instanceof GroundUnit) {
			ui.showDialog(market.getPrimaryEntity(), new UnitOrderDialogPlugin(this, (GroundUnit)buttonId, ui));
			return;
		}
		if (buttonId instanceof UnitQuickMoveHax) {
			UnitOrderDialogPlugin dialog = new UnitOrderDialogPlugin(this, ((UnitQuickMoveHax)buttonId).unit, ui);
			dialog.setQuickMove(true);
			ui.showDialog(market.getPrimaryEntity(), dialog);
			return;
		}
		if (buttonId instanceof AbilityPlugin) {
			ui.showDialog(market.getPrimaryEntity(), new AbilityDialogPlugin((AbilityPlugin)buttonId, ui));
		}
		if (buttonId instanceof GroundUnitDef) {
			createPlayerUnit(((GroundUnitDef)buttonId).id);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_ANDRADA) {
			handleAndradaOption();
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_GOVERNORSHIP) {
			handleGovernorshipPurchase();
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_AUTO_MOVE) {
			runAI(playerIsAttacker, true, playerData.autoMoveAllowDrop);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_AUTO_MOVE_TOGGLE) {
			playerData.autoMoveAtEndTurn = !playerData.autoMoveAtEndTurn;
			return;
		}
		if (buttonId == BUTTON_AUTO_MOVE_CAN_DROP) {
			playerData.autoMoveAllowDrop = !playerData.autoMoveAllowDrop;
			return;
		}
		if (buttonId == BUTTON_CANCEL_MOVES) {
			for (GroundUnit unit : playerData.getUnits()) {
				unit.cancelMove();
			}
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_JOIN_ATTACKER) {
			playerJoinBattle(true, true);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == BUTTON_JOIN_DEFENDER) {
			playerJoinBattle(false, true);
			ui.updateUIForItem(this);
			return;
		}
		
		if (buttonId instanceof Pair) {
			try {
				Pair pair = (Pair)buttonId;
				String action = (String)pair.one;
				if (pair.two instanceof IndustryForBattle) {
					IndustryForBattle ifb = (IndustryForBattle)pair.two;
					switch (action) {
						case "loot":
							loot(ifb);
							ui.updateUIForItem(this);
							break;
					}
				}
			} catch (Exception ex) {
				log.error("Button press failed", ex);
			}
		}
	}
	
	// adapted from Starship Legends' BattleReport
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float opad = 10;
		FactionAPI faction = market.getFaction();
		
		TooltipMakerAPI outer = panel.createUIElement(width, height, true);
		
		outer.addSectionHeading(getSmallDescriptionTitle(), faction.getBaseUIColor(), 
				faction.getDarkUIColor(), Alignment.MID, opad);
		
		if (outcome != null) {
			generatePostBattleDisplay(panel, outer, width, height);
			generateLogDisplay(outer, panel, width - 14);
			generateIndustryDisplay(outer, panel, width);
			
			panel.addUIElement(outer).inTL(0, 0);
			return;
		}
		
		if (viewMode == null) viewMode = ViewMode.UNITS;
		
		generateIntro(panel, outer, width, opad);
		
		if (viewMode == ViewMode.HELP) {
			generateHelpDisplay(outer, panel, width);
			panel.addUIElement(outer).inTL(0, 0);
			return;
		}
		
		if (viewMode == ViewMode.UNITS) {
			if (Global.getSettings().isDevMode() || playerIsAttacker != null || showAllUnits)
				generateUnitDisplay(outer, panel, width, opad);
		} 
		else if (viewMode == ViewMode.ABILITIES) {
			generateAbilityDisplay(outer, panel, width, opad);
		}
		else if (viewMode == ViewMode.INFO) {
			generateModifiersDisplay(outer, panel, width, opad);
		}
		else if (viewMode == ViewMode.LOG) {
			generateLogDisplay(outer, panel, width - 14);
		}
		
		generateIndustryDisplay(outer, panel, width);
		
		panel.addUIElement(outer).inTL(0, 0);
	}
	
	@Override
	public FactionAPI getFactionForUIColors() {
		if (true) return super.getFactionForUIColors();
		
		if (Boolean.FALSE.equals(playerIsAttacker)) {
			return defender.getFaction();
		}
		return attacker.getFaction();
	}
	
	@Override
	protected String getName() {
		return getSmallDescriptionTitle();
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$market", market.getName());
		if (outcome != null) {
			String suffix = StringHelper.getString("over", true);
			if (outcome == BattleOutcome.ATTACKER_VICTORY)
				suffix = getString("intelTitleSuffixAttackerVictory");
			else if (outcome == BattleOutcome.DEFENDER_VICTORY)
				suffix = getString("intelTitleSuffixDefenderVictory");
			
			str += " - " + suffix;
		}
		return str;
	}
	
	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return market.getPrimaryEntity();
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/markets/mercenaries.png";
	}
	
	@Override
	public String getCommMessageSound() {
		if (listInfoParam == UPDATE_TURN && abilitiesUsedLastTurn.isEmpty()
				&& !attacker.getLossesLastTurnV2().isEmpty()
				&& !defender.getLossesLastTurnV2().isEmpty())
			return "nex_sfx_combat";
		if (listInfoParam == UPDATE_NON_VICTORY_END && outcome != BattleOutcome.CANCELLED) {
			return "nex_sfx_gb_draw";
		}
		if (listInfoParam == UPDATE_VICTORY) {
			boolean attackerVictory = (outcome == BattleOutcome.ATTACKER_VICTORY);
			boolean playerVictory = attackerVictory == (playerIsAttacker == null || playerIsAttacker.booleanValue());
			if (playerVictory) return "nex_sfx_gb_victory";
			else return "nex_sfx_gb_defeat";
		}
		
		return getSoundMajorPosting();
	}
	
	@Override
	public IntelSortTier getSortTier() {
		if (isEnding()) {
			return IntelSortTier.TIER_COMPLETED;
		}
		return IntelSortTier.TIER_2;
	}
	
	@Override
	protected float getBaseDaysAfterEnd() {
		return 30;
	}
		
	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_MILITARY);
		//tags.add(StringHelper.getString("exerelin_invasion", "invasions", true));
		if (defender.faction.isPlayerFaction() || defender.faction == Misc.getCommissionFaction())
			tags.add(Tags.INTEL_COLONIES);
		tags.add(attacker.faction.getId());
		tags.add(defender.faction.getId());
		return tags;
	}
	
	@Override
    public boolean hasSmallDescription() {
        return false;
    }

    @Override
    public boolean hasLargeDescription() { 
		return true; 
	}
	
	@Override
	public void reportPlayerClickedOn() {
		reapply();
	}
	
	public static String getString(String id) {
		return getString(id, false);
	}
	
	public static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_invasion2", id, ucFirst);
	}
	
	// =========================================================================
	// other stuff
	
	public static GroundBattleIntel getOngoing(MarketAPI market) {
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(GroundBattleIntel.class))
		{
			GroundBattleIntel gbi = (GroundBattleIntel)intel;
			if (gbi.market == market && !gbi.isEnding() && !gbi.isEnded())
				return gbi;
		}
		return null;
	}
	
	public static List<GroundBattleIntel> getOngoing() {
		List<GroundBattleIntel> results = new ArrayList<>();
		for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(GroundBattleIntel.class))
		{
			GroundBattleIntel gbi = (GroundBattleIntel)intel;
			if (!gbi.isEnding() && !gbi.isEnded() && gbi.getOutcome() == null)
				results.add(gbi);
		}
		return results;
	}
	
	// runcode exerelin.campaign.intel.groundbattle.GroundBattleIntel.createDebugEvent();
	public static void createDebugEvent() {
		MarketAPI market = Global.getSector().getEconomy().getMarket("yesod");
		FactionAPI attacker = Global.getSector().getFaction("hegemony");
		FactionAPI defender = market.getFaction();
		
		new GroundBattleIntel(market, attacker, defender).initDebug();
	}
	
	public enum ViewMode {
		UNITS, ABILITIES, INFO, LOG, HELP
	}
	
	public enum BattleOutcome {
		ATTACKER_VICTORY, DEFENDER_VICTORY, PEACE, DESTROYED, CANCELLED, OTHER
	}
	
	public static final Comparator<Industry> INDUSTRY_COMPARATOR = new Comparator<Industry>() {
		@Override
		public int compare(Industry one, Industry two) {
			return Integer.compare(one.getSpec().getOrder(), two.getSpec().getOrder());
		}
	};
}
