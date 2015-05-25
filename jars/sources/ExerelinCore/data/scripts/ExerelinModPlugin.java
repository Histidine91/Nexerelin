package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.*;

public class ExerelinModPlugin extends BaseModPlugin
{
    @Override
    public void beforeGameSave()
    {
        //SectorManager.getCurrentSectorManager().getCommandQueue().executeAllCommands();
    }

    @Override
    public void onGameLoad()
    {
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        //ExerelinCheck.checkModCompatability();
    }

    @Override
    public void onNewGame() {
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
        //ExerelinCheck.checkModCompatability();
    }
}
