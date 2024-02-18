package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.*;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.PlayerColonizationListener;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.econ.FreeMarket;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.impl.campaign.tutorial.TutorialMissionIntel;
import com.fs.starfarer.api.loading.IndustrySpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.ColonyManager.QueuedIndustry.QueueType;
import exerelin.campaign.ai.StrategicAI;
import exerelin.campaign.colony.ColonyTargetValuator;
import exerelin.campaign.diplomacy.DiplomacyTraits;
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitIds;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.campaign.econ.FactionConditionPlugin;
import exerelin.campaign.econ.GroundPoolManager;
import exerelin.campaign.econ.ResourcePoolManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.campaign.intel.fleets.ReliefFleetIntelAlt;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.campaign.intel.groundbattle.GBUtils;
import exerelin.campaign.intel.missions.ConquestMissionIntel;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.*;
import exerelin.world.ExerelinProcGen;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.IndustryClassGen;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.util.Misc.getImmigrationPlugin;

/**
 * Handles assorted colony-related functions, including NPC colony growth, NPC colony expeditions,
 * admin bonuses from empire size, and relief fleets.
 */
public class ColonyManager extends BaseCampaignEventListener implements EveryFrameScript,
		InvasionListener, PlayerColonizationListener, MarketImmigrationModifier,
		ColonyPlayerHostileActListener, ColonyNPCHostileActListener
{
	public static Logger log = Global.getLogger(ColonyManager.class);
	
	public static final String PERSISTENT_KEY = "nex_colonyManager";
	public static final String MEMORY_KEY_GROWTH_LIMIT = "$nex_colony_growth_limit";
	public static final String MEMORY_KEY_STASHED_CORES = "$nex_stashed_ai_cores";
	public static final String MEMORY_KEY_STASHED_CORE_ADMIN = "$nex_stashed_ai_core_admin";
	public static final String MEMORY_KEY_RULER_TEMP_OWNERSHIP = "$nex_ruler_temp_owner";
	public static final String MEMORY_KEY_RULER_TEMP_OWNERSHIP_ADMINDEX = "$nex_ruler_temp_owner_adminIndex";
	public static final String MEMORY_KEY_FACTION_SURVEY_BONUS = "$nex_colony_surveyBonus";
	public static final Set<String> NEEDED_OFFICIALS = new HashSet<>(Arrays.asList(
			Ranks.POST_ADMINISTRATOR, Ranks.POST_BASE_COMMANDER, 
			Ranks.POST_STATION_COMMANDER, Ranks.POST_PORTMASTER
	));
	public static final int MAX_STATION_SIZE = 6;
	public static final int MIN_CYCLE_FOR_NPC_GROWTH = 207;
	public static final int MIN_CYCLE_FOR_EXPEDITIONS = 207;
	public static final float MAX_EXPEDITION_FP = 600;
	public static final float EXPEDITION_BASE_CHANCE_BEFORE_DATA = 150f;
	//public static final float AUTONOMOUS_INCOME_MULT = 0.2f;
	public static final float NPC_FREE_PORT_GROWTH_REDUCTION_MULT = 0.5f;
	public static final boolean STRATEGIC_AI_BLOCKS_BUILD_ECON_ON_UPSIZE = false;
	public static final boolean STRATEGIC_AI_BLOCKS_BUILD_MILITARY_ON_UPSIZE = true;
	
	public static final int[] BONUS_ADMIN_LEVELS;
	
	public static final Map<String, Float> SURVEY_DATA_VALUES = new HashMap<>();
	
	public transient List<MarketAPI> coloniesToMonitor = new ArrayList<>();
	
	protected Map<MarketAPI, LinkedList<QueuedIndustry>> npcConstructionQueues = new HashMap<>();
	protected int bonusAdminLevel = 0;
	protected float colonyExpeditionProgress;
	protected int numDeadExpeditions;
	protected int numAvertedExpeditions;
	protected int numColonies;
	protected int currIter;
	protected float reliefFleetCooldown;
	protected float profitMarginForXP = 0;
	
	static {
		SURVEY_DATA_VALUES.put(Commodities.SURVEY_DATA_1, 1f);
		SURVEY_DATA_VALUES.put(Commodities.SURVEY_DATA_2, 2f);
		SURVEY_DATA_VALUES.put(Commodities.SURVEY_DATA_3, 5f);
		SURVEY_DATA_VALUES.put(Commodities.SURVEY_DATA_4, 10f);
		SURVEY_DATA_VALUES.put(Commodities.SURVEY_DATA_5, 25f);
		
		try {
			JSONArray adminLevels = Global.getSettings().getJSONArray("nex_bonusAdminLevels");
			
			BONUS_ADMIN_LEVELS = new int[adminLevels.length()];
			for (int i=0; i<adminLevels.length(); i++) {
				BONUS_ADMIN_LEVELS[i] = adminLevels.getInt(i);
			}
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	protected Object readResolve() {
		coloniesToMonitor = new ArrayList<>();
		return this;
	}
	
	public ColonyManager() {
		super(true);
	}
		
	/**
	 * Clamps player station population, increments NPC population size, and adds bonus admins if needed.
	 */
	protected void updateMarkets() {
		numColonies = 0;
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		List<MarketAPI> needRelief = new ArrayList<>();
		
		int playerFactionSize = 0;
		boolean allowGrowth = Global.getSector().getClock().getCycle() >= MIN_CYCLE_FOR_NPC_GROWTH;
		float numTicksPerMonth = Global.getSettings().getFloat("economyIterPerMonth");
		for (MarketAPI market : markets) 
		{
			if (market.getFaction().isPlayerFaction() || market.isPlayerOwned())
			{
				playerFactionSize += market.getSize();
			}
			
			setGrowthRate(market);
			
			if (market.getFaction().isPlayerFaction()) 
			{
				updateIncome(market);
			}
			else
			{
				updateFreePortSetting(market);
			}
			
			// handle market growth
			if (!market.isPlayerOwned())
			{
				float growthRate = market.getIncoming().getWeightValue();
				if (allowGrowth && !market.isHidden() && growthRate > 0 && Misc.getMarketSizeProgress(market) >= 1) 
				{
					// workaround for colony near-instant growth in later cycles
					// if we're still in the grace period, we know it shouldn't have grown, so reset it
					if (market.getMemoryWithoutUpdate().getBoolean("$nex_delay_growth")) {
						ImmigrationPlugin plugin = getImmigrationPlugin(market);
						market.getPopulation().setWeight(plugin.getWeightForMarketSize(market.getSize()));
						market.getPopulation().normalize();
					}
					else {
						int maxSize = Global.getSettings().getInt("maxColonySize");
						if (market.getMemoryWithoutUpdate().contains(MEMORY_KEY_GROWTH_LIMIT))
							maxSize = (int)market.getMemoryWithoutUpdate().getLong(MEMORY_KEY_GROWTH_LIMIT);
						else if (market.getMemoryWithoutUpdate().contains(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
							maxSize = NexConfig.maxNPCNewColonySize;
						else if (market.getPlanetEntity() == null)
							maxSize = Global.getSettings().getInt("nex_stationMaxSize");
						
						if (market.getSize() < maxSize) {
							upsizeMarket(market);
						}
					}
				}
				
				if (market.getFaction().isPlayerFaction() && !market.isHidden()) 
				{
					processAutonomousColonyIncome(market, false);
				}
				else if (Nex_IsFactionRuler.isRuler(market.getFactionId()) && !market.isHidden()) 
				{
					processAutonomousColonyIncome(market, true);
				}
				
				// prevent incentive credit debt accumulation on NPC markets
				market.setIncentiveCredits(0);
				
				checkVICEVC(market);
			}
			
			{
				// garrison damage recovery
				float garDamage = GBUtils.getGarrisonDamageMemory(market);
				if (garDamage > 0) {
					float recoveryFactor = 1/(numTicksPerMonth*GBConstants.INVASION_HEALTH_MONTHS_TO_RECOVER);

					// check ground pool and deduct recovery expenses
					float recoverMarineCount = recoveryFactor * GBUtils.getTroopCountForMarketSize(market) * 0.25f;
					float wantedPts = recoverMarineCount * GroundPoolManager.POOL_PER_MARINE;
					ResourcePoolManager.RequisitionParams rp = new ResourcePoolManager.RequisitionParams(wantedPts);
					float available = GroundPoolManager.getManager().drawFromPool(market.getFactionId(), rp);
					if (available <= 0) continue;

					float mult = Math.max(available/wantedPts, 1);
					recoveryFactor *= mult;
					garDamage -= recoveryFactor;
					GBUtils.setGarrisonDamageMemory(market, garDamage);
					log.info(String.format("%s (size %s) expending %.1f ground pool points to recover garrison health by %.3f",
							market.getName(), market.getSize(), wantedPts, recoveryFactor));
				}
			}
			
			if (market.getMemoryWithoutUpdate().getBoolean(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
				numColonies += 1;
			
			if (reliefFleetCooldown <= 0 && !market.isHidden() && RecentUnrest.get(market) != null
					&& !NexUtilsFaction.isPirateOrTemplarFaction(market.getFactionId())) 
			{
				int unrest = RecentUnrest.getPenalty(market);
				boolean wantStabilize = unrest >= NexConfig.stabilizePackageEffect + 1|| (market.getStabilityValue() <= 1 && unrest > 0);
				if (wantStabilize) 
				{
					needRelief.add(market);
				}
			}

			if (market.getAdmin() != null && market.getAdmin().isPlayer()) {
				float profit = market.getNetIncome();
				float cost = market.getIndustryUpkeep();
				if (profit <= 0 || cost <= 0) continue;
				float margin = profit/cost;
				if (margin <= 0) continue;
				if (margin > 2) margin = 2;
				log.info(String.format("Market %s adding %.2f profit margin (%.0f profit, %.0f cost)", market.getName(), margin, profit, cost));
				float size = market.getSize() - 2;
				if (size < 0.5f) size = 0.5f;
				float marginForXP = margin * size / numTicksPerMonth;
				float xpEquivalent = marginForXP * Global.getSettings().getFloat("nex_xpPerProfitMargin");
				log.info(String.format("This will be worth %.0f XP at month end (about %.0f/month)", xpEquivalent, xpEquivalent * numTicksPerMonth));
				profitMarginForXP += marginForXP;
			}
		}
		updatePlayerBonusAdmins(playerFactionSize);
		if (!needRelief.isEmpty()) {
			processReliefFleetEvent(needRelief);
		}
	}
	
	// runcode exerelin.campaign.ColonyManager.getManager().upsizeMarket(Global.getSector().getEconomy().getMarket("jangala"))
	/**
	 * Makes the specified market one size larger (with intel notification), 
	 * and prompts it to build new industries.
	 * @param market
	 */
	public void upsizeMarket(MarketAPI market) 
	{
		int oldSize = market.getSize();		
		CoreImmigrationPluginImpl.increaseMarketSize(market);
		int newSize = market.getSize();
		if (newSize <= oldSize) return;	// failed to upsize
		
		// intel: copied from CoreImmigrationPluginImpl
		MessageIntel intel = new MessageIntel(getString("intelGrowthTitle", false) 
				+ " - " + market.getName(), Misc.getBasePlayerColor());
		intel.addLine(BaseIntelPlugin.BULLET + getString("intelGrowthBullet", false),
				Misc.getTextColor(), 
				new String[] {"" + (int)Math.round(market.getSize())},
				Misc.getHighlightColor());
		intel.setIcon(market.getFaction().getCrest());
		intel.setSound(BaseIntelPlugin.getSoundStandardPosting());
		if (Global.getSettings().getBoolean("nex_showNPCGrowthMessages"))
			Global.getSector().getCampaignUI().addMessage(intel, 
					market.isPlayerOwned() ? MessageClickAction.COLONY_INFO : MessageClickAction.NOTHING, 
					market);
		
		if (!market.isPlayerOwned()) {
			buildIndustries(market);
			processNPCConstruction(market);
			if (market.getSize() >= 5 && market.getMemoryWithoutUpdate().contains(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
			{
				market.setImmigrationIncentivesOn(null);
			}
		}
	}
	
	/**
	 * Sets whether the market should have immigration modifiers applied to it.
	 * @param market
	 */
	public void setGrowthRate(MarketAPI market) {
		boolean player = market.getFaction().isPlayerFaction() || market.isPlayerOwned();
		boolean want = true;	//!player || SectorManager.getManager().isHardMode();
		boolean have = market.getImmigrationModifiers().contains(this);
		if (want == have) return;
		
		if (want)
		{
			market.addImmigrationModifier(this);
		}
		else 
		{
			market.removeImmigrationModifier(this);
		}
	}
	
	@Override
	public void modifyIncoming(MarketAPI market, PopulationComposition incoming) {
		if (market.getFaction().isPlayerFaction() || market.isPlayerOwned()) {
			if (SectorManager.getManager().isHardMode()) {
				incoming.getWeight().modifyMult("nex_colonyManager_hardModeGrowth", NexConfig.hardModeColonyGrowthMult, 
						getString("hardModeGrowthMultDesc", false));
			}
		}
		else {
			incoming.getWeight().modifyMult("nex_colonyManager_npcGrowth", Global.getSettings().getFloat("nex_npcColonyGrowthMult"), 
					getString("npcGrowthMultDesc", false));
			// lower benefit of free port on NPC growth
			float freePortGrowth = getFreePortGrowthBonus(market);
			if (freePortGrowth > 0)
			{
				float penalty = Math.round(-freePortGrowth * NPC_FREE_PORT_GROWTH_REDUCTION_MULT * 2)/2;
				incoming.getWeight().modifyFlat("nex_colonyManager_npcGrowth_freePort", penalty, 
						getString("npcFreePortGrowthModDesc"));
			}
		}
		if (market.getPlanetEntity() == null) {
			incoming.getWeight().modifyMult("nex_colonyManager_stationGrowth", Global.getSettings().getFloat("nex_stationGrowthMult"), 
					getString("stationGrowthMultDesc", false));
		}
	}
	
	/**
	 * Gets the vanilla bonus (if any) which this market has from being a free port.
	 * Make sure this matches {@code getImmigrationBonus()} in {@code FreeMarket} class
	 * @param market
	 * @return
	 */
	protected float getFreePortGrowthBonus(MarketAPI market) {
		MarketConditionAPI cond = market.getCondition(Conditions.FREE_PORT);
		if (cond == null) return 0;
		
		FreeMarket plugin = (FreeMarket)cond.getPlugin();
		float growth = FreeMarket.MIN_GROWTH + plugin.getDaysActive() 
				/ FreeMarket.MAX_DAYS * (FreeMarket.MAX_GROWTH - FreeMarket.MIN_GROWTH);
		growth = Math.round(growth);
		if (growth > FreeMarket.MAX_GROWTH) growth = FreeMarket.MAX_GROWTH;
		if (growth < 1) growth = 1;
		return growth;
	}
	
	public void updatePlayerBonusAdmins() {
		updatePlayerBonusAdmins(NexUtilsFaction.getFactionMarketSizeSum(Factions.PLAYER));
	}
		
	public void updatePlayerBonusAdmins(int playerFactionSize) {
		int index = bonusAdminLevel;
		for (int i=bonusAdminLevel + 1; i <BONUS_ADMIN_LEVELS.length; i++) {
			int sizeNeeded =BONUS_ADMIN_LEVELS[i];
			if (playerFactionSize < sizeNeeded)
				break;
			index = i;
		}
		Global.getSector().getPlayerStats().getAdminNumber().modifyFlat("nex_population_size", 
					index, getString("globalPopulation", true));
		if (index > bonusAdminLevel) {
			bonusAdminLevel = index;
			log.info("Reached bonus level " + index + " from market size " + playerFactionSize);
			
			Color hl = Misc.getHighlightColor();
			Color textColor =  Misc.getTextColor();
			MessageIntel intel = new MessageIntel(getString("bonusAdminIntelTitle"), 
					Global.getSector().getPlayerFaction().getBaseUIColor());
			intel.addLine(BaseIntelPlugin.BULLET + getString("bonusAdminIntelBullet1"), textColor,
					new String[] {playerFactionSize + ""}, hl);
			intel.addLine(BaseIntelPlugin.BULLET + getString("bonusAdminIntelBullet2"), textColor, 
					new String[] {bonusAdminLevel + ""}, hl);
			if (index < BONUS_ADMIN_LEVELS.length - 1) {
				int next = BONUS_ADMIN_LEVELS[index + 1];
				intel.addLine(BaseIntelPlugin.BULLET + getString("bonusAdminIntelBullet3"), textColor, 
					new String[] {next + ""}, hl);
			}
			intel.setIcon(Global.getSector().getPlayerFaction().getCrest());
			Global.getSector().getCampaignUI().addMessage(intel, CommMessageAPI.MessageClickAction.COLONY_INFO);
		}
	}
	
	public static void updateFreePortSetting(MarketAPI market)
	{
		if (market.getFaction().isPlayerFaction() || market.isPlayerOwned()) return;	// let player decide
		
		NexFactionConfig newOwnerConfig = NexConfig.getFactionConfig(market.getFactionId());
		// keep the cond check; motherfuckers can't be trusted to have the right settting
		boolean isFreePort = market.isFreePort() || market.hasCondition(Conditions.FREE_PORT);
		boolean wantFreePort;
		if (!SectorManager.getManager().isCorvusMode())
		{
			wantFreePort = newOwnerConfig.freeMarket || market.getId().equals("nex_prismFreeport");
		}
		else
		{
			if (market.getMemoryWithoutUpdate().contains("$startingFreeMarket"))
				wantFreePort = market.getMemoryWithoutUpdate().getBoolean("$startingFreeMarket");
			else wantFreePort = (newOwnerConfig.pirateFaction && newOwnerConfig.freeMarket);
		}
		
		if (isFreePort != wantFreePort) {
			market.setFreePort(wantFreePort);
			NexUtilsMarket.setTariffs(market);
		}
	}
	
	public static void updateIncome() {
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			updateIncome(market);
		}
	}
	
	/**
	 * Updates the presence or absence of the Starfarer mode income multiplier for the specified market.
	 * @param market
	 */
	public static void updateIncome(MarketAPI market)
	{
		boolean player = market.getFaction().isPlayerFaction() || market.isPlayerOwned();
		if (player && SectorManager.getManager().isHardMode())
		{
			market.getIncomeMult().modifyMult("nex_hardMode", NexConfig.hardModeColonyIncomeMult, 
						getString("hardModeIncomeMultDesc"));
		}
		else {
			market.getIncomeMult().unmodify("nex_hardMode");
		}
	}
	
	/**
	 * Builds the necessary structure(s) to manage viral contamination from VIC's viral bomb condition, if needed.
	 * @param market
	 */
	public static void checkVICEVC(MarketAPI market)
	{
		if (!market.hasCondition("VIC_VBomb_scar")) {
			return;
		}
		FactionAPI vic = Global.getSector().getFaction("vic");
		if (vic == null) return;
		
		if (market.getFaction() == vic) {	// handled by VIC's own code?
			//return;
		}
		
		// what tier of antiviral structure are we allowed?
		int wantedLevel = 1;
		if (market.getFaction() == vic) wantedLevel = 3;
		else {
			if (market.getSize() >= 6 || Misc.isMilitary(market))
				wantedLevel++;
			if (AllianceManager.areFactionsAllied(market.getFactionId(), "vic"))
				wantedLevel++;
			if (vic.getRelationshipLevel(market.getFactionId()).isAtWorst(RepLevel.FRIENDLY))
				wantedLevel++;
		}
		
		if (wantedLevel > 3) wantedLevel = 3;
		
		// what tier of antiviral structure do we currently have?
		int currLevel = 0;
		Industry ind = null;
		do {
			ind = market.getIndustry("vic_antiEVCt3");
			if (ind != null) {
				currLevel = 3;
				break;
			}
			ind = market.getIndustry("vic_antiEVCt2");
			if (ind != null) {
				currLevel = 2;
				break;
			}
			ind = market.getIndustry("vic_antiEVCt1");
			if (ind != null) {
				currLevel = 1;
				break;
			}
		} while (false);
		
		// don't do anything if structure is already building/upgrading
		if (ind != null) {
			if (ind.isBuilding() || ind.isUpgrading())
				return;
		}
		
		if (wantedLevel > currLevel) {
			log.info(String.format("Wanted VIC EVC level %s, current %s", wantedLevel, currLevel));
			if (ind != null) NexUtilsMarket.upgradeIndustryIfCan(ind, false);
			else {
				market.addIndustry("vic_antiEVCt1");
				market.getIndustry("vic_antiEVCt1").startBuilding();
			}
		}
	}
	
	// this basically reverses the CoreScript fee collection; it does the same math
	// and then reduces the upkeep instead of increasing it
	/**
	 * Refunds storage fee for markets that belong to player faction but are not player-controlled.
	 */
	protected void processStorageRebate() 
	{
		float numIter = Global.getSettings().getFloat("economyIterPerMonth");
		float f = 1f / numIter;
		
		MonthlyReport report = SharedData.getData().getCurrentReport();
		FDNode storageNode = null;
		
		float storageFraction = Global.getSettings().getFloat("storageFreeFraction");
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (!market.isPlayerOwned() && Nex_IsFactionRuler.isRuler(market.getFactionId()) 
					&& Misc.playerHasStorageAccess(market)) {
				float vc = Misc.getStorageCargoValue(market);
				float vs = Misc.getStorageShipValue(market);
				
				float fc = (int) (vc * storageFraction);
				float fs = (int) (vs * storageFraction);
				if (fc > 0 || fs > 0) {
					if (storageNode == null) {
						storageNode = report.getNode(MonthlyReport.STORAGE);
					}
					FDNode mNode = report.getNode(storageNode, market.getId());
					mNode.upkeep -= (fc + fs) * f;
				}
			}
		}
	}
	
	/**
	 * Registers income from an autonomous colony in the monthly report.
	 * @param market
	 * @param byRuler True if we're calling this method due to being faction ruler, false for normal autonomous colonies of {@code player} faction.
	 */
	protected void processAutonomousColonyIncome(MarketAPI market, boolean byRuler) {
		float numIter = Global.getSettings().getFloat("economyIterPerMonth");
		float f = 1f / numIter;
		
		MonthlyReport report = SharedData.getData().getCurrentReport();
		FDNode colonyNode = null;
		
		float income = market.getIndustryIncome() + market.getExportIncome(false);
		float upkeep = market.getIndustryUpkeep();
		
		if (income > 0 || upkeep > 0) {
			if (colonyNode == null) {
				colonyNode = report.getNode(MonthlyReport.OUTPOSTS);
			}
			FDNode mNode = report.getNode(colonyNode, market.getId());
			mNode.name = market.getName() + " (" + market.getSize() + ")";
			mNode.custom = market;
			
			FDNode subNode = report.getNode(mNode, "autonomous"); 
			subNode.name = getString("reportAutonomousTax");
			subNode.tooltipCreator = AUTONOMOUS_INCOME_NODE_TOOLTIP;
			
			float mult = Global.getSettings().getFloat(byRuler ? "nex_rulerIncomeMult" : "nex_autonomousIncomeMult");
			
			subNode.income += income * mult * f;
			subNode.upkeep += upkeep * mult * f;
			subNode.icon = subNode.income >= subNode.upkeep ? "graphics/icons/reports/generic_income.png"
					: "graphics/icons/reports/generic_expense.png";
		}
	}
	
	public static boolean isBuildingAnything(MarketAPI market) {
		for (Industry ind : market.getIndustries()) {
			if (ind.isBuilding() && !ind.isUpgrading())
				return true;
		}
		return false;
	}
	
	public void processNPCConstruction() {
		Iterator<MarketAPI> iter = npcConstructionQueues.keySet().iterator();
		while (iter.hasNext()) {
			MarketAPI market = iter.next();
			processNPCConstruction(market);
		}
	}
	
	/**
	 * Starts queued construction/upgrade projects on the market as appropriate.
	 * @param market
	 */
	public void processNPCConstruction(MarketAPI market) {
		if (isBuildingAnything(market))
			return;
		
		//log.info("Processing NPC construction queue for " + market.getName());
		LinkedList<QueuedIndustry> queue = npcConstructionQueues.get(market);
		if (queue == null || queue.isEmpty())
			return;
		
		if (market.isPlayerOwned()) {
			// don't build stuff on player-owned market, clear queue
			//npcConstructionQueues.remove(market);	// causes concurrent modification exception if done during the loop
			queue.clear();
			return;
		}
		
		LinkedList<QueuedIndustry> queueCopy = new LinkedList<>(queue);
		for (QueuedIndustry item : queueCopy) {
			//log.info("\tChecking industry queue: " + item.industry + ", " + item.type.toString());
			
			// Station double addition preventer
			if (item.type == QueueType.NEW) {
				Industry temp = market.instantiateIndustry(item.industry);
				if (temp.getSpec().hasTag(Industries.TAG_STATION) && NexMarketBuilder.haveStation(market)) {
					removeItemFromQueue(item, queue);
					continue;
				}
			}
			
			// already has that industry
			if (market.hasIndustry(item.industry)) {
				// queued item is a new build that is unneeded, remove and continue
				if (item.type == QueueType.NEW) {
					removeItemFromQueue(item, queue);
					continue;
				}
				// is an upgrade, start upgrade if possible
				// if not, move on to next item in queue
				else if (item.type == QueueType.UPGRADE) {
					Industry ind = market.getIndustry(item.industry);
					if (ind == null) {
						removeItemFromQueue(item, queue);
						continue;
					}
					if (NexUtilsMarket.upgradeIndustryIfCan(ind, false)) {
						removeItemFromQueue(item, queue);
					}
					else continue;
				}
			}
			else {
				// new build
				if (item.type == QueueType.NEW) {
					// check industry limit; if we're over it, remove from queue
					Industry temp = market.instantiateIndustry(item.industry);
					if (temp == null) 
					{
						removeItemFromQueue(item, queue);
						continue;
					}
					if (temp.isIndustry() && Misc.getNumIndustries(market) >= Misc.getMaxIndustries(market)) 
					{
						removeItemFromQueue(item, queue);
						continue;
					}
					
					market.addIndustry(item.industry);
					market.getIndustry(item.industry).startBuilding();
					removeItemFromQueue(item, queue);
					break;	// no further action once construction is underway
				}
				// trying to upgrade nonexistent structure
				// maybe this will become available once existing constructions/upgrades are finished;
				// in the meantime, let's move on to next item in queue
				else if (item.type == QueueType.UPGRADE) {
					continue;
				}
			}
		}
	}
	
	protected void removeItemFromQueue(QueuedIndustry toRemove, LinkedList<QueuedIndustry> queue) {
		queue.remove(toRemove);
	}

	public boolean removeQueuedIndustry(String industryId, MarketAPI market) {
		LinkedList<QueuedIndustry> queue = getConstructionQueue(market);
		if (queue == null) return false;
		for (QueuedIndustry qi : queue) {
			if (qi.industry.equals(industryId)) {
				removeItemFromQueue(qi, queue);
				return true;
			}
		}
		return false;
	}
	
	public void queueIndustry(MarketAPI market, String industry, QueueType type) {
		if (!npcConstructionQueues.containsKey(market))
			npcConstructionQueues.put(market, new LinkedList<QueuedIndustry>());
		
		LinkedList<QueuedIndustry> queue = npcConstructionQueues.get(market);
		queue.add(new QueuedIndustry(industry, type));
		log.info(String.format("Queued industry %s (type %s) on %s", industry, type.toString(), market.getName()));
	}
	
	public LinkedList<QueuedIndustry> getConstructionQueue(MarketAPI market) {
		return npcConstructionQueues.get(market);
	}
	
	protected static String getString(String id) {
		return getString(id, false);
	}
	
	protected static String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_colonies", id, ucFirst);
	}
	
	// this exists because else it'd be a leak in constructor
	public void init() {
		Global.getSector().getPersistentData().put(PERSISTENT_KEY, this);
		Global.getSector().addScript(this);
		Global.getSector().getListenerManager().addListener(this);
	}
	
	public void incrementDeadExpeditions() {
		numDeadExpeditions++;
	}
	
	public int getNumDeadExpeditions() {
		return numDeadExpeditions;
	}
	
	public void incrementAvertedExpeditions() {
		numAvertedExpeditions++;
	}
	
	public int getNumAvertedExpeditions() {
		return numAvertedExpeditions;
	}
	
	protected float getSurveyDataDaysValue(String commodityId) {
		if (commodityId == null) return 0;
		
		if (SURVEY_DATA_VALUES.containsKey(commodityId))
			return SURVEY_DATA_VALUES.get(commodityId);

		return 0;
	}

	public WeightedRandomPicker<String> generateColonizeFactionPicker(Random random, boolean useSurveyDataBonus) {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			float chance = NexConfig.getFactionConfig(factionId).colonyExpeditionChance;
			if (chance <= 0)
				continue;

			// bonus for having survey data
			if (useSurveyDataBonus) {
				float mult = EXPEDITION_BASE_CHANCE_BEFORE_DATA;
				MemoryAPI mem = Global.getSector().getFaction(factionId).getMemoryWithoutUpdate();
				if (mem.contains(MEMORY_KEY_FACTION_SURVEY_BONUS)) {
					mult += mem.getFloat(MEMORY_KEY_FACTION_SURVEY_BONUS);
				}
				mult /= EXPEDITION_BASE_CHANCE_BEFORE_DATA;
				if (mult < 1) mult = 1;

				chance *= mult;

				//log.info(String.format("Faction %s has colony chance %s, mult %s", factionId, chance, mult));
			}

			picker.add(factionId, chance);
		}
		return picker;
	}
	
	protected String pickFactionToColonize(Random random, boolean useSurveyDataBonus)
	{
		return generateColonizeFactionPicker(random, useSurveyDataBonus).pick();
	}
	
	protected MarketAPI pickColonyExpeditionSource(String factionId, Random random) 
	{
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>(random);
		for (MarketAPI market : NexUtilsFaction.getFactionMarkets(factionId)) {
			if (!NexUtilsMarket.hasWorkingSpaceport(market)) continue;
			int size = market.getSize();
			if (size < 5) continue;
			
			float weight = size * size * market.getStabilityValue();
			if (market.hasIndustry(Industries.MEGAPORT)) {
				weight *= 1.5f;
			}
			if (market.hasIndustry(Industries.WAYSTATION)) {
				weight *= 1.2f;
			}

			picker.add(market, weight);
		}
		return picker.pick();
	}
	
	protected PlanetAPI pickColonyExpeditionTarget(String factionId, SectorEntityToken anchor, boolean silent) 
	{
		Set<PlanetAPI> existingTargets = getExistingColonyTargets();
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		ColonyTargetValuator valuator = loadColonyTargetValuator(factionId);
		if (silent) valuator.setSilent(true);
		
		//WeightedRandomPicker<PlanetAPI> planetPicker = new WeightedRandomPicker<>();
		PlanetAPI best = null;
		float bestScore = 0;
		float maxDist = valuator.getMaxDistanceLY(faction);
		float minScore = valuator.getMinScore(faction);
		for (StarSystemAPI system : Global.getSector().getStarSystems()) 
		{
			//log.info("Trying system " + system.getBaseName());
			if (!valuator.prefilterSystem(system, faction)) {
				//log.info("Filtering system " + system.getBaseName());
				continue;
			}
			
			float dist = Misc.getDistanceLY(system.getHyperspaceAnchor(), anchor);
			if (dist > maxDist) {
				//log.info("System too far: " + system.getBaseName());
				continue;
			}
			
			for (PlanetAPI planet : system.getPlanets()) 
			{
				if (existingTargets.contains(planet)) continue;
				
				MarketAPI market = planet.getMarket();
				if (market == null || market.isInEconomy()) continue;
				if (!valuator.prefilterMarket(planet.getMarket(), faction)) {
					//log.info("Filtering planet " + planet.getMarket().getName());
					continue;
				}
				
				float score = valuator.evaluatePlanet(planet.getMarket(), dist, faction);
				if (score < minScore) continue;
				
				score *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
				
				//log.info("  Adding planet " + planet);
				if (score > bestScore) {
					bestScore = score;
					best = planet;
				}
			}
		}
		
		return best;
	}
	
	public static <T extends ColonyTargetValuator> T loadColonyTargetValuator(String factionId)
	{
		ColonyTargetValuator valuator = null;
		String className = NexConfig.getFactionConfig(factionId).colonyTargetValuator;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			valuator = (ColonyTargetValuator)clazz.newInstance();
			valuator.initForFaction(factionId);
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(IndustryClassGen.class).error("Failed to load colony target valuator " + className, ex);
		}

		return (T)valuator;
	}
	
	/**
	 * Returns the set of planets for which a colony expedition is already ongoing.
	 * @return
	 */
	public Set<PlanetAPI> getExistingColonyTargets() {
		Set<PlanetAPI> targets = new HashSet<>();
		for (IntelInfoPlugin intRaw : Global.getSector().getIntelManager().getIntel(ColonyExpeditionIntel.class)) {
			ColonyExpeditionIntel intel = (ColonyExpeditionIntel)intRaw;
			if (intel.isEnding() || intel.isEnded()) continue;
			targets.add(intel.getTargetPlanet());
		}
		return targets;
	}
	
	// debug command: runcode exerelin.campaign.ColonyManager.getManager().spawnColonyExpedition();
	/**
	 * Spawns a colony expedition for a random faction to a semi-randomly-selected suitable target.
	 * @return The colony expedition intel, if successfully created.
	 */
	public ColonyExpeditionIntel spawnColonyExpedition() 
	{
		Random random = new Random();
		log.info("Attempting to spawn colony expedition");
		String factionId = pickFactionToColonize(random, true);
		if (factionId == null) {
			log.info("Failed to pick faction for expedition");
			return null;
		}
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		MarketAPI source = pickColonyExpeditionSource(factionId, random);
		if (source == null) {
			log.info("Failed to pick source market for expedition");
			return null;
		}
		SectorEntityToken anchor;
		if (source.getContainingLocation().isHyperspace()) anchor = source.getPrimaryEntity();
		else anchor = source.getStarSystem().getHyperspaceAnchor();
		
		PlanetAPI target = pickColonyExpeditionTarget(factionId, anchor, false);
		if (target == null) {
			log.info("Failed to pick target for expedition");
			return null;
		}
		
		float fp = 80 + 30 * numDeadExpeditions;
		if (fp > MAX_EXPEDITION_FP) fp = MAX_EXPEDITION_FP;
		float organizeTime = InvasionFleetManager.getOrganizeTime(fp + 30) + 30;
		
		ColonyExpeditionIntel intel = new ColonyExpeditionIntel(faction, source, target.getMarket(), fp, organizeTime);
		intel.init();
		return intel;
	}
	
	/**
	 * Like {@code spawnColonyExpedition}, but creates the colony instantly instead of sending an expedition.
	 * @param random
	 * @return
	 */
	public boolean generateInstantColony(Random random) 
	{
		log.info("Attempting to generate instant colony");
		String factionId = pickFactionToColonize(random, false);
		if (factionId == null) {
			//log.info("Failed to pick faction for colony");
			return false;
		}
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		MarketAPI source = pickColonyExpeditionSource(factionId, random);
		if (source == null) {
			//log.info("Failed to pick source market for colony");
			return false;
		}
		SectorEntityToken anchor;
		if (source.getContainingLocation().isHyperspace()) anchor = source.getPrimaryEntity();
		else anchor = source.getStarSystem().getHyperspaceAnchor();
		
		PlanetAPI target = pickColonyExpeditionTarget(factionId, anchor, true);
		if (target == null) {
			log.info("Failed to pick target for colony");
			return false;
		}
		
		ColonyExpeditionIntel.createColonyStatic(target.getMarket(), target, faction, false, false);
		return true;
	}
	
	public void processReliefFleetEvent(List<MarketAPI> candidates) {
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		for (MarketAPI market : candidates) {
			int weight = market.getSize() * (int)(10 - market.getStabilityValue());
			if (weight <= 0) continue;
			picker.add(market, weight);
		}
		MarketAPI target = picker.pick();
		if (target == null)
			return;
		
		// pick a market to send relief from
		FactionAPI faction = target.getFaction();
		picker.clear();
		
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(target.getEconGroup()))
		{
			if (market == target) continue;	// no self-relief
			if (market.getFaction().isPlayerFaction()) continue;
			
			FactionAPI other = market.getFaction();
			if (!NexConfig.getFactionConfig(other.getId()).playableFaction)
				continue;
			if (other.isAtBest(faction, RepLevel.SUSPICIOUS))
				continue;
			if (market.getSize() < target.getSize() - 1)
				continue;
			int weight = market.getSize();
			if (faction != other) {
				switch (other.getRelationshipLevel(faction)) {
					case FAVORABLE:
						weight *= 1.5f;
						break;
					case WELCOMING:
						weight *= 2;
						break;
					case FRIENDLY:
						weight *= 3;
						break;
					case COOPERATIVE:
						weight *= 4;
						break;
				}
				if (AllianceManager.areFactionsAllied(faction.getId(), other.getId())) {
					weight *= 4;
				}
			}
			else
				weight *= 20;
			
			picker.add(market, weight);
		}
		
		MarketAPI source = picker.pick();
		if (source == null)
			return;
		
		ReliefFleetIntelAlt.createEvent(source, target);
		reliefFleetCooldown += (target.getSize() + 1) * 15;
	}

	public void addXPFromProfit() {
		long xp = Math.round(profitMarginForXP * Global.getSettings().getFloat("nex_xpPerProfitMargin"));
		if (xp > 0) {
			Global.getSector().getCampaignUI().addMessage(getString("profitXPMessage"), Misc.getPositiveHighlightColor());
			Global.getSector().getPlayerPerson().getStats().addXP(xp);
		}
		profitMarginForXP = 0;
	}
	
	/**
	 * Generates an official in the specified post for the specified market, if needed. 
	 * If an official is already present in that post, update their rank.
	 * @param market
	 * @param rankId Optional.
	 * @param postId
	 * @param postsPresent Set of posts already found on the market before adding/updating. 
	 * If a new official is added, their post is added to this set.
	 */
	public void addOrUpdateOfficial(MarketAPI market, String rankId, String postId, 
			Set<String> postsPresent) 
	{
		if (postsPresent.contains(postId) && rankId != null) {
			PersonAPI person = NexUtilsMarket.getPerson(market, postId);
			if (person != null && !rankId.equals(person.getRankId()))
				person.setRankId(rankId);
			return;
		}
		NexUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, rankId, postId, true);
		postsPresent.add(postId);
	}
	
	public void processSurveyData(String factionId, float value) {
		MemoryAPI mem = Global.getSector().getFaction(factionId).getMemoryWithoutUpdate();
		NexUtils.incrementMemoryValue(mem, MEMORY_KEY_FACTION_SURVEY_BONUS, value);
		if (ExerelinModPlugin.isNexDev) {
			log.info(String.format("Adding %s survey data value to faction %s", value, factionId));
		}
	}

	/**
	 * When data is sold to a faction that does not send colony expeditions.
	 * @param values
	 */
	public void processSurveyDataFactionless(List<Float> values, @Nullable Random random) {
		WeightedRandomPicker<String> picker = generateColonizeFactionPicker(random, false);
		for (Float val : values) {
			processSurveyData(picker.pick(), val);
		}
	}
	
	/**
	 * Adds base officials to the market based on the industries present. 
	 * If the relevant officials are already present, updates their ranks. 
	 * Behavior should match {@code CoreLifeCyclePluginImpl.createInitialPeople()}.
	 * @param market
	 */
	public void addOrUpdateOfficials(MarketAPI market) {
		Set<String> officialsPresent = new HashSet<>();
		
		for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
		{
			if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
			PersonAPI person = (PersonAPI)dir.getEntryData();
			if (person.getFaction() != market.getFaction()) continue;
			if (!NEEDED_OFFICIALS.contains(person.getPostId())) continue;
			officialsPresent.add(person.getPostId());
		}
		
		// Base commander
		boolean havePerson = !officialsPresent.isEmpty();
		if (market.hasIndustry(Industries.MILITARYBASE) || market.hasIndustry(Industries.HIGHCOMMAND)) {
			String rankId = Ranks.GROUND_MAJOR;
			if (market.getSize() >= 6) {
				rankId = Ranks.GROUND_GENERAL;
			} else if (market.getSize() >= 4) {
				rankId = Ranks.GROUND_COLONEL;
			}
			addOrUpdateOfficial(market, rankId, Ranks.POST_BASE_COMMANDER, officialsPresent);
			havePerson = true;
		}
		
		// Station commander
		boolean hasStation = false;
		for (Industry curr : market.getIndustries()) {
			if (curr.getSpec().hasTag(Industries.TAG_STATION)) {
				hasStation = true;
				break;
			}
		}
		if (hasStation) {
			String rankId = Ranks.SPACE_COMMANDER;
			if (market.getSize() >= 6) {
				rankId = Ranks.SPACE_ADMIRAL;
			} else if (market.getSize() >= 4) {
				rankId = Ranks.SPACE_CAPTAIN;
			}
			addOrUpdateOfficial(market, rankId, Ranks.POST_STATION_COMMANDER, officialsPresent);
			havePerson = true;
		}
		
		// Portmaster
		if (market.hasSpaceport()) {
			addOrUpdateOfficial(market, null, Ranks.POST_PORTMASTER, officialsPresent);
			havePerson = true;
		}
		
		// Supply officer
		if (havePerson) {
			addOrUpdateOfficial(market, Ranks.SPACE_COMMANDER, Ranks.POST_SUPPLY_OFFICER, officialsPresent);
			havePerson = true;
		}
		
		// Administrator
		if (true || !havePerson) {
			addOrUpdateOfficial(market, Ranks.CITIZEN, Ranks.POST_ADMINISTRATOR, officialsPresent);
			havePerson = true;
		}
	}
	
	// add admin to player market if needed
	// also adds military submarkets to places that should have them
	// handles temporary governorship in ruler mode
	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {
		if (market.getFaction().isPlayerFaction() && !market.isHidden())
		{
			addOrUpdateOfficials(market);
		}
		if (SectorManager.shouldHaveMilitarySubmarket(market))
		{
			SectorManager.addOrRemoveMilitarySubmarket(market, market.getFactionId(), true);
		}

		// make player the temp owner if faction ruler
		if (!market.isPlayerOwned() && market.getFaction() == Misc.getCommissionFaction()
				&& Nex_IsFactionRuler.isRuler(market.getFactionId())
				&& !market.getMemoryWithoutUpdate().contains(MEMORY_KEY_RULER_TEMP_OWNERSHIP))
		{
			PersonAPI admin = market.getAdmin();
			market.getMemoryWithoutUpdate().set(MEMORY_KEY_RULER_TEMP_OWNERSHIP, admin, 0);
			CommDirectoryEntryAPI entry = market.getCommDirectory().getEntryForPerson(admin);
			if (entry != null) {
				market.getMemoryWithoutUpdate().set(MEMORY_KEY_RULER_TEMP_OWNERSHIP_ADMINDEX, market.getCommDirectory().getEntriesCopy().indexOf(entry), 0);
			}
			market.setPlayerOwned(true);
		}
	}
	
	// unset player as temporary planet owner in ruler mode
	@Override
	public void reportPlayerClosedMarket(MarketAPI market) {
		if (market.isPlayerOwned())
		{
			MemoryAPI mem = market.getMemoryWithoutUpdate();
			if (!mem.contains(MEMORY_KEY_RULER_TEMP_OWNERSHIP)) return;
			
			PersonAPI admin = (PersonAPI)mem.get(MEMORY_KEY_RULER_TEMP_OWNERSHIP);
			market.setPlayerOwned(false);
			market.setAdmin(admin);
			int index = 0;
			if (market.getMemoryWithoutUpdate().contains(MEMORY_KEY_RULER_TEMP_OWNERSHIP_ADMINDEX)) {
				index = market.getMemoryWithoutUpdate().getInt(MEMORY_KEY_RULER_TEMP_OWNERSHIP_ADMINDEX);
			}
			market.getCommDirectory().addPerson(admin, index);
			mem.unset(MEMORY_KEY_RULER_TEMP_OWNERSHIP);
		}
	}

	// Handle survey data contribution to colony expeditions
	@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
		if (transaction.getSubmarket().getPlugin().isFreeTransfer())
			return;
		
		float net = 0;
		List<Float> values = new ArrayList<>();
		
		for (CargoStackAPI stack : transaction.getSold().getStacksCopy()) {
			float val = getSurveyDataDaysValue(stack.getCommodityId());
			net += val * stack.getSize();
			for (int i=0; i<stack.getSize(); i++) values.add(val);
		}
		for (CargoStackAPI stack : transaction.getBought().getStacksCopy()) {
			float val = -getSurveyDataDaysValue(stack.getCommodityId());
			net += val * stack.getSize();
			for (int i=0; i<stack.getSize(); i++) values.add(val);
		}
		if (net == 0) return;

		String factionId = transaction.getSubmarket().getFaction().getId();
        processSurveyData(factionId, net);
		/*
		if (generateColonizeFactionPicker(null, false).getItems().contains(factionId)) {
			processSurveyData(factionId, net);
		} else {
		    // creates weird results on buyback since the factions losing progress won't be the ones who gained it
			processSurveyDataFactionless(values, new Random());
		}
		*/

		log.info("Colony expedition progress from selling survey data: " + net);
		colonyExpeditionProgress += net;
	}
	
	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		if (TutorialMissionIntel.isTutorialInProgress()) 
			return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		if (reliefFleetCooldown > 0) {
			reliefFleetCooldown -= days;
			if (reliefFleetCooldown < 0) reliefFleetCooldown = 0;
		}
		
		if (Global.getSector().getClock().getCycle() < MIN_CYCLE_FOR_EXPEDITIONS)
			return;

		if (Global.getSettings().getBoolean("nex_colonyExpeditionOnlyAfterGalatia")) {
			boolean completed = !SectorManager.getManager().isCorvusMode() || Global.getSector().getMemoryWithoutUpdate().getBoolean("$gaATG_missionCompleted");
			if (!completed) return;
		}

		if (NexConfig.colonyExpeditionsOnlyAfterPlayerColony) {
			boolean have = Misc.isPlayerFactionSetUp();
			if (!have) return;
		}
		
		colonyExpeditionProgress += days;
		float interval = NexConfig.colonyExpeditionInterval;
		if (interval < 0) return;
		if (colonyExpeditionProgress > interval) {
			ColonyExpeditionIntel intel = spawnColonyExpedition();
			if (intel != null) {
				colonyExpeditionProgress = MathUtils.getRandomNumberInRange(-interval * 0.1f, interval * 0.1f);
				colonyExpeditionProgress -= numColonies * Global.getSettings().getFloat("nex_expeditionDelayPerExistingColony");
				intel.getFaction().getMemoryWithoutUpdate().set(MEMORY_KEY_FACTION_SURVEY_BONUS, 0);
				InvasionFleetManager.getManager().modifySpawnCounter(intel.getFaction().getId(), InvasionFleetManager.getInvasionPointCost(intel) * 2);
			}
			else {	// failed to spawn, try again in 10 days
				colonyExpeditionProgress -= Math.min(NexConfig.colonyExpeditionInterval/2, 10);
			}
		}
	}
	
	public float getColonyExpeditionProgress() {
		return colonyExpeditionProgress;
	}
	
	public int getNumColonies() {
		return numColonies;
	}
	
	/**
	 * Resets player gathering point if we no longer own the current one.
	 * @param market The market whose ownership recently changed.
	 */
	public void checkGatheringPoint(MarketAPI market) {
		FactionAPI pf = Global.getSector().getPlayerFaction();
		FactionProductionAPI prod = pf.getProduction();
		
		MarketAPI gatheringPoint = prod.getGatheringPoint();
		if (gatheringPoint == market && !market.isPlayerOwned()) {
			prod.setGatheringPoint(pickNewGatheringPoint());
		}
	}
	
	protected MarketAPI pickNewGatheringPoint() {
		MarketAPI largest = null;
		int largestSize = 0;
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (!market.isPlayerOwned()) continue;
			if (market.getSize() > largestSize) {
				largest = market;
				largestSize = market.getSize();
			}
		}
		return largest;
	}
	
	public void printCoreStashMessage(String str, int numCores, MarketAPI market) {
		if (numCores <= 0) return;
		InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
		Color hl = Misc.getHighlightColor();
		Color marketColor = market.getFaction().getBaseUIColor();
		
		if (dialog != null) {
			str += ".";
			LabelAPI label = dialog.getTextPanel().addPara(str, Misc.getHighlightColor(), 
					numCores + "", market.getName());
			label.setHighlight(numCores + "", market.getName());
			label.setHighlightColors(hl, marketColor);
		}
		else {
			Global.getSector().getCampaignUI().addMessage(str, Misc.getTextColor(), 
					numCores + "", market.getName(), hl, marketColor);
		}
	}
	
	/**
	 * Uninstall AI cores from industries and store them in memory.
	 * @param market
	 */
	public void stashCores(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner) {
		Map<String, String> industriesToCores;
		if (market.getMemoryWithoutUpdate().contains(MEMORY_KEY_STASHED_CORES))
			industriesToCores = (Map<String, String>)(market.getMemoryWithoutUpdate()
					.get(MEMORY_KEY_STASHED_CORES));
		else
			industriesToCores = new HashMap<>();
		
		int numStashed = 0;
		for (Industry ind : market.getIndustries()) {
			String indId = ind.getId();
			if (industriesToCores.containsKey(indId)) continue;
			String aiId = ind.getAICoreId();
			
			if (aiId != null ) {
				industriesToCores.put(indId, aiId);
				ind.setAICoreId(null);
				numStashed++;
			}
		}

		PersonAPI admin = market.getAdmin();
		if (admin != null && admin.isAICore()) {
			market.getMemoryWithoutUpdate().set(MEMORY_KEY_STASHED_CORE_ADMIN, admin);
			market.setAdmin(null);

			// set AI admin to the faction they were before SectorManager applied its changes
			if (SectorManager.POSTS_TO_CHANGE_ON_CAPTURE.contains(admin.getPostId())) {
				admin.setFaction(oldOwner.getId());
			}

			reassignAdminIfNeeded(market, oldOwner, newOwner);
			replaceDisappearedAdmin(market, admin);
			numStashed++;
		}

		market.getMemoryWithoutUpdate().set(MEMORY_KEY_STASHED_CORES, industriesToCores);
		printCoreStashMessage(StringHelper.getString("exerelin_misc", "aiCoreStashMsg"), 
				numStashed, market);
	}
	
	/**
	 * Restore AI cores from memory and reassign them to industries, or as admin.
	 * @param market
	 */
	public void restoreCores(MarketAPI market) {
		int numRestored = 0;

		if (market.getMemoryWithoutUpdate().contains(MEMORY_KEY_STASHED_CORE_ADMIN)) {
			PersonAPI aiAdmin = (PersonAPI)market.getMemoryWithoutUpdate().get(MEMORY_KEY_STASHED_CORE_ADMIN);
			PersonAPI currAdmin = market.getAdmin();
			market.setAdmin(aiAdmin);
			replaceDisappearedAdmin(market, currAdmin);
			market.getMemoryWithoutUpdate().unset(MEMORY_KEY_STASHED_CORE_ADMIN);
			//market.getCommDirectory().addPerson(aiAdmin);

			// set AI admin to the new faction
			if (SectorManager.POSTS_TO_CHANGE_ON_CAPTURE.contains(aiAdmin.getPostId()))
				aiAdmin.setFaction(market.getFactionId());

			numRestored++;
		}

		if (market.getMemoryWithoutUpdate().contains(MEMORY_KEY_STASHED_CORES))
		{
			Map<String, String> industriesToCores = (Map<String, String>) (market
					.getMemoryWithoutUpdate().get(MEMORY_KEY_STASHED_CORES));

			for (Industry ind : market.getIndustries()) {
				String indId = ind.getId();
				if (!industriesToCores.containsKey(indId)) continue;

				String currAI = ind.getAICoreId();
				String wantedAI = industriesToCores.get(indId);

				// If industry already has an AI core installed, put the stashed one in storage
				// else, assign it to the industry
				if (currAI == null)
					ind.setAICoreId(wantedAI);
				else
					market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().addCommodity(wantedAI, 1);
				numRestored++;
			}
			market.getMemoryWithoutUpdate().unset(MEMORY_KEY_STASHED_CORES);
		}

		if (numRestored > 0) {
			printCoreStashMessage(StringHelper.getString("exerelin_misc", "aiCoreRestoreMsg"),
					numRestored, market);
		}
	}
	
	public static boolean doesFactionAllowAI(FactionAPI faction) {
		return !DiplomacyTraits.hasTrait(faction.getId(), TraitIds.HATES_AI);
	}
	
	public void checkIndustriesAfterSatBomb(MarketAPI market) {
		int max = Misc.getMaxIndustries(market);
		int curr = Misc.getNumIndustries(market);
		
		if (curr > max) {
			float lowestCost = 999999999;
			Industry cheapest = null;
			
			for (Industry ind : market.getIndustries()) {
				IndustrySpecAPI spec = ind.getSpec();
				if (!spec.hasTag(Industries.TAG_INDUSTRY)) continue;
				if (ind.isHidden()) continue;
				if (!ind.isAvailableToBuild()) continue;
				if (!ind.canShutDown()) continue;
				
				float cost = ind.getBuildCost();
				if (cost < lowestCost) {
					lowestCost = cost;
					cheapest = ind;
				}
			}
			
			if (cheapest != null) {
				InteractionDialogAPI dialog = Global.getSector().getCampaignUI().getCurrentInteractionDialog();
				if (dialog != null) {
					String str = StringHelper.getStringAndSubstituteToken("nex_bombardment", 
							"effectIndustryRemoved", "$market", market.getName());
					dialog.getTextPanel().addPara(str, Misc.getHighlightColor(), cheapest.getCurrentName());
				}
				
				market.removeIndustry(cheapest.getId(), null, false);
			}
		}
	}
	
	/**
	 * Check if the AI cores on this market should be stashed following market capture.
	 * @param market
	 * @param oldOwner
	 * @param newOwner
	 */
	public void coreStashCheck(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner)
	{
		//boolean oldAllowsAI = doesFactionAllowAI(oldOwner);
		boolean newAllowsAI = doesFactionAllowAI(newOwner);
		if (!newAllowsAI) stashCores(market, oldOwner, newOwner);
		else restoreCores(market);
	}

	public void checkFactionMarketCondition(MarketAPI market) {
		MarketConditionAPI cond = market.getCondition(FactionConditionPlugin.CONDITION_ID);
		if (cond != null) {
			((FactionConditionPlugin)cond.getPlugin()).checkForRegen();
		}
	}

	public void reportSatBomb(MarketAPI market, MarketCMD.TempData actionData) {
		checkIndustriesAfterSatBomb(market);

		// increment attacker badboy
		if (actionData instanceof Nex_MarketCMD.NexTempData) {
			Nex_MarketCMD.NexTempData nad = (Nex_MarketCMD.NexTempData)actionData;
			if (!nad.satBombLimitedHatred && nad.attackerFaction != null) {
				DiplomacyManager.modifyBadboy(nad.attackerFaction, nad.sizeBeforeBombardment * nad.sizeBeforeBombardment);
			}
		}
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		if (TutorialMissionIntel.isTutorialInProgress()) 
			return;
		// workaround: reportEconomyTick is called twice,
		// since we've added colony manager as a script in sector, and also as a listener
		// so don't do anything the second time
		// Dunno if this is still needed since we're no longer implementing EconomyTickListener, but keep it for now
		if (currIter == iterIndex) {
			log.error("reportEconomyTick called twice by ColonyManager");
			return;
		}
		
		updateMarkets();
		processNPCConstruction();
		processStorageRebate();
		
		currIter = iterIndex;
	}

	@Override
	public void reportEconomyMonthEnd() {
		addXPFromProfit();
	}
	
	@Override
	public void reportPlayerColonizedPlanet(PlanetAPI planet) {
		MarketAPI market = planet.getMarket();
		market.addCondition(FactionConditionPlugin.CONDITION_ID);
		if (!market.getMemoryWithoutUpdate().contains(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION))
			market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, Factions.PLAYER);
	}
	
	@Override
	public void reportPlayerAbandonedColony(MarketAPI market) {}

	@Override
	public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market, 
			Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {
	}

	@Override
	public void reportInvasionRound(InvasionRound.InvasionRoundResult result, 
			CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr) {
	}

	@Override
	public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction, 
			MarketAPI market, float numRounds, boolean success) {
	}

	@Override
	public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner, boolean playerInvolved,
			boolean isCapture, List<String> factionsToNotify, float repChangeStrength) 
	{
		LinkedList<QueuedIndustry> existing = npcConstructionQueues.remove(market);
		if (!newOwner.isPlayerFaction()) 
		{			
			// hax to handle "give small colony with HI away for raiding" exploit
			// if the faction doesn't really need the industry and can't defend it, remove it
			float def = InvasionRound.getDefenderStrength(market, 1);
			EconomyInfoHelper help = EconomyInfoHelper.getInstance();
			if (!isCapture && oldOwner.isPlayerFaction() && def < 1000 && help != null 
					&& help.hasHeavyIndustry(newOwner.getId()))
			{
				log.info("Removing vulnerable heavy industry on " + market.getName());
				final List<String> toRemove = new ArrayList<>();
				for (Industry ind : market.getIndustries()) {
					if (ind.getSpec().hasTag(Industries.TAG_HEAVYINDUSTRY) && !ind.getSpec().getId().equals("IndEvo_ScrapYard")) 
					{
						toRemove.add(ind.getId());
					}
				}

				// delay the actual removal to avoid screwing with payout on conquest missions
				// actually no, since this allows the exploit to continue if you don't leave the screen
				// instead, we'll add a memory key to inform the conquest mission
				/*
				final MarketAPI marketF = market;
				Global.getSector().addScript(new DelayedActionScript(0) {
					@Override
					public void doAction() {
						for (String indId : toRemove) {
							moveSpecialsToBestSubmarket(marketF, marketF.getIndustry(indId));
							marketF.removeIndustry(indId, null, false);
						}
						marketF.reapplyIndustries();
					}
				});
				 */

				float bonus = 0;
				if (market.getMemoryWithoutUpdate().contains(ConquestMissionIntel.MEMKEY_CONQUEST_VALUE_BONUS)) {
					bonus = market.getMemoryWithoutUpdate().getFloat(ConquestMissionIntel.MEMKEY_CONQUEST_VALUE_BONUS);
				}
				for (String indId : toRemove) {
					moveSpecialsToBestSubmarket(market, market.getIndustry(indId));
					bonus += market.getIndustry(indId).getBuildCost();
					market.removeIndustry(indId, null, false);
				}
				market.getMemoryWithoutUpdate().set(ConquestMissionIntel.MEMKEY_CONQUEST_VALUE_BONUS, bonus, 0);
				
				market.reapplyIndustries();
			}
			
			if (oldOwner.isPlayerFaction() || existing != null) {
				buildIndustries(market);
				processNPCConstruction(market);
			}
		}
		
		// reassign admins on market capture
		reassignAdminIfNeeded(market, oldOwner, newOwner);
		setGrowthRate(market);
		
		checkGatheringPoint(market);
		
		coreStashCheck(market, oldOwner, newOwner);
		
		checkFactionMarketCondition(market);
		
		// Turn "derelict officers" into normal ones
		if (oldOwner.getId().equals("nex_derelict")) 
		{
			for (CommDirectoryEntryAPI entry : market.getCommDirectory().getEntriesCopy())
			{
				PersonAPI person = (PersonAPI)entry.getEntryData();
				String postId = person.getPostId();
				//log.info("Checking person " + person.getNameString() + ", " + postId);
				if (person.getMemoryWithoutUpdate().getBoolean("$nex_derelict_officer_removed")) continue;
				if (Ranks.POST_FREELANCE_ADMIN.equals(postId) || Ranks.POST_MERCENARY.equals(postId)
						|| Ranks.POST_OFFICER_FOR_HIRE.equals(postId)) {
					PersonAPI temp = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 3);
					person.setPortraitSprite(temp.getPortraitSprite());
					person.setName(temp.getName());
					person.getMemoryWithoutUpdate().set("$nex_derelict_officer_removed", true);
				}
			}			
		}
	}
	
	/**
	 * Move any special items from the specified industry to open market, black market, 
	 * military submarket, or storage (in that order, picks the first one available).
	 * @param market
	 * @param ind
	 */
	public static void moveSpecialsToBestSubmarket(MarketAPI market, Industry ind) {
		String aiCore = ind.getAICoreId();
		SpecialItemData special = ind.getSpecialItem();
		SubmarketAPI sub = market.getSubmarket(Submarkets.SUBMARKET_OPEN);
		if (sub == null) {
			market.getSubmarket(Submarkets.SUBMARKET_BLACK);
		}
		if (sub == null) {
			market.getSubmarket(Submarkets.GENERIC_MILITARY);
		}
		if (sub == null) {
			market.getSubmarket(Submarkets.SUBMARKET_STORAGE);
		}
		if (sub == null) return;
		
		if (aiCore != null) {
			sub.getCargo().addCommodity(aiCore, 1);
		}
		if (special != null) {
			sub.getCargo().addSpecial(special, 1);
		}
	}
	
	public static void buildIndustry(MarketAPI market, String id) {
		buildIndustry(market, id, false);
	}
	
	public static void buildIndustry(MarketAPI market, String id, boolean instant) {
		if (market.hasIndustry(id))
			return;
		
		market.addIndustry(id);
		Industry ind = market.getIndustry(id);
		if (!instant) ind.startBuilding();
	}
	
	/**
	 * Adds industries to a market. Used by NPC colonies, and semi-counters 
	 * market stripping by the player.
	 * @param market
	 * @param military Adds military structures.
	 * @param productive Adds productive industries such as farms and mines.
	 */
	public static void buildIndustries(MarketAPI market, boolean military, boolean productive) {		
		Random random = new Random();
		ProcGenEntity entity = ExerelinProcGen.createEntityData(market.getPrimaryEntity());
		entity.numProductiveIndustries = countProductive(market);
		
		if (!market.hasIndustry(Industries.SPACEPORT) && !market.hasIndustry(Industries.MEGAPORT))
			NexMarketBuilder.addSpaceportOrMegaport(market, entity.type, false, random);

		if (!STRATEGIC_AI_BLOCKS_BUILD_MILITARY_ON_UPSIZE || StrategicAI.getAI(market.getFactionId()) == null) {
			if (military)
				NexMarketBuilder.addMilitaryStructures(entity, false, random);
		}

		if (!STRATEGIC_AI_BLOCKS_BUILD_ECON_ON_UPSIZE || StrategicAI.getAI(market.getFactionId()) == null) {
			if (productive)
				NexMarketBuilder.addIndustriesToMarket(entity, false, random);
		}
	}
	
	/**
	 * Adds industries to a market. Used by NPC colonies, and semi-counters 
	 * market stripping by the player.
	 * <p>This overload build spaceports by default (if needed), and tries to automatically
	 * figure out if it should also build military and productive structures.</p>
	 * @param market
	 */
	public static void buildIndustries(MarketAPI market) {
		if (market.getMemoryWithoutUpdate().getBoolean(ColonyExpeditionIntel.MEMORY_KEY_COLONY)) {
			buildIndustries(market, true, true);
			return;
		}
		
		// if the spaceport is gone we can guess that player strip-mined the market
		boolean productive = true;	//(!market.hasIndustry(Industries.SPACEPORT) && !market.hasIndustry(Industries.MEGAPORT));
		boolean military = shouldBuildMilitary(market);
		buildIndustries(market, military, productive);
	}
	
	public static boolean shouldBuildMilitary(MarketAPI market) {
		if (!market.hasIndustry(Industries.PATROLHQ) 
				&& !market.hasIndustry(Industries.MILITARYBASE) 
				&& !market.hasIndustry(Industries.HIGHCOMMAND)
				&& !market.hasIndustry("tiandong_merchq"))
			return true;
		if (!market.hasIndustry(Industries.GROUNDDEFENSES) 
				&& !market.hasIndustry(Industries.HEAVYBATTERIES))
			return true;
		if (!NexMarketBuilder.haveStation(market))
			return true;
		return false;
	}
	
	public static int countProductive(MarketAPI market) {
		int count = 0;
		Map<String, IndustryClassGen> gens = NexMarketBuilder.getIndustryClassesByIndustryId();
		for (Industry ind : market.getIndustries()) {
			String id = ind.getSpec().getId();
			if (!gens.containsKey(id)) continue;
			if (gens.get(id).isSpecial()) continue;
			count++;
		}
		return count;
	}
	
	/**
	 * NPC admins will vanish from the market and its comm board if replaced, so
	 * call this when changing admins to put them back.
	 * @param market
	 * @param prevAdmin
	 */
	public static void replaceDisappearedAdmin(MarketAPI market, PersonAPI prevAdmin) 
	{
		if (prevAdmin == null) return;
		if (prevAdmin.isPlayer()) return;
		if (isPlayerHiredAdmin(prevAdmin)) return;
		if (prevAdmin.isDefault()) return;
		
		market.addPerson(prevAdmin);
		market.getCommDirectory().addPerson(prevAdmin);
	}
	
	/**
	 * Is the specified person a player-hired admin?
	 * @param person
	 * @return
	 */
	public static boolean isPlayerHiredAdmin(PersonAPI person) {
		for (AdminData data : Global.getSector().getCharacterData().getAdmins())
		{
			if (data.getPerson() == person) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Sets a person on the market's comm board to be admin, if appropriate.
	 * @param market
	 * @param oldOwner
	 * @param newOwner
	 */
	public static void reassignAdminIfNeeded(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner) {
		
		// do nothing if admin is AI core (unless it's a faction ruler, e.g. II)
		// or if the new owner hates AI
		if (market.getAdmin().isAICore() && !Ranks.POST_FACTION_LEADER.equals(market.getAdmin().getPostId()) && !doesFactionAllowAI(newOwner))
		{
			return;
		}
		
		PersonAPI currAdmin = market.getAdmin();
		
		// if player has captured a market, assign player as admin
		if (newOwner.isPlayerFaction() && market.isPlayerOwned()) {
			market.setAdmin(Global.getSector().getPlayerPerson());
			replaceDisappearedAdmin(market, currAdmin);
			return;
		}
		
		// if market was player-controlled or administered by a faction leader, pick a new admin from comm board
		boolean reassign = oldOwner.isPlayerFaction() || market.isPlayerOwned() 
				|| Ranks.POST_FACTION_LEADER.equals(market.getAdmin().getPostId())
				|| hasFactionLeaderOrPreferredAdmin(market);
		if (!reassign)
			return;
		
		PersonAPI admin = getBestAdmin(market);
		market.setAdmin(admin);
		replaceDisappearedAdmin(market, currAdmin);
	}
	
	public static boolean hasFactionLeader(MarketAPI market) {
		for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
		{
			if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
			PersonAPI person = (PersonAPI)dir.getEntryData();
			if (Ranks.POST_FACTION_LEADER.equals(person.getPostId()))
				return true;
		}
		return false;
	}
	
	public static boolean hasFactionLeaderOrPreferredAdmin(MarketAPI market) {
		for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
		{
			if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
			PersonAPI person = (PersonAPI)dir.getEntryData();
			if (Ranks.POST_FACTION_LEADER.equals(person.getPostId()))
				return true;
			if (person.getMemoryWithoutUpdate().getString("$nex_preferredAdmin_factionId") != null)
				return true;
		}
		return false;
	}
	
	/**
	 * Gets the person on the market's comm board who should be admin (based on their post).
	 * @param market
	 * @return
	 */
	public static PersonAPI getBestAdmin(MarketAPI market) {
		PersonAPI best = null;
		int bestScore = -1;
		for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
		{
			if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
			PersonAPI person = (PersonAPI)dir.getEntryData();
			int score = getAdminScore(market, person);
			if (score > bestScore) {
				best = person;
				bestScore = score;
			}
		}
		
		return best;
	}
	
	public static int getAdminScore(MarketAPI market, PersonAPI person) {
		String postId = person.getPostId();
		if (postId == null) return -1;
		
		if (person.getMemoryWithoutUpdate().getBoolean("$nex_preferredAdmin")) {
			String factionId = person.getMemoryWithoutUpdate().getString("$nex_preferredAdmin_factionId");
			//log.info(String.format("Preferred admin %s has faction ID %s, market faction is %s", person.getNameString(), factionId, market.getFactionId()));
			if (factionId == null || factionId.equals(market.getFactionId()))
				return 4;
		}
		
		if (postId.equals(Ranks.POST_FACTION_LEADER) && person.getFaction() == market.getFaction())
			return 3;
		else if (postId.equals(Ranks.POST_STATION_COMMANDER)) 
		{
			if (market.getPrimaryEntity().hasTag(Tags.STATION))
				return 2;
			return 0;
		}
		else if (postId.equals(Ranks.POST_ADMINISTRATOR))
			return 1;
		
		return -1;
	}
	
	public static ColonyManager getManager()
	{
		return (ColonyManager)Global.getSector().getPersistentData().get(PERSISTENT_KEY);
	}

	@Override
	public void reportRaidForValuablesFinishedBeforeCargoShown(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData, CargoAPI cargo) {}

	@Override
	public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, 
			MarketCMD.TempData actionData, Industry industry) {}

	@Override
	public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {}

	@Override
	public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, 
			MarketAPI market, MarketCMD.TempData actionData) {
		reportSatBomb(market, actionData);
	}
	
	@Override
	public void reportNPCGenericRaid(MarketAPI market, MarketCMD.TempData actionData) {}

	@Override
	public void reportNPCIndustryRaid(MarketAPI market, MarketCMD.TempData actionData, Industry industry) {}

	@Override
	public void reportNPCTacticalBombardment(MarketAPI market, MarketCMD.TempData actionData) {}

	@Override
	public void reportNPCSaturationBombardment(MarketAPI market, MarketCMD.TempData actionData) 
	{
		reportSatBomb(market, actionData);
	}
	
	public static class QueuedIndustry {
		
		public String industry;
		public QueueType type;
		
		public QueuedIndustry(String industry, QueueType type) {
			this.industry = industry;
			this.type = type;
		}
		
		public static enum QueueType { NEW, UPGRADE }
	}
	
	public static final TooltipMakerAPI.TooltipCreator AUTONOMOUS_INCOME_NODE_TOOLTIP = new AINTT();

	public static class AINTT implements TooltipMakerAPI.TooltipCreator {
		public boolean isTooltipExpandable(Object tooltipParam) {
			return false;
		}
		public float getTooltipWidth(Object tooltipParam) {
			return 450;
		}
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
			tooltip.addPara(getString("reportAutonomousTaxTooltip"), 0, Misc.getHighlightColor(),
					String.format("%.0f", Global.getSettings().getFloat("nex_autonomousIncomeMult") * 100) + "%");
		}
	}
}
