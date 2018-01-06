package exerelin.campaign;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import exerelin.ExerelinConstants;
import exerelin.campaign.events.MarketAttackedEvent;
import exerelin.campaign.events.RevengeanceManagerEvent;
import exerelin.campaign.fleets.DefenceStationManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import java.util.ArrayList;
import org.lazywizard.lazylib.MathUtils;

/**
 *
 * @author Histidine
 * Does some math to determine the damage dealt to a market during an invasion, 
 * the marine losses the attackers take, and whether the market is captured as a result.
 * 
 */
public class InvasionRound {
	public static Logger log = Global.getLogger(InvasionRound.class);
	
	public static final float ATTACKER_BASE_STRENGTH = 0.6f; 
	public static final float ATTACKER_RANDOM_BONUS = 0.2f;
	public static final float ATTACKER_FLEET_MULT = 0.5f;
	public static final float DEFENDER_BASE_STRENGTH = 0.2f;
	public static final float DEFENDER_STABILITY_MOD = 0.5f;
	public static final float DEFENDER_MILITARY_BASE_MOD = 0.25f;
	public static final float DEFENDER_REGIONAL_CAPITAL_MOD = 0.25f;
	public static final float DEFENDER_HEADQUARTERS_MOD = 0.4f;
	public static final float DEFENDER_URBAN_MOD = 0.2f;
	public static final float DEFENDER_RURAL_MOD = -0.2f;
	public static final float DEFENDER_AVALON_MOD = 1.0f;
	public static final float DEFENDER_REBELLION_MOD = 0.5f;
	public static final float DEFENDER_RAID_STRENGTH_MULT = 0.75f;
	public static final float DEFENDER_STRENGTH_XP_MULT = 500f;
	public static final float MARINE_LOSS_MULT = 0.4f;
	public static final float MARINE_LOSS_RANDOM_MOD = 0.1f;
	public static final float MARINE_LOSS_RAID_MULT = 0.5f;
	public static final int BASE_DESTABILIZATION = 3;
	public static final float COMMODITY_DESTRUCTION_MULT_SUCCESS = 0.2f;
	public static final float COMMODITY_DESTRUCTION_MULT_FAILURE = 0.1f;
	public static final float COMMODITY_DESTRUCTION_VARIANCE = 0.2f;
	public static final float COMMODITY_LOOT_MULT = 0.05f;
	public static final float COMMODITY_LOOT_VARIANCE = 0.2f;
	public static final float RAID_LOOT_MULT_FLOOR = 0.25f;
	public static final String LOOT_MEMORY_KEY = "$nex_invasionLoot";
	
	/**
	* PESSIMISTIC and OPTIMISTIC are used for prediction;
	* REALISTIC is used for what actually happens
	*/
	public static enum InvasionSimulationType {
		PESSIMISTIC,
		REALISTIC,
		OPTIMISTIC,
	}
	
	public static class InvasionRoundResult {
		public boolean success;
		public boolean isRaid = false;
		public CargoAPI enemyCargoDamaged;
		public int marinesLost = 0;
		public float attackerStrength = 0;
		public float defenderStrength = 0;
		public float timeTaken = 0;
		public Map<String, Float> attackerBonuses = new HashMap<>();
		public Map<String, Float> defenderBonuses = new HashMap<>();
		public Map<String, Float> loot = new HashMap<>();

		public InvasionRoundResult()
		{
			this(false);
		}
		
		public InvasionRoundResult(boolean success)
		{
			this.success = success;
		}
		public void addAttackerBonus(String name, float amount)
		{
			attackerBonuses.put(name, amount);
		}
		public void addDefenderBonus(String name, float amount)
		{
			defenderBonuses.put(name, amount);
		}
	}
	
	public static float GetDefenderStrength(MarketAPI market, boolean isRaid)
	{
		return GetDefenderStrength(market, 1, isRaid);
	}
	
	public static float GetDefenderStrength(MarketAPI market, float bonusMult, boolean isRaid)
	{
		float marketSize = market.getSize();
		if (market.getId().equals(ExerelinConstants.AVESTA_ID)) marketSize += 2;
		
		float baseDefenderStrength = DEFENDER_BASE_STRENGTH * (float)(Math.pow(marketSize+1, 3));
		baseDefenderStrength = baseDefenderStrength * (market.getStabilityValue() + 1 - DEFENDER_STABILITY_MOD) * DEFENDER_STABILITY_MOD;
		float defenderStrength = baseDefenderStrength;
		float defenderBonus = 0;
		
		if (market.hasCondition(Conditions.MILITARY_BASE))
		{
			defenderBonus += baseDefenderStrength * DEFENDER_MILITARY_BASE_MOD;
		}
		if (market.hasCondition(Conditions.REGIONAL_CAPITAL))
		{
			defenderBonus += baseDefenderStrength * DEFENDER_REGIONAL_CAPITAL_MOD;
		}
		if (market.hasCondition(Conditions.HEADQUARTERS))
		{
			defenderBonus += baseDefenderStrength * DEFENDER_HEADQUARTERS_MOD;
		}
		if (market.hasCondition(Conditions.URBANIZED_POLITY))
		{
			defenderBonus += baseDefenderStrength * DEFENDER_URBAN_MOD;
		}
		if (market.hasCondition(Conditions.RURAL_POLITY))
		{
			defenderBonus += baseDefenderStrength * DEFENDER_RURAL_MOD;
		}
		if (market.hasCondition("tem_avalon"))
		{
			defenderBonus += baseDefenderStrength * DEFENDER_AVALON_MOD;
		}
		
		if (Global.getSector().getEventManager().isOngoing(new CampaignEventTarget(market), "nex_rebellion"))
		{
			defenderBonus -= baseDefenderStrength * DEFENDER_REBELLION_MOD;
		}
		
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		if (factionConfig != null)
		{
			defenderBonus += baseDefenderStrength * factionConfig.invasionStrengthBonusDefend;
		}
		
		defenderStrength += (defenderBonus * bonusMult);
		
		if (isRaid)
			defenderStrength *= DEFENDER_RAID_STRENGTH_MULT;
		return defenderStrength;
	}
	
	/**
	 * Gets the result of a "real" invasion round
	 * @param attacker
	 * @param defender
	 * @param isRaid
	 * @return
	 */
	public static InvasionRoundResult GetInvasionRoundResult(CampaignFleetAPI attacker, 
			SectorEntityToken defender, boolean isRaid) 
	{
		return GetInvasionRoundResult(attacker, defender, isRaid, InvasionSimulationType.REALISTIC);
	}
	
	/**
	 * Gets the result of an invasion round (test or for real)
	 * @param attacker
	 * @param defender
	 * @param isRaid
	 * @param simType
	 * @return
	 */
	public static InvasionRoundResult GetInvasionRoundResult(CampaignFleetAPI attacker, 
			SectorEntityToken defender, boolean isRaid, InvasionSimulationType simType)
	{
		MarketAPI market = defender.getMarket();
		if (market == null) return new InvasionRoundResult(true);
		
		CargoAPI attackerCargo = attacker.getCargo();
		int marineCount = attackerCargo.getMarines();
		if (marineCount <= 0) {
			InvasionRoundResult result = new InvasionRoundResult(false);
			result.defenderStrength = GetDefenderStrength(market, isRaid);
			return result;
		}
		
		// combat resolution
		float randomBonus = (float)(Math.random()) * ATTACKER_RANDOM_BONUS;
		if (simType == InvasionSimulationType.PESSIMISTIC)
			randomBonus = 0;
		else if (simType == InvasionSimulationType.OPTIMISTIC)
			randomBonus = ATTACKER_RANDOM_BONUS;
		
		float marketSize = market.getSize();
		if (market.getId().equals(ExerelinConstants.AVESTA_ID)) marketSize++;
		
		float attackerMarineMult = attacker.getCommanderStats().getMarineEffectivnessMult().getModifiedValue();
		float attackerAssets = (marineCount * attackerMarineMult) + (attacker.getFleetPoints() * ATTACKER_FLEET_MULT);
		float baseAttackerStrength = (ATTACKER_BASE_STRENGTH  + randomBonus) * attackerAssets;
		float attackerStrength = baseAttackerStrength;
		ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(attacker.getFaction().getId());
		if (factionConfig != null)
		{
			attackerStrength += baseAttackerStrength * factionConfig.invasionStrengthBonusAttack;
		}
		
		float defenderStrength = GetDefenderStrength(market, isRaid);
		
		InvasionRoundResult result = new InvasionRoundResult();
		
		if(market.hasCondition("military_base"))
		{
			result.addDefenderBonus("Military base", DEFENDER_MILITARY_BASE_MOD);
		}
		if(market.hasCondition("regional_capital"))
		{
			result.addDefenderBonus("Regional capital", DEFENDER_REGIONAL_CAPITAL_MOD);
		}
		if(market.hasCondition("headquarters"))
		{
			result.addDefenderBonus("Headquarters", DEFENDER_HEADQUARTERS_MOD);
		}
		if(market.hasCondition("tem_avalon"))
		{
			result.addDefenderBonus("Avalon", DEFENDER_AVALON_MOD);
		}
		
		float outcome = attackerStrength - defenderStrength;
		float marineLossFactor = defenderStrength * (0.5f + 0.5f*defenderStrength/attackerStrength);		
		float marineLossMod = MARINE_LOSS_MULT - MARINE_LOSS_RANDOM_MOD;
		if (simType == InvasionSimulationType.REALISTIC)
			marineLossMod += (float)(2 * Math.random() * MARINE_LOSS_RANDOM_MOD);
		else if (simType == InvasionSimulationType.PESSIMISTIC)
			marineLossMod += 2 * MARINE_LOSS_RANDOM_MOD;
		if (isRaid)
			marineLossMod *= MARINE_LOSS_RAID_MULT;
		
		int marinesLost = (int)(marineLossFactor * marineLossMod + 0.5f);
		if (marinesLost > marineCount) marinesLost = marineCount;
		if (marinesLost < 0) marinesLost = 0;
		
		result.success = outcome > 0;
		result.attackerStrength = attackerStrength;
		result.defenderStrength = defenderStrength;
		result.marinesLost = marinesLost;
		result.timeTaken = marketSize/2;
		
		return result;
	}
	
	public static InvasionRoundResult AttackMarket(CampaignFleetAPI attacker,
			SectorEntityToken defender, boolean isRaid)
	{
		InvasionRoundResult result = GetInvasionRoundResult(attacker, defender, isRaid);
		
		SectorAPI sector = Global.getSector();
		FactionAPI attackerFaction = attacker.getFaction();
		String attackerFactionId = attackerFaction.getId();
		FactionAPI defenderFaction = defender.getFaction();
		String defenderFactionId = defenderFaction.getId();
		MarketAPI market = defender.getMarket();
		
		boolean playerInvolved = false;
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		if ( attacker == playerFleet )
		{
			playerInvolved = true;
			attackerFactionId = PlayerFactionStore.getPlayerFactionId();
			attackerFaction = sector.getFaction(attackerFactionId);
		}
		
		if (attackerFaction == defenderFaction || !attackerFaction.isHostileTo(defenderFaction))
		{
			return null;
		}
		
		boolean captured = false;
		boolean success = result.success;

		CampaignEventPlugin eventSuper = sector.getEventManager().getOngoingEvent(
				new CampaignEventTarget(market), "exerelin_market_attacked");
		if (eventSuper == null) 
			eventSuper = sector.getEventManager().startEvent(new CampaignEventTarget(market), 
					"exerelin_market_attacked", null);
		MarketAttackedEvent event = (MarketAttackedEvent)eventSuper;
		
		int currentPenalty = event.getStabilityPenalty();
		if ((isRaid && success) || (!isRaid && !success))
		{
			if (currentPenalty < BASE_DESTABILIZATION)
				event.increaseStabilityPenalty(1);
		}
		else if (!isRaid && success)
		{
			if (currentPenalty < BASE_DESTABILIZATION)
			{
				event.increaseStabilityPenalty(BASE_DESTABILIZATION);
				if (event.getStabilityPenalty() > BASE_DESTABILIZATION + 1)
					event.setStabilityPenalty(BASE_DESTABILIZATION + 1);
			}
			else event.increaseStabilityPenalty(1);
		}
		
		if (success) {
			float baseLootMult = ExerelinConfig.invasionLootMult;
			float lootMult = baseLootMult;
			if (isRaid) lootMult *= Math.min(1.75f, result.attackerStrength/result.defenderStrength - 1) + RAID_LOOT_MULT_FLOOR;
			float destroyMult = lootMult / baseLootMult;
			
			ExerelinUtilsMarket.destroyAllCommodityStocks(market, 
					COMMODITY_DESTRUCTION_MULT_SUCCESS * destroyMult, COMMODITY_DESTRUCTION_VARIANCE);
			
			result.loot = ExerelinUtilsMarket.getAllCommodityPartialStocks(market,
					lootMult, COMMODITY_LOOT_VARIANCE, true, true);
		} else {
			ExerelinUtilsMarket.destroyAllCommodityStocks(market, 
					COMMODITY_DESTRUCTION_MULT_FAILURE, COMMODITY_DESTRUCTION_VARIANCE);
		}
		
		//ExerelinUtilsMarket.refreshMarket(market, true);
		
		CargoAPI attackerCargo = attacker.getCargo();
		attackerCargo.removeMarines(result.marinesLost);
		
		if (!isRaid && success)
		{
			captured = true;
		}
		// relationship changes
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
		// we want to use this to only apply the rep change on event message receipted
		// but it's null when it gets to the event for some reason
		List<String> factionsToNotify = new ArrayList<>();  
		Set<String> seenFactions = new HashSet<>();
		float repChangeStrength = 0;
		if (success && !isRaid)
		{
			repChangeStrength = market.getSize() * 0.01f;

			for (final MarketAPI otherMarket : markets) {
				if (!otherMarket.getFaction().isHostileTo(defenderFaction)) continue;
				//if (!defender.isInOrNearSystem(otherMarket.getStarSystem())) continue;	// station capture news is sector-wide
				if (seenFactions.contains(otherMarket.getFactionId())) continue;

				RepLevel level = attackerFaction.getRelationshipLevel(otherMarket.getFaction());
				seenFactions.add(otherMarket.getFactionId());
				if (level.isAtWorst(RepLevel.HOSTILE)) {
					if (playerInvolved)
					{
						factionsToNotify.add(otherMarket.getFactionId());
					}
				}
			}
		}
		
		// add intel event if captured
		if (captured)
		{
			SectorManager.transferMarket(market, attackerFaction, defenderFaction, playerInvolved, true, factionsToNotify, repChangeStrength);
			
			if (playerInvolved)
			{
				float xp = result.defenderStrength * DEFENDER_STRENGTH_XP_MULT;
				playerFleet.getCargo().gainCrewXP(xp);
				playerFleet.getCommander().getStats().addXP((long) xp);
				playerFleet.getCommander().getStats().levelUpIfNeeded();
			}
			
			if (DefenceStationManager.getManager().getFleet(market) != null)
			{
				DefenceStationManager.debugMessage("Market " + market.getName() + " captured while having station");
			}
			else if (DefenceStationManager.getManager().getMaxStations(market) > 0)
			{
				DefenceStationManager.debugMessage("Station-having market " + market.getName() + " successfully captured");
			}
		}
		
		// revengeance
		if (attackerFactionId.equals(PlayerFactionStore.getPlayerFactionId()) 
				|| attackerFactionId.equals(ExerelinConstants.PLAYER_NPC_ID))
		{
			RevengeanceManagerEvent rvng = RevengeanceManagerEvent.getOngoingEvent();
			if (rvng!= null) 
			{
				float sizeSq = market.getSize() * market.getSize();
				float mult = 0.25f;
				if (isRaid) mult = 0.2f;
				rvng.addPoints(sizeSq * ExerelinConfig.revengePointsForMarketCaptureMult * mult);
				if (playerInvolved) 
					rvng.addFactionPoints(defenderFactionId, sizeSq * ExerelinConfig.revengePointsForMarketCaptureMult * mult);
			}
		}
		
		// make orphans
		if (playerInvolved)
		{
			// Spire biology is different
			if (!defenderFactionId.equals("spire") &&  !defenderFactionId.equals("darkspire"))
			{
				float deathsInflicted = GetDefenderStrength(market, 0.5f, isRaid);
				float numAvgKids = MathUtils.getRandomNumberInRange(0f, 1.5f) + MathUtils.getRandomNumberInRange(0f, 1.5f);
				StatsTracker.getStatsTracker().modifyOrphansMade((int)(deathsInflicted * numAvgKids));
			}
		}
		
		MemoryAPI mem = market.getMemoryWithoutUpdate();
		// can't enter market for some time due to "commotion"
		if (isRaid || !success)
		{
			float timeout = isRaid ? 28 : 56;
			if (success) timeout *= 1.5f;
			
			if (mem.contains(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET))
				timeout += mem.getExpire(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET);
			timeout = Math.min(timeout, 180);

			mem.set(MemFlags.MEMORY_KEY_PLAYER_HOSTILE_ACTIVITY_NEAR_MARKET, timeout);
		}
		
		if (isRaid)
		{
			mem.set("$nex_recentlyRaided", true, 7);
		}
		
		log.info( String.format("Invasion of [%s] by " + attacker.getNameWithFaction() + (success ? " successful" : " failed"), defender.getName()) );
		return result;
	}
}
