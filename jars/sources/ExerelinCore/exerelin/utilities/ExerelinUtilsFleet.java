package exerelin.utilities;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.DS_Defs;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.campaign.fleets.utils.DSFleetUtilsProxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;


public class ExerelinUtilsFleet
{
	public static final float DMOD_INCREASE_CHANCE = 0.4f;
	public static final float DMOD_REDUCE_CHANCE = 0.25f;
	
    public static Logger log = Global.getLogger(ExerelinUtilsFleet.class);
   
    /**
     * Used by Starsector Plus/DynaSector to create customized fleets
     * @param fleet
     * @param market
     * @param stability
     * @param qualityFactor
     * @param type 
     */
    public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {      
        if (ExerelinModPlugin.HAVE_DYNASECTOR)
            DSFleetUtilsProxy.injectFleet(fleet, market, stability, qualityFactor, type);
    }
    
    public static FleetMemberAPI addMiningShipToFleet(CampaignFleetAPI fleet)
    {
        String variantId = "shepherd_Frontier";
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId());
        if (config != null && config.miningVariantsOrWings != null && !config.miningVariantsOrWings.isEmpty()) 
            variantId = ExerelinUtils.getRandomListElement(config.miningVariantsOrWings);
        FleetMemberAPI miner = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
        fleet.getFleetData().addFleetMember(miner);
        return miner;
    }
    
    /**
     * Makes a fleet where larger fleets prefer big ships over small ones (taken from SS+)
     * Also includes Templar debris/derelicts chance reducer
     * @param faction
     * @param params
     * @return 
     */
    public static CampaignFleetAPI customCreateFleet(FactionAPI faction, FleetParamsV3 params) {
        int total = (int)(params.combatPts + params.tankerPts + params.freighterPts);
        CampaignFleetAPI fleet = null;
        
        if (ExerelinModPlugin.HAVE_DYNASECTOR) {
            fleet = DSFleetUtilsProxy.enhancedCreateFleet(faction, params, total);
        }
        else fleet = FleetFactoryV3.createFleet(params);
        
        if (fleet == null) return null;
        
        if (faction.getId().equals("templars"))
        {
            if (ExerelinModPlugin.HAVE_DYNASECTOR)
            {
                fleet.getStats().getDynamic().getMod(DS_Defs.STAT_BATTLE_DEBRIS_CHANCE).modifyMult("tem_spawner_nex", 0.5f);
                fleet.getStats().getDynamic().getMod(DS_Defs.STAT_BATTLE_DERELICTS_CHANCE).modifyMult("tem_spawner_nex", 0.25f);
                fleet.getStats().getDynamic().getMod(DS_Defs.STAT_FLEET_DERELICTS_CHANCE).modifyMult("tem_spawner_nex", 0f);
            }
            fleet.getCargo().addCommodity("tem_fluxcore", MathUtils.getRandomNumberInRange(total * 2, total * 3));
        }
        return fleet;
    }
    
    public static float getDaysToOrbit(CampaignFleetAPI fleet)
    {
        float daysToOrbit = 0.0F;
        if (fleet.getFleetPoints() <= 50.0F) {
            daysToOrbit = 2.0F;
        } else if (fleet.getFleetPoints() <= 100.0F) {
            daysToOrbit = 4.0F;
        } else if (fleet.getFleetPoints() <= 150.0F) {
            daysToOrbit = 6.0F;
        } else {
            daysToOrbit = 8.0F;
        }
        daysToOrbit *= (0.5F + (float)Math.random() * 0.5F);
        return daysToOrbit;
    }
    
    /**
     * Gets the number of fleet generation points represented by the specified fleet, as used in FleetFactoryV2.
     * Frigates = 1 point each, destroyers = 2, cruisers = 4, capitals = 8
     * @param fleet
     * @return
     */
    public static int getFleetGenPoints(CampaignFleetAPI fleet)
    {
        int points = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy())
        {
            points += getFleetGenPoints(member);
        }
        return points;
    }
    
    public static int getFleetGenPoints(FleetMemberAPI member)
    {
        ShipAPI.HullSize size = member.getHullSpec().getHullSize();
        switch (size) {
            case CAPITAL_SHIP:
                return 8;
            case CRUISER: 
                return 4;
            case DESTROYER: 
                return 2;
            case FIGHTER:
            case FRIGATE:
                return 1;
            default:
                return 1;
        }
    }
    
    // taken from SS+
    public static int calculatePowerLevel(CampaignFleetAPI fleet) {
        int power = fleet.getFleetPoints();
        for (FleetMemberAPI member : fleet.getFleetData().getCombatReadyMembersListCopy()) {
            if (member.isCivilian()) {
                power += member.getFleetPointCost() / 2;
            } else {
                power += member.getFleetPointCost();
            }
        }
        int offLvl = 0;
        int cdrLvl = 0;
        boolean commander = false;
        for (OfficerDataAPI officer : fleet.getFleetData().getOfficersCopy()) {
            if (officer.getPerson() == fleet.getCommander()) {
                commander = true;
                cdrLvl = officer.getPerson().getStats().getLevel();
            } else {
                offLvl += officer.getPerson().getStats().getLevel();
            }
        }
        if (!commander) {
            cdrLvl = fleet.getCommanderStats().getLevel();
        }
        power *= Math.sqrt(cdrLvl / 100f + 1f);
        int flatBonus = cdrLvl + offLvl + 10;
        if (power < flatBonus * 2) {
            flatBonus *= power / (float) (flatBonus * 2);
        }
        power += flatBonus;
        return power;
    }
	
	public static float getFleetStrength(CampaignFleetAPI fleet, boolean withHull, boolean withQuality, boolean withCaptain) {
		float str = 0;
		for (FleetMemberAPI member : fleet.getFleetData().getCombatReadyMembersListCopy()) {
			str += Misc.getMemberStrength(member, withHull, withQuality, withCaptain);
		}
		return str;
	};
    
    public static float getPlayerLevelFPBonus()
    {
        return Global.getSector().getPlayerPerson().getStats().getLevel() * ExerelinConfig.fleetBonusFpPerPlayerLevel;
    }
    
    public static List<CampaignFleetAPI> getAllFleetsInSector()
    {
        List<CampaignFleetAPI> fleets = new ArrayList<>();
        for (LocationAPI loc : Global.getSector().getAllLocations())
        {
            fleets.addAll(loc.getFleets());
        }
        return fleets;
    }
    
    public static String getFleetType(CampaignFleetAPI fleet)
    {
        if (!fleet.getMemoryWithoutUpdate().contains(MemFlags.MEMORY_KEY_FLEET_TYPE))
            return "";
        return fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
    }
    
    public static boolean isPlayerSeenAndIdentified(FactionAPI faction)
    {
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        if (playerFleet.isTransponderOn()) return true;
        for (CampaignFleetAPI fleet : playerFleet.getContainingLocation().getFleets()) {
            if (fleet.getFaction() != faction) continue;            
            
            if (!fleet.knowsWhoPlayerIs()) continue;
                
            VisibilityLevel level = playerFleet.getVisibilityLevelTo(fleet);
            if (level == VisibilityLevel.NONE || level == VisibilityLevel.SENSOR_CONTACT) continue;
            
            return true;
        }
        return false;
    }
	
	public static void addDMods(CampaignFleetAPI fleet, int level) {
		log.info("Adding D-mods to fleet, level " + level);
		if (level <= 0) return;
		Random random = new Random(ExerelinUtils.getStartingSeed());
		
		
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			// needed to make the effect stick between saves
			ShipVariantAPI v = member.getVariant().clone();
			v.setSource(VariantSource.REFIT);
			v.setHullVariantId(Misc.genUID());
			member.setVariant(v, false, false);
			
			int num = level;
			if (random.nextFloat() <= DMOD_INCREASE_CHANCE)
				num++;
			if (random.nextFloat() <= DMOD_REDUCE_CHANCE)
				num--;
			
			DModManager.addDMods(member, true, num, random);
		}
		
		fleet.getFleetData().setSyncNeeded();
		fleet.getFleetData().syncIfNeeded();
	}
}
