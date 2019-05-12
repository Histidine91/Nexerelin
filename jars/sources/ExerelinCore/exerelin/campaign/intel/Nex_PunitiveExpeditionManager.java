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
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import java.util.List;
import org.json.JSONObject;

public class Nex_PunitiveExpeditionManager extends PunitiveExpeditionManager {
	
	// don't send punitive expeditions against player if member or ally (or faction is dead)
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
	// TODO: demand tribute instead of territorial sat bomb
	@Override
	public void createExpedition(PunExData curr, Integer fpOverride) {
		JSONObject json = curr.faction.getCustom().optJSONObject(Factions.CUSTOM_PUNITIVE_EXPEDITION_DATA);
		if (json == null) return;
		
//		boolean vsCompetitors = json.optBoolean("vsCompetitors", false);
//		boolean vsFreePort = json.optBoolean("vsFreePort", false);
		boolean canBombard = json.optBoolean("canBombard", false);
//		boolean territorial = json.optBoolean("territorial", false);
		
		List<PunExReason> reasons = getExpeditionReasons(curr);
		WeightedRandomPicker<PunExReason> reasonPicker = new WeightedRandomPicker<PunExReason>(curr.random);
		for (PunExReason r : reasons) {
			//if (r.type == PunExType.ANTI_COMPETITION) continue;
			reasonPicker.add(r, r.weight);
		}
		PunExReason reason = reasonPicker.pick();
		if (reason == null) return;
		
		
		WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker<MarketAPI>(curr.random);
		//for (PunExReason reason : reasons) {
		
		//WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>(curr.random);
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			if (!market.isPlayerOwned()) continue;
			
			float weight = 0f;
			if (reason.type == PunExType.ANTI_COMPETITION && reason.commodityId != null) {
				CommodityOnMarketAPI com = market.getCommodityData(reason.commodityId);
				int share = com.getCommodityMarketData().getExportMarketSharePercent(market);
//				if (share <= 0 && com.getAvailable() > 0) {
//					share = 1;
//				}
				weight += share * share;
			} else if (reason.type == PunExType.ANTI_FREE_PORT && market.getId().equals(reason.marketId)) {
				weight = 1f;
			} else if (reason.type == PunExType.TERRITORIAL && market.getId().equals(reason.marketId)) {
				weight = 1f;
			}
			
			targetPicker.add(market, weight);
		}
		
		MarketAPI target = targetPicker.pick();
		if (target == null) return;
		
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>(curr.random);
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(null)) {
			if (market.getFaction() == curr.faction && 
					market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY)) {
				picker.add(market, market.getSize());
			}
		}
		
		MarketAPI from = picker.pick();
		if (from == null) return;
		
		PunExGoal goal = null;
		Industry industry = null;
		if (reason.type == PunExType.ANTI_FREE_PORT) {
			goal = PunExGoal.RAID_SPACEPORT;
			if (canBombard && curr.numSuccesses >= 2) {
				goal = PunExGoal.BOMBARD;
			}
		} else if (reason.type == PunExType.TERRITORIAL) {
			if (canBombard || true) {
				goal = PunExGoal.BOMBARD;
			} else {
				//goal = PunExGoal.EVACUATE;
			}
		} else {
			goal = PunExGoal.RAID_PRODUCTION;
			if (reason.commodityId == null || curr.numSuccesses >= 1) {
				goal = PunExGoal.RAID_SPACEPORT;
			}
			if (canBombard && curr.numSuccesses >= 2) {
				goal = PunExGoal.BOMBARD;
			}
		}
		
		//goal = PunExGoal.BOMBARD;
		
		if (goal == PunExGoal.RAID_SPACEPORT) {
			for (Industry temp : target.getIndustries()) {
				if (temp.getSpec().hasTag(Industries.TAG_UNRAIDABLE)) continue;
				if (temp.getSpec().hasTag(Industries.TAG_SPACEPORT)) {
					industry = temp;
					break;
				}
			}
			if (industry == null) return;
		} else if (goal == PunExGoal.RAID_PRODUCTION && reason.commodityId != null) {
			int max = 0;
			for (Industry temp : target.getIndustries()) {
				if (temp.getSpec().hasTag(Industries.TAG_UNRAIDABLE)) continue;
				
				int prod = temp.getSupply(reason.commodityId).getQuantity().getModifiedInt();
				if (prod > max) {
					max = prod;
					industry = temp;
				}
			}
			if (industry == null) return;
		}
		
		//float fp = from.getSize() * 20 + threshold * 0.5f;
		float fp = 50 + curr.threshold * 0.5f;
		fp = Math.max(50, fp - 50);
		//fp = 500;
//		if (from.getFaction().isHostileTo(target.getFaction())) {
//			fp *= 1.25f;
//		}
		
		if (fpOverride != null) {
			fp = fpOverride;
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
												 goal, industry, reason);
		if (curr.intel.isDone()) {
			curr.intel = null;
			return;
		}
		
		if (curr.random.nextFloat() < numSentSinceTimeout * PROB_TIMEOUT_PER_SENT) {
			timeout = orgDur + MIN_TIMEOUT + curr.random.nextFloat() * (MAX_TIMEOUT - MIN_TIMEOUT);
		}
		numSentSinceTimeout++;
		
		curr.numAttempts++;
		curr.anger = 0f;
		curr.threshold *= 2f;
		if (curr.threshold > MAX_THRESHOLD) {
			curr.threshold = MAX_THRESHOLD;
		}
	}
}
