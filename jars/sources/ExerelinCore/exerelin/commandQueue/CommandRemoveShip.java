package exerelin.commandQueue;

import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

@Deprecated
public class CommandRemoveShip implements BaseCommand
{
    private CargoAPI cargo;
    private FleetMemberAPI fleetMemberAPI;

    public CommandRemoveShip(CargoAPI cargo, FleetMemberAPI fleetMemberAPI)
    {
        this.cargo = cargo;
        this.fleetMemberAPI = fleetMemberAPI;
    }

    @Override
    public void executeCommand()
    {
        cargo.getMothballedShips().removeFleetMember(fleetMemberAPI);
    }
}
