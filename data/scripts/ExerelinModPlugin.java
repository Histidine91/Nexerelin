package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import data.scripts.world.exerelin.ExerelinSetupData;
import exerelin.ExerelinUtils;
import exerelin.SectorManager;
import exerelin.utilities.*;

import java.awt.*;
import java.util.List;

public class ExerelinModPlugin extends BaseModPlugin
{
    @Override
    public void beforeGameSave()
    {
        System.out.println("beforeGameSave");
        SectorManager.getCurrentSectorManager().getCommandQueue().executeAllCommands();
    }

    @Override
    public void onGameLoad()
    {
        System.out.println("onGameLoad");
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        ExerelinCheck.checkModCompatability();
    }

    @Override
    public void onNewGame() {
        System.out.println("onNewGame");
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        ExerelinCheck.checkModCompatability();
    }
}
