package exerelin.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.impl.campaign.intel.FactionHostilityManager;
import com.fs.starfarer.api.impl.campaign.intel.ProcurementMissionCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.DeliveryBarEventCreator;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionManager;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager;
import com.fs.starfarer.api.impl.campaign.submarkets.StoragePlugin;
import com.fs.starfarer.api.util.Misc;
import com.thoughtworks.xstream.XStream;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.ui.FieldOptionsScreenScript;
import exerelin.campaign.MarketDescChanger;
import exerelin.campaign.ui.PlayerFactionSetupNag;
import exerelin.campaign.StartSetupPostTimePass;
import exerelin.campaign.ui.ReinitScreenScript;
import exerelin.campaign.RevengeanceManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.battle.EncounterLootHandler;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.utilities.*;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.MiningFleetManagerV2;
import exerelin.campaign.fleets.PlayerInSystemTracker;
import exerelin.campaign.intel.ConquestMissionManager;
import exerelin.campaign.intel.FactionBountyManager;
import exerelin.campaign.intel.Nex_HegemonyInspectionManager;
import exerelin.campaign.intel.Nex_PunitiveExpeditionManager;
import exerelin.campaign.intel.agents.AgentBarEventCreator;
import exerelin.campaign.intel.bar.NexDeliveryBarEventCreator;
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel;
import exerelin.campaign.intel.invasion.InvasionIntel;
import exerelin.campaign.intel.missions.DisruptMissionManager;
import exerelin.campaign.intel.missions.Nex_ProcurementMissionCreator;
import exerelin.campaign.intel.rebellion.RebellionCreator;
import exerelin.campaign.intel.specialforces.SpecialForcesManager;
import exerelin.campaign.submarkets.Nex_LocalResourcesSubmarketPlugin;
import exerelin.campaign.submarkets.Nex_StoragePlugin;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.versionchecker.VCModPluginCustom;
import exerelin.world.ExerelinProcGen;
import exerelin.world.LandmarkGenerator;
import exerelin.world.SSP_AsteroidTracker;
import exerelin.world.VanillaSystemsGenerator;
import exerelin.world.scenarios.ScenarioManager;
import java.io.IOException;
import org.apache.log4j.Logger;

public class ExerelinModPlugin extends BaseModPlugin
{
    // call order: onNewGame -> onNewGameAfterProcGen -> onNewGameAfterEconomyLoad -> onEnabled -> onNewGameAfterTimePass -> onGameLoad
    public static final boolean HAVE_SWP = Global.getSettings().getModManager().isModEnabled("swp");
    public static final boolean HAVE_DYNASECTOR = Global.getSettings().getModManager().isModEnabled("dynasector");
    public static final boolean HAVE_UNDERWORLD = Global.getSettings().getModManager().isModEnabled("underworld");
    //public static final boolean HAVE_STELLAR_INDUSTRIALIST = Global.getSettings().getModManager().isModEnabled("stellar_industrialist");
    public static final boolean HAVE_VERSION_CHECKER = Global.getSettings().getModManager().isModEnabled("lw_version_checker");
    public static boolean isNexDev = false;
    
    public static Logger log = Global.getLogger(ExerelinModPlugin.class);
    protected static boolean isNewGame = false;
    
    
    public static void replaceSubmarket(MarketAPI market, String submarketId) {
        if (!market.hasSubmarket(submarketId)) return;
        
        CargoAPI current = market.getSubmarket(submarketId).getCargo();
        FleetDataAPI ships = current.getMothballedShips();
        boolean haveAccess = Misc.playerHasStorageAccess(market);
        
        market.removeSubmarket(submarketId);
        market.addSubmarket(submarketId);
        SubmarketAPI submarket = market.getSubmarket(submarketId);
        
        // migrate cargo
        CargoAPI newCargo = market.getSubmarket(submarketId).getCargo();
        newCargo.clear();
        newCargo.addAll(current);
        newCargo.sort();
        
        // move ships to new cargo
        newCargo.initMothballedShips(submarket.getFaction().getId());
        for (FleetMemberAPI ship : ships.getMembersListCopy()) {
            newCargo.getMothballedShips().addFleetMember(ship);
        }
        
        if (submarketId.equals(Submarkets.SUBMARKET_STORAGE)) {
            ((StoragePlugin)submarket.getPlugin()).setPlayerPaidToUnlock(haveAccess);
        }
    }
    
    protected void applyToExistingSave()
    {
        log.info("Applying Nexerelin to existing game");
        
        SectorAPI sector = Global.getSector();
        addScripts();
        
        // debugging
        //im.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.invasionGracePeriod);
        //am.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.allianceGracePeriod);
        
        // replace or remove relevant intel items
        ScriptReplacer.replaceScript(sector, FactionHostilityManager.class, null);
        ScriptReplacer.replaceScript(sector, HegemonyInspectionManager.class, new Nex_HegemonyInspectionManager());
        ScriptReplacer.replaceScript(sector, PunitiveExpeditionManager.class, new Nex_PunitiveExpeditionManager());
        ScriptReplacer.replaceMissionCreator(ProcurementMissionCreator.class, new Nex_ProcurementMissionCreator());
        //replaceMissionCreator(AnalyzeEntityIntelCreator.class, new Nex_AnalyzeEntityIntelCreator());
        //replaceMissionCreator(SurveyPlanetIntelCreator.class, new Nex_SurveyPlanetIntelCreator());
        ScriptReplacer.replaceBarEventCreator(DeliveryBarEventCreator.class, new NexDeliveryBarEventCreator());
        
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            replaceSubmarket(market, Submarkets.LOCAL_RESOURCES);
            replaceSubmarket(market, Submarkets.SUBMARKET_OPEN);
            replaceSubmarket(market, Submarkets.GENERIC_MILITARY);
            replaceSubmarket(market, Submarkets.SUBMARKET_BLACK);
            replaceSubmarket(market, Submarkets.SUBMARKET_STORAGE);
        }
        
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (!market.getMemoryWithoutUpdate().contains(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION))
                market.getMemoryWithoutUpdate().set(ExerelinConstants.MEMKEY_MARKET_STARTING_FACTION, market.getFactionId());
            market.getMemoryWithoutUpdate().set("$startingFreeMarket", market.hasCondition(Conditions.FREE_PORT) || market.isFreePort());
            ColonyManager.updateFreePortSetting(market);
        }
        
        StatsTracker.create();
        
        SectorManager.getManager().setCorvusMode(true);
        SectorManager.reinitLiveFactions();
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
        // replace submarkets
        // local resources
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (!market.hasSubmarket(Submarkets.LOCAL_RESOURCES)) continue;
            if (market.getSubmarket(Submarkets.LOCAL_RESOURCES).getPlugin() instanceof Nex_LocalResourcesSubmarketPlugin)
                continue;
            log.info("Replacing local resources submarket on " + market.getName());
            replaceSubmarket(market, Submarkets.LOCAL_RESOURCES);
        }
        
        // storage
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (!market.hasSubmarket(Submarkets.SUBMARKET_STORAGE)) continue;
            if (market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getPlugin() instanceof Nex_StoragePlugin)
                continue;
            
            log.info("Replacing storage submarket on " + market.getName());
            replaceSubmarket(market, Submarkets.SUBMARKET_STORAGE);
        }
        
        // retroactive fix for brawl mode defense fleets which are still hanging around
        for (IntelInfoPlugin intel : Global.getSector().getIntelManager().getIntel(InvasionIntel.class)) {
            InvasionIntel invIntel = (InvasionIntel)intel;
            DefenseFleetIntel defIntel = invIntel.getBrawlDefIntel();
            if (defIntel != null && (defIntel.isEnding() || defIntel.isEnded())) {
                defIntel.giveReturnOrders();
            }
        }
        
        ScriptReplacer.replaceBarEventCreator(DeliveryBarEventCreator.class, new NexDeliveryBarEventCreator());
        
        DiplomacyManager.getManager().reverseCompatibility();
        
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
            defender.unmodify("nex_invasionDefBonus");
            defender.unmodify("nex_invasionDefBonusGeneral");
        }
        
        if (SpecialForcesManager.getManager() == null) {
            new SpecialForcesManager().init();
        }
            
    }
    
    protected void addBarEvents() {
        BarEventManager bar = BarEventManager.getInstance();
        if (bar != null && !bar.hasEventCreator(AgentBarEventCreator.class)) {
            bar.addEventCreator(new AgentBarEventCreator());
        }
    }
    
    public static void addScripts() {
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
        new SpecialForcesManager().init();
        RebellionCreator.generate();
        
        sector.addScript(new ConquestMissionManager());
        sector.addScript(new DisruptMissionManager());
        sector.addScript(new FactionBountyManager());
    }
    
    // Stuff here should be moved to new game once it is expected that no existing saves lack them
    protected void addScriptsAndEventsIfNeeded() {
        if (!Global.getSector().hasScript(RebellionCreator.class)) {
            RebellionCreator.generate();
        }
        
        addBarEvents();
    }
    
    @Override
    public void onGameLoad(boolean newGame) {
        log.info("Game load");
        isNewGame = newGame;
        
        ScenarioManager.clearScenario();
        
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
        
        if (!HAVE_VERSION_CHECKER && Global.getSettings().getBoolean("nex_enableVersionChecker"))
            VCModPluginCustom.onGameLoad(newGame);
        
        if (!Misc.isPlayerFactionSetUp())
            sector.addTransientScript(new PlayerFactionSetupNag());
        
        sector.addTransientListener(new EncounterLootHandler());
        if (!newGame)
            EconomyInfoHelper.createInstance();
        
        if (ExerelinConfig.updateMarketDescOnCapture)
            sector.getListenerManager().addListener(new MarketDescChanger(), true);
        
        sector.addTransientScript(new PlayerInSystemTracker());
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
        if (!HAVE_VERSION_CHECKER && Global.getSettings().getBoolean("nex_enableVersionChecker"))
            VCModPluginCustom.onApplicationLoad();
        boolean hasLazyLib = Global.getSettings().getModManager().isModEnabled("lw_lazylib");
        if (!hasLazyLib) {
            throw new RuntimeException(StringHelper.getString("exerelin_misc", "errorLazyLib"));
        }
        
        // Nex dev check
        try {
            String str = Global.getSettings().readTextFileFromCommon("nex_dev");
            if (str != null && !str.isEmpty()) {
                log.info("Nex dev mode on: " + str);
                isNexDev = true;
            }
            else {
                log.info("Nex dev mode off");
            }
        }
        catch (IOException ex)
        {
            // Do nothing
        }
        
        // TODO: toggle to disable this
        /*
        int officerMaxLevel = (int)Global.getSettings().getFloat("officerMaxLevel");
        //Global.getLogger(this.getClass()).info("wololo officer level: " + officerMaxLevel);
        if (officerMaxLevel > 29)
            throw new RuntimeException(StringHelper.getStringAndSubstituteToken(
                    "exerelin_misc", "errorOfficerMaxLevel", "$currMax", officerMaxLevel + ""));
        */
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
        
        if (SectorManager.getCorvusMode()) {
            VanillaSystemsGenerator.enhanceVanillaMarkets();
        }
        
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
		EconomyInfoHelper.createInstance();
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
