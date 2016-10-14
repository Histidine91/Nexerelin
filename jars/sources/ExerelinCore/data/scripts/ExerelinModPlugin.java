package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
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
import exerelin.plugins.ExerelinCoreCampaignPlugin;
import exerelin.utilities.*;
import exerelin.world.DefenceFleetAI;
import exerelin.world.ExerelinPatrolFleetManager;
import static exerelin.world.ExerelinSectorGen.BINARY_STAR_DISTANCE;
import exerelin.world.InvasionFleetAI;
import exerelin.world.InvasionFleetManager;
import exerelin.world.MiningFleetAI;
import exerelin.world.MiningFleetManager;
import exerelin.world.RespawnFleetAI;
import exerelin.world.ResponseFleetAI;
import exerelin.world.ResponseFleetManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.omnifac.OmniFacSettings;
import org.lwjgl.util.vector.Vector2f;

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
        // fix binary star systems
        SectorAPI sector = Global.getSector();
        if (!SectorManager.getCorvusMode()) 
        {
            for (StarSystemAPI system : sector.getStarSystems())
            {
                for (PlanetAPI planet : system.getPlanets())
                {
                    if (planet.isStar() && system.getStar() != planet)
                    {
                        Vector2f loc = planet.getLocation();
                        float orbitRadius = (float)Math.sqrt(loc.x * loc.x + loc.y * loc.y);
                        if (orbitRadius < 100000) continue;

                        Global.getLogger(this.getClass()).info("Star " + planet.getName() + " has defective orbit, recomputing");
                        //Global.getLogger(this.getClass()).info("Star position: " + planet.getName() + ", " + planet.getLocation().x + ", " + planet.getLocation().y);
                        PlanetAPI primary = system.getStar();
                        float distance = (BINARY_STAR_DISTANCE + primary.getRadius()*5 + planet.getRadius()*5) * MathUtils.getRandomNumberInRange(0.95f, 1.1f) ;
                        float orbitDays = ExerelinUtilsAstro.getOrbitalPeriod(primary, distance + primary.getRadius());
                        ExerelinUtilsAstro.setOrbit(planet, primary, distance, true, ExerelinUtilsAstro.getRandomAngle(), orbitDays);

                        // regenerate fringe jump point
                        List<SectorEntityToken> jumps = system.getEntities(JumpPointAPI.class);
                        List<SectorEntityToken> toRemove = new ArrayList<>();
                        for (SectorEntityToken temp : jumps)
                        {
                            if (!temp.getName().contains("Fringe")) continue;
                            JumpPointAPI jump = (JumpPointAPI)temp;
                            for (JumpDestination dest : jump.getDestinations())
                            {
                                SectorEntityToken destEnt = dest.getDestination();
                                toRemove.add(destEnt);
                            }
                            toRemove.add(temp);
                        }
                        for (SectorEntityToken token : toRemove) token.getContainingLocation().removeEntity(token);
                        system.autogenerateHyperspaceJumpPoints(false, true);
                        break;
                    }
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
        
        x.alias("DefenceFleetAI", DefenceFleetAI.class);
        x.alias("InvasionFleetAI", InvasionFleetAI.class);
        x.alias("MiningFleetAI", MiningFleetAI.class);
        x.alias("RespawnFleetAI", RespawnFleetAI.class);
        x.alias("ResponseFleetAI", ResponseFleetAI.class);
    }
}
