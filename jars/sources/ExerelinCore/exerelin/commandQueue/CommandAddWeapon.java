package exerelin.commandQueue;

import com.fs.starfarer.api.campaign.CargoAPI;

@Deprecated
public class CommandAddWeapon implements BaseCommand
{
    private CargoAPI cargo;
    private String weaponId;
    private int count;

    public CommandAddWeapon(CargoAPI cargo, String weaponId, int count)
    {
        this.cargo = cargo;
        this.weaponId = weaponId;
        this.count = count;
    }

    @Override
    public void executeCommand()
    {
        cargo.addWeapons(weaponId, count);
    }
}
