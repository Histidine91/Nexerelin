package exerelin.campaign.submarkets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemQuantity;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction.ShipSaleInfo;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.Highlights;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.campaign.ExerelinSetupData;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PrismMarket extends BaseSubmarketPlugin {

    //public static final int NUMBER_OF_SHIPS = 16;
    //public static final int MAX_WEAPONS = 27;
    public static final RepLevel MIN_STANDING = RepLevel.NEUTRAL;
    public static final String IBB_FILE = "data/config/prism/prism_boss_ships.csv";
    public static final String SHIPS_BLACKLIST = "data/config/prism/prism_ships_blacklist.csv";
    public static final String WEAPONS_BLACKLIST = "data/config/prism/prism_weapons_blacklist.csv";
    public static final String ILLEGAL_TRANSFER_MESSAGE = StringHelper.getString("exerelin_markets", "prismNoSale");
    public static final Set<String> DISALLOWED_FACTIONS = new HashSet<>(Arrays.asList(new String[] {
        "templars", Factions.DERELICT, Factions.REMNANTS, Factions.PIRATES
    }));
    
    public static Logger log = Global.getLogger(PrismMarket.class);
    
    protected static Set<String> restrictedWeapons;
    protected static Set<String> restrictedShips;
    
    public Set<String> alreadyBoughtShips = new HashSet<>();
    
    public static String getIBBFile() {
        return IBB_FILE;
    }
    
    @Override
    public void init(SubmarketAPI submarket) {
        super.init(submarket);
    }

    @Override
    public void updateCargoPrePlayerInteraction() {

        if (!okToUpdateCargo()) return;
        sinceLastCargoUpdate = 0f;
        
        //Setup blacklists
        try {
            setupLists();
        } catch (JSONException | IOException ex) {
            log.error(ex);
        }
        
        CargoAPI cargo = getCargo();

        //pruneWeapons(1f);
        for (CargoStackAPI s : cargo.getStacksCopy()) {
            if(Math.random()>0.5f){
                float qty = s.getSize();
                cargo.removeItems(s.getType(), s.getData(), qty );
                cargo.removeEmptyStacks();
            }
        }
        addShipsAndWings();
        addWeapons();
        cargo.sort();
    }
    
    public boolean isShipAllowed(FleetMemberAPI member, float requiredFP)
    {
        if (member.getHullSpec().isDHull()) return false;
        if (member.getFleetPointCost() < requiredFP) return false; //quality check
        if (restrictedShips.contains(member.getHullSpec().getBaseHullId())) return false;
        if (member.getHullSpec().getHints().contains(ShipTypeHints.STATION)) return false;
        
        return true;
    }
    
    protected void addWeapons()
    {
        CargoAPI cargo = getCargo();
        
        float variation=(float)Math.random()*0.5f+0.75f;
        for ( float i=0f; i < ExerelinConfig.prismMaxWeapons*variation; i = (float) cargo.getStacksCopy().size()) {
            addRandomWeapons(10, 3);
            for (CargoStackAPI s : cargo.getStacksCopy()) {
                //remove all low tier weapons
                if (s.isWeaponStack() 
                        && (s.getWeaponSpecIfWeapon().getTier()<2 
                            || s.getWeaponSpecIfWeapon().getWeaponId().startsWith("tem_") //templars are forbiden anyway
                            || restrictedWeapons.contains(s.getWeaponSpecIfWeapon().getWeaponId()) //blacklist check
                        )
                    ){
                    float qty = s.getSize();
                    cargo.removeItems(s.getType(), s.getData(), qty );
                    cargo.removeEmptyStacks();
                }
            }
        }
    }

    protected void addShipsAndWings() {
        
        CargoAPI cargo = getCargo();
        FleetDataAPI data = cargo.getMothballedShips();
        Set<String> allBossShips = getAllBossShips();
        
        //remove half the stock (and all boss ships)
        for (FleetMemberAPI member : data.getMembersListCopy()) {
            if (allBossShips.contains(member.getHullId())) data.removeFleetMember(member);
            else if (Math.random()>0.5f) data.removeFleetMember(member);                
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

        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker<>();
        SectorAPI sector = Global.getSector();
        for (String factionId: ExerelinSetupData.getInstance().getAllFactions()) {
            FactionAPI faction = sector.getFaction(factionId);
            if (!faction.isShowInIntelTab()) continue;
            //if (faction.isNeutralFaction()) continue;
            //if (faction.isPlayerFaction()) continue;
            if (DISALLOWED_FACTIONS.contains(factionId)) continue;
            factionPicker.add(sector.getFaction(factionId));
        }
        
        //renew the stock
        float variation=(float)Math.random()*0.5f+0.75f;
        for (int i=0; i<ExerelinConfig.prismNumShips*variation; i=cargo.getMothballedShips().getNumMembers()){
            //pick the role and faction
            FactionAPI faction = factionPicker.pick();
            String role = rolePicker.pick();            
            //pick the random ship
            List<ShipRolePick> picks = faction.pickShip(role, 1, rolePicker.getRandom());
            for (ShipRolePick pick : picks) {
                FleetMemberType type = FleetMemberType.SHIP;
                String variantId = pick.variantId; 
                                
                //set the ID
                FleetMemberAPI member = Global.getFactory().createFleetMember(type, variantId);
                variantId = member.getHullId() + "_Hull";
                member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
               
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
                if (isShipAllowed(member, FP))
                {
                    member.getRepairTracker().setMothballed(true);
                    getCargo().getMothballedShips().addFleetMember(member);
                } else { 
                    i-=1;
                }
            }
        }
        
        // add fighters
        WeightedRandomPicker<String> fighterPicker = new WeightedRandomPicker<>();
        for (String role : rolePicker.getItems()) {
            List<FighterWingSpecAPI> wings = getWingsOnRolePick(role, factionPicker);
            for (FighterWingSpecAPI spec : wings) {
                if (spec.getTier() < 2) continue;
                if (spec.getTier() >= 5) continue;
                fighterPicker.add(spec.getId());
            }
        }
        
        int picks = 0;
        for (CargoItemQuantity<String> quantity : cargo.getFighters())
        {
            picks += quantity.getCount();
        }
        while (!fighterPicker.isEmpty() && picks < ExerelinConfig.prismNumWings) {
            String id = fighterPicker.pick();        
            cargo.addItems(CargoAPI.CargoItemType.FIGHTER_CHIP, id, 1);
            picks++;
        }
        
        //add some IBBs
        List<String> bossShips = getBossShips();
        for (String variantId : bossShips)
        {
            try { 
                FleetMemberAPI member;
                if (variantId.endsWith("_wing")) {
                    getCargo().addFighters(variantId, 1);
                }
                else { 
                    variantId += "_Hull";
                    member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
                    member.getRepairTracker().setMothballed(true);
                    getCargo().getMothballedShips().addFleetMember(member);
                }
            } catch (RuntimeException rex) {
                // ship doesn't exist; meh
            }
        }
    }
    
    /**
     * Gets a set of all boss ships in the merged definition .csv
     * @return
     */
    public Set<String> getAllBossShips() {
        Set<String> bossShips = new HashSet<>();
        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", getIBBFile(), "nexerelin");
            for(int i = 0; i < config.length(); i++) {
            
                JSONObject row = config.getJSONObject(i);
                String hullId = row.getString("id");
                String factionId = row.getString("faction");
                
                if (!canLoadShips(factionId)) continue;
                bossShips.add(hullId);    
            }
        } catch (IOException | JSONException ex) {
            log.error(ex);
        }
        return bossShips;
    }
    

    /**
     * Gets a limited number of boss ships to add to the market stocks
     * @return
     */
    public List<String> getBossShips() {
        
        List<BossShipEntry> validShips = new ArrayList<>();
        List<String> ret = new ArrayList<>();
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
        
        int ibbProgress = 999;
        Set<Integer> stageCompletion = (Set<Integer>) Global.getSector().getPersistentData().get("ssp_famousBountyStageTrueCompletion");
        if (stageCompletion == null) stageCompletion = (Set<Integer>) Global.getSector().getPersistentData().get("ssp_famousBountyStageCompletion");
        if (stageCompletion == null) stageCompletion = new HashSet<>();
                
        //Check for SS+ IBBs
        if (!Global.getSector().getPersistentData().containsKey("ssp_famousBountyStage")) {
            ibbProgress = 0;
        } else {
            ibbProgress = (int) Global.getSector().getPersistentData().get("ssp_famousBountyStage");
        }
        
        if (ibbProgress == -1) ibbProgress = 999;
        log.info("Current IBB progress: " + ibbProgress);
        int highestIBBNum = 0;
        
        try {
            JSONArray config = Global.getSettings().getMergedSpreadsheetDataForMod("id", getIBBFile(), "nexerelin");
            for(int i = 0; i < config.length(); i++) {
            
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

            for(BossShipEntry entry : validShips) {
                if (ExerelinConfig.prismUseIBBProgressForBossShips) {
                    if (entry.ibbNum > 0 && !stageCompletion.contains(entry.ibbNum - 1)){
                        //log.info("IBB not completed for " + entry.id + " (" + entry.ibbNum + ")");
                        continue;
                    }
                }
                //ignore already bought IBB
                if (!ExerelinConfig.prismRenewBossShips && alreadyBoughtShips.contains(entry.id))
                    continue;

                // favour ships from bounties close to the last one we did
                int weight = 3;
                if (entry.ibbNum > 0){
                    int diff = Math.abs(ibbProgress - entry.ibbNum);
                    if (diff > 3) diff = 3;
                    weight = weight + 4*(3 - diff);
                }
                picker.add(entry.id, weight);
            }
        } catch (IOException | JSONException ex) {
            log.error(ex);
        }
        //How many IBB available
        for (int i=0; i<ExerelinConfig.prismNumBossShips; i++) {
            if (picker.isEmpty()) break;
            ret.add(picker.pickAndRemove());
        }
        return ret;
    }
    
    /**
     * Is this boss ship (as specified in CSV) available given our currently loaded mods?
     * @param factionOrModId
     * @return
     */
    public boolean canLoadShips(String factionOrModId) {
        if (factionOrModId == null) return true;
        if (factionOrModId.equals("ssp")) return ExerelinModPlugin.HAVE_SWP;    // legacy
        return Global.getSector().getFaction(factionOrModId) != null || Global.getSettings().getModManager().isModEnabled(factionOrModId);
    }
    
    //BLACKLISTS
    protected void setupLists() throws JSONException, IOException {

        // Restricted goods
        restrictedWeapons = new HashSet<>();
        restrictedShips = new HashSet<>();
        
        JSONArray csv = Global.getSettings().getMergedSpreadsheetDataForMod("id",
                WEAPONS_BLACKLIST, "nexerelin");
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            restrictedWeapons.add(row.getString("id"));
        }

        // Restricted ships
        csv = Global.getSettings().getMergedSpreadsheetDataForMod("id",
                SHIPS_BLACKLIST, "nexerelin");
        for (int x = 0; x < csv.length(); x++)
        {
            JSONObject row = csv.getJSONObject(x);
            restrictedShips.add(row.getString("id"));
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
    public float getTariff() {
        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        float mult = 1f;
        switch (level)
        {
            case NEUTRAL:
                mult = 1f;
                break;
            case FAVORABLE:
                mult = 0.9f;
                break;
            case WELCOMING:
                mult = 0.75f;
                break;
            case FRIENDLY:
                mult = 0.65f;
                break;
            case COOPERATIVE:
                mult = 0.5f;
                break;
            default:
                mult = 1f;
        }
        return mult * ExerelinConfig.prismTariff;
    }
    
    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        // record purchased ships
        List<ShipSaleInfo> shipsBought = transaction.getShipsBought();
        for (ShipSaleInfo saleInfo : shipsBought)
        {
            String id = saleInfo.getMember().getHullSpec().getBaseHullId();
            if (!alreadyBoughtShips.contains(id))
            {
                //log.info("Purchased boss ship " + hullId + "; will no longer appear");
                alreadyBoughtShips.add(id);
            }
        }
        
        // record purchased fighters
        CargoAPI otherBought = transaction.getBought();
        for (CargoItemQuantity<String> quantity : otherBought.getFighters())
        {
            String id = quantity.getItem();
            if (!alreadyBoughtShips.contains(id))
            {
                //log.info("Purchased boss wing " + id + "; will no longer appear");
                alreadyBoughtShips.add(id);
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
    public String getTooltipAppendix(CoreUIAPI ui)
    {
        if (!isEnabled(ui))
        {
            String msg = StringHelper.getString("exerelin_markets", "prismRelTooLow");
            msg = StringHelper.substituteToken(msg, "$faction", submarket.getFaction().getDisplayName());
            msg = StringHelper.substituteToken(msg, "$minRelationship", MIN_STANDING.getDisplayName().toLowerCase());
            return msg;
        }
        return null;
    }
    
    @Override
    public Highlights getTooltipAppendixHighlights(CoreUIAPI ui) {
        String appendix = getTooltipAppendix(ui);
        if (appendix == null) return null;
        
        Highlights h = new Highlights();
        h.setText(appendix);
        h.setColors(Misc.getNegativeHighlightColor());
        return h;
    }
    
    @Override
    public boolean isEnabled(CoreUIAPI ui)
    {
        RepLevel level = submarket.getFaction().getRelationshipLevel(Global.getSector().getFaction(Factions.PLAYER));
        return level.isAtWorst(MIN_STANDING);
    }

    @Override
    public boolean isBlackMarket() {
            return false;
    }
    
    //List IBBs and their progress
    public static class BossShipEntry {
        public String id;
        public int ibbNum;
        public BossShipEntry(String id, int ibbNum) {
                this.id = id;
                this.ibbNum = ibbNum;
        }
    }
}