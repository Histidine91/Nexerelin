package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.utilities.ExerelinConfig;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * When someone tries to invade our market, spawn a big freaking fleet to eat them
 */
public class ResponseFleetManager extends BaseCampaignEventListener implements EveryFrameScript
{
    public static final String MANAGER_MAP_KEY = "exerelin_responseFleetManager";
    public static final String RESERVE_SIZE_MAP_KEY = "exerelin_reserveFleetSize";
    private static final float RESERVE_INCREMENT_PER_DAY = 40f;
    private static final float RESERVE_MARKET_STABILITY_DIVISOR = 5f;
    private static final float INITIAL_RESERVE_SIZE_MULT = 0.75f;
    private static final float MIN_FP_TO_SPAWN = 20f;
    private Map<String, Float> reserves;
    
    public static Logger log = Global.getLogger(InvasionFleetManager.class);
    
    private final List<ResponseFleetData> activeFleets = new LinkedList();
    private final IntervalUtil tracker;
    
    private static ResponseFleetManager responseFleetManager;
  
    public ResponseFleetManager()
    {
        super(true);
    
        float interval = Global.getSettings().getFloat("averagePatrolSpawnInterval");
        //interval = 2;   // debug
        this.tracker = new IntervalUtil(interval * 0.75F, interval * 1.25F);
        
        //Map<String, Object> data = Global.getSector().getPersistentData();
        //reserves = (Map<String, Float>)data.get(RESERVE_SIZE_MAP_KEY);
        if (reserves == null)
        {
            reserves = new HashMap<>();
            List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
            for(MarketAPI market:markets)
                reserves.put(market.getId(), getMaxReserveSize(market, false)*INITIAL_RESERVE_SIZE_MULT);
            
            //data.put(RESERVE_SIZE_MAP_KEY, reserves);
        }
    }
  
    public void generateResponseFleet(MarketAPI origin, SectorEntityToken target)
    {
        float reserveSize = reserves.get(origin.getId());
        int maxFP = (int)reserveSize;
        if (maxFP < MIN_FP_TO_SPAWN)
        {
            log.info(origin.getName() + " has insufficient FP for response fleet: " + maxFP);
            return;
        }
        
        float qf = origin.getShipQualityFactor();
        
        String name = "Response Fleet";
        if (maxFP < 50) name = "Small " + name;
        CampaignFleetAPI fleet = FleetFactory.createGenericFleet(origin.getFactionId(), name, qf, maxFP);
             
        fleet.getMemoryWithoutUpdate().set("$fleetType", "exerelinInvasionFleet");
        fleet.getMemoryWithoutUpdate().set("$maxFP", maxFP);
        fleet.getMemoryWithoutUpdate().set("$originMarket", origin);
        
        SectorEntityToken entity = origin.getPrimaryEntity();
        entity.getContainingLocation().addEntity(fleet);
        fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
        
        ResponseFleetData data = new ResponseFleetData(fleet);
        data.startingFleetPoints = fleet.getFleetPoints();
        data.sourceMarket = origin;
        data.source = origin.getPrimaryEntity();
        data.target = target;
        this.activeFleets.add(data);
        
        ResponseFleetAI ai = new ResponseFleetAI(fleet, data);
        fleet.addScript(ai);
        log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + maxFP);
        reserves.put(origin.getId(), 0f);
    }
    
    public static float getMaxReserveSize(MarketAPI market, boolean raw)
    {
        float baseSize = market.getSize() * 20 + 30;
        float size = baseSize;
        if (raw) return size;
        
        if (market.hasCondition("military_base")) size += baseSize * 0.1;
        if (market.hasCondition("orbital_station")) size += baseSize * 0.05;
        if (market.hasCondition("spaceport")) size += baseSize * 0.05;
        if (market.hasCondition("regional_capital")) size += baseSize * 0.05;
        if (market.hasCondition("headquarters")) size += baseSize * 0.1;
        
        size = size + Global.getSector().getPlayerPerson().getStats().getLevel() * ExerelinConfig.fleetBonusFpPerPlayerLevel;
        
        return size;
    }
    
    public void updateReserves(float days)
    {
        // prevents NPE of unknown origin
        if (Global.getSector() == null || Global.getSector().getEconomy() == null)
            return;
        
        List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
        for(MarketAPI market:markets)
        {
            float increment = market.getSize() * (market.getStabilityValue()/RESERVE_MARKET_STABILITY_DIVISOR) * RESERVE_INCREMENT_PER_DAY * days;
            float newValue = Math.min(reserves.get(market.getId()) + increment, getMaxReserveSize(market, false));
            
            reserves.put(market.getId(), newValue);
        }
    }
    
    public static void requestResponseFleet(MarketAPI market, SectorEntityToken attacker)
    {
        if (responseFleetManager == null) return;
        responseFleetManager.generateResponseFleet(market, attacker);
    }
    
    public static float modifyReserveSize(MarketAPI market, float delta)
    {
        if (responseFleetManager == null) return 0f;
        String marketId = market.getId();
        if (!responseFleetManager.reserves.containsKey(marketId)) return 0f;
        float current = responseFleetManager.reserves.get(marketId);
        float newValue = current + delta;
        float max = getMaxReserveSize(market, false);
        if (newValue < 0) newValue = 0;
        else if (newValue > max) newValue = max;
        responseFleetManager.reserves.put(marketId, newValue);
        return newValue - current;
    }
    
    public static float getReserveSize(MarketAPI market)
    {
        if (responseFleetManager == null) return -1f;
        if (market == null) return -1f;
        String marketId = market.getId();
        Map<String, Float> reserves = responseFleetManager.reserves;
        // during post-battle loot it sends us a market with ID "fake_market"
        // so we have this check to prevent NPEs
        if (!reserves.containsKey(marketId))
        {
            //log.info("Get reserve size - failed to get key " + marketId);
            //log.info(reserves);
            return -1f;
        }
        return reserves.get(marketId);
    }
  
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        this.tracker.advance(days);
        if (!this.tracker.intervalElapsed()) {
            return;
        }
        List<ResponseFleetData> remove = new LinkedList();
        for (ResponseFleetData data : this.activeFleets) {
            if ((data.fleet.getContainingLocation() == null) || (!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) || (!data.fleet.isAlive())) {
                remove.add(data);
            }
        }
        this.activeFleets.removeAll(remove);
        
        updateReserves(days);
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
        for (ResponseFleetData data : this.activeFleets) {
            if (data.fleet == fleet)
            {
                // return the ships to reserve if applicable
                if(reason != CampaignEventListener.FleetDespawnReason.DESTROYED_BY_FLEET
                        && data.sourceMarket.getFaction() == data.fleet.getFaction())
                {
                    String marketId = data.sourceMarket.getId();
                    reserves.put(marketId, reserves.get(marketId) + fleet.getFleetPoints());
                }
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
    
    public static ResponseFleetManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        responseFleetManager = (ResponseFleetManager)data.get(MANAGER_MAP_KEY);
        if (responseFleetManager != null)
            return responseFleetManager;
        
        responseFleetManager = new ResponseFleetManager();
        data.put(MANAGER_MAP_KEY, responseFleetManager);
        return responseFleetManager;
    }
  
    public static class ResponseFleetData
    {
        public CampaignFleetAPI fleet;
        public SectorEntityToken source;
        public SectorEntityToken target;
        public MarketAPI sourceMarket;
        public float startingFleetPoints = 0.0F;
    
        public ResponseFleetData(CampaignFleetAPI fleet)
        {
            this.fleet = fleet;
        }
    }
}