package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import static com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.pickDerelictCondition;
import static com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.pickVariant;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.LocationType;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.StarSystemData;
import static com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.addSalvageEntity;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.StringHelper;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GraveyardWithMemorial extends BaseLandmarkDef {
	
	public static final Color BEACON_COLOR = new Color(128, 128, 128, 128);
	
	protected static final int MAX_TRIES = 5;
	
	@Override
	public boolean isApplicableToEntity(SectorEntityToken entity)
	{
		MarketAPI market = entity.getMarket();
		if (market == null || market.isPlanetConditionMarketOnly())
			return false;
		if (market.getSize() < 4)
			return false;
		
		String factionId = entity.getFaction().getId();
		return !NexUtilsFaction.isPirateOrTemplarFaction(factionId) && !NexUtilsFaction.isFactionHostileToAll(factionId);
	}
	
	@Override
	public int getCount() {
		int marketCount = Global.getSector().getEconomy().getMarketsCopy().size();
		return (int)Math.ceil(marketCount *= 0.05f);
	}
	
	/**
	 * Gets the factions whose ships should appear in the graveyard
	 * @param system
	 * @return
	 */
	protected String[] getFactions(StarSystemAPI system)
	{
		WeightedRandomPicker<String> factions = new WeightedRandomPicker<>(random);
		Map<String, List<String>> enemiesOfFactions = new HashMap<>();
		List<MarketAPI> markets = Misc.getMarketsInLocation(system);
		
		for (MarketAPI market : markets)
		{
			String factionId = getNonDerelictFaction(market);
			if (NexUtilsFaction.isPirateOrTemplarFaction(factionId))
				continue;
			if (!enemiesOfFactions.containsKey(factionId))
			{
				List<String> enemies = DiplomacyManager.getFactionsAtWarWithFaction(factionId, false, true, false);
				if (enemies.isEmpty()) continue;
				enemiesOfFactions.put(factionId, enemies);
			}
			
			factions.add(factionId);
		}
		String faction1 = factions.pick();
		if (faction1 == null) return null;
		
		WeightedRandomPicker<String> factions2 = new WeightedRandomPicker<>(random);
		factions2.addAll(enemiesOfFactions.get(faction1));
		String faction2 = factions2.pick();
		
		if (faction2 == null) return null;
		
		return new String[] {faction1, faction2};
	}
	
	public DerelictShipEntityPlugin.DerelictShipData createRandom(String factionId, Random random, float sModProb) {
		if (random == null) random = new Random();
		String variantId = pickVariantId(factionId);
		
		ShipRecoverySpecial.ShipCondition condition = pickDerelictCondition(random);
		
		ShipRecoverySpecial.PerShipData ship = new ShipRecoverySpecial.PerShipData(variantId, condition, sModProb);
		
		return new DerelictShipEntityPlugin.DerelictShipData(ship, true);
	}
	
	protected String pickVariantId(String factionId) {
		String variantId = pickVariant(factionId, random, 
				ShipRoles.COMBAT_SMALL, 12f,
				ShipRoles.COMBAT_FREIGHTER_SMALL, 3f,
				ShipRoles.COMBAT_MEDIUM, 8f,
				ShipRoles.COMBAT_FREIGHTER_MEDIUM, 2f,
				ShipRoles.COMBAT_LARGE, 5f,
				ShipRoles.COMBAT_FREIGHTER_LARGE, 1f,
				//ShipRoles.COMBAT_CAPITAL, 2f,
				ShipRoles.CARRIER_SMALL,6f,
				ShipRoles.CARRIER_MEDIUM, 4f,
				ShipRoles.CARRIER_LARGE, 2f,
				ShipRoles.PHASE_SMALL, 4f,
				ShipRoles.PHASE_MEDIUM, 2f,
				ShipRoles.PHASE_LARGE, 1f,
				ShipRoles.PHASE_CAPITAL, .5f,
				ShipRoles.TANKER_SMALL, 2f,
				ShipRoles.TANKER_MEDIUM, 1f,
				ShipRoles.TANKER_LARGE, .51f,
				ShipRoles.FREIGHTER_SMALL, 2f,
				ShipRoles.FREIGHTER_MEDIUM, 1f,
				ShipRoles.FREIGHTER_LARGE, .5f,
				ShipRoles.PERSONNEL_SMALL, 2f,
				ShipRoles.PERSONNEL_MEDIUM, 1f,
				ShipRoles.PERSONNEL_LARGE, .5f
		);
		return variantId;
	}
	
	// copied from BaseThemeGenerator.addShipGraveyard
	public void generateShipsForGraveyard(StarSystemData data, SectorEntityToken focus, WeightedRandomPicker<String> factions) {
		int numShips = random.nextInt(5) + 3;
		
		WeightedRandomPicker<Float> bands = new WeightedRandomPicker<Float>(random);
		for (int i = 0; i < numShips + 5; i++) {
			bands.add(new Float(140 + i * 20), (i + 1) * (i + 1));
		}
		
		for (int i = 0; i < numShips; i++) {
			float radius = bands.pickAndRemove();
			String factionId = factions.pick();
			DerelictShipEntityPlugin.DerelictShipData params = createRandom(factionId, 
					random, DerelictShipEntityPlugin.getDefaultSModProb());
			if (params != null) {
				try {
					CustomCampaignEntityAPI entity = (CustomCampaignEntityAPI) addSalvageEntity(random,
										focus.getContainingLocation(),
										Entities.WRECK, Factions.NEUTRAL, params);
					entity.setDiscoverable(true);
					float orbitDays = radius / (5f + random.nextFloat() * 10f);
					entity.setCircularOrbit(focus, random.nextFloat() * 360f, radius, orbitDays);
					BaseThemeGenerator.AddedEntity added = new BaseThemeGenerator.AddedEntity(entity, null, Entities.WRECK);
					data.generated.add(added);
				} catch (Exception ex) {
					log.error("Failed to spawn graveyard derelict for faction " + factionId);
					log.error("  Ship variant:" + params.ship.variantId);
				}
			}
		}
	}
	
	/**
	 * Creates a ship graveyard, debris field and beacon
	 * @param system
	 */
	public void createGraveyard(StarSystemAPI system)
	{
		StarSystemData data = BaseThemeGenerator.computeSystemData(system);
		
		// find a location for the field (uses vanilla procgen code)
		LinkedHashMap<LocationType, Float> weights = new LinkedHashMap<LocationType, Float>();
		weights.put(LocationType.IN_ASTEROID_BELT, 5f);
		weights.put(LocationType.IN_ASTEROID_FIELD, 5f);
		weights.put(LocationType.IN_RING, 5f);
		weights.put(LocationType.IN_SMALL_NEBULA, 5f);
		weights.put(LocationType.STAR_ORBIT, 20f);
		WeightedRandomPicker<BaseThemeGenerator.EntityLocation> locs = BaseThemeGenerator.getLocations(random, data.system, null, 1000f, weights);
		BaseThemeGenerator.EntityLocation loc = locs.pick();
		
		if (loc == null) return;
		
		// stuff will orbit around this token
		SectorEntityToken token = data.system.createToken(0, 0);
		data.system.addEntity(token);
		BaseThemeGenerator.setEntityLocation(token, loc, null);
		
		// factions appearing in the field
		String[] factions = getFactions(system);
		if (factions == null) return;
		
		WeightedRandomPicker<String> factionPicker = new WeightedRandomPicker<>(random);
		factionPicker.add(factions[0]);
		factionPicker.add(factions[1]);
		
		// graveyard proper
		generateShipsForGraveyard(data, token, factionPicker);
		
		// debris field
		// don't want, it gets in the way (blocks beacon tooltip)
		/*
		DebrisFieldTerrainPlugin.DebrisFieldParams params = new DebrisFieldTerrainPlugin.DebrisFieldParams(
						350f, // field radius - should not go above 1000 for performance reasons
						1f, // density, visual - affects number of debris pieces
						10000000f, // duration in days 
						0f); // days the field will keep generating glowing pieces
		params.source = DebrisFieldTerrainPlugin.DebrisFieldSource.MIXED;
		params.baseSalvageXP = 250; // base XP for scavenging in field
		SectorEntityToken debris = Misc.addDebrisField(system, params, random);
		debris.setCircularOrbit(token, 0, 1, 360);
		*/
		
		// beacon
		CustomCampaignEntityAPI beacon = system.addCustomEntity(null, null, Entities.WARNING_BEACON, factions[0]);
		beacon.setCircularOrbitWithSpin(token, 0, 1, 360, 40, 45);
		beacon.setName(StringHelper.getString("exerelin_landmarks", "memorialBeacon"));
		beacon.setCustomDescriptionId("nex_memorial_beacon");
		beacon.getMemoryWithoutUpdate().set("$nex_memorialBeacon", true);
		beacon.getMemoryWithoutUpdate().set("$nex_memorialFaction1", NexUtilsFaction.getFactionShortName(factions[0]));
		beacon.getMemoryWithoutUpdate().set("$nex_memorialFaction2", NexUtilsFaction.getFactionShortName(factions[1]));
		beacon.setDiscoverable(true);
		
		Misc.setWarningBeaconGlowColor(beacon, BEACON_COLOR);
		Misc.setWarningBeaconPingColor(beacon, BEACON_COLOR);
		
		log.info("Spawning ship graveyard with memorial in system " + system.getBaseName());		
	}
	
	public List<StarSystemAPI> getRandomSystems() {
		WeightedRandomPicker<StarSystemAPI> picker = new WeightedRandomPicker<>(random);
		for (StarSystemAPI system : Global.getSector().getStarSystems())
		{
			if (Misc.getMarketsInLocation(system).isEmpty())
				continue;
			picker.add(system);
		}
		List<StarSystemAPI> results = new ArrayList<>();
		int count = getCount();
		for (int i=0; i<count; i++)
		{
			if (picker.isEmpty()) break;
			results.add(picker.pickAndRemove());
		}
		
		return results;
	}
	
	@Override
	public void createAll() {
		List<StarSystemAPI> systems = getRandomSystems();
		for (StarSystemAPI system : systems)
			createGraveyard(system);
	}
}
