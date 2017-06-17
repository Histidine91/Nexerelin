package exerelin.utilities;


import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import data.scripts.util.DS_Defs;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.campaign.fleets.utils.DSFleetUtilsProxy;
import exerelin.campaign.fleets.utils.SWPFleetUtilsProxy;
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
    }
    
    public static FleetMemberAPI addMiningShipToFleet(CampaignFleetAPI fleet)
    {
        String variantId = "shepherd_Frontier";
        ExerelinFactionConfig config = ExerelinConfig.getExerelinFactionConfig(fleet.getFaction().getId());
        if (config != null && config.miningVariantsOrWings != null && !config.miningVariantsOrWings.isEmpty()) 
            variantId = (String) ExerelinUtils.getRandomListElement(config.miningVariantsOrWings);
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
    public static CampaignFleetAPI customCreateFleet(FactionAPI faction, FleetParams params) {
        int total = (int)(params.combatPts + params.tankerPts + params.freighterPts);
        CampaignFleetAPI fleet = null;
        
        if (ExerelinModPlugin.HAVE_DYNASECTOR) {
            fleet = DSFleetUtilsProxy.enhancedCreateFleet(faction, params, total);
        }
        else if (ExerelinModPlugin.HAVE_SWP) {
            fleet = SWPFleetUtilsProxy.enhancedCreateFleet(faction, params, total);
        }
        else fleet = FleetFactoryV2.createFleet(params);
        
        if (ExerelinModPlugin.HAVE_DYNASECTOR && faction.getId().equals("templars"))
        {
            fleet.getStats().getDynamic().getMod(DS_Defs.STAT_BATTLE_DEBRIS_CHANCE).modifyMult("tem_spawner_nex", 0.5f);
            fleet.getStats().getDynamic().getMod(DS_Defs.STAT_BATTLE_DERELICTS_CHANCE).modifyMult("tem_spawner_nex", 0.25f);
            fleet.getStats().getDynamic().getMod(DS_Defs.STAT_FLEET_DERELICTS_CHANCE).modifyMult("tem_spawner_nex", 0f);
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
}
