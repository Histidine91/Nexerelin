package exerelin.campaign.battle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.BattleAPI.BattleSide;
import com.fs.starfarer.api.campaign.BattleAutoresolverPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CombatDamageData;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin;
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.EncounterOption;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.fleets.DefenceStationManager;
import org.apache.log4j.Logger;

public class NexBattleAutoresolvePlugin implements BattleAutoresolverPlugin {
	
	public static Logger log = Global.getLogger(NexBattleAutoresolvePlugin.class);
	public static final boolean DEBUG_MODE = false;
	public static final float STATION_STRENGTH_MULT = 0.4f;
	public static final float MODULE_STRENGTH_MULT = 1f;
	
	protected static Map<FleetMemberAPI, Map<Integer, Float>> moduleHealths = new HashMap<>();
	
	private static class EngagementResultImpl implements EngagementResultAPI {
		private BattleAPI battle;
		private EngagementResultForFleetImpl winnerResult, loserResult;
		
		public EngagementResultImpl(BattleAPI battle, CampaignFleetAPI winner, CampaignFleetAPI loser) {
			this.battle = battle;
			winnerResult = new EngagementResultForFleetImpl(winner);
			loserResult = new EngagementResultForFleetImpl(loser);
		}
		
		public BattleAPI getBattle() {
			return battle;
		}

		public boolean didPlayerWin() {
			//return winnerResult.getFleet() != null && winnerResult.getFleet().isPlayerFleet();
			return winnerResult.getFleet() != null && Misc.isPlayerOrCombinedContainingPlayer(winnerResult.getFleet());
		}

		public EngagementResultForFleetAPI getLoserResult() {
			return loserResult;
		}

		public EngagementResultForFleetAPI getWinnerResult() {
			return winnerResult;
		}

		public boolean isPlayerOutBeforeEnd() {
			return false;
		}

		public void setPlayerOutBeforeEnd(boolean playerOutBeforeEnd) {
		}

		public void setBattle(BattleAPI battle) {
			this.battle = battle;
		}

		public CombatDamageData getLastCombatDamageData() {
			return null;
		}

		public void setLastCombatDamageData(CombatDamageData lastCombatData) {
			
		}
	}
	
	private static class EngagementResultForFleetImpl implements EngagementResultForFleetAPI {
		private CampaignFleetAPI fleet;
		private FleetGoal goal;
		private boolean winner = false;
		private List<FleetMemberAPI> deployed = new ArrayList<FleetMemberAPI>();
		private List<FleetMemberAPI> reserves = new ArrayList<FleetMemberAPI>();
		private List<FleetMemberAPI> destroyed = new ArrayList<FleetMemberAPI>();
		private List<FleetMemberAPI> disabled = new ArrayList<FleetMemberAPI>();
		private List<FleetMemberAPI> retreated = new ArrayList<FleetMemberAPI>();
		
		public EngagementResultForFleetImpl(CampaignFleetAPI fleet) {
			this.fleet = fleet;
		}
		
		public List<FleetMemberAPI> getDeployed() {
			return deployed;
		}
		public List<FleetMemberAPI> getDestroyed() {
			return destroyed;
		}
		public List<FleetMemberAPI> getDisabled() {
			return disabled;
		}
		public CampaignFleetAPI getFleet() {
			return fleet;
		}
		public FleetGoal getGoal() {
			return goal;
		}
		public List<FleetMemberAPI> getReserves() {
			return reserves;
		}
		public List<FleetMemberAPI> getRetreated() {
			return retreated;
		}
		public List<DeployedFleetMemberAPI> getAllEverDeployedCopy() {
			return null;
		}
		public boolean isWinner() {
			return winner;
		}
		public void setWinner(boolean winner) {
			this.winner = winner;
		}
		public void resetAllEverDeployed() {
		}
		public void setGoal(FleetGoal goal) {
			this.goal = goal;
		}
		public boolean isPlayer() {
			return false;
		}
	}
	
	private static enum FleetMemberBattleOutcome {
		UNSCATHED,
		LIGHT_DAMAGE,
		MEDIUM_DAMAGE,
		HEAVY_DAMAGE,
		DISABLED,
	}
	
	private static final class FleetMemberAutoresolveData {
		private FleetMemberAPI member;
		private float strength;
		private float shieldRatio;
		private boolean combatReady;
		private boolean isStation;
	}
	
	private static final class FleetAutoresolveData {
		private CampaignFleetAPI fleet;
		private float fightingStrength;
		private List<FleetMemberAutoresolveData> members = new ArrayList<FleetMemberAutoresolveData>();
		
		private void report() {
			if (!report) return;
			
			NexBattleAutoresolvePlugin.report(String.format("Fighting strength of %s: %f", fleet.getNameWithFaction(), fightingStrength));
			for (FleetMemberAutoresolveData data : members) {
				String str = String.format("%40s: CR % 3d%%    FP % 4d     STR % 3f   Shield %3.2f",
									data.member.getVariant().getFullDesignationWithHullName(),
									(int)(data.member.getRepairTracker().getCR() * 100f),
									data.member.getFleetPointCost(),
									data.strength,
									data.shieldRatio);
				NexBattleAutoresolvePlugin.report("  " + str);
			}

		}
	}
	
	private CampaignFleetAPI one;
	private CampaignFleetAPI two;
	private final BattleAPI battle;

	private boolean playerPursuitAutoresolveMode = false;
	private List<FleetMemberAPI> playerShipsToDeploy;
	
	public NexBattleAutoresolvePlugin(BattleAPI battle) {
		this.battle = battle;

		one = battle.getCombinedOne();
		two = battle.getCombinedTwo();
		if (battle.isPlayerInvolved()) {
			one = battle.getPlayerCombined();
			two = battle.getNonPlayerCombined();
		}
		
		setReport(false);
		if (DEBUG_MODE)
		{
			for (CampaignFleetAPI fleet : battle.getBothSides())
			{
				if (fleet.isStationMode())
				{
					setReport(true);
					break;
				}
			}
		}
		else
		{
			setReport(Global.getSettings().isDevMode());
			setReport(false);
		}
	}
		
	// same as vanilla except stations won't disengage
	@Override
	public void resolve() {
		// figure out battle type (escape vs engagement)
		
		report("***");
		report("***");
		report(String.format("Autoresolving %s vs %s", one.getNameWithFaction(), two.getNameWithFaction()));
		
		context = new NexFleetEncounterContext();
		context.setAutoresolve(true);
		context.setBattle(battle);
		EncounterOption optionOne = one.getAI().pickEncounterOption(context, two);
		EncounterOption optionTwo = two.getAI().pickEncounterOption(context, one);
		boolean stationForceMode = false;
		
		if (DefenceStationManager.hasStation(battle, BattleSide.ONE) && optionOne == EncounterOption.DISENGAGE)
		{
			DefenceStationManager.debugMessage("Fleet one " + one.getNameWithFaction() + " in " 
					+ one.getContainingLocation().getName() + " has station, blocking disengage");
			optionOne = EncounterOption.HOLD_VS_STRONGER;
			stationForceMode = true;
		}
		if (DefenceStationManager.hasStation(battle, BattleSide.TWO) && optionTwo == EncounterOption.DISENGAGE)
		{
			DefenceStationManager.debugMessage("Fleet two " + two.getNameWithFaction() + " in " 
					+ two.getContainingLocation().getName() + " has station, blocking disengage");
			optionTwo = EncounterOption.HOLD_VS_STRONGER;
			stationForceMode = true;
		}
		
		if (optionOne == EncounterOption.DISENGAGE && optionTwo == EncounterOption.DISENGAGE) {
			report("Both fleets want to disengage");
			report("Finished autoresolving engagement");
			report("***");
			report("***");
			return;
		}
		
		boolean oneEscaping = false;
		boolean twoEscaping = false;
		
		boolean freeDisengageIfCanOutrun = false;
		
		if (optionOne == EncounterOption.DISENGAGE && optionTwo == EncounterOption.ENGAGE) {
			report(String.format("%s wants to disengage", one.getNameWithFaction()));
			oneEscaping = true;
			if (freeDisengageIfCanOutrun && context.canOutrunOtherFleet(one, two)) {
				report(String.format("%s can outrun other fleet", one.getNameWithFaction()));
				report("Finished autoresolving engagement");
				report("***");
				report("***");
				return;
			}
		}
		if (optionOne == EncounterOption.ENGAGE && optionTwo == EncounterOption.DISENGAGE) {
			report(String.format("%s wants to disengage", two.getNameWithFaction()));
			twoEscaping = true;
			if (freeDisengageIfCanOutrun && context.canOutrunOtherFleet(two, one)) {
				report(String.format("%s can outrun other fleet", two.getNameWithFaction()));
				report("Finished autoresolving engagement");
				report("***");
				report("***");
				return;
			}
		}
		
		int limit = stationForceMode ? 50 : 1;
		for (int i=0; i<limit; i++)
		{
			resolveEngagement(context, oneEscaping, twoEscaping);
			if (one.getFleetData().getCombatReadyMembersListCopy().isEmpty())
				break;
			if (two.getFleetData().getCombatReadyMembersListCopy().isEmpty())
				break;
		}
		
		report("");
		report("Finished autoresolving engagement");
		report("***");
		report("***");
	}

	
	public void resolvePlayerPursuit(FleetEncounterContext context, List<FleetMemberAPI> playerShipsToDeploy) {
		this.context = context;
		playerPursuitAutoresolveMode = true;
		this.playerShipsToDeploy = playerShipsToDeploy;
		resolveEngagement(context, false, true);
	}
	
	private void resolveEngagement(FleetEncounterContext context, boolean oneEscaping, boolean twoEscaping) {
		
		FleetAutoresolveData dataOne = computeDataForFleet(one);
		FleetAutoresolveData dataTwo = computeDataForFleet(two);
		
		if (dataOne.fightingStrength <= 0 && dataTwo.fightingStrength <= 0) {
			return;
		}
		if (dataOne.fightingStrength <= 0.1f) {
			dataOne.fightingStrength = 0.1f;
		}
		if (dataTwo.fightingStrength <= 0.1f) {
			dataTwo.fightingStrength = 0.1f;
		}
		
		FleetAutoresolveData winner, loser;

		report("");
		report("--------------------------------------------");
		dataOne.report();
		
		report("");
		report("--------------------------------------------");
		dataTwo.report();

		report("");
		report("");
		
		boolean loserEscaping = false;
		if ((dataOne.fightingStrength > dataTwo.fightingStrength || twoEscaping) && !oneEscaping) {
			report(String.format("%s won engagement", one.getNameWithFaction()));
			winner = dataOne;
			loser = dataTwo;
			if (twoEscaping) {
				loserEscaping = true;
			}
		} else {
			report(String.format("%s won engagement", two.getNameWithFaction()));
			winner = dataTwo;
			loser = dataOne;
			if (oneEscaping) {
				loserEscaping = true;
			}
		}
		
		float winnerAdvantage = winner.fightingStrength / loser.fightingStrength;
//		if (winnerAdvantage > 2f) winnerAdvantage = 2f;
//		if (winnerAdvantage < 0.5f) winnerAdvantage = 0.5f;
		if (winnerAdvantage > 10f) winnerAdvantage = 10f;
		if (winnerAdvantage < 0.1f) winnerAdvantage = 0.1f;
		//if (winnerAdvantage < 0.1f) winnerAdvantage = 0.1f;
		
		float damageDealtToWinner = loser.fightingStrength / winnerAdvantage;
		float damageDealtToLoser = winner.fightingStrength * winnerAdvantage;
		if (playerPursuitAutoresolveMode) {
			damageDealtToWinner = 0f;
		}
		
		float damMult = Global.getSettings().getFloat("autoresolveDamageMult");
		damageDealtToWinner *= damMult;
		damageDealtToLoser *= damMult;
		
 		//result = new EngagementResultImpl(winner.fleet, loser.fleet);
 		result = new EngagementResultImpl(context.getBattle(), 
 										  context.getBattle().getCombinedFor(winner.fleet),
 										  context.getBattle().getCombinedFor(loser.fleet));
		
		report("");
		report("Applying damage to loser's ships");
		report("--------------------------------------------");
		Collections.shuffle(loser.members);
		//boolean loserCarrierLeft = false;
		Map<FleetMemberAPI, FleetMemberBattleOutcome> wingDamage = new HashMap<FleetMemberAPI, FleetMemberBattleOutcome>();
		for (FleetMemberAutoresolveData data : loser.members) {
			report(String.format("Remaining damage to loser: %02.2f", damageDealtToLoser));
			FleetMemberBattleOutcome outcome = computeOutcomeForFleetMember(data, 1f/winnerAdvantage, damageDealtToLoser, loserEscaping, false);
			damageDealtToLoser -= data.strength;
			
			if (data.member.isFighterWing() && outcome != FleetMemberBattleOutcome.UNSCATHED) {
				wingDamage.put(data.member, outcome);
			}
			
//			if (data.member.isMothballed()) continue;
//			if (data.member.getStatus().getHullFraction() > 0 && data.member.getNumFlightDecks() > 0) {
//				loserCarrierLeft = true;
//			}
			
			if (damageDealtToLoser <= 0)
				break;
		}
		
		for (FleetMemberAutoresolveData data : loser.members) {
//			if (data.member.isFighterWing()) {
//				FleetMemberBattleOutcome outcome = wingDamage.get(data.member);
//				if (outcome != null) {
//					float crLoss = 0f;
//					switch (outcome) {
//					case DISABLED: 
//						crLoss = 1f;
//						data.member.getStatus().disable();
//						break;
//					case HEAVY_DAMAGE: crLoss = 0.75f; break;
//					case MEDIUM_DAMAGE: crLoss = 0.5f; break;
//					case LIGHT_DAMAGE: crLoss = 0.25f; break;
//					case UNSCATHED: crLoss = 0f; break;
//					}
//					if (crLoss > 0) {
//						data.member.getRepairTracker().applyCREvent(-crLoss, "lost craft in battle");
//					}
//				}
//				if (data.member.getRepairTracker().getBaseCR() <= 0f && !loserCarrierLeft) {
//					result.getLoserResult().getDisabled().add(data.member);
//				} else {
//					result.getLoserResult().getRetreated().add(data.member);
//				}
//			} else {
				if (data.member.getStatus().getHullFraction() > 0) { // || (data.member.isFighterWing() && loserCarrierLeft)) {
					result.getLoserResult().getRetreated().add(data.member);
				} else {
					result.getLoserResult().getDisabled().add(data.member);
				}
//			}
		}
		wingDamage.clear();
		
		
		
		report("");
		report("Applying damage to winner's ships");
		report("--------------------------------------------");
		Collections.shuffle(winner.members);
		
		boolean winnerCarrierLeft = false;
		for (FleetMemberAutoresolveData data : winner.members) {
			if (!data.combatReady) continue;
			report(String.format("Remaining damage to winner: %02.2f", damageDealtToWinner));
			FleetMemberBattleOutcome outcome = computeOutcomeForFleetMember(data, winnerAdvantage, damageDealtToWinner, false, loserEscaping);
			damageDealtToWinner -= data.strength;

			if (data.member.isFighterWing() && outcome != FleetMemberBattleOutcome.UNSCATHED) {
				wingDamage.put(data.member, outcome);
			}
			
			if (data.member.isMothballed()) continue;
			if (data.member.getStatus().getHullFraction() > 0 && data.member.getNumFlightDecks() > 0) {
				winnerCarrierLeft = true;
			}
			
			if (damageDealtToWinner <= 0)
				break;
		}
		
		// which ships should count as "deployed" for CR loss purposes?
		// anything that was disabled, and then anything up to double the loser's strength
		float deployedStrength = 0f;
		float maxDeployedStrength = loser.fightingStrength * 2f;
		for (FleetMemberAutoresolveData data : winner.members) {
			if (!(data.member.getStatus().getHullFraction() > 0 || (data.member.isFighterWing() && winnerCarrierLeft))) {
				deployedStrength += data.strength;
			}
		}
		
		for (FleetMemberAutoresolveData data : winner.members) {
			if (playerPursuitAutoresolveMode) {
				if (playerShipsToDeploy.contains(data.member) || data.member.isAlly()) {
					result.getWinnerResult().getDeployed().add(data.member);
				} else {
					result.getWinnerResult().getReserves().add(data.member);
				}
			} else {
//				if (data.member.isFighterWing()) {
//					FleetMemberBattleOutcome outcome = wingDamage.get(data.member);
//					if (outcome != null) {
//						float crLoss = 0f;
//						switch (outcome) {
//						case DISABLED: 
//							crLoss = 1f;
//							data.member.getStatus().disable();
//						break;
//						case HEAVY_DAMAGE: crLoss = 0.75f; break;
//						case MEDIUM_DAMAGE: crLoss = 0.5f; break;
//						case LIGHT_DAMAGE: crLoss = 0.25f; break;
//						case UNSCATHED: crLoss = 0f; break;
//						}
//						if (crLoss > 0) {
//							data.member.getRepairTracker().applyCREvent(-crLoss, "lost craft in battle");
//						}
//					}
//					if (data.member.getRepairTracker().getBaseCR() <= 0f && !loserCarrierLeft) {
//						result.getLoserResult().getDisabled().add(data.member);
//					} else {
//						result.getLoserResult().getRetreated().add(data.member);
//					}
//				} else {
					if (data.member.getStatus().getHullFraction() > 0) { // || (data.member.isFighterWing() && winnerCarrierLeft)) {
						if (deployedStrength < maxDeployedStrength) {
							result.getWinnerResult().getDeployed().add(data.member);
							deployedStrength += data.strength;
						} else {
							result.getWinnerResult().getReserves().add(data.member);
						}
					} else {
						result.getWinnerResult().getDisabled().add(data.member);
					}
//				}
			}
		}
		
		
		// CR hit, ship/crew losses get applied here
		((EngagementResultForFleetImpl)result.getWinnerResult()).setGoal(FleetGoal.ATTACK);
		((EngagementResultForFleetImpl)result.getWinnerResult()).setWinner(true);
		
		if (loserEscaping) {
			((EngagementResultForFleetImpl)result.getLoserResult()).setGoal(FleetGoal.ESCAPE);
		} else {
			((EngagementResultForFleetImpl)result.getLoserResult()).setGoal(FleetGoal.ATTACK);
		}
		((EngagementResultForFleetImpl)result.getLoserResult()).setWinner(false);
		
		
		// will be handled inside the interaction dialog if it's the player auto-resolving pursuit
		if (!playerPursuitAutoresolveMode) {
			context.processEngagementResults(result);
			//context.applyPostEngagementOption(result);
			context.performPostVictoryRecovery(result);
			
			// need to set up one fleet as attacking, one fleet as losing + escaping/disengaging
			// for the scrapping/looting/etc to work properly
			context.getDataFor(winner.fleet).setDisengaged(false);
			context.getDataFor(winner.fleet).setWonLastEngagement(true);
			context.getDataFor(winner.fleet).setLastGoal(FleetGoal.ATTACK);
			
			context.getDataFor(loser.fleet).setDisengaged(true);
			context.getDataFor(loser.fleet).setWonLastEngagement(false);
			context.getDataFor(loser.fleet).setLastGoal(FleetGoal.ESCAPE);

			if (!winner.fleet.isAIMode()) {
				context.generateLoot(null, true);
				context.autoLoot();
				context.recoverCrew(winner.fleet);
			}
			//context.repairShips();
			
			context.applyAfterBattleEffectsIfThereWasABattle();
		} else {
			// for ship recovery to recognize these ships as non-player
//			DataForEncounterSide data = context.getDataFor(loser.fleet);
//			for (FleetMemberAPI member : data.getDisabledInLastEngagement()) {
//				member.setOwner(1);
//			}
//			for (FleetMemberAPI member : data.getDestroyedInLastEngagement()) {
//				member.setOwner(1);
//			}
			for (FleetMemberAutoresolveData data : loser.members) {
				data.member.setOwner(1);
			}
		}
		
//		context.getBattle().uncombine();
//		context.getBattle().finish();
	}
	
	protected void clampStationCR(FleetMemberAPI station)
	{
		//report("Clamping CR for station " + station.getShipName());
		station.getRepairTracker().setCR(station.getRepairTracker().getMaxCR());
	}
	
	public static void removeStationFromModuleHullCache(FleetMemberAPI member)
	{
		moduleHealths.remove(member);
	}
	
	protected int getAttachedModuleCount(FleetMemberAPI member)
	{
		int count = 0;
		for (int i=0; i < member.getVariant().getModuleSlots().size(); i++)
		{
			if (!member.getStatus().isDetached(i))
				count++;
		}
		return count;
	}
	
	protected void initModuleHullFractionCacheIfNeeded(FleetMemberAPI member, int index)
	{
		if (!moduleHealths.containsKey(member))
			moduleHealths.put(member, new HashMap<Integer, Float>());
		Map<Integer, Float> healths = moduleHealths.get(member);
		if (!healths.containsKey(index))
			healths.put(index, 1f);
	}
	
	protected float getModuleHullFraction(FleetMemberAPI member, int index)
	{
		initModuleHullFractionCacheIfNeeded(member, index);		
		return moduleHealths.get(member).get(index);
	}
	
	/**
	 * Applies damage to a module's hull fraction, using stored current hull fraction.
	 * @param member
	 * @param damageFraction
	 * @param index
	 */
	protected void addModuleDamage(FleetMemberAPI member, float damageFraction, int index)
	{
		float currHull = getModuleHullFraction(member, index);
		float newHull = currHull - damageFraction;
		if (newHull < 0) newHull = 0;
		member.getStatus().setHullFraction(index, newHull);
		moduleHealths.get(member).put(index, newHull);
		
		if (newHull <= 0)
		{
			member.getStatus().setDetached(index, true);
		}
	}
	
	protected void addDamageToOneModule(FleetMemberAPI member, float damageFraction)
	{		
		WeightedRandomPicker<Integer> picker = new WeightedRandomPicker<>();
		for (int i = 0; i < member.getVariant().getModuleSlots().size(); i++)
		{
			// preferentially damage already-damaged modules
			float weight = 1.5f - getModuleHullFraction(member, i);
			if (member.getStatus().isDetached(i)) continue;
			picker.add(i, weight);
		}
		if (picker.isEmpty()) 
		{
			report(member.getShipName() + " has no modules left to damage");
			return;
		}
		
		int moduleNum = picker.pick();
		report("\t\tDamaging module " + moduleNum + " on " + member.getShipName());
		addModuleDamage(member, damageFraction, moduleNum);
		
		int remainingModules = getAttachedModuleCount(member);
		report("\t\t" + member.getShipName() + " has " + remainingModules + " modules left");
		
		// destroy parent if Vast Bulk + no modules remaining
		if (member.getVariant().hasHullMod("vastbulk") && remainingModules <= 0)
		{
			report(member.getShipName() + " is dead, attempting to kill");
			member.getStatus().disable();
			removeStationFromModuleHullCache(member);
		}
	}
	
	protected void addDamageToHullAndAllModules(FleetMemberAPI member, float damageFraction)
	{
		if (member.getVariant().getModuleSlots().isEmpty())	// no modules, just wreck hull
		{
			member.getStatus().applyHullFractionDamage(damageFraction);
			return;
		}
		
		for (int i = 0; i < member.getVariant().getModuleSlots().size(); i++)
		{
			report("\t\tDamaging module " + i + " on " + member.getShipName());
			addModuleDamage(member, damageFraction, i);
		}
		
		int remainingModules = getAttachedModuleCount(member);
		report("\t\t" + member.getShipName() + " has " + remainingModules + " modules left");
		member.getStatus().applyHullFractionDamage(damageFraction);	// needs to be done after module damage?
		
		// destroy parent if Vast Bulk + no modules remaining, or all modules have 0 health
		if (member.getVariant().hasHullMod("vastbulk") && remainingModules <= 0 || member.getStatus().getHullFraction() <= 0)
		{
			report(member.getShipName() + " is dead, attempting to kill");
			member.getStatus().disable();
			removeStationFromModuleHullCache(member);
		}
	}
	
	/*
	protected void preventModuleRespawn(FleetMemberAPI member)
	{
		for (int i=0; i < member.getVariant().getModuleSlots().size(); i++)
		{
			if (getModuleHullFraction(member, i) <= 0)
				member.getStatus().setDetached(i, true);
		}
	}
	*/
	
	/**
	 * Damages the hull and all modules by a specified fraction if ship does not have Vast Bulk;
	 * else damages a randomly picked module
	 * @param member
	 * @param damageFraction
	 */
	protected void addDamage(FleetMemberAPI member, float damageFraction)
	{
		boolean hasVastBulk = member.getVariant().hasHullMod("vastbulk");
		if (!hasVastBulk)
		{
			report("  Damage fraction: " + damageFraction);
			report("  Hull before: " + member.getStatus().getHullFraction());
			//member.getStatus().applyHullFractionDamage(damageFraction);
			addDamageToHullAndAllModules(member, damageFraction);
			
			report("  Hull after: " + member.getStatus().getHullFraction());
		}
		else {
			//preventModuleRespawn(member);
			addDamageToOneModule(member, damageFraction);
		}
	}
	
	private FleetMemberBattleOutcome computeOutcomeForFleetMember(FleetMemberAutoresolveData data, float advantageInBattle, 
											  float maxDamage, boolean escaping, boolean enemyEscaping) {
		ShipHullSpecAPI hullSpec = data.member.getHullSpec();
		
		float unscathed = 1f;
		float lightDamage = 0f;
		float mediumDamage = 0f;
		float heavyDamage = 0f;
		float disabled = 0f;
		
		switch (hullSpec.getHullSize()) {
		case CAPITAL_SHIP:
			unscathed = 5f;
			break;
		case CRUISER:
			unscathed = 10f;
			break;
		case DESTROYER:
			unscathed = 15;;
			break;
		case FRIGATE:
		case FIGHTER:
			unscathed = 30f;
			break;
		}
		
		float maxDamageRatio = maxDamage / data.strength;
		if (maxDamageRatio > 1) maxDamageRatio = 1;
		if (maxDamageRatio <= 0) maxDamageRatio = 0;
		
		if (maxDamageRatio >= 0.8f) {
			disabled = 20f;
			heavyDamage = 10f;
			mediumDamage = 10f;
			lightDamage = 5f;
		} else if (maxDamageRatio >= 0.6f) {
			disabled = 5f;
			heavyDamage = 20f;
			mediumDamage = 10f;
			lightDamage = 5f;
		} else if (maxDamageRatio >= 0.4f) {
			disabled = 0f;
			heavyDamage = 10f;
			mediumDamage = 20f;
			lightDamage = 10f;
		} else if (maxDamageRatio >= 0.2f) {
			disabled = 0f;
			heavyDamage = 0f;
			mediumDamage = 10f;
			lightDamage = 20f;
		} else if (maxDamageRatio > 0) {
			disabled = 0f;
			heavyDamage = 0f;
			mediumDamage = 5f;
			lightDamage = 10f;
		}
		
		if (escaping) {
			unscathed *= 2f;
			lightDamage *= 1.5f;
		}
		
		if (enemyEscaping) {
			disabled *= 0.5f;
			heavyDamage *= 0.6f;
			mediumDamage *= 0.7f;
			lightDamage *= 0.8f;
			unscathed *= 1f;
		}
		
		// advantageInBattle goes from 0.5 (bad) to 2 (good)
		unscathed *= advantageInBattle;
		lightDamage *= advantageInBattle;
		
		float shieldRatio = data.shieldRatio;
		
		// shieldRatio goes from 0 (no shields/no flux) to 1 (shields dominate hull/armor)
		// shieldRatio at 0.5 roughly indicates balanced shields and hull/armor effectiveness
		
		disabled *= 1.5f - shieldRatio * 1f;
		heavyDamage *= 1.4f - shieldRatio * 0.8f;
		mediumDamage *= 1.3f - shieldRatio * 0.6f;
		lightDamage *= 1.2f - shieldRatio * 0.4f;
		unscathed *= 0.9f + shieldRatio * 0.2f;
		
		// station hax - not needed now that "apply damage once for each module" is fixed
		/*
		if (data.isStation)
		{
			unscathed *= 2.5;
			lightDamage *= 2;
			heavyDamage *= 0.5;
			disabled *= 0.25;
		}
		*/
		
		
		WeightedRandomPicker<FleetMemberBattleOutcome> picker = new WeightedRandomPicker<FleetMemberBattleOutcome>();

		picker.add(FleetMemberBattleOutcome.DISABLED, disabled);
		picker.add(FleetMemberBattleOutcome.HEAVY_DAMAGE, heavyDamage);
		picker.add(FleetMemberBattleOutcome.MEDIUM_DAMAGE, mediumDamage);
		picker.add(FleetMemberBattleOutcome.LIGHT_DAMAGE, lightDamage);
		picker.add(FleetMemberBattleOutcome.UNSCATHED, unscathed);
		
		
		report(String.format("Disabled: %d, Heavy: %d, Medium: %d, Light: %d, Unscathed: %d (Shield ratio: %3.2f)",
							(int) disabled, (int) heavyDamage, (int) mediumDamage, (int) lightDamage, (int) unscathed, shieldRatio));
		
		FleetMemberBattleOutcome outcome = picker.pick();
		
		
		float damage = 0f;
		
		data.member.getStatus().resetDamageTaken();
		int count = data.member.getStatus().getNumStatuses();
		if (data.isStation) count = 1;	// don't apply damage once for each module + central hull!
		
		switch (outcome) {
			case DISABLED:
				report(String.format("%40s: disabled", data.member.getVariant().getFullDesignationWithHullName()));
				damage = 1f;
				for (int i = 0; i < count; i++) {
					if (data.member.isFighterWing()) {
						//addDamage(data.member, damage, i);
					} else {
						addDamage(data.member, damage/3f);
						addDamage(data.member, damage/3f);
						addDamage(data.member, damage/3f);
					}
					if (data.isStation) break;
				}
				break;
			case HEAVY_DAMAGE:
				report(String.format("%40s: heavy damage", data.member.getVariant().getFullDesignationWithHullName()));
				damage = 0.7f + (float) Math.random() * 0.1f;
				for (int i = 0; i < count; i++) {
					if (data.member.isFighterWing()) {
						//addDamage(data.member, damage, i);
					} else {
						addDamage(data.member, damage/3f);
						addDamage(data.member, damage/3f);
						addDamage(data.member, damage/3f);
					}
					if (data.isStation) break;
				}
				break;
			case MEDIUM_DAMAGE:
				report(String.format("%40s: medium damage", data.member.getVariant().getFullDesignationWithHullName()));
				damage = 0.45f + (float) Math.random() * 0.1f;
				for (int i = 0; i < count; i++) {
					if (data.member.isFighterWing()) {
						//addDamage(data.member, damage, i);
					} else {
						addDamage(data.member, damage/2f);
						addDamage(data.member, damage/2f);
					}
					if (data.isStation) break;
				}
				break;
			case LIGHT_DAMAGE:
				report(String.format("%40s: light damage", data.member.getVariant().getFullDesignationWithHullName()));
				damage = 0.2f + (float) Math.random() * 0.1f;
				for (int i = 0; i < count; i++) {
					if (data.member.isFighterWing()) {
						//addDamage(data.member, damage, i);
					} else {
						addDamage(data.member, damage);
					}
				}
				break;
			case UNSCATHED:
				report(String.format("%40s: unscathed", data.member.getVariant().getFullDesignationWithHullName()));
				damage = 0f;
				break;
		}
		
		return outcome;
	}
	
	private FleetAutoresolveData computeDataForFleet(CampaignFleetAPI fleet) {
		FleetAutoresolveData fleetData = new FleetAutoresolveData();
		fleetData.fleet = fleet;
		
		fleetData.fightingStrength = 0;
		for (FleetMemberAPI member : fleet.getFleetData().getMembersListCopy()) {
			FleetMemberAutoresolveData data = computeDataForMember(member);
			fleetData.members.add(data);
			boolean okToDeployIfInPlayerPursuit = playerPursuitAutoresolveMode && playerShipsToDeploy != null && fleet == one && (playerShipsToDeploy.contains(member) || member.isAlly());
			if (data.combatReady && (!playerPursuitAutoresolveMode || fleet != one || okToDeployIfInPlayerPursuit)) {
				float mult = 1f;
				if (playerShipsToDeploy != null && playerShipsToDeploy.contains(member)) {
					mult = 8f;
				}
				fleetData.fightingStrength += data.strength * mult;
			}
		}

		return fleetData;
	}
	
	protected float getShieldStr(ShipHullSpecAPI hullSpec, MutableShipStatsAPI stats)
	{
		float normalizedShieldStr = stats.getFluxCapacity().getModifiedValue() + 
									stats.getFluxDissipation().getModifiedValue() * 10f;
		
		
		if (hullSpec.getShieldType() == ShieldType.NONE) {
			normalizedShieldStr = 0;
		} else {
			float shieldFluxPerDamage = hullSpec.getBaseShieldFluxPerDamageAbsorbed(); 
			shieldFluxPerDamage *= stats.getShieldAbsorptionMult().getModifiedValue() * stats.getShieldDamageTakenMult().getModifiedValue();;
			if (shieldFluxPerDamage < 0.1f) shieldFluxPerDamage = 0.1f;
			float shieldMult = 1f / shieldFluxPerDamage;
			normalizedShieldStr *= shieldMult;
		}
		
		return normalizedShieldStr;
	}
	
	protected float getStrength(FleetMemberAPI member, float hullFraction, float mult)
	{
		float strength = member.getMemberStrength();
		strength *= 0.5f + 0.5f * hullFraction;
		strength *= 0.85f + 0.3f * (float) Math.random();
		strength *= mult;
		
		return strength;
	}

	private FleetMemberAutoresolveData computeDataForMember(FleetMemberAPI member) {
		FleetMemberAutoresolveData data = new FleetMemberAutoresolveData();
		
		data.member = member;
		ShipHullSpecAPI hullSpec = data.member.getHullSpec();
		if ((member.isCivilian() && !playerPursuitAutoresolveMode) || !member.canBeDeployedForCombat()) {
			data.strength = 0.25f;
			if (hullSpec.getShieldType() != ShieldType.NONE) {
				data.shieldRatio = 0.5f;
			}
			data.combatReady = false;
			return data;
		}
		
		data.combatReady = true;
		boolean hasVastBulk = data.member.getVariant().hasHullMod("vastbulk");
		data.isStation = hasVastBulk;
		// defence stations always have max CR
		if (hasVastBulk)
		{
			if (DefenceStationManager.getManager() != null 
					&& DefenceStationManager.getManager().isRegisteredDefenceStation(member))
				clampStationCR(member);
		}
		
		MutableShipStatsAPI stats = data.member.getStats();
		
		PersonAPI captain = null;
		float captainMult = 0.5f;
		if (member.getCaptain() != null) {
			//captainMult = (10f + member.getCaptain().getStats().getAptitudeLevel("combat")) / 20f;
			float captainLevel = member.getCaptain().getStats().getLevel();
			captainMult += captainLevel / 20f;
			captain = member.getCaptain();
		}		
		
		float strength = getStrength(member, member.getStatus().getHullFraction(), hasVastBulk ? STATION_STRENGTH_MULT : 1);
		//report("Member " + member.getShipName() + " has strength " + getStrength(member));
				
		float normalizedHullStr = 0;
		if (!hasVastBulk)
		{
			normalizedHullStr += stats.getHullBonus().computeEffective(hullSpec.getHitpoints()) + 
									  stats.getArmorBonus().computeEffective(hullSpec.getArmorRating()) * 10f;
		}
		
		float normalizedShieldStr = getShieldStr(hullSpec, stats);
		
		// process modules
		for (int i=0; i < member.getVariant().getModuleSlots().size(); i++)
		{
			if (member.getStatus().isDetached(i))	// module dead
				continue;
				
			String moduleSlot = member.getVariant().getModuleSlots().get(i);
			
			ShipVariantAPI moduleVariant = member.getModuleVariant(moduleSlot);
			FleetMemberAPI tempMember = Global.getFactory().createFleetMember(FleetMemberType.SHIP, moduleVariant);
			if (captain != null)
				tempMember.setCaptain(captain);
			ShipHullSpecAPI moduleSpec = tempMember.getHullSpec();
			MutableShipStatsAPI moduleStats = data.member.getStats();
			
			normalizedShieldStr += getShieldStr(moduleSpec, moduleStats);
			if (!tempMember.getVariant().hasHullMod("vastbulk"))
			{
				normalizedHullStr += moduleStats.getHullBonus().computeEffective(moduleSpec.getHitpoints()) + 
										  moduleStats.getArmorBonus().computeEffective(moduleSpec.getArmorRating()) * 10f;
			}
			strength += getStrength(tempMember, getModuleHullFraction(member, i), MODULE_STRENGTH_MULT);
			//report("\tModule " + member.getVariant().getDisplayName() + " has strength " + getStrength(member));
			
			if (captain != null)
				tempMember.setCaptain(null);
		}
		
		if (normalizedHullStr < 1) normalizedHullStr = 1;
		if (normalizedShieldStr < 1) normalizedShieldStr = 1;
		//report("Member " + member.getShipName() + " total shield str: " + normalizedShieldStr + "; hull str: " + 
		//		normalizedHullStr);
		data.shieldRatio = normalizedShieldStr / (normalizedShieldStr + normalizedHullStr);
		
		strength *= captainMult;
		
		data.strength = Math.max(strength, 0.25f);
		//report("Member " + member.getShipName() + " has strength " + strength);
		
		member.setCaptain(captain);
		
		return data;
	}
	
	private static void report(String str) {
		if (report) {
			log.info(str);
		}
	}
	
	private static boolean report = false;
	private EngagementResultAPI result;
	private FleetEncounterContext context;
	
	public void setReport(boolean report) {
		NexBattleAutoresolvePlugin.report = report;
	}

	public EngagementResultAPI getResult() {
		return result;
	}

	public FleetEncounterContextPlugin getContext() {
		return context;
	}
	
}










