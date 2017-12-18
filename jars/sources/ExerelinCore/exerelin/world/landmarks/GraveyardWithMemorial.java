package exerelin.world.landmarks;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.LocationType;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.StarSystemData;
import com.fs.starfarer.api.impl.campaign.procgen.themes.RuinsThemeGenerator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.DiplomacyManager;
import exerelin.utilities.ExerelinUtilsFaction;
import exerelin.utilities.StringHelper;
import static exerelin.world.landmarks.BaseLandmarkDef.log;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
		return !ExerelinUtilsFaction.isPirateOrTemplarFaction(factionId) && !ExerelinUtilsFaction.isFactionHostileToAll(factionId);
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
			String factionId = market.getFactionId();
			if (ExerelinUtilsFaction.isPirateOrTemplarFaction(factionId))
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
	
	/**
	 * Creates a ship graveyard, debris field and beacon
	 * @param system
	 */
	public void createGraveyard(StarSystemAPI system)
	{
		RuinsThemeGenerator generator = new RuinsThemeGenerator();
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
		generator.addShipGraveyard(data, token, factionPicker);
		
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
		beacon.getMemoryWithoutUpdate().set("$nex_memorialFaction1", ExerelinUtilsFaction.getFactionShortName(factions[0]));
		beacon.getMemoryWithoutUpdate().set("$nex_memorialFaction2", ExerelinUtilsFaction.getFactionShortName(factions[1]));
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
