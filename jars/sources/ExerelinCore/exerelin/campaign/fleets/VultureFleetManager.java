package exerelin.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.fleets.DisposableFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.ids.Terrain;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction.SalvageSpecialData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.ExerelinUtilsFleet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class VultureFleetManager extends DisposableFleetManager
{
	public static final String MANAGER_MAP_KEY = "nex_vultureFleetManager";
	public static final String MEM_KEY_FLEET_COUNT_CACHE = "$nex_vultureFleetCountCache";
	public static final boolean DEBUG_MODE = true;
	
	public static Logger log = Global.getLogger(VultureFleetManager.class);
	
	/*
	runcode MarketAPI market = Global.getSector().getEconomy().getMarket("jangala"); 
	CampaignFleetAPI fleet = new exerelin.campaign.fleets.VultureFleetManager().spawnVultureFleet(market);
	*/
	
	public CampaignFleetAPI spawnVultureFleet(MarketAPI origin)
	{
		log.info("Trying vulture fleet for market " + origin.getName());
		SectorEntityToken target = getClosestScavengable(origin.getLocation(), origin.getContainingLocation());
		
		if (target == null) {
			log.info("  No scavenge target found");
			return null;
		}
		
		FactionAPI faction = Global.getSector().getFaction(Factions.SCAVENGERS);
		int marketSize = origin.getSize();
		int maxFP = (int)(Math.pow(marketSize, 1.5f) * 5);
		
		Random random = new Random();
		
		// Medium scavenger fleet, plus more combat ships and minus some unnecessary stuff
		int combat = 4 + random.nextInt(5);
		int freighter = 4 + random.nextInt(5);
		int utility = 2 + random.nextInt(3);
		
		combat *= 8f;	// more than normal scav
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
		
		if (DEBUG_MODE) noWait = true;
		
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
		
		VultureFleetAI ai = new VultureFleetAI(fleet, data);
		fleet.addScript(ai);
		log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + maxFP);
		Global.getSector().getCampaignUI().addMessage("Spawned vulture fleet in " 
				+ origin.getContainingLocation() + " from " + origin.getName());
		
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
	
	public static SectorEntityToken getClosestScavengable(Vector2f fleetLoc, LocationAPI loc) 
	{
		SectorEntityToken closest = null;
		double closestDistSq = 999999999d;
		
		for (SectorEntityToken entity : getDerelictShips(loc)) {
			float distSq = MathUtils.getDistanceSquared(fleetLoc, entity.getLocation());
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = entity;
			}
		}
		// debris field
		for (CampaignTerrainAPI terrain : getDebrisFields(loc)) {
			float distSq = MathUtils.getDistance(fleetLoc, terrain.getLocation());
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = terrain;
			}
		}
		return closest;
	}
	
	public static float getShipScavengeScore(SectorEntityToken entity) {
		DerelictShipEntityPlugin plugin = (DerelictShipEntityPlugin) entity.getCustomPlugin();
		PerShipData ship = plugin.getData().ship;
		String variantId = ship.variantId;
		return getDPFromVariant(variantId) * 20;
	}
	
	public static List<PerShipData> getShipsInDebris(CampaignTerrainAPI terrain) {
		MemoryAPI mem = terrain.getMemoryWithoutUpdate();
		if (mem.contains(MemFlags.SALVAGE_SPECIAL_DATA)) {
			SalvageSpecialData special = (SalvageSpecialData)mem.get(MemFlags.SALVAGE_SPECIAL_DATA);

			if (special instanceof ShipRecoverySpecialData) 
			{
				ShipRecoverySpecialData special2 = (ShipRecoverySpecialData)special;
				return special2.ships;
			}
		}
		return null;
	}
	
	public static float getDebrisScavengeScore(CampaignTerrainAPI terrain, DebrisFieldParams debris) {
		float value = debris.baseSalvageXP * debris.density;

		// check for ships in the debris field
		List<PerShipData> ships = getShipsInDebris(terrain);
		if (ships != null) {
			for (PerShipData ship : ships) {
				String variantId = ship.variantId;
				float shipValue = getDPFromVariant(variantId) * 20;
				value += shipValue;
			}
		}
		
		return value;
	}
	
	/**
	 * Gets the derelict ships present in the location. Will not grab ships with no expiry.
	 * @param loc
	 * @return
	 */
	public static List<SectorEntityToken> getDerelictShips(LocationAPI loc) {
		List<SectorEntityToken> results = new ArrayList<>();
		for (SectorEntityToken entity : loc.getEntitiesWithTag(Tags.SALVAGEABLE)) 
		{
			if (!entity.hasTag(Tags.EXPIRES)) continue;
			if (!Entities.WRECK.equals(entity.getCustomEntityType()))
				continue;
			if (!(entity.getCustomPlugin() instanceof DerelictShipEntityPlugin))
				continue;
			results.add(entity);
		}
		return results;
	}
	
	/**
	 * Gets the debris fields in the location which are valid targets for scavenging. 
	 * Will not include permanent debris fields, or those generated from any source 
	 * other than combat.
	 * @param loc
	 * @return
	 */
	public static List<CampaignTerrainAPI> getDebrisFields(LocationAPI loc) {
		List<CampaignTerrainAPI> results = new ArrayList<>();
		for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
			if (!terrain.getType().equals(Terrain.DEBRIS_FIELD)) continue;
			DebrisFieldParams params = ((DebrisFieldTerrainPlugin)terrain.getPlugin()).getParams();
			if (params.lastsDays > 60 || params.source != DebrisFieldSource.BATTLE) continue;
			results.add(terrain);
		}
		return results;
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
		for (SectorEntityToken entity : getDerelictShips(loc)) 
		{
			float shipValue = getShipScavengeScore(entity);
			score += shipValue;
		}
		
		// debris field
		for (CampaignTerrainAPI terrain : getDebrisFields(loc)) {
			DebrisFieldTerrainPlugin debris = (DebrisFieldTerrainPlugin)terrain.getPlugin();
			float value = getDebrisScavengeScore(terrain, debris.getParams());		
			score += value;
		}
		
		return score;
	}
	
	public static boolean hasOngoingRaids(LocationAPI loc) {
		List<IntelInfoPlugin> raids = Global.getSector().getIntelManager().getIntel(RaidIntel.class);
		for (IntelInfoPlugin intel : raids) {
			RaidIntel raid = (RaidIntel)intel;
			if (raid.getSystem() != loc)
				continue;
			// only ongoing raids
			if (raid.getStageIndex(raid.getActionStage()) != raid.getCurrentStage()) {
				continue;
			}
			return true;
		}
		return false;
	}
	
	public static CampaignFleetAPI getRandomRaidFleet(CampaignFleetAPI scav) 
	{
		WeightedRandomPicker<CampaignFleetAPI> picker = new WeightedRandomPicker<>();
		for (CampaignFleetAPI fleet : scav.getContainingLocation().getFleets()) 
		{
			if (fleet.isHostileTo(scav)) continue;
			if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER))
				picker.add(fleet);
		}
		return picker.pick();
	}
	
	@Override
	protected int getDesiredNumFleetsForSpawnLocation() {
		
		// Based on number of raids happening
		List<IntelInfoPlugin> raids = Global.getSector().getIntelManager().getIntel(RaidIntel.class);
		float raidFactor = 0;
		for (IntelInfoPlugin intel : raids) {
			RaidIntel raid = (RaidIntel)intel;
			if (raid.isEnding() || raid.isEnded()) continue;
			if (raid.getSystem() != currSpawnLoc) continue;
			// only ongoing raids
			if (raid.getStageIndex(raid.getActionStage()) != raid.getCurrentStage()) {
				continue;
			}
			raidFactor += raid.getOrigNumFleets()/3;
		}
		int numFromRaids = Math.round(raidFactor);
		if (numFromRaids > 3) numFromRaids = 3;
		
		if (numFromRaids > 0) return numFromRaids;
		
		// Old debris field system
		// Too reactive, we want to spawn the vultures before debris shows up
		// But it's also needed to deal with salvage that occurs without a raid (e.g. patrols fighting each other)
		int numFromSalvage = 0;
		MemoryAPI mem = currSpawnLoc.getMemoryWithoutUpdate();
		if (mem.contains(MEM_KEY_FLEET_COUNT_CACHE))
			numFromSalvage = (int)mem.getFloat(MEM_KEY_FLEET_COUNT_CACHE);
		else {
			//Global.getSector().getCampaignUI().addMessage("Querying num spawn vulture fleets");
			float scavengeScore = getScavengeScore(currSpawnLoc);
			numFromSalvage = (int)(scavengeScore/1200);
			if (numFromSalvage > 3) numFromSalvage = 3;
			mem.set(MEM_KEY_FLEET_COUNT_CACHE, raidFactor, 2);
		}
		return Math.max(numFromRaids, numFromSalvage);
	}

	@Override
	protected CampaignFleetAPI spawnFleetImpl() {
		StarSystemAPI system = currSpawnLoc;
		
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
	
	/**
	 * Like the superclass, but inits its Random on creation. Used to handle recovery of ships by vulture fleets.
	 */
	public static class ShipRecoverySpecialNPC extends ShipRecoverySpecial {
		
		public ShipRecoverySpecialNPC() {
			super();
			this.random = new Random();
		}
		
		@Override
		public void prepareMember(FleetMemberAPI member, PerShipData shipData) {
		
			int hits = getHitsForCondition(member, shipData.condition);
			int dmods = getDmodsForCondition(shipData.condition);
			
			// modified from super: don't use player fleet's d-mod reduction
			int reduction = 1;	//(int) playerFleet.getStats().getDynamic().getValue(Stats.SHIP_DMOD_REDUCTION, 0);
			reduction = random.nextInt(reduction + 1);
			dmods -= reduction;

			member.getStatus().setRandom(random);

			for (int i = 0; i < hits; i++) {
				member.getStatus().applyDamage(1000000f);
			}

			member.getStatus().setHullFraction(getHullForCondition(shipData.condition));
			member.getRepairTracker().setCR(0f);


			ShipVariantAPI variant = member.getVariant();
			variant = variant.clone();
			variant.setOriginalVariant(null);

			int dModsAlready = DModManager.getNumDMods(variant);
			dmods = Math.max(0, dmods - dModsAlready);

			if (dmods > 0 && shipData.addDmods) {
				DModManager.setDHull(variant);
			}
			member.setVariant(variant, false, true);

			if (dmods > 0 && shipData.addDmods) {
				DModManager.addDMods(member, true, dmods, random);
			}

			if (shipData.pruneWeapons) {
				float retain = getFighterWeaponRetainProb(shipData.condition);
				FleetEncounterContext.prepareShipForRecovery(member, false, false, retain, retain, random);
				member.getVariant().autoGenerateWeaponGroups();
			}
		}
	}
}