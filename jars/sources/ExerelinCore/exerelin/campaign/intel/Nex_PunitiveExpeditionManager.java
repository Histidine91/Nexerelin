package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager;
import static com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager.MAX_THRESHOLD;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONObject;

public class Nex_PunitiveExpeditionManager extends PunitiveExpeditionManager {
	
	// don't send punitive expeditions against player if member or ally
	@Override
	protected void checkExpedition(PunExData curr) {
		
		String factionId = curr.faction.getId();
		if (!SectorManager.isFactionAlive(factionId))
			return;
		
		String playerFactionId = PlayerFactionStore.getPlayerFactionId();
		if (AllianceManager.areFactionsAllied(playerFactionId, factionId))
			return;
		
		super.checkExpedition(curr);
	}
	
	// create Nex version of the expedition intel
	protected void createExpedition(PunExData curr) {
		
		JSONObject json = curr.faction.getCustom().optJSONObject(Factions.CUSTOM_PUNITIVE_EXPEDITION_DATA);
		if (json == null) return;
		
		boolean vsCompetitors = json.optBoolean("vsCompetitors", false);
		boolean vsFreePort = json.optBoolean("vsFreePort", false);
		boolean canBombard = json.optBoolean("canBombard", false);
		boolean territorial = json.optBoolean("territorial", false);
		
		List<PunExReason> reasons = getExpeditionReasons(curr);
		if (reasons.isEmpty()) return;
		
		//for (PunExReason reason : reasons) {
		
		MarketAPI target = null;
		float max = 0f;
		//WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>(curr.random);
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (!market.isPlayerOwned()) continue;
			
			float weight = 0f;
			for (PunExReason reason : reasons) {
				if (reason.commodityId != null) {
					CommodityOnMarketAPI com = market.getCommodityData(reason.commodityId);
					weight += com.getAvailable();
				}
			}
			
			if (vsFreePort && market.isFreePort()) {
				weight += market.getSize();
			}
			if (territorial && Misc.getClaimingFaction(market.getPrimaryEntity()) == curr.faction) {
				weight += 1000f + market.getDaysInExistence();
			}
				
			if (weight > max) {
				target = market;
				max = weight;
			}
			//picker.add(market, weight);
		}
		
		if (target == null || max <= 0) return;
		
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>(curr.random);
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(null)) {
			if (market.getFaction() == curr.faction && 
					market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY)) {
				picker.add(market, market.getSize());
			}
		}
		
		MarketAPI from = picker.pick();
		if (from == null) return;
		
		Collections.sort(reasons, new Comparator<PunExReason>() {
			public int compare(PunExReason o1, PunExReason o2) {
				return (int)Math.signum(o2.weight - o1.weight);
			}
		});
		PunExReason bestReason = reasons.get(0);
		
		PunExGoal goal = null;
		Industry industry = null;
		if (bestReason.type == PunExType.ANTI_FREE_PORT) {
			goal = PunExGoal.RAID_SPACEPORT;
			if (canBombard && curr.numSuccesses >= 2) {
				goal = PunExGoal.BOMBARD;
			}
		} else if (bestReason.type == PunExType.TERRITORIAL) {
			if (canBombard || true) {
				goal = PunExGoal.BOMBARD;
			} else {
				//goal = PunExGoal.EVACUATE;
			}
		} else {
			goal = PunExGoal.RAID_PRODUCTION;
			if (bestReason.commodityId == null || curr.numSuccesses >= 1) {
				goal = PunExGoal.RAID_SPACEPORT;
			}
			if (canBombard && curr.numSuccesses >= 2) {
				goal = PunExGoal.BOMBARD;
			}
		}
		
		//goal = PunExGoal.BOMBARD;
		
		if (goal == PunExGoal.RAID_SPACEPORT) {
			for (Industry temp : target.getIndustries()) {
				if (temp.getSpec().hasTag(Industries.TAG_SPACEPORT)) {
					industry = temp;
					break;
				}
			}
			if (industry == null) return;
		} else if (goal == PunExGoal.RAID_PRODUCTION && bestReason.commodityId != null) {
			max = 0;
			for (Industry temp : target.getIndustries()) {
				int prod = temp.getSupply(bestReason.commodityId).getQuantity().getModifiedInt();
				if (prod > max) {
					max = prod;
					industry = temp;
				}
			}
			if (industry == null) return;
		}
		
		//float fp = from.getSize() * 20 + threshold * 0.5f;
		float fp = 50 + curr.threshold * 0.5f;
		//fp = 500;
		if (from.getFaction().isHostileTo(target.getFaction())) {
			fp *= 1.5f;
		}
		

		float totalAttempts = 0f;
		for (PunExData d : data.values()) {
			totalAttempts += d.numAttempts;
		}
		//if (totalAttempts > 10) totalAttempts = 10;
		
		float extraMult = 0f;
		if (totalAttempts <= 2) {
			extraMult = 0f;
		} else if (totalAttempts <= 4) {
			extraMult = 1f;
		} else if (totalAttempts <= 7) {
			extraMult = 2f;
		} else if (totalAttempts <= 10) {
			extraMult = 3f;
		} else {
			extraMult = 4f;
		}
		
		float orgDur = 20f + extraMult * 10f + (10f + extraMult * 5f) * (float) Math.random();
		
		
		curr.intel = new Nex_PunitiveExpeditionIntel(from.getFaction(), from, target, fp, orgDur,
												 goal, industry, bestReason);
		if (curr.intel.isDone()) {
			curr.intel = null;
			return;
		}
		
		
		curr.numAttempts++;
		curr.anger = 0f;
		curr.threshold *= 2f;
		if (curr.threshold > MAX_THRESHOLD) {
			curr.threshold = MAX_THRESHOLD;
		}
	}
}
