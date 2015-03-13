package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.InvasionRound;
import exerelin.utilities.ExerelinConfig;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Handles invasion fleets (the ones that capture stations)
 * Based on Dark.Revenant's II_WarFleetManager
 */
public class InvasionFleetManager extends BaseCampaignEventListener implements EveryFrameScript
{
    private static final int MIN_MARINE_STOCKPILE_FOR_INVASION = 200;
    private static final float MAX_MARINE_STOCKPILE_TO_DEPLOY = 0.5f;
    private static final float DEFENDER_STRENGTH_FP_MULT = 0.3f;
    private static final float DEFENDER_STRENGTH_MARINE_MULT = 1.1f;
    
    public static Logger log = Global.getLogger(InvasionFleetManager.class);
    
    private final List<InvasionFleetData> activeFleets = new LinkedList();
    private int maxFleets;
    private final IntervalUtil tracker;
  
    public InvasionFleetManager()
    {
        super(true);
    
        float interval = Global.getSettings().getFloat("averagePatrolSpawnInterval");
        //interval = 2;   // debug
        this.tracker = new IntervalUtil(interval * 0.75F, interval * 1.25F);
        this.maxFleets = 20;
    }
    
    public static float calculateMaxFpForFleet(MarketAPI originMarket, MarketAPI targetMarket)
    {
        // old formula
        /*
        float marketScalar = originMarket.getSize() * originMarket.getStabilityValue();
        if (originMarket.hasCondition("military_base") || originMarket.hasCondition("exerelin_military_base")) {
            marketScalar += 20.0F;
        }
        if (originMarket.hasCondition("orbital_station")) {
            marketScalar += 10.0F;
        }
        if (originMarket.hasCondition("spaceport")) {
            marketScalar += 15.0F;
        }
        if (originMarket.hasCondition("headquarters")) {
            marketScalar += 15.0F;
        }
        if (originMarket.hasCondition("regional_capital")) {
            marketScalar += 10.0F;
        }
        int maxFP = (int)MathUtils.getRandomNumberInRange(marketScalar * 0.75F, MathUtils.getRandomNumberInRange(marketScalar * 1.5F, marketScalar * 2.5F));
        return maxFP;
        */
       
        // new; should be in the same general ballpark for an average case
        float responseFleetSize = ResponseFleetManager.getMaxReserveSize(targetMarket, true);
        float maxFPbase = (responseFleetSize * DEFENDER_STRENGTH_FP_MULT + 8 * (2 + originMarket.getSize()));
        maxFPbase = maxFPbase * (float)(0.5 + originMarket.getStabilityValue()/10);
        //maxFPbase *= 0.95;
        
        float maxFP = maxFPbase;
        if (originMarket.hasCondition("military_base")) maxFP += maxFPbase * 0.15;
        if (originMarket.hasCondition("orbital_station")) maxFP += maxFPbase * 0.05;
        if (originMarket.hasCondition("spaceport")) maxFP += maxFPbase * 0.05;
        if (originMarket.hasCondition("regional_capital")) maxFP += maxFPbase * 0.05;
        if (originMarket.hasCondition("headquarters")) maxFP += maxFPbase * 0.1;
        
        maxFP = maxFP + Global.getSector().getPlayerPerson().getStats().getLevel() * ExerelinConfig.fleetBonusFpPerPlayerLevel;
        
        return (maxFP * (MathUtils.getRandomNumberInRange(0.75f, 1f) + MathUtils.getRandomNumberInRange(0, 0.25f)));
    }
    
    public static InvasionFleetData spawnSupportFleet(FactionAPI invader, MarketAPI originMarket, MarketAPI targetMarket)
    {
        int maxFP = (int)(calculateMaxFpForFleet(originMarket, targetMarket) * 0.66f);
        float qf = originMarket.getShipQualityFactor();
        
        String name = "Strike Fleet";
        if (maxFP < 50) name = "Small " + name;
        else if (maxFP > 150) name = "Large " + name;
        CampaignFleetAPI fleet = FleetFactory.createGenericFleet(originMarket.getFactionId(), name, qf, maxFP);
        
        fleet.getMemoryWithoutUpdate().set("$fleetType", "exerelinInvasionSupportFleet");
        fleet.getMemoryWithoutUpdate().set("$maxFP", maxFP);
        fleet.getMemoryWithoutUpdate().set("$originMarket", originMarket);
        
        InvasionFleetData data = new InvasionFleetData(fleet);
        data.startingFleetPoints = fleet.getFleetPoints();
        data.sourceMarket = originMarket;
        data.source = originMarket.getPrimaryEntity();
        data.targetMarket = targetMarket;
        data.target = targetMarket.getPrimaryEntity();
        data.marineCount = 0;
        data.noWait = false;
        
        InvasionSupportFleetAI ai = new InvasionSupportFleetAI(fleet, data);
        fleet.addScript(ai);
        log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + maxFP);
        return data;
    }
  
    public static InvasionFleetData spawnFleet(FactionAPI invader, MarketAPI originMarket, MarketAPI targetMarket, boolean noWait)
    {
        float defenderStrength = InvasionRound.GetDefenderStrength(targetMarket);
        
        int maxFP = (int)calculateMaxFpForFleet(originMarket, targetMarket);
        float qf = originMarket.getShipQualityFactor();
        
        String name = "Invasion Fleet";
        if (maxFP < 50) name = "Small " + name;
        else if (maxFP > 150) name = "Grand " + name;
        CampaignFleetAPI fleet = FleetFactory.createGenericFleet(originMarket.getFactionId(), name, qf, maxFP);
        
        for (int i=0; i<defenderStrength/100; i++)
        {
            invader.pickShipAndAddToFleet(ShipRoles.PERSONNEL_MEDIUM, qf, fleet);
        }
        int marinesToSend = (int)(defenderStrength * DEFENDER_STRENGTH_MARINE_MULT);
        fleet.getCargo().addMarines(marinesToSend);
        
        fleet.getMemoryWithoutUpdate().set("$fleetType", "exerelinInvasionFleet");
        fleet.getMemoryWithoutUpdate().set("$maxFP", maxFP);
        fleet.getMemoryWithoutUpdate().set("$originMarket", originMarket);
        
        SectorEntityToken entity = originMarket.getPrimaryEntity();
        entity.getContainingLocation().addEntity(fleet);
        fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
        
        InvasionFleetData data = new InvasionFleetData(fleet);
        data.startingFleetPoints = fleet.getFleetPoints();
        data.sourceMarket = originMarket;
        data.source = originMarket.getPrimaryEntity();
        data.targetMarket = targetMarket;
        data.target = targetMarket.getPrimaryEntity();
        data.marineCount = marinesToSend;
        data.noWait = noWait;
        
        InvasionFleetAI ai = new InvasionFleetAI(fleet, data);
        fleet.addScript(ai);
        log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + maxFP);
        return data;
    }
    
    public void generateInvasionFleet()
    {
        SectorAPI sector = Global.getSector();
        WeightedRandomPicker<FactionAPI> factionPicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> sourcePicker = new WeightedRandomPicker();
        WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker();
        List<FactionAPI> factions = sector.getAllFactions();
        List<MarketAPI> markets = sector.getEconomy().getMarketsCopy();
        float marineStockpile = 0;
        //log.info("Starting invasion fleet check");
        
        // pick a faction to invade someone
        for (FactionAPI faction: factions)
        {
            if (faction.isNeutralFaction() || faction.isPlayerFaction()) continue;
            List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(faction, false);
            for (String otherFaction: enemies)
            {
                factionPicker.add(sector.getFaction(otherFaction));
                break;
            }
        }
        FactionAPI invader = factionPicker.pick();
        if (invader == null) return;
        //log.info("\t" + invader.getDisplayName() + " picked to launch invasion");
        
        // now pick source market
        for (MarketAPI market : markets) {
            if  ( market.getFaction() == invader && !market.hasCondition("decivilized") && 
                ( (market.hasCondition("spaceport")) || (market.hasCondition("orbital_station")) || (market.hasCondition("military_base"))
                    || (market.hasCondition("regional_capital")) || (market.hasCondition("headquarters"))
                ) && market.getSize() >= 3 )
            {
                marineStockpile = market.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();
                if (marineStockpile < MIN_MARINE_STOCKPILE_FOR_INVASION)
                        continue;
                float weight = marineStockpile;
                if (market.hasCondition("military_base")) {
                    weight *= 2.0F;
                }
                if (market.hasCondition("orbital_station")) {
                    weight *= 1.25F;
                }
                if (market.hasCondition("spaceport")) {
                    weight *= 1.5F;
                }
                if (market.hasCondition("headquarters")) {
                    weight *= 1.5F;
                }
                if (market.hasCondition("regional_capital")) {
                    weight *= 1.25F;
                }
                weight *= market.getSize() * market.getStabilityValue();
                sourcePicker.add(market, weight);
            }
        }
        MarketAPI originMarket = (MarketAPI)sourcePicker.pick();
        if (originMarket == null) {
            return;
        }
        //log.info("\tStaging from " + originMarket.getName());
        //marineStockpile = originMarket.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();
        
        // now we pick a target
        Vector2f originMarketLoc = originMarket.getLocationInHyperspace();
        List<String> pirateFactions = DiplomacyManager.getPirateFactionsCopy();
        for (MarketAPI market : markets) {
            if  ( market.getFaction().isHostileTo(invader) && !pirateFactions.contains(market.getFactionId()) )
            {
                /*
                float defenderStrength = InvasionRound.GetDefenderStrength(market);
                float estimateMarinesRequired = defenderStrength * 1.2f;
                if (estimateMarinesRequired > marineStockpile * MAX_MARINE_STOCKPILE_TO_DEPLOY)
                    continue;   // too strong for us
                */
                float dist = Misc.getDistance(market.getLocationInHyperspace(), originMarketLoc);
                if (dist < 5000.0F) {
                    dist = 5000.0F;
                }
                float weight = 20000.0F / dist;
                //weight *= market.getSize() * market.getStabilityValue();    // try to go after high value targets
                targetPicker.add(market, weight);
            }
        }
        MarketAPI targetMarket = (MarketAPI)targetPicker.pick();
        if (targetMarket == null) {
            return;
        }
        //log.info("\tTarget: " + targetMarket.getName());
        
        // okay, assemble battlegroup
        InvasionFleetData data = spawnFleet(invader, originMarket, targetMarket, false);
        this.activeFleets.add(data);
        if (originMarket.getSize() >= 4)
        {
            this.activeFleets.add(spawnSupportFleet(invader, originMarket, targetMarket));
            if (originMarket.getSize() >= 6)
            {
                this.activeFleets.add(spawnSupportFleet(invader, originMarket, targetMarket));
            }
        }
        
        Map<String, Object> params = new HashMap<>();
        params.put("target", targetMarket);
        params.put("dp", data.startingFleetPoints);
        Global.getSector().getEventManager().startEvent(new CampaignEventTarget(originMarket), "exerelin_invasion_fleet", params);
    }
  
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        this.tracker.advance(days);
        if (!this.tracker.intervalElapsed()) {
            return;
        }
        List<InvasionFleetData> remove = new LinkedList();
        for (InvasionFleetData data : this.activeFleets) {
            if ((data.fleet.getContainingLocation() == null) || (!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) || (!data.fleet.isAlive())) {
                remove.add(data);
            }
        }
        this.activeFleets.removeAll(remove);
    
        if (this.activeFleets.size() < this.maxFleets)
        {
            generateInvasionFleet();
        }
    }
  
    @Override
    public boolean isDone()
    {
        return false;
    }
  
    @Override
    public void reportFleetDespawned(CampaignFleetAPI fleet, CampaignEventListener.FleetDespawnReason reason, Object param)
    {
        super.reportFleetDespawned(fleet, reason, param);
        for (InvasionFleetData data : this.activeFleets) {
            if (data.fleet == fleet)
            {
                this.activeFleets.remove(data);
                break;
            }
        }
    }
  
    @Override
    public boolean runWhilePaused()
    {
        return false;
    }
  
    public static class InvasionFleetData
    {
        public CampaignFleetAPI fleet;
        public SectorEntityToken source;
        public SectorEntityToken target;
        public MarketAPI sourceMarket;
        public MarketAPI targetMarket;
        public float startingFleetPoints = 0.0F;
        public int marineCount = 0;
        public boolean noWait;
    
        public InvasionFleetData(CampaignFleetAPI fleet)
        {
            this.fleet = fleet;
        }
    }
}