package exerelin.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI;
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RuinsFleetRouteManager;
import com.thoughtworks.xstream.XStream;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DirectoryScreenScript;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.StartSetupPostTimePass;
import exerelin.campaign.ReinitScreenScript;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.events.RevengeanceManagerEvent;
import exerelin.utilities.*;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.MiningFleetManager;
import exerelin.campaign.fleets.PatrolFleetManagerReplacer;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.missions.ConquestMissionCreator;
import exerelin.world.ExerelinProcGen;
import exerelin.world.SSP_AsteroidTracker;
import java.util.HashMap;

public class ExerelinModPlugin extends BaseModPlugin
{
    // call order: onNewGame -> onNewGameAfterProcGen -> onNewGameAfterEconomyLoad -> onEnabled -> onNewGameAfterTimePass -> onGameLoad
    public static final boolean HAVE_SWP = Global.getSettings().getModManager().isModEnabled("swp");
    public static final boolean HAVE_DYNASECTOR = Global.getSettings().getModManager().isModEnabled("dynasector");
    public static final boolean HAVE_UNDERWORLD = Global.getSettings().getModManager().isModEnabled("underworld");
    public static final boolean HAVE_STELLAR_INDUSTRIALIST = Global.getSettings().getModManager().isModEnabled("stellar_industrialist");
    
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
        sector.addScript(ResponseFleetManager.create());
        sector.addScript(MiningFleetManager.create());
        sector.addScript(CovertOpsManager.create());
        sector.addScript(am);
        sector.addScript(new PatrolFleetManagerReplacer());
        // debugging
        //im.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.invasionGracePeriod);
        //am.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.allianceGracePeriod);
        SectorManager.setSystemToRelayMap(new HashMap<String,String>());
        SectorManager.setPlanetToRelayMap(new HashMap<String,String>());
        
        // replace patrol handling with our own
        for (MarketAPI market : sector.getEconomy().getMarketsCopy())
        {
            market.getPrimaryEntity().removeScriptsOfClass(PatrolFleetManager.class);
        }
        
        StatsTracker.create();
        
        SectorManager.setCorvusMode(true);
        SectorManager.reinitLiveFactions();
        PlayerFactionStore.setPlayerFactionId(ExerelinConstants.PLAYER_NPC_ID);
        sector.getFaction(Factions.PLAYER).setRelationship(ExerelinConstants.PLAYER_NPC_ID, 1);
        ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
        
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
    
    @Override
    public void beforeGameSave()
    {
    }

    @Override
    public void onNewGame() {
        Global.getLogger(this.getClass()).info("New game");
        isNewGame = true;
        //ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        //ExerelinCheck.checkModCompatability();
        addScriptsIfNeeded();
    }
    
    protected void reverseCompatibility()
    {
    
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
    
    protected void addScriptsIfNeeded() {
        SectorAPI sector = Global.getSector();
        if (!sector.hasScript(ConquestMissionCreator.class)) {
            sector.addScript(new ConquestMissionCreator());
        }
    }
    
    @Override
    public void onGameLoad(boolean newGame) {
        Global.getLogger(this.getClass()).info("Game load; " + SectorManager.isSectorManagerSaved());
        isNewGame = newGame;
        
        addScriptsIfNeeded();
        
        ExerelinConfig.loadSettings();
        SectorManager.create();
        DiplomacyManager.create();
        InvasionFleetManager.create();
        ResponseFleetManager.create();
        MiningFleetManager.create();
        CovertOpsManager.create();
        AllianceManager.create();
        
        SectorAPI sector = Global.getSector();
        
        if (!sector.getEventManager().isOngoing(null, "exerelin_faction_salary")) {
            sector.getEventManager().startEvent(null, "exerelin_faction_salary", null);
        }
        if (!sector.getEventManager().isOngoing(null, "exerelin_faction_insurance")) {
            sector.getEventManager().startEvent(null, "exerelin_faction_insurance", null);
        }
        if (ExerelinUtilsFaction.isExiInCorvus() && !sector.getEventManager().isOngoing(null, "exerelin_exigency_respawn")) {
            sector.getEventManager().startEvent(null, "exerelin_exigency_respawn", null);
        }
        if (RevengeanceManagerEvent.getOngoingEvent() == null) {
            sector.getEventManager().startEvent(null, "exerelin_revengeance_manager", null);
        }
        if (!sector.getEventManager().isOngoing(null, "exerelin_slaves_sold")) {
            sector.getEventManager().startEvent(null, "exerelin_slaves_sold", null);
        }
        
        reverseCompatibility();
        refreshTariffs();
        
        sector.registerPlugin(new ExerelinCoreCampaignPlugin());
        sector.addTransientScript(new DirectoryScreenScript());
        sector.addTransientScript(new SSP_AsteroidTracker());
    }
    
    @Override
    public void onApplicationLoad() throws Exception
    {
        //ExerelinConfig.loadSettings();
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
        }
        //SectorAPI sector = Global.getSector();
        //for (int i=0; i<OmniFacSettings.getNumberOfFactories(); i++) // TODO: use Omnifactory's numberOfFactories setting when it's supported
        //    PlayerStartHandler.addOmnifactory(sector, i);
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
