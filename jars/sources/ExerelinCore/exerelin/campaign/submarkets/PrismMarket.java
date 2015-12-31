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
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction.ShipSaleInfo;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtils;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PrismMarket extends BaseSubmarketPlugin {
    
    public static final String CONFIG_FILE = "data/config/exerelin/prism_boss_ships.csv";
    public static final String ILLEGAL_TRANSFER_MESSAGE = StringHelper.getString("exerelin_markets", "prismNoSale");
    
    protected static final int MIN_NUMBER_OF_SHIPS = 5;
    
    public static Logger log = Global.getLogger(PrismMarket.class);

    public Set<String> alreadyBoughtShips = new HashSet<>();
        
    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
        
        //loadBossShips();
    }
    
    public boolean canLoadShips(String factionId)
    {
        if (factionId.equals("ssp")) return ExerelinUtils.isSSPInstalled();
        return Global.getSector().getFaction(factionId) != null;
    }
    
    public List<String> getBossShips()
    {
        //if (sspBossShips == null)
        //    sspBossShips = new ArrayList<>();
		List<BossShipEntry> validShips = new ArrayList<>();
        List<String> ret = new ArrayList<>();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        
        int ibbProgress = 999;
		Set<Integer> stageCompletion = (Set<Integer>) Global.getSector().getPersistentData().get("ssp_famousBountyStageCompletion");
		if (stageCompletion == null) stageCompletion = new HashSet<>();
		
        if (ExerelinConfig.prismUseIBBProgressForBossShips)
        {
            if (!Global.getSector().getPersistentData().containsKey("ssp_famousBountyStage")) {
                ibbProgress = 0;
            }
            else
            {
                ibbProgress = (int) Global.getSector().getPersistentData().get("ssp_famousBountyStage");
            }
        }
        if (ibbProgress == -1) ibbProgress = 999;
        log.info("Current IBB progress: " + ibbProgress);
        int highestIBBNum = 0;
		
        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", CONFIG_FILE, "nexerelin");
            for(int i = 0; i < config.length(); i++)
            {
				JSONObject row = config.getJSONObject(i);
                String hullId = row.getString("id");
				String factionId = row.getString("faction");
                if (!canLoadShips(factionId)) continue;
                int ibbNum = row.getInt("ibbNum");
				validShips.add(new BossShipEntry(hullId, ibbNum));
				
				if (ibbNum > highestIBBNum)
					highestIBBNum = ibbNum;
			}
			
			// ensure proper emphasis on last ships once IBB sidequest is complete
			if (ibbProgress == 999)
				ibbProgress = highestIBBNum;
                    
			for(BossShipEntry entry : validShips)
			{
				// currently the completion stage is actually set when the bounty is created, not when it's completed
				if (entry.ibbNum > 0 && ExerelinConfig.prismUseIBBProgressForBossShips && !stageCompletion.contains(entry.ibbNum - 1))
				{
					log.info("IBB not completed for " + entry.id + " (" + entry.ibbNum + ")");
					continue;
				}
				
				//if (entry.ibbNum < ibbProgress) continue;
				
				//log.info("Ship " + id + " has IBB number " + ibbNum);
				if (!ExerelinConfig.prismRenewBossShips && alreadyBoughtShips.contains(entry.id))
					continue;

				// favour ships from bounties close to the last one we did
				int weight = 3;
				if (ExerelinConfig.prismUseIBBProgressForBossShips && entry.ibbNum > 0)
				{
					int diff = Math.abs(ibbProgress - entry.ibbNum);
					if (diff > 3) diff = 3;
					weight = weight + 4*(3 - diff);
				}
				picker.add(entry.id, weight);
			}
        } catch (IOException | JSONException ex) {
            log.error(ex);
        }
        
        for (int i=0; i<ExerelinConfig.prismNumBossShips; i++)
        {
            if (picker.isEmpty()) break;
            ret.add(picker.pickAndRemove());
        }
        
        return ret;
    }

    @Override
    public void updateCargoPrePlayerInteraction() {

        if (!okToUpdateCargo()) return;
        sinceLastCargoUpdate = 0f;
        
        CargoAPI cargo = getCargo();

        pruneWeapons(1f);
        
        float numFactions = ExerelinSetupData.getInstance().getAvailableFactions().size();
        float maxWeaponsPerFaction = ExerelinConfig.prismMaxWeaponsPerFaction;
        for ( float i=0; i < Math.max (20, maxWeaponsPerFaction*numFactions); i++) 
        {
            addRandomPrismWeapons(1, 2, 4);
        }
        addShips();
        cargo.sort();
    }

    protected void addShips() {
        
        CargoAPI cargo = getCargo();
        FleetDataAPI data = cargo.getMothballedShips();
        for (FleetMemberAPI member : data.getMembersListCopy()) {                
            data.removeFleetMember(member);                
        }

        WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();
        rolePicker.add(ShipRoles.CIV_RANDOM, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1f);
        rolePicker.add(ShipRoles.FREIGHTER_MEDIUM, 2f);
        rolePicker.add(ShipRoles.FREIGHTER_LARGE, 5f);
        rolePicker.add(ShipRoles.TANKER_SMALL, 1f);
        rolePicker.add(ShipRoles.TANKER_MEDIUM, 1f);
        rolePicker.add(ShipRoles.TANKER_LARGE, 2f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1f);
        rolePicker.add(ShipRoles.COMBAT_FREIGHTER_MEDIUM, 2f);
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
        SectorAPI sector = Global.getSector();
        for (String factionId: ExerelinSetupData.getInstance().getAvailableFactions()) {
            if (!factionId.equals("templars") && !factionId.equals("pirates") && !factionId.equals("sector") && !factionId.equals("everything")){
                factionPicker.add(sector.getFaction(factionId));
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
                    //&& !member.getHullId().toLowerCase().startsWith("tem_")
                    && member.getFleetPointCost()>=FP)
                {
                    member.getRepairTracker().setMothballed(true);
                    getCargo().getMothballedShips().addFleetMember(member);
                } else { 
                    i-=1;
                }
            }
        }
        List<String> bossShips = getBossShips();
        for (String variantId : bossShips)
        {
            variantId += "_Hull";
            FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
            member.getRepairTracker().setMothballed(true);
            getCargo().getMothballedShips().addFleetMember(member);
        }
    }
    
    protected void addRandomPrismWeapons(int max, int minTier, int maxTier) {
        CargoAPI cargo = getCargo();
        List<String> weaponIds = Global.getSector().getAllWeaponIds();
        
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        
        for (String id : weaponIds) {
            //if (id.startsWith("tem_")) continue;
            WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(id);
            int tier = spec.getTier();
            if (tier >= minTier && tier <= maxTier) {
                //float weaponQuality = 0.33f * (float) spec.getTier();
                //float qualityDiff = Math.abs(qualityFactor - weaponQuality);
                float qualityDiff = 0f;
                float f = Math.max(0, 1f - qualityDiff);
                float weight = Math.max(0.05f, f * f);
                picker.add(spec.getWeaponId(), weight);
            }
        }
        if (!picker.isEmpty()) {
            for (int i = 0; i < max; i++) {
                String weaponId = picker.pick();
                int quantity = 2;
                WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
                switch (spec.getSize()) {
                    case LARGE:
                        quantity = 1;
                        break;
                    case MEDIUM:
                        quantity = 2;
                        break;
                    case SMALL:
                        quantity = 3;
                        break;
                }
                cargo.addWeapons(weaponId, quantity);
            }    
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
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        List<ShipSaleInfo> shipsBought = transaction.getShipsBought();
        for (ShipSaleInfo saleInfo : shipsBought)
        {
            String hullId = saleInfo.getMember().getHullId();
            if (alreadyBoughtShips == null)
                alreadyBoughtShips = new HashSet<>();
            if (!alreadyBoughtShips.contains(hullId))
            {
                //log.info("Purchased boss ship " + hullId + "; will no longer appear");
                alreadyBoughtShips.add(hullId);
            }
        }
    }
    
    @Override
    public String getIllegalTransferText(FleetMemberAPI member, TransferAction action) {
        return ILLEGAL_TRANSFER_MESSAGE;
    }
    
    @Override
    public String getIllegalTransferText(CargoStackAPI stack, TransferAction action)
    {
        return ILLEGAL_TRANSFER_MESSAGE;
    }

    @Override
    public float getTariff() {
            return ExerelinConfig.prismTariff;
    }


    @Override
    public boolean isBlackMarket() {
            return false;
    }

	public static class BossShipEntry {
		public String id;
		public int ibbNum;
		
		public BossShipEntry(String id, int ibbNum)
		{
			this.id = id;
			this.ibbNum = ibbNum;
		}
	}
}