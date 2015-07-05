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
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.StringHelper;
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
    private static final float RESERVE_INCREMENT_PER_DAY = 0.32f;
    private static final float RESERVE_MARKET_STABILITY_DIVISOR = 5f;
    private static final float INITIAL_RESERVE_SIZE_MULT = 0.75f;
    private static final float MIN_FP_TO_SPAWN = 20f;
    private Map<String, Float> reserves;
    
    public static Logger log = Global.getLogger(ResponseFleetManager.class);
    
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
        int enemyFP = ((CampaignFleetAPI)target).getFleetPoints();
        if (enemyFP > maxFP * 2)
        {
            log.info(target.getName() + " is more than twice our size: " + enemyFP);
            return;
        }
        
        float qf = origin.getShipQualityFactor();
        qf = Math.max(qf, 0.7f);
        
        String name = StringHelper.getString("exerelin_fleets", "responseFleetName");
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(origin.getFactionId());
        if (factionConfig != null)
        {
            name = factionConfig.responseFleetName;
        }
        if (maxFP < 70) name = StringHelper.getString("exerelin_fleets", "responseFleetPrefixSmall") + " " + name;
        else if (maxFP > 210) name = StringHelper.getString("exerelin_fleets", "responseFleetPrefixLarge") + " " + name;
        CampaignFleetAPI fleet = FleetFactory.createGenericFleet(origin.getFactionId(), name, qf, maxFP);
             
        fleet.getMemoryWithoutUpdate().set("$fleetType", "exerelinResponseFleet");
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
        if (market.hasCondition("regional_capital")) size += baseSize * 0.1;
        if (market.hasCondition("headquarters")) size += baseSize * 0.2;
        
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
        if (factionConfig != null)
        {
            size += baseSize * factionConfig.responseFleetSizeMod;
        }
        
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
            if (!reserves.containsKey(market.getId()))
                reserves.put(market.getId(), getMaxReserveSize(market, false)*INITIAL_RESERVE_SIZE_MULT);
            
            float baseIncrement = market.getSize() * (0.5f + (market.getStabilityValue()/RESERVE_MARKET_STABILITY_DIVISOR));
            float increment = baseIncrement;
            //if (market.hasCondition("regional_capital")) increment += baseIncrement * 0.1f;
            if (market.hasCondition("headquarters")) increment += baseIncrement * 0.25f;
            
            ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
            if (factionConfig != null)
            {
                increment += baseIncrement * factionConfig.responseFleetSizeMod;
            }
            
            increment = increment * RESERVE_INCREMENT_PER_DAY * days;
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
  
    
    float lastReserveUpdateAge = 0f;
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
        lastReserveUpdateAge += days;
        if (lastReserveUpdateAge > 0.5f)
        {
            lastReserveUpdateAge -= 0.5f;
            updateReserves(0.5f);
        }
        
        this.tracker.advance(days);
        if (this.tracker.intervalElapsed()) {
            return;
        }
        List<ResponseFleetData> remove = new LinkedList();
        for (ResponseFleetData data : this.activeFleets) {
            if ((data.fleet.getContainingLocation() == null) || (!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) || (!data.fleet.isAlive())) {
                remove.add(data);
            }
        }
        this.activeFleets.removeAll(remove);
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
                //log.info("Despawning response fleet " + fleet.getName());
                // return the ships to reserve if applicable - donut work
                /*
                if(reason != CampaignEventListener.FleetDespawnReason.DESTROYED_BY_FLEET
                        && data.sourceMarket.getFaction().getId().equals(data.fleet.getFaction().getId()))
                {
                    float fp = 0;
                    int numShips = 0;
                    List<FleetMemberAPI> snapshot = fleet.getFleetData().getSnapshot();
                    for (FleetMemberAPI member : snapshot) {
                        fp += member.getFleetPointCost();
                        numShips++;
                    }
                    //reserves.put(marketId, reserves.get(marketId) + fp);
                    modifyReserveSize(data.sourceMarket, fp);
                    log.info("Returning points to reserve: " + fp + " (from " + numShips + " ships)");
                }
                */
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