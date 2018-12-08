package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.CommodityOnMarketAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_MarketCMD;
import com.fs.starfarer.api.impl.campaign.submarkets.BaseSubmarketPlugin;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.utilities.ExerelinUtilsMarket;
import exerelin.utilities.StringHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

/**
 *
 * @author Histidine
 * Does some math to determine the damage dealt to a market during an invasion, 
 * the marine losses the attackers take, and whether the market is captured as a result.
 */

public class InvasionRound {
	
	public static final float DAMAGE_PER_ROUND_MULT = 0.3f;
	public static final float INSTABILITY_PER_ROUND = 1;
	
	public static Logger log = Global.getLogger(InvasionRound.class);
	
	public static InvasionRoundResult execute(CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr, Random random)
	{
		int marines = fleet.getCargo().getMarines();
		
		float atkDam = (atkStr - defStr/3) * DAMAGE_PER_ROUND_MULT * (float)(0.75f + random.nextGaussian() * 0.5f);
		float defDam = (defStr - atkStr/3) * DAMAGE_PER_ROUND_MULT * (float)(0.75f + random.nextGaussian() * 0.5f);
		if (atkDam < 0) atkDam = 0;
		if (defDam < 0) defDam = 0;
		
		int losses = (int)(marines * defDam/atkStr);
		if (losses > marines) losses = marines;
		
		InvasionRoundResult result = new InvasionRoundResult();
		result.losses = losses;
		result.atkDam = atkDam;
		result.defDam = defDam;
		result.atkStr = atkStr - defDam;
		result.defStr = defStr - atkDam;
		
		// disruption
		WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
		for (Industry curr : defender.getIndustries()) {
			if (curr.canBeDisrupted() && !curr.getSpec().hasTag(Industries.TAG_UNRAIDABLE)) 
			{
				float currDisruption = curr.getDisruptedDays();
				//industryPicker.add(curr, curr.getBuildCost());
				if (currDisruption > curr.getBuildTime() * 4)
					continue;
				float weight = Math.max(100 - currDisruption, 20);
				if (currDisruption > 0) weight *= 0.5f;
				industryPicker.add(curr, weight);
			}
		}
		Industry toDisrupt = industryPicker.pick();
		if (toDisrupt != null)
		{
			float dur = toDisrupt.getBuildTime() + toDisrupt.getDisruptedDays();
			dur *= StarSystemGenerator.getNormalRandom(random, 1f, 1.25f);
			dur = Math.min(dur, toDisrupt.getBuildTime() * 4);
			toDisrupt.setDisrupted(dur);
			result.disrupted = toDisrupt;
			result.disruptionLength = dur;
		}
		
		return result;
	}
	
	public static String getString(String entry, boolean ucFirst)
	{
		String str = StringHelper.getString("exerelin_invasion", entry);
		if (ucFirst) str = Misc.ucFirst(str);
		return str;
	}
	
	public static String getString(String entry)
	{
		return getString(entry, false);
	}
	
	// TODO: modMult should be used to make invader underestimate defense bonuses
	public static float getDefenderStrength(MarketAPI market, float modMult)
	{
		StatBonus defenderBase = new StatBonus();
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		
		ExerelinFactionConfig defConf = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "defendBonus", "$Faction", 
				Misc.ucFirst(market.getFaction().getDisplayName()));
		defenderBase.modifyPercent("nex_invasionDefBonus", defConf.invasionStrengthBonusDefend * 100f, str);
		
		String increasedDefensesKey = "core_addedDefStr";
		float added = Nex_MarketCMD.getDefenderIncreaseValue(market);
		if (added > 0) {
			defender.modifyFlat(increasedDefensesKey, added, getString("defenderPreparedness", true));
		}
		
		float defenderStr = (int) Math.round(defender.computeEffective(defenderBase.computeEffective(0f)));
		
		return defenderStr;
	}
	
	public static boolean npcInvade(CampaignFleetAPI fleet, MarketAPI market)
	{
		float marines = fleet.getCargo().getMarines();
		float support = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_GROUND_SUPPORT, 0f);
		if (support > marines) support = marines;
		
		StatBonus attackerBase = new StatBonus();
		//defenderBase.modifyFlatAlways("base", baseDef, "Base value for a size " + market.getSize() + " colony");
		
		attackerBase.modifyFlatAlways("core_marines", marines, getString("marinesOnBoard", true));
		attackerBase.modifyFlatAlways("core_support", support, getString("groundSupportCapability", true));
		
		StatBonus attacker = fleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		
		ExerelinFactionConfig atkConf = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "attackBonus", "$Faction", 
				Misc.ucFirst(fleet.getFaction().getDisplayName()));
		attackerBase.modifyPercent("nex_invasionAtkBonus", atkConf.invasionStrengthBonusAttack * 100f, str);
				
		float attackerStr = (int) Math.round(attacker.computeEffective(attackerBase.computeEffective(0f)));
		float defenderStr = getDefenderStrength(market, 1);
		
		Random random = new Random();
		int numRounds = 0;
		
		// play invasion rounds till someone wins
		while (true)
		{
			InvasionRoundResult result = execute(fleet, market, attackerStr, defenderStr, random);
			numRounds++;
			attackerStr = result.atkStr;
			defenderStr = result.defStr;
			
			if (attackerStr <= 0 || defenderStr <= 0)
			{
				finishInvasion(fleet, market, numRounds, defenderStr <= 0);
				if (defenderStr <= 0) 
					conquerMarket(market, fleet.getFaction(), false);
				break;
			}
			
			if (numRounds > 30)	// safety
				break;
		}
		return defenderStr <= 0;
	}
	
	public static void finishInvasion(CampaignFleetAPI attacker, MarketAPI market, float numRounds, boolean success)
	{		
		SectorAPI sector = Global.getSector();
		FactionAPI attackerFaction = attacker.getFaction();
		String attackerFactionId = attackerFaction.getId();
		FactionAPI defenderFaction = market.getFaction();
		String defenderFactionId = defenderFaction.getId();
		
		boolean playerInvolved = false;
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		if ( attacker == playerFleet )
		{
			playerInvolved = true;
			attackerFactionId = PlayerFactionStore.getPlayerFactionId();
			attackerFaction = sector.getFaction(attackerFactionId);
		}
		
		if (attackerFaction == defenderFaction)
		{
			return;
		}
		
		float defStrength = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0);
				
		// trash resource availability
		WeightedRandomPicker<CommodityOnMarketAPI> picker = new WeightedRandomPicker<>();
		for (CommodityOnMarketAPI commodity : market.getAllCommodities())
		{
			if (commodity.isNonEcon() || commodity.isPersonnel()) continue;
			picker.add(commodity);
		}
		for (int i=0; i<numRounds; i++)
		{
			CommodityOnMarketAPI commodity = picker.pick();
			commodity.addTradeModMinus("invasion_" + Misc.genUID(), -1, BaseSubmarketPlugin.TRADE_IMPACT_DAYS);
		}
		
		// XP
		if (playerInvolved)
		{
			float xp = defStrength;
			playerFleet.getCargo().gainCrewXP(xp);
			playerFleet.getCommander().getStats().addXP((long) xp);
			playerFleet.getCommander().getStats().levelUpIfNeeded();
		}
		
		// make orphans
		if (playerInvolved)
		{
			// Spire biology is different
			if (!defenderFactionId.equals("spire") &&  !defenderFactionId.equals("darkspire"))
			{
				float deathsInflicted = defStrength;
				float numAvgKids = MathUtils.getRandomNumberInRange(0f, 1.5f) + MathUtils.getRandomNumberInRange(0f, 1.5f);
				StatsTracker.getStatsTracker().modifyOrphansMade((int)(deathsInflicted * numAvgKids));
			}
		}
				
		log.info( String.format("Invasion of [%s] by " + attacker.getNameWithFaction() + (success ? " successful" : " failed"), market.getName()) );
	}
	
	public static void conquerMarket(MarketAPI market, FactionAPI attackerFaction, boolean playerInvolved)
	{
		FactionAPI defenderFaction = market.getFaction();
		
		// relationship changes
		List<MarketAPI> markets = Global.getSector().getEconomy().getMarketsWithSameGroup(market);
		// we want to use this to only apply the rep change on event message receipted
		// but it's null when it gets to the event for some reason
		List<String> factionsToNotify = new ArrayList<>();  
		Set<String> seenFactions = new HashSet<>();
		float repChangeStrength = market.getSize() * 0.01f;

		for (final MarketAPI otherMarket : markets) {
			if (!otherMarket.getFaction().isHostileTo(defenderFaction)) continue;
			//if (!defender.isInOrNearSystem(otherMarket.getStarSystem())) continue;	// station capture news is sector-wide
			if (seenFactions.contains(otherMarket.getFactionId())) continue;

			RepLevel level = attackerFaction.getRelationshipLevel(otherMarket.getFaction());
			seenFactions.add(otherMarket.getFactionId());
			if (playerInvolved)
			{
				factionsToNotify.add(otherMarket.getFactionId());
			}
		}
		
		// perform actual transfer
		SectorManager.transferMarket(market, attackerFaction, defenderFaction, playerInvolved, true, factionsToNotify, repChangeStrength);
		
		// revengeance
		/*
		if (attackerFactionId.equals(PlayerFactionStore.getPlayerFactionId()))
		{
			RevengeanceManagerEvent rvng = RevengeanceManagerEvent.getOngoingEvent();
			if (rvng!= null) 
			{
				float sizeSq = market.getSize() * market.getSize();
				float mult = 0.25f;
				rvng.addPoints(sizeSq * ExerelinConfig.revengePointsForMarketCaptureMult * mult);
				if (playerInvolved) 
					rvng.addFactionPoints(defenderFactionId, sizeSq * ExerelinConfig.revengePointsForMarketCaptureMult * mult);
			}
		}
		*/
	}
	
	public static boolean canInvade(SectorEntityToken entity)
	{
		if (entity == null) return false;
		MarketAPI market = entity.getMarket();
		if (market == null) return false;
		return ExerelinUtilsMarket.canBeInvaded(market, true);
	}
	
	public static class InvasionRoundResult {
		public float atkDam = 0;
		public float defDam = 0;
		public float atkStr = 0;
		public float defStr = 0;
		public int losses = 0;
		public Industry disrupted = null;
		public float disruptionLength = 0;
	}
}
