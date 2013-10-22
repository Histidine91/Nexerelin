package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import data.scripts.world.exerelin.ExerelinData;
import data.scripts.world.exerelin.SectorManager;

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
        ExerelinData.resetInstance();
        ExerelinData.getInstance().setSectorManager((SectorManager)Global.getSector().getPersistentData().get("SectorManager"));
    }

    @Override
    public void onNewGame() {
        System.out.println("onNewGame");
        ExerelinData.resetInstance();
    }
}
