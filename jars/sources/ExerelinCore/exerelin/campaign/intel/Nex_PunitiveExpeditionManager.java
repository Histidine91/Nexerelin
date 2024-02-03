package exerelin.campaign.intel;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.CommodityMarketDataAPI;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DebugFlags;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.PlayerFactionStore;
import exerelin.campaign.SectorManager;
import exerelin.campaign.intel.diplomacy.TributeIntel;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsFaction;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
	
	// don't territorial-attack planets which are paying tribute
	// also, a star system may be claimed by player
	// and only check player faction markets for territorial attack
	// have config option to turn off most punitive expeditions
	@Override
	public List<PunExReason> getExpeditionReasons(PunExData curr) {
		List<PunExReason> result = new ArrayList<PunExReason>();

		JSONObject json = curr.faction.getCustom().optJSONObject(Factions.CUSTOM_PUNITIVE_EXPEDITION_DATA);
		if (json == null) return result;
		
		List<MarketAPI> markets = Misc.getFactionMarkets(curr.faction, null);
		if (markets.isEmpty()) return result;
		
		boolean vsCompetitors = json.optBoolean("vsCompetitors", false);
		boolean vsFreePort = json.optBoolean("vsFreePort", false);
		boolean territorial = json.optBoolean("territorial", false);
		
		// MODIFIED
		if (!NexConfig.enablePunitiveExpeditions) {
			vsCompetitors = false;
			vsFreePort = false;
		}
		
		MarketAPI test = markets.get(0);
		FactionAPI player = Global.getSector().getPlayerFaction();
		
		if (vsCompetitors) {
			for (CommodityOnMarketAPI com : test.getAllCommodities()) {
				if (com.isNonEcon()) continue;
				if (curr.faction.isIllegal(com.getId())) continue;
				
				CommodityMarketDataAPI cmd = com.getCommodityMarketData();
				if (cmd.getMarketValue() <= 0) continue;
				
				Map<FactionAPI, Integer> shares = cmd.getMarketSharePercentPerFaction();
				int numHigher = 0;
				int factionShare = shares.get(curr.faction);
				if (factionShare <= 0) continue;
				
				for (FactionAPI faction : shares.keySet()) {
					if (curr.faction == faction) continue;
					if (shares.get(faction) > factionShare) {
						numHigher++;
					}
				}
				
				if (numHigher >= FACTION_MUST_BE_IN_TOP_X_PRODUCERS) continue;
				
				int playerShare = cmd.getMarketSharePercent(player);
				float threshold = PLAYER_FRACTION_TO_NOTICE;
				if (DebugFlags.PUNITIVE_EXPEDITION_DEBUG) {
					threshold = 0.1f;
				}
				if (playerShare < factionShare * threshold || playerShare <= 0) continue;
				
				PunExReason reason = new PunExReason(PunExType.ANTI_COMPETITION);
				reason.weight = (float)playerShare / (float)factionShare * COMPETITION_PRODUCTION_MULT;
				reason.commodityId = com.getId();
				result.add(reason);
			}
		}
		
		if (vsFreePort) {
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(null)) {
				if (!market.isPlayerOwned()) continue;
				if (!market.isFreePort()) continue;
				if (market.isInHyperspace()) continue;
				
				for (CommodityOnMarketAPI com : test.getAllCommodities()) {
					if (com.isNonEcon()) continue;
					if (!curr.faction.isIllegal(com.getId())) continue;
					
					CommodityMarketDataAPI cmd = com.getCommodityMarketData();
					if (cmd.getMarketValue() <= 0) continue;
					
					int playerShare = cmd.getMarketSharePercent(player);
					if (playerShare <= 0) continue;
					
					PunExReason reason = new PunExReason(PunExType.ANTI_FREE_PORT);
					reason.weight = playerShare * ILLEGAL_GOODS_MULT;
					reason.commodityId = com.getId();
					reason.marketId = market.getId();
					result.add(reason);
				}
				
				if (market.isFreePort()) {
					PunExReason reason = new PunExReason(PunExType.ANTI_FREE_PORT);
					reason.weight = Math.max(1, market.getSize() - 2) * FREE_PORT_SIZE_MULT;
					reason.marketId = market.getId();
					result.add(reason);
				}
			}
		}
		
		// MODIFIED
		if (territorial) {
			int maxSize = MarketCMD.getBombardDestroyThreshold();
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(null)) {
				if (!market.isPlayerOwned()) continue;
				if (market.isInHyperspace()) continue;
				if (TributeIntel.hasOngoingIntel(market)) continue;
				
				boolean destroy = market.getSize() <= maxSize;
				if (!destroy) continue;
				
				FactionAPI claimedBy = NexUtilsFaction.getClaimingFaction(market.getPrimaryEntity());
				if (claimedBy != curr.faction) continue;
				
				PunExReason reason = new PunExReason(PunExType.TERRITORIAL);
				reason.weight = TERRITORIAL_ANGER;
				reason.marketId = market.getId();
				result.add(reason);
			}
		}
		
		return result;
	}
	
	// create Nex version of the expedition intel
	// demands tribute instead of territorial sat bomb
	// also don't launch expeditions against hyperspace markets
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
			if (!market.getFaction().isPlayerFaction()) continue;
			if (!market.isPlayerOwned()) continue;
			if (market.isInHyperspace()) continue;
			TributeIntel ti = TributeIntel.getOngoingIntel(market);
			if (ti != null && ti.getFactionForUIColors() == curr.faction) continue;
			
			float weight = 0f;
			if (reason.type == PunExType.ANTI_COMPETITION && reason.commodityId != null) {
				if (market.getSize() < MIN_COLONY_SIZE_FOR_NON_TERRITORIAL) continue;
				
				CommodityOnMarketAPI com = market.getCommodityData(reason.commodityId);
				int share = com.getCommodityMarketData().getExportMarketSharePercent(market);
//				if (share <= 0 && com.getAvailable() > 0) {
//					share = 1;
//				}
				weight += share * share;
			} else if (reason.type == PunExType.ANTI_FREE_PORT && market.getId().equals(reason.marketId)) {
				if (market.getSize() < MIN_COLONY_SIZE_FOR_NON_TERRITORIAL) continue;
				
				weight = 1f;
			} else if (reason.type == PunExType.TERRITORIAL && market.getId().equals(reason.marketId)) {
				weight = 1f;
			}
			
			targetPicker.add(market, weight);
		}
		
		MarketAPI target = targetPicker.pick();
		if (target == null) return;
		
		if (reason.type == PunExType.TERRITORIAL) {
			// Global.getLogger(this.getClass()).info("Checking territorial expedition for " + target.getName());
			// check if we should demand tribute from target instead
			String factionId = curr.faction.getId();
			if (!TributeIntel.hasRejectedTribute(factionId, target) 
					&& !curr.faction.isHostileTo(Factions.PLAYER)) {
				if (!TributeIntel.hasOngoingIntel(target)) {
					new TributeIntel(factionId, target).init();
				}
				return;
			}
		}

		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>(curr.random);
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(null)) {
			boolean canSendWithoutMilitaryBase = json.optBoolean("canSendWithoutMilitaryBase", false);
			boolean military = market.getMemoryWithoutUpdate().getBoolean(MemFlags.MARKET_MILITARY);
			if (market.getFaction() == curr.faction &&
					(military || canSendWithoutMilitaryBase)) {
				float w = 1f;
				if (military) w *= 10f;
				picker.add(market, market.getSize() * w);
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
		
		// TODO: send agent instead of expedition
		boolean covertAction = false;
		if (false) {	//(Global.getSettings().getBoolean("nex_allowReplaceExpeditionWithCovertAction")) {
			covertAction = Math.random() < 0.5f;
		}
		if (covertAction) {
			if (reason.commodityId != null) {
			
			}
			else if (industry != null) {

			}
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
			timeout = orgDur + MIN_TIMEOUT + curr.random.nextFloat() * (MAX_TIMEOUT - MIN_TIMEOUT);
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
	
	/**
	 * Roll to see if we should send an agent to mess the other guy up instead of launching a covert action
	 * @param faction
	 * @return
	 */
	@Deprecated
	public static float getCovertActionChance(FactionAPI faction) {
		switch (faction.getRelationshipLevel(Factions.PLAYER)) {
			case COOPERATIVE:
				return 0.65f;
			case FRIENDLY:
				return 0.6f;
			case WELCOMING:
				return 0.55f;
			case FAVORABLE:
			case NEUTRAL:
				return 0.5f;
			case SUSPICIOUS:
				return 0.4f;
			case INHOSPITABLE:
				return 0.35f;
			case HOSTILE:
				return 0.25f;
			case VENGEFUL:
				return 0.2f;
			default:
				return 0.5f;
		}
	}
}
