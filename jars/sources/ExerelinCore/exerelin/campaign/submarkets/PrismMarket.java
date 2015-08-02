/*
 * modified from SCY_prismMarket from Tartiflette's Scy
 * licensed as CC-BY-NC-SA 4.0
 */
package exerelin.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

public class PrismMarket extends BaseSubmarketPlugin {
    
    protected static final int MIN_NUMBER_OF_SHIPS = 5;
    protected static final int MAX_TRIES_WEAPONS = 5;
    
    public static Logger log = Global.getLogger(PrismMarket.class);

    public final List<String> SSP_BOSS_SHIPS = new ArrayList<>();
        
    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
		
		// this used to be static, but was moved here to help find a crash bug
		try {
			if (ExerelinUtils.isSSPInstalled())
			{
				SSP_BOSS_SHIPS.add("ssp_boss_aurora");
				SSP_BOSS_SHIPS.add("ssp_boss_falcon");
				SSP_BOSS_SHIPS.add("ssp_boss_hammerhead");
				SSP_BOSS_SHIPS.add("ssp_boss_hyperion");
				SSP_BOSS_SHIPS.add("ssp_boss_medusa");
				SSP_BOSS_SHIPS.add("ssp_boss_mule");
				SSP_BOSS_SHIPS.add("ssp_boss_odyssey");
				SSP_BOSS_SHIPS.add("ssp_boss_paragon");
				SSP_BOSS_SHIPS.add("ssp_boss_phaeton");
				SSP_BOSS_SHIPS.add("ssp_boss_sunder");
				SSP_BOSS_SHIPS.add("ssp_boss_tarsus");

				// maybe check for their respective mods as well?
				SSP_BOSS_SHIPS.add("syndicate_asp_boss_copperhead");
				SSP_BOSS_SHIPS.add("junk_pirates_boss_dugong");
			}
			SectorAPI sector = Global.getSector();
			if (sector.getFaction("shadow_industry") != null)
			{
				SSP_BOSS_SHIPS.add("ms_boss_mimir");
				SSP_BOSS_SHIPS.add("ms_boss_charybdis");
				SSP_BOSS_SHIPS.add("msp_boss_potniaBis");
			}
			if (sector.getFaction("templars") != null)
			{
				//SSP_BOSS_SHIPS.add("tem_boss_paladin");
			}
			if (sector.getFaction("interstellarimperium") != null)
			{
				SSP_BOSS_SHIPS.add("ii_boss_praetorian");
				SSP_BOSS_SHIPS.add("ii_boss_olympus");
			}
		} catch (Exception ex) {
			log.error(ex);
		}
    }

    @Override
    public void updateCargoPrePlayerInteraction() {

        if (!okToUpdateCargo()) return;
        sinceLastCargoUpdate = 0f;

        CargoAPI cargo = getCargo();

        pruneWeapons(1f);
        
        // add 10 weapons at a time
        // then prune cheapo ones
        // repeat until have at least prismMaxWeaponsPerFaction*numFactions OR 20 weapons, whichever is higher
        int tries = 0;
        float numFactions = Global.getSector().getAllFactions().size();
        float maxWeaponsPerFaction = ExerelinConfig.prismMaxWeaponsPerFaction;
        for ( float i=0f; i < Math.max (20, maxWeaponsPerFaction*numFactions); i = (float) cargo.getStacksCopy().size()) 
        {
            addRandomWeapons(10, 3);
            for (CargoStackAPI s : cargo.getStacksCopy()) {
                //remove all low tier weapons
                if (s.isWeaponStack() && (s.getWeaponSpecIfWeapon().getTier()<2 
                        || s.getWeaponSpecIfWeapon().getWeaponId().startsWith("tem_")))
                {
                    float qty = s.getSize();
                    cargo.removeItems(s.getType(), s.getData(), qty );
                    cargo.removeEmptyStacks();
                }
            }
            tries++;
            if (tries > MAX_TRIES_WEAPONS) break;
        }
        addShips();
        cargo.sort();
    }

    private void addShips() {
        
        CargoAPI cargo = getCargo();
        FleetDataAPI data = cargo.getMothballedShips();
        for (FleetMemberAPI member : data.getMembersListCopy()) {                
            data.removeFleetMember(member);                
        }

        WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();
        rolePicker.add(ShipRoles.CIV_RANDOM, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_MEDIUM, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_LARGE, 5f);
        rolePicker.add(ShipRoles.TANKER_SMALL, 1f);
        rolePicker.add(ShipRoles.TANKER_MEDIUM, 1f);
        rolePicker.add(ShipRoles.TANKER_LARGE, 1f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 1f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_LARGE, 5f);
        rolePicker.add(ShipRoles.COMBAT_SMALL, 25f);
        rolePicker.add(ShipRoles.COMBAT_MEDIUM, 30f);
        rolePicker.add(ShipRoles.COMBAT_LARGE, 25f);
        rolePicker.add(ShipRoles.COMBAT_CAPITAL, 15f);
        rolePicker.add(ShipRoles.CARRIER_SMALL, 5f);
        rolePicker.add(ShipRoles.CARRIER_MEDIUM, 5f);
        rolePicker.add(ShipRoles.CARRIER_LARGE, 5f);
        rolePicker.add(ShipRoles.INTERCEPTOR, 20f);
        rolePicker.add(ShipRoles.FIGHTER, 20f);
        rolePicker.add(ShipRoles.BOMBER, 20f);

        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker<>();
        for (FactionAPI faction: Global.getSector().getAllFactions()) {
            if (!faction.getId().equals("templars") && !faction.getId().equals("pirates")){
                factionPicker.add(faction);
            }
        }
        
        for (int i=0; i<MIN_NUMBER_OF_SHIPS + (Global.getSector().getAllFactions().size()*ExerelinConfig.prismNumShipsPerFaction); i++){
            //pick the role and faction
            FactionAPI faction = factionPicker.pick();
            String role = rolePicker.pick();            
            //pick the random ship
            List<ShipRolePick> picks = faction.pickShip(role, 1, rolePicker.getRandom());
            for (ShipRolePick pick : picks) {
                FleetMemberType type = FleetMemberType.SHIP;
                String variantId = pick.variantId;                
                //set the ID
                if (pick.isFighterWing()) {
                    type = FleetMemberType.FIGHTER_WING;
                } else {
                    FleetMemberAPI member = Global.getFactory().createFleetMember(type, pick.variantId);
                    variantId = member.getHullId() + "_Hull";
                }                
                //create the ship
                FleetMemberAPI member = Global.getFactory().createFleetMember(type, variantId);
               
                // Fleet point cost threshold
                int FP;
                if (member.isCapital()){
                    FP = 20;
                } else if (member.isCruiser()){
                    FP = 14;
                } else if (member.isDestroyer()){
                    FP = 10;
                } else if (member.isFrigate()){
                    FP = 5;
                } else {
                    FP = 6;
                }
                
                //if the variant is not degraded and high end, add it. Else start over
                if (member.getHullSpec().getBaseHullId() != null
                    && !member.getHullId().toLowerCase().endsWith("_d")
                    && !member.getHullId().toLowerCase().contains("_d_")
                    && !member.getHullSpec().getHullName().toLowerCase().endsWith("(d)")
                    && member.getFleetPointCost()>=FP)
                {
                    member.getRepairTracker().setMothballed(true);
                    getCargo().getMothballedShips().addFleetMember(member);
                } else { 
                    i-=1;
                }
            }
        }
        if (ExerelinConfig.prismSellBossShips && !SSP_BOSS_SHIPS.isEmpty())
        {
            String variantId = SSP_BOSS_SHIPS.get(MathUtils.getRandomNumberInRange(0, SSP_BOSS_SHIPS.size()-1));
            variantId += "_Hull";
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            member.getRepairTracker().setMothballed(true);
            getCargo().getMothballedShips().addFleetMember(member);
        }
    }


    @Override
    public boolean isIllegalOnSubmarket(CargoStackAPI stack, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) return true;
        return false;
    }


    @Override
    public boolean isIllegalOnSubmarket(String commodityId, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) return true;
        return false;
    }
    
    @Override
    public boolean isIllegalOnSubmarket(FleetMemberAPI member, TransferAction action) {
        if (action == TransferAction.PLAYER_SELL) return true;
        return false;
    }
    
    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return "No sales/returns";
    }
    
    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action)
    {
        return "No sales/returns";
    }

    @Override
    public float getTariff() {
            return ExerelinConfig.prismTariff;
    }


    @Override
    public boolean isBlackMarket() {
            return false;
    }

}