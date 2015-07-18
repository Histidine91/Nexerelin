package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactory;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.MiningHelper;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Handles invasion fleets (the ones that capture stations)
 * Originally derived from Dark.Revenant's II_WarFleetManager
 */
public class MiningFleetManager extends BaseCampaignEventListener implements EveryFrameScript
{
	public static final String MANAGER_MAP_KEY = "exerelin_miningFleetManager";
		
	public static Logger log = Global.getLogger(MiningFleetManager.class);
	protected static final float POINT_INCREMENT_PER_DAY = 4f;
	protected static final float MARKET_STABILITY_DIVISOR = 5f;
	protected static final float POINTS_TO_SPAWN = 100f;
	
	protected final List<MiningFleetData> activeFleets = new LinkedList();
	protected HashMap<String, Float> spawnCounter = new HashMap<>();
	
	private final IntervalUtil tracker;
	
	protected float daysElapsed = 0;
	
	private static MiningFleetManager miningFleetManager;
	
	public MiningFleetManager()
	{
		super(true);
		this.tracker = new IntervalUtil(1, 1);
	}
	
	public void spawnMiningFleet(MarketAPI origin)
	{
		log.info("Trying mining fleet for market " + origin.getName());
		SectorEntityToken target = null;
		LocationAPI loc = origin.getContainingLocation();
		if (loc == Global.getSector().getHyperspace()) return;
		
		FactionAPI faction = origin.getFaction();
		int marketSize = origin.getSize();
		int maxFP = (int)(Math.pow(marketSize, 1.5f) * 5);
		
		float qf = origin.getShipQualityFactor();
		//qf = Math.max(qf, 0.7f);
		
		boolean isGasMiningFleet = false;
		boolean hasOreFacilities = origin.hasCondition(Conditions.ORE_COMPLEX) || origin.hasCondition(Conditions.ORE_REFINING_COMPLEX) 
				|| faction.getId().equals("spire") || faction.getId().equals("darkspire");
		boolean hasGasFacilities = origin.hasCondition(Conditions.VOLATILES_COMPLEX) || origin.hasCondition(Conditions.VOLATILES_DEPOT)
				|| origin.hasCondition(Conditions.CRYOSANCTUM) || origin.hasCondition(Conditions.ANTIMATTER_FUEL_PRODUCTION)
				|| origin.hasCondition(Conditions.LIGHT_INDUSTRIAL_COMPLEX);
		if (hasOreFacilities && hasGasFacilities)
			isGasMiningFleet = Math.random()<0.5f;
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
			if (isGasMiningFleet)
			{
				if ( ((PlanetAPI)planetToken).isGasGiant() )
					targetPicker.add(planetToken);
			}
			else
			{
				if (planetToken instanceof PlanetAPI)
				{
					PlanetAPI planet = (PlanetAPI)planetToken;
					if (!planet.isMoon()) continue;
				}
				if (planetToken.getMarket() != null) continue;
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
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(origin.getFactionId());
		if (factionConfig != null)
		{
			name = factionConfig.asteroidMiningFleetName;
		}
		if (maxFP < 50) name = StringHelper.getString("exerelin_fleets", "miningFleetPrefixSmall") + " " + name;
		else if (maxFP > 100) name = StringHelper.getString("exerelin_fleets", "miningFleetPrefixLarge") + " " + name;
		
		//log.info("Trying to create mining fleet of size " + maxFP + ", target " + target.getName());
		CampaignFleetAPI fleet = FleetFactory.createGenericFleet(origin.getFactionId(), name, qf, maxFP/3);
		
		float minerFP = maxFP - fleet.getFleetPoints();
		while (minerFP > 0)
		{
			if (minerFP > 30)
			{
				fleet.getFaction().pickShipAndAddToFleet(ShipRoles.FREIGHTER_MEDIUM, qf, fleet);
				ExerelinUtilsFleet.addMiningShipToFleet(fleet);
				ExerelinUtilsFleet.addMiningShipToFleet(fleet);
			}
			else
			{
				fleet.getFaction().pickShipAndAddToFleet(ShipRoles.FREIGHTER_SMALL, qf, fleet);
				ExerelinUtilsFleet.addMiningShipToFleet(fleet);
			}
			minerFP = maxFP - fleet.getFleetPoints();
		}
			 
		fleet.getMemoryWithoutUpdate().set("$fleetType", "exerelinMiningFleet");
		fleet.getMemoryWithoutUpdate().set("$maxFP", maxFP);
		fleet.getMemoryWithoutUpdate().set("$originMarket", origin);
		
		float miningStrength = MiningHelper.getFleetMiningStrength(fleet);
		
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
			
			ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
			if (factionConfig != null)
			{
				increment += baseIncrement * factionConfig.responseFleetSizeMod;
			}
			
			increment = increment * POINT_INCREMENT_PER_DAY * days;
			float newValue = spawnCounter.get(market.getId()) + increment;
			
			if (newValue > POINTS_TO_SPAWN)
			{
				newValue -= POINTS_TO_SPAWN;
				spawnMiningFleet(market);
			}
			
			spawnCounter.put(market.getId(), newValue);
		}
	}
  
	@Override
	public void advance(float amount)
	{
		float days = Global.getSector().getClock().convertToDays(amount);
			
		this.tracker.advance(days);
		if (!this.tracker.intervalElapsed()) {
			return;
		}
		List<MiningFleetData> remove = new LinkedList();
		for (MiningFleetData data : this.activeFleets) {
			if ((data.fleet.getContainingLocation() == null) || (!data.fleet.getContainingLocation().getFleets().contains(data.fleet)) || (!data.fleet.isAlive())) {
				remove.add(data);
			}
		}
		this.activeFleets.removeAll(remove);
	
		updateMiningFleetPoints(days);
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