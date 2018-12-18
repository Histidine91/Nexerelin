package exerelin.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.thoughtworks.xstream.XStream;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DirectoryScreenScript;
import exerelin.campaign.NexEventProbabilityManager;
import exerelin.campaign.StartSetupPostTimePass;
import exerelin.campaign.ReinitScreenScript;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.*;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.MiningFleetManager;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.missions.ConquestMissionCreator;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.versionchecker.VCModPluginCustom;
import exerelin.world.ExerelinProcGen;
import exerelin.world.LandmarkGenerator;
import exerelin.world.SSP_AsteroidTracker;
import java.util.HashMap;

public class ExerelinModPlugin extends BaseModPlugin
{
    // call order: onNewGame -> onNewGameAfterProcGen -> onNewGameAfterEconomyLoad -> onEnabled -> onNewGameAfterTimePass -> onGameLoad
    public static final boolean HAVE_SWP = Global.getSettings().getModManager().isModEnabled("swp");
    public static final boolean HAVE_DYNASECTOR = Global.getSettings().getModManager().isModEnabled("dynasector");
    public static final boolean HAVE_UNDERWORLD = Global.getSettings().getModManager().isModEnabled("underworld");
    public static final boolean HAVE_STELLAR_INDUSTRIALIST = Global.getSettings().getModManager().isModEnabled("stellar_industrialist");
    public static final boolean HAVE_VERSION_CHECKER = Global.getSettings().getModManager().isModEnabled("lw_version_checker");
    
    protected static boolean isNewGame = false;
    
    protected void applyToExistingSave()
    {
        Global.getLogger(this.getClass()).info("Applying Nexerelin to existing game");
        
        SectorAPI sector = Global.getSector();
        InvasionFleetManager im = InvasionFleetManager.create();
        AllianceManager am = AllianceManager.create();
        sector.addScript(SectorManager.create());
        sector.addScript(DiplomacyManager.create());
        sector.addScript(im);
        //sector.addScript(ResponseFleetManager.create());
        sector.addScript(MiningFleetManager.create());
        //sector.addScript(CovertOpsManager.create());
        sector.addScript(am);
        // debugging
        //im.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.invasionGracePeriod);
        //am.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.allianceGracePeriod);
        SectorManager.setSystemToRelayMap(new HashMap<String,String>());
        SectorManager.setPlanetToRelayMap(new HashMap<String,String>());
        
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
    }
    
    protected void addEventIfNeeded(String eventId)
    {
        if (!Global.getSector().getEventManager().isOngoing(null, eventId)) {
            Global.getSector().getEventManager().startEvent(null, eventId, null);
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
        MiningFleetManager.create();
        //CovertOpsManager.create();
        AllianceManager.create();
        
        addScriptsAndEventsIfNeeded();
        
        reverseCompatibility();
        refreshTariffs();
        
        SectorAPI sector = Global.getSector();
        sector.registerPlugin(new ExerelinCampaignPlugin());
        sector.addTransientScript(new DirectoryScreenScript());
        sector.addTransientScript(new SSP_AsteroidTracker());
        
        PrismMarket.clearSubmarketCache();
        
        if (!HAVE_VERSION_CHECKER)
            VCModPluginCustom.onGameLoad(newGame);
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
