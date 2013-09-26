package data.scripts.world.exerelin.commandQueue;

import com.fs.starfarer.api.campaign.CargoAPI;

public class CommandAddCargo implements BaseCommand
{
    private CargoAPI cargo;
    private Object data;
    private CargoAPI.CargoItemType type;
    private float toAdd;

    public CommandAddCargo(CargoAPI cargo, Object stackData,
                              CargoAPI.CargoItemType stackType, float toAdd)
    {
        this.cargo = cargo;
        this.data = stackData;
        this.type = stackType;
        this.toAdd = toAdd;
    }

    @Override
    public void executeCommand()
    {
        cargo.addItems(type, data, toAdd);
    }
}
