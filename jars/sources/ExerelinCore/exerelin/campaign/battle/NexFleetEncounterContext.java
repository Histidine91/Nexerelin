package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide.OfficerEngagementData;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.SWPModPlugin;
import data.scripts.util.SWP_Util;
import exerelin.campaign.StatsTracker;
import exerelin.plugins.ExerelinModPlugin;
import exerelin.utilities.NexConfig;
import exerelin.utilities.NexUtilsMath;

import java.util.*;

public class NexFleetEncounterContext extends FleetEncounterContext {

	public static final float FRIGATE_XP_BONUS = 1.5f;
	public static final float DESTROYER_XP_BONUS = 1.25f;
	
	//==========================================================================
	// START IBB HANDLING
	
	@Override
	public List<FleetMemberAPI> getRecoverableShips(BattleAPI battle, CampaignFleetAPI winningFleet, CampaignFleetAPI otherFleet) {
		// not part of the IBB handling, just some hax to make S-mods kept
		if (Global.getSettings().getBoolean("nex_keepSModsForRecoveredShips")) {
			DataForEncounterSide winnerData = getDataFor(winningFleet);
			List<FleetMemberData> enemyCasualties = winnerData.getEnemyCasualties();
			for (FleetMemberData casualty : enemyCasualties) {
				FleetMemberAPI otherMember = casualty.getMember();
				
				CampaignFleetAPI fleet = battle.getSourceFleet(otherMember);
				if (fleet != null && fleet.getMemoryWithoutUpdate().getBoolean("$nex_noKeepSMods")) 
				{
					continue;
				}
				
				ShipVariantAPI variant = otherMember.getVariant();
				//Global.getLogger(this.getClass()).info("Checking variant for " + otherMember.getShipName() + ": " + variant.getSource());
				if (variant.getSource() == VariantSource.REFIT) {
					variant.addTag(Tags.VARIANT_ALWAYS_RETAIN_SMODS_ON_SALVAGE);
				}
			}
		}


		//TODO change this back to super.getRecoverableShips() override past 0.97a, also remove the generatePlayerLoot() override and the methods below it - Lukas04
		//List<FleetMemberAPI> result = super.getRecoverableShips(battle, winningFleet, otherFleet);
		List<FleetMemberAPI> result = getVanillaRecoverableShips(battle, winningFleet, otherFleet);


		if (!ExerelinModPlugin.HAVE_SWP) {
			return result;
		}
		
		if (!SWPModPlugin.Setting_IBBAlwaysRecover) {
			return result;
		}
		
		if (Misc.isPlayerOrCombinedContainingPlayer(otherFleet)) {
			return result;
		}
		
		Set<String> recoveredTypes = new HashSet<>();
		for (FleetMemberAPI member : result) {
			recoveredTypes.add(SWP_Util.getNonDHullId(member.getHullSpec()));
		}
		
		DataForEncounterSide winnerData = getDataFor(winningFleet);
		
		float playerContribMult = computePlayerContribFraction();
		List<FleetMemberData> enemyCasualties = winnerData.getEnemyCasualties();
			
		for (FleetMemberData data : enemyCasualties) {
			if (Misc.isUnboardable(data.getMember())) {
				continue;
			}
			if ((data.getStatus() != Status.DISABLED) && (data.getStatus() != Status.DESTROYED)) {
				continue;
			}
			
			/* IBBs only */
			if (!SWP_Util.SPECIAL_SHIPS.contains(SWP_Util.getNonDHullId(data.getMember().getHullSpec()))) {
				continue;
			}
			
			/* Don't double-add */
			if (result.contains(data.getMember())) {
				continue;
			}
			if (getStoryRecoverableShips().contains(data.getMember())) {
				continue;
			}
			
			/* Only one of each type */
			if (recoveredTypes.contains(SWP_Util.getNonDHullId(data.getMember().getHullSpec()))) {
				continue;
			}
			
			if (playerContribMult > 0f) {
				data.getMember().setCaptain(Global.getFactory().createPerson());
				
				ShipVariantAPI variant = data.getMember().getVariant();
				variant = variant.clone();
				variant.setSource(VariantSource.REFIT);
				variant.setOriginalVariant(null);
				data.getMember().setVariant(variant, false, true);
				
				Random dModRandom = new Random(1000000 * data.getMember().getId().hashCode() + Global.getSector().getPlayerBattleSeed());
				dModRandom = Misc.getRandom(dModRandom.nextLong(), 5);
				DModManager.addDMods(data, false, Global.getSector().getPlayerFleet(), dModRandom);
				if (DModManager.getNumDMods(variant) > 0) {
					DModManager.setDHull(variant);
				}
				
				float weaponProb = Global.getSettings().getFloat("salvageWeaponProb");
				float wingProb = Global.getSettings().getFloat("salvageWingProb");
				
				/* Always recover the unique wasps */
				if (SWP_Util.getNonDHullId(data.getMember().getHullSpec()).contentEquals("uw_boss_astral")) {
					wingProb = 1f;
				}
				
				prepareShipForRecovery(data.getMember(), false, true, true, weaponProb, wingProb, getSalvageRandom());
				
				getStoryRecoverableShips().add(data.getMember());
				recoveredTypes.add(SWP_Util.getNonDHullId(data.getMember().getHullSpec()));
			}
		}
		
		return result;
	}
	
	//==========================================================================
	// END IBB HANDLING
	
	//==========================================================================
	// START OFFICER DEATH HANDLING
	
	protected List<OfficerDataAPI> playerLostOfficers = new ArrayList<>(10);
	protected List<EscapedOfficerData> playerOfficersEscaped = new ArrayList<>(10);
	protected List<OfficerDataAPI> playerOfficersKIA = new ArrayList<>(10);
	protected List<OfficerDataAPI> playerOfficersMIA = new ArrayList<>(10);
	protected List<OfficerDataAPI> playerRecoverableOfficerLosses = new ArrayList<>(10);
	protected List<OfficerDataAPI> playerUnconfirmedOfficers = new ArrayList<>(10);

	public List<OfficerDataAPI> getPlayerLostOfficers() {
		return Collections.unmodifiableList(playerLostOfficers);
	}

	public List<EscapedOfficerData> getPlayerOfficersEscaped() {
		return Collections.unmodifiableList(playerOfficersEscaped);
	}

	public List<OfficerDataAPI> getPlayerOfficersKIA() {
		return Collections.unmodifiableList(playerOfficersKIA);
	}

	public List<OfficerDataAPI> getPlayerOfficersMIA() {
		return Collections.unmodifiableList(playerOfficersMIA);
	}

	public List<OfficerDataAPI> getPlayerRecoverableOfficers() {
		return Collections.unmodifiableList(playerRecoverableOfficerLosses);
	}

	public List<OfficerDataAPI> getPlayerUnconfirmedOfficers() {
		return Collections.unmodifiableList(playerUnconfirmedOfficers);
	}

	public void recoverPlayerOfficers() {
		for (OfficerDataAPI officer : playerRecoverableOfficerLosses) {
			Global.getSector().getPlayerFleet().getFleetData().addOfficer(officer.getPerson());
			StatsTracker.getStatsTracker().removeDeadOfficer(officer);
			officer.addXP(0);
			if (officer.canLevelUp()) {
				officer.getSkillPicks();
			}
			playerUnconfirmedOfficers.remove(officer);
		}
		playerRecoverableOfficerLosses.clear();
	}

	protected void applyOfficerLosses(EngagementResultAPI result) {
		EngagementResultForFleetAPI winner = result.getWinnerResult();
		EngagementResultForFleetAPI loser = result.getLoserResult();

		/* For display only; no need to retain */
		playerOfficersEscaped.clear();
		playerOfficersKIA.clear();
		playerOfficersMIA.clear();

		boolean playerInvolved = battle.isPlayerInvolved();
		calculateAndApplyOfficerLosses(winner, playerInvolved);
		calculateAndApplyOfficerLosses(loser, playerInvolved);
	}

	@Override
	protected void applyResultToFleets(EngagementResultAPI result) {
		super.applyResultToFleets(result);
		applyOfficerLosses(result);
	}

	protected void calculateAndApplyOfficerLosses(EngagementResultForFleetAPI result, boolean playerInvolved) {
		if (!NexConfig.officerDeaths) {
			return;
		}
		if (!playerInvolved) {
			return;
		}

		List<FleetMemberAPI> all = new ArrayList<>(result.getDisabled().size() + result.getDestroyed().size());
		all.addAll(result.getDisabled());
		all.addAll(result.getDestroyed());

		CampaignFleetAPI playerFleet = null;

		for (FleetMemberAPI member : all) {
			if (battle.getSourceFleet(member) == null) {
				continue;
			}
			if (member.isFighterWing()) {
				continue;
			}
			if (member.getCaptain().isDefault()) {
				continue;
			}
			if (member.getCaptain().getMemoryWithoutUpdate().getBoolean("$nex_noOfficerDeath")) 
			{
				continue;
			}
			if (Misc.isUnremovable(member.getCaptain())) {
				continue;
			}

			OfficerDataAPI officer = battle.getSourceFleet(member).getFleetData().getOfficerData(member.getCaptain());
			if (officer == null) {
				continue;
			}

			float escapeChance;
			float recoverableChance;
			float crewLossMult = member.getStats().getCrewLossMult().getModifiedValue();
			float baseEscapeChance = Global.getSettings().getFloat("nex_officerBaseEscapeChance");
			float baseSurviveChance = Global.getSettings().getFloat("nex_officerSurviveChance");
			if (result.getDestroyed().contains(member)) {
				escapeChance = NexUtilsMath.lerp(baseEscapeChance, 1f, 1f - crewLossMult);
				recoverableChance = NexUtilsMath.lerp(0, baseSurviveChance, 1f - crewLossMult);
			} else {
				escapeChance = NexUtilsMath.lerp(baseEscapeChance, 1f, 1f - crewLossMult);
				recoverableChance = NexUtilsMath.lerp(baseSurviveChance, 1f, 1f - crewLossMult);
			}

			boolean isPlayer;
			if (battle.getSourceFleet(member).isPlayerFleet()) {
				playerFleet = battle.getSourceFleet(member);
				isPlayer = true;
			} else {
				isPlayer = false;
			}

			if ((float) Math.random() < escapeChance) {
				// Escaped!
				if (isPlayer) {
					playerOfficersEscaped.add(new EscapedOfficerData(officer, member));
				}
			} else if (recoverableChance == 0) {
				// KIA
				member.setCaptain(null);
				battle.getSourceFleet(member).getFleetData().removeOfficer(officer.getPerson());
				if (isPlayer) {
					playerOfficersKIA.add(officer);
					playerLostOfficers.add(officer);
					StatsTracker.getStatsTracker().addDeadOfficer(officer, member);
				}
			} else {
				// MIA
				member.setCaptain(null);
				battle.getSourceFleet(member).getFleetData().removeOfficer(officer.getPerson());
				if (isPlayer) {
					playerOfficersMIA.add(officer);
					playerUnconfirmedOfficers.add(officer);
				}
				if ((float) Math.random() < recoverableChance) {
					if (isPlayer) {
						playerRecoverableOfficerLosses.add(officer);
						StatsTracker.getStatsTracker().addDeadOfficer(officer, member);
					}
				} else {
					if (isPlayer) {
						playerLostOfficers.add(officer);
						StatsTracker.getStatsTracker().addDeadOfficer(officer, member);
					}
				}
			}
		}

		// If everybody blew up...
		if (playerFleet != null && !playerFleet.isValidPlayerFleet()) {
			for (EscapedOfficerData escaped : playerOfficersEscaped) {
				OfficerDataAPI officer = escaped.officer;
				playerFleet.getFleetData().removeOfficer(officer.getPerson());
				playerOfficersMIA.add(officer);
				playerLostOfficers.add(officer);
				playerUnconfirmedOfficers.add(officer);
			}
			playerOfficersEscaped.clear();
		}
	}
	
	// Needed because at the point where we remove escaped officers from the fleet, we've forgotten the fleet member
	public class EscapedOfficerData
	{
		public OfficerDataAPI officer;
		public FleetMemberAPI member;
		
		public EscapedOfficerData(OfficerDataAPI officer, FleetMemberAPI member)
		{
			this.officer = officer;
			this.member = member;
		}
	}

	//==========================================================================
	// END OFFICER DEATH HANDLING
	
	// officer XP bonus
	@Override
	protected void gainOfficerXP(DataForEncounterSide data, float xp) {
		float max = data.getMaxTimeDeployed();
		if (max < 1) max = 1;
		float num = data.getOfficerData().size();
		if (num < 1) num = 1;
		for (PersonAPI person : data.getOfficerData().keySet()) {
			OfficerEngagementData oed = data.getOfficerData().get(person);
			if (oed.sourceFleet == null || !oed.sourceFleet.isPlayerFleet()) continue;
			
			OfficerDataAPI od = oed.sourceFleet.getFleetData().getOfficerData(person);
			if (od == null) continue; // shouldn't happen, as this is checked earlier before it goes into the map
			
			float f = oed.timeDeployed / max;
			if (f < 0) f = 0;
			if (f > 1) f = 1;
			
			// MODIFIED
			float bonusMult = 1;
			if (NexConfig.officerDaredevilBonus) {
				FleetMemberAPI member = oed.sourceFleet.getFleetData().getMemberWithCaptain(person);
				if (member != null && member.isFrigate()) {
					bonusMult = FRIGATE_XP_BONUS;
				} else if (member != null && member.isDestroyer()) {
					bonusMult = DESTROYER_XP_BONUS;
				}
			}
			xp *= bonusMult;
			
			od.addXP((long)(f * xp / num), textPanelForXPGain);
		}
	}












	//Temporary Fix for AI Cores barely dropping in version 0.97a below, should be removed in later versions, as alex is fixing it himself.
	//Issue is related to the getRecoverableShips method clearing the ai officer of recoverable ships, and lootWeapons check for the AI core now failing.
	//Only an issue if the player can recover automated ships.


	public HashMap<FleetMemberAPI, String> aiCoreshipsToLoot = new HashMap<>();

	@Override
	protected void generatePlayerLoot(List<FleetMemberAPI> recoveredShips, boolean withCredits) {
		super.generatePlayerLoot(recoveredShips, withCredits);

		Random random = new Random();
		for (Map.Entry<FleetMemberAPI, String> entry : aiCoreshipsToLoot.entrySet()) {
			if (entry.getKey().getVariant().hasTag(Tags.VARIANT_DO_NOT_DROP_AI_CORE_FROM_CAPTAIN)) continue;
			generateAICoreDropsForRecoverableOpponents(entry.getKey(), entry.getValue(), random);
		}
		aiCoreshipsToLoot.clear();
	}

	public void generateAICoreDropsForRecoverableOpponents(FleetMemberAPI member, String cid, Random random) {
		if (cid != null) {
			CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(cid);
			if (!spec.hasTag(Tags.NO_DROP)) {
				float prob = Global.getSettings().getFloat("drop_prob_officer_" + cid);
				if (member.isStation()) {
					prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_station");
				} else if (member.isFrigate()) {
					prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_frigate");
				} else if (member.isDestroyer()) {
					prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_destroyer");
				} else if (member.isCruiser()) {
					prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_cruiser");
				} else if (member.isCapital()) {
					prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_capital");
				}
				if (prob > 0 && random.nextFloat() < prob) {
					loot.addItems(CargoAPI.CargoItemType.RESOURCES, cid, 1);
				}
			}
		}
	}

	public List<FleetMemberAPI> getVanillaRecoverableShips(BattleAPI battle, CampaignFleetAPI winningFleet, CampaignFleetAPI otherFleet) {

		storyRecoverableShips.clear();

		List<FleetMemberAPI> result = new ArrayList<FleetMemberAPI>();
//		int max = Global.getSettings().getMaxShipsInFleet() -
//				  Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().size();
//		if (Misc.isPlayerOrCombinedContainingPlayer(winningFleet) && max <= 0) {
//			return result;
//		}

		if (Misc.isPlayerOrCombinedContainingPlayer(otherFleet)) {
			return result;
		}

		DataForEncounterSide winnerData = getDataFor(winningFleet);
		DataForEncounterSide loserData = getDataFor(otherFleet);

		float playerContribMult = computePlayerContribFraction();
		List<FleetMemberData> enemyCasualties = winnerData.getEnemyCasualties();
		List<FleetMemberData> ownCasualties = winnerData.getOwnCasualties();
		List<FleetMemberData> all = new ArrayList<FleetMemberData>();
		all.addAll(ownCasualties);
		Collections.sort(all, new Comparator<FleetMemberData>() {
			public int compare(FleetMemberData o1, FleetMemberData o2) {
				int result = o2.getMember().getVariant().getSMods().size() - o1.getMember().getVariant().getSMods().size();
				if (result == 0) {
					result = o2.getMember().getHullSpec().getHullSize().ordinal() - o1.getMember().getHullSpec().getHullSize().ordinal();
				}
				return result;
			}
		});


		//Random random = Misc.getRandom(battle.getSeed(), 11);
		Random random = Misc.getRandom(Global.getSector().getPlayerBattleSeed(), 11);
		//System.out.println("BATTLE SEED: " + Global.getSector().getPlayerBattleSeed());

		// since the number of recoverable ships is limited, prefer "better" ships
		WeightedRandomPicker<FleetMemberData> enemyPicker = new WeightedRandomPicker<FleetMemberData>(random);

		// doesn't matter how it's sorted, as long as it's consistent so that
		// the order it's insertied into the picker in is the same
		List<FleetMemberData> enemy = new ArrayList<FleetMemberData>(enemyCasualties);
		Collections.sort(enemy, new Comparator<FleetMemberData>() {
			public int compare(FleetMemberData o1, FleetMemberData o2) {
				int result = o2.getMember().getId().hashCode() - o1.getMember().getId().hashCode();
				return result;
			}
		});

		for (FleetMemberData curr : enemy) {
			float base = 10f;
			switch (curr.getMember().getHullSpec().getHullSize()) {
				case CAPITAL_SHIP: base = 40f; break;
				case CRUISER: base = 20f; break;
				case DESTROYER: base = 10f; break;
				case FRIGATE: base = 5f; break;
			}
			float w = curr.getMember().getUnmodifiedDeploymentPointsCost() / base;

			enemyPicker.add(curr, w);
		}
		List<FleetMemberData> sortedEnemy = new ArrayList<FleetMemberData>();
		while (!enemyPicker.isEmpty()) {
			sortedEnemy.add(enemyPicker.pickAndRemove());
		}


		all.addAll(sortedEnemy);

//		for (FleetMemberData curr : all) {
//			System.out.println(curr.getMember().getHullId());
//		}

		CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

		int maxRecoverablePerType = 24;

		float probLessDModsOnNext = Global.getSettings().getFloat("baseProbLessDModsOnRecoverableEnemyShip");
		float lessDmodsOnNextMult = Global.getSettings().getFloat("lessDModsOnRecoverableEnemyShipMultNext");

		int count = 0;
		for (FleetMemberData data : all) {
//			if (data.getMember().getHullId().contains("legion")) {
//				System.out.println("wefwefwefe");
//			}
			//if (data.getMember().getHullSpec().getHints().contains(ShipTypeHints.UNBOARDABLE)) continue;
			if (Misc.isUnboardable(data.getMember())) continue;
			if (data.getStatus() != Status.DISABLED && data.getStatus() != Status.DESTROYED) continue;

			boolean own = ownCasualties.contains(data);
			if (own && data.getMember().isAlly()) continue;

//			if (data.getMember().getHullId().startsWith("vanguard_pirates")) {
//				System.out.println("wefwefwefe12341234");
//			}

			float mult = 1f;
			if (data.getStatus() == Status.DESTROYED) mult = 0.5f;
			if (!own) mult *= playerContribMult;


			boolean useOfficerRecovery = false;
			if (own) {
				useOfficerRecovery = winnerData.getMembersWithOfficerOrPlayerAsOrigCaptain().contains(data.getMember());
				if (useOfficerRecovery) {
					mult = 1f;
				}
			}

			boolean noRecovery = false;
			if (battle != null &&
					battle.getSourceFleet(data.getMember()) != null) {
				CampaignFleetAPI fleet = battle.getSourceFleet(data.getMember());
				if (fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_NO_SHIP_RECOVERY)) {
					noRecovery = true;
				}
			}

//			if (data.getMember().getHullId().startsWith("cerberus")) {
//				System.out.println("wefwefew");
//			}
			boolean normalRecovery = !noRecovery &&
					Misc.isShipRecoverable(data.getMember(), playerFleet, own, useOfficerRecovery, 1f * mult);
			boolean storyRecovery = !noRecovery && !normalRecovery;

			boolean alwaysRec = data.getMember().getVariant().hasTag(Tags.VARIANT_ALWAYS_RECOVERABLE);

			float shipRecProb = data.getMember().getStats().getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).computeEffective(0f);
			if (!own && !alwaysRec && (storyRecovery || normalRecovery) && shipRecProb < 1f) {
				float per = Global.getSettings().getFloat("probNonOwnNonRecoverablePerDMod");
				float perAlready = Global.getSettings().getFloat("probNonOwnNonRecoverablePerAlreadyRecoverable");
				float max = Global.getSettings().getFloat("probNonOwnNonRecoverableMax");
				int dmods = DModManager.getNumDMods(data.getMember().getVariant());

				float assumedAddedDmods = 3f;
				assumedAddedDmods -= Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.SHIP_DMOD_REDUCTION, 0) * 0.5f;
				assumedAddedDmods = Math.min(assumedAddedDmods, 5 - dmods);

				float recoveredSoFar = 0f;
				if (storyRecovery) recoveredSoFar = storyRecoverableShips.size();
				else recoveredSoFar = result.size();

				if (random.nextFloat() < Math.min(max, (dmods + assumedAddedDmods) * per) + recoveredSoFar * perAlready) {
					noRecovery = true;
				}
			}


			//if (true || Misc.isShipRecoverable(data.getMember(), playerFleet, own, useOfficerRecovery, battle.getSeed(), 1f * mult)) {
			if (!noRecovery && (normalRecovery || storyRecovery)) {
				//if (Misc.isShipRecoverable(data.getMember(), playerFleet, battle.getSeed(), 1f * mult)) {

				if (!own || !Misc.isUnremovable(data.getMember().getCaptain())) {
					String aiCoreId = null;
					if (/*own && */data.getMember().getCaptain() != null &&
							data.getMember().getCaptain().isAICore()) {
						aiCoreId = data.getMember().getCaptain().getAICoreId();
					}

					// if it's an AI core on a player ship, then:
					// 1. It's integrated/unremovable, so, don't remove (we don't even end up here)
					// 2. Ship will be recovered and will still have it, or
					// 3. Ship will not be recovered, and it will get added to loot in lootWeapons()
					boolean keepCaptain = false;
					// don't do this - want to only show the AI core in recovery dialog when
					// it's integrated and would be lost if not recovered
//					if (own && (data.getMember().getCaptain() == null ||
//							data.getMember().getCaptain().isAICore())) {
//						keepCaptain = true;
//					}
					if (!keepCaptain) {
						data.getMember().setCaptain(Global.getFactory().createPerson());

						if (aiCoreId != null) {
							data.getMember().getCaptain().getMemoryWithoutUpdate().set(
									"$aiCoreIdForRecovery", aiCoreId);
							if (!own) {
								aiCoreshipsToLoot.put(data.getMember(), aiCoreId);
							}
						}
					}
				}

				ShipVariantAPI variant = data.getMember().getVariant();
				variant = variant.clone();
				variant.setSource(VariantSource.REFIT);
				variant.setOriginalVariant(null);
				//DModManager.setDHull(variant);
				data.getMember().setVariant(variant, false, true);

				boolean lessDmods = false;
				if (!own && data.getStatus() != Status.DESTROYED && random.nextFloat() < probLessDModsOnNext) {
					lessDmods = true;
					probLessDModsOnNext *= lessDmodsOnNextMult;
				}

				//Random dModRandom = new Random(1000000 * (data.getMember().getId().hashCode() + Global.getSector().getClock().getDay()));
				Random dModRandom = new Random(1000000 * data.getMember().getId().hashCode() + Global.getSector().getPlayerBattleSeed());
				dModRandom = Misc.getRandom(dModRandom.nextLong(), 5);
				if (lessDmods) {
					DModManager.reduceNextDmodsBy = 3;
				}

				float probAvoidDmods =
						data.getMember().getStats().getDynamic().getMod(
								Stats.DMOD_AVOID_PROB_MOD).computeEffective(0f);

				float probAcquireDmods =
						data.getMember().getStats().getDynamic().getMod(
								Stats.DMOD_ACQUIRE_PROB_MOD).computeEffective(1f);

				if (dModRandom.nextFloat() >= probAvoidDmods && dModRandom.nextFloat() < probAcquireDmods) {
					DModManager.addDMods(data, own, Global.getSector().getPlayerFleet(), dModRandom);
					if (DModManager.getNumDMods(variant) > 0) {
						DModManager.setDHull(variant);
					}
				}

				float weaponProb = Global.getSettings().getFloat("salvageWeaponProb");
				float wingProb = Global.getSettings().getFloat("salvageWingProb");
				if (own) {
					weaponProb = Global.getSettings().getFloat("salvageOwnWeaponProb");
					wingProb = Global.getSettings().getFloat("salvageOwnWingProb");
					weaponProb = playerFleet.getStats().getDynamic().getValue(Stats.OWN_WEAPON_RECOVERY_MOD, weaponProb);
					wingProb = playerFleet.getStats().getDynamic().getValue(Stats.OWN_WING_RECOVERY_MOD, wingProb);
				}

				boolean retain = data.getMember().getHullSpec().hasTag(Tags.TAG_RETAIN_SMODS_ON_RECOVERY) ||
						data.getMember().getVariant().hasTag(Tags.TAG_RETAIN_SMODS_ON_RECOVERY);
				prepareShipForRecovery(data.getMember(), own, true, !own && !retain, weaponProb, wingProb, getSalvageRandom());

				if (normalRecovery) {
					if (result.size() < maxRecoverablePerType) {
						result.add(data.getMember());
					}
				} else if (storyRecovery) {
					if (storyRecoverableShips.size() < maxRecoverablePerType) {
						storyRecoverableShips.add(data.getMember());
					}
				}

//				count++;
//				if (count >= max) break;
			}


//			else {
//				data.getMember().getVariant().removeTag(Tags.SHIP_RECOVERABLE);
//			}
		}

		//System.out.println("Recoverable: " + result.size() + ", story: " + storyRecoverableShips.size());


		recoverableShips.clear();
		recoverableShips.addAll(result);
		return result;
	}


























	
	//==========================================================================
	// START AI CORE DROP DEBUG
	
	/*
	protected void lootWeapons(FleetMemberAPI member, ShipVariantAPI variant, boolean own, float mult, boolean lootingModule) {
		if (variant == null) return;
		if (member.isFighterWing()) return;
		
//		if (own) {
//			System.out.println("238034wefwef");
//		}
		//isUnremovable(
		if (own && !lootingModule && member.getCaptain() != null &&
				member.getCaptain().getMemoryWithoutUpdate().contains("$aiCoreIdForRecovery") &&
				//member.getCaptain().isAICore() && 
				!Misc.isUnremovable(member.getCaptain())) {
			//loot.addItems(CargoItemType.RESOURCES, member.getCaptain().getAICoreId(), 1);
			loot.addItems(CargoAPI.CargoItemType.RESOURCES,
						  member.getCaptain().getMemoryWithoutUpdate().getString("$aiCoreIdForRecovery"), 1);
		}
		
		Random random = Misc.random;
		if (getSalvageRandom() != null) random = getSalvageRandom();
		
		if (!own && !lootingModule && 
				member.getCaptain().isAICore() && !variant.hasTag(Tags.VARIANT_DO_NOT_DROP_AI_CORE_FROM_CAPTAIN)) {
			String cid = member.getCaptain().getAICoreId();
			if (cid != null) {
				Global.getLogger(this.getClass()).info(String.format("Checking AI core drop %s from hull %s",
						cid, member.getHullSpec().getHullName()));
				CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(cid);
				if (!spec.hasTag(Tags.NO_DROP)) {
					float prob = Global.getSettings().getFloat("drop_prob_officer_" + cid);
					Global.getLogger(this.getClass()).info(String.format("  Base chance: %.2f", prob));
					if (member.isStation()) {
						prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_station");
					} else if (member.isFrigate()) {
						prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_frigate");
					} else if (member.isDestroyer()) {
						prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_destroyer");
					} else if (member.isCruiser()) {
						prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_cruiser");
					} else if (member.isCapital()) {
						prob *= Global.getSettings().getFloat("drop_prob_mult_ai_core_capital");
					}
					Global.getLogger(this.getClass()).info(String.format("  Chance after size multiplier: %.2f", prob));
					float roll = random.nextFloat();
					if (prob > 0 && roll < prob) {
						Global.getLogger(this.getClass()).info(String.format(":D  Roll successful: %.2f < %.2f", roll, prob));
						loot.addItems(CargoAPI.CargoItemType.RESOURCES, cid, 1);
					}
					else {
						Global.getLogger(this.getClass()).info(String.format(":(  Roll failed: %.2f < %.2f", roll, prob));
					}
				}
			}
			
		}
		
		float p = Global.getSettings().getFloat("salvageWeaponProb");
		if (own) {
			p = Global.getSettings().getFloat("salvageOwnWeaponProb");
			p = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.OWN_WEAPON_RECOVERY_MOD, p);
		} else {
			p = Global.getSector().getPlayerFleet().getStats().getDynamic().getValue(Stats.ENEMY_WEAPON_RECOVERY_MOD, p);
		}
		boolean alreadyStripped = true;	//recoverableShips.contains(member);
		

		Set<String> remove = new HashSet<String>();
		
		if (variant.hasTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS)) {
			for (String slotId : variant.getNonBuiltInWeaponSlots()) {
				String weaponId = variant.getWeaponId(slotId);
				if (weaponId == null) continue;
				if (loot.getNumWeapons(weaponId) <= 0) {
					WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
					if (spec.hasTag(Tags.NO_DROP)) continue;
					
					loot.addWeapons(weaponId, 1);
					remove.add(slotId);
				}
			}
		}
		
		for (String slotId : variant.getNonBuiltInWeaponSlots()) {
			if (remove.contains(slotId)) continue;
			//if ((float) Math.random() * mult > 0.75f) {
			if (!alreadyStripped) {
				if (random.nextFloat() > mult) continue;
				if (random.nextFloat() > p) continue;
			}
			
			String weaponId = variant.getWeaponId(slotId);
			WeaponSpecAPI spec = Global.getSettings().getWeaponSpec(weaponId);
			if (spec.hasTag(Tags.NO_DROP)) continue;
			
			loot.addItems(CargoAPI.CargoItemType.WEAPONS, weaponId, 1);
			remove.add(slotId);
		}
		
		
		for (String slotId : variant.getModuleSlots()) {
			WeaponSlotAPI slot = variant.getSlot(slotId);
			if (slot.isStationModule()) {
				ShipVariantAPI module = variant.getModuleVariant(slotId);
				if (module == null) continue;
				lootWeapons(member, module, own, mult, true);
			}
		}
	}
	*/
}
