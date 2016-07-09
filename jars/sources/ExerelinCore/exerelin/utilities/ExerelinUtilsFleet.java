package exerelin.utilities;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import data.scripts.ExerelinModPlugin;
import exerelin.campaign.fleets.DSFleetUtilsProxy;
import exerelin.campaign.fleets.SSPFleetUtilsProxy;
import exerelin.campaign.fleets.SWPFleetUtilsProxy;
import org.apache.log4j.Logger;


public class ExerelinUtilsFleet
{
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
        else if (ExerelinUtils.isSSPInstalled(true)) {
            SSPFleetUtilsProxy.injectFleet(fleet, market, stability, qualityFactor, type);
        }
    }
    
    public static FleetMemberAPI addMiningShipToFleet(CampaignFleetAPI fleet)
    {
        String variantId = "mining_drone_wing";
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId());
        if (config != null && config.miningVariantsOrWings != null && !config.miningVariantsOrWings.isEmpty()) 
            variantId = (String) ExerelinUtils.getRandomListElement(config.miningVariantsOrWings);
        FleetMemberType type = FleetMemberType.SHIP;
        if (variantId.contains("_wing")) type = FleetMemberType.FIGHTER_WING;
        FleetMemberAPI miner = Global.getFactory().createFleetMember(type, variantId);
        fleet.getFleetData().addFleetMember(miner);
        return miner;
    }
    
    /**
     * Makes a fleet where larger fleets prefer big ships over small ones (taken from SS+)
     * @param faction
     * @param params
     * @return 
     */
    public static CampaignFleetAPI createFleetWithSSPDoctrineHax(FactionAPI faction, FleetParams params) {
        int total = (int)(params.combatPts + params.tankerPts + params.freighterPts);
        
        if (ExerelinModPlugin.HAVE_SWP) {
            return SWPFleetUtilsProxy.enhancedCreateFleet(faction, params, total);
        }
        
        if (ExerelinModPlugin.HAVE_DYNASECTOR) {
            return DSFleetUtilsProxy.enhancedCreateFleet(faction, params, total);
        }
        
        if (ExerelinUtils.isSSPInstalled(true) ) {
            return SSPFleetUtilsProxy.enhancedCreateFleet(faction, params, total);
        }

        return FleetFactoryV2.createFleet(params);
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
}
