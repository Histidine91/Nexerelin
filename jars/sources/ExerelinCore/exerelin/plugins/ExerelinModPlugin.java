package exerelin.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.intel.FactionHostilityManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.inspection.HegemonyInspectionManager;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager;
import com.fs.starfarer.api.util.Misc;
import com.thoughtworks.xstream.XStream;
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
import exerelin.utilities.*;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.MiningFleetManagerV2;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.intel.FactionBountyManager;
import exerelin.campaign.intel.Nex_HegemonyInspectionManager;
import exerelin.campaign.intel.Nex_PunitiveExpeditionManager;
import exerelin.campaign.intel.agents.AgentBarEventCreator;
import exerelin.campaign.missions.ConquestMissionCreator;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.versionchecker.VCModPluginCustom;
import exerelin.world.ExerelinProcGen;
import exerelin.world.LandmarkGenerator;
import exerelin.world.SSP_AsteroidTracker;

public class ExerelinModPlugin extends BaseModPlugin
{
    // call order: onNewGame -> onNewGameAfterProcGen -> onNewGameAfterEconomyLoad -> onEnabled -> onNewGameAfterTimePass -> onGameLoad
    public static final boolean HAVE_SWP = Global.getSettings().getModManager().isModEnabled("swp");
    public static final boolean HAVE_DYNASECTOR = Global.getSettings().getModManager().isModEnabled("dynasector");
    public static final boolean HAVE_UNDERWORLD = Global.getSettings().getModManager().isModEnabled("underworld");
    //public static final boolean HAVE_STELLAR_INDUSTRIALIST = Global.getSettings().getModManager().isModEnabled("stellar_industrialist");
    public static final boolean HAVE_VERSION_CHECKER = Global.getSettings().getModManager().isModEnabled("lw_version_checker");
    
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
                
                Global.getLogger(this.getClass()).info("Removing EveryFrameScript " + script.toString() + " | " + toRemove.getCanonicalName());
                
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
    
    protected void applyToExistingSave()
    {
        Global.getLogger(this.getClass()).info("Applying Nexerelin to existing game");
        
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
        
        /*
        // replace patrol handling with our own
        sector.addScript(new PatrolFleetManagerReplacer());
        // not sure if this is needed since the replacer should already do it, but just to be safe
        for (MarketAPI market : sector.getEconomy().getMarketsCopy())
        {
            ExerelinUtils.removeScriptAndListener(market.getPrimaryEntity(), 
                    PatrolFleetManager.class, ExerelinPatrolFleetManager.class);
        }
        */
        
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            market.getMemoryWithoutUpdate().set("$startingFactionId", market.getFactionId());
            market.getMemoryWithoutUpdate().set("$startingFreeMarket", market.hasCondition(Conditions.FREE_PORT));
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
    
    protected void refreshTariffs()
    {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            ExerelinUtilsMarket.setTariffs(market);
        }
    }
    
    protected void reverseCompatibility()
    {
        if (CovertOpsManager.getManager() == null) {
            Global.getSector().addScript(CovertOpsManager.create());
        }
        
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
                Global.getLogger(this.getClass()).info("Fixing free ports for market " + market.getName() + ": " + numFreePorts);
                market.removeCondition(Conditions.FREE_PORT);
                market.addCondition(Conditions.FREE_PORT);
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
        if (!sector.hasScript(ConquestMissionCreator.class)) {
            //sector.addScript(new ConquestMissionCreator());
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
        Global.getLogger(this.getClass()).info("Game load; " + SectorManager.isSectorManagerSaved());
        isNewGame = newGame;
        
        // legacy: assign the static variables referencing the singletons,
        // so the static methods in those classes can get them
        SectorManager.create();
        DiplomacyManager.create();
        InvasionFleetManager.create();
        ResponseFleetManager.create();
        AllianceManager.create();
        
        addScriptsAndEventsIfNeeded();
        
        reverseCompatibility();
        refreshTariffs();
        
        SectorAPI sector = Global.getSector();
        sector.registerPlugin(new ExerelinCampaignPlugin());
        sector.addTransientScript(new FieldOptionsScreenScript());
        sector.addTransientScript(new SSP_AsteroidTracker());
        //sector.removeScriptsOfClass(FactionHostilityManager.class);
        
        PrismMarket.clearSubmarketCache();
		
		ColonyManager.getManager().updatePlayerBonusAdmins();
        
        if (!HAVE_VERSION_CHECKER)
            VCModPluginCustom.onGameLoad(newGame);
        
        if (!Misc.isPlayerFactionSetUp())
            sector.addTransientScript(new PlayerFactionSetupNag());
    }
    
    @Override
    public void beforeGameSave()
    {
        Global.getLogger(this.getClass()).info("Before game save");
    }
    
    @Override
    public void afterGameSave() {
        Global.getLogger(this.getClass()).info("After game save");
    }
    
    @Override
    public void onApplicationLoad() throws Exception
    {
        //ExerelinConfig.loadSettings();
        if (!HAVE_VERSION_CHECKER)
            VCModPluginCustom.onApplicationLoad();
        boolean hasLazyLib = Global.getSettings().getModManager().isModEnabled("lw_lazylib");
        if (!hasLazyLib) {
            throw new RuntimeException(StringHelper.getString("exerelin_misc", "errorLazyLib"));
        }
        
        // compatibility warnings
        int playerMaxLevel = (int)Global.getSettings().getFloat("playerMaxLevel");
        //Global.getLogger(this.getClass()).info("wololo player level: " + playerMaxLevel);
        //if (playerMaxLevel > 97)
        //    throw new RuntimeException("Player max level in config is over 97 (current: " + playerMaxLevel + ")"
        //            + "\nThis risks a hang during gameplay."
        //            + "\nSee http://fractalsoftworks.com/forum/index.php?topic=13195.0 for more details.");
        int officerMaxLevel = (int)Global.getSettings().getFloat("officerMaxLevel");
        //Global.getLogger(this.getClass()).info("wololo officer level: " + officerMaxLevel);
        if (officerMaxLevel > 29)
            throw new RuntimeException(StringHelper.getStringAndSubstituteToken(
                    "exerelin_misc", "errorOfficerMaxLevel", "$currMax", officerMaxLevel + ""));
    }
    
    @Override
    public void onNewGame() {
        Global.getLogger(this.getClass()).info("New game");
        isNewGame = true;
        //ExerelinSetupData.resetInstance();
        //ExerelinCheck.checkModCompatability();
        addScriptsAndEventsIfNeeded();
    }
    
    @Override
    public void onEnabled(boolean wasEnabledBefore) {
        Global.getLogger(this.getClass()).info("On enabled; " + wasEnabledBefore);
        if (!isNewGame && !wasEnabledBefore)
        {
            Global.getLogger(this.getClass()).info(!isNewGame + ", " + !wasEnabledBefore);
            applyToExistingSave();
        }
    }
    
    @Override
    public void onNewGameAfterProcGen() {
        Global.getLogger(this.getClass()).info("New game after proc gen; " + isNewGame);
        if (!SectorManager.getCorvusMode())
            new ExerelinProcGen().generate();
    }
    
    @Override
    public void onNewGameAfterEconomyLoad() {
        Global.getLogger(this.getClass()).info("New game after economy load; " + isNewGame);
        
        SectorManager.reinitLiveFactions();
        if (SectorManager.getCorvusMode())
        {
            DiplomacyManager.initFactionRelationships(false);    // the mod factions set their own relationships, so we have to re-randomize if needed afterwards
        }
        
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            market.getMemoryWithoutUpdate().set("$startingFactionId", market.getFactionId());
            market.getMemoryWithoutUpdate().set("$startingFreeMarket", market.hasCondition(Conditions.FREE_PORT));
        }
        
        new LandmarkGenerator().generate(Global.getSector(), SectorManager.getCorvusMode());
        
        addBarEvents();
    }
    
    @Override
    public void onNewGameAfterTimePass() {
        Global.getLogger(this.getClass()).info("New game after time pass; " + isNewGame);
        StartSetupPostTimePass.execute();
    }
    
    @Override
    public void configureXStream(XStream x) {
        XStreamConfig.configureXStream(x);
    }
}
