package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import data.scripts.world.exerelin.ExerelinSetupData;
import exerelin.SectorManager;
import exerelin.utilities.ExerelinConfig;

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
    }

    @Override
    public void onNewGame() {
        System.out.println("onNewGame");
        ExerelinSetupData.resetInstance();
        ExerelinConfig.loadSettings();
    }
}
