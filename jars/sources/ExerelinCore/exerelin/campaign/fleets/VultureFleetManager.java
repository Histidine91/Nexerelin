package exerelin.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.campaign.ai.ModularFleetAIAPI;
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
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageSpecialInteraction.SalvageSpecialData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldParams;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin.DebrisFieldSource;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsFleet;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

public class VultureFleetManager extends DisposableFleetManager
{
	public static final String MANAGER_MAP_KEY = "nex_vultureFleetManager";
	public static final String MEM_KEY_FLEET_COUNT_CACHE = "$nex_vultureFleetCountCache";
	public static final int CHECK_HOSTILE_FLEET_RANGE_SQ = 1000 * 1000;
	public static final boolean DEBUG_MODE = false;
	
	public static Logger log = Global.getLogger(VultureFleetManager.class);
	
	/*
	runcode MarketAPI market = Global.getSector().getEconomy().getMarket("jangala"); 
	CampaignFleetAPI fleet = new exerelin.campaign.fleets.VultureFleetManager().spawnVultureFleet(market);
	*/
	
	public CampaignFleetAPI spawnVultureFleet(MarketAPI origin)
	{
		log.info("Trying vulture fleet for market " + origin.getName());
		Vector2f from = origin.getLocation();
		if (Global.getSector().getPlayerFleet().getContainingLocation() == origin.getContainingLocation())
			from = Global.getSector().getPlayerFleet().getLocation();
		
		SectorEntityToken target = getClosestScavengable(from, origin.getContainingLocation(), null);
		
		if (target == null) {
			log.info("  No scavenge target found");
			return null;
		}
		
		FactionAPI faction = Global.getSector().getFaction(Factions.SCAVENGERS);
		
		Random random = new Random();
		
		// Medium scavenger fleet, plus more combat ships and minus some unnecessary stuff
		// make it not so random? ... nah it's fine
		int combat = 4 + random.nextInt(5);
		int freighter = 4 + random.nextInt(5);
		int utility = 2 + random.nextInt(3);
		
		combat *= 8f;	// more than normal scav
		freighter *= 3f;
		utility *= 2f;	// more than normal scav
		
		int total = combat + freighter + utility;
		
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
		
		CampaignFleetAPI fleet = NexUtilsFleet.customCreateFleet(faction, params);
		if (fleet == null)
			return null;
		
		fleet.setFaction(Factions.INDEPENDENT, true);
		fleet.setAIMode(true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_SCAVENGER, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_TRADE_FLEET, true);
		Misc.makeLowRepImpact(fleet, "scav");
		
		//float machineryToTake = Math.min(machineryRequired * 1.25f, origin.getCommodityData(Commodities.HEAVY_MACHINERY).getStockpile());
		
		SectorEntityToken entity = origin.getPrimaryEntity();
		entity.getContainingLocation().addEntity(fleet);
		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		
		boolean onTheSpot = true;	// Try to spawn the vulture fleet right on top of the scavengable if we can
		float searchRange = 0;
		if (playerFleet != null && playerFleet.getContainingLocation() == entity.getContainingLocation()) 
		{
			searchRange = playerFleet.getMaxSensorRangeToDetect(fleet)*1f;
			if (searchRange < 1000) searchRange = 1000;
			//log.info("Player fleet could detect " + fleet.getName() + " from " + playerFleet.getMaxSensorRangeToDetect(fleet) + " away");
			onTheSpot = !MathUtils.isWithinRange(target, playerFleet, searchRange);
		}
				
		if (DEBUG_MODE) onTheSpot = true;
		
		if (!onTheSpot) {
			// Pick the position closest to target and outside player view distance
			float angle = VectorUtils.getAngle(playerFleet.getLocation(), target.getLocation());
			Vector2f pos = MathUtils.getPointOnCircumference(playerFleet.getLocation(), searchRange, angle);
			fleet.setLocation(pos.x, pos.y);
		}
		else {
			fleet.setLocation(target.getLocation().x, target.getLocation().y);
		}
		
		VultureFleetData data = new VultureFleetData(fleet);
		data.startingFleetPoints = fleet.getFleetPoints();
		data.source = origin;
		data.target = target;
		data.noWait = true;
		
		VultureFleetAI ai = new VultureFleetAI(fleet, data);
		fleet.addScript(ai);
		log.info("\tSpawned " + fleet.getNameWithFaction() + " of size " + total);
		if (false && ExerelinModPlugin.isNexDev) {
			Global.getSector().getCampaignUI().addMessage("Spawned vulture fleet in " 
					+ origin.getContainingLocation() + " from " + origin.getName() 
					+ ", translocation: " + !onTheSpot);
		}
		
		
		
		return fleet;
	}
	
	@Override
	protected String getActionInsideText(StarSystemAPI system, CampaignFleetAPI fleet) {
		return "debug me";
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
	
	// runcode exerelin.campaign.fleets.VultureFleetManager.getDangerousFleetsInSystem(Global.getSector().getCurrentLocation(), null)
	/**
	 * Lists fleet that are hostile to indies and that (if the specific vulture 
	 * fleet is defined) the vultures can't beat in a fight.
	 * @param loc
	 * @param vulture
	 * @return
	 */
	public static List<CampaignFleetAPI> getDangerousFleetsInSystem(LocationAPI loc, 
			CampaignFleetAPI vulture) 
	{
		List<CampaignFleetAPI> fleets = new ArrayList<>();
		ModularFleetAIAPI ai = null;
		if (vulture != null) {
			ai = (ModularFleetAIAPI)vulture.getAI();
			if (ai == null) return fleets;
		}
		
		// count a fleet if it's hostile to indies
		// if our fleet is defined, also only count the other fleet if we can't beat it in a fight
		for (CampaignFleetAPI fleet : loc.getFleets()) {
			if (!fleet.getFaction().isHostileTo(Factions.INDEPENDENT)) continue;
			
			if (ai != null && ai.getTacticalModule().pickEncounterOption(null, fleet) 
					== EncounterOption.ENGAGE) {
				continue;
			}
			
			fleets.add(fleet);
		}
		//log.info("Dangerous fleet count: " + fleets.size());
		
		return fleets;
	}
	
	public static CampaignFleetAPI getFleetCloseToTarget(Vector2f targetLoc, LocationAPI loc, Collection<CampaignFleetAPI> fleets) 
	{
		for (CampaignFleetAPI fleet : fleets) {
			float distSq = MathUtils.getDistanceSquared(fleet.getLocation(), targetLoc);
			if (distSq < CHECK_HOSTILE_FLEET_RANGE_SQ)
				return fleet;
		}
		return null;
	}
	
	public static SectorEntityToken getClosestScavengable(Vector2f fleetLoc, LocationAPI loc, 
			CampaignFleetAPI vulture) 
	{
		SectorEntityToken closest = null;
		double closestDistSq = 999999999d;
		
		for (SectorEntityToken entity : getDerelictShips(loc, vulture)) {
			float distSq = MathUtils.getDistanceSquared(fleetLoc, entity.getLocation());
			if (distSq < closestDistSq) {
				closestDistSq = distSq;
				closest = entity;
			}
		}
		// debris field
		for (CampaignTerrainAPI terrain : getDebrisFields(loc, vulture)) {
			float distSq = MathUtils.getDistanceSquared(fleetLoc, terrain.getLocation());
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
	 * Gets the derelict ships present in the location. Will not grab ships with 
	 * no expiry, or with nearby fleets that could threaten the vulture fleet.
	 * @param loc
	 * @param vulture
	 * @return
	 */
	public static List<SectorEntityToken> getDerelictShips(LocationAPI loc, CampaignFleetAPI vulture) 
	{
		List<SectorEntityToken> results = new ArrayList<>();
		List<CampaignFleetAPI> hostileFleets = getDangerousFleetsInSystem(loc, vulture);
		
		for (SectorEntityToken entity : loc.getEntitiesWithTag(Tags.SALVAGEABLE)) 
		{
			if (!entity.hasTag(Tags.EXPIRES)) continue;
			if (!Entities.WRECK.equals(entity.getCustomEntityType()))
				continue;
			if (!(entity.getCustomPlugin() instanceof DerelictShipEntityPlugin))
				continue;
			CampaignFleetAPI nearbyHostile = getFleetCloseToTarget(entity.getLocation(), loc, hostileFleets);
			if (nearbyHostile != null) {
				log.info("Entity " + entity.getName() + " has unfriendly fleet " 
						+ nearbyHostile.getNameWithFactionKeepCase() + " nearby, skip");
				continue;
			}
			results.add(entity);
		}
		return results;
	}
	
	/**
	 * Gets the debris fields in the location which are valid targets for scavenging. 
	 * Will not include permanent or already-scavenged debris fields, or those generated 
	 * from any source other than combat, or those with hostile-to-independents fleets nearby.
	 * @param loc
	 * @param vulture
	 * @return
	 */
	public static List<CampaignTerrainAPI> getDebrisFields(LocationAPI loc, CampaignFleetAPI vulture)
	{
		List<CampaignTerrainAPI> results = new ArrayList<>();
		List<CampaignFleetAPI> hostileFleets = getDangerousFleetsInSystem(loc, vulture);
		
		for (CampaignTerrainAPI terrain : loc.getTerrainCopy()) {
			if (!terrain.getType().equals(Terrain.DEBRIS_FIELD)) continue;
			
			DebrisFieldTerrainPlugin plugin = (DebrisFieldTerrainPlugin)terrain.getPlugin();
			if (plugin.isScavenged() || plugin.isFadingOut()) continue;
			
			DebrisFieldParams params = ((DebrisFieldTerrainPlugin)terrain.getPlugin()).getParams();
			if (params.lastsDays > 60 || params.source != DebrisFieldSource.BATTLE) continue;
			
			CampaignFleetAPI nearbyHostile = getFleetCloseToTarget(terrain.getLocation(), loc, hostileFleets);
			if (nearbyHostile != null) {
				log.info("Debris field has unfriendly fleet " 
						+ nearbyHostile.getNameWithFactionKeepCase() + " nearby, skip");
				continue;
			}
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
		for (SectorEntityToken entity : getDerelictShips(loc, null)) 
		{
			float shipValue = getShipScavengeScore(entity);
			score += shipValue;
		}
		
		// debris field
		for (CampaignTerrainAPI terrain : getDebrisFields(loc, null)) 
		{
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
	
	public static CampaignFleetAPI getRandomFleetToFollow(CampaignFleetAPI scav) 
	{
		WeightedRandomPicker<CampaignFleetAPI> picker = new WeightedRandomPicker<>();
		for (CampaignFleetAPI fleet : scav.getContainingLocation().getFleets()) 
		{
			if (fleet.isHostileTo(scav)) continue;
			if (fleet.getBattle() != null)
				picker.add(fleet, 4);
			else if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_RAIDER))
				picker.add(fleet);
			else if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET))
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
			if (raid.getFaction().isHostileTo(Factions.INDEPENDENT)) continue;
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
			if (NexUtilsFaction.isPirateOrTemplarFaction(market.getFactionId()))
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
	protected void updateSpawnRateMult() {
		super.updateSpawnRateMult();
		spawnRateMult *= 5f * Global.getSettings().getFloat("nex_vultureSpawnRateMult");
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

			if (shipData.sModProb > 0 && random.nextFloat() < shipData.sModProb) {
				int num = 1;
				float r = random.nextFloat();
				if (r > 0.85f) {
					num = 3;
				} else if (num > 0.5f) {
					num = 2;
				}

				WeightedRandomPicker<String> picker = new WeightedRandomPicker<String>(random);
				for (String id : variant.getHullMods()) {
					HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
					if (spec.isHidden()) continue;
					if (spec.isHiddenEverywhere()) continue;
					if (spec.hasTag(Tags.HULLMOD_DMOD)) continue;
					if (variant.getPermaMods().contains(spec.getId())) continue;
					picker.add(id, spec.getCapitalCost());
				}
				for (int i = 0; i < num && !picker.isEmpty(); i++) {
					String id = picker.pickAndRemove();
					variant.addPermaMod(id, true);
					//variant.getPermaMods().add(id);
				}
			}


			if (shipData.pruneWeapons) {
				float retain = getFighterWeaponRetainProb(shipData.condition);
				FleetEncounterContext.prepareShipForRecovery(member, false, false, false, retain, retain, random);
				member.getVariant().autoGenerateWeaponGroups();
			}
		}
	}
}