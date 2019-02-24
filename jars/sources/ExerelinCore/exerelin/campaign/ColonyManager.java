package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI.MessageClickAction;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.population.CoreImmigrationPluginImpl;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import static exerelin.campaign.SectorManager.sectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.InvasionListener;
import exerelin.utilities.StringHelper;
import exerelin.world.ExerelinProcGen;
import exerelin.world.ExerelinProcGen.ProcGenEntity;
import exerelin.world.NexMarketBuilder;
import exerelin.world.industry.IndustryClassGen;
import java.awt.Color;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Handles assorted colony-related functions.
 */
public class ColonyManager extends BaseCampaignEventListener implements EveryFrameScript,
		EconomyTickListener, InvasionListener
{
	
	public static final String PERSISTENT_KEY = "nex_colonyManager";
	public static final Set<String> NEEDED_OFFICIALS = new HashSet<>(Arrays.asList(
			Ranks.POST_ADMINISTRATOR, Ranks.POST_BASE_COMMANDER, 
			Ranks.POST_STATION_COMMANDER, Ranks.POST_PORTMASTER
	));
	public static final int MAX_STATION_SIZE = 6;
	public static final int MAX_NPC_COLONY_SIZE = 0;
	public static final int MIN_CYCLE_FOR_NPC_GROWTH = 209;
	
	public static final int[] BONUS_ADMIN_LEVELS = new int[] {
		0, 10, 25, 50, 80, 120
	};
	
	protected int bonusAdminLevel = 0;
	
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
				if (market.getPlanetEntity() == null && market.getSize() >= MAX_STATION_SIZE) 
				{
					//market.setImmigrationClosed(true);
				}
				playerFactionSize += market.getSize();
			}
			else 
			{
				if (allowGrowth && !market.isHidden() && market.getSize() < MAX_NPC_COLONY_SIZE
						&& Misc.getMarketSizeProgress(market) >= 1) {
					CoreImmigrationPluginImpl.increaseMarketSize(market);
					// intel: copied from CoreImmigrationPluginImpl
					MessageIntel intel = new MessageIntel("Colony Growth - " + market.getName(), Misc.getBasePlayerColor());
					intel.addLine(BaseIntelPlugin.BULLET + "Size increased to %s",
							Misc.getTextColor(), 
							new String[] {"" + (int)Math.round(market.getSize())},
							Misc.getHighlightColor());
					intel.setIcon(market.getFaction().getCrest());
					intel.setSound(BaseIntelPlugin.getSoundStandardPosting());
					Global.getSector().getCampaignUI().addMessage(intel, MessageClickAction.NOTHING);
				}
				
				updateFreePortSetting(market);
			}
			
		}
		updatePlayerBonusAdmins(playerFactionSize);
	}
		
	protected void updatePlayerBonusAdmins(int playerFactionSize) {	
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
			Global.getLogger(this.getClass()).info("Reached bonus level " + index + " from market size " + playerFactionSize);
			
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
		boolean wantFreePort = true;
		if (!sectorManager.corvusMode)
		{
			wantFreePort = newOwnerConfig.freeMarket;
		}
		else
		{
			wantFreePort = market.getMemoryWithoutUpdate().getBoolean("$startingFreeMarket")
					|| (newOwnerConfig.pirateFaction && newOwnerConfig.freeMarket);
		}
		// needs forcible toggle because if it's already enabled it won't do anything
		// also check if free port condition already exists so we don't add it twice
		if (wantFreePort == true && !market.hasCondition(Conditions.FREE_PORT)) {
			market.setFreePort(wantFreePort);
			if (!market.hasCondition(Conditions.FREE_PORT))
				market.addCondition(Conditions.FREE_PORT);
		}
		else
			market.removeCondition(Conditions.FREE_PORT);
			//market.setFreePort(false);
		
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
	public void advance(float amount) {}

	@Override
	public void reportEconomyTick(int iterIndex) {
		updateMarkets();
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
		if (oldOwner.isPlayerFaction())
			buildIndustries(market);
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
		// if the spaceport is gone we can guess that player strip-mined the market
		boolean productive = (!market.hasIndustry(Industries.SPACEPORT) && !market.hasIndustry(Industries.MEGAPORT));
		boolean military = shouldBuildMilitary(market);
		buildIndustries(market, military, productive);
	}
	
	public static boolean shouldBuildMilitary(MarketAPI market) {
		if (!market.hasIndustry(Industries.PATROLHQ) 
				&& !market.hasIndustry(Industries.MILITARYBASE) 
				&& !market.hasIndustry(Industries.HIGHCOMMAND))
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
	
	public static ColonyManager getManager()
	{
		return (ColonyManager)Global.getSector().getPersistentData().get(PERSISTENT_KEY);
	}
}
