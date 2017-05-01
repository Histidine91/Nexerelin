package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParams;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.MiningHelperLegacy;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.histidine.industry.scripts.MiningHelper;
import org.histidine.industry.scripts.MiningHelper.MiningReport;

/**
 * Handles invasion fleets (the ones that capture stations)
 * Originally derived from Dark.Revenant's II_WarFleetManager
 */
public class MiningFleetManager extends BaseCampaignEventListener implements EveryFrameScript
{
	public static final String MANAGER_MAP_KEY = "exerelin_miningFleetManager";
		
	public static Logger log = Global.getLogger(MiningFleetManager.class);
	protected static final float POINT_INCREMENT_PER_DAY = 3f;
	protected static final float MARKET_STABILITY_DIVISOR = 5f;
	protected static final float POINTS_TO_SPAWN = 100f;
	protected static final float POINT_INCREMENT_PERIOD = 1;
	protected static final float GAS_FLEET_CHANCE = 0.4f;
	
	protected final List<MiningFleetData> activeFleets = new LinkedList();
	protected HashMap<String, Float> spawnCounter = new HashMap<>();
	
	protected float timer = 0;
	protected float daysElapsed = 0;
	
	private static MiningFleetManager miningFleetManager;
	
	public MiningFleetManager()
	{
		super(true);
	}
	
	protected boolean hasOreFacilities(MarketAPI market)
	{
		FactionAPI faction = market.getFaction();
		return market.hasCondition(Conditions.ORE_COMPLEX) || market.hasCondition(Conditions.ORE_REFINING_COMPLEX) 
				|| market.hasCondition("aiw_inorganic_populace");
	}
	
	protected boolean hasGasFacilities(MarketAPI market)
	{
		return market.hasCondition(Conditions.VOLATILES_COMPLEX) || market.hasCondition(Conditions.VOLATILES_DEPOT)
				|| market.hasCondition(Conditions.CRYOSANCTUM) || market.hasCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION)
				|| market.hasCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
	}
	
	protected boolean isOreMineable(SectorEntityToken entity)
	{
		if (entity instanceof AsteroidAPI) return true;
		if (!(entity instanceof PlanetAPI)) return false;
		if (ExerelinModPlugin.HAVE_STELLAR_INDUSTRIALIST)
		{
			float exhaustion = MiningHelper.getExhaustion(entity);
			if (exhaustion > 0.7f) return false;
			
			MiningReport report = MiningHelper.getMiningReport(null, entity, 1 - exhaustion);
			if (report.totalOutput.containsKey(Commodities.ORE))
				return report.totalOutput.get(Commodities.ORE) > 0.5;
			if (report.totalOutput.containsKey(Commodities.RARE_ORE))
				return report.totalOutput.get(Commodities.RARE_ORE) > 0.05;
			return false;
		}
		else
		{
			PlanetAPI planet = (PlanetAPI)entity;
			return !planet.isGasGiant() && !planet.isStar();
		}
	}
	
	protected boolean isGasMineable(SectorEntityToken entity)
	{
		if (!(entity instanceof PlanetAPI)) return false;
		if (ExerelinModPlugin.HAVE_STELLAR_INDUSTRIALIST)
		{
			float exhaustion = MiningHelper.getExhaustion(entity);
			if (exhaustion > 0.7f) return false;
			
			MiningReport report = MiningHelper.getMiningReport(null, entity, 1 - exhaustion);
			if (report.totalOutput.containsKey(Commodities.VOLATILES))
				return report.totalOutput.get(Commodities.VOLATILES) > 0.4;
			return false;
		}
		else
		{
			return ((PlanetAPI)entity).isGasGiant();
		}
	}
	
	public void spawnMiningFleet(MarketAPI origin)
	{
		log.info("Trying mining fleet for market " + origin.getName());
		SectorEntityToken target = null;
		LocationAPI loc = origin.getContainingLocation();
		if (loc == Global.getSector().getHyperspace()) return;
		
		FactionAPI faction = origin.getFaction();
		if (faction.getId().equals("templars")) return;
		int marketSize = origin.getSize();
		int maxFP = (int)(Math.pow(marketSize, 1.5f));
		
		float qf = origin.getShipQualityFactor();
		//qf = Math.max(qf, 0.7f);
		
		boolean isGasMiningFleet = false;
		boolean hasOreFacilities = hasOreFacilities(origin);
		boolean hasGasFacilities = hasGasFacilities(origin);
		if (hasOreFacilities && hasGasFacilities)
			isGasMiningFleet = Math.random() < GAS_FLEET_CHANCE;
		else if (hasOreFacilities)
			isGasMiningFleet = false;
		else if (hasGasFacilities)
			isGasMiningFleet = true;
		else
		{
			//log.info("Market " + origin.getName() + " is not valid for mining operations");
			return;
		}
		
		WeightedRandomPicker<SectorEntityToken> targetPicker = new WeightedRandomPicker<>();
		List<SectorEntityToken> planets = origin.getContainingLocation().getEntitiesWithTag("planet");
		if (!isGasMiningFleet) planets.addAll(origin.getContainingLocation().getAsteroids());
		
		for (SectorEntityToken planetToken : planets)
		{
			if (planetToken.getMarket() != null && !planetToken.getMarket().isPlanetConditionMarketOnly())
				continue;
			
			if (isGasMiningFleet)
			{
				if (isGasMineable(planetToken))
					targetPicker.add(planetToken);
			}
			else
			{
				if (!isOreMineable(planetToken))
					continue;
				
				// don't try to mine moons of hostile factions
				OrbitAPI orbit = planetToken.getOrbit();
				if (orbit != null && orbit.getFocus() != null)
				{
					FactionAPI owner = orbit.getFocus().getFaction();
					if (owner != null && owner.isHostileTo(faction)) 
						continue;
				}
				targetPicker.add(planetToken);
			}
		}
		if (targetPicker.isEmpty()) 
		{
			//log.info("No valid target for mining fleet");
			return;
		}
		else target = targetPicker.pick();
		if (target == null)
		{
			//log.info("Target is null; picker size " + targetPicker.getItems().size());
			return;
		}
		
		String name = StringHelper.getString("exerelin_fleets", "miningFleetName");
		String factionId = origin.getFactionId();
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(factionId);
		if (factionConfig != null)
		{
			name = factionConfig.asteroidMiningFleetName;
		}
		if (maxFP < 10) name = StringHelper.getString("exerelin_fleets", "miningFleetPrefixSmall") + " " + name;
		else if (maxFP > 20) name = StringHelper.getString("exerelin_fleets", "miningFleetPrefixLarge") + " " + name;
		
		//log.info("Trying to create mining fleet of size " + maxFP + ", target " + target.getName());
		FleetParams params = new FleetParams(null, origin, factionId, null, "exerelinMiningFleet", 
				maxFP/4, // combat
				maxFP*0.45f, // freighters
				0,		// tankers
				0,		// personnel transports
				0,		// liners
				0,		// civilian
				maxFP*0.05f,	// utility
				-0.25f, -1, 0.5f, -5);	// quality bonus, quality override, officer num mult, officer level bonus
		
		//CampaignFleetAPI fleet = FleetFactory.createGenericFleet(origin.getFactionId(), name, qf, maxFP/3);
		CampaignFleetAPI fleet = FleetFactoryV2.createFleet(params);
		if (fleet == null)
			return;
		
		fleet.setName(name);
		fleet.setAIMode(true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
		
		int minerFP = (int)(maxFP * 0.25f + 0.5f);
		while (minerFP > 0)
		{
			FleetMemberAPI miner = ExerelinUtilsFleet.addMiningShipToFleet(fleet);
			minerFP -= FleetFactoryV2.getPointsForVariant(miner.getVariant().getHullVariantId());
			//log.info("Adding miner to fleet: " + miner.getHullId());
		}
		
		float miningStrength = 0;
		if (ExerelinModPlugin.HAVE_STELLAR_INDUSTRIALIST)
		{
			miningStrength = MiningHelper.getFleetMiningStrength(fleet);
			// take machinery with us
			float machineryRequired = MiningHelper.getRequiredMachinery(miningStrength);
			float machineryToTake = Math.min(machineryRequired * 1.25f, origin.getCommodityData(Commodities.HEAVY_MACHINERY).getStockpile());
			fleet.getCargo().addCommodity(Commodities.HEAVY_MACHINERY, machineryToTake);
			origin.getCommodityData(Commodities.HEAVY_MACHINERY).removeFromStockpile(machineryToTake);
		}
		else
		{
			miningStrength = MiningHelperLegacy.getFleetMiningStrength(fleet);
		}
		
		SectorEntityToken entity = origin.getPrimaryEntity();
		entity.getContainingLocation().addEntity(fleet);
		fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
		
		MiningFleetData data = new MiningFleetData(fleet);
		data.startingFleetPoints = fleet.getFleetPoints();
		data.sourceMarket = origin;
		data.source = origin.getPrimaryEntity();
		data.target = target;
		data.miningStrength = miningStrength;
		this.activeFleets.add(data);
		
		MiningFleetAI ai = new MiningFleetAI(fleet, data);
		fleet.addScript(ai);
		log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + maxFP);
	}
	
	public void updateMiningFleetPoints(float days)
	{
		//log.info("Incrementing mining points");
		// prevents NPE of unknown origin
		if (Global.getSector() == null || Global.getSector().getEconomy() == null)
			return;
		
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		for(MarketAPI market:markets)
		{
			if (ExerelinUtilsFaction.isPirateOrTemplarFaction(market.getFactionId()))
				continue;
			
			if (!spawnCounter.containsKey(market.getId()))
				spawnCounter.put(market.getId(), 0f);
			
			float baseIncrement = (0.5f + (market.getStabilityValue()/MARKET_STABILITY_DIVISOR));
			float increment = baseIncrement;
			//if (market.hasCondition("regional_capital")) increment += baseIncrement * 0.1f;
			if (market.hasCondition(Conditions.SPACEPORT)) increment += baseIncrement * 0.25f;
			if (market.hasCondition(Conditions.ORBITAL_STATION)) increment += baseIncrement * 0.25f;
			
			increment = increment * POINT_INCREMENT_PER_DAY * days;
			float newValue = spawnCounter.get(market.getId()) + increment;
			//log.info("Market " + market.getName() + " has " + newValue + " mining points");
			if (newValue > POINTS_TO_SPAWN)
			{
				newValue -= POINTS_TO_SPAWN;
				//spawnMiningFleet(market);
			}
			
			spawnCounter.put(market.getId(), newValue);
		}
	}
  
	@Override
	public void advance(float amount)
	{
		float days = Global.getSector().getClock().convertToDays(amount);
			
		timer += days;
		if (timer < POINT_INCREMENT_PERIOD) {
			return;
		}
		List<MiningFleetData> remove = new LinkedList();
		for (MiningFleetData data : this.activeFleets) {
			if ((data.fleet.getContainingLocation() == null) || (!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) || (!data.fleet.isAlive())) {
				remove.add(data);
			}
		}
		this.activeFleets.removeAll(remove);
	
		updateMiningFleetPoints(POINT_INCREMENT_PERIOD);
		MiningHelperLegacy.renewResources(POINT_INCREMENT_PERIOD);
		timer -= POINT_INCREMENT_PERIOD;
	}
	
	public static MiningFleetManager create()
	{
		Map<String, Object> data = Global.getSector().getPersistentData();
		miningFleetManager = (MiningFleetManager)data.get(MANAGER_MAP_KEY);
		if (miningFleetManager != null)
			return miningFleetManager;
		
		miningFleetManager = new MiningFleetManager();
		data.put(MANAGER_MAP_KEY, miningFleetManager);
		return miningFleetManager;
	}
	
	public static MiningFleetManager getFleetManager()
	{
		return miningFleetManager;
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
		for (MiningFleetData data : this.activeFleets) {
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
	
	public static class MiningFleetData
	{
		public CampaignFleetAPI fleet;
		public SectorEntityToken source;
		public SectorEntityToken target;
		public MarketAPI sourceMarket;
		public float startingFleetPoints = 0.0F;
		public float miningStrength = 0;
		public boolean noWait = false;
	
		public MiningFleetData(CampaignFleetAPI fleet)
		{
			this.fleet = fleet;
		}
	}
}