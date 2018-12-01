package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode;
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
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
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Entities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.ExerelinConstants;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

// actually now same as the Stellar Industrialist one, except for e.g. file paths
public class MiningHelperLegacy {
	
	protected static final String CONFIG_FILE = "data/config/exerelin/miningConfig.json";
	protected static final String MINING_SHIP_DEFS = "data/config/exerelin/mining_ships.csv";
	protected static final String MINING_WEAPON_DEFS = "data/config/exerelin/mining_weapons.csv";
	protected static final String RESOURCE_DEFS = "data/config/exerelin/mining_resources.csv";
	protected static final String EXHAUSTION_DATA_KEY = "nex_miningExhaustion";	// exhaustion list is a <SectorEntityToken, Float> map
	
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
				ShipRoles.ESCORT_MEDIUM,
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
				Factions.DERELICT,
				Factions.REMNANTS
		}
	));
	
	public static final float MAX_EXHAUSTION = 0.9f; 
	
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
	protected static float machineryPerMiningStrength = 0.4f;
	
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
			machineryPerMiningStrength = (float)config.optDouble("machineryPerMiningStrength", machineryPerMiningStrength);
			
			baseAccidentCRLoss = (float)config.optDouble("baseAccidentCRLoss", baseAccidentCRLoss);
			baseAccidentHullDamage = (float)config.optDouble("baseAccidentHullDamage", baseAccidentHullDamage);
			exhaustionPer100MiningStrength = (float)config.optDouble("exhaustionPer100MiningStrength", exhaustionPer100MiningStrength);
			renewRatePerDay = (float)config.optDouble("renewRatePerDay", renewRatePerDay);
			planetDangerMult = (float)config.optDouble("planetDangerMult", planetDangerMult);
			planetExhaustionMult = (float)config.optDouble("planetExhaustionMult", planetExhaustionMult);
			planetRenewMult = (float)config.optDouble("planetRenewMult", planetRenewMult);
			
			// use Nex directory files as fallback
			//loadMiningShips(MINING_SHIP_DEFS_NEX);
			//loadMiningWeapons(MINING_WEAPON_DEFS_NEX);
			loadMiningShips(MINING_SHIP_DEFS);
			loadMiningWeapons(MINING_WEAPON_DEFS);
			
			JSONArray resourcesCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", RESOURCE_DEFS, ExerelinConstants.MOD_ID);
			for(int x = 0; x < resourcesCsv.length(); x++)
			{
				String id = "";
				try {
					JSONObject row = resourcesCsv.getJSONObject(x);
					id = row.getString("id");
					if (id.isEmpty()) continue;

					Map<String, Float> resources = new HashMap<>();
					for (String commodityId : OUTPUT_COMMODITIES)
					{
						float value = (float)row.optDouble(commodityId, 0);
						if (value > 0) resources.put(commodityId, value);
					}
					miningConditions.put(id, resources);
					miningConditionsCache.put(id, (float)row.optDouble("cache", 0));
				} catch (JSONException ex) {
					log.error("Error loading market condition entry " + id + ": " + ex);
				}
			}
		} catch (IOException | JSONException ex) {
			log.error("Error loading market condition data: " + ex);
		}
		
		initCacheDefs();
		accidentPicker.add(AccidentType.HULL_DAMAGE, 1);
		accidentPicker.add(AccidentType.CR_LOSS, 1.5f);
		accidentPicker.add(AccidentType.CREW_LOSS, 1.5f);
		accidentPicker.add(AccidentType.MACHINERY_LOSS, 2f);
	}
	
	public static void loadMiningShips(String path)
	{
		try {
			JSONArray miningShipsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, ExerelinConstants.MOD_ID);
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
					log.error("Error loading mining ship " + shipId + ": " + ex);
				}
			}
		} catch (IOException | JSONException ex) {
			log.error("Error loading mining ships: " + ex);
		} catch (RuntimeException rex) {
			// file not found, do nothing
		}
	}
	
	public static void loadMiningWeapons(String path)
	{
		try {
			JSONArray miningWeaponsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", path, ExerelinConstants.MOD_ID);
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
					log.error("Error loading mining weapon " + weaponId + ": " + ex);
				}
			}
		} catch (IOException | JSONException ex) {
			log.error("Error loading mining weapons: " + ex);
		} catch (RuntimeException rex) {
			// file not found, do nothing
		}
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
		cacheDefs.add(new CacheDef("hand_weapons", CacheType.COMMODITY, Commodities.HAND_WEAPONS, 3, 1f));
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
	 * Fills a mining report with the resource output possible from mining this entity
	 * Exhaustion is ignored (unless included in mult)
	 * @param entity The entity being mined
	 * @param mult Resource output multiplier
	 * @param report A mining report to fill out
	 */
	protected static void getResources(SectorEntityToken entity, float mult, MiningReport report)
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
		
		// subtract base 100% hazard
		val -= 1;
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
	
	public static float getShipMiningStrength(FleetMemberAPI member, boolean useCRMod)
	{
		if (member.isMothballed()) return 0;
		
		float crModifier = 1;
		if (useCRMod)
		{
			float cr = member.getRepairTracker().getCR();
			crModifier = cr / 0.6f;
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
		
		return strength * crModifier;
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
				if (member.getHullSpec().getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)) continue;
				
				float cost = member.getBaseBuyValue();
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
			if (CACHE_DISALLOWED_FACTIONS.contains(faction.getId())) continue;
			
			for (String role : ROLES_FOR_FIGHTERS) 
			{
				List<ShipRolePick> picks = faction.pickShip(role, ShipPickParams.priority());
				for (ShipRolePick pick : picks) {
					FleetMemberType type = FleetMemberType.SHIP;
					if (pick.isFighterWing()) continue;

					FleetMemberAPI member = Global.getFactory().createFleetMember(type, pick.variantId);
					for (String wingId : member.getVariant().getFittedWings()) {
						if (restrictedShips.contains(wingId)) continue;
						FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(wingId);
						if (spec.getTags().contains(Tags.WING_NO_SELL)) continue;
						
						picker.add(spec, 6 - spec.getTier());
					}
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
			}
			else if (def.type == CacheType.FIGHTER_WING)
			{
				FighterWingSpecAPI spec = getRandomFighter();
				fleet.getCargo().addFighters(spec.getId(), 1);
				name = StringHelper.getStringAndSubstituteToken("exerelin_mining", "LPC", "$fighterName", spec.getVariant().getFullDesignationWithHullName());
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
			}
			else if (def.type == CacheType.HULLMOD)
			{
				HullModSpecAPI mod = getRandomHullmod();
				fleet.getCargo().addHullmods(mod.getId(), 1);
				name = StringHelper.getStringAndSubstituteToken("exerelin_mining", "hullmod", "$hullmod", mod.getDisplayName());
			}
			else if (def.type == CacheType.COMMODITY)
			{
				num = (int)(Math.sqrt(strength) * def.mult * miningProductionMult * cacheSizeMult);
				if (def.mult == 0)
					num = 1;
				fleet.getCargo().addCommodity(def.commodityId, num);
				name = Global.getSector().getEconomy().getCommoditySpec(def.commodityId).getName();
			}
			caches.add(new CacheResult(def, name, num));
		}
		return caches;
	}
	
	public static float applyResourceExhaustion(SectorEntityToken entity, float miningStrength)
	{
		Map<SectorEntityToken, Float> exhaustionMap = getExhaustionMap();
		String type = "default";
		float currExhaustion = 0;
		if (exhaustionMap.containsKey(entity))
			currExhaustion = exhaustionMap.get(entity);
		
		float delta = miningStrength * exhaustionPer100MiningStrength/100;
		//delta *= getMiningResources(entity).exhaustionRate;
		if (entity instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)entity;
			type = planet.getTypeId();
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
			float xp = strength * xpPerMiningStrength;
			fleet.getCommander().getStats().addXP((long) xp);
			fleet.getCommander().getStats().levelUpIfNeeded();
			
			applyResourceExhaustion(entity, strength);
		}
		
		return result;
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