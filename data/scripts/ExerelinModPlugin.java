package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import data.scripts.world.exerelin.SectorManager;

public class ExerelinModPlugin extends BaseModPlugin {

    @Override
    public void beforeGameSave()
    {
        // DISABLED AS THREADING IS DISABLED
        //System.out.println("Emptying command queue...");
        //SectorManager.getCurrentSectorManager().getCommandQueue().executeAllCommands();
    }

    @Override
    public void onGameLoad()
    {
        // DISABLED AS THREADING IS DISABLED
        //System.out.println("Emptying command queue...");
        //SectorManager.getCurrentSectorManager().getCommandQueue().executeAllCommands();
    }
}
