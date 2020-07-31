package exerelin.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.fleets.DisposableFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction.SalvageSpecialData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

public class VultureFleetManager extends DisposableFleetManager
{
	public static final String MANAGER_MAP_KEY = "nex_vultureFleetManager";
	
	public static Logger log = Global.getLogger(VultureFleetManager.class);
		
	public CampaignFleetAPI spawnVultureFleet(MarketAPI origin)
	{
		log.info("Trying vulture fleet for market " + origin.getName());
		SectorEntityToken target = null;
		
		FactionAPI faction = Global.getSector().getFaction(Factions.SCAVENGERS);
		int marketSize = origin.getSize();
		int maxFP = (int)(Math.pow(marketSize, 1.5f) * 5);
		
		Random random = new Random();
		
		// Medium scavenger fleet, minus some unnecessary stuff
		int combat = 4 + random.nextInt(5);
		int freighter = 4 + random.nextInt(5);
		int utility = 2 + random.nextInt(3);
		
		combat *= 5f;
		freighter *= 3f;
		utility *= 2f;	// more than normal scav
		
		//log.info("Trying to create mining fleet of size " + maxFP + ", target " + target.getName());
		FleetParamsV3 params = new FleetParamsV3(origin, origin.getLocationInHyperspace(),
				Factions.SCAVENGERS,
				null,				// quality override
				"nex_vultureFleet",	// fleet type
				combat,
				freighter,
				0,	// tankers
				0,	// transports
				0,	// liners
				utility,
				0);	// quality mod
		
		CampaignFleetAPI fleet = ExerelinUtilsFleet.customCreateFleet(faction, params);
		if (fleet == null)
			return null;
		
		fleet.setFaction(Factions.INDEPENDENT, true);
		fleet.setAIMode(true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SCAVENGER, true);
		Misc.makeLowRepImpact(fleet, "scav");
		
		//float machineryToTake = Math.min(machineryRequired * 1.25f, origin.getCommodityData(Commodities.HEAVY_MACHINERY).getStockpile());
		
		SectorEntityToken entity = origin.getPrimaryEntity();
		entity.getContainingLocation().addEntity(fleet);
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		
		boolean noWait = true;
		if (playerFleet != null && playerFleet.getContainingLocation() == entity.getContainingLocation())
			noWait = !MathUtils.isWithinRange(target, playerFleet, playerFleet.getMaxSensorRangeToDetect(fleet) + getInSystemCullRange());
		
		if (!noWait) {
			fleet.setLocation(entity.getLocation().x, entity.getLocation().y);
		}
		else {
			fleet.setLocation(target.getLocation().x, target.getLocation().y);
		}
		
		VultureFleetData data = new VultureFleetData(fleet);
		data.startingFleetPoints = fleet.getFleetPoints();
		data.source = origin;
		data.target = target;
		data.noWait = noWait;
		
		// partially fill cargo (as if we're already been mining for some time)
		if (noWait) {
			// TODO
		}
		
		//MiningFleetAI ai = new MiningFleetAI(fleet, data);
		//fleet.addScript(ai);
		log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + maxFP);
		
		return fleet;
	}
	
	@Override
	protected String getActionInsideText(StarSystemAPI system) {
		return "scavenging";
	}
	
	public static VultureFleetManager create()
	{
		Map<String, Object> data = Global.getSector().getPersistentData();
		VultureFleetManager manager = (VultureFleetManager)data.get(MANAGER_MAP_KEY);
		if (manager != null)
			return manager;
		
		manager = new VultureFleetManager();
		data.put(MANAGER_MAP_KEY, manager);
		return manager;
	}
	
	public static VultureFleetManager getManager()
	{
		Map<String, Object> data = Global.getSector().getPersistentData();
		return (VultureFleetManager)data.get(MANAGER_MAP_KEY);
	}
	
	public static float getDPFromVariant(String variantId) {
		ShipVariantAPI var = Global.getSettings().getVariant(variantId);
		if (var == null) return 0;
		FleetMemberAPI temp = Global.getFactory().createFleetMember(FleetMemberType.SHIP, var);
		return temp.getDeploymentPointsCost();
	}
	
	// runcode exerelin.campaign.fleets.VultureFleetManager.getScavengeScore(Global.getSector().getCurrentLocation());
	/**
	 * Generates a score based on the number of ship wrecks and debris fields in the specified location.
	 * @param loc
	 * @return
	 */
	public static float getScavengeScore(LocationAPI loc) {
		float score = 0;
		
		// floating wrecks
		for (SectorEntityToken entity : loc.getEntitiesWithTag(Tags.SALVAGEABLE)) 
		{
			if (!entity.hasTag(Tags.EXPIRES)) continue;
			if (!Entities.WRECK.equals(entity.getCustomEntityType()))
				continue;
			if (!(entity.getCustomPlugin() instanceof DerelictShipEntityPlugin))
				continue;
			DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) entity.getCustomPlugin();
			PerShipData ship = plugin.getData().ship;
			String variantId = ship.variantId;
			float shipValue = getDPFromVariant(variantId) * 20;
			//log.info("Found floating ship " + variantId + ", value " + shipValue);
			score += shipValue;
		}
		
		// debris field
		for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
			if (!terrain.getType().equals(Terrain.DEBRIS_FIELD)) continue;
			DebrisFieldTerrainPlugin debris = (DebrisFieldTerrainPlugin)terrain.getPlugin();
			DebrisFieldParams params = debris.getParams();
			if (params.lastsDays > 60 || params.source != DebrisFieldSource.BATTLE) continue;
			
			float value = params.baseSalvageXP * debris.getParams().density;
			//log.info("Found debris field, value " + value);
			
			// check for ships in the debris field
			MemoryAPI mem = terrain.getMemoryWithoutUpdate();
			if (mem.contains(MemFlags.SALVAGE_SPECIAL_DATA)) {
				SalvageSpecialData special = (SalvageSpecialData)mem.get(MemFlags.SALVAGE_SPECIAL_DATA);
				
				if (special instanceof ShipRecoverySpecialData) 
				{
					ShipRecoverySpecialData special2 = (ShipRecoverySpecialData)special;
					for (PerShipData ship : special2.ships) {
						String variantId = ship.variantId;
						float shipValue = getDPFromVariant(variantId) * 20;
						//log.info("Found ship " + variantId + " in debris field, value " + shipValue);
						score += shipValue;
					}
				}
			}
			
			//log.info("Adding " + value + " points from debris field");
			score += value;
		}
		
		return score;
	}

	@Override
	protected int getDesiredNumFleetsForSpawnLocation() {
		float scavengeScore = getScavengeScore(currSpawnLoc);
		int num = (int)(scavengeScore/1200);
		if (num > 3) num = 3;
		return num;
	}

	@Override
	protected CampaignFleetAPI spawnFleetImpl() {
		StarSystemAPI system = currSpawnLoc;
		if (system == null) return null;
		
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarkets(system))
		{
			if (ExerelinUtilsFaction.isPirateOrTemplarFaction(market.getFactionId()))
				continue;
			if (market.getFaction().isHostileTo(Factions.INDEPENDENT)) 
				continue;
			
			picker.add(market, market.getSize());
		}
		
		if (picker.isEmpty()) return null;
		MarketAPI market = picker.pick();
		
		CampaignFleetAPI fleet = spawnVultureFleet(market);
		
		return fleet;
	}

	@Override
	protected String getSpawnId() {
		return "nex_vultureFleet";
	}
	
	@Override
	protected float getExpireDaysPerFleet() {
		return 30f;
	}
	
	@Override
	public float getSpawnRateMult() {
		return super.getSpawnRateMult() * 5f;
	}
	
	public static class VultureFleetData
	{
		public CampaignFleetAPI fleet;
		public SectorEntityToken target;
		public MarketAPI source;
		public float startingFleetPoints = 0.0f;
		public boolean noWait = false;
	
		public VultureFleetData(CampaignFleetAPI fleet)
		{
			this.fleet = fleet;
		}
	}
}