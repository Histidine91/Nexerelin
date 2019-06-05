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
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketImmigrationModifier;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.population.PopulationComposition;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ColonyManager.QueuedIndustry.QueueType;
import static exerelin.campaign.SectorManager.sectorManager;
import exerelin.campaign.colony.ColonyTargetValuator;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.intel.colony.ColonyExpeditionIntel;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinProcGen;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.IndustryClassGen;
import java.awt.Color;
import java.util.ArrayList;
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
	public static final int MIN_CYCLE_FOR_NPC_GROWTH = 209;
	
	public static final int[] BONUS_ADMIN_LEVELS = new int[] {
		0, 10, 25, 50, 80, 120
	};
	
	protected Map<MarketAPI, LinkedList<QueuedIndustry>> npcConstructionQueues = new HashMap<>();
	protected int bonusAdminLevel = 0;
	protected float colonyExpeditionProgress;
	protected int numDeadExpeditions;
	
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
				if (allowGrowth && !market.isHidden() && Misc.getMarketSizeProgress(market) >= 1) 
				{
					int maxSize = ExerelinConfig.maxNPCColonySize;
					if (market.getMemoryWithoutUpdate().contains(ColonyExpeditionIntel.MEMORY_KEY_COLONY))
						maxSize = ExerelinConfig.maxNPCNewColonySize;
					if (market.getMemoryWithoutUpdate().contains(MEMORY_KEY_GROWTH_LIMIT))
						maxSize = (int)market.getMemoryWithoutUpdate().getLong(MEMORY_KEY_GROWTH_LIMIT);
					
					if (market.getSize() < maxSize) {
						CoreImmigrationPluginImpl.increaseMarketSize(market);
						// intel: copied from CoreImmigrationPluginImpl
						MessageIntel intel = new MessageIntel(StringHelper.getString("nex_colonies", "intelGrowthTitle") 
								+ " - " + market.getName(), Misc.getBasePlayerColor());
						intel.addLine(BaseIntelPlugin.BULLET + StringHelper.getString("nex_colonies", "intelGrowthBullet"),
								Misc.getTextColor(), 
								new String[] {"" + (int)Math.round(market.getSize())},
								Misc.getHighlightColor());
						intel.setIcon(market.getFaction().getCrest());
						intel.setSound(BaseIntelPlugin.getSoundStandardPosting());
						Global.getSector().getCampaignUI().addMessage(intel, MessageClickAction.NOTHING);
					}
				}
				updateFreePortSetting(market);
			}
		}
		updatePlayerBonusAdmins(playerFactionSize);
	}
	
	public void setNPCGrowthRate(MarketAPI market) {
		boolean want = !market.getFaction().isPlayerFaction();
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
		incoming.getWeight().modifyMult("nex_colonyManager_npcGrowth", 0.5f, "NPC market");
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
		boolean isFreePort = market.isFreePort();
		boolean wantFreePort;
		if (!sectorManager.corvusMode)
		{
			wantFreePort = newOwnerConfig.freeMarket;
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
	
	protected String getString(String id) {
		return getString(id, false);
	}
	
	protected String getString(String id, boolean ucFirst) {
		return StringHelper.getString("nex_colonies", id, ucFirst);
	}
	
	// this exists because else it'd be a leak in constructor
	public void init() {
		Global.getSector().getPersistentData().put(PERSISTENT_KEY, this);
		Global.getSector().addScript(this);
		Global.getSector().getListenerManager().addListener(this);
	}
	
	public void incrementDeadColonyExpeditions() {
		numDeadExpeditions++;
	}
	
	protected String pickFactionToColonize() {
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			if (!ExerelinConfig.getExerelinFactionConfig(factionId).createsColonies)
				continue;
			picker.add(factionId);
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
	
	// debug command: runcode exerelin.campaign.ColonyManager.getManager().spawnColonyExpedition();
	/**
	 * Spawns a colony expedition for a random faction to a semi-randomly-selected suitable target.
	 * @return
	 */
	public ColonyExpeditionIntel spawnColonyExpedition() {
		String factionId = pickFactionToColonize();
		if (factionId == null) return null;
		FactionAPI faction = Global.getSector().getFaction(factionId);
		
		MarketAPI source = pickColonyExpeditionSource(factionId);
		if (source == null) return null;
		SectorEntityToken anchor;
		if (source.getContainingLocation().isHyperspace()) anchor = source.getPrimaryEntity();
		else anchor = source.getStarSystem().getHyperspaceAnchor();
		
		ColonyTargetValuator valuator = loadColonyTargetValuator(factionId);
		
		WeightedRandomPicker<PlanetAPI> planetPicker = new WeightedRandomPicker<>();
		float maxDist = valuator.getMaxDistanceLY(faction);
		float minScore = valuator.getMinScore(faction);
		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			if (!valuator.prefilterSystem(system, faction))
				continue;
			
			float dist = Misc.getDistanceLY(system.getHyperspaceAnchor(), anchor);
			if (dist > maxDist) continue;
			
			for (PlanetAPI planet : system.getPlanets()) {
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
		if (target == null) return null;
		float fp = 20 + 15 * numDeadExpeditions;
		float organizeTime = InvasionFleetManager.getOrganizeTime(fp + 30) + 30;
		
		ColonyExpeditionIntel intel = new ColonyExpeditionIntel(faction, source, target.getMarket(), fp, organizeTime);
		intel.init();
		return intel;
	}
	
	// add admin to player faction if needed
	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {
		if (market.getFaction().isPlayerFaction())
		{
			if (hasBaseOfficial(market)) return;
			ExerelinUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, Ranks.CITIZEN, Ranks.POST_ADMINISTRATOR, true);
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
		float days = Global.getSector().getClock().convertToDays(amount);
		colonyExpeditionProgress += days;
		float interval = ExerelinConfig.colonyExpeditionInterval;
		if (days > interval) {
			ColonyExpeditionIntel intel = spawnColonyExpedition();
			if (intel != null) {
				colonyExpeditionProgress = MathUtils.getRandomNumberInRange(-interval * 0.1f, interval * 0.1f);
			}
			else {	// failed to spawn, try again in 15 days
				colonyExpeditionProgress -= 15;
			}
		}
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		updateMarkets();
		processNPCConstruction();
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
		if (oldOwner.isPlayerFaction() || existing != null) {
			buildIndustries(market);
			processNPCConstruction(market);
		}
		
		// reassign admins on market capture
		reassignAdminIfNeeded(market, oldOwner, newOwner);
		setNPCGrowthRate(market);
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
	 * Adds industries to a market. Intended to semi-counter market stripping
	 * by the player, and will be used later for NPC colonies.
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
	 * Adds industries to a market. Intended to semi-counter market stripping
	 * by the player, and will be used later for NPC colonies. 
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
		boolean productive = (!market.hasIndustry(Industries.SPACEPORT) && !market.hasIndustry(Industries.MEGAPORT));
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
	 */
	public static void reassignAdminIfNeeded(MarketAPI market, FactionAPI oldOwner, FactionAPI newOwner) {
		
		// if player has captured a market, assing player as admin
		if (newOwner.isPlayerFaction()) {
			market.setAdmin(Global.getSector().getPlayerPerson());
			return;
		}
		
		// do nothing if admin is AI core
		if (market.getAdmin().isAICore()) {
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
}
