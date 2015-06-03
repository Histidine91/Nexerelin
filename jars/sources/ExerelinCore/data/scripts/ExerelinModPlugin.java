package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.DirectoryScreenScript;
import exerelin.campaign.ExerelinSetupData;
import exerelin.campaign.SectorManager;
import exerelin.utilities.*;
import exerelin.world.InvasionFleetManager;
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
        // fix tariffs for new free port handling (0.37)
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy())
        {
            if (market.getTariff().getFlatMods().containsKey("generator"))
            {
                Global.getLogger(ExerelinModPlugin.class).info("Resetting tariffs for market " + market.getName());
                market.getTariff().unmodify("generator");
                if (market.hasCondition("free_market")) market.getTariff().modifyFlat("isFreeMarket", 0.1f);
                else market.getTariff().modifyFlat("isFreeMarket", 0.2f);
            }
        }
    }
    
    @Override
    public void onGameLoad() {
        ExerelinConfig.loadSettings();
        SectorManager.create();
        DiplomacyManager.create();
        InvasionFleetManager.create();
        ResponseFleetManager.create();
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
        DiplomacyManager.initFactionRelationships();
    }
}
