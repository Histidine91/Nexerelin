package exerelin.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.AnalyzeEntityIntelCreator;
import com.fs.starfarer.api.impl.campaign.intel.FactionHostilityManager;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager.GenericMissionCreator;
import com.fs.starfarer.api.impl.campaign.intel.ProcurementMissionCreator;
import com.fs.starfarer.api.impl.campaign.intel.SurveyPlanetIntelCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionManager;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager;
import com.fs.starfarer.api.util.Misc;
import com.thoughtworks.xstream.XStream;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.FieldOptionsScreenScript;
import exerelin.campaign.NexEventProbabilityManager;
import exerelin.campaign.PlayerFactionSetupNag;
import exerelin.campaign.StartSetupPostTimePass;
import exerelin.campaign.ReinitScreenScript;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.battle.EncounterLootHandler;
import exerelin.utilities.*;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.MiningFleetManagerV2;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.intel.ConquestMissionManager;
import exerelin.campaign.intel.FactionBountyManager;
import exerelin.campaign.intel.Nex_HegemonyInspectionManager;
import exerelin.campaign.intel.Nex_PunitiveExpeditionManager;
import exerelin.campaign.intel.agents.AgentBarEventCreator;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.missions.Nex_ProcurementMissionCreator;
import exerelin.campaign.submarkets.Nex_LocalResourcesSubmarketPlugin;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.versionchecker.VCModPluginCustom;
import exerelin.world.ExerelinProcGen;
import exerelin.world.LandmarkGenerator;
import exerelin.world.SSP_AsteroidTracker;
import exerelin.world.scenarios.ScenarioManager;
import java.util.ArrayList;
import org.apache.log4j.Logger;

public class ExerelinModPlugin extends BaseModPlugin
{
    // call order: onNewGame -> onNewGameAfterProcGen -> onNewGameAfterEconomyLoad -> onEnabled -> onNewGameAfterTimePass -> onGameLoad
    public static final boolean HAVE_SWP = Global.getSettings().getModManager().isModEnabled("swp");
    public static final boolean HAVE_DYNASECTOR = Global.getSettings().getModManager().isModEnabled("dynasector");
    public static final boolean HAVE_UNDERWORLD = Global.getSettings().getModManager().isModEnabled("underworld");
    //public static final boolean HAVE_STELLAR_INDUSTRIALIST = Global.getSettings().getModManager().isModEnabled("stellar_industrialist");
    public static final boolean HAVE_VERSION_CHECKER = Global.getSettings().getModManager().isModEnabled("lw_version_checker");
    
    public static Logger log = Global.getLogger(ExerelinModPlugin.class);
    protected static boolean isNewGame = false;
    
    protected <T extends EveryFrameScript> boolean replaceScript(SectorAPI sector, Class toRemove, T toAdd)
    {
        boolean removedAny = false;
        for (EveryFrameScript script : sector.getScripts())
        {
            if (toRemove.isInstance(script))
            {
                if (toAdd != null && toAdd.getClass().isInstance(script))
                    continue;
                
                log.info("Removing EveryFrameScript " + script.toString() + " | " + toRemove.getCanonicalName());
                
                sector.removeScript(script);
                if (script instanceof CampaignEventListener)
                    sector.removeListener((CampaignEventListener)script);
                
                if (toAdd != null)
                    sector.addScript(toAdd);
                
                removedAny = true;
                break;
            }
        }
        return removedAny;
    }
	
	protected <T extends GenericMissionCreator> boolean replaceMissionCreator(Class toRemove, T toAdd) {
		boolean removedAny = false;
		GenericMissionManager manager = GenericMissionManager.getInstance();
		if (!manager.hasMissionCreator(toRemove))
			return false;
        for (GenericMissionCreator creator : new ArrayList<>(manager.getCreators()))
        {
            if (toRemove.isInstance(creator))
            {
                if (toAdd != null && toAdd.getClass().isInstance(creator))
                    continue;
                
                log.info("Removing mission creator " + creator.toString() + " | " + toRemove.getCanonicalName());
                
                manager.getCreators().remove(creator);
                
                if (toAdd != null)
                    manager.addMissionCreator(toAdd);
                
                removedAny = true;
                break;
            }
        }
        return removedAny;
	}
    
    public static void replaceSubmarket(MarketAPI market, String submarketId) {
        if (!market.hasSubmarket(submarketId)) return;
        CargoAPI current = market.getSubmarket(submarketId).getCargo();
        
        market.removeSubmarket(submarketId);
        market.addSubmarket(submarketId);
        CargoAPI newCargo = market.getSubmarket(submarketId).getCargo();
        newCargo.clear();
        newCargo.addAll(current);
        newCargo.sort();
    }
    
    protected void applyToExistingSave()
    {
        log.info("Applying Nexerelin to existing game");
        
        SectorAPI sector = Global.getSector();
        sector.addScript(SectorManager.create());
        sector.addScript(DiplomacyManager.create());
        sector.addScript(InvasionFleetManager.create());
        //sector.addScript(ResponseFleetManager.create());
        sector.addScript(MiningFleetManagerV2.create());
        sector.addScript(CovertOpsManager.create());
        sector.addScript(AllianceManager.create());
        new ColonyManager().init();
        new RevengeanceManager().init();
        
        // debugging
        //im.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.invasionGracePeriod);
        //am.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.allianceGracePeriod);
        
        // replace or remove relevant intel items
        replaceScript(sector, FactionHostilityManager.class, null);
        replaceScript(sector, HegemonyInspectionManager.class, new Nex_HegemonyInspectionManager());
        replaceScript(sector, PunitiveExpeditionManager.class, new Nex_PunitiveExpeditionManager());
		replaceMissionCreator(ProcurementMissionCreator.class, new Nex_ProcurementMissionCreator());
        //replaceMissionCreator(AnalyzeEntityIntelCreator.class, new Nex_AnalyzeEntityIntelCreator());
		//replaceMissionCreator(SurveyPlanetIntelCreator.class, new Nex_SurveyPlanetIntelCreator());
        
        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction()))
        {
            replaceSubmarket(market, Submarkets.LOCAL_RESOURCES);
            replaceSubmarket(market, Submarkets.SUBMARKET_OPEN);
            replaceSubmarket(market, Submarkets.GENERIC_MILITARY);
            replaceSubmarket(market, Submarkets.SUBMARKET_BLACK);
        }
        
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (!market.getMemoryWithoutUpdate().contains(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION))
                market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, market.getFactionId());
            market.getMemoryWithoutUpdate().set("$startingFreeMarket", market.hasCondition(Conditions.FREE_PORT) || market.isFreePort());
			ColonyManager.updateFreePortSetting(market);
        }
        
        StatsTracker.create();
        
        SectorManager.setCorvusMode(true);
        SectorManager.reinitLiveFactions();
        //sector.getFaction(Factions.PLAYER).setRelationship(ExerelinConstants.PLAYER_NPC_ID, 1);
        NexUtilsReputation.syncFactionRelationshipsToPlayer();
        
        // add Follow Me ability
        Global.getSector().getCharacterData().addAbility("exerelin_follow_me");
        AbilitySlotsAPI slots = Global.getSector().getUIData().getAbilitySlotsAPI();
        for (AbilitySlotAPI slot: slots.getCurrSlotsCopy())
        {
            if (slot.getAbilityId() == null || slot.getAbilityId().isEmpty())
            {
                slot.setAbilityId("exerelin_follow_me");
                break;
            }
        }
        
        sector.addTransientScript(new ReinitScreenScript());
    }
    
    protected void refreshTariffsAndGrowthRate()
    {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            ExerelinUtilsMarket.setTariffs(market);
            ColonyManager.getManager().setGrowthRate(market);
        }
    }
    
    protected void reverseCompatibility()
    {
		// fix free port overdose
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (!market.isFreePort()) continue;
            if (market.getFaction().isPlayerFaction()) continue;
            int numFreePorts = 0;
            for (MarketConditionAPI cond : market.getConditions()) {
                if (cond.getId().equals(Conditions.FREE_PORT)) {
                    numFreePorts++;
                }
            }
            if (numFreePorts > 1) {
                log.info("Fixing free ports for market " + market.getName() + ": " + numFreePorts);
                market.removeCondition(Conditions.FREE_PORT);
                market.addCondition(Conditions.FREE_PORT);
            }
        }
		
        for (MarketAPI market : Misc.getFactionMarkets(Global.getSector().getPlayerFaction()))
        {
            //if (market.isPlayerOwned()) continue;
            if (market.getSubmarket(Submarkets.LOCAL_RESOURCES).getPlugin() instanceof Nex_LocalResourcesSubmarketPlugin)
                continue;
            log.info("Replacing local storage on " + market.getName());
            replaceSubmarket(market, Submarkets.LOCAL_RESOURCES);
        }
        
        // retroactive fix for brawl mode defense fleets which are still hanging around
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(InvasionIntel.class)) {
            InvasionIntel invIntel = (InvasionIntel)intel;
            DefenseFleetIntel defIntel = invIntel.getBrawlDefIntel();
            if (defIntel != null && (defIntel.isEnding() || defIntel.isEnded())) {
                defIntel.giveReturnOrders();
            }
        }
    }
    
    protected void addBarEvents() {
        BarEventManager bar = BarEventManager.getInstance();
        if (bar != null && !bar.hasEventCreator(AgentBarEventCreator.class)) {
            bar.addEventCreator(new AgentBarEventCreator());
        }
    }
    
    protected void addScriptsAndEventsIfNeeded() {
        SectorAPI sector = Global.getSector();
        if (!sector.hasScript(ConquestMissionManager.class)) {
            sector.addScript(new ConquestMissionManager());
        }
        if (!sector.hasScript(NexEventProbabilityManager.class)) {
            //sector.addScript(new NexEventProbabilityManager());
        }
        if (!sector.hasScript(FactionBountyManager.class)) {
            sector.addScript(new FactionBountyManager());
        }
        
        /*
        addEventIfNeeded("exerelin_faction_salary");
        addEventIfNeeded("exerelin_followers_tax");
        if (ExerelinUtilsFaction.isExiInCorvus()) {
            addEventIfNeeded("exerelin_exigency_respawn");
        }
        if (RevengeanceManagerEvent.getOngoingEvent() == null) {
            sector.getEventManager().startEvent(null, "exerelin_revengeance_manager", null);
        }
        addEventIfNeeded("exerelin_slaves_sold");
        addEventIfNeeded("exerelin_warmonger");
        addEventIfNeeded("nex_rebellion_creator");
        //addEventIfNeeded("nex_trade_info");
        */
        
        addBarEvents();
    }
    
    @Override
    public void onGameLoad(boolean newGame) {
        log.info("Game load; " + SectorManager.isSectorManagerSaved());
        isNewGame = newGame;
        
        ScenarioManager.clearScenario();
        
        // legacy: assign the static variables referencing the singletons,
        // so the static methods in those classes can get them
        SectorManager.create();
        DiplomacyManager.create();
        InvasionFleetManager.create();
        ResponseFleetManager.create();
        AllianceManager.create();
        
        addScriptsAndEventsIfNeeded();
        
        reverseCompatibility();
        refreshTariffsAndGrowthRate();
        
        SectorAPI sector = Global.getSector();
        sector.registerPlugin(new ExerelinCampaignPlugin());
        sector.addTransientScript(new FieldOptionsScreenScript());
        sector.addTransientScript(new SSP_AsteroidTracker());
        //sector.removeScriptsOfClass(FactionHostilityManager.class);
        
        PrismMarket.clearSubmarketCache();
        
        ColonyManager.getManager().updatePlayerBonusAdmins();
        ColonyManager.updateIncome();
        
        if (!HAVE_VERSION_CHECKER)
            VCModPluginCustom.onGameLoad(newGame);
        
        if (!Misc.isPlayerFactionSetUp())
            sector.addTransientScript(new PlayerFactionSetupNag());
        
        sector.addTransientListener(new EncounterLootHandler());
    }
    
    @Override
    public void beforeGameSave()
    {
        log.info("Before game save");
    }
    
    @Override
    public void afterGameSave() {
        log.info("After game save");
    }
    
    @Override
    public void onApplicationLoad() throws Exception
    {
        starsectorVersionCheck();
        boolean bla = ExerelinConfig.countPiratesForVictory;	// just loading config class, not doing anything with it
        if (!HAVE_VERSION_CHECKER)
            VCModPluginCustom.onApplicationLoad();
        boolean hasLazyLib = Global.getSettings().getModManager().isModEnabled("lw_lazylib");
        if (!hasLazyLib) {
            throw new RuntimeException(StringHelper.getString("exerelin_misc", "errorLazyLib"));
        }
    }
    
    @Override
    public void onNewGame() {
        log.info("New game");
        isNewGame = true;
        //ExerelinSetupData.resetInstance();
        //ExerelinCheck.checkModCompatability();
        addScriptsAndEventsIfNeeded();
    }
    
    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        log.info("On enabled; " + wasEnabledBefore);
        if (!isNewGame && !wasEnabledBefore)
        {
            log.info(!isNewGame + ", " + !wasEnabledBefore);
            applyToExistingSave();
        }
    }
    
    @Override
    public void onNewGameAfterProcGen() {
        log.info("New game after proc gen; " + isNewGame);
        if (!SectorManager.getCorvusMode())
            new ExerelinProcGen().generate();
        
        ScenarioManager.afterProcGen(Global.getSector());
    }
    
    @Override
    public void onNewGameAfterEconomyLoad() {
        log.info("New game after economy load; " + isNewGame);
        
        ScenarioManager.afterEconomyLoad(Global.getSector());
        
        SectorManager.reinitLiveFactions();
        if (SectorManager.getCorvusMode())
        {
            DiplomacyManager.initFactionRelationships(false);    // the mod factions set their own relationships, so we have to re-randomize if needed afterwards
        }
        
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (!market.getMemoryWithoutUpdate().contains(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION))
                market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, market.getFactionId());
            market.getMemoryWithoutUpdate().set("$startingFreeMarket", market.hasCondition(Conditions.FREE_PORT) || market.isFreePort());
        }
        
        new LandmarkGenerator().generate(Global.getSector(), SectorManager.getCorvusMode());
        
        addBarEvents();
    }
    
    @Override
    public void onNewGameAfterTimePass() {
        log.info("New game after time pass; " + isNewGame);
        ScenarioManager.afterTimePass(Global.getSector());
        StartSetupPostTimePass.execute();
    }
    
    @Override
    public void configureXStream(XStream x) {
        XStreamConfig.configureXStream(x);
    }
    
    /**
     * Throw a RuntimeException if the current Starsector version is older than the one specified.
     * See http://fractalsoftworks.com/forum/index.php?topic=15894.0
     */
    public static void starsectorVersionCheck() {
        String message = "";

        try {
            ModSpecAPI spec = Global.getSettings().getModManager().getModSpec("nexerelin");
            Version wantedVersion = new Version(0, 9, 1, 8);    //new Version(spec.getGameVersion());
            Version installedVersion = new Version(Global.getSettings().getVersionString());

            if (installedVersion.isOlderThan(wantedVersion, false)) {
                message = String.format(StringHelper.getString("exerelin_misc", "errorStarsectorVersion"),
                        spec.getName(), wantedVersion, installedVersion);
            }
            /*
            else if (installedVersion.isNewerThan(wantedVersion, false)) {
                message = String.format("\r%s is not up to date for Starsector %s!" +
                                "\rAn updated version of THI may be available; if not, please wait for a release." +
                                "\rRequired Version: %s" +
                                "\rCurrent Version: %s",
                        spec.getName(), installedVersion, wantedVersion, installedVersion);
            }
            */
        } catch (Exception e) {
            log.error("Version comparison failed.", e);
        }

        if(!message.isEmpty()) throw new RuntimeException(message);
    }
    
    static class Version {
        public final int MAJOR, MINOR, PATCH, RC;

        public Version(String versionStr) {
            String[] temp = versionStr.replace("Starsector ", "").replace("a", "").split("-RC");

            RC = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;

            temp = temp[0].split("\\.");

            MAJOR = temp.length > 0 ? Integer.parseInt(temp[0]) : 0;
            MINOR = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;
            PATCH = temp.length > 2 ? Integer.parseInt(temp[2]) : 0;
        }
        
        public Version(int... version) {
            MAJOR = version[0];
            MINOR = version[1];
            PATCH = version[2];
            if (version.length > 3)
                RC = version[3];
            else
                RC = 0;
        }

        public boolean isOlderThan(Version other, boolean ignoreRC) {
            if(MAJOR < other.MAJOR) return true;
            if(MINOR < other.MINOR) return true;
            if(PATCH < other.PATCH) return true;
            if(!ignoreRC && RC < other.RC) return true;

            return false;
        }
        
        public boolean isNewerThan(Version other, boolean ignoreRC) {
            if(MAJOR > other.MAJOR) return true;
            if(MINOR > other.MINOR) return true;
            if(PATCH > other.PATCH) return true;
            if(!ignoreRC && RC > other.RC) return true;

            return false;
        }

        @Override
        public String toString() {
            return String.format("%d.%d.%d%s-RC%d", MAJOR, MINOR, PATCH, (MAJOR >= 1 ? "" : "a"), RC);
        }
    }
}
