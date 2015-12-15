package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.thoughtworks.xstream.XStream;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DirectoryScreenScript;
import exerelin.campaign.ExerelinCoreScript;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.PlayerStartHandler;
import exerelin.campaign.ReinitScreenScript;
import exerelin.campaign.SectorManager;
import exerelin.campaign.StatsTracker;
import exerelin.plugins.ExerelinCoreCampaignPlugin;
import exerelin.utilities.*;
import exerelin.world.ExerelinPatrolFleetManager;
import exerelin.world.InvasionFleetManager;
import exerelin.world.MiningFleetManager;
import exerelin.world.ResponseFleetManager;
import java.util.HashMap;
import org.lazywizard.omnifac.OmniFacSettings;

public class ExerelinModPlugin extends BaseModPlugin
{
    // call order: onNewGame -> onNewGameAfterEconomyLoad -> onEnabled -> onNewGameAfterTimePass -> onGameLoad
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
        //im.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.invasionGracePeriod);
        //am.advance(sector.getClock().getSecondsPerDay() * ExerelinConfig.allianceGracePeriod);
        SectorManager.setSystemToRelayMap(new HashMap<String,String>());
        SectorManager.setPlanetToRelayMap(new HashMap<String,String>());
        
		// replace patrol handling with our own
		for (MarketAPI market : sector.getEconomy().getMarketsCopy())
		{
			market.getPrimaryEntity().removeScriptsOfClass(PatrolFleetManager.class);
		}
		sector.removeScriptsOfClass(CoreScript.class);
		sector.addScript(new ExerelinCoreScript());
		
        StatsTracker.create();
        
        sector.registerPlugin(new ExerelinCoreCampaignPlugin());
        SectorManager.setCorvusMode(true);
        SectorManager.reinitLiveFactions();
        PlayerFactionStore.setPlayerFactionId("player_npc");
        sector.getFaction(Factions.PLAYER).setRelationship("player_npc", 1);
		ExerelinUtilsReputation.syncFactionRelationshipsToPlayer();
        
        sector.addTransientScript(new ReinitScreenScript());
    }
    
    protected void refreshTariffs()
    {
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (!SectorManager.getCorvusMode())
                market.getTariff().modifyFlat("default_tariff", ExerelinConfig.baseTariff);
            if (market.hasCondition("free_market")) 
                market.getTariff().modifyMult("isFreeMarket", ExerelinConfig.freeMarketTariffMult);
        }
    }
    
    @Override
    public void beforeGameSave()
    {
        //SectorManager.getCurrentSectorManager().getCommandQueue().executeAllCommands();
    }

    @Override
    public void onNewGame() {
        Global.getLogger(this.getClass()).info("New game");
        isNewGame = true;
        //ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        //ExerelinCheck.checkModCompatability();
    }
    
    protected void reverseCompatibility()
    {
        // fix famous bounties fighting people they shouldn't
        for (FactionAPI faction : Global.getSector().getAllFactions()) 
        {
            if (!faction.isPlayerFaction() && !faction.getId().equals("famous_bounty"))
            faction.setRelationship("famous_bounty", 0);
        }
        
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
    public void onGameLoad(boolean newGame) {
        Global.getLogger(this.getClass()).info("Game load; " + SectorManager.isSectorManagerSaved());
        isNewGame = newGame;
        
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
        if (SectorManager.getHardMode() && ExerelinUtils.isSSPInstalled())
        {
            //if (!Global.getSector().getEventManager().isOngoing(null, "player_bounty")) {
            //    Global.getSector().getEventManager().startEvent(null, "player_bounty", null);
            //}
        }
        
        reverseCompatibility();
        refreshTariffs();
        
        Global.getSector().addTransientScript(new DirectoryScreenScript());
    }
    
    @Override
    public void onApplicationLoad() throws Exception
    {
        OmniFacSettings.reloadSettings();
        ExerelinConfig.loadSettings();
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
    }
	
	@Override
	public void configureXStream(XStream x) {
		x.alias("AllianceManager", AllianceManager.class);
		x.alias("CovertOpsManager", CovertOpsManager.class);
		x.alias("DiplomacyManager", DiplomacyManager.class);
		x.alias("ExerelinCoreScript", ExerelinCoreScript.class);
		x.alias("PlayerFactionStore", PlayerFactionStore.class);
		x.alias("SectorManager", SectorManager.class);
		x.alias("InvasionFleetManager", InvasionFleetManager.class);
		x.alias("ResponseFleetManager", ResponseFleetManager.class);
		x.alias("MiningFleetManager", MiningFleetManager.class);
		x.alias("ExerelinPatrolFleetManager", ExerelinPatrolFleetManager.class);
	}
}
