package exerelin.campaign.colony;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class ColonyTargetValuator {
	
	protected static final Map<String, Float> DEFAULT_CONDITION_VALUES = new HashMap<>();
	@Deprecated
	public static final Set<String> TAGS_DO_NOT_COLONIZE = new HashSet<>(Arrays.asList(new String[] {
		Tags.THEME_REMNANT_RESURGENT, Tags.THEME_REMNANT_SUPPRESSED, "theme_breakers_suppressed", "theme_breakers_resurgent"
	}));
	
	protected static Logger log = Global.getLogger(ColonyTargetValuator.class);
	
	static {
		try {
            loadSettings();
        } catch (IOException | JSONException | NullPointerException ex) {
            throw new RuntimeException(ex);
        }
	}
	
	protected static void loadSettings() throws JSONException, IOException {
		JSONObject entriesJson = Global.getSettings().getJSONObject("nex_colonyConditionValues");
		
		Iterator<String> keys = entriesJson.sortedKeys();
		while (keys.hasNext()) {
			String id = keys.next();
			float value = (float)entriesJson.getDouble(id);
			DEFAULT_CONDITION_VALUES.put(id, value);
		}
	}
	
	public float evaluatePlanet(MarketAPI market, float distance, FactionAPI faction) {
		List<String> conds = new ArrayList<>();
		if (ExerelinUtilsMarket.getOriginalOwner(market).equals(faction.getId())
				&& !market.getStarSystem().isProcgen())
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
		
		float hazard = market.getHazardValue() * getHazardDivisorMult(faction);
		if (hazard < 0.1) hazard = 0.1f;
		
		score /= hazard;
		score *= getDistanceValueMult(distance, faction);
		
		log.info("Planet " + market.getName() + " (hazard " + hazard + ") in " 
				+ market.getStarSystem().getNameWithLowercaseType() + " (dist " 
				+ distance + ") has score " + score);
		log.info("\t" + StringHelper.writeStringCollection(conds, false, true));
		
		return score;
	}
	
	public boolean prefilterMarket(MarketAPI market, FactionAPI faction) {
		StarSystemAPI system = market.getStarSystem();
		if (system.hasPulsar()) return false;
		if (system.isNebula()) return false;
		
		if (system.isProcgen()) {
			// don't colonize a system with an existing market, unless it's a player market 
			// and player is commissioned with that faction
			for (MarketAPI existingMarket : Global.getSector().getEconomy().getMarkets(system)) {
				//if (existingMarket.getFaction().isPlayerFaction() && faction == PlayerFactionStore.getPlayerFaction())
				//	continue;
				return false;
			}
			// don't colonize systems with stations (this is to avoid Remnant stations etc.)
			for (CampaignFleetAPI fleet : system.getFleets()) {
				if (fleet.isStationMode()) return false;
			}
		}
		
		return true;
	}
	
	public float getConditionValue(String conditionId, FactionAPI faction) {
		if (DEFAULT_CONDITION_VALUES.containsKey(conditionId))
			return DEFAULT_CONDITION_VALUES.get(conditionId);
		return 0;
	}
	
	public float getHazardDivisorMult(FactionAPI faction) {
		return 2;
	}
	
	public float getMaxDistance(FactionAPI faction) {
		// TODO put in faction config?
		return 30000;
	}
	
	public float getDistanceValueMult(float distance, FactionAPI faction) {
		return 2 - (distance/getMaxDistance(faction));
	}
}
