package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
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
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Handles invasion fleets (the ones that capture stations)
 * Originally derived from Dark.Revenant's II_WarFleetManager
 */
public class InvasionFleetManager extends BaseCampaignEventListener implements EveryFrameScript
{
    public static final String MANAGER_MAP_KEY = "exerelin_invasionFleetManager";
    
    public static final int MIN_MARINE_STOCKPILE_FOR_INVASION = 200;
    public static final float MAX_MARINE_STOCKPILE_TO_DEPLOY = 0.5f;
    public static final float DEFENDER_STRENGTH_FP_MULT = 0.3f;
    public static final float DEFENDER_STRENGTH_MARINE_MULT = 1.15f;
    public static final float RESPAWN_FLEET_SPAWN_DISTANCE = 18000f;
    public static final float HOSTILE_TO_ALL_INVASION_POINT_MOD = 0.4f;
    public static final float HOSTILE_TO_ALL_INVASION_TARGET_MOD = 0.25f;
    public static final int MAX_FLEETS = 50;
    
    public static Logger log = Global.getLogger(InvasionFleetManager.class);
    
    protected final List<InvasionFleetData> activeFleets = new LinkedList();
    protected HashMap<String, Float> spawnCounter = new HashMap<>();
    
    private final IntervalUtil tracker;
    
    protected float daysElapsed = 0;
    
    private static InvasionFleetManager invasionFleetManager;
  
    public InvasionFleetManager()
    {
        super(true);
    
        float interval = 8; //Global.getSettings().getFloat("averagePatrolSpawnInterval");
        //interval = 2;   // debug
        //this.tracker = new IntervalUtil(interval * 0.75F, interval * 1.25F);
        this.tracker = new IntervalUtil(1, 1);
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
        
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(originMarket.getFactionId());
        if (factionConfig != null)
        {
            maxFP += maxFPbase * factionConfig.invasionFleetSizeMod;
        }
        
        maxFP = maxFP + Global.getSector().getPlayerPerson().getStats().getLevel() * ExerelinConfig.fleetBonusFpPerPlayerLevel;
        
        return (maxFP * (MathUtils.getRandomNumberInRange(0.75f, 1f) + MathUtils.getRandomNumberInRange(0, 0.25f)));
    }
    
    public static InvasionFleetData spawnFleet(FleetSpawnParams params)
    {
        FactionAPI faction = params.faction;
        CampaignFleetAPI fleet = FleetFactory.createGenericFleet(faction.getId(), params.name, params.qf, params.fp);
        for (int i=0; i<params.numMarines; i=i+100)
        {
            faction.pickShipAndAddToFleet(ShipRoles.PERSONNEL_MEDIUM, 1, fleet);
        }
        fleet.getCargo().addMarines(params.numMarines);
        
        fleet.getMemoryWithoutUpdate().set("$fleetType", params.fleetType);
        fleet.getMemoryWithoutUpdate().set("$maxFP", params.fp);
        fleet.getMemoryWithoutUpdate().set("$originMarket", params.originMarket);
        
        InvasionFleetData data = new InvasionFleetData(fleet);
        data.startingFleetPoints = fleet.getFleetPoints();
        data.sourceMarket = params.originMarket;
        data.source = params.originMarket.getPrimaryEntity();
        data.targetMarket = params.targetMarket;
        data.target = params.targetMarket.getPrimaryEntity();
        data.marineCount = params.numMarines;
        data.noWait = params.noWait;
        data.noWander = params.noWander;
        
        if (params.spawnLoc == null) params.spawnLoc = params.originMarket.getContainingLocation();
        params.spawnLoc.addEntity(fleet);
        if (params.jumpToOrigin)
        {
            SectorEntityToken entity = params.originMarket.getPrimaryEntity();
            fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
        }
        
        // add AI script
        EveryFrameScript ai = null;
        if (params.fleetType.equals("exerelinInvasionFleet"))
        {
            ai = new InvasionFleetAI(fleet, data);
        }
        else if (params.fleetType.equals("exerelinDefenceFleet"))
        {
            ai = new DefenceFleetAI(fleet, data);
        }
        else if (params.fleetType.equals("exerelinInvasionSupportFleet"))
        {
            ai = new InvasionSupportFleetAI(fleet, data);
        }
        fleet.addScript(ai);
        /*
        try {
            Class<?> aiClass = Class.forName(params.aiClass);
            Constructor<?> aiConstructor = aiClass.getConstructor(fleet.getClass(), data.getClass());
            fleet.addScript((EveryFrameScript)aiConstructor.newInstance(fleet, data));
        } catch (Exception ex)
        {
            log.error(ex);
        }
        */
        
        if (invasionFleetManager != null)
            invasionFleetManager.activeFleets.add(data);
        
        return data;
    }
    
    public static String getFleetName(String fleetType, String factionId, float fp)
    {
        String name = "Fleet";
        ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
        
        if (fleetType.equals("exerelinInvasionFleet"))
        {
            name = "Invasion Fleet";
            if (factionConfig != null)
            {
                name = factionConfig.invasionFleetName;
            }
            if (fp < 70) name = "Small " + name;
            else if (fp >= 210) name = "Grand " + name;
        }
        else if (fleetType.equals("exerelinInvasionSupportFleet"))
        {
            name = "Strike Fleet";
            if (factionConfig != null)
            {
                name = factionConfig.invasionSupportFleetName;
            }
            if (fp < 60) name = "Small " + name;
            else if (fp >= 180) name = "Large " + name;
        }
        else if (fleetType.equals("exerelinDefenceFleet"))
        {
            name = "Defence Fleet";
            if (factionConfig != null)
            {
                name = factionConfig.defenceFleetName;
            }
            if (fp < 70) name = "Small " + name;
            else if (fp >= 210) name = "Large " + name;
        }
        
        return name;
    }
    
    public static InvasionFleetData spawnRespawnFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket)
    {
        float defenderStrength = InvasionRound.GetDefenderStrength(targetMarket, 1f, false);
        float responseFleetSize = ResponseFleetManager.getMaxReserveSize(targetMarket, false);
        float maxFPbase = (responseFleetSize * DEFENDER_STRENGTH_FP_MULT + 8 * (2 + originMarket.getSize()));
        float maxFP = maxFPbase + Global.getSector().getPlayerPerson().getStats().getLevel() * ExerelinConfig.fleetBonusFpPerPlayerLevel;
        maxFP *= MathUtils.getRandomNumberInRange(0.75f, 1f) + MathUtils.getRandomNumberInRange(0, 0.25f);
        maxFP *= 1.25;
        
        String name = getFleetName("exerelinInvasionFleet", faction.getId(), maxFP);
        
        LocationAPI spawnLoc = Global.getSector().getHyperspace();
        if (Global.getSector().getStarSystems().size() == 1)    // one-star start; target will be inaccessible from hyper
        {
            SectorEntityToken entity = originMarket.getPrimaryEntity();
            spawnLoc = entity.getContainingLocation();
        }
        
        FleetSpawnParams params = new FleetSpawnParams();
        params.name = name;
        params.fleetType = "exerelinInvasionFleet";
        params.faction = faction;
        params.fp = (int)maxFP;
        params.qf = 1;
        params.originMarket = originMarket;
        params.targetMarket = targetMarket;
        params.spawnLoc = spawnLoc;
        params.jumpToOrigin = false;
        params.noWait = true;
        //params.aiClass = RespawnFleetAI.class.getName();
        
        InvasionFleetData fleetData = spawnFleet(params);
        float distance = RESPAWN_FLEET_SPAWN_DISTANCE;
        float angle = MathUtils.getRandomNumberInRange(0, 359);
        fleetData.fleet.setLocation((float)Math.cos(angle) * distance, (float)Math.sin(angle) * distance);
        log.info("\tSpawned respawn fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
        return fleetData;
    }
    
    public static InvasionFleetData spawnDefenceFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, boolean noWander, boolean noWait)
    {
        int maxFP = (int)(calculateMaxFpForFleet(originMarket, targetMarket) * 0.66f);
        float qf = originMarket.getShipQualityFactor();
        qf = Math.max(qf, 0.7f);
        
        String name = getFleetName("exerelinDefenseFleet", faction.getId(), maxFP);
        
        FleetSpawnParams params = new FleetSpawnParams();
        params.name = name;
        params.fleetType = "exerelinDefenseFleet";
        params.faction = faction;
        params.fp = (int)maxFP;
        params.qf = qf;
        params.originMarket = originMarket;
        params.targetMarket = targetMarket;
        params.noWander = noWander;
        params.noWait = noWait;
        //params.aiClass = InvasionSupportFleetAI.class.getName();
        
        InvasionFleetData fleetData = spawnFleet(params);
        log.info("\tSpawned defence fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
        return fleetData;
    }
    
    public static InvasionFleetData spawnSupportFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, boolean noWander, boolean noWait)
    {
        int maxFP = (int)(calculateMaxFpForFleet(originMarket, targetMarket) * 0.66f);
        float qf = originMarket.getShipQualityFactor();
        qf = Math.max(qf, 0.7f);
        
        String name = getFleetName("exerelinInvasionSupportFleet", faction.getId(), maxFP);

        FleetSpawnParams params = new FleetSpawnParams();
        params.name = name;
        params.fleetType = "exerelinInvasionSupportFleet";
        params.faction = faction;
        params.fp = (int)maxFP;
        params.qf = qf;
        params.originMarket = originMarket;
        params.targetMarket = targetMarket;
        params.noWander = true;
        params.noWait = noWait;
        //params.aiClass = InvasionSupportFleetAI.class.getName();
        
        InvasionFleetData fleetData = spawnFleet(params);
        log.info("\tSpawned strike fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
        return fleetData;
    }
    
    public static InvasionFleetData spawnInvasionFleet(FactionAPI faction, MarketAPI originMarket, MarketAPI targetMarket, float marineMult, boolean noWait)
    {
        float defenderStrength = InvasionRound.GetDefenderStrength(targetMarket, 0.5f, false);
        
        int maxFP = (int)calculateMaxFpForFleet(originMarket, targetMarket);
        float qf = originMarket.getShipQualityFactor();
        qf = Math.max(qf, 0.7f);
        
        String name = getFleetName("exerelinInvasionFleet", faction.getId(), maxFP);
                
        FleetSpawnParams params = new FleetSpawnParams();
        params.name = name;
        params.fleetType = "exerelinInvasionFleet";
        params.faction = faction;
        params.fp = (int)maxFP;
        params.qf = qf;
        params.originMarket = originMarket;
        params.targetMarket = targetMarket;
        params.noWait = noWait;
        //params.aiClass = InvasionFleetAI.class.getName();
        params.numMarines = (int)(defenderStrength * marineMult);
        
        InvasionFleetData fleetData = spawnFleet(params);
        log.info("\tSpawned invasion fleet " + fleetData.fleet.getNameWithFaction() + " of size " + maxFP);
        return fleetData;
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
        
        boolean allowPirates = ExerelinConfig.allowPirateInvasions;
        
        // pick a faction to invade someone
        
        // old random way meh
        /*
        for (FactionAPI faction: factions)
        {
            if (faction.isNeutralFaction()) continue;
            if (faction.isPlayerFaction()) continue;
            if (!allowPirates && ExerelinUtilsFaction.isPirateFaction(faction.getId())) continue;
            List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(faction, ExerelinConfig.allowPirateInvasions, true);

            if (enemies.isEmpty()) continue;
            int numWars = enemies.size();
            
            float weight = 1;
            if (faction.getId().equals("templars")) // TODO don't hardcode faction
            {
                weight = numWars*HOSTILE_TO_ALL_INVASION_POINT_MOD + (1 - HOSTILE_TO_ALL_INVASION_POINT_MOD);
            }
            else if (numWars == 1 && enemies.get(0).equals("templars"))
            {
                weight = HOSTILE_TO_ALL_INVASION_TARGET_MOD;
            }
            factionPicker.add(faction, weight);
        }
        FactionAPI invader = factionPicker.pick();
        if (invader == null) return;
        //log.info("\t" + invader.getDisplayName() + " picked to launch invasion");
        */
        
        // new "everyone gets a chance way"
        if (spawnCounter == null) spawnCounter = new HashMap<>();
        HashMap<String, Float> pointsPerFaction = new HashMap<>();
        for (MarketAPI market : markets)
        {
            String factionId = market.getFactionId();
            if (!pointsPerFaction.containsKey(factionId))
                pointsPerFaction.put(factionId, 0f);
            
            float points = pointsPerFaction.get(factionId);
            points += market.getSize() * market.getStabilityValue();
            pointsPerFaction.put(factionId, points);
        }
        
        List<String> liveFactionIds = SectorManager.getLiveFactionIdsCopy();
        for (String factionId: liveFactionIds)
        {
            FactionAPI faction = sector.getFaction(factionId);
            if (faction.isNeutralFaction()) continue;
            if (faction.isPlayerFaction()) continue;
            boolean isPirateFaction = ExerelinUtilsFaction.isPirateFaction(factionId);
            if (!allowPirates && isPirateFaction) continue;
            
            float mult = 0f;
            List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(faction, ExerelinConfig.allowPirateInvasions, true);
            if (enemies.isEmpty()) continue;
            
            
            if (faction.getId().equals("templars") || isPirateFaction) // TODO don't hardcode faction
            {
                float numWars = enemies.size();
                numWars = (float)Math.sqrt(numWars);
                mult = numWars*HOSTILE_TO_ALL_INVASION_POINT_MOD + (1 - HOSTILE_TO_ALL_INVASION_POINT_MOD);
            }
            else
            {
                for (String enemyId : enemies)
                {
                    if (enemyId.equals("templars") || ExerelinUtilsFaction.isPirateFaction(enemyId))
                    {
                        float enemyWars = DiplomacyManager.getFactionsAtWarWithFaction(enemyId, ExerelinConfig.allowPirateInvasions, true).size();
                        enemyWars = (float)Math.sqrt(enemyWars);
                        if (enemyWars > 0 )
                            mult += 1/((enemyWars*HOSTILE_TO_ALL_INVASION_POINT_MOD) + (1 - HOSTILE_TO_ALL_INVASION_POINT_MOD));
                    }
                    else mult +=1;
                }
                if (mult > 1) mult = 1;
            }
            
            // increment invasion counter
            if (!spawnCounter.containsKey(factionId))
                spawnCounter.put(factionId, 0f);
            
            float counter = spawnCounter.get(factionId);
            float increment = pointsPerFaction.get(factionId) + ExerelinConfig.baseInvasionPointsPerFaction;
            increment *= mult * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
            counter += increment;
            
            if (counter < ExerelinConfig.pointsRequiredForInvasionFleet)
            {
                spawnCounter.put(factionId, counter);
                continue;   // done here
            }
            
            // okay, we can invade
            counter -= ExerelinConfig.pointsRequiredForInvasionFleet;
            spawnCounter.put(factionId, counter);
            FactionAPI invader = faction;
            
            // pick a source market
            for (MarketAPI market : markets) {
                if  ( market.getFactionId().equals(invader.getId()) && !market.hasCondition("decivilized") && 
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
            MarketAPI originMarket = sourcePicker.pick();
            if (originMarket == null) {
                continue;
            }
            //log.info("\tStaging from " + originMarket.getName());
            //marineStockpile = originMarket.getCommodityData(Commodities.MARINES).getAverageStockpileAfterDemand();

            // now we pick a target
            Vector2f originMarketLoc = originMarket.getLocationInHyperspace();
            for (MarketAPI market : markets) 
            {
                FactionAPI marketFaction = market.getFaction();
                if  ( marketFaction.isHostileTo(invader)) 
                {
                    if (marketFaction.getId().equals("independent")) continue;
                    if (!allowPirates && ExerelinUtilsFaction.isPirateFaction(marketFaction.getId()))
                        continue;
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
                    if (marketFaction.getId().equals("templars") || ExerelinUtilsFaction.isPirateFaction(marketFaction.getId())) 
                        weight *= HOSTILE_TO_ALL_INVASION_TARGET_MOD;

                    targetPicker.add(market, weight);
                }
            }
            MarketAPI targetMarket = targetPicker.pick();
            if (targetMarket == null) {
                continue;
            }
            //log.info("\tTarget: " + targetMarket.getName());

            // okay, assemble battlegroup
            InvasionFleetData data = spawnInvasionFleet(invader, originMarket, targetMarket, DEFENDER_STRENGTH_MARINE_MULT, false);
            spawnSupportFleet(invader, originMarket, targetMarket, false, false);
            spawnSupportFleet(invader, originMarket, targetMarket, false, false);

            /*
            if (originMarket.getSize() >= 4)
            {
                this.activeFleets.add(spawnSupportFleet(invader, originMarket, targetMarket));
                //this.activeFleets.add(spawnSupportFleet(invader, originMarket, targetMarket));
                if (originMarket.getSize() >= 6)
                {
                    this.activeFleets.add(spawnSupportFleet(invader, originMarket, targetMarket));
                    //this.activeFleets.add(spawnSupportFleet(invader, originMarket, targetMarket));
                }
            }
            */

            Map<String, Object> params = new HashMap<>();
            params.put("target", targetMarket);
            params.put("dp", data.startingFleetPoints);
            Global.getSector().getEventManager().startEvent(new CampaignEventTarget(originMarket), "exerelin_invasion_fleet", params);
        }
    }
  
    @Override
    public void advance(float amount)
    {
        float days = Global.getSector().getClock().convertToDays(amount);
    
        if (daysElapsed < ExerelinConfig.invasionGracePeriod)
        {
            daysElapsed += days;
            return;
        }
        
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
    
        if (this.activeFleets.size() < MAX_FLEETS)
        {
            generateInvasionFleet();
        }
    }
    
    public static InvasionFleetManager create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        invasionFleetManager = (InvasionFleetManager)data.get(MANAGER_MAP_KEY);
        if (invasionFleetManager != null)
            return invasionFleetManager;
        
        invasionFleetManager = new InvasionFleetManager();
        data.put(MANAGER_MAP_KEY, invasionFleetManager);
        return invasionFleetManager;
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
        public boolean noWait = false;
        public boolean noWander = false;
    
        public InvasionFleetData(CampaignFleetAPI fleet)
        {
            this.fleet = fleet;
        }
    }
    
    public static class FleetSpawnParams
    {
        public String name = "Fleet";
        public String fleetType = "genericFleet";
        public int fp = 0;
        public float qf = 0.5f;
        public FactionAPI faction;
        public MarketAPI originMarket;
        public MarketAPI targetMarket;
        public LocationAPI spawnLoc;
        public boolean noWait = false;
        public boolean jumpToOrigin = true;
        public boolean noWander = false; // only used by strike and patrol fleets ATM
        public int numMarines = 0;
        //public String aiClass;
    }
}