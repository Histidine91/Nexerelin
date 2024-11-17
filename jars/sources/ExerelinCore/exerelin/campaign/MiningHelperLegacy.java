package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.intel.raid.AssembleStage;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.magiclib.util.MagicSettings;
import exerelin.ExerelinConstants;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.NexUtilsAstro;
import exerelin.utilities.NexUtilsFaction;
import exerelin.utilities.NexUtilsFleet;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

import java.io.IOException;
import java.util.*;

// actually now same as the Stellar Industrialist one, except for e.g. file paths
public class MiningHelperLegacy {
	
	protected static final String CONFIG_FILE = "data/config/exerelin/miningConfig.json";
	protected static final String MINING_SHIP_DEFS = "data/config/exerelin/mining_ships.csv";
	protected static final String MINING_WEAPON_DEFS = "data/config/exerelin/mining_weapons.csv";
	protected static final String RESOURCE_DEFS = "data/config/exerelin/mining_resources.csv";
	protected static final String EXHAUSTION_DATA_KEY = "nex_miningExhaustion";	// exhaustion list is a <SectorEntityToken, Float> map
	protected static final String MINING_HARASS_KEY = "$nex_mining_harass";
	
	public static final boolean DEBUG_MODE = false;
	
	public static final Set<String> OUTPUT_COMMODITIES = new HashSet<>(Arrays.asList(
			new String[]{Commodities.ORE, Commodities.RARE_ORE, Commodities.VOLATILES, Commodities.ORGANICS}
	));
	public static final List<String> ROLES_FOR_FIGHTERS = Arrays.asList(
			new String[]{
				ShipRoles.COMBAT_SMALL, 
				ShipRoles.FREIGHTER_SMALL, 
				ShipRoles.COMBAT_FREIGHTER_SMALL, 
				ShipRoles.CARRIER_SMALL,
				ShipRoles.COMBAT_MEDIUM,
				ShipRoles.FREIGHTER_MEDIUM, 
				ShipRoles.COMBAT_FREIGHTER_MEDIUM, 
				ShipRoles.CARRIER_MEDIUM,
				ShipRoles.CARRIER_LARGE,
				ShipRoles.FREIGHTER_LARGE,
				ShipRoles.COMBAT_FREIGHTER_LARGE,
				ShipRoles.CARRIER_LARGE,
				ShipRoles.COMBAT_CAPITAL,
		}
	);
	public static final Set<String> CACHE_DISALLOWED_FACTIONS = new HashSet(Arrays.asList(
			new String[]{
				"templars",
				"exigency",
				"exipirated",
				// not really needed since it should filter out all not-in-intel-tab factions
				Factions.DERELICT,
				Factions.REMNANTS,
				Factions.POOR,
				Factions.NEUTRAL,
		}
	));
	
	public static final float MAX_EXHAUSTION = 0.9f;
	public static final float HAZARD_TO_DANGER_MULT = 0.6f;
	
	protected static float miningProductionMult = 2f;
	protected static float cacheSizeMult = 1;
	protected static float baseCacheChance = 0.1f;
	protected static float baseAccidentChance = 0.5f;
	protected static float baseAccidentCRLoss = 0.1f;
	//protected static float baseAccidentSupplyLoss = 12.5f;
	protected static float baseAccidentHullDamage = 400;
	protected static float exhaustionPer100MiningStrength = 0.04f;
	protected static float renewRatePerDay = 0.002f;
	protected static float planetDangerMult = 1.25f;	// for non-moon planets
	protected static float planetExhaustionMult = 0.75f;
	protected static float planetRenewMult = 1.25f;
	protected static float xpPerMiningStrength = 10;
	protected static float maxXPPerMine = 1500;
	protected static float machineryPerMiningStrength = 0.4f;

	protected static float miningHarassThreshold = 10f;
	protected static boolean enableMiningHarass = true;
	
	protected static final Map<String, Float> miningWeapons = new HashMap<>();
	protected static final Map<String, Float> miningShips = new HashMap<>();
	protected static final Map<String, Map<String, Float>> miningConditions = new HashMap<>();	// maps each market condition to its resource contents
	protected static final Map<String, Float> miningConditionsCache = new HashMap<>();
	protected static final Set<String> hiddenTools = new HashSet<>();
	
	protected static final List<CacheDef> cacheDefs = new ArrayList<>();
	
	public static final Logger log = Global.getLogger(MiningHelperLegacy.class);
	protected static final WeightedRandomPicker<AccidentType> accidentPicker = new WeightedRandomPicker<>();
	
	static {
		try {
			JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
			miningProductionMult = (float)config.optDouble("miningProductionMult", miningProductionMult);
			cacheSizeMult = (float)config.optDouble("cacheSizeMult", cacheSizeMult);
			baseCacheChance = (float)config.optDouble("baseCacheChance", baseCacheChance);
			baseAccidentChance = (float)config.optDouble("baseAccidentChance", baseAccidentChance);
			xpPerMiningStrength = (float)config.optDouble("xpPerMiningStrength", xpPerMiningStrength);
			maxXPPerMine = (float)config.optDouble("xpPerMiningStrength", maxXPPerMine);
			machineryPerMiningStrength = (float)config.optDouble("machineryPerMiningStrength", machineryPerMiningStrength);
			
			baseAccidentCRLoss = (float)config.optDouble("baseAccidentCRLoss", baseAccidentCRLoss);
			baseAccidentHullDamage = (float)config.optDouble("baseAccidentHullDamage", baseAccidentHullDamage);
			exhaustionPer100MiningStrength = (float)config.optDouble("exhaustionPer100MiningStrength", exhaustionPer100MiningStrength);
			renewRatePerDay = (float)config.optDouble("renewRatePerDay", renewRatePerDay);
			planetDangerMult = (float)config.optDouble("planetDangerMult", planetDangerMult);
			planetExhaustionMult = (float)config.optDouble("planetExhaustionMult", planetExhaustionMult);
			planetRenewMult = (float)config.optDouble("planetRenewMult", planetRenewMult);

			enableMiningHarass = Global.getSettings().getBoolean("nex_enableMiningHarass");
			miningHarassThreshold = Global.getSettings().getFloat("nex_miningHarassThreshold");
			
			loadMiningShips();
			loadMiningWeapons();
			loadHiddenTools();
			
			JSONArray resourcesCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", RESOURCE_DEFS, ExerelinConstants.MOD_ID);
			for(int x = 0; x < resourcesCsv.length(); x++)
			{
				String id = "";
				try {
					JSONObject row = resourcesCsv.getJSONObject(x);
					id = row.getString("id");
					if (id.isEmpty()) continue;

					Map<String, Float> resources = new HashMap<>();
					Iterator keys = row.keys();
					while (keys.hasNext())
					{
						String commodityId = (String)keys.next();
						if ("id".equals(commodityId) || "cache".equals(commodityId)) continue;
						float value = (float)row.optDouble(commodityId, 0);
						if (value > 0) resources.put(commodityId, value);
					}
					miningConditions.put(id, resources);
					miningConditionsCache.put(id, (float)row.optDouble("cache", 0));
				} catch (JSONException ex) {
					log.error("Error loading market condition entry " + id, ex);
				}
			}
		} catch (IOException | JSONException ex) {
			log.error("Error loading market condition data", ex);
		}
		
		initCacheDefs();
		accidentPicker.add(AccidentType.HULL_DAMAGE, 1);
		accidentPicker.add(AccidentType.CR_LOSS, 1.5f);
		accidentPicker.add(AccidentType.CREW_LOSS, 1.5f);
		accidentPicker.add(AccidentType.MACHINERY_LOSS, 2f);
	}
	
	public static void loadMiningShips()
	{
		// Legacy CSV system
		try {
			JSONArray miningShipsCsv = Global.getSettings().getMergedSpreadsheetDataForMod(
					"id", MINING_SHIP_DEFS, ExerelinConstants.MOD_ID);
			for(int x = 0; x < miningShipsCsv.length(); x++)
			{
				String shipId = "<unknown>";
				try {
					JSONObject row = miningShipsCsv.getJSONObject(x);
					shipId = row.getString("id");
					if (shipId.isEmpty()) continue;
					float strength = (float)row.getDouble("strength");
					miningShips.put(shipId, strength);
					if (row.optBoolean("hidden", false))
					{
						hiddenTools.add(shipId);
					}
				} catch (JSONException ex) {
					log.error("Error loading mining ship " + shipId, ex);
				}
			}
		} catch (IOException | JSONException ex) {
			log.error("Error loading mining ships", ex);
		} catch (RuntimeException rex) {
			// file not found, do nothing
		}
		
		// new MagicLib system
		Map<String, Float> strengths = MagicSettings.getFloatMap(ExerelinConstants.MOD_ID, "mining_ship_strengths");
		miningShips.putAll(strengths);
	}
	
	public static void loadMiningWeapons()
	{
		// Legacy CSV system
		try {
			JSONArray miningWeaponsCsv = Global.getSettings().getMergedSpreadsheetDataForMod(
					"id", MINING_WEAPON_DEFS, ExerelinConstants.MOD_ID);
			for(int x = 0; x < miningWeaponsCsv.length(); x++)
			{
				String weaponId = "<unknown>";
				try {
					JSONObject row = miningWeaponsCsv.getJSONObject(x);
					weaponId = row.getString("id");
					if (weaponId.isEmpty()) continue;
					float strength = (float)row.getDouble("strength");
					miningWeapons.put(weaponId, strength);
					if (row.optBoolean("hidden", false))
					{
						hiddenTools.add(weaponId);
					}
				} catch (JSONException ex) {
					log.error("Error loading mining weapon " + weaponId + ": ", ex);
				}
			}
		} catch (IOException | JSONException ex) {
			log.error("Error loading mining weapons", ex);
		} catch (RuntimeException rex) {
			// file not found, do nothing
		}
		
		// new MagicLib system
		Map<String, Float> strengths = MagicSettings.getFloatMap(ExerelinConstants.MOD_ID, "mining_weapon_strengths");
		miningWeapons.putAll(strengths);
	}
	
	public static void loadHiddenTools() {
		List<String> hidden = MagicSettings.getList(ExerelinConstants.MOD_ID, "mining_hidden_ships_and_weapons");
		hiddenTools.addAll(hidden);
	}
		
	public static void initCacheDefs()
	{
		cacheDefs.add(new CacheDef("weapon", CacheType.WEAPON, null, 1, 0.8f));
		cacheDefs.add(new CacheDef("frigate", CacheType.FRIGATE, null, 1, 0.2f));
		cacheDefs.add(new CacheDef("fighters", CacheType.FIGHTER_WING, null, 1, 0.3f));
		cacheDefs.add(new CacheDef("hullmod", CacheType.HULLMOD, null, 1, 0.3f));
		cacheDefs.add(new CacheDef("supplies", CacheType.COMMODITY, Commodities.SUPPLIES, 3, 1f));
		cacheDefs.add(new CacheDef("fuel", CacheType.COMMODITY, Commodities.FUEL, 4, 1f));
		cacheDefs.add(new CacheDef("food", CacheType.COMMODITY, Commodities.FOOD, 5, 1f));
		cacheDefs.add(new CacheDef("hand_weapons", CacheType.COMMODITY, Commodities.HAND_WEAPONS, 1, 1f));
		cacheDefs.add(new CacheDef("heavy_machinery", CacheType.COMMODITY, Commodities.HEAVY_MACHINERY, 2, 0.7f));
		cacheDefs.add(new CacheDef("rare_metals", CacheType.COMMODITY, Commodities.RARE_METALS, 1.5f, 0.7f));
		cacheDefs.add(new CacheDef("drugs", CacheType.COMMODITY, Commodities.DRUGS, 1.25f, 0.5f));
		cacheDefs.add(new CacheDef("gamma_core", CacheType.COMMODITY, Commodities.GAMMA_CORE, 0, 0.05f));
		cacheDefs.add(new CacheDef("beta_core", CacheType.COMMODITY, Commodities.BETA_CORE, 0, 0.01f));
	}
	
	
	public static Map<SectorEntityToken, Float> getExhaustionMap() {
		Map<String, Object> data = Global.getSector().getPersistentData();
		Map<SectorEntityToken, Float> exhaustionMap;
		if (data.containsKey(EXHAUSTION_DATA_KEY))
			exhaustionMap = (Map<SectorEntityToken, Float>)data.get(EXHAUSTION_DATA_KEY);
		else {
			exhaustionMap = new HashMap<>();
			data.put(EXHAUSTION_DATA_KEY, exhaustionMap);
		}
		return exhaustionMap;
	}
	
	public static boolean canMine(SectorEntityToken entity)
	{
		if (entity instanceof AsteroidAPI) return true;
		if (entity.getMarket() != null && !entity.getMarket().isPlanetConditionMarketOnly()) return false;
		if (entity instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)entity;
			if (planet.isStar()) return false;
			return true;
			//if (planet.isMoon()) return true;
			//if (planet.isGasGiant()) return true;
		}
		return false;
	}
	
	public static boolean isHidden(String shipOrWeaponId)
	{
		return hiddenTools.contains(shipOrWeaponId);
	}
	
	/**
	 * Adds the contents of 'added' to 'base'
	 * @param base
	 * @param added
	 */
	protected static void addToResourcesResult(Map<String, Float> base, Map<String, Float> added)
	{
		if (base == null || added == null)
			return;
		for (Map.Entry<String, Float> entry : added.entrySet())
		{
			String commodityId = entry.getKey();
			float value = entry.getValue();
			
			if (!base.containsKey(commodityId))
			{
				base.put(commodityId, value);
			}
			else
			{
				base.put(commodityId, value + base.get(commodityId));
			}
		}
	}
	
	/**
	 * Returns the resource output from a market condition
	 * @param conditionId
	 * @return Map of commodityIDs to amount of resources
	 */
	protected static Map<String, Float> getResourcesForCondition(String conditionId)
	{
		if (!miningConditions.containsKey(conditionId)) return null;
		return new HashMap<>(miningConditions.get(conditionId));
	}
	
	protected static Map<String, Float> processCondition(String conditionId, float mult, MiningReport report)
	{
		Map<String, Float> resources = getResourcesForCondition(conditionId);
		if (resources == null) return null;

		if (mult != 1)
		{
			Map<String, Float> resourcesAdjusted = new HashMap<>();
			
			for (Map.Entry<String, Float> tmp : resources.entrySet())
			{
				float thisMult = mult * MathUtils.getRandomNumberInRange(0.85f, 1.15f);
				resourcesAdjusted.put(tmp.getKey(), tmp.getValue() * thisMult);
			}
			resources = resourcesAdjusted;
		}
		if (report != null) 
		{
			addToResourcesResult(report.totalOutput, resources);
			report.outputByCondition.put(conditionId, resources);
		}
		
		return resources;
	}
	
	/**
	 * Fills a mining report with the resource output possible from mining this entity.
	 * Exhaustion is ignored (unless included in mult).
	 * @param entity The entity being mined
	 * @param mult Resource output multiplier
	 * @param report A mining report to fill out
	 */
	public static void getResources(SectorEntityToken entity, float mult, MiningReport report)
	{
		if (entity == null) return;
		boolean isGasGiant = (entity instanceof PlanetAPI && ((PlanetAPI)entity).isGasGiant());
		
		MarketAPI market = entity.getMarket();
		if (market != null)
		{
			for (MarketConditionAPI cond : market.getConditions())
			{
				if (!cond.isSurveyed() && report.isPlayer) continue;
				String id = cond.getId();
				processCondition(id, mult, report);
			}
		}
		if (entity instanceof AsteroidAPI)
		{
			processCondition("asteroid", mult, report);
		}
		/*
		if (isGasGiant)
		{
			if (totalResources.containsKey(Commodities.VOLATILES))
				totalResources.put(Commodities.VOLATILES, totalResources.get(Commodities.VOLATILES) * 2);
		}
		*/
	}
	
	public static float getCacheChanceMod(SectorEntityToken entity, boolean isPlayer)
	{
		float chance = 0;
		MarketAPI market = entity.getMarket();
		if (market != null)
		{
			for (MarketConditionAPI cond : market.getConditions())
			{
				if (!cond.isSurveyed() && isPlayer) continue;
				String id = cond.getId();
				if (miningConditionsCache.containsKey(id))
					chance += miningConditionsCache.get(id);
			}
		}
		if (entity instanceof AsteroidAPI)
		{
			chance += miningConditionsCache.get("asteroid");
		}
		
		return chance;
	}
	
	public static float getDanger(SectorEntityToken entity)
	{
		if (entity instanceof AsteroidAPI || entity.getMarket() == null) return 0.2f;
		float val = entity.getMarket().getHazardValue();
		if (entity instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI) entity;
			if (!planet.isMoon()) val *= planetDangerMult;
		}
		//else if (((PlanetAPI)entity).isGasGiant()) return danger.get("gas_giant");
		/*
		if (machinerySufficiency < 1)
		{
			val *= 1 + (2 * (1 - machinerySufficiency));
		}
		*/
		
		// subtract base 100% hazard, then reduce the remainder
		val -= 1;
		val *= HAZARD_TO_DANGER_MULT;
		if (val < 0) val = 0;
		
		return val;
	}
	
	public static float getExhaustion(SectorEntityToken entity)
	{
		if (entity instanceof AsteroidAPI) return 0;
		Map<SectorEntityToken, Float> exhaustionMap = getExhaustionMap();
		if (exhaustionMap.containsKey(entity))
			return exhaustionMap.get(entity);
		return 0;
	}
	
	public static float getRequiredMachinery(float strength)
	{
		return strength * machineryPerMiningStrength;
	}
	
	public static float getMachinerySufficiency(CampaignFleetAPI fleet)
	{
		float requiredMachinery = getRequiredMachinery(getFleetMiningStrength(fleet));
		float availableMachinery = fleet.getCargo().getCommodityQuantity(Commodities.HEAVY_MACHINERY);
		return Math.min(availableMachinery/requiredMachinery, 1);
	}
	
	public static MiningReport getMiningReport(CampaignFleetAPI fleet, SectorEntityToken entity, float strength)
	{
		MiningReport report = new MiningReport();
		
		report.danger = getDanger(entity);
		report.exhaustion = getExhaustion(entity);
		if (fleet != null && fleet.getFaction().isPlayerFaction())
			report.isPlayer = true;
		strength *= (1 - report.exhaustion);
		getResources(entity, strength, report);
		
		return report;
	}
	
	public static Map<String, Float> getMiningShipsCopy()
	{
		return new HashMap<>(miningShips);
	}
	
	public static Map<String, Float> getMiningWeaponsCopy()
	{
		return new HashMap<>(miningWeapons);
	}
	
	public static float getVariantMiningStrength(ShipVariantAPI variant)
	{
		float strength = 0;
		String hullId = variant.getHullSpec().getHullId();
		String baseHullId = variant.getHullSpec().getBaseHullId();
		if (miningShips.containsKey(hullId))
			strength += miningShips.get(hullId);
		else if (miningShips.containsKey(baseHullId))
			strength += miningShips.get(baseHullId);
		
		Collection<String> weaponSlots = variant.getFittedWeaponSlots();
		for (String slot : weaponSlots)
		{
			String weaponId = variant.getWeaponSpec(slot).getWeaponId();
			if (miningWeapons.containsKey(weaponId))
				strength+= miningWeapons.get(weaponId);
		}
		for (String wing: variant.getFittedWings())
		{
			strength += getWingMiningStrength(wing);
		}
		
		return strength;
	}
	
	public static float getWingMiningStrength(String wing)
	{
		FighterWingSpecAPI wingSpec = Global.getSettings().getFighterWingSpec(wing);
		return getWingMiningStrength(wingSpec);
	}
	
	public static float getWingMiningStrength(FighterWingSpecAPI wingSpec)
	{
		ShipVariantAPI variant = wingSpec.getVariant();
		
		float strength = getVariantMiningStrength(variant);
		
		float count = wingSpec.getNumFighters();
		return strength * count;
	}

	public static float getMiningCRMult(FleetMemberAPI member) {
		float cr = member.getRepairTracker().getCR();
		return cr / 0.7f;
	}
	
	public static float getShipMiningStrength(FleetMemberAPI member, boolean useCRMod)
	{
		if (member.isMothballed()) return 0;
		
		float crMult = 1;
		if (useCRMod)
		{
			crMult = getMiningCRMult(member);
		}
		
		ShipVariantAPI variant = member.getVariant();
		float strength = getVariantMiningStrength(variant);
		for (int i=0; i<variant.getModuleSlots().size(); i++)
		{
			if (member.getStatus().isDetached(i)) continue;
			String moduleSlot = member.getVariant().getModuleSlots().get(i);
			ShipVariantAPI moduleVar = member.getVariant().getModuleVariant(moduleSlot);
			if (moduleVar != null)
				strength += getVariantMiningStrength(moduleVar);
		}
		
		return strength * crMult;
	}
	
	public static float getFleetMiningStrength(CampaignFleetAPI fleet)
	{
		float strength = 0;
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy())
		{
			strength += getShipMiningStrength(member, true);
		}
		return strength;
	}
	
	public static FleetMemberAPI getRandomSmallShip()
	{
		WeightedRandomPicker<FleetMemberAPI> shipPicker = new WeightedRandomPicker<>();
		
		WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();

		rolePicker.add(ShipRoles.CIV_RANDOM, 1f);
		rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1f);
		rolePicker.add(ShipRoles.TANKER_SMALL, 1f);
		rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1f);
		rolePicker.add(ShipRoles.COMBAT_SMALL, 5f);
			
		String role = rolePicker.pick();
		
		List<FactionAPI> factions = Global.getSector().getAllFactions();
		Set<String> restrictedShips = PrismMarket.getRestrictedShips();
		for (FactionAPI faction : factions)
		{
			if (!faction.isShowInIntelTab()) continue;
			if (CACHE_DISALLOWED_FACTIONS.contains(faction.getId())) continue;
			
			List<ShipRolePick> picks = faction.pickShip(role, new ShipPickParams(ShipPickMode.PRIORITY_THEN_ALL), null, rolePicker.getRandom());
			for (ShipRolePick pick : picks) 
			{				
				if (restrictedShips.contains(pick.variantId)) continue;
				FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, pick.variantId);
				ShipHullSpecAPI hull = member.getHullSpec();
				if (hull.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) continue;
				if (hull.hasTag(Tags.RESTRICTED)) continue;
				
				float cost = member.getBaseValue();
				shipPicker.add(member, 10000/cost);
			}
		}
		return shipPicker.pick();
	}
	
	public static FighterWingSpecAPI getRandomFighter()
	{
		List<FactionAPI> factions = Global.getSector().getAllFactions();
		Set<String> restrictedShips = PrismMarket.getRestrictedShips();
		WeightedRandomPicker<FighterWingSpecAPI> picker = new WeightedRandomPicker();
		for (FactionAPI faction : factions)
		{
			if (!faction.isShowInIntelTab()) continue;
			if (CACHE_DISALLOWED_FACTIONS.contains(faction.getId())) continue;
			
			for (String role : ROLES_FOR_FIGHTERS) 
			{
				try {
					List<ShipRolePick> picks = faction.pickShip(role, ShipPickParams.priority());
					for (ShipRolePick pick : picks) {
						FleetMemberType type = FleetMemberType.SHIP;
						if (pick.isFighterWing()) continue;

						FleetMemberAPI member = Global.getFactory().createFleetMember(type, pick.variantId);
						for (String wingId : member.getVariant().getFittedWings()) {
							if (restrictedShips.contains(wingId)) continue;
							FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
							if (spec.getTags().contains(Tags.WING_NO_SELL)) continue;
							if (spec.getTags().contains(Tags.RESTRICTED)) continue;

							picker.add(spec, 6 - spec.getTier());
						}
					}
				} catch (NullPointerException npex) {
					// role doesn't exist, do nothing
				}
			}
		}
		return picker.pick();
	}
	
	public static WeaponSpecAPI getRandomWeapon()
	{
		WeightedRandomPicker<WeaponSpecAPI> weaponPicker = new WeightedRandomPicker<>();
		List<String> weaponIds = Global.getSector().getAllWeaponIds();
		Set<String> restrictedWeapons = PrismMarket.getRestrictedWeapons();
		for (String weaponId : weaponIds)
		{
			if (weaponId.startsWith("tem_")) continue;
			if (restrictedWeapons.contains(weaponId)) continue;
			
			WeaponSpecAPI weapon = Global.getSettings().getWeaponSpec(weaponId);
			if (weapon.getAIHints().contains(AIHints.SYSTEM)) continue;
			if (weapon.getTags().contains(Tags.RESTRICTED)) continue;
			float weight = 10000/weapon.getBaseValue();
			if (weapon.getSize() == WeaponSize.LARGE) weight *= 4;
			else if (weapon.getSize() == WeaponSize.MEDIUM) weight *= 2;
			weaponPicker.add(weapon, weight);
		}
		return weaponPicker.pick();
	}
	
	public static HullModSpecAPI getRandomHullmod()
	{
		List<FactionAPI> factions = Global.getSector().getAllFactions();
		WeightedRandomPicker<HullModSpecAPI> hullmodPicker = new WeightedRandomPicker<>();
		for (FactionAPI faction : factions)
		{
			if (!faction.isShowInIntelTab()) continue;
			if (CACHE_DISALLOWED_FACTIONS.contains(faction.getId())) continue;
			
			for (String id : faction.getKnownHullMods()) {
				HullModSpecAPI spec = Global.getSettings().getHullModSpec(id);
				if (spec.isHidden()) continue;
				if (spec.isAlwaysUnlocked()) continue;
				if (spec.getTier() > 3) continue;
				hullmodPicker.add(spec, spec.getRarity());
			}
		}
		return hullmodPicker.pick();
	}
	
	
	// copied from FleetEncounterContext
	protected static float computeCrewLossFraction(FleetMemberAPI member,  float hullFraction, float hullDamage) {		
		if (hullFraction == 0) {
			return (0.75f + (float) Math.random() * 0.25f) * member.getStats().getCrewLossMult().getModifiedValue(); 
		}
		return hullDamage * hullDamage * (0.5f + (float) Math.random() * 0.5f) * member.getStats().getCrewLossMult().getModifiedValue();
	}
	
	public static MiningAccident handleAccidents(CampaignFleetAPI fleet, float strength, float danger)
	{
		MiningAccident accident = null;
		
		float accidentChance = MathUtils.getRandomNumberInRange(-1, 3) * strength/10;
		if (accidentChance < 0) return null;
		
		List<FleetMemberAPI> ships = fleet.getFleetData().getCombatReadyMembersListCopy();
		WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>();
		for (FleetMemberAPI ship : ships)
		{
			float shipStrength = getShipMiningStrength(ship, true);
			if (shipStrength > 0) picker.add(ship, shipStrength);
		}
		
		while (accidentChance > 0)
		{
			accidentChance -= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			if (Math.random() < danger*baseAccidentChance)
			{
				if (accident == null) accident = new MiningAccident();
				FleetMemberAPI fm = picker.pick();
				if (fm == null || fm.getStatus() == null) continue;	// null reference protection
				
				AccidentType accidentType = accidentPicker.pick();
				
				// ship takes damage
				if (accidentType == AccidentType.HULL_DAMAGE)
				{
					float hull = fm.getStatus().getHullFraction();
					float hullDamageFactor = 0f;
					float damage = baseAccidentHullDamage * MathUtils.getRandomNumberInRange(0.5f, 1.5f);
					fm.getStatus().applyDamage(damage);
					if (fm.getStatus().getHullFraction() <= 0) 
					{
						fm.getStatus().disable();
						picker.remove(fm);
						fleet.getFleetData().removeFleetMember(fm);
						hullDamageFactor = 1f;
						if (accident.damage.containsKey(fm))
							accident.damage.remove(fm);
						if (accident.crLost.containsKey(fm))
							accident.crLost.remove(fm);
						accident.shipsDestroyed.add(fm);
						
						boolean spawnShip = true;	// cba to calculate fancy chance
						if (spawnShip) {
							DerelictShipData params = new DerelictShipData(new PerShipData(fm.getVariant().getHullVariantId(),
													DerelictShipEntityPlugin.pickBadCondition(null)), false);
							params.durationDays = DerelictShipEntityPlugin.getBaseDuration(fm.getHullSpec().getHullSize());
							CustomCampaignEntityAPI entity = (CustomCampaignEntityAPI) BaseThemeGenerator.addSalvageEntity(
															 fleet.getContainingLocation(),
															 Entities.WRECK, Factions.NEUTRAL, params);
							entity.addTag(Tags.EXPIRES);

							float angle = (float) Math.random() * 360f;
							float speed = 10f + 10f * (float) Math.random();
							Vector2f vel = Misc.getUnitVectorAtDegreeAngle(angle);
							vel.scale(speed);
							entity.getVelocity().set(vel);

							entity.getLocation().x = fleet.getLocation().x + vel.x * 3f;
							entity.getLocation().y = fleet.getLocation().y + vel.y * 3f;
						}
						
					} else {
						float newHull = fm.getStatus().getHullFraction();
						float diff = hull - newHull;
						if (diff < 0) diff = 0;
						hullDamageFactor = diff;
						accident.damage.put(fm, damage);
					}
					
					// kill crew as applicable
					float dead = fm.getCrewComposition().getCrew();
					float lossFraction = computeCrewLossFraction(fm, fm.getStatus().getHullFraction(), hullDamageFactor);
					dead *= lossFraction;
					accident.crewLost += dead;
				}
				// CR loss
				else if (accidentType == AccidentType.CR_LOSS)
				{
					//float crLost = baseAccidentSupplyLoss * ExerelinUtilsShip.getCRPerSupplyUnit(fm, true);
					//crLost *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
					float crLost = baseAccidentCRLoss * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
					HullSize size = fm.getHullSpec().getHullSize();
					if (size == HullSize.DESTROYER) crLost *= 0.5f;
					else if (size == HullSize.CRUISER) crLost *= 0.25f;
					else if (size == HullSize.CAPITAL_SHIP) crLost *= 0.125f;
					
					fm.getRepairTracker().applyCREvent(-crLost, StringHelper.getString("exerelin_mining", "miningAccident"));
					if (!accident.crLost.containsKey(fm))
						accident.crLost.put(fm, 0f);
					accident.crLost.put(fm, accident.crLost.get(fm) + crLost);
				}
				// crew loss
				else if (accidentType == AccidentType.CREW_LOSS)
				{
					float dead = MathUtils.getRandomNumberInRange(1f, 5f) + 0.5f;
					dead *= fleet.getStats().getDynamic().getValue(Stats.NON_COMBAT_CREW_LOSS_MULT);
					dead = Math.min(dead, fleet.getCargo().getTotalCrew());
					CargoAPI cargo = fleet.getCargo();
					cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, Commodities.CREW, (int)dead);
					accident.crewLost += dead;
				}
				// machinery loss
				else if (accidentType == AccidentType.MACHINERY_LOSS)
				{
					int lost = MathUtils.getRandomNumberInRange(1, 3) + MathUtils.getRandomNumberInRange(0, 2);
					lost = Math.min(lost, (int)fleet.getCargo().getCommodityQuantity(Commodities.HEAVY_MACHINERY));
					CargoAPI cargo = fleet.getCargo();
					cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, Commodities.HEAVY_MACHINERY, lost);
					accident.machineryLost += lost;
				}
			}
		}
		
		return accident;
	}
	
	public static List<CacheResult> findCaches(CampaignFleetAPI fleet, float strength, SectorEntityToken entity)
	{
		List<CacheResult> caches = new ArrayList<>();
		
		int numCaches = MathUtils.getRandomNumberInRange(0, 2) + MathUtils.getRandomNumberInRange(0, 1);
		if (numCaches <= 0) numCaches = 1;
		WeightedRandomPicker<CacheDef> cachePicker = new WeightedRandomPicker<>();
		for (CacheDef def: cacheDefs)
		{
			cachePicker.add(def, def.chance);
		}
		for (int i=0; i < numCaches; i++)
		{
			int num = 1;
			String name = "";
			CacheDef def = cachePicker.pickAndRemove();
			if (def.type == CacheType.FRIGATE)
			{
				FleetMemberAPI member = getRandomSmallShip();
				member.getRepairTracker().setMothballed(true);
				fleet.getFleetData().addFleetMember(member);
				fleet.updateCounts();
				name = member.getVariant().getFullDesignationWithHullName();
				def.id = member.getHullId();
			}
			else if (def.type == CacheType.FIGHTER_WING)
			{
				FighterWingSpecAPI spec = getRandomFighter();
				fleet.getCargo().addFighters(spec.getId(), 1);
				name = StringHelper.getStringAndSubstituteToken("exerelin_mining", "LPC", "$fighterName", spec.getVariant().getFullDesignationWithHullName());
				def.id = spec.getId();
			}
			else if (def.type == CacheType.WEAPON)
			{
				WeaponSpecAPI weapon = getRandomWeapon();
				if (weapon.getSize() == WeaponSize.MEDIUM) 
					num = MathUtils.getRandomNumberInRange(1, 3);
				else if (weapon.getSize() == WeaponSize.SMALL) 
					num = MathUtils.getRandomNumberInRange(1, 3) + MathUtils.getRandomNumberInRange(0, 2);
				fleet.getCargo().addWeapons(weapon.getWeaponId(), num);
				//name = weapon.toString();

				// hax to get weapon name
				for (CargoStackAPI stack: fleet.getCargo().getStacksCopy())
				{
					if (stack.getWeaponSpecIfWeapon() == weapon)
					{
						name = stack.getDisplayName();
						break;
					}
				}
				def.id = weapon.getWeaponId();
			}
			else if (def.type == CacheType.HULLMOD)
			{
				HullModSpecAPI mod = getRandomHullmod();
				fleet.getCargo().addHullmods(mod.getId(), 1);
				name = StringHelper.getStringAndSubstituteToken("exerelin_mining", "hullmod", "$hullmod", mod.getDisplayName());
				def.id = mod.getId();
			}
			else if (def.type == CacheType.COMMODITY)
			{
				num = (int)(Math.sqrt(strength) * def.mult * miningProductionMult * cacheSizeMult);
				if (def.mult == 0)
					num = 1;
				fleet.getCargo().addCommodity(def.commodityId, num);
				name = StringHelper.getCommodityName(def.commodityId);
			}
			caches.add(new CacheResult(def, name, num));
		}
		return caches;
	}
	
	public static float applyResourceExhaustion(SectorEntityToken entity, float miningStrength)
	{
		Map<SectorEntityToken, Float> exhaustionMap = getExhaustionMap();
		float currExhaustion = 0;
		if (exhaustionMap.containsKey(entity))
			currExhaustion = exhaustionMap.get(entity);
		
		float delta = miningStrength * exhaustionPer100MiningStrength/100;
		//delta *= getMiningResources(entity).exhaustionRate;
		if (entity instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)entity;
			if (!planet.isMoon())
				delta *= planetExhaustionMult;
		}
		if (delta <= 0) return 0;
		
		float exhaustion = currExhaustion + delta;
		if (exhaustion > MAX_EXHAUSTION) exhaustion = MAX_EXHAUSTION;
		
		exhaustionMap.put(entity, exhaustion);
		
		return (exhaustion - currExhaustion);
	}
	
	public static MiningResult getMiningResults(CampaignFleetAPI fleet, SectorEntityToken entity, float mult, boolean isPlayer)
	{
		float baseStrength = getFleetMiningStrength(fleet) * mult;
		float machineryMult = 0.5f + 0.5f * getMachinerySufficiency(fleet);
		float strength = baseStrength * machineryMult;
		MiningReport report = getMiningReport(fleet, entity, strength * miningProductionMult);
		
		MiningResult result = new MiningResult();
		result.report = report;
		
		// add resources to cargo
		for (Map.Entry<String, Float> tmp : report.totalOutput.entrySet())
		{
			int amount = (int)(float)(tmp.getValue() + 0.5);
			fleet.getCargo().addCommodity(tmp.getKey(), amount);
		}
		
		if (isPlayer)
		{
			float cacheChance = baseCacheChance * (1 + getCacheChanceMod(entity, isPlayer));
			//Global.getSector().getCampaignUI().addMessage("Cache chance: " + cacheChance);
			if (DEBUG_MODE || Math.random() < cacheChance)
			{
				result.cachesFound = findCaches(fleet, strength, entity);
			}
		}
		result.accidents = handleAccidents(fleet, baseStrength, getDanger(entity));
		
		if (isPlayer)
		{
			float xp = 50 + strength * xpPerMiningStrength;
			xp = Math.max(xp, maxXPPerMine);
			fleet.getCommander().getStats().addXP((long) xp);
			fleet.getCommander().getStats().levelUpIfNeeded();
			
			applyResourceExhaustion(entity, strength);
		}

		if (isPlayer && enableMiningHarass) {
			float valueForHarass = 1;	//computeMiningValue(result);
			if (!result.cachesFound.isEmpty()) valueForHarass += result.cachesFound.size();
						
			Float currAmount = Global.getSector().getMemoryWithoutUpdate().getFloat(MINING_HARASS_KEY);
			if (currAmount == null) currAmount = 0f;
			float newVal = currAmount + valueForHarass;
			
			LocationAPI loc = entity.getContainingLocation();
			float needed = miningHarassThreshold + getMiningHarassThresholdModifier(loc);
			
			if (DEBUG_MODE || newVal + MathUtils.getRandomNumberInRange(-3, 3) >= needed) {
				Global.getSector().getMemoryWithoutUpdate().set(MINING_HARASS_KEY, 0);
				
				spawnHarassmentFleet(getHostileFaction(loc), loc);
			} else {
				//Global.getSector().getCampaignUI().addMessage("Mining harass value: " + newVal + "/" + miningHarassThreshold);
				Global.getSector().getMemoryWithoutUpdate().set(MINING_HARASS_KEY, newVal);
			}
		}

		return result;
	}
	
	public static float getMiningHarassThresholdModifier(LocationAPI location) {
		float mod = 0;
		for (MarketAPI market : Misc.getMarketsInLocation(location)) {
			boolean pirate = NexUtilsFaction.isPirateFaction(market.getFactionId());
			
			// pirate markets make harassment more frequent
			if (pirate) mod -=1;
			else {
				if (market.isHidden()) continue;
				// main faction markets make harassment less frequent, unless they're hostile to us in which case they do the opposite
				float thisMod = 1;
				if (Misc.isMilitary(market)) thisMod = 2;
				if (market.getFaction().isHostileTo(Factions.PLAYER)) thisMod *= -1;
				mod += thisMod;
			}
		}
		
		return mod;
	}

	public static float computeMiningValue(MiningResult result){
		float miningValue = 0f;

		for (Map.Entry<String, Float> entry : result.report.totalOutput.entrySet()) {
			miningValue += Global.getSector().getEconomy().getCommoditySpec(entry.getKey()).getBasePrice() * entry.getValue();
		}

		for(CacheResult cacheResult : result.cachesFound){
			switch(cacheResult.def.type){
				case COMMODITY :
					miningValue += Global.getSector().getEconomy().getCommoditySpec(cacheResult.def.id).getBasePrice() * cacheResult.numItems;
					break;
				case HULLMOD :
					miningValue += Global.getSettings().getHullModSpec(cacheResult.def.id).getBaseValue() * cacheResult.numItems;
					break;
				case FRIGATE :
					miningValue += Global.getSettings().getHullSpec(cacheResult.def.id).getBaseValue() * cacheResult.numItems;
					break;
				case FIGHTER_WING :
					miningValue += Global.getSettings().getFighterWingSpec(cacheResult.def.id).getBaseValue() * cacheResult.numItems;
					break;
				case WEAPON :
					miningValue += Global.getSettings().getWeaponSpec(cacheResult.def.id).getBaseValue() * cacheResult.numItems;
					break;
			}
		}

		return miningValue;
	}

	protected static CampaignFleetAPI spawnHarassmentFleet(FactionAPI faction, LocationAPI loc) 
	{
		if (faction == null) return null;
		
		int combat, freighter, tanker, utility;

		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
		float player = NexUtilsFleet.calculatePowerLevel(playerFleet) * 0.4f;
		int capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus());

		combat = Math.round(player * MathUtils.getRandomNumberInRange(0.75f, 0.9f));
		combat = Math.min(120 + capBonus, combat);
		if (faction.getId().equals(Factions.REMNANTS)) {
			combat *= 0.7f;
		}
		
		freighter = Math.round(combat / 20f);
		tanker = Math.round(combat / 30f);
		utility = Math.round(combat / 40f);
		
		String type = FleetTypes.PATROL_SMALL;
		if (combat > AssembleStage.FP_LARGE)
			type = FleetTypes.PATROL_LARGE;
		else if (combat > AssembleStage.FP_MEDIUM)
			type = FleetTypes.PATROL_MEDIUM;

		Vector2f locInHyper = loc.getLocation();
		FleetParamsV3 params = new FleetParamsV3(locInHyper,
				faction.getId(),
				null,
				type,
				combat, // combatPts
				freighter, // freighterPts
				tanker, // tankerPts
				0f, // transportPts
				0f, // linerPts
				utility, // utilityPts
				0f);
		params.ignoreMarketFleetSizeMult = true;

		CampaignFleetAPI fleet = NexUtilsFleet.customCreateFleet(faction, params);

		if (fleet == null)
			return null;

		String targetName = StringHelper.getString("yourFleet");

		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_MAKE_HOSTILE, true);
		fleet.getMemoryWithoutUpdate().set(MemFlags.MEMORY_KEY_LOW_REP_IMPACT, true);

		Vector2f pos = MathUtils.getPointOnCircumference(playerFleet.getLocation(), 
				playerFleet.getMaxSensorRangeToDetect(fleet) * MathUtils.getRandomNumberInRange(1.25f, 1.6f),
				NexUtilsAstro.getRandomAngle());
		fleet.setLocation(pos.x, pos.y);
		loc.addEntity(fleet);
		
		fleet.addAssignment(FleetAssignment.ATTACK_LOCATION, playerFleet, 0.5f);	// make it get a little closer
		fleet.addAssignment(FleetAssignment.INTERCEPT, playerFleet, 15,
				StringHelper.getFleetAssignmentString("intercepting", targetName));
		Misc.giveStandardReturnToSourceAssignments(fleet, false);
		
		return fleet;
	}
	
	protected static void addToPicker(WeightedRandomPicker<String> picker, String id, float wt) {
		log.info("Adding to picker: " + id + ", " + wt);
		picker.add(id, wt);
	}

	protected static FactionAPI getHostileFaction(LocationAPI loc){
		Random random = new Random();
		FactionAPI player = Global.getSector().getPlayerFaction();
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(random);
		boolean core = loc.hasTag(Tags.THEME_CORE);
		
		// local hostile factions
		List<MarketAPI> markets = Misc.getMarketsInLocation(loc);
		for (MarketAPI market : markets) {
			if (!market.getFaction().isHostileTo(player)) continue;
			addToPicker(picker, market.getFactionId(), market.getSize());
		}
		
		// pirates can always show up
		if (player.isHostileTo(Factions.PIRATES)) {
			addToPicker(picker, Factions.PIRATES, 5);
		}
		
		// as can Pathers
		if (player.isHostileTo(Factions.LUDDIC_PATH)) {
			addToPicker(picker, Factions.LUDDIC_PATH, 3.5f);
		}
		
		// beware Remnants
		if (player.isHostileTo(Factions.REMNANTS)) {
			float weight = 0;
			if (loc.hasTag(Tags.THEME_REMNANT_RESURGENT))
				weight = 20;
			else if (loc.hasTag(Tags.THEME_REMNANT_SUPPRESSED))
				weight = 10;
			else if (loc.hasTag(Tags.THEME_REMNANT_DESTROYED))
				weight = 5;
			
			if (weight > 0) addToPicker(picker, Factions.REMNANTS, weight);
		}
		
		// stray derelicts
		if (!core) {
			float weight = 1;
			if (loc.hasTag(Tags.THEME_DERELICT_MOTHERSHIP))
				weight = 4;
			else if (loc.hasTag(Tags.THEME_DERELICT_SURVEY_SHIP))
				weight = 3;
			else if (loc.hasTag(Tags.THEME_DERELICT_PROBES))
				weight = 2;
			
			if (weight > 0) addToPicker(picker, Factions.DERELICT, weight);
		}
		String factionId = picker.pick();
		if (factionId == null) return null;
		
		return Global.getSector().getFaction(factionId);
	}
	
	public static void renewResources(float days)
	{
		Map<SectorEntityToken, Float> exhaustionMap = getExhaustionMap();
		List<SectorEntityToken> toRemove = new ArrayList<>();
		for (Map.Entry<SectorEntityToken, Float> tmp : exhaustionMap.entrySet())
		{
			SectorEntityToken entity = tmp.getKey();
			float currentExhaustion = tmp.getValue();
			float regen = days * renewRatePerDay;
			if (entity instanceof PlanetAPI)
			{
				PlanetAPI planet = (PlanetAPI)entity;
				if (!planet.isMoon()) regen *= planetRenewMult;
			}
			float exhaustion = currentExhaustion - regen;
			if (exhaustion <= 0)
				toRemove.add(entity);
			else exhaustionMap.put(entity, exhaustion);
			log.info("Regenerating resources for " + entity.getName() + ": " + exhaustion);
		}
		for (SectorEntityToken token : toRemove)
			exhaustionMap.remove(token);
	}
	
	public static class MiningResult {
		public MiningReport report;
		public MiningAccident accidents;
		public List<CacheResult> cachesFound = new ArrayList<>();
	}
	
	public static class CacheDef {
		public String id;
		public CacheType type;
		public String commodityId;
		public float mult;
		public float chance;
		
		public CacheDef(String id, CacheType type, String commodityId, float mult, float chance)
		{
			this.id = id;
			this.type = type;
			this.mult = mult;
			this.chance = chance;
			this.commodityId = commodityId;
		}
	}
	
	public static class MiningReport {
		public Map<String, Map<String, Float>> outputByCondition = new HashMap<>();
		public Map<String, Float> totalOutput = new HashMap<>();
		public float exhaustion = 0;
		public float danger = 0;
		public boolean isPlayer = false;
	}
	
	public static class CacheResult {
		public CacheDef def;
		public String name = "";
		public int numItems = 0;
		
		public CacheResult(CacheDef def, String name, int numItems)
		{
			this.def = def;
			this.name = name;
			this.numItems = numItems;
		}
	}
	
	public static class MiningAccident {
		public int crewLost = 0;
		public int machineryLost = 0;
		public Map<FleetMemberAPI, Float> damage = new HashMap<>();
		public List<FleetMemberAPI> shipsDestroyed = new ArrayList<>();
		public Map<FleetMemberAPI, Float> crLost = new HashMap<>();
	}
		
	public enum CacheType {
		WEAPON, FRIGATE, FIGHTER_WING, HULLMOD, COMMODITY
	}
	
	public enum AccidentType {
		HULL_DAMAGE, CR_LOSS, CREW_LOSS, MACHINERY_LOSS
	}
}