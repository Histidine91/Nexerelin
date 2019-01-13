package exerelin.campaign.battle;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.EngagementResultForFleetAPI;
import com.fs.starfarer.api.campaign.FleetEncounterContextPlugin.DataForEncounterSide.OfficerEngagementData;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.StatsTracker;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.NexUtilsMath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
			
			float bonusMult = 1;
			if (ExerelinConfig.officerDaredevilBonus) {
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
	
	// low rep impact battles have no rep impact
	/*
	@Override
	public boolean adjustPlayerReputation(InteractionDialogAPI dialog, String ffText, boolean okToAdjustAlly, boolean okToAdjustEnemy) {
		if (alreadyAdjustedRep) return false;
		
		if (battle != null && battle.isPlayerInvolved() && engagedInHostilities) {
			alreadyAdjustedRep = true;
			
			boolean printedAdjustmentText = false;
			
			boolean playerWon = didPlayerWinEncounter();
			List<CampaignFleetAPI> playerSide = battle.getPlayerSide();
			List<CampaignFleetAPI> enemySide = battle.getNonPlayerSide();
			
			CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
			pf.setMoveDestination(pf.getLocation().x, pf.getLocation().y);
			
			// cases to cover: 1) player destroyed ships 2) player harried/harassed 3) player pursued
			// i.e. anything other than a non-destructive retreat, w/o an engagement
			boolean playerWasAggressive = playerDidSeriousDamage || !playerOnlyRetreated;
			CoreReputationPlugin.RepActions action = null;
//			if (engagedInHostilities) {
//				action = RepActions.COMBAT_NO_DAMAGE_ESCAPE;
//			}
			boolean knowsWhoPlayerIs = battle.knowsWhoPlayerIs(enemySide);
			//boolean lowImpact = battle.getNonPlayerCombined().getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_LOW_REP_IMPACT) == true;
			boolean lowImpact = isLowRepImpact();
			if (lowImpact) {
				for (CampaignFleetAPI enemy : battle.getSnapshotFor(enemySide)) {
					Misc.makeLowRepImpact(enemy, "battleOnLowImpactSide");
				}
			}
			
			// ONLY CHANGED BLOCK
			if (!lowImpact)
			{
				if (playerPursued && playerWon) {
					if (knowsWhoPlayerIs && !lowImpact) {
						action = CoreReputationPlugin.RepActions.COMBAT_AGGRESSIVE;
					} else {
						action = CoreReputationPlugin.RepActions.COMBAT_AGGRESSIVE_TOFF;
					}
				} else if (playerWasAggressive) {
					if (knowsWhoPlayerIs && !lowImpact) {
						action = CoreReputationPlugin.RepActions.COMBAT_NORMAL;
					} else {
						action = CoreReputationPlugin.RepActions.COMBAT_NORMAL_TOFF;
					}
				}
			}
			
			if (!okToAdjustEnemy) action = null;
			
			Set<String> seen = new HashSet<String>();
			if (action != null) {
				// use snapshot: ensure loss of reputation with factions if their fleets were destroyed
				for (CampaignFleetAPI enemy : battle.getSnapshotFor(enemySide)) {
					String factionId = enemy.getFaction().getId();
					if (seen.contains(factionId)) continue;
					seen.add(factionId);
					Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(action, null, dialog.getTextPanel()), factionId);
					printedAdjustmentText = true;
				}
			}
			
			//if (playerWon) {
				action = CoreReputationPlugin.RepActions.COMBAT_HELP_MINOR;
				float playerFP = 0;
				float allyFP = 0;
				float enemyFP = 0;
				for (CampaignFleetAPI fleet : battle.getSnapshotFor(playerSide)) {
					for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
						if (fleet.isPlayerFleet()) {
							playerFP += member.getFleetPointCost();
						} else {
							allyFP += member.getFleetPointCost();
						}
					}
				}
				for (CampaignFleetAPI fleet : battle.getSnapshotFor(enemySide)) {
					for (FleetMemberAPI member : fleet.getFleetData().getSnapshot()) {
						enemyFP += member.getFleetPointCost();
					}
				}
				if (allyFP > enemyFP || !playerWon) {
					action = CoreReputationPlugin.RepActions.COMBAT_HELP_MINOR;
				} else if (allyFP < enemyFP * 0.5f) {
					action = CoreReputationPlugin.RepActions.COMBAT_HELP_CRITICAL;
				} else {
					action = CoreReputationPlugin.RepActions.COMBAT_HELP_MAJOR;
				}
				
//				if (playerFPHullDamageToEnemies <= 0) {
//					action = null;
//				} else if (playerFPHullDamageToEnemies < allyFPHullDamageToEnemies * 0.1f) {
//					action = RepActions.COMBAT_HELP_MINOR;
//				}
				float f = computePlayerContribFraction();
				if (f <= 0) {
					action = null;
				} else if (f < 0.1f) {
					action = CoreReputationPlugin.RepActions.COMBAT_HELP_MINOR;
				}
				
				if (action != null) {
					float totalDam = allyFPHullDamageToEnemies + playerFPHullDamageToEnemies;
					if (totalDam < 10) {
						action = CoreReputationPlugin.RepActions.COMBAT_HELP_MINOR;
					} else if (totalDam < 20 && action == CoreReputationPlugin.RepActions.COMBAT_HELP_CRITICAL) {
						action = CoreReputationPlugin.RepActions.COMBAT_HELP_MAJOR;
					}
				}
				
				if (battle.isPlayerInvolvedAtStart() && action != null) {
					//action = RepActions.COMBAT_HELP_MINOR;
					action = null;
				}
				
				if (!okToAdjustAlly) action = null;
//				if (leavingEarly) {
//					action = null;
//				}
				
				// rep increases
				seen.clear();
				for (CampaignFleetAPI ally : battle.getSnapshotFor(playerSide)) {
					if (ally.isPlayerFleet()) continue;
					
					String factionId = ally.getFaction().getId();
					if (seen.contains(factionId)) continue;
					seen.add(factionId);
					
					Float friendlyFPHull = playerFPHullDamageToAlliesByFaction.get(ally.getFaction());
					float threshold = 2f;
					if (action == CoreReputationPlugin.RepActions.COMBAT_HELP_MAJOR) {
						threshold = 5f;
					} else if (action == CoreReputationPlugin.RepActions.COMBAT_HELP_CRITICAL) {
						threshold = 10f;
					}
					if (friendlyFPHull != null && friendlyFPHull > threshold) {
						// can lose reputation with sides that didn't survive
						//Global.getSector().adjustPlayerReputation(new RepActionEnvelope(RepActions.COMBAT_FRIENDLY_FIRE, (friendlyFPHull - threshold), dialog.getTextPanel()), factionId);
					} else if (action != null && playerSide.contains(ally)) {
						// only gain reputation with factions whose fleets actually survived
						Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(action, null, dialog.getTextPanel()), factionId);
						printedAdjustmentText = true;
					}
				}
				
				
				// friendly fire rep decreases
				if (okToAdjustAlly) {
					boolean first = true;
					seen.clear();
					for (CampaignFleetAPI ally : battle.getSnapshotFor(playerSide)) {
						if (ally.isPlayerFleet()) continue;
						
						String factionId = ally.getFaction().getId();
						if (Factions.PLAYER.equals(factionId)) continue;
						if (seen.contains(factionId)) continue;
						seen.add(factionId);
						
						Float friendlyFPHull = playerFPHullDamageToAlliesByFaction.get(ally.getFaction());
						float threshold = 2f;
						if (action == CoreReputationPlugin.RepActions.COMBAT_HELP_MAJOR) {
							threshold = 5f;
						} else if (action == CoreReputationPlugin.RepActions.COMBAT_HELP_CRITICAL) {
							threshold = 10f;
						}
						if (friendlyFPHull != null && friendlyFPHull > threshold) {
							if (first && ffText != null) {
								first = false;
								dialog.getTextPanel().addParagraph(ffText);
							}
							// can lose reputation with sides that didn't survive
							Global.getSector().adjustPlayerReputation(new CoreReputationPlugin.RepActionEnvelope(CoreReputationPlugin.RepActions.COMBAT_FRIENDLY_FIRE, (friendlyFPHull - threshold), dialog.getTextPanel()), factionId);
							printedAdjustmentText = true;
						} else if (action != null && playerSide.contains(ally)) {
							// only gain reputation with factions whose fleets actually survived
							//Global.getSector().adjustPlayerReputation(new RepActionEnvelope(action, null, dialog.getTextPanel()), factionId);
						}
					}
				}
			//}
			return printedAdjustmentText;
		}
		
		return false;
	}
	*/
}
