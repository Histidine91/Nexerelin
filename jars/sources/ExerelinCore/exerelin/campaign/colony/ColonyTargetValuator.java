package exerelin.campaign.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import exerelin.ExerelinConstants;
import exerelin.campaign.econ.EconomyInfoHelper;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Used to determine the relative value of planets as colonization targets, 
 * for weighting in random selection of a target for a colony expedition.
 */
public class ColonyTargetValuator {
	
	public static final String RESOURCES_CSV_PATH = "data/config/exerelin/conditions_to_commodities.csv";
	public static final String MEM_KEY_NO_COLONIZE = "$nex_do_not_colonize";
	protected static final Map<String, Float> DEFAULT_CONDITION_VALUES = new HashMap<>();
	protected static final Map<String, Float> ORE_CONDITIONS = new HashMap<>();
	protected static final Map<String, Float> RARE_ORE_CONDITIONS = new HashMap<>();
	protected static final Map<String, Float> ORGANICS_CONDITIONS = new HashMap<>();
	protected static final Map<String, Float> VOLATILES_CONDITIONS = new HashMap<>();
	protected static final Map<String, Float> FOOD_CONDITIONS = new HashMap<>();
	
	@Deprecated
	public static final Set<String> TAGS_DO_NOT_COLONIZE = new HashSet<>(Arrays.asList(new String[] {
		Tags.THEME_REMNANT_RESURGENT, Tags.THEME_REMNANT_SUPPRESSED, "theme_breakers_suppressed", "theme_breakers_resurgent"
	}));
	
	protected static Logger log = Global.getLogger(ColonyTargetValuator.class);
	
	protected boolean silent = false;
	
	static {
		try {
			loadSettings();
		} catch (IOException | JSONException | NullPointerException ex) {
			throw new RuntimeException(ex);
		}
	}
	
	protected int oreProduction, rareOreProduction, organicsProduction, volatilesProduction, foodProduction;
	
	protected static void checkConditionForMap(String conditionId, String columnId,
			JSONObject row, Map<String, Float> map) 
	{
		float amount = (float)row.optDouble(columnId, 0);
		if (amount >= 0)
			map.put(conditionId, amount);
	}
	
	protected static void loadSettings() throws JSONException, IOException {
		JSONObject entriesJson = Global.getSettings().getJSONObject("nex_colonyConditionValues");
		
		Iterator<String> keys = entriesJson.sortedKeys();
		while (keys.hasNext()) {
			String id = keys.next();
			float value = (float)entriesJson.getDouble(id);
			DEFAULT_CONDITION_VALUES.put(id, value);
		}
		
		JSONArray resourcesCsv = Global.getSettings().getMergedSpreadsheetDataForMod("id", 
				RESOURCES_CSV_PATH, ExerelinConstants.MOD_ID);
		for(int x = 0; x < resourcesCsv.length(); x++)
		{
			String id = "";
			try {
				JSONObject row = resourcesCsv.getJSONObject(x);
				id = row.getString("id");
				if (id.isEmpty()) continue;
				
				checkConditionForMap(id, "ore", row, ORE_CONDITIONS);
				checkConditionForMap(id, "rare_ore", row, RARE_ORE_CONDITIONS);
				checkConditionForMap(id, "organics", row, ORGANICS_CONDITIONS);
				checkConditionForMap(id, "volatiles", row, VOLATILES_CONDITIONS);
				checkConditionForMap(id, "food", row, FOOD_CONDITIONS);
			} catch (JSONException ex) {
				log.error("Error loading market condition entry " + id, ex);
			}
		}
	}
	
	public void initForFaction(String factionId) {
		EconomyInfoHelper helper = EconomyInfoHelper.getInstance(true);
		oreProduction = helper.getFactionCommodityProduction(factionId, Commodities.ORE);
		rareOreProduction = helper.getFactionCommodityProduction(factionId, Commodities.RARE_ORE);
		organicsProduction = helper.getFactionCommodityProduction(factionId, Commodities.ORGANICS);
		volatilesProduction = helper.getFactionCommodityProduction(factionId, Commodities.VOLATILES);
		foodProduction = helper.getFactionCommodityProduction(factionId, Commodities.FOOD);
	}
	
	public void setSilent(boolean silent) {
		this.silent = silent;
	}
	
	public float evaluatePlanet(MarketAPI market, float distanceLY, FactionAPI faction) {
		
		if (market.hasCondition("VIC_VBomb_scar")) return -1000;
		
		List<String> conds = new ArrayList<>();
		if (faction.getId().equals(NexUtilsMarket.getOriginalOwner(market))
				&& NexUtilsAstro.isCoreSystem(market.getStarSystem()))
			return 1000;
		
		float score = 0;
		for (MarketConditionAPI cond : market.getConditions()) {
			score += getConditionValue(cond.getId(), faction);
			conds.add(cond.getId());
		}
		
		if (score <= 0) return 0;
		
		if (market.hasCondition(Conditions.HIGH_GRAVITY))
			score *= 0.8f;
		else if (market.hasCondition(Conditions.LOW_GRAVITY))
			score *= 1.1f;
		
		if (faction.getId().equals(NexUtilsMarket.getOriginalOwner(market)))
			score += 10;
		
		float hazard = market.getHazardValue() * getHazardDivisorMult(faction);
		if (hazard < 0.1) hazard = 0.1f;
		
		score /= hazard;
		score *= getDistanceValueMult(distanceLY, faction);
		
		if (NexUtilsFaction.getClaimingFaction(market.getPrimaryEntity()) == faction)
		{
			score *= 2;
		}
		
		if (!silent) {
			String distStr = String.format("%.1f", distanceLY);
			String scoreStr = String.format("%.2f", score);

			log.info("Planet " + market.getName() + " (hazard " + hazard + ") in " 
					+ market.getStarSystem().getNameWithLowercaseType() + " (dist " 
					+ distStr + ") has score " + scoreStr);
			log.info("\t" + StringHelper.writeStringCollection(conds, false, true));
		}
		
		
		return score;
	}
	
	public boolean prefilterSystem(StarSystemAPI system, FactionAPI faction) {
		if (system.getMemoryWithoutUpdate().getBoolean(MEM_KEY_NO_COLONIZE))
			return false;
		
		if (system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER))
			return false;
		
		if (system.hasPulsar()) return false;
		//if (system.isNebula()) return false;
		if (system.getPlanets().isEmpty()) return false;
		boolean inhabited = !Global.getSector().getEconomy().getMarkets(system).isEmpty();
		
		if (inhabited) {
			if (NexUtilsFaction.getSystemOwner(system) != faction) {
				//log.info(system.getName() + " controlled by someone else");
				return false;
			}
		} else if (system.hasTag(Tags.THEME_UNSAFE)) {
			return false;
		}
		
		// don't colonize systems with stations or large fleets (this is to avoid Remnant stations etc.)
		if (!inhabited) {
			for (CampaignFleetAPI fleet : system.getFleets()) {
				//log.info(system.getName() + " has fleet " + fleet.getNameWithFaction() + ": " + fleet.isStationMode() + ", " + fleet.getFleetPoints());
				// was thinking of filtering out neutral IndEvo artillery stations
				// but then they might be able to set up a colony on a planet with station, without first killing the station
				//if (fleet.getFaction().isNeutralFaction()) continue;
				if (fleet.isStationMode()) return false;
				if (fleet.getFaction().isHostileTo(faction) && fleet.getFleetPoints() > 25)
					return false;
			}
		}
		
		if (!NexUtilsAstro.canHaveCommRelay(system)) {
			//log.info(system.getName() + " cannot have comm relay");
			return false;
		}
		
		return true;
	}
	
	public boolean prefilterMarket(MarketAPI market, FactionAPI faction) {
		//StarSystemAPI system = market.getStarSystem();
		// don't colonize core world systems except to repopulate decivilized worlds
		/*
		if (ExerelinUtilsAstro.isCoreSystem(system)) {
			//if (!faction.getId().equals(ExerelinUtilsMarket.getOriginalOwner(market)))
			//	return false;
			if (!market.getMemoryWithoutUpdate().getBoolean("$wasCivilized"))
				return false;
		}
		*/
		if (market.getFaction() == null || !market.getFaction().isNeutralFaction()) {
			return false;
		}
		if (market.getMemoryWithoutUpdate().getBoolean(MEM_KEY_NO_COLONIZE)) {
			return false;
		}
		
		if (market.getPrimaryEntity().hasTag(Tags.NOT_RANDOM_MISSION_TARGET)) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Conditions have more value if they supply a commodity we're currently lacking in.
	 * @param conditionId
	 * @param currProduction
	 * @param lookup
	 * @return
	 */
	public float getMultiplierForUnavailableResource(String conditionId, int currProduction, 
			Map<String, Float> lookup) {
		if (!lookup.containsKey(conditionId))
			return 1;
		
		float strength = lookup.get(conditionId);
		if (strength <= currProduction)
			return 1;
		
		return strength - currProduction;
	}
	
	public float getConditionValue(String conditionId, FactionAPI faction) {
		float value = 0;
		if (DEFAULT_CONDITION_VALUES.containsKey(conditionId)) {
			value = DEFAULT_CONDITION_VALUES.get(conditionId);
			
			value *= getMultiplierForUnavailableResource(conditionId, oreProduction,
					ORE_CONDITIONS);
			value *= getMultiplierForUnavailableResource(conditionId, rareOreProduction,
					RARE_ORE_CONDITIONS);
			value *= getMultiplierForUnavailableResource(conditionId, organicsProduction,
					ORGANICS_CONDITIONS);
			value *= getMultiplierForUnavailableResource(conditionId, volatilesProduction,
					VOLATILES_CONDITIONS);
			value *= getMultiplierForUnavailableResource(conditionId, foodProduction,
					FOOD_CONDITIONS);
		}
			
		return value;
	}
	
	/**
	 * Multiplies the planet's hazard rating, which is then used to divide the planet's score.
	 * @param faction
	 * @return
	 */
	public float getHazardDivisorMult(FactionAPI faction) {
		return 2;
	}
	
	public float getMaxDistanceLY(FactionAPI faction) {
		return NexConfig.getFactionConfig(faction.getId()).maxColonyDistance;
	}
	
	public float getDistanceValueMult(float distance, FactionAPI faction) {
		return 2 - (distance/getMaxDistanceLY(faction));
	}
	
	public float getMinScore(FactionAPI faction) {
		return 10;
	}
}
