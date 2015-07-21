package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.AsteroidAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.WeaponAPI.AIHints;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponSize;
import com.fs.starfarer.api.fleet.CrewCompositionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.fleet.ShipRolePick;
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
import org.json.JSONException;
import org.json.JSONObject;
import org.lazywizard.lazylib.MathUtils;

public class MiningHelper {
    
    protected static final String CONFIG_FILE = "data/config/exerelin/miningConfig.json";
    protected static float miningProductionMult = 4f;
	protected static float cacheSizeMult = 1;
	protected static float baseCacheChance = 0.1f;
	protected static float baseAccidentChance = 1;
	protected static float baseAccidentCRLoss = 0.1f;
	protected static float baseAccidentHullDamage = 500;
	protected static float xpPerMiningStrength = 10;
    protected static final Map<String, Float> miningWeapons = new HashMap<>();
    protected static final Map<String, Float> miningShips = new HashMap<>();
    protected static final Map<String, Map<String,Float>> resources = new HashMap<>();
    protected static final Map<String, Float> danger = new HashMap<>();
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
            
            JSONObject resourcesJson = config.getJSONObject("resources");
            keys = resourcesJson.keys();
            while( keys.hasNext() ) {
                String planetType = (String)keys.next();
                Map<String, Float> resMap = new HashMap<>();
                JSONObject resDef = resourcesJson.getJSONObject(planetType);
                Iterator<?> resKeys = resDef.keys();
                while (resKeys.hasNext())
                {
                    String res = (String)resKeys.next();
                    resMap.put(res, (float)resDef.getDouble(res));
                }
                resources.put(planetType, resMap);
            }
            
            JSONObject dangerJson = config.getJSONObject("danger");
            keys = dangerJson.keys();
            while( keys.hasNext() ) {
                String key = (String)keys.next();
                danger.put(key, (float)dangerJson.getDouble(key));
            }
            
            //generatorSystems = config.getJSONArray("systems");
        } catch (IOException | JSONException ex) {
            log.error(ex);
        }
		
		initCacheDefs();
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
	
    public static boolean canMine(SectorEntityToken entity)
    {
        if (entity instanceof AsteroidAPI) return true;
        if (entity.getMarket() != null) return false;
        if (entity instanceof PlanetAPI)
        {
            PlanetAPI planet = (PlanetAPI)entity;
            if (planet.isMoon()) return true;
            if (planet.isGasGiant()) return true;
        }
        return false;
    }
    
    public static float getDanger(SectorEntityToken entity)
    {
        String type = "default";
        if (entity instanceof PlanetAPI)
        {
            type = ((PlanetAPI)entity).getTypeId();
        }
        if (danger.containsKey(type)) return danger.get(type);
		//else if (((PlanetAPI)entity).isGasGiant()) return danger.get("gas_giant");
        return danger.get("default");
    }
    
    public static Map<String, Float> getResources(SectorEntityToken entity)
    {
        String type = "default";
        if (entity instanceof PlanetAPI)
        {
            type = ((PlanetAPI)entity).getTypeId();
        }
        if (resources.containsKey(type)) return resources.get(type);
		//else if (((PlanetAPI)entity).isGasGiant()) return resources.get("gas_giant");
		return resources.get("default");
    }
    
    public static float getFleetMiningStrength(CampaignFleetAPI fleet)
    {
        float strength = 0;
        for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy())
        {
			int count = 1;
			if (member.isFighterWing())
				count = member.getNumFightersInWing();
			
            String hullId = member.getHullId();
            if (miningShips.containsKey(hullId))
                strength += miningShips.get(hullId) * count;
						
            Collection<String> weaponSlots = member.getVariant().getFittedWeaponSlots();
            for (String slot : weaponSlots)
            {
                String weaponId = member.getVariant().getWeaponSpec(slot).getWeaponId();
                if (miningWeapons.containsKey(weaponId))
                    strength+= miningWeapons.get(weaponId) * count;
            }
        }
        
        return strength;
    }
	
	public static FleetMemberAPI getRandomSmallShipOrWing(boolean isFighter)
	{
		WeightedRandomPicker<FleetMemberAPI> shipPicker = new WeightedRandomPicker<>();
		
		WeightedRandomPicker<String> rolePicker = new WeightedRandomPicker<>();
		if (isFighter)
		{
			rolePicker.add(ShipRoles.INTERCEPTOR, 1f);
			rolePicker.add(ShipRoles.FIGHTER, 1f);
			rolePicker.add(ShipRoles.BOMBER, 1f);
		}
		else
		{
			rolePicker.add(ShipRoles.CIV_RANDOM, 1f);
			rolePicker.add(ShipRoles.FREIGHTER_SMALL, 1f);
			rolePicker.add(ShipRoles.TANKER_SMALL, 1f);
			rolePicker.add(ShipRoles.COMBAT_FREIGHTER_SMALL, 1f);
			rolePicker.add(ShipRoles.COMBAT_SMALL, 5f);
			rolePicker.add(ShipRoles.CARRIER_SMALL, 2f);
		}
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
	
	protected static CrewCompositionAPI applyCrewLosses(CampaignFleetAPI fleet, CrewCompositionAPI losses)
	{
		CargoAPI cargo = fleet.getCargo();
		cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.GREEN.getId(), losses.getGreen());
		cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.REGULAR.getId(), losses.getRegular());
		cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.VETERAN.getId(), losses.getVeteran());
		cargo.removeItems(CargoAPI.CargoItemType.RESOURCES, CargoAPI.CrewXPLevel.ELITE.getId(), losses.getElite());
		
		return losses;
	}
	
	
	
	protected static CrewCompositionAPI applyCrewLossesBottomUp(CampaignFleetAPI fleet, int dead)
	{
		int levelIndex = 0;
		
		CrewCompositionAPI currentCrew = Global.getFactory().createCrewComposition();
		CrewCompositionAPI crewToLose = Global.getFactory().createCrewComposition();
		List<FleetMemberAPI> ships = fleet.getFleetData().getCombatReadyMembersListCopy();
		for (FleetMemberAPI ship : ships)
		{
			currentCrew.addAll(ship.getCrewComposition());
		}
		while (dead > 0 && levelIndex < CrewXPLevel.values().length)
		{
			CrewXPLevel level = CrewXPLevel.values()[levelIndex];
			int currentCrewForLevel = (int)currentCrew.getCrew(level);
			if (currentCrewForLevel >= dead)
			{
				crewToLose.addCrew(level, dead);
				dead = 0;
			}
			else
			{
				crewToLose.addCrew(level, currentCrewForLevel);
				dead -= currentCrewForLevel;
			}
			levelIndex++;
		}
		return applyCrewLosses(fleet, crewToLose);
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
			if (!ship.isFighterWing()) picker.add(ship);
		}
		
		while (accidentChance > 0)
		{
			accidentChance -= MathUtils.getRandomNumberInRange(0.75f, 1.25f);
			if (Math.random() < danger*baseAccidentChance)
			{
				if (accident == null) accident = new MiningAccident();
				
				// ship takes damage
				if (Math.random() < 0.25f)
				{
					// TODO maybe only apply to ships particpating in mining?
					FleetMemberAPI fm = picker.pick();
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
					CrewCompositionAPI temp = Global.getFactory().createCrewComposition();
					temp.addAll(fm.getCrewComposition());
					float lossFraction = computeCrewLossFraction(fm, fm.getStatus().getHullFraction(), hullDamageFactor);
					temp.multiplyBy(lossFraction);
					accident.crewLost.addAll(temp);
					applyCrewLosses(fleet, temp);
				}
				// CR loss
				else if (Math.random() < 0.4f)
				{
					FleetMemberAPI fm = picker.pick();
					float crLost = baseAccidentCRLoss * MathUtils.getRandomNumberInRange(0.75f, 1.25f);
					fm.getRepairTracker().applyCREvent(-crLost, StringHelper.getString("exerelin_mining", "miningAccident"));
					accident.crLost.put(fm, crLost);
				}
				// crew loss
				else
				{
					int dead = MathUtils.getRandomNumberInRange(1, 5);
					dead = Math.min(dead, fleet.getCargo().getTotalCrew());
					CrewCompositionAPI temp = applyCrewLossesBottomUp(fleet, dead);
					accident.crewLost.addAll(temp);
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
	
	public static MiningResult getMiningResults(CampaignFleetAPI fleet, SectorEntityToken entity, float mult, boolean isPlayer)
	{
		float strength = getFleetMiningStrength(fleet);
		Map<String, Float> planetResources = getResources(entity);
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
		}
		
		return result;
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
		public CrewCompositionAPI crewLost = Global.getFactory().createCrewComposition();
		public Map<FleetMemberAPI, Float> damage = new HashMap<>();
		public List<FleetMemberAPI> shipsDestroyed = new ArrayList<>();
		public Map<FleetMemberAPI, Float> crLost = new HashMap<>();
	}
	
	public enum CacheType {
		WEAPON, FRIGATE, FIGHTER_WING, COMMODITY
	}
}