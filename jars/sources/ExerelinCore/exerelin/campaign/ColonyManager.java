package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.econ.MonthlyReport;
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ColonyManager.QueuedIndustry.QueueType;
import exerelin.campaign.colony.ColonyTargetValuator;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.utilities.ExerelinConfig;
import static exerelin.utilities.ExerelinConfig.hardModeColonyGrowthMult;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinProcGen;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.IndustryClassGen;
import java.awt.Color;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

/**
 * Handles assorted colony-related functions.
 */
public class ColonyManager extends BaseCampaignEventListener implements EveryFrameScript,
		EconomyTickListener, InvasionListener, MarketImmigrationModifier
{
	public static Logger log = Global.getLogger(ColonyManager.class);
	
	public static final String PERSISTENT_KEY = "nex_colonyManager";
	public static final String MEMORY_KEY_GROWTH_LIMIT = "$nex_colony_growth_limit";
	public static final Set<String> NEEDED_OFFICIALS = new HashSet<>(Arrays.asList(
			Ranks.POST_ADMINISTRATOR, Ranks.POST_BASE_COMMANDER, 
			Ranks.POST_STATION_COMMANDER, Ranks.POST_PORTMASTER
	));
	public static final int MAX_STATION_SIZE = 6;
	public static final int MIN_CYCLE_FOR_NPC_GROWTH = 207;
	public static final int MIN_CYCLE_FOR_EXPEDITIONS = 207;
	public static final float MAX_EXPEDITION_FP = 300;
	
	public static final int[] BONUS_ADMIN_LEVELS = new int[] {
		0, 10, 25, 50, 80, 120, 200, 300
	};
	
	protected Map<MarketAPI, LinkedList<QueuedIndustry>> npcConstructionQueues = new HashMap<>();
	protected int bonusAdminLevel = 0;
	protected float colonyExpeditionProgress;
	protected int numDeadExpeditions;
	protected int numAvertedExpeditions;
	protected int numColonies;
	protected int currIter;
	
	public ColonyManager() {
		super(true);
	}
	
	protected boolean hasBaseOfficial(MarketAPI market)
	{
		for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
		{
			if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
			PersonAPI person = (PersonAPI)dir.getEntryData();
			if (NEEDED_OFFICIALS.contains(person.getPostId()))
				return true;
		}
		return false;
	}
	
	/**
	 * Clamps player station population, increments NPC population size, and adds bonus admins if needed.
	 */
	protected void updateMarkets() {
		numColonies = 0;
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		int playerFactionSize = 0;
		boolean allowGrowth = Global.getSector().getClock().getCycle() >= MIN_CYCLE_FOR_NPC_GROWTH;
		for (MarketAPI market : markets) 
		{
			if (market.getFaction().isPlayerFaction()) 
			{
				playerFactionSize += market.getSize();
			}
			else
			{
				updateFreePortSetting(market);
			}
			
			// handle market growth
			if (!market.isPlayerOwned())
			{
				if (allowGrowth && !market.isHidden() && Misc.getMarketSizeProgress(market) >= 1) 
				{
					int maxSize;
					if (market.getFaction().isPlayerFaction())
						maxSize = 10;
					else if (market.getMemoryWithoutUpdate().contains(MEMORY_KEY_GROWTH_LIMIT))
						maxSize = (int)market.getMemoryWithoutUpdate().getLong(MEMORY_KEY_GROWTH_LIMIT);
					else if (market.getMemoryWithoutUpdate().contains(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
						maxSize = ExerelinConfig.maxNPCNewColonySize;
					else
						maxSize = ExerelinConfig.maxNPCColonySize;
					
					if (market.getSize() < maxSize) {
						CoreImmigrationPluginImpl.increaseMarketSize(market);
						// intel: copied from CoreImmigrationPluginImpl
						MessageIntel intel = new MessageIntel(getString("intelGrowthTitle", false) 
								+ " - " + market.getName(), Misc.getBasePlayerColor());
						intel.addLine(BaseIntelPlugin.BULLET + getString("intelGrowthBullet", false),
								Misc.getTextColor(), 
								new String[] {"" + (int)Math.round(market.getSize())},
								Misc.getHighlightColor());
						intel.setIcon(market.getFaction().getCrest());
						intel.setSound(BaseIntelPlugin.getSoundStandardPosting());
						Global.getSector().getCampaignUI().addMessage(intel, MessageClickAction.NOTHING);
						
						buildIndustries(market);
					}
				}
			}
			
			if (market.getMemoryWithoutUpdate().getBoolean(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
				numColonies += 1;
		}
		updatePlayerBonusAdmins(playerFactionSize);
	}
	
	public void setGrowthRate(MarketAPI market) {
		boolean want = !market.getFaction().isPlayerFaction() || SectorManager.getHardMode();
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
		if (market.getFaction().isPlayerFaction()) {
			incoming.getWeight().modifyMult("nex_colonyManager_hardModeGrowth", ExerelinConfig.hardModeColonyGrowthMult, 
					getString("hardModeGrowthMultDesc", false));
		}
		else {
			incoming.getWeight().modifyMult("nex_colonyManager_npcGrowth", Global.getSettings().getFloat("nex_npcColonyGrowthMult"), 
					getString("npcGrowthMultDesc", false));
		}
		
	}
	
	public void updatePlayerBonusAdmins() {
		updatePlayerBonusAdmins(ExerelinUtilsFaction.getFactionMarketSizeSum(Factions.PLAYER));
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
		if (market.getFaction().isPlayerFaction()) return;	// let player decide
		
		ExerelinFactionConfig newOwnerConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		// keep the cond check; motherfuckers can't be trusted to have the right settting
		boolean isFreePort = market.isFreePort() || market.hasCondition(Conditions.FREE_PORT);
		boolean wantFreePort;
		if (!SectorManager.getCorvusMode())
		{
			wantFreePort = newOwnerConfig.freeMarket || market.getId().equals("nex_prismFreeport");
		}
		else
		{
			wantFreePort = market.getMemoryWithoutUpdate().getBoolean("$startingFreeMarket")
					|| (newOwnerConfig.pirateFaction && newOwnerConfig.freeMarket);
		}
		
		if (isFreePort != wantFreePort) {
			market.setFreePort(wantFreePort);
			ExerelinUtilsMarket.setTariffs(market);
		}
	}
	
	public static void updateIncome() {
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
		{
			updateIncome(market);
		}
	}
	
	public static void updateIncome(MarketAPI market)
	{
		if (market.getFaction().isPlayerFaction() && SectorManager.getHardMode())
		{
			market.getIncomeMult().modifyMult("nex_hardMode", ExerelinConfig.hardModeColonyIncomeMult, 
						getString("hardModeIncomeMultDesc"));
		}
		else {
			market.getIncomeMult().unmodify("nex_hardMode");
		}
	}
	
	// see CoreScript
	/**
	 * Refunds storage fee for markets that belong to player faction but are not player-controlled.
	 */
	protected void processStorageRebate() 
	{
		float numIter = Global.getSettings().getFloat("economyIterPerMonth");
		float f = 1f / numIter;
		
		MonthlyReport report = SharedData.getData().getCurrentReport();
		
		float storageFraction = Global.getSettings().getFloat("storageFreeFraction");
		
		float rebate = 0;
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) 
		{			
			if (!market.isPlayerOwned() && market.getFaction().isPlayerFaction() && Misc.playerHasStorageAccess(market)) {
				float vc = Misc.getStorageCargoValue(market);
				float vs = Misc.getStorageShipValue(market);
				
				float fc = (int) (vc * storageFraction);
				float fs = (int) (vs * storageFraction);
				if (fc > 0 || fs > 0) {
					rebate += (fc + fs) * f;
				}
			}
		}
		log.info("Applying storage rebate: " + rebate);
		
		if (rebate <= 0) return;
		
		FDNode storageNode = report.getNode(MonthlyReport.STORAGE);
		FDNode node = report.getNode(storageNode, "nex_node_id_storageRebate");
		node.name = StringHelper.getString("exerelin_markets", "storageRebate");
		node.custom = "nex_node_id_storageRebate";
		node.icon = Global.getSettings().getSpriteName("income_report", "generic_income");
		node.income += rebate;
		node.tooltipCreator = STORAGE_REBATE_NODE_TOOLTIP;
		
	}
	
	public static boolean isBuildingAnything(MarketAPI market) {
		for (Industry ind : market.getIndustries()) {
			if (ind.isBuilding() && !ind.isUpgrading())
				return true;
		}
		return false;
	}
	
	protected void processNPCConstruction() {
		Iterator<MarketAPI> iter = npcConstructionQueues.keySet().iterator();
		while (iter.hasNext()) {
			MarketAPI market = iter.next();
			processNPCConstruction(market);
		}
	}
	
	protected void processNPCConstruction(MarketAPI market) {
		if (isBuildingAnything(market))
			return;
		
		log.info("Processing NPC construction queue for " + market.getName());
		LinkedList<QueuedIndustry> queue = npcConstructionQueues.get(market);
		if (queue == null || queue.isEmpty())
			return;
		
		LinkedList<QueuedIndustry> queueCopy = new LinkedList<>(queue);
		for (QueuedIndustry item : queueCopy) {
			log.info("\tChecking industry queue: " + item.industry + ", " + item.type.toString());
			
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
					if (ind.canUpgrade()) {
						market.getIndustry(item.industry).startUpgrading();
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
	
	public void queueIndustry(MarketAPI market, String industry, QueueType type) {
		if (!npcConstructionQueues.containsKey(market))
			npcConstructionQueues.put(market, new LinkedList<QueuedIndustry>());
		
		LinkedList<QueuedIndustry> queue = npcConstructionQueues.get(market);
		queue.add(new QueuedIndustry(industry, type));
		log.info("Queued industry: " + industry + ", " + type.toString());
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
	
	protected String pickFactionToColonize() {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			float chance = ExerelinConfig.getExerelinFactionConfig(factionId).colonyExpeditionChance;
			if (chance <= 0)
				continue;
			picker.add(factionId, chance);
		}
		return picker.pick();
	}
	
	protected MarketAPI pickColonyExpeditionSource(String factionId) {
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		for (MarketAPI market : ExerelinUtilsFaction.getFactionMarkets(factionId)) {
			if (!market.hasSpaceport()) continue;
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
	
	public static <T extends ColonyTargetValuator> T loadColonyTargetValuator(String factionId)
	{
		ColonyTargetValuator valuator = null;
		String className = ExerelinConfig.getExerelinFactionConfig(factionId).colonyTargetValuator;
		
		try {
			ClassLoader loader = Global.getSettings().getScriptClassLoader();
			Class<?> clazz = loader.loadClass(className);
			valuator = (ColonyTargetValuator)clazz.newInstance();
		} catch (ClassNotFoundException | IllegalAccessException | InstantiationException ex) {
			Global.getLogger(IndustryClassGen.class).error("Failed to load colony target valuator " + className, ex);
		}

		return (T)valuator;
	}
	
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
	 * @return
	 */
	public ColonyExpeditionIntel spawnColonyExpedition() {
		log.info("Attempting to spawn colony expedition");
		String factionId = pickFactionToColonize();
		if (factionId == null) {
			log.info("Failed to pick faction for expedition");
			return null;
		}
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		MarketAPI source = pickColonyExpeditionSource(factionId);
		if (source == null) {
			log.info("Failed to pick source market for expedition");
			return null;
		}
		SectorEntityToken anchor;
		if (source.getContainingLocation().isHyperspace()) anchor = source.getPrimaryEntity();
		else anchor = source.getStarSystem().getHyperspaceAnchor();
		
		Set<PlanetAPI> existingTargets = getExistingColonyTargets();
		
		ColonyTargetValuator valuator = loadColonyTargetValuator(factionId);
		
		WeightedRandomPicker<PlanetAPI> planetPicker = new WeightedRandomPicker<>();
		float maxDist = valuator.getMaxDistanceLY(faction);
		float minScore = valuator.getMinScore(faction);
		for (StarSystemAPI system : Global.getSector().getStarSystems()) 
		{
			if (!valuator.prefilterSystem(system, faction))
				continue;
			
			float dist = Misc.getDistanceLY(system.getHyperspaceAnchor(), anchor);
			if (dist > maxDist) continue;
			
			for (PlanetAPI planet : system.getPlanets()) 
			{
				if (existingTargets.contains(planet)) continue;
				
				MarketAPI market = planet.getMarket();
				if (market == null || market.isInEconomy()) continue;
				if (!valuator.prefilterMarket(planet.getMarket(), faction)) {
					continue;
				}
				
				float score = valuator.evaluatePlanet(planet.getMarket(), dist, faction);
				if (score < minScore) continue;
				
				planetPicker.add(planet, score);
			}
		}
		PlanetAPI target = planetPicker.pick();
		if (target == null) {
			log.info("Failed to pick target for expedition");
			return null;
		}
		float fp = 30 + 15 * numDeadExpeditions;
		if (fp > MAX_EXPEDITION_FP) fp = MAX_EXPEDITION_FP;
		float organizeTime = InvasionFleetManager.getOrganizeTime(fp + 30) + 30;
		
		ColonyExpeditionIntel intel = new ColonyExpeditionIntel(faction, source, target.getMarket(), fp, organizeTime);
		intel.init();
		return intel;
	}
	
	// add admin to player market if needed
	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {
		if (market.getFaction().isPlayerFaction() && !market.isHidden())
		{
			if (hasBaseOfficial(market)) return;
			ExerelinUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, Ranks.CITIZEN, Ranks.POST_ADMINISTRATOR, true);
		}
		// retroactive fix for admins not having their faction changed
		for (CommDirectoryEntryAPI entry : market.getCommDirectory().getEntriesCopy()) {
			PersonAPI person = (PersonAPI)entry.getEntryData();
			if (person.getFaction() != market.getFaction() && 
					SectorManager.POSTS_TO_CHANGE_ON_CAPTURE.contains(person.getPostId()))
			{
				log.info("Forcing person " + person.getName().getFullName() 
						+ " (post " + person.getPost() + ") to correct faction");
				person.setFaction(market.getFaction().getId());
			}
		}
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
		if (Global.getSector().getClock().getCycle() < MIN_CYCLE_FOR_EXPEDITIONS)
			return;
		
		float days = Global.getSector().getClock().convertToDays(amount);
		colonyExpeditionProgress += days;
		float interval = ExerelinConfig.colonyExpeditionInterval;
		if (colonyExpeditionProgress > interval) {
			ColonyExpeditionIntel intel = spawnColonyExpedition();
			if (intel != null) {
				colonyExpeditionProgress = MathUtils.getRandomNumberInRange(-interval * 0.1f, interval * 0.1f);
				colonyExpeditionProgress -= numColonies * Global.getSettings().getFloat("nex_expeditionDelayPerExistingColony");
			}
			else {	// failed to spawn, try again in 10 days
				colonyExpeditionProgress -= Math.min(ExerelinConfig.colonyExpeditionInterval/2, 10);
			}
		}
	}
	
	public float getColonyExpeditionProgress() {
		return colonyExpeditionProgress;
	}
	
	public int getNumColonies() {
		return numColonies;
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		
		// workaround: reportEconomyTick is called twice,
		// since we've added colony manager as a script in sector, and also as a listener
		// so don't do anything the second time
		if (currIter == iterIndex)
			return;
		
		updateMarkets();
		processNPCConstruction();
		processStorageRebate();
		
		currIter = iterIndex;
	}

	@Override
	public void reportEconomyMonthEnd() {}

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
			boolean isCapture, List<String> factionsToNotify, float repChangeStrength) {
		LinkedList<QueuedIndustry> existing = npcConstructionQueues.remove(market);
		if (!newOwner.isPlayerFaction()) {
			if (oldOwner.isPlayerFaction() || existing != null) {
				buildIndustries(market);
				processNPCConstruction(market);
			}
		}
		
		// reassign admins on market capture
		reassignAdminIfNeeded(market, oldOwner, newOwner);
		setGrowthRate(market);
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
		
		if (military)
			NexMarketBuilder.addMilitaryStructures(entity, false, random);
		if (productive)
			NexMarketBuilder.addIndustriesToMarket(entity, false);
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
	 * Sets a person on the market's comm board to be admin, if appropriate.
	 * @param market
	 * @param oldOwner
	 * @param newOwner
	 */
	public static void reassignAdminIfNeeded(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner) {
		
		// do nothing if admin is AI core (unless it's a faction ruler, e.g. II)
		if (market.getAdmin().isAICore() && !Ranks.POST_FACTION_LEADER.equals(market.getAdmin().getPostId())) 
		{
			return;
		}
		
		// if player has captured a market, assign player as admin
		if (newOwner.isPlayerFaction() && market.isPlayerOwned()) {
			market.setAdmin(Global.getSector().getPlayerPerson());
			return;
		}
		
		// if market was player-controlled or administered by a faction leader, pick a new admin from comm board
		boolean reassign = oldOwner.isPlayerFaction() || Ranks.POST_FACTION_LEADER.equals(market.getAdmin().getPostId())
				|| hasFactionLeader(market);
		if (!reassign)
			return;
		
		PersonAPI admin = getBestAdmin(market);
		market.setAdmin(admin);
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
	
	/**
	 * Gets the person on the market's comm board who should be admin (based on their post).
	 * @param market
	 * @return
	 */
	public static PersonAPI getBestAdmin(MarketAPI market) {
		PersonAPI best = null;
		int bestScore = 0;
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
		
		if (postId.equals(Ranks.POST_FACTION_LEADER) && person.getFaction() == market.getFaction())
			return 3;
		else if (postId.equals(Ranks.POST_STATION_COMMANDER) && market.getPrimaryEntity().hasTag(Tags.STATION))
			return 2;
		else if (postId.equals(Ranks.POST_ADMINISTRATOR))
			return 1;
		
		return -1;
	}
	
	public static ColonyManager getManager()
	{
		return (ColonyManager)Global.getSector().getPersistentData().get(PERSISTENT_KEY);
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
	
	public static final TooltipMakerAPI.TooltipCreator STORAGE_REBATE_NODE_TOOLTIP = new TooltipMakerAPI.TooltipCreator() {
		public boolean isTooltipExpandable(Object tooltipParam) {
			return false;
		}
		public float getTooltipWidth(Object tooltipParam) {
			return 450;
		}
		public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
			tooltip.addPara(StringHelper.getString("exerelin_markets", "storageRebateTooltip"), 0);
		}
	};
}
