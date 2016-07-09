package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
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
import exerelin.campaign.missions.ConquestMissionCreator;
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
			HAVE_SSP_LEGACY = Global.getSettings().getModManager().getModSpec("dr_ssp").getVersion().equals("3.4.0");	// FIXME not optimal (but meh)
		}
	}
    
    protected void applyToExistingSave()
    {
        Global.getLogger(this.getClass()).info("Applying Nexerelin to existing game");
        
        SectorAPI sector = Global.getSector();
        InvasionFleetManager im = InvasionFleetManager.create();
        AllianceManager am = AllianceManager.create();
        sector.removeScriptsOfClass(CoreScript.class);
        sector.removeScriptsOfClass(SSP_CoreScript.class);
        sector.addScript(new ExerelinCoreScript());
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
        PlayerFactionStore.setPlayerFactionId(ExerelinConstants.PLAYER_NPC_ID);
        sector.getFaction(Factions.PLAYER).setRelationship(ExerelinConstants.PLAYER_NPC_ID, 1);
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
        // fix SSP vs. SWP star system backgrounds
        if (HAVE_SWP && !HAVE_SSP)
        {
            for (StarSystemAPI system : Global.getSector().getStarSystems())
            {
                switch (system.getBackgroundTextureFilename()) {
                    case "graphics/ssp/backgrounds/ssp_arcade.png":
                        system.setBackgroundTextureFilename("graphics/swp/backgrounds/swp_arcade.png");
                        break;
                    case "graphics/ssp/backgrounds/ssp_atopthemountain.jpg":
                        system.setBackgroundTextureFilename("graphics/swp/backgrounds/swp_atopthemountain.jpg");
                        break;
                    case "graphics/ssp/backgrounds/ssp_conflictofinterest.jpg":
                        system.setBackgroundTextureFilename("graphics/swp/backgrounds/swp_conflictofinterest.jpg");
                        break;
                    case "graphics/ssp/backgrounds/ssp_corporateindirection.jpg":
                        system.setBackgroundTextureFilename("graphics/swp/backgrounds/swp_corporateindirection.jpg");
                        break;
                    case "graphics/ssp/backgrounds/ssp_overreachingexpansion.jpg":
                        system.setBackgroundTextureFilename("graphics/swp/backgrounds/swp_overreachingexpansion.jpg");
                        break;
                }
            }
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
		if (ExerelinUtilsFaction.isExiInCorvus() && !Global.getSector().getEventManager().isOngoing(null, "exerelin_exigency_respawn")) {
            Global.getSector().getEventManager().startEvent(null, "exerelin_exigency_respawn", null);
        }
        
        reverseCompatibility();
        refreshTariffs();
        
        Global.getSector().addTransientScript(new DirectoryScreenScript());
    }
    
    @Override
    public void onApplicationLoad() throws Exception
    {
        OmniFacSettings.reloadSettings();
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
		for (int i=0; i<OmniFacSettings.getNumberOfFactories(); i++) // TODO: use Omnifactory's numberOfFactories setting when it's supported
			PlayerStartHandler.addOmnifactory(sector, i);
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
