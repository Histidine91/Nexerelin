package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide.OfficerEngagementData;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.util.Misc;
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

		List<FleetMemberAPI> result = super.getRecoverableShips(battle, winningFleet, otherFleet);


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
}
