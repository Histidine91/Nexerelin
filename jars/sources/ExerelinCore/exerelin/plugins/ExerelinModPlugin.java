package exerelin.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.thoughtworks.xstream.XStream;
import data.scripts.campaign.SSP_CoreScript;
import exerelin.ExerelinConstants;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DirectoryScreenScript;
import exerelin.campaign.ExerelinCoreScript;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.PlayerStartHandler;
import exerelin.campaign.ReinitScreenScript;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.campaign.events.AgentDestabilizeMarketEvent;
import exerelin.campaign.events.AgentDestabilizeMarketEventForCondition;
import exerelin.campaign.events.AgentLowerRelationsEvent;
import exerelin.campaign.events.AllianceChangedEvent;
import exerelin.campaign.events.CovertOpsEventBase;
import exerelin.campaign.events.DiplomacyEvent;
import exerelin.campaign.events.ExerelinFactionCommissionMissionEvent;
import exerelin.campaign.events.ExigencyRespawnFleetEvent;
import exerelin.campaign.events.FactionChangedEvent;
import exerelin.campaign.events.FactionEliminatedEvent;
import exerelin.campaign.events.FactionRespawnedEvent;
import exerelin.campaign.events.InvasionFleetEvent;
import exerelin.campaign.events.MarketAttackedEvent;
import exerelin.campaign.events.MarketCapturedEvent;
import exerelin.campaign.events.RevengeanceFleetEvent;
import exerelin.campaign.events.SaboteurDestroyFoodEvent;
import exerelin.campaign.events.SaboteurSabotageReserveEvent;
import exerelin.campaign.events.SuperweaponEvent;
import exerelin.campaign.events.WarmongerEvent;
import exerelin.utilities.*;
import exerelin.campaign.fleets.DefenceFleetAI;
import exerelin.campaign.fleets.ExerelinPatrolFleetManager;
import exerelin.campaign.fleets.InvasionFleetAI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.campaign.fleets.MiningFleetAI;
import exerelin.campaign.fleets.MiningFleetManager;
import exerelin.campaign.fleets.RespawnFleetAI;
import exerelin.campaign.fleets.ResponseFleetAI;
import exerelin.campaign.fleets.ResponseFleetManager;
import exerelin.campaign.missions.ConquestMissionCreator;
import java.util.HashMap;

public class ExerelinModPlugin extends BaseModPlugin
{
    // call order: onNewGame -> onNewGameAfterEconomyLoad -> onEnabled -> onNewGameAfterTimePass -> onGameLoad
    public static final boolean HAVE_SSP;
    public static final boolean HAVE_SSP_LEGACY;
    public static final boolean HAVE_SWP = Global.getSettings().getModManager().isModEnabled("swp");
    public static final boolean HAVE_DYNASECTOR = Global.getSettings().getModManager().isModEnabled("dynasector");
    public static final boolean HAVE_UNDERWORLD = Global.getSettings().getModManager().isModEnabled("underworld");
    
    protected static boolean isNewGame = false;
    
    static {
        boolean sspLoaded = Global.getSettings().getModManager().isModEnabled("dr_ssp");
        if (!sspLoaded) {
            HAVE_SSP = false;
            HAVE_SSP_LEGACY = false;
        }
        else {
            HAVE_SSP = true;
            HAVE_SSP_LEGACY = Global.getSettings().getModManager().getModSpec("dr_ssp").getVersion().equals("3.4.0");    // FIXME not optimal (but meh)
        }
    }
    
    protected void applyToExistingSave()
    {
        Global.getLogger(this.getClass()).info("Applying Nexerelin to existing game");
        
        SectorAPI sector = Global.getSector();
        InvasionFleetManager im = InvasionFleetManager.create();
        AllianceManager am = AllianceManager.create();
        sector.removeScriptsOfClass(CoreScript.class);
        if (HAVE_SSP)
            sector.removeScriptsOfClass(SSP_CoreScript.class);
        sector.addScript(new ExerelinCoreScript());
        sector.addScript(SectorManager.create());
        sector.addScript(DiplomacyManager.create());
        sector.addScript(im);
        sector.addScript(ResponseFleetManager.create());
        sector.addScript(MiningFleetManager.create());
        sector.addScript(CovertOpsManager.create());
        sector.addScript(am);
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
        
        sector.registerPlugin(new ExerelinCoreCampaignPlugin());
        SectorManager.setCorvusMode(true);
        SectorManager.reinitLiveFactions();
        PlayerFactionStore.setPlayerFactionId(ExerelinConstants.PLAYER_NPC_ID);
        sector.getFaction(Factions.PLAYER).setRelationship(ExerelinConstants.PLAYER_NPC_ID, 1);
        ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
        
        sector.addTransientScript(new ReinitScreenScript());
    }
    
    protected void refreshTariffs()
    {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            market.getTariff().modifyMult("nexerelinMult", ExerelinConfig.baseTariffMult);
            if (market.hasCondition("free_market")) 
                market.getTariff().modifyMult("isFreeMarket", ExerelinConfig.freeMarketTariffMult);
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
        
        if (!Global.getSector().getEventManager().isOngoing(null, "exerelin_faction_salary")) {
            Global.getSector().getEventManager().startEvent(null, "exerelin_faction_salary", null);
        }
        if (!Global.getSector().getEventManager().isOngoing(null, "exerelin_faction_insurance")) {
            Global.getSector().getEventManager().startEvent(null, "exerelin_faction_insurance", null);
        }
        if (ExerelinUtilsFaction.isExiInCorvus() && !Global.getSector().getEventManager().isOngoing(null, "exerelin_exigency_respawn")) {
            Global.getSector().getEventManager().startEvent(null, "exerelin_exigency_respawn", null);
        }
        if (RevengeanceFleetEvent.getOngoingEvent() == null) {
            Global.getSector().getEventManager().startEvent(null, "exerelin_revengeance_fleet", null);
        }
        
        reverseCompatibility();
        refreshTariffs();
        
        Global.getSector().addTransientScript(new DirectoryScreenScript());
    }
    
    @Override
    public void onApplicationLoad() throws Exception
    {
        //ExerelinConfig.loadSettings();
    }
    
    @Override
    public void onNewGameAfterTimePass() {
        Global.getLogger(this.getClass()).info("New game after time pass; " + isNewGame);
        PlayerStartHandler.execute();
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        Global.getLogger(this.getClass()).info("New game after economy load; " + isNewGame);
        
        if (SectorManager.getCorvusMode())
        {
            SectorManager.reinitLiveFactions();
            DiplomacyManager.initFactionRelationships(false);    // the mod factions set their own relationships, so we have to re-randomize if needed afterwards
        }
        
        SectorAPI sector = Global.getSector();
        //for (int i=0; i<OmniFacSettings.getNumberOfFactories(); i++) // TODO: use Omnifactory's numberOfFactories setting when it's supported
        //    PlayerStartHandler.addOmnifactory(sector, i);
    }
    
    @Override
    public void configureXStream(XStream x) {
        /*
        x.alias("AllianceMngr", AllianceManager.class);
        x.alias("CovertOpsMngr", CovertOpsManager.class);
        x.alias("DiplomacyMngr", DiplomacyManager.class);
        x.alias("ExerelinCoreScript", ExerelinCoreScript.class);
        x.alias("PlayerFactionStore", PlayerFactionStore.class);
        x.alias("SectorMngr", SectorManager.class);
        
        x.alias("InvasionFltMngr", InvasionFleetManager.class);
        x.alias("ResponseFltMngr", ResponseFleetManager.class);
        x.alias("MiningFltMngr", MiningFleetManager.class);
        x.alias("ExePatrolFltMngr", ExerelinPatrolFleetManager.class);
        */
                
        x.alias("DefenceFltAI", DefenceFleetAI.class);
        x.alias("InvasionFltAI", InvasionFleetAI.class);
        x.alias("MiningFltAI", MiningFleetAI.class);
        x.alias("RespawnFltAI", RespawnFleetAI.class);
        x.alias("ResponseFltAI", ResponseFleetAI.class);
        x.alias("ExePatrolFltMngr", ExerelinPatrolFleetManager.class);
        
        x.alias("AgntDestabilizeMrktEvnt", AgentDestabilizeMarketEvent.class);
        x.alias("AgntDestabilizeMrktEvntForCondition", AgentDestabilizeMarketEventForCondition.class);
        x.alias("AgntLowerRelationsEvnt", AgentLowerRelationsEvent.class);
        x.alias("AllyChangedEvnt", AllianceChangedEvent.class);
        x.alias("CovertOpsEvnt", CovertOpsEventBase.class);
        x.alias("DiploEvnt", DiplomacyEvent.class);
        x.alias("ExeCommissionMissionEvnt", ExerelinFactionCommissionMissionEvent.class);
        //x.alias("ExeRepTrckrEvnt", ExerelinRepTrackerEvent.class);
        x.alias("ExiRespawnFltEvnt", ExigencyRespawnFleetEvent.class);
        x.alias("FactionChangeEvnt", FactionChangedEvent.class);
        x.alias("FactionElimEvnt", FactionEliminatedEvent.class);
        //x.alias("FactionInsurEvnt", FactionInsuranceEvent.class);
        x.alias("FactionRespawnEvnt", FactionRespawnedEvent.class);
        //x.alias("FactionSalaryEvnt", FactionSalaryEvent.class);
        x.alias("InvasionFltEvnt", InvasionFleetEvent.class);
        x.alias("MrktAttackedEvnt", MarketAttackedEvent.class);
        x.alias("MrktCapturedEvnt", MarketCapturedEvent.class);
        //x.alias("RevengeanceFltEvnt", RevengeanceFleetEvent.class);
        x.alias("SbtrDestroyFoodEvnt", SaboteurDestroyFoodEvent.class);
        x.alias("SbtrSabotageReserveEvnt", SaboteurSabotageReserveEvent.class);
        //x.alias("SlavesSoldEvnt", SlavesSoldEvent.class);    // TODO merge into reptracker
        x.alias("SuperweaponEvnt", SuperweaponEvent.class);
        //x.alias("VictoryEvnt", VictoryEvent.class);
        x.alias("WarmongerEvnt", WarmongerEvent.class);
    }
}
