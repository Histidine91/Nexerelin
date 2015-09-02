package exerelin.utilities;

import java.util.ArrayList;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;

import exerelin.SectorManager;
import exerelin.commandQueue.*;

public class ExerelinUtilsCargo
{
    public static void addCommodityStockpile(MarketAPI market, String commodityID, float amountToAdd)
    {
        CommodityOnMarketAPI commodity = market.getCommodityData(commodityID);
        
        commodity.addToStockpile(amountToAdd);
        commodity.addToAverageStockpile(amountToAdd);
        
        if (market.getFactionId().equals("templars"))
        {
            CargoAPI cargoTemplars = market.getSubmarket("tem_templarmarket").getCargo();
            cargoTemplars.addCommodity(commodityID, amountToAdd * 0.2f);
            return;
        }
        
        CargoAPI cargoOpen = market.getSubmarket(Submarkets.SUBMARKET_OPEN).getCargo();
        CargoAPI cargoBlack = cargoOpen;
        if (market.hasSubmarket(Submarkets.SUBMARKET_BLACK))
            cargoBlack = market.getSubmarket(Submarkets.SUBMARKET_BLACK).getCargo();
        CargoAPI cargoMilitary = null;
        if (market.hasSubmarket(Submarkets.GENERIC_MILITARY))
            cargoMilitary = market.getSubmarket(Submarkets.GENERIC_MILITARY).getCargo();
        
        if (commodityID.equals("agent") || commodityID.equals("saboteur"))
        {
            if (cargoMilitary != null)
            {
                cargoOpen.addCommodity(commodityID, amountToAdd * 0.02f);
                cargoMilitary.addCommodity(commodityID, amountToAdd * 0.11f);
                cargoBlack.addCommodity(commodityID, amountToAdd * 0.02f);
            }
            else
            {
                cargoOpen.addCommodity(commodityID, amountToAdd * 0.04f);
                cargoBlack.addCommodity(commodityID, amountToAdd * 0.11f);
            }
        }
        else if(!market.isIllegal(commodity))
            cargoOpen.addCommodity(commodityID, amountToAdd * 0.15f);
        else if (commodityID.equals("hand_weapons") && cargoMilitary != null)
        {
            cargoMilitary.addCommodity(commodityID, amountToAdd * 0.1f);
            cargoBlack.addCommodity(commodityID, amountToAdd * 0.05f);
        }
        else
            cargoBlack.addCommodity(commodityID, amountToAdd * 0.1f);
        //log.info("Adding " + amount + " " + commodityID + " to " + market.getName());
    }
    
    public static void addCommodityStockpile(MarketAPI market, String commodityID, float minMult, float maxMult)
    {
        float multDiff = maxMult - minMult;
        float mult = minMult + (float)(Math.random()) * multDiff;
        CommodityOnMarketAPI commodity = market.getCommodityData(commodityID);
        float demand = commodity.getDemand().getDemandValue();
        float amountToAdd = demand*mult;
        addCommodityStockpile(market, commodityID, amountToAdd);
    }
    
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
            String weaponId = (String) ExerelinUtils.getRandomListElement(weaponList);
            SectorManager.getCurrentSectorManager().getCommandQueue().addCommandToQueue(new CommandAddWeapon(cargo, weaponId, ExerelinUtils.getRandomInRange(1, numberOfEachWeapon)));
        }
    }
}
