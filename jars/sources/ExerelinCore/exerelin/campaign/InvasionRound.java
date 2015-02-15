/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import exerelin.PlayerFactionStore;

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
	public static final float DEFENDER_BASE_STRENGTH = 0.4f;
	public static final float DEFENDER_STABILITY_MOD = 0.5f;
	public static final float DEFENDER_MILITARY_BASE_MOD = 0.25f;
	public static final float DEFENDER_REGIONAL_CAPITAL_MOD = 0.25f;
	public static final float DEFENDER_HEADQUARTERS_MOD = 0.4f;
	public static final float MARINE_LOSS_MULT = 0.125f;
	public static final float MARINE_LOSS_RANDOM_MOD = 0.025f;
	
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
		private boolean success;
		private CargoAPI enemyCargoDamaged;
		private int marinesLost;
		private float attackerStrength;
		private float defenderStrength;
		private float timeTaken;
		private Map<String, Float> attackerBonuses;
		private Map<String, Float> defenderBonuses;

		public InvasionRoundResult()
		{
			this(false);
		}
		
		public InvasionRoundResult(boolean success)
		{
			this.success = success;
			this.attackerStrength = 0;
			this.defenderStrength = 0;
			this.marinesLost = 0;
			this.attackerBonuses = new HashMap<>();
			this.defenderBonuses = new HashMap<>();
		}
		
		public boolean getSuccess() {
			return success;
		}
		public void setSuccess(boolean success) {
			this.success = success;
		}
		public float getAttackerStrength() {
			return attackerStrength;
		}
		public void setAttackerStrength(float strength) {
			attackerStrength = strength;
		}
		public float getDefenderStrength() {
			return defenderStrength;
		}
		public void setDefenderStrength(float strength) {
			defenderStrength = strength;
		}
		public float getTimeTaken() {
			return timeTaken;
		}
		public void setTimeTaken(float time) {
			timeTaken = time;
		}
		public CargoAPI getDamageDone() {
			return enemyCargoDamaged;
		}
		public void setDamageDone(CargoAPI damage) {
			this.enemyCargoDamaged = damage;
		}
		public int getMarinesLost() {
			return marinesLost;
		}
		public void setMarinesLost(int losses) {
			this.marinesLost = losses;
		}
		public Map<String, Float> getAttackerBonuses()
		{
			return new HashMap<>(attackerBonuses);
		}
		public void addAttackerBonus(String name, float amount)
		{
			attackerBonuses.put(name, amount);
		}
		public Map<String, Float> getDefenderBonuses()
		{
			return new HashMap<>(defenderBonuses);
		}
		public void addDefenderBonus(String name, float amount)
		{
			defenderBonuses.put(name, amount);
		}
	}
	
	public static InvasionRoundResult GetInvasionRoundResult(CampaignFleetAPI attacker, SectorEntityToken defender) 
	{
		return GetInvasionRoundResult(attacker, defender, InvasionSimulationType.REALISTIC);
	}
	
	public static InvasionRoundResult GetInvasionRoundResult(CampaignFleetAPI attacker, SectorEntityToken defender, InvasionSimulationType simType)
	{
		CargoAPI attackerCargo = attacker.getCargo();
		int marineCount = attackerCargo.getMarines();
		if (marineCount <= 0) return new InvasionRoundResult();
		
		MarketAPI market = defender.getMarket();
		if (market == null) return new InvasionRoundResult(true);
		
		// combat resolution (TODO: incomplete)
		float randomBonus = (float)(Math.random()) * ATTACKER_RANDOM_BONUS;
		if (simType == InvasionSimulationType.PESSIMISTIC)
			randomBonus = 0;
		else if (simType == InvasionSimulationType.OPTIMISTIC)
			randomBonus = ATTACKER_RANDOM_BONUS;
		
		float marketSize = market.getSize();
		float attackerMarineMult = attacker.getCommanderStats().getMarineEffectivnessMult().getModifiedValue();
		float attackerAssets = (marineCount * attackerMarineMult) + (attacker.getFleetPoints() * ATTACKER_FLEET_MULT);
		float baseAttackerStrength = (ATTACKER_BASE_STRENGTH  + randomBonus) * attackerAssets;
		float baseDefenderStrength = DEFENDER_BASE_STRENGTH * (float)(Math.pow(marketSize, 3));
		baseDefenderStrength = baseDefenderStrength * (market.getStabilityValue() + 1 - DEFENDER_STABILITY_MOD) * DEFENDER_STABILITY_MOD;
		float attackerStrength = baseAttackerStrength;
		float defenderStrength = baseDefenderStrength;
		InvasionRoundResult result = new InvasionRoundResult();
		
		if(market.hasCondition("military_base"))
		{
			defenderStrength += baseDefenderStrength * DEFENDER_MILITARY_BASE_MOD;
			result.addDefenderBonus("Military base", DEFENDER_MILITARY_BASE_MOD);
		}
		if(market.hasCondition("regional_capital"))
		{
			defenderStrength += baseDefenderStrength * DEFENDER_REGIONAL_CAPITAL_MOD;
			result.addDefenderBonus("Regional capital", DEFENDER_REGIONAL_CAPITAL_MOD);
		}
		if(market.hasCondition("headquarters"))
		{
			defenderStrength += baseDefenderStrength * DEFENDER_HEADQUARTERS_MOD;
			result.addDefenderBonus("Headquarters", DEFENDER_HEADQUARTERS_MOD);
		}
		
		float outcome = attackerStrength - defenderStrength;
		float marineLossFactor = defenderStrength*6 - attackerStrength*2;
		float marineLossMod = MARINE_LOSS_MULT - MARINE_LOSS_RANDOM_MOD;
		if (simType == InvasionSimulationType.REALISTIC)
			marineLossMod += (float)(2 * Math.random() * MARINE_LOSS_RANDOM_MOD);
		else if (simType == InvasionSimulationType.PESSIMISTIC)
			marineLossMod += 2 * MARINE_LOSS_RANDOM_MOD;
		
		
		int marinesLost = (int)(marineLossFactor * marineLossMod + 0.5f);
		if (marinesLost > marineCount) marinesLost = marineCount;
		if (marinesLost < 0) marinesLost = 0;
		
		result.setSuccess(outcome > 0);
		result.setAttackerStrength(attackerStrength);
		result.setDefenderStrength(defenderStrength);
		result.setMarinesLost(marinesLost);
		result.setTimeTaken(marketSize/2);
		
		// todo implement cargo damage
		return result;
	}
	
	public static InvasionRoundResult InvadeMarket(CampaignFleetAPI attacker, SectorEntityToken defender)
	{
		InvasionRoundResult result = GetInvasionRoundResult(attacker, defender);
		SectorAPI sector = Global.getSector();
		
		// TODO do stuff to market
		
		CargoAPI attackerCargo = attacker.getCargo();
		attackerCargo.removeMarines(result.getMarinesLost());
		
		if (result.getSuccess())
		{
			boolean playerInvolved = false;
			
			// transfer market and associated entities
			FactionAPI newOwner = attacker.getFaction();
			String newOwnerId = newOwner.getId();
			FactionAPI oldOwner = defender.getFaction();
			if ( attacker == sector.getPlayerFleet() )
			{
				playerInvolved = true;
				newOwnerId = PlayerFactionStore.getPlayerFactionId();
				newOwner = sector.getFaction(newOwnerId);
			}
			MarketAPI market = defender.getMarket();
			List<SectorEntityToken> linkedEntities = market.getConnectedEntities();
			for (SectorEntityToken entity : linkedEntities)
			{
				entity.setFaction(newOwnerId);
			}
			market.setFactionId(newOwnerId);
			List<SubmarketAPI> submarkets = market.getSubmarketsCopy();
			for (SubmarketAPI submarket : submarkets)
			{
				if(submarket.getFaction() == oldOwner)
				submarket.setFaction(newOwner);
			}
			market.reapplyConditions();
		
			// relationship changes
			List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsCopy();
			Set<String> seenFactions = new HashSet<>();
			float repChangeStrength = market.getSize()*5;
			for (final MarketAPI otherMarket : markets) {
				if (!otherMarket.getFaction().isHostileTo(oldOwner)) continue;
				//if (!defender.isInOrNearSystem(otherMarket.getStarSystem())) continue;	// station capture news is sector-wide
				if (seenFactions.contains(otherMarket.getFactionId())) continue;
				
				CampaignEventTarget tempTarget = new CampaignEventTarget(otherMarket);
				RepLevel level = newOwner.getRelationshipLevel(otherMarket.getFaction());
				seenFactions.add(otherMarket.getFactionId());
				if (level.isAtWorst(RepLevel.HOSTILE)) {
					if (playerInvolved)
						sector.adjustPlayerReputation(
							new RepActionEnvelope(RepActions.COMBAT_WITH_ENEMY, (float)market.getSize()*5f),
							otherMarket.getFaction().getId());
					//log.info(String.format("Improving reputation with owner of market [%s] due to conquest of " + defender.getName(), otherMarket.getName()));
				}
			}
			
			// add intel event
			Map<String, Object> params = new HashMap<>();
			params.put("newOwner", newOwner);
			params.put("oldOwner", oldOwner);
			params.put("playerInvolved", playerInvolved);
			sector.getEventManager().startEvent(new CampaignEventTarget(market), "market_captured", params);
		}
		return result;
	}
}
