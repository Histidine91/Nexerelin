package exerelin;

import exerelin.utilities.ExerelinUtilsPlayer;
import exerelin.utilities.ExerelinUtils;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import exerelin.diplomacy.AllianceManager;
import exerelin.diplomacy.AllianceRecord;
import exerelin.diplomacy.DiplomacyRecord;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsMessaging;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.ArrayList;

/**
 * OUT OF DATE, use exerelin.campaign.DiplomacyManager instead!.
 * This class manages faction relationships for Exerelin.
 * The updateRelationships method should be run each day. It will
 * pick the next faction to update and repeat in a cycle.
 * Also handles player influence with factions
 */

public class DiplomacyManager
{
	private AllianceManager allianceManager;
	private DiplomacyRecord[] factionRecords;

	private final int WAR_LEVEL = -40;
	private final int ALLY_LEVEL = 40;
	private final int PEACE_TREATY_LEVEL = -20;
	private final int END_ALLIANCE_LEVEL = 20;

	public DiplomacyRecord playerRecord;

	// Variables used to manage single update per day
	public int nextRecordToUpdate = 0;
	String factionIdFirst;
	String factionIdLast;

	public DiplomacyManager()
	{
		allianceManager = new AllianceManager();

		String [] availableFactions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();
		factionRecords = new DiplomacyRecord[availableFactions.length];

		// Build a record for each available faction
		for (int i = 0; i < factionRecords.length; i = i + 1)
		{
			factionRecords[i] = new DiplomacyRecord(availableFactions[i], availableFactions);
			if(availableFactions[i].equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
				playerRecord = factionRecords[i];
		}
	}

	// Check if player has betrayed a faction since last check
	public void checkBetrayal()
	{
        // TODO CAN POSSIBLY BETRAY AS PLAYER FACTION?
        if(SectorManager.getCurrentSectorManager().isPlayerInPlayerFaction())
            return;

		FactionAPI f1API = Global.getSector().getFaction(playerRecord.getFactionId());

		// Check for betrayal of each faction
		for(int j = 0; j < factionRecords.length; j = j + 1)
		{
			FactionAPI f2API = Global.getSector().getFaction(factionRecords[j].getFactionId());

			if(f2API.getId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
				continue;

			float currentRelationship = f1API.getRelationship(f2API.getId());

			// Check what we had stored for game relationship
			if(factionRecords[j].checkForBetray(f1API.getId(), currentRelationship))
			{
				float previousGameRelationship = factionRecords[j].getGameRelationship(f1API.getId());

				if(previousGameRelationship >= 1)
				{
					ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getDisplayName() + " has betrayed " + Global.getSector().getFaction(f2API.getId()).getDisplayName(), Color.magenta);
				}
				else if(previousGameRelationship >= 0)
				{
					ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getDisplayName() + " has broken treaty with " + Global.getSector().getFaction(f2API.getId()).getDisplayName(), Color.magenta);
				}

				if(playerRecord.isInAlliance() && factionRecords[j].isInAlliance())
				{
					// War between players alliance and alliance
					if(playerRecord.getAllianceId().equalsIgnoreCase(factionRecords[j].getAllianceId()))
					{
						// Same alliance
						removeFactionFromAlliance(playerRecord.getAllianceId(), playerRecord.getFactionId(), true);
					}
					else
					{
						// Different alliances
						declareAllianceWarOrPeace(playerRecord.getAllianceId(), factionRecords[j].getAllianceId(), -1);

						DiplomacyRecord[] playerAllianceRecords = getDiplomacyRecordsForAlliance(playerRecord.getAllianceId());
						DiplomacyRecord[] otherAllianceRecords = getDiplomacyRecordsForAlliance(factionRecords[j].getAllianceId());

						for(int i = 0; i < playerAllianceRecords.length; i++)
						{
							DiplomacyRecord playerAllianceFactionRecord = playerAllianceRecords[i];

							if(playerAllianceFactionRecord.getFactionId().equalsIgnoreCase(playerRecord.getFactionId()))
								continue;

							for(int k = 0; k < otherAllianceRecords.length; k++)
							{
								DiplomacyRecord otherAllianceFactionRecord = otherAllianceRecords[k];

								playerAllianceFactionRecord.setFactionRelationship(otherAllianceFactionRecord.getFactionId(), WAR_LEVEL);
								otherAllianceFactionRecord.setFactionRelationship(playerAllianceFactionRecord.getFactionId(), WAR_LEVEL *2);
							}
						}
					}
				}
				else if (playerRecord.isInAlliance())
				{
					// War between players alliance and faction
					declareWarOrPeaceBetweenFactionAndAlliance(factionRecords[j].getFactionId(), playerRecord.getAllianceId(), -1);

					DiplomacyRecord[] records = getDiplomacyRecordsForAlliance(playerRecord.getAllianceId());
					for(int i = 0; i < records.length; i++)
					{
						DiplomacyRecord allianceFactionRecord = records[i];
						allianceFactionRecord.setFactionRelationship(factionRecords[j].getFactionId(), WAR_LEVEL);
						factionRecords[j].setFactionRelationship(allianceFactionRecord.getFactionId(), WAR_LEVEL *2);
					}
				}
				else if(factionRecords[j].isInAlliance())
				{
					// War between player and other factions alliance
					declareWarOrPeaceBetweenFactionAndAlliance(playerRecord.getFactionId(), factionRecords[j].getAllianceId(), -1);

					DiplomacyRecord[] records = getDiplomacyRecordsForAlliance(factionRecords[j].getAllianceId());
					for(int i = 0; i < records.length; i++)
					{
						DiplomacyRecord allianceFactionRecord = records[i];
						allianceFactionRecord.setFactionRelationship(playerRecord.getFactionId(), WAR_LEVEL);
						playerRecord.setFactionRelationship(allianceFactionRecord.getFactionId(), WAR_LEVEL *2);
					}
				}
				else
				{
					// War between player and faction
					factionRecords[j].setFactionRelationship(f1API.getId(), WAR_LEVEL + WAR_LEVEL);
					playerRecord.setFactionRelationship(factionRecords[j].getFactionId(), WAR_LEVEL);
					declareFactionWarOrPeace(playerRecord, factionRecords[j], -1);
				}



				// Increase players relationship with betrayed factions enemies
				String[] enemies = factionRecords[j].getEnemyFactions();
				for(int i = 0; i < enemies.length; i++)
				{
					DiplomacyRecord enemyOfBetrayed = this.getRecordForFaction(enemies[i]);
					if(!enemyOfBetrayed.getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
						enemyOfBetrayed.addToFactionRelationship(SectorManager.getCurrentSectorManager().getPlayerFactionId(), 20);
				}

				// Decrease players relationship with betrayed factions allies
				String[] allies = factionRecords[j].getAlliedFactions();
				for(int i = 0; i < allies.length; i++)
				{
					DiplomacyRecord allyOfBetrayed = this.getRecordForFaction(allies[i]);
					if(!allyOfBetrayed.getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
						allyOfBetrayed.addToFactionRelationship(SectorManager.getCurrentSectorManager().getPlayerFactionId(), -20);
				}

				// Decrease players relationship with factions neutral to betrayed
				for(int k = 0; k < this.factionRecords.length; k++)
				{
					Boolean allyOrEnemy = false;
					DiplomacyRecord record = this.factionRecords[k];

					if(record.getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()) || record.getFactionId().equalsIgnoreCase(factionRecords[j].getFactionId()))
						continue; // Skip player faction or betrayed faction

					for (int l = 0; l < allies.length; l++)
					{
						if(allies[l].equalsIgnoreCase(record.getFactionId()))
						{
							allyOrEnemy = true;
							break;
						}
					}

					for (int m = 0; m < allies.length; m++)
					{
						if(allies[m].equalsIgnoreCase(record.getFactionId()))
						{
							allyOrEnemy = true;
							break;
						}
					}

					if(allyOrEnemy)
						continue;

					record.addToFactionRelationship(SectorManager.getCurrentSectorManager().getPlayerFactionId(), -10);
				}
			}
		}
	}

	// Each call will update a faction relationships, and move the counter to the next faction
	public void updateRelationships()
	{
		DiplomacyRecord recordToUpdate = factionRecords[nextRecordToUpdate];

		// If first record, reset system data
		if(nextRecordToUpdate == 0)
		{
			System.out.println(" - - - - - -        - - - - - - ");
			factionIdFirst = SectorManager.getCurrentSectorManager().getLeadingFaction();
			factionIdLast = SectorManager.getCurrentSectorManager().getLosingFaction();
			System.out.println("Sector Leader: " + factionIdFirst + ", Sector Loser: " + factionIdLast);
		}

		if(nextRecordToUpdate == factionRecords.length - 1)
			nextRecordToUpdate = 0;
		else
			nextRecordToUpdate = nextRecordToUpdate + 1;

		if(!SectorManager.getCurrentSectorManager().isFactionInSector(recordToUpdate.getFactionId()))
			return;

		// Update and Get current level of warWeariness
		Boolean atWar = isFactionAtWarAndHasTarget(recordToUpdate.getFactionId());
		int warweariness = recordToUpdate.updateWarWeariness(atWar);
		if(warweariness > 1)
			warweariness = 1;

		// Update all relationship levels with all other factions
		for(int j = 0; j < factionRecords.length; j = j + 1)
		{

			if(factionRecords[j].getFactionId().equalsIgnoreCase(recordToUpdate.getFactionId()))
				continue;

			if(!SectorManager.getCurrentSectorManager().isFactionInSector(factionRecords[j].getFactionId()))
				continue;

			int factionRelationship = recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId());
			float gameRelationship  = recordToUpdate.getGameRelationship(factionRecords[j].getFactionId());

			// If last, like them, if first, don't like them
			if(factionRecords[j].getFactionId().equalsIgnoreCase(factionIdFirst))
				factionRelationship = factionRelationship - 4;
			else if (factionRecords[j].getFactionId().equalsIgnoreCase(factionIdLast))
				factionRelationship = factionRelationship + 2;

			// Are they beating us or are we beating them
			Float ourPercentage = SectorManager.getCurrentSectorManager().getSectorOwnership(recordToUpdate.getFactionId());
			Float theirPercentage = SectorManager.getCurrentSectorManager().getSectorOwnership(factionRecords[j].getFactionId());
			factionRelationship = factionRelationship + (int)((ourPercentage - theirPercentage)*5);

			// If far, like them, if close, don't like them (we want their stations!)
			//TODO - A good way to rework this on a sector level, present in same systems?
			/*
			float factionDistanceDiffFromAverage = ExerelinData.getInstance().systemManager.getSystemStationManager().getDistanceBetweenFactionsRelativeToAverage(recordToUpdate.getFactionId(), factionRecords[j].getFactionId());

			if(factionDistanceDiffFromAverage != Float.POSITIVE_INFINITY
					&& factionDistanceDiffFromAverage != Float.NEGATIVE_INFINITY)
			{
				int relationshipChange = ExerelinUtils.getRandomNearestInteger( factionDistanceDiffFromAverage * 2.0f );
				if (gameRelationship > 0) { // if they are ally, like them when close
					relationshipChange = Math.abs(relationshipChange); //we want strong and nearby ally
				}

				factionRelationship += relationshipChange;
			}
			*/

			// Add whatever the current bias is
			int f1API_about_f2API = recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId());
			int bias = 0;
			if(f1API_about_f2API < -10 && f1API_about_f2API > -60)
				bias = -1;
			else if (f1API_about_f2API > 10 && f1API_about_f2API < 60)
				bias = 1;

			// Only add warweariness if we are actually at war with this faction
			if(gameRelationship < 0)
				factionRelationship = factionRelationship + bias + warweariness;
			else
				factionRelationship = factionRelationship + bias;

			// If not at war, decrease relationships with allies a lot and neutral slightly
			if(!atWar && gameRelationship >= 1)
				factionRelationship = factionRelationship - 4;
			else if(!atWar && gameRelationship > 0)
				factionRelationship = factionRelationship - 2;

			// Add a bit of random
			int relationshipRandom = ExerelinUtils.getRandomInRange(-2, 2);
			factionRelationship = factionRelationship + relationshipRandom;

			// Dislike them if in different alliance
			if(recordToUpdate.isInAlliance() && factionRecords[j].isInAlliance() && !recordToUpdate.getAllianceId().equalsIgnoreCase(factionRecords[j].getAllianceId()))
				factionRelationship = factionRelationship - 3;

			// 1% chance to reset relationship
			if(ExerelinUtils.getRandomInRange(0,99) == 0)
				factionRelationship = 0;

            // Add extra player increase if skilled appropriatly
            if(recordToUpdate.getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
            {
                float bonus = ExerelinUtilsPlayer.getPlayerDiplomacyRelationshipBonus();
                factionRelationship = factionRelationship + Math.abs((int)(factionRelationship*bonus));
            }

            // Check if we need to add/remove anything for this specific faction
            if(factionRelationship < 0)
                factionRelationship = factionRelationship + ExerelinConfig.getExerelinFactionConfig(recordToUpdate.getFactionId()).negativeDiplomacyExtra;
            else if(factionRelationship > 0)
                factionRelationship = factionRelationship + ExerelinConfig.getExerelinFactionConfig(recordToUpdate.getFactionId()).positiveDiplomacyExtra;

            // Does this faction innately like/dislike the other faction
            if(ExerelinUtils.doesStringArrayContainValue(factionRecords[j].getFactionId(), ExerelinConfig.getExerelinFactionConfig(recordToUpdate.getFactionId()).factionsLiked, false))
                factionRelationship = factionRelationship + 1;
            if(ExerelinUtils.doesStringArrayContainValue(factionRecords[j].getFactionId(), ExerelinConfig.getExerelinFactionConfig(recordToUpdate.getFactionId()).factionsDisliked, false))
                factionRelationship = factionRelationship - 1;

			// Update the relationship setting
			recordToUpdate.setFactionRelationship(factionRecords[j].getFactionId(), factionRelationship);
			//System.out.println(recordToUpdate.getFactionId() + " relationship to " + factionRecords[j].getFactionId() + " = " + recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId()));
		}

		String[] enemies = recordToUpdate.getEnemyFactions();
		String[] allies = recordToUpdate.getAlliedFactions();

		// Increase relationship with allies of current records allies
		for(int i = 0; i < allies.length; i++)
		{
			DiplomacyRecord allyRecord = this.getRecordForFaction(allies[i]);
			String[] alliesAllies = allyRecord.getAlliedFactions();
			for(int k = 0; k < alliesAllies.length; k++)
			{
				DiplomacyRecord alliesAllyRecord = this.getRecordForFaction(alliesAllies[k]);
				if(!alliesAllyRecord.getFactionId().equalsIgnoreCase(recordToUpdate.getFactionId()))
					recordToUpdate.addToFactionRelationship(alliesAllyRecord.getFactionId(), 1);
			}
		}

		// Decrease relationship with allies of current records enemies
		for(int i = 0; i < allies.length; i++)
		{
			DiplomacyRecord allyRecord = this.getRecordForFaction(allies[i]);
			String[] alliesEnemies = allyRecord.getEnemyFactions();
			for(int k = 0; k < alliesEnemies.length; k++)
			{
				DiplomacyRecord alliesEnemyRecord = this.getRecordForFaction(alliesEnemies[k]);
				if(!alliesEnemyRecord.getFactionId().equalsIgnoreCase(recordToUpdate.getFactionId()))
					recordToUpdate.addToFactionRelationship(alliesEnemyRecord.getFactionId(), -1);
			}
		}

		// Decrease relationship with enemies of current records allies
		for(int i = 0; i < enemies.length; i++)
		{
			DiplomacyRecord enemyRecord = this.getRecordForFaction(enemies[i]);
			String[] enemiesAllies = enemyRecord.getAlliedFactions();
			for(int k = 0; k < enemiesAllies.length; k++)
			{
				DiplomacyRecord enemiesAllyRecord = this.getRecordForFaction(enemiesAllies[k]);
				if(!enemiesAllyRecord.getFactionId().equalsIgnoreCase(recordToUpdate.getFactionId()))
					recordToUpdate.addToFactionRelationship(enemiesAllyRecord.getFactionId(), -1);
			}
		}

		// Increase relationship with enemies of current records enemies
		for(int i = 0; i < enemies.length; i++)
		{
			DiplomacyRecord enemyRecord = this.getRecordForFaction(enemies[i]);
			String[] enemiesEnemies = enemyRecord.getEnemyFactions();
			for(int k = 0; k < enemiesEnemies.length; k++)
			{
				DiplomacyRecord enemiesEnemyRecord = this.getRecordForFaction(enemiesEnemies[k]);
				if(!enemiesEnemyRecord.getFactionId().equalsIgnoreCase(recordToUpdate.getFactionId()))
					recordToUpdate.addToFactionRelationship(enemiesEnemyRecord.getFactionId(), 1);
			}
		}

		// Decrease relationship with allies as alliance gets large
		if(recordToUpdate.isInAlliance())
		{
			float allianceOwnership = deriveAllianceSectorOwnership(recordToUpdate.getAllianceId());

			for(int i = 0; i < allies.length; i++)
				recordToUpdate.addToFactionRelationship(allies[i], (int)(allianceOwnership * -6));

		}

		// Process any diplomacy changes
		this.updateFactionDiplomacy(recordToUpdate);
	}

	// Apply any war/peace/alliance changes that need to be made
	private void updateFactionDiplomacy(DiplomacyRecord diplomacyRecord)
	{
		if(!SectorManager.getCurrentSectorManager().isFactionInSector(diplomacyRecord.getFactionId()))
			return;

		for(int i = 0; i < this.factionRecords.length; i++)
		{
			DiplomacyRecord otherDiplomacyRecord = this.factionRecords[i];

			if(diplomacyRecord.getFactionId().equalsIgnoreCase(otherDiplomacyRecord.getFactionId()))
				continue;

			if(!SectorManager.getCurrentSectorManager().isFactionInSector(otherDiplomacyRecord.getFactionId()))
				continue;

			int rel1 = 0;
			int rel2 = 0;
			float currentGameRelationship = 0f;
			if(	diplomacyRecord.isInAlliance()
				&& !otherDiplomacyRecord.isInAlliance())
			{
				// First record is in alliance
				rel1 = deriveAllianceRelationshipWithFaction(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getFactionId());
				rel2 = deriveFactionRelationshipWithAlliance(otherDiplomacyRecord.getFactionId(), diplomacyRecord.getAllianceId());
				currentGameRelationship = deriveAllianceGameRelationshipWithFaction(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getFactionId());
			}
			else if(!diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance())
			{
				// Second record is in alliance
				rel1 = deriveFactionRelationshipWithAlliance(diplomacyRecord.getFactionId(), otherDiplomacyRecord.getAllianceId());
				rel2 = deriveAllianceRelationshipWithFaction(otherDiplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId());
				currentGameRelationship = deriveAllianceGameRelationshipWithFaction(otherDiplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId());
			}
			else if(diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance()
					&& !diplomacyRecord.getAllianceId().equalsIgnoreCase(otherDiplomacyRecord.getAllianceId()))
			{
				// Records are in different alliances
				rel1 = deriveAllianceRelationshipWithAlliance(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getAllianceId());
				rel2 = deriveAllianceRelationshipWithAlliance(otherDiplomacyRecord.getAllianceId(), diplomacyRecord.getAllianceId());
				currentGameRelationship = deriveAllianceGameRelationshipWithAlliance(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getAllianceId());
			}
			else if(diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance()
					&& diplomacyRecord.getAllianceId().equalsIgnoreCase(otherDiplomacyRecord.getAllianceId())
					&& allianceManager.getAllianceRecord(diplomacyRecord.getAllianceId()).getNumFactionsInAlliance() > 2)
			{
				// Records are in same alliance, alliance has more than 2 factions in it
				rel1 = deriveFactionRelationshipWithAlliance(diplomacyRecord.getFactionId(), otherDiplomacyRecord.getAllianceId());
				rel2 = deriveAllianceRelationshipWithFaction(otherDiplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId());
				currentGameRelationship = 1;
			}
			else
			{
				// Factions are not in alliances OR factions are in same alliance with only them
				rel1 = diplomacyRecord.getFactionRelationship(factionRecords[i].getFactionId());
				rel2 = factionRecords[i].getFactionRelationship(diplomacyRecord.getFactionId());
				currentGameRelationship = diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId());
			}

			if( !diplomacyRecord.isInAlliance()
				&& !otherDiplomacyRecord.isInAlliance())
			{
				// Neither faction is in an alliance
				if(rel1 + rel2 < WAR_LEVEL + WAR_LEVEL && currentGameRelationship >= 0 && currentGameRelationship < 1)
				{
					// Reset war weariness for factions if they dont have a war
					if(diplomacyRecord.getEnemyFactions().length == 0)
						diplomacyRecord.setWarweariness(0);
					if(otherDiplomacyRecord.getEnemyFactions().length == 0)
						otherDiplomacyRecord.setWarweariness(0);

					// Declare war
					declareFactionWarOrPeace(diplomacyRecord,  otherDiplomacyRecord, -1);

					// Reduce likelihood of war with other factions slightly
					diplomacyRecord.bulkAddToFactionRelationships(otherDiplomacyRecord.getFactionId(), ExerelinUtils.getRandomInRange(5, 6));
					break;
				}
				else if(rel1 + rel2 > PEACE_TREATY_LEVEL + PEACE_TREATY_LEVEL && currentGameRelationship < 0)
				{
					// End war
					declareFactionWarOrPeace(diplomacyRecord,  otherDiplomacyRecord, 0);
					break;
				}
				else if(rel1 + rel2 > ALLY_LEVEL + ALLY_LEVEL && currentGameRelationship < 1)
				{
					// Start alliance
					startAlliance(diplomacyRecord,  otherDiplomacyRecord);

					// Increase likelihood of war with other factions slightly
					diplomacyRecord.bulkAddToFactionRelationships(otherDiplomacyRecord.getFactionId(), ExerelinUtils.getRandomInRange(-5, -8));
					break;
				}
			}
			else if(diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance()
					&& diplomacyRecord.getAllianceId().equalsIgnoreCase(otherDiplomacyRecord.getAllianceId())
					&& allianceManager.getAllianceRecord(diplomacyRecord.getAllianceId()).getNumFactionsInAlliance() == 2)
			{
				// Same alliance with just these two factions

				// Work out alliance system ownership
				float allianceSectorOwnership = 0f;
				String[] allies = allianceManager.getAllianceRecord(diplomacyRecord.getAllianceId()).getFactions();
				for(int j = 0; j< allies.length; j++)
					allianceSectorOwnership = allianceSectorOwnership + SectorManager.getCurrentSectorManager().getSectorOwnership(allies[j]);

				if(rel1 + rel2 < END_ALLIANCE_LEVEL + END_ALLIANCE_LEVEL)
				{
					// End alliance
					dissolveAlliance(diplomacyRecord.getAllianceId(), getDiplomacyRecordsForAlliance(diplomacyRecord.getAllianceId()));
					break;
				}
				/*else if(ExerelinUtils.getRandomInRange(0,9)*allianceSectorOwnership > 4)
				{
					// Betray alliance
                    if(this.allianceManager.isFactionInAlliance(SectorManager.getCurrentSectorManager().getPlayerFactionId(), diplomacyRecord.getAllianceId())
                            && ExerelinUtilsPlayer.getPlayerDiplomacyBetrayalReducedChance() > 0f)
                    {
                        System.out.println("Chance to save alliance betrayal");
                        if(ExerelinUtils.getRandomInRange(0, 4) > 0)
                        {
                            removeFactionFromAlliance(diplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId(), true);
                            break;
                        }
                    }
                    else
                    {
					    removeFactionFromAlliance(diplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId(), true);
					    break;
                    }
				}*/
			}
			else if(diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance()
					&& diplomacyRecord.getAllianceId().equalsIgnoreCase(otherDiplomacyRecord.getAllianceId()))
			{
				// Same alliance with more than two members

				// Work out alliance system ownership
				float allianceSectorOwnership = 0f;
				String[] allies = allianceManager.getAllianceRecord(diplomacyRecord.getAllianceId()).getFactions();
				for(int j = 0; j< allies.length; j++)
					allianceSectorOwnership = allianceSectorOwnership + SectorManager.getCurrentSectorManager().getSectorOwnership(allies[j]);

				if(rel1 + rel2 < END_ALLIANCE_LEVEL + END_ALLIANCE_LEVEL)
				{
					// Leave alliance
					removeFactionFromAlliance(diplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId(), false);
					break;
				}
				/*else if(ExerelinUtils.getRandomInRange(0,9)*allianceSectorOwnership > 4)
				{
					// Betray the alliance
                    if(this.allianceManager.isFactionInAlliance(SectorManager.getCurrentSectorManager().getPlayerFactionId(), diplomacyRecord.getAllianceId())
                            && ExerelinUtilsPlayer.getPlayerDiplomacyBetrayalReducedChance() > 0f)
                    {
                        System.out.println("Chance to save alliance betrayal");
                        if(ExerelinUtils.getRandomInRange(0, 4) > 0)
                        {
                            removeFactionFromAlliance(diplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId(), true);
                            break;
                        }
                    }
                    else
                    {
                        removeFactionFromAlliance(diplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId(), true);
                        break;
                    }
				}*/
			}
			else if(diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance())
			{
				// Different alliances

				if(rel1 + rel2 < WAR_LEVEL + WAR_LEVEL && currentGameRelationship >= 0 && currentGameRelationship < 1)
				{
					// Declare war between alliances
					declareAllianceWarOrPeace(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getAllianceId(), -1);
					break;
				}
				else if(rel1 + rel2 > PEACE_TREATY_LEVEL + PEACE_TREATY_LEVEL && currentGameRelationship < 0)
				{
					// End war between alliances
					declareAllianceWarOrPeace(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getAllianceId(), 0);
					break;
				}
				else if(rel1 + rel2 > ALLY_LEVEL + ALLY_LEVEL && currentGameRelationship < 1 && currentGameRelationship > 0)
				{
					// Join alliances
					joinAlliances(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getAllianceId());
					break;
				}
			}
			else if (diplomacyRecord.isInAlliance()
					&& !otherDiplomacyRecord.isInAlliance())
			{
				// First record is in alliance, second record alone

				if(rel1 + rel2 < WAR_LEVEL + WAR_LEVEL && currentGameRelationship >= 0 && currentGameRelationship < 1)
				{
					// Declare war between alliance and faction
					declareWarOrPeaceBetweenFactionAndAlliance(otherDiplomacyRecord.getFactionId(), diplomacyRecord.getAllianceId(), -1);
					break;
				}
				else if(rel1 + rel2 > PEACE_TREATY_LEVEL + PEACE_TREATY_LEVEL && currentGameRelationship < 0)
				{
					// End war between alliance and faction
					declareWarOrPeaceBetweenFactionAndAlliance(otherDiplomacyRecord.getFactionId(), diplomacyRecord.getAllianceId(), 0);
					break;
				}
				else if(rel1 + rel2 > ALLY_LEVEL + ALLY_LEVEL && currentGameRelationship < 1 && currentGameRelationship > 0)
				{
					// Join alliance
					addFactionToAlliance(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getFactionId());
					break;
				}
			}
			else if(!diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance())
			{
				// First record alone, second record is in alliance

				if(rel1 + rel2 < WAR_LEVEL + WAR_LEVEL && currentGameRelationship >= 0)
				{
					// Declare war between faction and alliance
					declareWarOrPeaceBetweenFactionAndAlliance(diplomacyRecord.getFactionId(), otherDiplomacyRecord.getAllianceId(), -1);
					break;
				}
				else if(rel1 + rel2 > PEACE_TREATY_LEVEL + PEACE_TREATY_LEVEL && currentGameRelationship < 0)
				{
					// End war between faction and alliance
					declareWarOrPeaceBetweenFactionAndAlliance(diplomacyRecord.getFactionId(), otherDiplomacyRecord.getAllianceId(), 0);
					break;
				}
				else if(rel1 + rel2 > ALLY_LEVEL + ALLY_LEVEL && currentGameRelationship < 1)
				{
					// Join alliance
					addFactionToAlliance(otherDiplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId());
					break;
				}
			}
		}
	}

	// Update faction relationships on system event
	public void updateRelationshipOnEvent(String factionId, String otherFactionId, String event)
	{
		DiplomacyRecord factionRecord = this.getRecordForFaction(factionId);
		DiplomacyRecord otherFactionRecord = this.getRecordForFaction(otherFactionId);

		if(factionRecord == null || otherFactionRecord == null)
			return;

		int relChange = 0;
		int allyChange = 0;
		int enemyChange = 0;
		Boolean affectsAllies = false;
		Boolean affectsEnemies = false;
        Boolean updateBoth = false;

		if(event.equalsIgnoreCase("LostStation"))
		{
			relChange = -5;
			affectsAllies = true;
			affectsEnemies = true;
			allyChange = -1; // allies perceive weakness
			enemyChange = 10; // Less of a threat

			if(!SectorManager.getCurrentSectorManager().isFactionInSector(factionId))
			{
				ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + " has been driven from Exerelin!");
				declarePeaceWithAllFactions(factionId);
			}
		}
		else if(event.equalsIgnoreCase("agent"))
		{
			ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(SectorManager.getCurrentSectorManager().getPlayerFactionId()).getDisplayName() + " agent has caused a disagreement between " + Global.getSector().getFaction(factionId).getDisplayName() + " and " + Global.getSector().getFaction(otherFactionId).getDisplayName(), Color.magenta);
            int factionRelationship = factionRecord.getFactionRelationship(otherFactionId);

            if(factionRelationship >= ALLY_LEVEL)
                relChange = factionRelationship * -1;
            else
                relChange = -40;

            updateBoth = true;
		}
		else if(event.equalsIgnoreCase("prisoner"))
        {
            // Get max of 30 or 50% of negative faction relationship
            int factionRelationship = factionRecord.getFactionRelationship(otherFactionId);
            relChange = Math.max(30, (int)(factionRelationship * 0.50) * -1);
            updateBoth = true;
        }


		System.out.println(event + " for " + factionId + " and " + otherFactionId + " results in " + relChange + " change");
		factionRecord.addToFactionRelationship(otherFactionId, relChange);
        if(updateBoth)
            otherFactionRecord.addToFactionRelationship(factionId, relChange);
		if(affectsAllies)
			updateAllies(factionRecord, allyChange);
		if(affectsEnemies)
			updateEnemies(factionRecord, enemyChange);

	}

	// Create a war with the leading faction if passed in faction doesn't have a war
	public void createWarIfNoneExists(String factionId)
	{
		for(int i = 0; i < SectorManager.getCurrentSectorManager().getSystemManagers().length; i++)
		{
			if(this.getRecordForFaction(factionId).isAtWar())
				return;
		}

		String leadingFactionId = SectorManager.getCurrentSectorManager().getLeadingFaction();
		if(leadingFactionId == null || leadingFactionId.equalsIgnoreCase(factionId))
			return;

		DiplomacyRecord faction = this.getRecordForFaction(factionId);
		DiplomacyRecord leadingFaction = this.getRecordForFaction(leadingFactionId);

		if(leadingFaction != null && faction != null)
		{
			if(leadingFaction.isInAlliance())
			{
				declareWarOrPeaceBetweenFactionAndAlliance(faction.getFactionId(), leadingFaction.getAllianceId(), -1);

				DiplomacyRecord[] records = getDiplomacyRecordsForAlliance(leadingFaction.getAllianceId());
				for(int i = 0; i < records.length; i++)
				{
					DiplomacyRecord allianceFactionRecord = records[i];
					allianceFactionRecord.setFactionRelationship(faction.getFactionId(), WAR_LEVEL);
					faction.setFactionRelationship(allianceFactionRecord.getFactionId(), WAR_LEVEL *2);
				}
			}
			else
			{
				faction.setFactionRelationship(leadingFactionId, WAR_LEVEL *2);
				leadingFaction.setFactionRelationship(faction.getFactionId(), WAR_LEVEL);
				declareFactionWarOrPeace(faction, leadingFaction, -1);
			}
		}
	}

	// Updates relationships for factions allied to a faction
	private void updateAllies(DiplomacyRecord factionRecord, int value)
	{
		for(int i = 0; i < factionRecords.length; i = i + 1)
		{
			if(factionRecords[i].getFactionId().equalsIgnoreCase(factionRecord.getFactionId()))
				continue;

			if(factionRecords[i].getGameRelationship(factionRecord.getFactionId()) >= 1)
			{
				int rel = factionRecords[i].getFactionRelationship(factionRecord.getFactionId());
				rel = rel + value;
				factionRecords[i].setFactionRelationship(factionRecord.getFactionId(), rel);
			}
		}
	}

	// Updates relationships for factions at war with a faction
	private void updateEnemies(DiplomacyRecord factionRecord, int value)
	{
		for(int i = 0; i < factionRecords.length; i = i + 1)
		{
			if(factionRecords[i].getFactionId().equalsIgnoreCase(factionRecord.getFactionId()))
				continue;

			if(factionRecords[i].getGameRelationship(factionRecord.getFactionId()) < 0)
			{
				int rel = factionRecords[i].getFactionRelationship(factionRecord.getFactionId());
				rel = rel + value;
				factionRecords[i].setFactionRelationship(factionRecord.getFactionId(), rel);
			}
		}
	}

	public DiplomacyRecord getRecordForFaction(String factionId)
	{
		for(int i = 0; i < factionRecords.length; i++)
		{
			if(factionRecords[i].getFactionId().equalsIgnoreCase(factionId))
				return factionRecords[i];
		}
		return null;
	}

	public String getRandomNonEnemyFactionIdForFaction(String factionId)
	{
		DiplomacyRecord diplomacyRecord = this.getRecordForFaction(factionId);

		if(diplomacyRecord == null)
			return "";

		int i = ExerelinUtils.getRandomInRange(0, 1);

		String[] alliedFactions = diplomacyRecord.getAlliedFactions();
		String[] neutralFactions = diplomacyRecord.getNeutralFactions();

		String[] possibleFactions;
		if((i == 0 && alliedFactions.length > 0) || neutralFactions.length == 0)
			possibleFactions = diplomacyRecord.getAlliedFactions();
		else
			possibleFactions = diplomacyRecord.getNeutralFactions();

        // Ensure factions are in sector curretnly
        List<String> factions = new ArrayList<String>();
        for(String possibleFactionId : possibleFactions)
        {
            if(SectorManager.getCurrentSectorManager().isFactionInSector(possibleFactionId))
                factions.add(possibleFactionId);
        }


		if(factions.size() == 0)
			return "";

		return factions.get(ExerelinUtils.getRandomInRange(0, factions.size() - 1));
	}

	// Declares peace with all, no message displayed
	public void declarePeaceWithAllFactions(String factionId)
	{
		DiplomacyRecord record = this.getRecordForFaction(factionId);
		record.bulkSetFactionRelationships(null, 0);
		record.bulkSetGameRelationships(null, 0);

		for(int i = 0; i < factionRecords.length; i = i + 1)
		{
			if(factionRecords[i].getFactionId().equalsIgnoreCase(factionId))
				continue;

			factionRecords[i].setGameRelationship(factionId,  0);
			factionRecords[i].getFactionAPI().setRelationship(factionId,  0);
			factionRecords[i].setFactionRelationship(factionId,  0);
		}

		// Remove from alliance
		if(record.isInAlliance())
			removeFactionFromAlliance(record.getAllianceId(), record.getFactionId(), false);
	}



	private int deriveFactionRelationshipWithAlliance(String factionId, String allianceId)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);
		int totalRelationship = 0;

		if(allianceRecord == null)
			return 0;

		String[] allianceFactions = allianceRecord.getFactions();

		for(int i = 0; i < allianceFactions.length; i++)
		{
			if(allianceFactions[i].equalsIgnoreCase(factionId))
				continue;

			totalRelationship = totalRelationship + getRecordForFaction(factionId).getFactionRelationship(allianceFactions[i]);
		}

		//System.out.println(factionId + " relationship with " + allianceId + " = " + totalRelationship/allianceFactions.length);
		if(totalRelationship == 0)
			return totalRelationship;
		else
			return totalRelationship/(allianceFactions.length/2);
	}

	private int deriveAllianceRelationshipWithFaction(String allianceId, String factionId)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);
		int totalRelationship = 0;

		if(allianceRecord == null)
			return 0;

		String[] allianceFactions = allianceRecord.getFactions();

		for(int i = 0; i < allianceFactions.length; i++)
		{
			if(allianceFactions[i].equalsIgnoreCase(factionId))
				continue;

			totalRelationship = totalRelationship + getRecordForFaction(allianceFactions[i]).getFactionRelationship(factionId);
		}

		//System.out.println(allianceId + " relationship with " + factionId + " = " + totalRelationship/allianceFactions.length);
		if(totalRelationship == 0)
			return totalRelationship;
		else
			return totalRelationship/(allianceFactions.length/2);
	}

	private int deriveAllianceRelationshipWithAlliance(String allianceOneId, String allianceTwoId)
	{
		AllianceRecord allianceOneRecord = allianceManager.getAllianceRecord(allianceOneId);
		AllianceRecord allianceTwoRecord = allianceManager.getAllianceRecord(allianceTwoId);
		int totalRelationship = 0;

		if(allianceOneRecord == null || allianceTwoRecord == null)
			return 0;

		String[] allianceOneFactions = allianceOneRecord.getFactions();
		String[] allianceTwoFactions = allianceTwoRecord.getFactions();

		for(int i = 0; i < allianceOneFactions.length; i++)
		{
			// Work out what each faction thinks of the factions in the other alliance
			int relationship = 0;
			for(int j = 0; j < allianceTwoFactions.length; j++)
				relationship = relationship + getRecordForFaction(allianceOneFactions[i]).getFactionRelationship(allianceTwoFactions[j]);

			if(relationship != 0)
				totalRelationship = totalRelationship + relationship/allianceTwoFactions.length;
		}

		//System.out.println(allianceOneRecord.getAllianceNameAndFactions() + " relationship with " + allianceTwoRecord.getAllianceNameAndFactions() + " = " + totalRelationship/allianceOneFactions.length);
		if(totalRelationship == 0)
			return totalRelationship;
		else
			return totalRelationship/(allianceOneFactions.length/2);
	}

    public float deriveFactionRelationshipWithFaction(String factionId, String otherFactionId)
    {
        return this.getRecordForFaction(factionId).getGameRelationship(otherFactionId);
    }

	private float deriveAllianceGameRelationshipWithAlliance(String allianceOneId, String allianceTwoId)
	{
		AllianceRecord allianceOneRecord = allianceManager.getAllianceRecord(allianceOneId);
		AllianceRecord allianceTwoRecord = allianceManager.getAllianceRecord(allianceTwoId);

		if(allianceOneRecord == null || allianceTwoRecord == null)
			return 0;

		DiplomacyRecord[] allianceOneFactions = getDiplomacyRecordsForAlliance(allianceOneId);
		DiplomacyRecord[] allianceTwoFactions = getDiplomacyRecordsForAlliance(allianceTwoId);

		float gameRelationshipTotal = 0;
		for(int i = 0; i < allianceOneFactions.length; i++)
		{
			for(int j = 0; j < allianceTwoFactions.length; j++)
			{
				gameRelationshipTotal = gameRelationshipTotal + allianceTwoFactions[j].getGameRelationship(allianceOneFactions[i].getFactionId());
			}
			gameRelationshipTotal = gameRelationshipTotal/allianceTwoFactions.length;
		}

		gameRelationshipTotal = gameRelationshipTotal/allianceOneFactions.length;

		if(gameRelationshipTotal == 0 || (gameRelationshipTotal < 1 && gameRelationshipTotal > 0))
			return 0;
		else if(gameRelationshipTotal >= 1)
			return 1;
		else if (gameRelationshipTotal < 0)
			return -1;
		else
			return 0;
	}

	private float deriveAllianceGameRelationshipWithFaction(String allianceId, String factionId)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);

		if(allianceRecord == null)
			return 0;

		DiplomacyRecord[] allianceFactions = getDiplomacyRecordsForAlliance(allianceId);

		float gameRelationshipTotal = 0;

		for(int i = 0; i < allianceFactions.length; i++)
			gameRelationshipTotal = gameRelationshipTotal + allianceFactions[i].getGameRelationship(factionId);

		gameRelationshipTotal = gameRelationshipTotal/allianceFactions.length;

		if(gameRelationshipTotal == 0 || (gameRelationshipTotal < 1 && gameRelationshipTotal > 0))
			return 0;
		else if(gameRelationshipTotal >= 1)
			return 1;
		else if (gameRelationshipTotal < 0)
			return -1;
		else
			return 0;
	}

	private DiplomacyRecord[] getDiplomacyRecordsForAlliance(String allianceId)
	{
		String[] allianceFactions = allianceManager.getAllianceRecord(allianceId).getFactions();
		DiplomacyRecord[] diplomacyRecords = new DiplomacyRecord[allianceFactions.length];

		for(int i = 0; i < allianceFactions.length; i++)
			diplomacyRecords[i] = getRecordForFaction(allianceFactions[i]);

		return diplomacyRecords;
	}

	private void addFactionToAlliance(String allianceId, String factionId)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);
		DiplomacyRecord rec = getRecordForFaction(factionId);

		// Set factions game relationships in line with their new alliance
		for(int i = 0; i < factionRecords.length; i++)
		{
			if(factionRecords[i].getFactionId().equalsIgnoreCase(factionId))
				continue;

			DiplomacyRecord iRec = factionRecords[i];

			float allianceGameRelationship = deriveAllianceGameRelationshipWithFaction(allianceId, iRec.getFactionId());

			rec.setGameRelationship(iRec.getFactionId(), allianceGameRelationship);
			rec.getFactionAPI().setRelationship(iRec.getFactionId(), allianceGameRelationship);
			iRec.setGameRelationship(rec.getFactionId(), allianceGameRelationship);
			iRec.getFactionAPI().setRelationship(rec.getFactionId(), allianceGameRelationship);
		}

		allianceRecord.addFactionToAlliance(factionId);
		rec.setAllianceId(allianceId);

		DiplomacyRecord[] allianceRecords = this.getDiplomacyRecordsForAlliance(allianceId);
		for(int i = 0; i < allianceRecords.length; i++)
		{
			allianceRecords[i].setGameRelationship(rec.getFactionId(), 1);
			allianceRecords[i].getFactionAPI().setRelationship(rec.getFactionId(), 1);
			rec.setGameRelationship(allianceRecords[i].getFactionId(), 1);
			rec.getFactionAPI().setRelationship(allianceRecords[i].getFactionId(), 1);
		}

		System.out.println(Global.getSector().getFaction(factionId).getDisplayName() + " has joined " + allianceRecord.getAllianceNameAndFactions());
		if(playerRecord != null && (factionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId)))
			ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + " has joined " + allianceRecord.getAllianceNameAndFactions(), Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + " has joined " + allianceRecord.getAllianceNameAndFactions());
	}

	private void removeFactionFromAlliance(String allianceId, String factionId, Boolean betrayal)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);
		DiplomacyRecord rec = getRecordForFaction(factionId);

		if(!betrayal)
		{
			// Remove faction from alliance peacefully
			if(allianceRecord.getNumFactionsInAlliance() == 2)
				dissolveAlliance(allianceRecord.getAllianceId(), getDiplomacyRecordsForAlliance(allianceId));
			else
			{
				allianceRecord.removeFactionFromAlliance(factionId);
				rec.setAllianceId("");

                DiplomacyRecord[] records = this.getDiplomacyRecordsForAlliance(allianceId);
                for(int i = 0; i < records.length; i++)
                {
                    rec.setGameRelationship(records[i].getFactionId(), 0);
                    rec.getFactionAPI().setRelationship(records[i].getFactionId(), 0);
                    records[i].setGameRelationship(rec.getFactionId(), 0);
                    records[i].getFactionAPI().setRelationship(rec.getFactionId(), 0);
                }

				if(playerRecord != null && (factionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId)))
					ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + " has left " + allianceRecord.getAllianceNameAndFactions(), Color.magenta);
				else
					ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + " has left " + allianceRecord.getAllianceNameAndFactions());
			}
		}
		else
		{
			// Remove faction from alliance and declare war with remaining faction in alliance
			DiplomacyRecord[] factionRecords = getDiplomacyRecordsForAlliance(allianceId);

			if(playerRecord != null && (factionId.equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId)))
				ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + " has betrayed " + allianceRecord.getAllianceNameAndFactions() + "!", Color.magenta);
			else
				ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + " has betrayed " + allianceRecord.getAllianceNameAndFactions() + "!");

			if(allianceRecord.getNumFactionsInAlliance() == 2)
			{
				dissolveAlliance(allianceRecord.getAllianceId(), factionRecords);

				for(int i = 0; i < factionRecords.length; i++)
				{
					if(factionRecords[i].getFactionId().equalsIgnoreCase(factionId))
						continue;
					rec.setFactionRelationship(factionRecords[i].getFactionId(), WAR_LEVEL *2);
					factionRecords[i].setFactionRelationship(rec.getFactionId(), WAR_LEVEL);
					declareFactionWarOrPeace(rec, factionRecords[i], -1);
				}
			}
			else
			{
				allianceRecord.removeFactionFromAlliance(factionId);
				rec.setAllianceId("");

				for(int i = 0; i < factionRecords.length; i++)
				{
					if(factionRecords[i].getFactionId().equalsIgnoreCase(factionId))
						continue;

					rec.setFactionRelationship(factionRecords[i].getFactionId(), WAR_LEVEL);
					factionRecords[i].setFactionRelationship(rec.getFactionId(), PEACE_TREATY_LEVEL);
				}

				declareWarOrPeaceBetweenFactionAndAlliance(factionId, allianceId, -1);
			}
		}
	}

	private void declareFactionWarOrPeace(DiplomacyRecord recordOne, DiplomacyRecord recordTwo, float value)
	{
		recordOne.setGameRelationship(recordTwo.getFactionId(), value);
		recordOne.getFactionAPI().setRelationship(recordTwo.getFactionId(), value);
		recordTwo.setGameRelationship(recordOne.getFactionId(), value);
		recordTwo.getFactionAPI().setRelationship(recordOne.getFactionId(), value);

		String innerMessage = "";
		if(value == -1)
			innerMessage = " has declared war with ";
		else if(value == 0)
			innerMessage = " has signed a peace treaty with ";

		if(playerRecord != null && (recordOne.getFactionId().equalsIgnoreCase(playerRecord.getFactionId()) || recordTwo.getFactionId().equalsIgnoreCase(playerRecord.getFactionId())))
			ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(recordOne.getFactionId()).getDisplayName() + innerMessage + Global.getSector().getFaction(recordTwo.getFactionId()).getDisplayName(), Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(recordOne.getFactionId()).getDisplayName() + innerMessage + Global.getSector().getFaction(recordTwo.getFactionId()).getDisplayName());

	}

	private void declareAllianceWarOrPeace(String allianceOneId, String allianceTwoId, float value)
	{
		AllianceRecord allianceOneRecord = allianceManager.getAllianceRecord(allianceOneId);
		AllianceRecord allianceTwoRecord = allianceManager.getAllianceRecord(allianceTwoId);

		String[] allianceOneFactions = allianceOneRecord.getFactions();
		String[] allianceTwoFactions = allianceTwoRecord.getFactions();

		for(int i = 0; i < allianceOneFactions.length; i++)
		{
			DiplomacyRecord iRec = getRecordForFaction(allianceOneFactions[i]);

			for(int j = 0; j < allianceTwoFactions.length; j++)
			{
				DiplomacyRecord jRec = getRecordForFaction(allianceTwoFactions[j]);
				iRec.setGameRelationship(jRec.getFactionId(), value);
				iRec.getFactionAPI().setRelationship(jRec.getFactionId(), value);
				jRec.setGameRelationship(iRec.getFactionId(), value);
				jRec.getFactionAPI().setRelationship(iRec.getFactionId(), value);
			}
		}

		String innerMessage = "";
		if(value == -1)
			innerMessage = " has declared war with ";
		else if(value == 0)
			innerMessage = " has signed a peace treaty with ";

		if(playerRecord != null && (allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceOneId) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceTwoId)))
			ExerelinUtilsMessaging.addMessage(allianceOneRecord.getAllianceNameAndFactions() + innerMessage + allianceTwoRecord.getAllianceNameAndFactions(), Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage(allianceOneRecord.getAllianceNameAndFactions() + innerMessage + allianceTwoRecord.getAllianceNameAndFactions());
	}

	private void declareWarOrPeaceBetweenFactionAndAlliance(String factionId, String allianceId, float value)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);

		String[] allianceFactions = allianceRecord.getFactions();

		DiplomacyRecord rec = getRecordForFaction(factionId);

		for(int i = 0; i < allianceFactions.length; i++)
		{
			DiplomacyRecord iRec = getRecordForFaction(allianceFactions[i]);
			iRec.setGameRelationship(rec.getFactionId(), value);
			iRec.getFactionAPI().setRelationship(rec.getFactionId(), value);
			rec.setGameRelationship(iRec.getFactionId(), value);
			rec.getFactionAPI().setRelationship(iRec.getFactionId(), value);
		}

		String innerMessage = "";
		if(value == -1)
			innerMessage = " has declared war with ";
		else if(value == 0)
			innerMessage = " has signed a peace treaty with ";

		if(playerRecord != null && (factionId.equalsIgnoreCase(playerRecord.getFactionId()) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId)))
			ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + innerMessage + allianceRecord.getAllianceNameAndFactions(), Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage(Global.getSector().getFaction(factionId).getDisplayName() + innerMessage + allianceRecord.getAllianceNameAndFactions());
	}

	private void startAlliance(DiplomacyRecord rec1, DiplomacyRecord rec2)
	{
		String allianceId = allianceManager.createAlliance(rec1, rec2);
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);

		rec1.setGameRelationship(rec2.getFactionId(), 1);
		rec1.getFactionAPI().setRelationship(rec2.getFactionId(), 1);
		rec2.setGameRelationship(rec1.getFactionId(), 1);
		rec2.getFactionAPI().setRelationship(rec1.getFactionId(), 1);

		// Set the factions game relationships in line with their new alliance
		for(int i = 0; i < factionRecords.length; i++)
		{
			if(factionRecords[i].getFactionId().equalsIgnoreCase(rec1.getFactionId()) || factionRecords[i].getFactionId().equalsIgnoreCase(rec2.getFactionId()))
				continue;

			DiplomacyRecord iRec = factionRecords[i];

			float allianceGameRelationship = deriveAllianceGameRelationshipWithFaction(allianceId, iRec.getFactionId());

			rec1.setGameRelationship(iRec.getFactionId(), allianceGameRelationship);
			rec1.getFactionAPI().setRelationship(iRec.getFactionId(), allianceGameRelationship);
			rec2.setGameRelationship(iRec.getFactionId(), allianceGameRelationship);
			rec2.getFactionAPI().setRelationship(iRec.getFactionId(), allianceGameRelationship);
			iRec.setGameRelationship(rec1.getFactionId(), allianceGameRelationship);
			iRec.getFactionAPI().setRelationship(rec1.getFactionId(), allianceGameRelationship);
			iRec.setGameRelationship(rec2.getFactionId(), allianceGameRelationship);
			iRec.getFactionAPI().setRelationship(rec2.getFactionId(), allianceGameRelationship);
		}

		if(playerRecord != null && (rec1.getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()) || rec2.getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId())))
			ExerelinUtilsMessaging.addMessage("New alliance " + allianceRecord.getAllianceNameAndFactions() + " has been founded!", Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage("New alliance " + allianceRecord.getAllianceNameAndFactions() + " has been founded!");

	}

	private void dissolveAlliance(String allianceId, DiplomacyRecord[] records)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);

		if(playerRecord != null && (allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId)))
			ExerelinUtilsMessaging.addMessage("Alliance " + allianceRecord.getAllianceNameAndFactions() + " has been dissolved.", Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage("Alliance " + allianceRecord.getAllianceNameAndFactions() + " has been dissolved.");

        DiplomacyRecord[] allianceFactions = records;

        for(int i = 0; i < allianceFactions.length; i++)
        {
            DiplomacyRecord iRec = allianceFactions[i];
            for(int j = 0; j < allianceFactions.length; j++)
            {
                DiplomacyRecord jRec = allianceFactions[j];

                if(iRec.getFactionId().equalsIgnoreCase(jRec.getFactionId()))
                    continue;

                System.out.println(iRec.getFactionId() + " is now 0 with " + jRec.getFactionId());

                iRec.setGameRelationship(jRec.getFactionId(), 0);
                iRec.getFactionAPI().setRelationship(jRec.getFactionId(), 0);
                jRec.setGameRelationship(iRec.getFactionId(), 0);
                jRec.getFactionAPI().setRelationship(iRec.getFactionId(), 0);
            }
        }

		allianceManager.dissolveAlliance(allianceId, records);
	}

	private void joinAlliances(String allianceOneId, String allianceTwoId)
	{
		DiplomacyRecord[] allianceOneFactions = getDiplomacyRecordsForAlliance(allianceOneId);
		DiplomacyRecord[] allianceTwoFactions = getDiplomacyRecordsForAlliance(allianceTwoId);
		AllianceRecord allianceOneRecord = allianceManager.getAllianceRecord(allianceOneId);
		AllianceRecord allianceTwoRecord = allianceManager.getAllianceRecord(allianceTwoId);

		if(playerRecord != null && (allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceOneId) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceTwoId)))
			ExerelinUtilsMessaging.addMessage("Alliances " + allianceOneRecord.getAllianceNameAndFactions() + " and " + allianceTwoRecord.getAllianceNameAndFactions() + " have joined together!", Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage("Alliances " + allianceOneRecord.getAllianceNameAndFactions() + " and " + allianceTwoRecord.getAllianceNameAndFactions() + " have joined together!");

		String combinedAllianceId = allianceManager.createAlliance(allianceOneFactions[0], allianceTwoFactions[0]);
		AllianceRecord combinedAlliance = allianceManager.getAllianceRecord(combinedAllianceId);

		for(int i = 1; i < allianceOneFactions.length; i++)
		{
			allianceOneFactions[i].setAllianceId(combinedAllianceId);
			combinedAlliance.addFactionToAlliance(allianceOneFactions[i].getFactionId());
		}

		for(int i = 1; i < allianceTwoFactions.length; i++)
		{
			allianceTwoFactions[i].setAllianceId(combinedAllianceId);
			combinedAlliance.addFactionToAlliance(allianceTwoFactions[i].getFactionId());
		}

		DiplomacyRecord[] combinedAllianceFactions = getDiplomacyRecordsForAlliance(combinedAllianceId);
		for(int i = 0; i < combinedAllianceFactions.length; i ++)
		{
			DiplomacyRecord rec = combinedAllianceFactions[i];

			for(int j = 0; j < factionRecords.length; j++)
			{
				if(factionRecords[j].getFactionId().equalsIgnoreCase(rec.getFactionId()))
					continue;

				DiplomacyRecord iRec = factionRecords[j];

				float allianceGameRelationship = deriveAllianceGameRelationshipWithFaction(combinedAllianceId, iRec.getFactionId());

				rec.setGameRelationship(iRec.getFactionId(), allianceGameRelationship);
				rec.getFactionAPI().setRelationship(iRec.getFactionId(), allianceGameRelationship);
				iRec.setGameRelationship(rec.getFactionId(), allianceGameRelationship);
				iRec.getFactionAPI().setRelationship(rec.getFactionId(), allianceGameRelationship);
			}
		}

		if(playerRecord != null && (allianceManager.isFactionInAlliance(playerRecord.getFactionId(), combinedAllianceId)))
			ExerelinUtilsMessaging.addMessage(combinedAlliance.getAllianceNameAndFactions() + " has been established as the new alliance!", Color.magenta);
		else
			ExerelinUtilsMessaging.addMessage(combinedAlliance.getAllianceNameAndFactions() + " has been established as the new alliance!");
	}

	private float deriveAllianceSectorOwnership(String allianceId)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);
		String[] allianceFactions = allianceRecord.getFactions();

		float ownership = 0;
		for(int i = 0; i < allianceFactions.length; i++)
			ownership = ownership + SectorManager.getCurrentSectorManager().getSectorOwnership(allianceFactions[i]);

		return ownership;
	}

	public Boolean isFactionAtWarAndHasTarget(String factionId)
	{
		Boolean atWar = false;
		SystemManager[] systemManagers = SectorManager.getCurrentSectorManager().getSystemManagers();
		String[] enemyFactionIds = this.getRecordForFaction(factionId).getEnemyFactions();
		for(int i = 0; i < systemManagers.length; i++)
		{
			if(systemManagers[i].isFactionInSystem(factionId)
					&& systemManagers[i].isOneOfFactionInSystem(enemyFactionIds))
			{
				atWar = true;
				break;
			}
		}

		return atWar;
	}

    public void playerLeaveFaction()
    {
        // Clear influence with faction
        for (DiplomacyRecord record : this.factionRecords)
        {
            if(record.getFactionId().equalsIgnoreCase(Global.getSector().getPlayerFleet().getFaction().getId()))
                record.setPlayerInfluence(-25);
        }

        SectorManager.getCurrentSectorManager().setPlayerFactionId("player");
        Global.getSector().getPlayerFleet().setFaction("player");
        this.playerRecord = null;
    }

    public void playerJoinFaction(String factionId)
    {
        SectorManager.getCurrentSectorManager().setPlayerFactionId(factionId);
        Global.getSector().getPlayerFleet().setFaction(factionId);
        this.playerRecord = this.getRecordForFaction(factionId);

        // Reset influence with factions not joined
        for (DiplomacyRecord record : this.factionRecords)
        {
            if(!record.getFactionId().equalsIgnoreCase(factionId) && record.getPlayerInfluence() > 0)
                record.setPlayerInfluence(0);
        }

        // Reset stations to balanced
        FactionDirector.getFactionDirectorForFactionId(factionId).updateAllStationsToStance(StationRecord.StationFleetStance.BALANCED);
    }

    public void applyInfluenceChangeForWonEncounter(String targetedFaction, int amount, ArrayList<String> factionsInSystem)
    {
        for(DiplomacyRecord diplomacyRecord : this.factionRecords)
        {
            if(targetedFaction.equalsIgnoreCase("pirates")
                    || targetedFaction.equalsIgnoreCase("rebel")
                    || ExerelinConfig.getAllCustomFactionRebels().contains(targetedFaction))
            {
                // Pirate/Rebels destroyed so increase influence with factions resident in system
                if(factionsInSystem.contains(diplomacyRecord.getFactionId()))
                {
                    diplomacyRecord.setPlayerInfluence(diplomacyRecord.getPlayerInfluence() + amount);
                }
            }
            else
            {
                // Check if faction destroyed is enemy of any factions
                if(diplomacyRecord.getEnemyFactionsAsList().contains(targetedFaction))
                {
                    diplomacyRecord.setPlayerInfluence(diplomacyRecord.getPlayerInfluence() + amount);

                    // Double influence gains if player is destroying enemies of faction they are in
                    if(diplomacyRecord.getFactionId().equalsIgnoreCase(SectorManager.getCurrentSectorManager().getPlayerFactionId()))
                        diplomacyRecord.setPlayerInfluence(diplomacyRecord.getPlayerInfluence() + amount);
                }

                // Reduce influence with destroyed faction (half)
                if(diplomacyRecord.getFactionId().equalsIgnoreCase(targetedFaction))
                {
                    diplomacyRecord.setPlayerInfluence(diplomacyRecord.getPlayerInfluence() - amount/2);
                }
            }
        }
    }
}
