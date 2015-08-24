package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DirectoryScreenScript;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.SectorManager;
import exerelin.utilities.*;
import exerelin.world.InvasionFleetManager;
import exerelin.world.MiningFleetManager;
import exerelin.world.ResponseFleetManager;
import org.lazywizard.omnifac.OmniFacSettings;

public class ExerelinModPlugin extends BaseModPlugin
{
    @Override
    public void beforeGameSave()
    {
        //SectorManager.getCurrentSectorManager().getCommandQueue().executeAllCommands();
    }

    @Override
    public void onNewGame() {
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        //ExerelinCheck.checkModCompatability();
    }
    
    protected void reverseCompatibility()
    {
        // fix using background from unreleased SS+ version; remove after new version is released
        for (StarSystemAPI system : Global.getSector().getStarSystems())
        {
            if (system.getBackgroundTextureFilename().equals("graphics/ssp/backgrounds/ssp_arcade.png"))
            {
                system.setBackgroundTextureFilename("graphics/ssp/backgrounds/ssp_randombattle.jpg");
            }
        }
        
        // fix factions that were accidentally set to be neutral to themselves
        for (FactionAPI faction : Global.getSector().getAllFactions()) 
        {
            faction.setRelationship(faction.getId(), 1);
        }
    }
    
    @Override
    public void onGameLoad() {
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
        
        reverseCompatibility();
        
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
    }

    @Override
    public void onNewGameAfterEconomyLoad() {
        SectorManager.reinitLiveFactions();
        
        if (SectorManager.getCorvusMode())
        {
            DiplomacyManager.initFactionRelationships(false);    // the mod factions set their own relationships, so we have to re-randomize if needed afterwards
            
            // fix Corvus mode tariffs
            for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
            {
                if (market.hasCondition("free_market")) 
                    market.getTariff().modifyMult("isFreeMarket", 0.5f);
            }
        }
    }
}
