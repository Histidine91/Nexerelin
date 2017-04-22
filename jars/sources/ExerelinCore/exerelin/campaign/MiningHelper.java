package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.ShipRoles;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

public class MiningHelper {
	
	protected static final String CONFIG_FILE = "data/config/exerelin/miningConfig.json";
	protected static final String MINING_SHIP_DEFS = "data/config/exerelin/mining_ships.csv";
	protected static final String MINING_WEAPON_DEFS = "data/config/exerelin/mining_weapons.csv";
	protected static final String EXHAUSTION_DATA_KEY = "exerelinMiningExhaustion";	// exhaustion list is a <SectorEntityToken, Float> map
	public static final float MAX_EXHAUSTION = 0.8f; 
	
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
	
	protected static final Map<String, Float> miningWeapons = new HashMap<>();
	protected static final Map<String, Float> miningShips = new HashMap<>();
	protected static final Map<String, MineableDef> mineableDefs = new HashMap<>();
	protected static final List<CacheDef> cacheDefs = new ArrayList<>();
	//protected static final
	
	public static final Logger log = Global.getLogger(MiningHelper.class);
	
	static {
		try {
			JSONObject config = Global.getSettings().loadJSON(CONFIG_FILE);
			miningProductionMult = (float)config.optDouble("miningProductionMult", miningProductionMult);
			cacheSizeMult = (float)config.optDouble("cacheSizeMult", cacheSizeMult);
			baseCacheChance = (float)config.optDouble("baseCacheChance", baseCacheChance);
			baseAccidentChance = (float)config.optDouble("baseAccidentChance", baseAccidentChance);
			xpPerMiningStrength = (float)config.optDouble("xpPerMiningStrength", xpPerMiningStrength);
			
			baseAccidentCRLoss = (float)config.optDouble("baseAccidentCRLoss", baseAccidentCRLoss);
			baseAccidentHullDamage = (float)config.optDouble("baseAccidentHullDamage", baseAccidentHullDamage);
			exhaustionPer100MiningStrength = (float)config.optDouble("exhaustionPer100MiningStrength", exhaustionPer100MiningStrength);
			renewRatePerDay = (float)config.optDouble("renewRatePerDay", renewRatePerDay);
			planetDangerMult = (float)config.optDouble("planetDangerMult", planetDangerMult);
			planetExhaustionMult = (float)config.optDouble("planetExhaustionMult", planetExhaustionMult);
			planetRenewMult = (float)config.optDouble("planetRenewMult", planetRenewMult);

			/*
			JSONObject weaponsJson = config.getJSONObject("miningWeapons");
			Iterator<?> keys = weaponsJson.keys();
			while( keys.hasNext() ) {
				String key = (String)keys.next();
				miningWeapons.put(key, (float)weaponsJson.getDouble(key));
			}
			
			JSONObject shipsJson = config.getJSONObject("miningShips");
			keys = shipsJson.keys();
			while( keys.hasNext() ) {
				String key = (String)keys.next();
				miningShips.put(key, (float)shipsJson.getDouble(key));
			}
			*/
			
			JSONArray miningShipsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", MINING_SHIP_DEFS, "nexerelin");
			for(int x = 0; x < miningShipsCsv.length(); x++)
            {
                JSONObject row = miningShipsCsv.getJSONObject(x);
                String shipId = row.getString("id");
                float strength = (float)row.getDouble("strength");
                miningShips.put(shipId, strength);
            }
			
			JSONArray miningWeaponsCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", MINING_WEAPON_DEFS, "nexerelin");
			for(int x = 0; x < miningWeaponsCsv.length(); x++)
            {
                JSONObject row = miningWeaponsCsv.getJSONObject(x);
                String weaponId = row.getString("id");
                float strength = (float)row.getDouble("strength");
                miningWeapons.put(weaponId, strength);
            }
			
			JSONObject typesJson = config.getJSONObject("planetTypes");
			mineableDefs.put("default", new MineableDef("placeholder"));
			loadMineableDef("default", typesJson.getJSONObject("default"));
			
			
			Iterator <?> keys = typesJson.keys();
			while( keys.hasNext() ) {
				String planetType = (String)keys.next();
				if (planetType.equals("default")) continue;
				JSONObject defJson = typesJson.getJSONObject(planetType);
				loadMineableDef(planetType, defJson);
			}
			
			//generatorSystems = config.getJSONArray("systems");
		} catch (IOException | JSONException ex) {
			log.error(ex);
		}
		
		initCacheDefs();
	}
	
	public static void loadMineableDef(String planetType, JSONObject defJson) throws JSONException
	{
		MineableDef def = new MineableDef(planetType);
				
		JSONObject resourcesJson = defJson.getJSONObject("resources");
		Iterator<?> resourceKeys = resourcesJson.keys();
		while (resourceKeys.hasNext())
		{
			String res = (String)resourceKeys.next();
			def.resources.put(res, (float)resourcesJson.getDouble(res));
		}
		def.danger = (float)defJson.optDouble("danger", mineableDefs.get("default").danger);
		def.exhaustionRate = (float)defJson.optDouble("exhaustionRate", mineableDefs.get("default").exhaustionRate);
		def.renewRate = (float)defJson.optDouble("renewRate", mineableDefs.get("default").renewRate);

		mineableDefs.put(planetType, def);
	}
	
	public static void initCacheDefs()
	{
		cacheDefs.add(new CacheDef("weapon", CacheType.WEAPON, null, 1, 0.8f));
		cacheDefs.add(new CacheDef("frigate", CacheType.FRIGATE, null, 1, 0.2f));
		cacheDefs.add(new CacheDef("fighters", CacheType.FIGHTER_WING, null, 1, 0.3f));
		cacheDefs.add(new CacheDef("supplies", CacheType.COMMODITY, "supplies", 3, 1f));
		cacheDefs.add(new CacheDef("fuel", CacheType.COMMODITY, "fuel", 4, 1f));
		cacheDefs.add(new CacheDef("food", CacheType.COMMODITY, "food", 5, 1f));
		cacheDefs.add(new CacheDef("hand_weapons", CacheType.COMMODITY, "hand_weapons", 3, 1f));
		cacheDefs.add(new CacheDef("heavy_machinery", CacheType.COMMODITY, "heavy_machinery", 2, 0.7f));
		cacheDefs.add(new CacheDef("rare_metals", CacheType.COMMODITY, "rare_metals", 1.5f, 0.7f));
		cacheDefs.add(new CacheDef("drugs", CacheType.COMMODITY, "drugs", 1.25f, 0.5f));
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
	
	public static String getPlanetType(SectorEntityToken entity)
	{
		if (entity instanceof AsteroidAPI) return "asteroid";
		if (entity instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)entity;
			return planet.getTypeId();
		}
		return "default";
	}
	
	public static boolean canMine(SectorEntityToken entity)
	{
		if (entity instanceof AsteroidAPI) return true;
		if (entity.getMarket() != null) return false;
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
	
	public static MineableDef getMineableDefForEntity(SectorEntityToken entity)
	{
		String type = getPlanetType(entity);
		if (mineableDefs.containsKey(type)) return mineableDefs.get(type);
		return mineableDefs.get("default");
	}
	
	public static float getDanger(SectorEntityToken entity)
	{
		MineableDef def = getMineableDefForEntity(entity);
		boolean isPlanet = false;
		if (entity instanceof PlanetAPI)
		{
			PlanetAPI planet = (PlanetAPI)entity;
			if (!planet.isMoon()) isPlanet = true;
		}
		
		float val = def.danger;
		if (isPlanet) val *= planetDangerMult;
		//else if (((PlanetAPI)entity).isGasGiant()) return danger.get("gas_giant");
		return val;
	}
	
	public static Map<String, Float> getResources(SectorEntityToken entity, boolean useExhaustion)
	{
		MineableDef def = getMineableDefForEntity(entity);
		Map<String, Float> resCopy = new HashMap<>(def.resources);
		float mult = 1;
		
		if (useExhaustion)
		{
			Map<SectorEntityToken, Float> exhaustionMap = getExhaustionMap();
			if (exhaustionMap.containsKey(entity))
				mult *= (1 - exhaustionMap.get(entity));
			
			if (mult < 0) mult = 0;
			
			Iterator<Map.Entry<String, Float>> iter = resCopy.entrySet().iterator();
            while (iter.hasNext())
            {
				Map.Entry<String, Float> tmp = iter.next();
				resCopy.put(tmp.getKey(), tmp.getValue() * mult);
			}
		}
		
		return resCopy;
	}
	
	public static float getExhaustion(SectorEntityToken entity)
	{
		Map<SectorEntityToken, Float> exhaustionMap = getExhaustionMap();
		if (exhaustionMap.containsKey(entity))
			return exhaustionMap.get(entity);
		return 0;
	}
	
	public static Map<String, Float> getMiningShipsCopy()
	{
		return new HashMap<>(miningShips);
	}
	
	public static Map<String, Float> getMiningWeaponsCopy()
	{
		return new HashMap<>(miningWeapons);
	}
	
	public static float getShipMiningStrength(FleetMemberAPI member, boolean useCRMod)
	{
		if (member.isMothballed()) return 0;
		
		float strength = 0;
		int count = 1;
		if (member.isFighterWing())
			count = member.getNumFightersInWing();
		
		float crModifier = 1;
		if (useCRMod)
		{
			float cr = member.getRepairTracker().getCR();
			crModifier = cr / 0.6f;
		}

		String hullId = member.getHullId();
		if (miningShips.containsKey(hullId))
			strength += miningShips.get(hullId) * count * crModifier;

		Collection<String> weaponSlots = member.getVariant().getFittedWeaponSlots();
		for (String slot : weaponSlots)
		{
			String weaponId = member.getVariant().getWeaponSpec(slot).getWeaponId();
			if (miningWeapons.containsKey(weaponId))
				strength+= miningWeapons.get(weaponId) * count * crModifier;
		}
		return strength;
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
	
	public static FleetMemberAPI getRandomSmallShipOrWing(boolean isFighter)
	{
		WeightedRandomPicker<FleetMemberAPI> shipPicker = new WeightedRandomPicker<>();
		
		WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();

		rolePicker.add(ShipRoles.CIV_RANDOM, 1f);
		rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1f);
		rolePicker.add(ShipRoles.TANKER_SMALL, 1f);
		rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1f);
		rolePicker.add(ShipRoles.COMBAT_SMALL, 5f);
		rolePicker.add(ShipRoles.CARRIER_SMALL, 2f);
			
		String role = rolePicker.pick();
		
		List<FactionAPI> factions = Global.getSector().getAllFactions();
		for (FactionAPI faction : factions)
		{
			if (faction.getId().equals("templars")) continue;
			
			List<ShipRolePick> picks = faction.pickShip(role, 1, rolePicker.getRandom());
			for (ShipRolePick pick : picks) {
				FleetMemberType type = FleetMemberType.SHIP;
				if (isFighter) type = FleetMemberType.FIGHTER_WING;
				
				FleetMemberAPI member = Global.getFactory().createFleetMember(type, pick.variantId);
				float cost = member.getBaseBuyValue();
				shipPicker.add(member, 10000/cost);
			}
		}
		return shipPicker.pick();
	}
	
	public static WeaponSpecAPI getRandomWeapon()
	{
		WeightedRandomPicker<WeaponSpecAPI> weaponPicker = new WeightedRandomPicker<>();
		List<String> weaponIds = Global.getSector().getAllWeaponIds();
		for (String weaponId : weaponIds)
		{
			if (weaponId.startsWith("tem_")) continue;
			
			WeaponSpecAPI weapon = Global.getSettings().getWeaponSpec(weaponId);
			if (weapon.getAIHints().contains(AIHints.SYSTEM)) continue;
			float weight = 10000/weapon.getBaseValue();
			if (weapon.getSize() == WeaponSize.LARGE) weight *= 4;
			else if (weapon.getSize() == WeaponSize.MEDIUM) weight *= 2;
			weaponPicker.add(weapon, weight);
		}
		return weaponPicker.pick();
	}
	
	protected static float computeCrewLossFraction(FleetMemberAPI member,  float hullFraction, float hullDamage) {		
		if (hullFraction == 0) {
			return (0.75f + (float) Math.random() * 0.25f) * member.getStats().getCrewLossMult().getModifiedValue(); 
		}
		return hullDamage * hullDamage * (0.5f + (float) Math.random() * 0.5f) * member.getStats().getCrewLossMult().getModifiedValue();
	}
	
	// TODO
	public static MiningAccident handleAccidents(CampaignFleetAPI fleet, float strength, float danger)
	{
		MiningAccident accident = null;
		
		float accidentChance = MathUtils.getRandomNumberInRange(-1, 3) * (float)Math.sqrt(strength);
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
				// ship takes damage
				if (!fm.isFighterWing() && Math.random() < 0.25f)
				{
					float hull = fm.getStatus().getHullFraction();
					float hullDamageFactor = 0f;
					float damage = baseAccidentHullDamage * MathUtils.getRandomNumberInRange(0.5f, 1.5f);
					fm.getStatus().applyDamage(damage);
					if (fm.getStatus().getHullFraction() <= 0) 
					{
						fm.getStatus().disable();
						fleet.getFleetData().removeFleetMember(fm);
						hullDamageFactor = 1f;
						if (accident.damage.containsKey(fm))
							accident.damage.remove(fm);
						if (accident.crLost.containsKey(fm))
							accident.crLost.remove(fm);
						accident.shipsDestroyed.add(fm);
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
				else if (Math.random() < 0.4f)
				{
					//float crLost = baseAccidentSupplyLoss * ExerelinUtilsShip.getCRPerSupplyUnit(fm, true);
					//crLost *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
					float crLost = baseAccidentCRLoss * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
					HullSize size = fm.getHullSpec().getHullSize();
					if (size == HullSize.DESTROYER) crLost *= 0.75f;
					else if (size == HullSize.CRUISER) crLost *= 0.5f;
					else if (size == HullSize.CAPITAL_SHIP) crLost *= 0.25f;
					
					fm.getRepairTracker().applyCREvent(-crLost, StringHelper.getString("exerelin_mining", "miningAccident"));
					accident.crLost.put(fm, crLost);
				}
				// crew loss
				else
				{
					int dead = MathUtils.getRandomNumberInRange(1, 5);
					dead = Math.min(dead, fleet.getCargo().getTotalCrew());
					CargoAPI cargo = fleet.getCargo();
					cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, Commodities.CREW, dead);
					accident.crewLost += dead;
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
			if (def.type == CacheType.FRIGATE || def.type == CacheType.FIGHTER_WING)
			{
				boolean isFighter = def.type == CacheType.FIGHTER_WING;
				FleetMemberAPI member = getRandomSmallShipOrWing(isFighter);
				member.getRepairTracker().setMothballed(true);
				fleet.getFleetData().addFleetMember(member);
				fleet.updateCounts();
				if (isFighter) name = member.getVariant().getFullDesignationWithHullName();
				else name = member.getHullSpec().getHullName();
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
			else if (def.type == CacheType.COMMODITY)
			{
				num = (int)(Math.sqrt(strength) * def.mult * miningProductionMult * cacheSizeMult);
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
		delta *= getMineableDefForEntity(entity).exhaustionRate;
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
		float strength = getFleetMiningStrength(fleet);
		Map<String, Float> planetResources = getResources(entity, isPlayer);
		Map<String, Float> output = new HashMap<>();
		
		Iterator<String> iter = planetResources.keySet().iterator();
		while (iter.hasNext())
		{
			String res = iter.next();
			float amount = planetResources.get(res) * strength * miningProductionMult * mult;
			amount *= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			amount = Math.round(amount);
			fleet.getCargo().addCommodity(res, amount);
			output.put(res, amount);
		}
		
		MiningResult result = new MiningResult();
		result.resources = output;
		
		if (isPlayer && Math.random() < baseCacheChance)
		{
			result.cachesFound = findCaches(fleet, strength, entity);
		}
		
		if (isPlayer)
		{
			result.accidents = handleAccidents(fleet, strength, getDanger(entity));
			
			float xp = strength * xpPerMiningStrength;
			fleet.getCargo().gainCrewXP(xp);
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
		Iterator<Map.Entry<SectorEntityToken, Float>> iter = exhaustionMap.entrySet().iterator();
		while (iter.hasNext())
		{
			Map.Entry<SectorEntityToken, Float> tmp = iter.next();
			SectorEntityToken entity = tmp.getKey();
			MineableDef def = getMineableDefForEntity(entity);
			float currentExhaustion = tmp.getValue();
			float regen = def.renewRate * days * renewRatePerDay;
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
		public Map<String, Float> resources;
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
		public Map<FleetMemberAPI, Float> damage = new HashMap<>();
		public List<FleetMemberAPI> shipsDestroyed = new ArrayList<>();
		public Map<FleetMemberAPI, Float> crLost = new HashMap<>();
	}
	
	public static class MineableDef {
		protected String name;
		protected Map<String, Float> resources = new HashMap<>();
		protected float danger = 0;
		protected float exhaustionRate = 0;
		protected float renewRate = 0;
		
		public MineableDef(String name) {
			this.name = name;
		}
	}
	
	public enum CacheType {
		WEAPON, FRIGATE, FIGHTER_WING, COMMODITY
	}
}