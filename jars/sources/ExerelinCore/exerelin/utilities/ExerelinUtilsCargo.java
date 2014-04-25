package exerelin.utilities;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;

import exerelin.ExerelinUtils;
import exerelin.SectorManager;
import exerelin.commandQueue.*;

public class ExerelinUtilsCargo
{
    public static void addFactionVariantsToCargo(CargoAPI cargo, String factionId, int number)
    {
        for(int i = 0; i < number; i++)
        {
            String variantId = "";
            FleetMemberType type = FleetMemberType.SHIP;

            if(ExerelinUtils.getRandomInRange(1, 5) == 1)
            {
                if(ExerelinUtils.getRandomInRange(1, 4) == 1)
                    variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(factionId, ExerelinUtilsFleet.ExerelinVariantType.MINING);
                else
                    variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(factionId, ShipAPI.HullSize.FIGHTER);
                type = FleetMemberType.FIGHTER_WING;
            }
            else
            {
                int rand = ExerelinUtils.getRandomInRange(1, 20);

                switch(rand)
                {
                    case 1:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(factionId, ExerelinUtilsFleet.ExerelinVariantType.SUPER_FREIGHTER);
                        break;
                    case 2:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(factionId, ExerelinUtilsFleet.ExerelinVariantType.CARRIER);
                        break;
                    case 3:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(factionId, ExerelinUtilsFleet.ExerelinVariantType.FREIGHTER);
                        break;
                    case 4:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(factionId, ExerelinUtilsFleet.ExerelinVariantType.TANKER);
                        break;
                    case 5:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionOfExerelinType(factionId, ExerelinUtilsFleet.ExerelinVariantType.TROOP_TRANSPORT);
                        break;
                    default:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFaction(factionId, false);
                        break;
                }

                type = FleetMemberType.SHIP;
            }

            FleetMemberAPI memberAPI = Global.getFactory().createFleetMember(type, variantId);

            String shipId = "";
            if(type == FleetMemberType.FIGHTER_WING)
                shipId = memberAPI.getSpecId();
            else if (type == FleetMemberType.SHIP)
                shipId = memberAPI.getHullId() + "_Hull";

            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddShip(cargo, memberAPI.getType(), shipId, null));
        }
    }

    public static void addFactionVariantsToCargo(CargoAPI cargo, String factionId, int number, Boolean includeLargeShips)
    {
        if(includeLargeShips)
            addFactionVariantsToCargo(cargo, factionId, number);
        else
        {
            for (int i = 0; i < number; i++)
            {
                int var = ExerelinUtils.getRandomInRange(1, 3);
                String variantId = "";
                FleetMemberType type = FleetMemberType.SHIP;
                switch(var)
                {
                    case 1:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(factionId, ShipAPI.HullSize.FIGHTER);
                        type = FleetMemberType.FIGHTER_WING;
                        break;
                    case 2:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(factionId, ShipAPI.HullSize.FRIGATE);
                        type = FleetMemberType.SHIP;
                        break;
                    case 3:
                        variantId = ExerelinUtilsFleet.getRandomVariantIdForFactionByHullsize(factionId, ShipAPI.HullSize.DESTROYER);
                        type = FleetMemberType.SHIP;
                        break;
                }

                FleetMemberAPI memberAPI = Global.getFactory().createFleetMember(type, variantId);

                String shipId = "";
                if(type == FleetMemberType.FIGHTER_WING)
                    shipId = memberAPI.getSpecId();
                else if (type == FleetMemberType.SHIP)
                    shipId = memberAPI.getHullId() + "_Hull";


                SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddShip(cargo, memberAPI.getType(), shipId, null));
            }
        }
    }

    public static void addFactionWeaponsToCargo(CargoAPI cargo, String factionId, int numberOfWeaponTypes, int numberOfEachWeapon)
    {
        String variantId = ExerelinUtilsFleet.getRandomVariantIdForFaction(factionId, false);
        FleetMemberAPI memberAPI = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);

        List weaponSlots = memberAPI.getVariant().getNonBuiltInWeaponSlots();

        ArrayList weaponList = new ArrayList(weaponSlots.size());

        for(int i = 0; i < weaponSlots.size(); i++)
            weaponList.add(memberAPI.getVariant().getWeaponId((String)weaponSlots.get(i)));

        if(weaponList.size() == 0)
            return; // TODO FIX

        for(int i = 0; i < numberOfWeaponTypes; i++)
        {
            String weaponId = (String)weaponList.get(ExerelinUtils.getRandomInRange(0, weaponList.size() - 1));
            cargo.addWeapons(weaponId, numberOfEachWeapon);
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddWeapon(cargo, weaponId, ExerelinUtils.getRandomInRange(1, numberOfEachWeapon)));
        }
    }
}
