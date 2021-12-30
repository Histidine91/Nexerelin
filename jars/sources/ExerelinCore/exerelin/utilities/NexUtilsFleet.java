package exerelin.utilities;


import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RouteFleetAssignmentAI;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.campaign.fleets.utils.DSFleetUtilsProxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.apache.log4j.Logger;


public class NexUtilsFleet
{
	public static final float DMOD_INCREASE_CHANCE = 0.4f;
	public static final float DMOD_REDUCE_CHANCE = 0.25f;
	
    public static Logger log = Global.getLogger(NexUtilsFleet.class);
   
    /**
     * Used by Starsector Plus/DynaSector to create customized fleets
     * @param fleet
     * @param market
     * @param stability
     * @param qualityFactor
     * @param type 
     */
    public static void injectFleet(CampaignFleetAPI fleet, MarketAPI market, Float stability, Float qualityFactor, String type) {      
        //if (ExerelinModPlugin.HAVE_DYNASECTOR)
        //    DSFleetUtilsProxy.injectFleet(fleet, market, stability, qualityFactor, type);
    }
    
    public static FleetMemberAPI addMiningShipToFleet(CampaignFleetAPI fleet)
    {
        String variantId = "shepherd_Frontier";
        NexFactionConfig config = NexConfig.getFactionConfig(fleet.getFaction().getId());
        if (config != null && config.miningVariantsOrWings != null && !config.miningVariantsOrWings.isEmpty()) 
            variantId = NexUtils.getRandomListElement(config.miningVariantsOrWings);
        FleetMemberAPI miner = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantId);
        
        // set correct CR
        miner.getCrewComposition().setCrew(miner.getNeededCrew());
        miner.updateStats();
        miner.getRepairTracker().setCR(miner.getRepairTracker().getMaxCR());
        
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
    
	// adapted from new Dark.Revenant algorithm, replacing older implementation from SS+
	/**
	 * Estimate of a fleet's strength based on fleet points, D/S-mods, officer levels and commander levels.
	 * @param fleet
	 * @return
	 */
	public static int calculatePowerLevel(CampaignFleetAPI fleet) {
		float power = 0;
		for (FleetMemberAPI member : fleet.getFleetData().getCombatReadyMembersListCopy()) {
			power = calculatePowerLevel(member);
		}
		
		// count commander skills
		int commanderSkills = 0;
		if (fleet.getCommanderStats() != null) {
			for (SkillLevelAPI level : fleet.getCommanderStats().getSkillsCopy()) {
				if (level.getSkill().isAdmiralSkill()) commanderSkills++;
			}
		}
		
		// account for DP dilution of commander skills
		int dpTotal = 0;
		float dpMult = 1;
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			dpTotal += member.getBaseDeployCost();
		}
		if (dpTotal > 240) {
			dpMult *= 240/dpTotal;
		}
		
		float commanderMult = 1 + (commanderSkills * 0.1f * dpMult);
		
		power *= commanderMult;
		return Math.round(power);
	}
	
	public static float calculatePowerLevel(FleetMemberAPI member) {
		float myPower = member.getFleetPointCost();
		if (member.isCivilian()) myPower /= 2;
		
		int numSkills = 0;
		int numElite = 0;
		if (member.getCaptain() != null) {
			for (SkillLevelAPI skillLevel : member.getCaptain().getStats().getSkillsCopy()) {
				numSkills++;
				if (skillLevel.getLevel() >= 2) numElite++;
			}
			if (member.getCaptain().isPlayer()) {
				numSkills *= 2;
				numElite *= 2;
			}
		}
		
		float officerMult = 1 + numSkills * 0.15f + numElite * 0.05f;
		
		float dModEffect = 0.06f;
		if (member.getHullSpec().getHints().contains(ShipTypeHints.CARRIER)) {
			dModEffect = 0.04f;
		}
		float dModMult = 1 - DModManager.getNumDMods(member.getVariant()) * dModEffect;
		
		float sModMult = 1 + member.getVariant().getSMods().size() * 0.12f;
		
		return myPower * dModMult * officerMult * sModMult;
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
        return Global.getSector().getPlayerPerson().getStats().getLevel() * NexConfig.fleetBonusFpPerPlayerLevel;
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
		Random random = new Random(NexUtils.getStartingSeed());
		
		
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
	
	public static RouteFleetAssignmentAI getRouteAssignmentAI(CampaignFleetAPI fleet) {
		for (EveryFrameScript script : fleet.getScripts()) {
			if (script instanceof RouteFleetAssignmentAI)
				return (RouteFleetAssignmentAI)script;
		}
		return null;
	}
}
