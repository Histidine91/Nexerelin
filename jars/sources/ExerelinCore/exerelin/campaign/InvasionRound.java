package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AdminData;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest;
import com.fs.starfarer.api.impl.campaign.econ.impl.PopulationAndInfrastructure;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Industries;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_GrantAutonomy;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.groundbattle.GBConstants;
import exerelin.utilities.*;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

/**
 *
 * @author Histidine
 * Does some math to determine the damage dealt to a market during an invasion, 
 * the marine losses the attackers take, and whether the market is captured as a result.
 */

public class InvasionRound {
	
	public static final float DAMAGE_PER_ROUND_MULT = 0.3f;	// 1 means damage dealt == strength (meaning battle ends in one round if not modified)
	public static final float STR_DEF_MULT = 0;	// what proportion of one side's strength is used to negate other side's attack damage
	public static final float INSTABILITY_PER_ROUND = 0.75f;
	public static final int UNREST_DO_NOT_EXCEED = 10;
	public static final int CONQUEST_UNREST_BONUS = 2;
	public static final float HEAVY_WEAPONS_MULT = 2.5f;
	public static final boolean DEBUG_MESSAGES = true;
	
	public static Logger log = Global.getLogger(InvasionRound.class);
	
	public static void printDebug(String str)
	{
		if (!DEBUG_MESSAGES) return;
		log.info(str);
	}
	
	public static float getRandomDamageMult(Random random) {
		float gauss = NexUtils.getBoundedGaussian(random, -3.2f, 3.2f);
		float mult = (float)(1f + gauss * 0.25f);
		return mult;
	}
	
	public static InvasionRoundResult execute(CampaignFleetAPI fleet, MarketAPI defender, float atkStr, float defStr, Random random)
	{
		printDebug("Executing invasion round of " + defender.getName());
		int marines = 0, mechs = 0;
		if (fleet != null) {
			marines = fleet.getCargo().getMarines();
			mechs = (int)fleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS);
			
			// don't involve more mechs than can actually contribute to battle
			int maxMechs = (int)(marines / HEAVY_WEAPONS_MULT);
			if (mechs > maxMechs) mechs = maxMechs;
		}
		
		float atkDam = (atkStr - defStr * STR_DEF_MULT) * DAMAGE_PER_ROUND_MULT * getRandomDamageMult(random);
		float defDam = (defStr - atkStr * STR_DEF_MULT) * DAMAGE_PER_ROUND_MULT * getRandomDamageMult(random);
		if (atkDam < 0) atkDam = 0;
		if (defDam < 0) defDam = 0;
		
		printDebug("\tInitial attacker strength: " + atkStr);
		printDebug("\tInitial defender strength: " + defStr);
		printDebug("\tAttacker damage: " + atkDam);
		printDebug("\tDefender damage: " + defDam);
		
		float lossMult = 1;
		if (fleet != null)
			lossMult = fleet.getCommanderStats().getDynamic().getStat(Stats.PLANETARY_OPERATIONS_CASUALTIES_MULT).getModifiedValue();
		
		int losses = (int)(marines * defDam/atkStr * lossMult);
		if (losses > marines) losses = marines;
		
		int lossesMech = (int)(mechs * defDam/atkStr * lossMult);
		if (lossesMech > mechs) lossesMech = mechs;
		
		InvasionRoundResult result = new InvasionRoundResult();
		result.losses = losses;
		result.lossesMech = lossesMech;
		result.atkDam = atkDam;
		result.defDam = defDam;
		result.atkStr = atkStr - defDam;
		result.defStr = defStr - atkDam;
		
		//printDebug("\tAttacker losses: " + losses);
		printDebug("\tFinal attacker strength: " + result.atkStr);
		printDebug("\tFinal defender strength: " + result.defStr);
		
		// disruption
		float bombardDisruptDur = Global.getSettings().getFloat("bombardDisruptDuration");
		WeightedRandomPicker<Industry> industryPicker = new WeightedRandomPicker<>();
		for (Industry curr : defender.getIndustries()) {
			if (curr.canBeDisrupted() && !curr.getSpec().hasTag(Industries.TAG_UNRAIDABLE)) 
			{
				float currDisruption = curr.getDisruptedDays();
				//industryPicker.add(curr, curr.getBuildCost());
				float maxDisruption = Math.min(curr.getBuildTime() * 4, bombardDisruptDur);
				if (currDisruption > maxDisruption)
					continue;
				float weight = Math.max(100 - currDisruption, 20);
				if (currDisruption > 0) weight *= 0.5f;
				industryPicker.add(curr, weight);
			}
		}
		Industry toDisrupt = industryPicker.pick();
		if (toDisrupt != null)
		{
			//float dur = toDisrupt.getBuildTime() * StarSystemGenerator.getNormalRandom(random, 0.75f, 1.25f);
			float dur = 30 * StarSystemGenerator.getNormalRandom(random, 0.75f, 1.25f);
			float damMult = Math.min(0.5f + 0.5f * atkDam/defDam, 1);
			dur *= damMult;
			//if (dur > 5) {
				dur += toDisrupt.getDisruptedDays();
				dur = Math.min(dur, toDisrupt.getBuildTime() * 4);
				dur = Math.min(dur, bombardDisruptDur);
				toDisrupt.setDisrupted(dur);
				result.disrupted = toDisrupt;
				result.disruptionLength = dur;
				printDebug("\t" + toDisrupt.getCurrentName() + " disrupted for " + dur + " days");
			//}
		}
		
		NexUtilsMarket.reportInvasionRound(result, fleet, defender, atkStr, defStr);
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
	
	public static float getAttackerStrength(CampaignFleetAPI fleet)
	{
		float marines = fleet.getCargo().getMarines();
		float support = Misc.getFleetwideTotalMod(fleet, Stats.FLEET_GROUND_SUPPORT, 0f);
		if (support > marines) support = marines;
		float mechs = fleet.getCargo().getCommodityQuantity(Commodities.HAND_WEAPONS) * HEAVY_WEAPONS_MULT;
		if (mechs > marines) mechs = marines;
		
		if (!fleet.isPlayerFleet()) {
			mechs = 0;
		}
				
		StatBonus attackerBase = new StatBonus();
		//defenderBase.modifyFlatAlways("base", baseDef, "Base value for a size " + market.getSize() + " colony");
		
		attackerBase.modifyFlatAlways("core_marines", marines, getString("marinesOnBoard", true));
		attackerBase.modifyFlatAlways("core_support", support, getString("groundSupportCapability", true));
		attackerBase.modifyFlatAlways("nex_mechs", mechs, getString("heavyWeaponsOnBoard", true));
		
		StatBonus attacker = fleet.getStats().getDynamic().getMod(Stats.PLANETARY_OPERATIONS_MOD);
		
		NexFactionConfig atkConf = NexConfig.getFactionConfig(fleet.getFaction().getId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "attackBonus", "$Faction", 
				Misc.ucFirst(fleet.getFaction().getDisplayName()));
		attackerBase.modifyMult("nex_invasionAtkBonus", atkConf.invasionStrengthBonusAttack + 1, str);
		
		float attackerStr = (int) Math.round(attacker.computeEffective(attackerBase.computeEffective(0f)));
		return attackerStr;
	}
	
	public static float getAttackerStrength(FactionAPI faction, float marines)
	{
		StatBonus attackerBase = new StatBonus();
		//defenderBase.modifyFlatAlways("base", baseDef, "Base value for a size " + market.getSize() + " colony");
		
		attackerBase.modifyFlatAlways("core_marines", marines, getString("marinesOnBoard", true));
		
		NexFactionConfig atkConf = NexConfig.getFactionConfig(faction.getId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "attackBonus", "$Faction", 
				Misc.ucFirst(faction.getDisplayName()));
		attackerBase.modifyMult("nex_invasionAtkBonus", atkConf.invasionStrengthBonusAttack + 1, str);
		
		float attackerStr = (int) Math.round(attackerBase.computeEffective(0f));
		return attackerStr;
	}
	
	public static StatBonus getDefenderStrengthStat(MarketAPI market) {
		StatBonus defenderModded = new StatBonus();
		StatBonus defender = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD);
		
		String increasedDefensesKey = "core_addedDefStr";
		float added = Nex_MarketCMD.getDefenderIncreaseValue(market);
		if (added > 0) {
			defender.modifyFlat(increasedDefensesKey, added, getString("defenderPreparedness", true));
		}
		
		defenderModded.applyMods(defender);
		
		NexFactionConfig defConf = NexConfig.getFactionConfig(market.getFactionId());
		String str = StringHelper.getStringAndSubstituteToken("exerelin_invasion", "defenseBonus", "$Faction", 
				Misc.ucFirst(market.getFaction().getDisplayName()));
		defenderModded.modifyMult("nex_invasionDefBonus", defConf.invasionStrengthBonusDefend + 1, str);
		
		defenderModded.modifyMult("nex_invasionDefBonusGeneral", Global.getSettings().getFloat("nex_invasionBaseDefenseMult"), 
				StringHelper.getString("exerelin_invasion", "defenseBonusGeneral"));
		
		defender.unmodifyFlat(increasedDefensesKey);
		
		return defenderModded;
	}
	
	/**
	 *
	 * @param market
	 * @param modMult Values over 1 make it overestimate defender strength, values under 1 give an underestimate
	 * @return
	 */
	public static float getDefenderStrength(MarketAPI market, float modMult)
	{
		StatBonus defenderModded = getDefenderStrengthStat(market);
		
		float defenderStr = (int) Math.round(defenderModded.computeEffective(0));
		
		if (modMult == 1) return defenderStr;
		
		float trueBase = defenderModded.getFlatBonus();
		float bonus = defenderStr - trueBase;
		return trueBase + bonus * modMult;
	}
	
	/**
	 * Resolves an NPC invasion event. This overload automatically gets attacker strength and faction from the fleet.
	 * @param fleet
	 * @param market
	 * @return
	 */
	public static boolean npcInvade(CampaignFleetAPI fleet, MarketAPI market)
	{
		float attackerStr = getAttackerStrength(fleet);
		
		return npcInvade(attackerStr, fleet, fleet.getFaction(), market);
	}
	
	/**
	 * Resolves an NPC invasion event.
	 * @param attackerStr
	 * @param fleet Invading fleet. Can be null if being abstracted
	 * @param attackerFaction
	 * @param market
	 * @return
	 */
	public static boolean npcInvade(float attackerStr, CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market)
	{
		float defenderStr = getDefenderStrength(market, 1);
		
		if (attackerStr <= 0) return false;
		
		Random random = new Random();
		int numRounds = 0;
		
		// play invasion rounds till someone wins
		while (true)
		{
			InvasionRoundResult result = execute(fleet, market, attackerStr, defenderStr, random);
			numRounds++;
			attackerStr = result.atkStr;
			defenderStr = result.defStr;
			
			if (fleet != null) {
				fleet.getCargo().removeMarines(result.losses);
				fleet.getCargo().removeCommodity(Commodities.HAND_WEAPONS, result.lossesMech);
			}
			
			if (attackerStr <= 0 || defenderStr <= 0)
			{
				printDebug(market.getName() + " invasion by " + attackerFaction.getDisplayName() + " ended: " + (defenderStr <= 0));
				finishInvasion(fleet, attackerFaction, market, numRounds, defenderStr <= 0);
				if (defenderStr <= 0) 
					conquerMarket(market, attackerFaction, false);
				break;
			}
			
			if (numRounds > 30)	// safety
				break;
		}
		return defenderStr <= 0;
	}
	
	/**
	 * Finalizes invasion effects, including stability impacts and XP received.
	 * @param fleet Invading fleet. Can be null if this is an abstracted NPC fleet.
	 * @param attackerFaction
	 * @param market
	 * @param numRounds Rounds required to complete invasion action.
	 * @param success
	 */
	public static void finishInvasion(CampaignFleetAPI fleet, FactionAPI attackerFaction, MarketAPI market, int numRounds, boolean success)
	{
		SectorAPI sector = Global.getSector();
		FactionAPI defenderFaction = market.getFaction();
		String defenderFactionId = defenderFaction.getId();
		
		boolean playerInvolved = false;
		CampaignFleetAPI playerFleet = sector.getPlayerFleet();
		if ( fleet == playerFleet )
		{
			playerInvolved = true;
			String attackerFactionId = PlayerFactionStore.getPlayerFactionId();
			attackerFaction = sector.getFaction(attackerFactionId);
		}
		
		if (attackerFaction == defenderFaction)
		{
			return;
		}
		
		float defStrength = market.getStats().getDynamic().getMod(Stats.GROUND_DEFENSES_MOD).computeEffective(0);
		
		applyGlobalDefenderIncrease(market, attackerFaction, playerInvolved, success);
		
		// destabilize
		int stabilityPenalty = getStabilityPenalty(market, numRounds, success);
		String origOwner = NexUtilsMarket.getOriginalOwner(market);
		if (success && origOwner != null && defenderFactionId.equals(origOwner))
			stabilityPenalty += CONQUEST_UNREST_BONUS;
		
		if (stabilityPenalty > 0) {
			String reason = Misc.ucFirst(getString("recentlyInvaded"));
			if (!attackerFaction.isPlayerFaction() || Misc.isPlayerFactionSetUp()) {
				reason = attackerFaction.getDisplayName() + " " + getString("invasion");
			}
			RecentUnrest.get(market).add(stabilityPenalty, reason);
		}
		
		// trash resource availability — doesn't seem to do anything in current form
		/*
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
		*/
		market.reapplyConditions();
		market.reapplyIndustries();
		
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
			if (StatsTracker.haveOrphans(defenderFactionId))
			{
				float deathsInflicted = defStrength;
				float numAvgKids = MathUtils.getRandomNumberInRange(0f, 1.5f) + MathUtils.getRandomNumberInRange(0f, 1.5f);
				StatsTracker.getStatsTracker().modifyOrphansMade((int)(deathsInflicted * numAvgKids));
			}
		}
		
		if (success) {
			market.getMemoryWithoutUpdate().unset(GBConstants.MEMKEY_INVASION_FAIL_STREAK);
		} else {
			NexUtilsMarket.incrementInvasionFailStreak(market, attackerFaction, true);
		}
		
		NexUtilsMarket.reportInvasionFinished(fleet, attackerFaction, market, numRounds, success);
		
		log.info( String.format("Invasion of [%s] by " + (fleet == null ? attackerFaction.getDisplayName() : fleet.getNameWithFaction())
				+ (success ? " successful" : " failed"), market.getName()) );
	}
	
	public static int getStabilityPenalty(MarketAPI market, int rounds, boolean success) {
		float stabilityPenalty = (int)(rounds * INSTABILITY_PER_ROUND);
		if (stabilityPenalty < 1) stabilityPenalty = 1;
		if (stabilityPenalty > 6) stabilityPenalty = 6;
		if (!success) stabilityPenalty /= 2;
		
		int maxUnrest = Math.min(rounds * 2, UNREST_DO_NOT_EXCEED);
		int threshold = maxUnrest - RecentUnrest.getPenalty(market);
		if (threshold < 0) threshold = 0;
		
		stabilityPenalty = Math.min(stabilityPenalty, threshold);
		return Math.round(stabilityPenalty);
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
		float repChangeStrength = (market.getSize() - 2) * 0.01f;
		if (repChangeStrength <= 0) repChangeStrength = 0;
		
		if (playerInvolved && !defenderFaction.getId().equals("nex_derelict")) {
			for (final MarketAPI otherMarket : markets) {
				if (!otherMarket.getFaction().isHostileTo(defenderFaction)) continue;
				//if (!defender.isInOrNearSystem(otherMarket.getStarSystem())) continue;	// station capture news is sector-wide
				if (seenFactions.contains(otherMarket.getFactionId())) continue;
				
				seenFactions.add(otherMarket.getFactionId());
				factionsToNotify.add(otherMarket.getFactionId());
			}
		}
		
		// perform actual transfer
		SectorManager.transferMarket(market, attackerFaction, defenderFaction, playerInvolved, true, factionsToNotify, repChangeStrength);
		if (market.getFaction().isPlayerFaction() && !playerInvolved) {
			makeAutonomousIfNeeded(market);
		}
	}

	protected static void makeAutonomousIfNeeded(MarketAPI market) {
		if (playerHasFreeGoverningCapacity()) return;
		Nex_GrantAutonomy.grantAutonomy(market);
		Nex_GrantAutonomy.setNoStabLossOnRevokeAutonomy(market);
	}

	protected static boolean playerHasFreeGoverningCapacity() {
		int capacity = 0;
		for (AdminData data : Global.getSector().getCharacterData().getAdmins()) {
			if (data.getMarket() == null) capacity++;
		}
		// FIXME: will probably count wrong if the per-colony stability penalty is >1
		int overLimit = PopulationAndInfrastructure.getMismanagementPenalty();
		capacity -= overLimit;
		return capacity >= 0;	// check is done after we're already admining the new market, so if it's at zero it means we had/have just enough
	}
	
	public static boolean canInvade(SectorEntityToken entity)
	{
		if (entity == null) return false;
		MarketAPI market = entity.getMarket();
		if (market == null) return false;
		return NexUtilsMarket.canBeInvaded(market, true);
	}
	
	/**
	 * Applies the defense-increase-from-raids bonus to markets, following an invasion.
	 * This affects all markets in the system that are inhospitable or worse to the invader,
	 * as well as any such markets Sector-wide if they are allied to the target.
	 * Effect is amplified for same-system and same-faction markets, and on hard mode.
	 * @param target
	 * @param attacker
	 * @param playerInvolved Was this invasion conducted by the player's personal fleet?
	 * @param successful Was the invasion successful?
	 */
	public static void applyGlobalDefenderIncrease(MarketAPI target, FactionAPI attacker, boolean playerInvolved, boolean successful)
	{
		boolean hyperspace = target.getContainingLocation().isHyperspace();
		boolean isPlayerFaction = attacker.isPlayerFaction() || PlayerFactionStore.getPlayerFaction() == attacker;
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsInGroup(target.getEconGroup())) {
			if (market == target) continue;
			
			if (!market.getFaction().isAtBest(attacker, RepLevel.INHOSPITABLE))
				continue;
			
			boolean shouldAlert = (!hyperspace && market.getContainingLocation() == target.getContainingLocation())
					|| AllianceManager.areFactionsAllied(market.getFactionId(), target.getFactionId());
			if (!shouldAlert) continue;
			
			float mult = 0.25f;
			if (market.getFaction().isHostileTo(attacker))
				mult *= 2f;
			if (!hyperspace && market.getContainingLocation() == target.getContainingLocation())
				mult *= 2f;
			if (successful)
				mult *= 2f;
			if (!isPlayerFaction)
				mult *= 0.75f;
			else if (SectorManager.getManager().isHardMode())
				mult *= 1.5f;
			
			log.info("Increasing defence for market " + market.getName() 
					+ " (" + market.getFaction().getDisplayName() + "): mult " + mult);
			
			Nex_MarketCMD.applyDefenderIncreaseFromRaid(market, mult);
		}
	}
	
	public static class InvasionRoundResult {
		public float atkDam = 0;
		public float defDam = 0;
		public float atkStr = 0;
		public float defStr = 0;
		public int losses = 0;
		public int lossesMech = 0;
		public Industry disrupted = null;
		public float disruptionLength = 0;
	}
}
