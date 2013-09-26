package data.scripts.world.exerelin.commandQueue;

// Credit: LazyWizard

import com.fs.starfarer.api.campaign.CargoAPI;

public class CommandRemoveCargo implements BaseCommand
{
    private CargoAPI cargo;
    private Object data;
    private CargoAPI.CargoItemType type;
    private float toRemove;

    public CommandRemoveCargo(CargoAPI cargo, Object stackData,
                       CargoAPI.CargoItemType stackType, float toRemove)
    {
        this.cargo = cargo;
        this.data = stackData;
        this.type = stackType;
        this.toRemove = toRemove;
    }

    @Override
    public void executeCommand()
    {
        cargo.removeItems(type, data, toRemove);
    }
}
