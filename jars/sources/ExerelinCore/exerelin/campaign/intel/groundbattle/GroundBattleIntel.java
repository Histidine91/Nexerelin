package exerelin.campaign.intel.groundbattle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.ReputationActionResponsePlugin.ReputationAdjustmentResult;
import com.fs.starfarer.api.campaign.RuleBasedDialog;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker;
import com.fs.starfarer.api.impl.PlayerFleetPersonnelTracker.PersonnelAtEntity;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.FireAll;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_BuyColony;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.InvasionRound;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.MarketTransferIntel;
import exerelin.campaign.intel.groundbattle.GroundUnit.ForceType;
import exerelin.campaign.intel.groundbattle.GroundUnit.UnitSize;
import exerelin.campaign.intel.groundbattle.dialog.UnitOrderDialogPlugin;
import exerelin.campaign.intel.groundbattle.plugins.FleetSupportPlugin;
import exerelin.campaign.intel.groundbattle.plugins.GeneralPlugin;
import exerelin.campaign.intel.groundbattle.plugins.GroundBattlePlugin;
import exerelin.campaign.intel.groundbattle.plugins.PlanetHazardPlugin;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ColonyNPCHostileActListener;
import exerelin.utilities.NexUtils;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

// may not actually use this in the end and just go with the "disrupt everything" system
public class GroundBattleIntel extends BaseIntelPlugin implements 
		ColonyPlayerHostileActListener, ColonyNPCHostileActListener {
	
	public static int MAX_PLAYER_UNITS = 12;
	public static final boolean ALWAYS_RETURN_TO_FLEET = false;
	
	public static final float VIEW_BUTTON_WIDTH = 128;
	public static final float VIEW_BUTTON_HEIGHT = 24;
	
	public static final Object UPDATE_TURN = new Object();
	public static final Object BUTTON_RESOLVE = new Object();
	public static final Object BUTTON_ANDRADA = new Object();
	public static final Object BUTTON_GOVERNORSHIP = new Object();
	
	public static Logger log = Global.getLogger(GroundBattleIntel.class);
	
	protected UnitSize unitSize;
	
	protected int turnNum;
	protected BattleOutcome outcome;
	protected int recentUnrest = 0;
	protected Float timerForDecision;
	
	protected MarketAPI market;
	protected InvasionIntel intel;
	protected boolean playerInitiated;
	protected Boolean playerIsAttacker;
	
	protected GroundBattleSide attacker;
	protected GroundBattleSide defender;
	protected GBPlayerData playerData;
		
	protected transient ViewMode viewMode;
	
	protected List<GroundBattleLog> battleLog = new LinkedList<>();
	//protected transient List<String> rawLog;
	
	protected List<IndustryForBattle> industries = new ArrayList<>();
	protected List<GroundBattlePlugin> otherPlugins = new LinkedList<>();
	
	protected Map<String, Object> data = new HashMap<>();
	
	protected IntervalUtil interval = new IntervalUtil(1, 1);
	protected IntervalUtil intervalShort = new IntervalUtil(0.2f, 0.2f);
	protected MilitaryResponseScript responseScript;
	
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
			unitSize = UnitSize.BATALLION;
		else
			unitSize = UnitSize.REGIMENT;
		
		this.attacker = new GroundBattleSide(this, true);
		this.defender = new GroundBattleSide(this, false);
		this.attacker.faction = attacker;
		this.defender.faction = defender;
		
		playerData = new GBPlayerData(this);
	}
	
	protected void generateDebugUnits() 
	{
		for (int i=0; i<6; i++) {
			GroundUnit unit = new GroundUnit(this, ForceType.MARINE, 0, i);
			unit.faction = Global.getSector().getPlayerFaction();
			unit.isPlayer = true;
			unit.isAttacker = this.playerIsAttacker;
			unit.type = i >= 4 ? ForceType.HEAVY : ForceType.MARINE;
			
			if (unit.type == ForceType.HEAVY) {
				unit.heavyArms = Math.round(this.unitSize.avgSize / GroundUnit.HEAVY_COUNT_DIVISOR * MathUtils.getRandomNumberInRange(1, 1.4f));
				unit.personnel = unit.heavyArms * 2;
			} else {
				unit.personnel = Math.round(this.unitSize.avgSize * MathUtils.getRandomNumberInRange(1, 1.4f));
				//unit.heavyArms = MathUtils.getRandomNumberInRange(10, 15);
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
		GeneralPlugin general = new GeneralPlugin();
		general.init(this);
		otherPlugins.add(general);
		
		PlanetHazardPlugin hazard = new PlanetHazardPlugin();
		hazard.init(this);
		otherPlugins.add(hazard);
		
		FleetSupportPlugin fSupport = new FleetSupportPlugin();
		fSupport.init(this);
		otherPlugins.add(fSupport);
	}
	
	/**
	 * Should be called after relevant parameters are set.
	 */
	public void init() {
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
		
		initPlugins();
		turnNum = 1;
		
		reapply();
	}
	
	public void start() {
		defender.generateDefenders();
		if (playerInitiated) {
			autoGeneratePlayerUnits();
			this.setImportant(true);
		}
		
		addMilitaryResponse();
		Global.getSector().getIntelManager().addIntel(this);
		Global.getSector().addScript(this);
		Global.getSector().getListenerManager().addListener(this);
		
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleStarted(this);
		}
	}
	
	public void initDebug() {
		playerInitiated = true;
		playerIsAttacker = true;
		init();
		generateDebugUnits();
	}
	
	public IndustryForBattle addIndustry(String industry) 
	{
		Industry ind = market.getIndustry(industry);
		IndustryForBattle ifb = new IndustryForBattle(this, ind);
		
		industries.add(ifb);
		return ifb;
	}
	
	public List<GroundBattlePlugin> getPlugins() {
		List<GroundBattlePlugin> list = new ArrayList<>();
		list.addAll(otherPlugins);
		for (IndustryForBattle ifb : industries) {
			if (ifb.getPlugin() == null) {
				log.warn("Null plugin for " + ifb.ind.getId());
				continue;
			}
			list.add(ifb.getPlugin());
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
	
	public Boolean isPlayerAttacker() {
		return playerIsAttacker;
	}
	
	public void setPlayerIsAttacker(Boolean bool) {
		playerIsAttacker = bool;
	}
	
	public void setPlayerInitiated(boolean playerInitiated) {
		this.playerInitiated = playerInitiated;
	}
		
	public Boolean isPlayerFriendly(boolean isAttacker) {
		if (playerIsAttacker == null) return null;
		return (playerIsAttacker == isAttacker);
	}
	
	public void setIntel(InvasionIntel intel) {
		this.intel = intel;
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
	
	protected boolean canSupport(FactionAPI faction, boolean isAttacker) {
		FactionAPI supportee = getSide(isAttacker).faction;
		if (faction.isPlayerFaction() && Misc.getCommissionFaction() == supportee)
			return true;
		if (AllianceManager.areFactionsAllied(faction.getId(), supportee.getId()))
			return true;
		
		return false;
	}
	
	public boolean fleetCanSupport(CampaignFleetAPI fleet, boolean isAttacker) 
	{
		if (fleet.isPlayerFleet()) {
			return isAttacker == playerIsAttacker;
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
		return MathUtils.getDistance(fleet, 
				market.getPrimaryEntity()) <= GBConstants.MAX_SUPPORT_DIST;
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
	
	public GroundUnit createPlayerUnit(ForceType type) {
		int index = 0;
		if (!playerData.getUnits().isEmpty()) {
			index = playerData.getUnits().get(playerData.getUnits().size() - 1).index + 1;
		}
		GroundUnit unit = new GroundUnit(this, type, 0, index);
		unit.faction = Global.getSector().getPlayerFaction();
		unit.isPlayer = true;
		unit.isAttacker = this.playerIsAttacker;
		unit.fleet = Global.getSector().getPlayerFleet();
		
		int size = UnitOrderDialogPlugin.getMaxCountForResize(unit, 0, unitSize.avgSize);
		unit.setSize(size, true);
		
		unit.setStartingMorale();
		
		playerData.getUnits().add(unit);
		getSide(playerIsAttacker).units.add(unit);
		return unit;
	}
	
	/**
	 * Creates units for player based on available marines and heavy armaments.
	 * Attempts to create the minimum number of units that will hold 100% of 
	 * player forces of each type, then distributes marines and heavy arms equally
	 * between each.
	 */
	public void autoGeneratePlayerUnits() {
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		int marines = cargo.getMarines();
		int heavyArms = (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS);
		
		// add heavy units
		int usableHeavyArms = Math.min(heavyArms, marines/GroundUnit.CREW_PER_MECH);
		
		float perUnitSize = (int)(unitSize.maxSize/GroundUnit.HEAVY_COUNT_DIVISOR);
		int numCreatable = (int)Math.ceil(usableHeavyArms / perUnitSize);
		numCreatable = Math.min(numCreatable, MAX_PLAYER_UNITS);
		int numPerUnit = 0;
		if (numCreatable > 0) numPerUnit = usableHeavyArms/numCreatable;
		
		if (market.getPlanetEntity() == null) {
			log.info("Non-planetary market, skipping generation of heavy units");
			usableHeavyArms = 0;
		} else {
			log.info(String.format("Can create %s heavies, %s units each, have %s heavies", numCreatable, numPerUnit, usableHeavyArms));
			for (int i=0; i<numCreatable; i++) {
				GroundUnit unit = createPlayerUnit(ForceType.HEAVY);
				unit.setSize(numPerUnit, true);
			}	
		}
		
		// add marines
		marines -= usableHeavyArms * GroundUnit.CREW_PER_MECH;
		int remainingSlots = MAX_PLAYER_UNITS - playerData.getUnits().size();
		perUnitSize = unitSize.maxSize;
		numCreatable = (int)Math.ceil(marines / perUnitSize);
		numCreatable = Math.min(numCreatable, remainingSlots);
		numPerUnit = 0;
		if (numCreatable > 0) numPerUnit = marines/numCreatable;
		
		log.info(String.format("Can create %s marines, %s units each, have %s marines", numCreatable, numPerUnit, marines));
		for (int i=0; i<numCreatable; i++) {
			GroundUnit unit = createPlayerUnit(ForceType.MARINE);
			unit.setSize(numPerUnit, true);
		}
	}
	
	public void updateStability() {
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
	
	/**
	 * Post-battle XP for player units, both those moved to fleet and to local storage.
	 * @param storage
	 */
	public void addXPToDeployedUnits(SubmarketAPI storage) 
	{
		// calc the number of marines involved
		int losses = countPersonnelFromMap(playerData.getLosses());
		int inFleet = countPersonnelFromMap(playerData.getDisbanded());
		Integer inStorage = playerData.getSentToStorage().get(Commodities.MARINES);
		if (inStorage == null) inStorage = 0;
		float total = inFleet + inStorage;
		if (total == 0) return;
		
		// calc XP to apply
		float sizeFactor = (float)Math.pow(2, market.getSize());
		float xp = GBConstants.XP_MARKET_SIZE_MULT * sizeFactor;
		log.info(String.format("%s xp from market size", xp));
		float xpFromLosses = losses * GBConstants.XP_CASUALTY_MULT;
		log.info(String.format("%s xp from losses", xpFromLosses));
		xp += xpFromLosses;
		xp = Math.min(xp, total/2);
		
		// apply the XP
		// TODO: log this		
		float fleetXP = (inFleet/total) * xp;
		if (fleetXP > 0) {
			log.info("Adding " + fleetXP + " XP for " + inFleet + " marines in fleet");
			PlayerFleetPersonnelTracker.getInstance().getMarineData().addXP(fleetXP);
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
			local.data.addXP(storageXP);
		}
	}
	
	/**
	 * Breaks up player units after the battle ends.
	 */
	public void disbandPlayerUnits() {
		SubmarketAPI storage = market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
		
		boolean anyInStorage = false;
		for (GroundUnit unit : new ArrayList<>(playerData.getUnits())) {
			// if any player units are on market, send them to storage
			if (unit.getLocation() != null) {
				if (storage != null && !ALWAYS_RETURN_TO_FLEET) {
					storage.getCargo().addCommodity(Commodities.MARINES, unit.personnel);
					storage.getCargo().addCommodity(Commodities.HAND_WEAPONS, unit.heavyArms);
					if (playerData.getLoot() != null) {
						playerData.getLoot().addCommodity(Commodities.MARINES, unit.personnel);
						playerData.getLoot().addCommodity(Commodities.HAND_WEAPONS, unit.heavyArms);
					}
					NexUtils.modifyMapEntry(playerData.getSentToStorage(), Commodities.MARINES, unit.personnel);
					NexUtils.modifyMapEntry(playerData.getSentToStorage(), Commodities.HAND_WEAPONS, unit.heavyArms);
					unit.removeUnit(false);
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
		addXPToDeployedUnits(storage);
	}
	
	public void handleTransfer() {
		if (outcome == BattleOutcome.ATTACKER_VICTORY ) {
			InvasionRound.conquerMarket(market, attacker.getFaction(), playerInitiated);
			market.getMemoryWithoutUpdate().unset(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET);
			market.getMemoryWithoutUpdate().set("$tradeMode", "OPEN", 0);
			
			InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
			if (dialog == null && dialog instanceof RuleBasedDialog) {
				((RuleBasedDialog)dialog.getPlugin()).updateMemory();
				FireAll.fire(null, dialog, dialog.getPlugin().getMemoryMap(), "PopulateOptions");
			}
		}
	}
	
	public void endBattle(BattleOutcome outcome) {
		this.outcome = outcome;
		market.getStability().removeTemporaryMod("invasion");
		if (outcome == BattleOutcome.ATTACKER_VICTORY || outcome == BattleOutcome.DEFENDER_VICTORY
				|| outcome == BattleOutcome.PEACE) {
			recentUnrest = 1 + (turnNum/market.getSize());
			RecentUnrest.get(market, true).add(recentUnrest, String.format(getString("unrestReason"), 
					attacker.getFaction().getDisplayName()));
		}
		
		if (Boolean.TRUE.equals(playerIsAttacker) && outcome == BattleOutcome.ATTACKER_VICTORY) 
		{
			playerData.setLoot(GroundBattleRoundResolve.lootMarket(market));
			
		}
		if (playerInitiated && outcome == BattleOutcome.ATTACKER_VICTORY && Misc.getCommissionFaction() != null) 
		{
			timerForDecision = 7f;
		}
		
		if (outcome == BattleOutcome.ATTACKER_VICTORY) {
			// reset to 25% health?
			GBUtils.setGarrisonDamageMemory(market, 0.75f);
		}
		else if (outcome != BattleOutcome.DESTROYED) {
			float currStrength = defender.getBaseStrength();
			float strRatio = currStrength/defender.currNormalBaseStrength;
			GBUtils.setGarrisonDamageMemory(market, 1 - strRatio);
		}
		
		for (IndustryForBattle ifb : industries) {
			if (!ifb.isIndustryTrueDisrupted())
				ifb.ind.setDisrupted(0);
		}
		
		disbandPlayerUnits();
		
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
			log.params.put("marinesLost", countPersonnelFromMap(playerData.getLosses()));
			log.params.put("heavyArmsLost", playerData.getLosses().get(ForceType.HEAVY));
			addLogEvent(log);
		}
		
		sendUpdateIfPlayerHasIntel(null, false);

		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleEnded(this);
		}
		
		endAfterDelay();
	}
	
	public boolean hasAnyDeployedUnits(boolean attacker) {
		for (GroundUnit unit : getSide(attacker).getUnits()) {
			if (unit.getLocation() != null)
				return true;
		}
		return false;
	}
	
	public void checkAnyAttackers() {
		// any units on ground?
		if (hasAnyDeployedUnits(true)) return;
		// guess not, end the battle
		endBattle(turnNum <= 1 ? BattleOutcome.CANCELLED : BattleOutcome.DEFENDER_VICTORY);
	}
	
	public void checkForVictory() {
		if (!hasAnyDeployedUnits(false)) {
			endBattle(BattleOutcome.ATTACKER_VICTORY);
		}
	}
	
	public void advanceTurn(boolean force) {
		if (force) {
			doShortIntervalStuff(interval.getIntervalDuration() - interval.getElapsed());
		}
		
		reapply();
		checkAnyAttackers();
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleBeforeTurn(this, turnNum);
		}
		new GroundBattleRoundResolve(this).resolveRound();
		for (GroundBattleCampaignListener x : Global.getSector().getListenerManager().getListeners(GroundBattleCampaignListener.class)) 
		{
			x.reportBattleAfterTurn(this, turnNum);
		}
		checkForVictory();
		playerData.updateXPTrackerNum();
		
		if (outcome != null) {
			return;
		}
		
		if (playerIsAttacker != null)
			sendUpdateIfPlayerHasIntel(UPDATE_TURN, false);
		interval.setElapsed(0);
		turnNum++;
	}
	
	/**
	 * Was this market originally owned by the player?
	 * @return
	 */
	protected boolean wasPlayerMarket() {
		String origOwner = NexUtilsMarket.getOriginalOwner(market);
		boolean originallyPlayer = origOwner == null || origOwner.equals(Factions.PLAYER);
		return originallyPlayer;
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
	}
	
	public void handleGovernorshipPurchase() {
		MutableStat cost = Nex_BuyColony.getValue(market, false, true);
		int curr = (int)Global.getSector().getPlayerFleet().getCargo().getCredits().get();
		if (curr > cost.getModifiedValue()) {
			Nex_BuyColony.buy(market, null);
			playerData.governorshipPrice = cost.getModifiedValue();
		}
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
	
	protected void addMilitaryResponse() {
		if (!market.getFaction().getCustomBoolean(Factions.CUSTOM_NO_WAR_SIM)) {
			MilitaryResponseScript.MilitaryResponseParams params = new MilitaryResponseScript.MilitaryResponseParams(CampaignFleetAIAPI.ActionType.HOSTILE, 
					"nex_player_invasion_" + market.getId(), 
					market.getFaction(),
					market.getPrimaryEntity(),
					0.75f,
					900);
			responseScript = new GBMilitaryResponseScript(params);
			market.getContainingLocation().addScript(responseScript);
		}
		List<CampaignFleetAPI> fleets = market.getContainingLocation().getFleets();
		for (CampaignFleetAPI other : fleets) {
			if (other.getFaction() == market.getFaction()) {
				MemoryAPI mem = other.getMemoryWithoutUpdate();
				Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, "raidAlarm", true, 1f);
			}
		}
	}
	
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
	
	public void doShortIntervalStuff(float days) {
		disruptIndustries();
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.advance(days);
		}
	}
	
	// =========================================================================
	// callins
	
	@Override
	protected void advanceImpl(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		if (timerForDecision != null) {
			timerForDecision -= days;
			if (timerForDecision <= 0) {
				timerForDecision = null;
			}
		}
		
		if (outcome != null) {
			return;
		}
		
		if (!market.isInEconomy()) {
			endBattle(BattleOutcome.DESTROYED);
			return;
		}
		if (!attacker.getFaction().isHostileTo(defender.getFaction())) {
			endBattle(BattleOutcome.PEACE);
			return;
		}
		
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
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, 
			MarketCMD.TempData actionData, Industry industry) {}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
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
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        String title = getSmallDescriptionTitle();

        info.addPara(title, Misc.getBasePlayerColor(), 0f);
		bullet(info);
		info.addPara(Misc.ucFirst(attacker.faction.getDisplayName()), attacker.faction.getBaseUIColor(), 3);
		info.addPara(Misc.ucFirst(defender.faction.getDisplayName()), defender.faction.getBaseUIColor(), 0);
		
		if (listInfoParam == UPDATE_TURN) {
			info.addPara(getString("intelDesc_round"), 0, Misc.getHighlightColor(), turnNum + "");
			if (!playerData.getLossesLastTurn().isEmpty()) {
				writeLossesLastTurnBullet(info);
			}
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
		}
		
		unindent(info);
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
	
	public void generateIntro(CustomPanelAPI outer, TooltipMakerAPI info, float width, float pad) {
		info.addImages(width, 128, pad, pad, attacker.faction.getLogo(), defender.faction.getLogo());
		
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
		info.addPara(str, pad, Misc.getHighlightColor(), Misc.ucFirst(unitSize.getName()), unitSize.avgSize + "", unitSize.maxSize + "");
		
		str = getString("intelDesc_round");
		info.addPara(str, pad, Misc.getHighlightColor(), turnNum + "");
		
		if (ExerelinModPlugin.isNexDev) {
			ButtonAPI button = info.addButton(getString("btnResolveRound"), BUTTON_RESOLVE, 128, 24, pad);
		}
		
		// view mode buttons
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		TooltipMakerAPI btnHolder1 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		btnHolder1.addButton(getString("btnViewOverview"), ViewMode.OVERVIEW, VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder1).inTL(0, 3);
		
		TooltipMakerAPI btnHolder2 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		btnHolder2.addButton(getString("btnViewCommand"), ViewMode.COMMAND, VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder2).rightOfTop(btnHolder1, 4);
		
		TooltipMakerAPI btnHolder3 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH, 
				VIEW_BUTTON_HEIGHT, false);
		btnHolder3.addButton(getString("btnViewLog"), ViewMode.LOG, VIEW_BUTTON_WIDTH, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder3).rightOfTop(btnHolder2, 4);
		
		info.addCustom(buttonRow, 0);
	}
	
	protected String getCommoditySprite(String commodityId) {
		return Global.getSettings().getCommoditySpec(commodityId).getIconName();
	}
	
	public TooltipMakerAPI addResourceSubpanel(CustomPanelAPI resourcePanel, float width, 
			TooltipMakerAPI rightOf, String commodity, int amount) 
	{
		TooltipMakerAPI subpanel = resourcePanel.createUIElement(width, 32, false);
		TooltipMakerAPI image = subpanel.beginImageWithText(getCommoditySprite(commodity), 32);
		image.addPara(amount + "", 0);
		subpanel.addImageWithText(0);
		if (rightOf == null)
			resourcePanel.addUIElement(subpanel).inTL(0, 0);
		else
			resourcePanel.addUIElement(subpanel).rightOfTop(rightOf, 0);
		
		return subpanel;
	}
	
	public void placeCard(TooltipMakerAPI unitCard, int numCards, int numPrevious, 
			CustomPanelAPI unitPanel, List<TooltipMakerAPI> unitCards,
			int maxPerRow) {
		if (numPrevious == 0) {
			// first card, place in TL
			unitPanel.addUIElement(unitCard).inTL(0, 3);
			//log.info("Placing card in TL");
		}
		else if (numPrevious % maxPerRow == 0) {
			// row filled, place under first card of previous row
			int rowNum = numPrevious/maxPerRow - 1;
			TooltipMakerAPI firstOfPrevious = unitCards.get(maxPerRow * rowNum);
			unitPanel.addUIElement(unitCard).belowLeft(firstOfPrevious, 3);
			//log.info("Placing card in new row");
		}
		else {
			// right of last card
			unitPanel.addUIElement(unitCard).rightOfTop(unitCards.get(numPrevious - 1), GroundUnit.PADDING_X);
			//log.info("Placing card in current row");
		}
	}
	
	public void generateUnitDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		info.addSectionHeading(getString("unitPanel_header"), Alignment.MID, pad);
		
		CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
		info.addPara(getString("unitPanel_resources"), 3);
		CustomPanelAPI resourcePanel = panel.createCustomPanel(width, 32, null);
		TooltipMakerAPI resourceSubPanel;
		
		int subWidth = 96;
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, null, 
				Commodities.MARINES, cargo.getMarines());
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.HAND_WEAPONS, (int)cargo.getCommodityQuantity(Commodities.HAND_WEAPONS));
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.SUPPLIES, (int)cargo.getSupplies());
		resourceSubPanel = addResourceSubpanel(resourcePanel, subWidth, resourceSubPanel, 
				Commodities.FUEL, (int)cargo.getFuel());
		
		info.addCustom(resourcePanel, 3);
				
		int CARDS_PER_ROW = (int)(width/(GroundUnit.PANEL_WIDTH + GroundUnit.PADDING_X));
		
		int numCards = 0;
		for (GroundUnit unit : playerData.getUnits()) {
			numCards++;
		}
		if (playerData.getUnits().size() < MAX_PLAYER_UNITS)
			numCards++;	// for the "create unit" card
		
		int NUM_ROWS = (int)Math.ceil((float)numCards/CARDS_PER_ROW);
		//log.info("Number of rows: " + NUM_ROWS);
		//log.info("Cards per row: " + CARDS_PER_ROW);
		
		CustomPanelAPI unitPanel = panel.createCustomPanel(width, NUM_ROWS * (GroundUnit.PANEL_HEIGHT + 3), null);
		
		//TooltipMakerAPI test = unitPanel.createUIElement(64, 64, true);
		//test.addPara("wololo", 0);
		//unitPanel.addUIElement(test).inTL(0, 0);
		
		List<TooltipMakerAPI> unitCards = new ArrayList<>();
		
		try {
			for (GroundUnit unit : playerData.getUnits()) {
				TooltipMakerAPI unitCard = unit.createUnitCard(unitPanel, false);
				//log.info("Created card for " + unit.name);
				
				int numPrevious = unitCards.size();
				placeCard(unitCard, numCards, numPrevious, unitPanel, unitCards, CARDS_PER_ROW);
				unitCards.add(unitCard);
			}
			if (playerData.getUnits().size() < MAX_PLAYER_UNITS) {
				TooltipMakerAPI newCard = GroundUnit.createBlankCard(unitPanel, unitSize);
				placeCard(newCard, unitCards.size(), unitCards.size(), unitPanel, unitCards, CARDS_PER_ROW);
			}
			
		} catch (Exception ex) {
			log.error("Failed to display cards", ex);
		}
				
		info.addCustom(unitPanel, 3);
	}
	
	public void populateModifiersDisplay(CustomPanelAPI outer, TooltipMakerAPI disp, 
			float width, float pad, Boolean isAttacker) 
	{
		for (GroundBattlePlugin plugin : getPlugins()) {
			plugin.addModifierEntry(disp, outer, width, pad, isAttacker);
		}
	}
	
	protected static float itemPanelHeight = 160;
	public void generateModifiersDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width, float pad) 
	{
		// Holds the display for each faction, added to 'info'
		CustomPanelAPI strPanel = panel.createCustomPanel(width, itemPanelHeight, null);
		
		float subWidth = width/3;
		try {
			TooltipMakerAPI dispAtk = strPanel.createUIElement(subWidth, itemPanelHeight, true);
			strPanel.addUIElement(dispAtk).inTL(0, 0);
			TooltipMakerAPI dispCom = strPanel.createUIElement(subWidth, itemPanelHeight, true);
			strPanel.addUIElement(dispCom).inTMid(0);
			TooltipMakerAPI dispDef = strPanel.createUIElement(subWidth, itemPanelHeight, true);
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
	
	public void generateIndustryDisplay(TooltipMakerAPI info, CustomPanelAPI panel, float width) 
	{
		info.beginTable(Global.getSector().getPlayerFaction(), 0,
				getString("industryPanel_header_industry"), IndustryForBattle.COLUMN_WIDTH_INDUSTRY,
				//getString("industryPanel_header_heldBy"), IndustryForBattle.COLUMN_WIDTH_CONTROLLED_BY,
				getString("industryPanel_header_attacker"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL,
				getString("industryPanel_header_defender"), IndustryForBattle.COLUMN_WIDTH_TROOP_TOTAL
		);
		info.addTable("", 0, 3);
		
		for (IndustryForBattle ifb : industries) {
			ifb.renderPanel(panel, info, width);
		}
	}
	
	static float logPanelHeight = 240;
	public void generateLogDisplay(TooltipMakerAPI info, CustomPanelAPI outer, float width) 
	{
		info.addSectionHeading(getString("logHeader"), Alignment.MID, 10);
		try {
			CustomPanelAPI logPanel = outer.createCustomPanel(width, logPanelHeight, null);
			TooltipMakerAPI scroll = logPanel.createUIElement(width, logPanelHeight, true);
			for (int i=battleLog.size() - 1; i>=0; i--) {
				battleLog.get(i).writeLog(logPanel, scroll, width - 4);
			}

			logPanel.addUIElement(scroll);
			info.addCustom(logPanel, 3);
		} catch (Exception ex) {
			log.error("Failed to create log display", ex);
		}		
	}
	
	public void addLogEvent(GroundBattleLog log) {
		battleLog.add(log);
	}
	
	public void writeLossesLastTurnBullet(TooltipMakerAPI info) {
		List<Pair<ForceType, Integer>> lossesSortable = new ArrayList<>();
		
		String str = getString("bulletLossesLastTurn");
		for (ForceType type : playerData.getLossesLastTurn().keySet()) {
			int thisLoss = playerData.getLossesLastTurn().get(type);
			lossesSortable.add(new Pair<>(type, thisLoss));
		}
		Collections.sort(lossesSortable, new Comparator<Pair<ForceType, Integer>>() {
			@Override
			public int compare(Pair<ForceType, Integer> obj1, Pair<ForceType, Integer> obj2) {
				return obj1.one.compareTo(obj2.one);
			}
		});
		
		List<String> strings = new ArrayList<>();
		for (Pair<ForceType, Integer> loss : lossesSortable) {
			strings.add(loss.two + " " + loss.one.getCommodityName().toLowerCase());
		}
		str = String.format(str, StringHelper.writeStringCollection(strings, false, true));
		info.addPara(str, 0);
	}
	
	public void addPostVictoryButtons(CustomPanelAPI outer, TooltipMakerAPI info, float width) 
	{
		float pad = 3, opad = 10;
		String str = StringHelper.substituteToken(getString("intelDesc_postVictoryOptions"), "$market", market.getName());
		info.addPara(str, opad);
		
		CustomPanelAPI buttonRow = outer.createCustomPanel(width, 24, null);
		TooltipMakerAPI btnHolder1 = buttonRow.createUIElement(VIEW_BUTTON_WIDTH * 2, 
				VIEW_BUTTON_HEIGHT, false);
		str = StringHelper.substituteToken(getString("btnAndrada"), "$market", market.getName());
		btnHolder1.addButton(str, BUTTON_ANDRADA, VIEW_BUTTON_WIDTH * 2, VIEW_BUTTON_HEIGHT, 0);
		buttonRow.addUIElement(btnHolder1).inTL(0, 3);
		
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
			} else if (timerForDecision != null) {
				addPostVictoryButtons(outer, info, width);
			}
		}
	}
	
	@Override
	public boolean doesButtonHaveConfirmDialog(Object buttonId) {
		return buttonId == BUTTON_ANDRADA || buttonId == BUTTON_GOVERNORSHIP;
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
							// TODO: print item info
							if (aiCore != null)
								prompt.addPara(" - " + Global.getSettings().getCommoditySpec(aiCore).getName(), 0);
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
		if (buttonId == BUTTON_RESOLVE) {
			advanceTurn(true);
			ui.updateUIForItem(this);
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
		if (buttonId == GroundUnit.BUTTON_NEW_HEAVY) {
			createPlayerUnit(ForceType.HEAVY);
			ui.updateUIForItem(this);
			return;
		}
		if (buttonId == GroundUnit.BUTTON_NEW_MARINE) {
			createPlayerUnit(ForceType.MARINE);
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
		
		if (buttonId instanceof Pair) {
			try {
				Pair pair = (Pair)buttonId;
				String action = (String)pair.one;
				if (pair.two instanceof IndustryForBattle) {
					IndustryForBattle ifb = (IndustryForBattle)pair.two;
					switch (action) {
						case "loot":
							String aiCore = ifb.getIndustry().getAICoreId();
							SpecialItemData special = ifb.getIndustry().getSpecialItem();
							// TODO: add stuff to cargo and play sound
							
							break;
					}
				}
			} catch (Exception ex) {
				// do nothing?
			}
		}
	}
	
	// adapted from Starship Legends' BattleReport
    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
		float pad = 3;
		float opad = 10;
		FactionAPI faction = market.getFaction();
		
		TooltipMakerAPI outer = panel.createUIElement(width, height, true);
		
		outer.addSectionHeading(getSmallDescriptionTitle(), faction.getBaseUIColor(), 
				faction.getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
		
		if (outcome != null) {
			generatePostBattleDisplay(panel, outer, width, height);
			generateLogDisplay(outer, panel, width - 14);
			panel.addUIElement(outer).inTL(0, 0);
			return;
		}
		
		if (viewMode == null) viewMode = ViewMode.OVERVIEW;
		
		generateIntro(panel, outer, width, opad);
		generateUnitDisplay(outer, panel, width, opad);
		if (viewMode == ViewMode.OVERVIEW) {
			generateModifiersDisplay(outer, panel, width - 6, opad);
		} else if (viewMode == ViewMode.COMMAND) {
			// abilities, when we get that
		}
		else if (viewMode == ViewMode.LOG) {
			generateLogDisplay(outer, panel, width - 14);
		}
		
		generateIndustryDisplay(outer, panel, width);
		
		panel.addUIElement(outer).inTL(0, 0);
	}
	
	@Override
	public String getSmallDescriptionTitle() {
		String str = getString("intelTitle");
		str = StringHelper.substituteToken(str, "$market", market.getName());
		if (outcome != null) {
			if (outcome == BattleOutcome.ATTACKER_VICTORY)
				str += " - " + StringHelper.getString("successful", true);
			else if (outcome == BattleOutcome.DEFENDER_VICTORY)
				str += " - " + StringHelper.getString("failed", true);
			else
				str += " - " + StringHelper.getString("over", true);
		}
		return str;
	}
	
	@Override
	public String getIcon() {
		return "graphics/icons/markets/mercenaries.png";
	}
	
	@Override
	public String getCommMessageSound() {
		if (listInfoParam == UPDATE_TURN)
			return "nex_sfx_combat";
		return super.getCommMessageSound();
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
		if (defender.faction.isPlayerFaction())
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
			if (!gbi.isEnding() && !gbi.isEnded())
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
		OVERVIEW, COMMAND, LOG, HELP
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
