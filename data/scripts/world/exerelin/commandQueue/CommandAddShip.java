package data.scripts.world.exerelin.commandQueue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

public class CommandAddShip implements BaseCommand
{
    private CargoAPI cargo;
    private FleetMemberType type;
    private String shipId;
    private String optionalName;

    public CommandAddShip(CargoAPI cargo, FleetMemberType type, String shipId, String optionalName)
    {
        this.cargo = cargo;
        this.type = type;
        this.shipId = shipId;
        this.optionalName = optionalName;
    }

    @Override
    public void executeCommand()
    {
        cargo.getMothballedShips().addFleetMember(Global.getFactory().createFleetMember(type, shipId));
        //cargo.addMothballedShip(type, shipId, optionalName);
    }
}
