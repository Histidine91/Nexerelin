package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide.OfficerEngagementData;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.NexUtilsMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NexFleetEncounterContext extends FleetEncounterContext {

	public static final float FRIGATE_XP_BONUS = 1.5f;
	public static final float DESTROYER_XP_BONUS = 1.25f;
	
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
		if (!ExerelinConfig.officerDeaths) {
			return;
		}
		if (!playerInvolved) {
			return;
		}

		DataForEncounterSide data = getDataFor(result.getFleet());

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

			OfficerDataAPI officer = battle.getSourceFleet(member).getFleetData().getOfficerData(member.getCaptain());
			if (officer == null) {
				continue;
			}

			float escapeChance;
			float recoverableChance;
			float crewLossMult = member.getStats().getCrewLossMult().getModifiedValue();
			if (result.getDestroyed().contains(member)) {
				escapeChance = NexUtilsMath.lerp(0.5f, 1f, 1f - crewLossMult);
				recoverableChance = 0f;
			} else {
				escapeChance = NexUtilsMath.lerp(0.5f, 1f, 1f - crewLossMult);
				recoverableChance = 0.75f;
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
		if (max < 1) {
			max = 1;
		}
		float num = data.getOfficerData().size();
		if (num < 1) {
			num = 1;
		}
		for (PersonAPI person : data.getOfficerData().keySet()) {
			OfficerEngagementData oed = data.getOfficerData().get(person);
			if (oed.sourceFleet == null || !oed.sourceFleet.isPlayerFleet()) {
				continue;
			}

			OfficerDataAPI od = oed.sourceFleet.getFleetData().getOfficerData(person);
			if (od == null) {
				continue; // shouldn't happen, as this is checked earlier before it goes into the map
			}
			float f = oed.timeDeployed / max;
			if (f < 0) {
				f = 0;
			}
			if (f > 1) {
				f = 1;
			}

			float bonus = 1f;
			if (data.getFleet().getCommanderStats() != null) {
				bonus *= data.getFleet().getCommanderStats().getDynamic().getValue("officerXPMult");
			}
			if (ExerelinConfig.officerDaredevilBonus) {
				FleetMemberAPI member = oed.sourceFleet.getFleetData().getMemberWithCaptain(person);
				if (member != null && member.isFrigate()) {
					bonus = FRIGATE_XP_BONUS;
				} else if (member != null && member.isDestroyer()) {
					bonus = DESTROYER_XP_BONUS;
				}
			}

			od.addXP((long) (bonus * f * xp / num), textPanelForXPGain);
		}
	}
}
