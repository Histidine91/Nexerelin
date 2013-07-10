package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import java.awt.*;

/* 	This class manages faction relationships for Exerelin.

	The updateRelationships method should be run each day. It will
	pick the next faction to update and repeat in a cycle.
 */

public class DiplomacyManager
{
	private AllianceManager allianceManager;
	private DiplomacyRecord[] factionRecords;
	private SectorAPI sector;

	private final int warLevel = -40;
	private final int allyLevel = 40;
	private final int peaceTreatyLevel = -20;
	private final int endAllianceLevel = 20;

	public DiplomacyRecord playerRecord;

	// Variables used to manage single update per day
	public int nextRecordToUpdate = 0;
	String factionIdFirst;
	String factionIdLast;

	public DiplomacyManager(SectorAPI sectorValue)
	{
		allianceManager = new AllianceManager();

		sector = sectorValue;

		String [] availableFactions = ExerelinData.getInstance().getAvailableFactions(sector);
		factionRecords = new DiplomacyRecord[availableFactions.length];

		// Build a record for each available faction
		for (int i = 0; i < factionRecords.length; i = i + 1)
		{
			factionRecords[i] = new DiplomacyRecord(sector, availableFactions[i], availableFactions);
			if(availableFactions[i].equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				playerRecord = factionRecords[i];
		}
	}

	// Check if player has betrayed a faction since last check
	public void checkBetrayal()
	{
		FactionAPI f1API = sector.getFaction(playerRecord.getFactionId());

		// Check for betrayal of each faction
		for(int j = 0; j < factionRecords.length; j = j + 1)
		{
			FactionAPI f2API = sector.getFaction(factionRecords[j].getFactionId());

			if(f2API.getId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
				continue;

			float currentRelationship = f1API.getRelationship(f2API.getId());

			// Check what we had stored for game relationship
			if(factionRecords[j].checkForBetray(f1API.getId(), currentRelationship))
			{
				float previousGameRelationship = factionRecords[j].getGameRelationship(f1API.getId());

				if(previousGameRelationship >= 1)
				{
					System.out.println(ExerelinData.getInstance().getPlayerFaction() + " has betrayed " + f2API.getId());
					sector.addMessage(ExerelinData.getInstance().getPlayerFaction() + " has betrayed " + f2API.getId(), Color.magenta);
				}
				else if(previousGameRelationship >= 0)
				{
					System.out.println(ExerelinData.getInstance().getPlayerFaction() + " has broken treaty with " + f2API.getId());
					sector.addMessage(ExerelinData.getInstance().getPlayerFaction() + " has broken treaty with " + f2API.getId(), Color.magenta);
				}

				if(playerRecord.isInAlliance() && factionRecords[j].isInAlliance())
				{
					// War between players alliance and alliance
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

							playerAllianceFactionRecord.setFactionRelationship(otherAllianceFactionRecord.getFactionId(), warLevel);
							otherAllianceFactionRecord.setFactionRelationship(playerAllianceFactionRecord.getFactionId(), warLevel*2);
						}
					}
				}
				else if(factionRecords[j].isInAlliance())
				{
					// War between player and alliance
					declareWarOrPeaceBetweenFactionAndAlliance(playerRecord.getFactionId(), factionRecords[j].getAllianceId(), -1);

					DiplomacyRecord[] records = getDiplomacyRecordsForAlliance(factionRecords[j].getAllianceId());
					for(int i = 0; i < records.length; i++)
					{
						DiplomacyRecord allianceFactionRecord = records[i];
						allianceFactionRecord.setFactionRelationship(playerRecord.getFactionId(), warLevel);
						playerRecord.setFactionRelationship(allianceFactionRecord.getFactionId(), warLevel*2);
					}
				}
				else
				{
					// War between player and faction
					factionRecords[j].setFactionRelationship(f1API.getId(), warLevel + warLevel);
					playerRecord.setFactionRelationship(factionRecords[j].getFactionId(), warLevel);
					declareFactionWarOrPeace(playerRecord, factionRecords[j], -1);
				}



				// Increase players relationship with betrayed factions enemies
				String[] enemies = factionRecords[j].getEnemyFactions();
				for(int i = 0; i < enemies.length; i++)
				{
					DiplomacyRecord enemyOfBetrayed = this.getRecordForFaction(enemies[i]);
					if(!enemyOfBetrayed.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
						enemyOfBetrayed.addToFactionRelationship(ExerelinData.getInstance().getPlayerFaction(), 20);
				}

				// Decrease players relationship with betrayed factions allies
				String[] allies = factionRecords[j].getAlliedFactions();
				for(int i = 0; i < allies.length; i++)
				{
					DiplomacyRecord allyOfBetrayed = this.getRecordForFaction(enemies[i]);
					if(!allyOfBetrayed.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
						allyOfBetrayed.addToFactionRelationship(ExerelinData.getInstance().getPlayerFaction(), -20);
				}

				// Decrease players relationship with factions neutral to betrayed
				for(int k = 0; k < this.factionRecords.length; k++)
				{
					Boolean allyOrEnemy = false;
					DiplomacyRecord record = this.factionRecords[k];

					if(record.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()) || record.getFactionId().equalsIgnoreCase(factionRecords[j].getFactionId()))
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

					record.addToFactionRelationship(ExerelinData.getInstance().getPlayerFaction(), -10);
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
			factionIdFirst = ExerelinData.getInstance().systemManager.stationManager.getFactionLeader();
			factionIdLast = ExerelinData.getInstance().systemManager.stationManager.getFactionLoser();
			System.out.println("First: " + factionIdFirst + ", Last: " + factionIdLast);
		}

		if(nextRecordToUpdate == factionRecords.length - 1)
			nextRecordToUpdate = 0;
		else
			nextRecordToUpdate = nextRecordToUpdate + 1;

		if(!ExerelinData.getInstance().systemManager.stationManager.doesFactionOwnStation(recordToUpdate.getFactionId()))
			return;

		FactionAPI f1API = sector.getFaction(recordToUpdate.getFactionId());

		int warweariness = recordToUpdate.updateWarWeariness();
		if(warweariness > 1)
			warweariness = 1;

		boolean hasWarTarget = recordToUpdate.hasWarTargetInSystem(false);

		// Update all relationship levels with all other factions
		for(int j = 0; j < factionRecords.length; j = j + 1)
		{
			FactionAPI f2API = sector.getFaction(factionRecords[j].getFactionId());

			if(!ExerelinData.getInstance().systemManager.stationManager.doesFactionOwnStation(f2API.getId()))
				continue;

			if(f2API.getId().equalsIgnoreCase(f1API.getId()))
				continue;

			int factionRelationship = recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId());
			float gameRelationship  = recordToUpdate.getGameRelationship(factionRecords[j].getFactionId());

			// If last, like them, if first, don't like them
			if(factionRecords[j].getFactionId().equalsIgnoreCase(factionIdFirst))
				factionRelationship = factionRelationship - 4;
			else if (factionRecords[j].getFactionId().equalsIgnoreCase(factionIdLast))
				factionRelationship = factionRelationship + 2;

			// Are they beating us or are we beating them
			Float ourPercentage = ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(recordToUpdate.getFactionId());
			Float theirPercentage = ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(factionRecords[j].getFactionId());
			factionRelationship = factionRelationship + (int)((ourPercentage - theirPercentage)*5);

			// If far, like them, if close, don't like them (we want their stations!)
			float factionDistanceDiffFromAverage = ExerelinData.getInstance().systemManager.stationManager.getDistanceBetweenFactionsRelativeToAverage(recordToUpdate.getFactionId(), factionRecords[j].getFactionId());

			int relationshipChange = ExerelinUtils.getRandomNearestInteger( factionDistanceDiffFromAverage * 2.0f );
			if (gameRelationship > 0) { // if they are ally, like them when close
				relationshipChange = Math.abs(relationshipChange); //we want strong and nearby ally
			}

			factionRelationship += relationshipChange;

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

			// If no war target, decrease relationships with allies a lot and neutral slightly
			if(!hasWarTarget && gameRelationship >= 1)
				factionRelationship = factionRelationship - 4;
			else if(!hasWarTarget && gameRelationship > 0)
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
			float allianceOwnership = deriveAllianceSystemOwnership(recordToUpdate.getAllianceId());

			for(int i = 0; i < allies.length; i++)
				recordToUpdate.addToFactionRelationship(allies[i], (int)(allianceOwnership * -6));

		}

		// Process any diplomacy changes
		this.updateFactionDiplomacy(recordToUpdate);
	}

	// Apply any war/peace/alliance changes that need to be made
	private void updateFactionDiplomacy(DiplomacyRecord diplomacyRecord)
	{
		for(int i = 0; i < this.factionRecords.length; i++)
		{
			DiplomacyRecord otherDiplomacyRecord = this.factionRecords[i];

			if(diplomacyRecord.getFactionId().equalsIgnoreCase(otherDiplomacyRecord.getFactionId()))
				continue;

			if(!ExerelinData.getInstance().systemManager.stationManager.doesFactionOwnStation(otherDiplomacyRecord.getFactionId()))
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

				if(rel1 + rel2 < warLevel + warLevel && currentGameRelationship >= 0)
				{
					// Declare war
					declareFactionWarOrPeace(diplomacyRecord,  otherDiplomacyRecord, -1);

					// Reset war weariness for factions if they dont have a war
					if(!diplomacyRecord.hasWarTargetInSystem(false))
						diplomacyRecord.setWarweariness(0);
					if(!otherDiplomacyRecord.hasWarTargetInSystem(false))
						otherDiplomacyRecord.setWarweariness(0);

					// Reduce likelihood of war with other factions slightly
					diplomacyRecord.bulkAddToFactionRelationships(otherDiplomacyRecord.getFactionId(), ExerelinUtils.getRandomInRange(2, 3));
					break;
				}
				else if(rel1 + rel2 > allyLevel + allyLevel && currentGameRelationship < 1)
				{
					// Start alliance
					startAlliance(diplomacyRecord,  otherDiplomacyRecord);

					// Increase likelihood of war with other factions slightly
					diplomacyRecord.bulkAddToFactionRelationships(otherDiplomacyRecord.getFactionId(), ExerelinUtils.getRandomInRange(-5, -8));
					break;
				}
				else if(rel1 + rel2 > peaceTreatyLevel + peaceTreatyLevel && currentGameRelationship < 0)
				{
					// End war
					declareFactionWarOrPeace(diplomacyRecord,  otherDiplomacyRecord, 0);
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
				float allySystemOwnership = 0f;
				String[] allies = allianceManager.getAllianceRecord(diplomacyRecord.getAllianceId()).getFactions();
				for(int j = 0; j< allies.length; j++)
					allySystemOwnership = allySystemOwnership + ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(allies[j]);

				if(rel1 + rel2 < endAllianceLevel + endAllianceLevel)
				{
					// End alliance
					dissolveAlliance(diplomacyRecord.getAllianceId(), getDiplomacyRecordsForAlliance(diplomacyRecord.getAllianceId()));
					break;
				}
				else if(ExerelinUtils.getRandomInRange(0,9)*allySystemOwnership > 2)
				{
					// Betray alliance
					allianceManager.dissolveAlliance(diplomacyRecord.getAllianceId(), getDiplomacyRecordsForAlliance(diplomacyRecord.getAllianceId()));
					diplomacyRecord.setFactionRelationship(otherDiplomacyRecord.getFactionId(), warLevel*3);
					otherDiplomacyRecord.setFactionRelationship(diplomacyRecord.getFactionId(), warLevel*3);
					declareFactionWarOrPeace(diplomacyRecord,  otherDiplomacyRecord, -1);
					break;
				}
			}
			else if(diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance()
					&& diplomacyRecord.getAllianceId().equalsIgnoreCase(otherDiplomacyRecord.getAllianceId()))
			{
				// Same alliance with more than two members

				// Work out alliance system ownership
				float allySystemOwnership = 0f;
				String[] allies = allianceManager.getAllianceRecord(diplomacyRecord.getAllianceId()).getFactions();
				for(int j = 0; j< allies.length; j++)
					allySystemOwnership = allySystemOwnership + ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(allies[j]);

				if(rel1 + rel2 < endAllianceLevel + endAllianceLevel)
				{
					// Leave alliance
					removeFactionFromAlliance(diplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId(), false);
					break;
				}
				else if(ExerelinUtils.getRandomInRange(0,9)*allySystemOwnership > 2)
				{
					// Betray the alliance
					removeFactionFromAlliance(diplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId(), true);
					break;
				}
			}
			else if(diplomacyRecord.isInAlliance()
					&& otherDiplomacyRecord.isInAlliance())
			{
				// Different alliances

				if(rel1 + rel2 < warLevel + warLevel && currentGameRelationship >= 0)
				{
					// Declare war between alliances
					declareAllianceWarOrPeace(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getAllianceId(), -1);
					break;
				}
				else if(rel1 + rel2 > peaceTreatyLevel + peaceTreatyLevel && currentGameRelationship < 0)
				{
					// End war between alliances
					declareAllianceWarOrPeace(diplomacyRecord.getAllianceId(), otherDiplomacyRecord.getAllianceId(), 0);
					break;
				}
				else if(rel1 + rel2 > allyLevel + allyLevel && currentGameRelationship < 1)
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

				if(rel1 + rel2 < warLevel + warLevel && currentGameRelationship >= 0)
				{
					// Declare war between alliance and faction
					declareWarOrPeaceBetweenFactionAndAlliance(otherDiplomacyRecord.getFactionId(), diplomacyRecord.getAllianceId(), -1);
					break;
				}
				else if(rel1 + rel2 > peaceTreatyLevel + peaceTreatyLevel && currentGameRelationship < 0)
				{
					// End war between alliance and faction
					declareWarOrPeaceBetweenFactionAndAlliance(otherDiplomacyRecord.getFactionId(), diplomacyRecord.getAllianceId(), 0);
					break;
				}
				else if(rel1 + rel2 > allyLevel + allyLevel && currentGameRelationship < 1)
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

				if(rel1 + rel2 < warLevel + warLevel && currentGameRelationship >= 0)
				{
					// Declare war between faction and alliance
					declareWarOrPeaceBetweenFactionAndAlliance(diplomacyRecord.getFactionId(), otherDiplomacyRecord.getAllianceId(), -1);
					break;
				}
				else if(rel1 + rel2 > peaceTreatyLevel + peaceTreatyLevel && currentGameRelationship < 0)
				{
					// End war between faction and alliance
					declareWarOrPeaceBetweenFactionAndAlliance(diplomacyRecord.getFactionId(), otherDiplomacyRecord.getAllianceId(), 0);
					break;
				}
				else if(rel1 + rel2 > allyLevel + allyLevel && currentGameRelationship < 1)
				{
					// Join alliance
					addFactionToAlliance(otherDiplomacyRecord.getAllianceId(), diplomacyRecord.getFactionId());
					break;
				}
			}
		}
	}

	// UNUSED
	private void changeGameRelationship(DiplomacyRecord fdr1, DiplomacyRecord fdr2, int value, float previousRelationship, boolean displayMessage)
	{
		fdr1.getFactionAPI().setRelationship(fdr2.getFactionId(), value);
		fdr2.getFactionAPI().setRelationship(fdr1.getFactionId(), value);
		fdr1.setGameRelationship(fdr2.getFactionId(), value);
		fdr2.setGameRelationship(fdr1.getFactionId(), value);

		String initialMessage = "";

		if(previousRelationship >= 0 && value < 0 && previousRelationship < 1)
			initialMessage = "War declared between ";
		else if(previousRelationship < 1 && value >= 1)
			initialMessage = "Alliance initiated between ";
		else if(previousRelationship >= 1 && value < 1 && value >= 0)
			initialMessage = "Alliance ended between ";
		else if(previousRelationship <= -1 && value >= 0)
			initialMessage = "Peace treaty signed between ";
		else if(previousRelationship >= 1 && value < 0)
			initialMessage = "Alliance betrayal between ";

		String faction1Id = "";
		String faction2Id = "";
		Boolean playerInvolved = false;

		if(fdr1.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
		{
			faction1Id = ExerelinData.getInstance().getPlayerFaction();
			playerInvolved = true;
		}
		else
			faction1Id = fdr1.getFactionId();

		if(fdr2.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
		{
			faction2Id = ExerelinData.getInstance().getPlayerFaction();
			playerInvolved = true;
		}
		else
			faction2Id = fdr2.getFactionId();

		if(displayMessage)
		{
			if(playerInvolved)
				sector.addMessage(initialMessage + faction1Id + " and " + faction2Id, Color.magenta);
			else
				sector.addMessage(initialMessage + faction1Id + " and " + faction2Id);
			System.out.println(initialMessage + faction1Id + " and " + faction2Id);
		}

		updateOtherRelationshipsOnChange(fdr1, fdr2, initialMessage);
	}

	// UNUSED
	private void updateOtherRelationshipsOnChange(DiplomacyRecord fdr1, DiplomacyRecord fdr2, String change)
	{
		if(change.equalsIgnoreCase("War declared between "))
		{
			for(int i = 0; i < factionRecords.length; i = i + 1)
			{
				if(factionRecords[i].getFactionId().equalsIgnoreCase(fdr1.getFactionId()) || factionRecords[i].getFactionId().equalsIgnoreCase(fdr2.getFactionId()))
					continue;

				// Ally of both so go with best relationship
				if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) >= 1 && factionRecords[i].getGameRelationship(fdr2.getFactionId()) >= 1 )
				{
					if(factionRecords[i].getFactionRelationship(fdr1.getFactionId()) > factionRecords[i].getFactionRelationship(fdr2.getFactionId()))
					{
						// Keep ally with fdr1 and declare war with fdr2
						if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) != -1)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr2, warLevel+(warLevel/2), -1);
							printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have joined ally %s in war against previous ally %s!");
						}
					}
					else
					{
						// Keep ally with fdr2 and declare war with fdr1
						if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) != -1)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr1, warLevel+(warLevel/2), -1);
							printAllyMessage(factionRecords[i].getFactionId(), fdr2.getFactionId(), fdr1.getFactionId(), "    %s have joined ally %s in war against previous ally %s!");
						}
					}
				}
				else if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) >= 1)
				{
					// Ally of fdr1 so join them in war on fdr2
					if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) != -1)
					{
						setGameAndFactionRelationships(factionRecords[i], fdr2, warLevel+(warLevel/2), -1);
						printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have joined ally %s in war with %s!");
					}
				}
				else if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) >= 1)
				{
					// Ally of fdr2 so join them in war in war on fdr1
					if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) != -1)
					{
						setGameAndFactionRelationships(factionRecords[i], fdr1, warLevel+(warLevel/2), -1);
						printAllyMessage(factionRecords[i].getFactionId(), fdr2.getFactionId(), fdr1.getFactionId(), "    %s have joined ally %s in war with %s!");
					}
				}
			}
		}
		else if(change.equalsIgnoreCase("Peace treaty signed between "))
		{
			for(int i = 0; i < factionRecords.length; i = i + 1)
			{
				if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) >= 1)
				{
					// ally of fdr1
					if(factionRecords[i].getFactionRelationship(fdr1.getFactionId()) > factionRecords[i].getFactionRelationship(fdr2.getFactionId()) * -1)
					{
						// likes fdr1 more than hates fdr2 so declare grudging peace with fdr2
						if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) != 0)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr2, peaceTreatyLevel, 0);
							printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have joined ally %s in peace treaty with %s");
						}
					}
					else
					{
						// hates fdr2 more than likes fdr1 so cancel alliance with fdr1
						if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) != 0)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr1, 0, 0);
							printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have cancelled their alliance with %s as a result of their peace treaty with %s!");
						}
					}
				}
				else if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) >= 1)
				{
					// ally of fdr2
					if(factionRecords[i].getFactionRelationship(fdr2.getFactionId()) > factionRecords[i].getFactionRelationship(fdr1.getFactionId()) * -1)
					{
						// likes fdr2 more than hates fdr1 so declare grudging peace with fdr1
						if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) != 0)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr1, peaceTreatyLevel, 0);
							printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have joined ally %s in peace treaty with %s");
						}
					}
					else
					{
						// hates fdr1 more than likes fdr2 so cancel alliance with fdr2
						if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) != 0)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr2, 0, 0);
							printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have cancelled their alliance with %s as a result of their peace treaty with %s!");
						}
					}
				}
			}
		}
		else if(change.equalsIgnoreCase("Alliance initiated between "))
		{
			// TODO: Needs to join alliance

			for(int i = 0; i < factionRecords.length; i = i + 1)
			{
				if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) < 0)
				{
					// At war with fdr1 so go to war with fdr2
					if(factionRecords[i].getGameRelationship(fdr2.getFactionId())  >= 0)
					{
						setGameAndFactionRelationships(factionRecords[i], fdr2, warLevel+warLevel/2, -1);
						printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have extended their war with %s to %s!");
					}
				}
				else if (factionRecords[i].getGameRelationship(fdr2.getFactionId()) < 0)
				{
					// At war with fdr2 so go to war with fdr1
					if(factionRecords[i].getGameRelationship(fdr1.getFactionId())  >= 0)
					{
						setGameAndFactionRelationships(factionRecords[i], fdr1, warLevel+warLevel/2, -1);
						printAllyMessage(factionRecords[i].getFactionId(), fdr2.getFactionId(), fdr1.getFactionId(), "    %s have extended their war with %s to %s!");
					}
				}
			}
		}
		else if(change.equalsIgnoreCase("Alliance ended between "))
		{
			// TODO: Needs to be removed from alliance
		}
		else if(change.equalsIgnoreCase("Alliance betrayal between "))
		{
			for(int i = 0; i < factionRecords.length; i = i + 1)
			{
				if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) >= 1 && factionRecords[i].getGameRelationship(fdr2.getFactionId()) >= 1)
				{
					// Allied with both so side with one side
					if(factionRecords[i].getFactionRelationship(fdr1.getFactionId()) > factionRecords[i].getFactionRelationship(fdr2.getFactionId()))
					{
						// likes fdr1 more than fdr2 so side with them
						if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) < 0)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr2, warLevel+warLevel/2, -1);
							printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have sided with %s against %s!");
						}
					}
					else
					{
						// likes fdr2 more than fdr1 so side with them
						if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) < 0)
						{
							setGameAndFactionRelationships(factionRecords[i], fdr1, warLevel+warLevel/2, -1);
							printAllyMessage(factionRecords[i].getFactionId(), fdr2.getFactionId(), fdr1.getFactionId(), "    %s have sided with %s against %s!");
						}
					}
				}
				else if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) >= 1)
				{
					// Allied with fdr1 so go to war with fdr2
					if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) < 0)
					{
						setGameAndFactionRelationships(factionRecords[i], fdr2, warLevel+warLevel/2, -1);
						printAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId(), "    %s have sided with %s against %s!");
					}
				}
				else if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) >= 1)
				{
					// Allied with fdr2 so go to war with fdr1
					if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) < 0)
					{
						setGameAndFactionRelationships(factionRecords[i], fdr1, warLevel+warLevel/2, -1);
						printAllyMessage(factionRecords[i].getFactionId(), fdr2.getFactionId(), fdr1.getFactionId(), "    %s have sided with %s against %s!");
					}
				}
			}
		}
		else
		{
			System.out.println("EXERELIN ERROR: Invalid relationship change reason: " + change);
		}
	}

	// UNUSED
	private void setGameAndFactionRelationships(DiplomacyRecord fdr1, DiplomacyRecord fdr2, int factionRelationshipValue, float gameRelationshipValue)
	{
		fdr1.setGameRelationship(fdr2.getFactionId(), gameRelationshipValue);
		fdr2.setGameRelationship(fdr1.getFactionId(), gameRelationshipValue);
		fdr1.setFactionRelationship(fdr2.getFactionId(), factionRelationshipValue);
		fdr2.setFactionRelationship(fdr1.getFactionId(), factionRelationshipValue);
		sector.getFaction(fdr1.getFactionId()).setRelationship(fdr2.getFactionId(), gameRelationshipValue);
		sector.getFaction(fdr2.getFactionId()).setRelationship(fdr1.getFactionId(), gameRelationshipValue);
	}

	public void updateRelationshipOnEvent(String factionId, String otherFactionId, String event)
	{
		for(int i = 0; i < factionRecords.length; i = i + 1)
		{
			if(factionRecords[i].getFactionId().equalsIgnoreCase(factionId))
			{
				Float rel = factionRecords[i].getGameRelationship(otherFactionId);
				int allyChange = 0;
				int enemyChange = 0;
				Boolean affectsAllies = false;
				Boolean affectsEnemies = false;

				if(event.equalsIgnoreCase("LostStation"))
				{
					rel = rel - 5;
					affectsAllies = true;
					affectsEnemies = true;
					allyChange = -1; // allies perceive weakness
					enemyChange = 10; // Less of a threat

					if(!ExerelinData.getInstance().systemManager.stationManager.doesFactionOwnStation(factionId))
					{
						sector.addMessage(factionId + " has been driven from Exerelin!");
						System.out.println(factionId + " has lost all stations");
						declarePeaceWithAllFactions(factionId);
					}
				}

				System.out.println(event + " for " + factionId + " and " + otherFactionId);
				factionRecords[i].setGameRelationship(otherFactionId, rel);
				if(affectsAllies)
					updateAllies(factionRecords[i], allyChange);
				if(affectsEnemies)
					updateEnemies(factionRecords[i], enemyChange);
			}
		}
	}

	public void createWarIfNoneExists(String factionId)
	{
		if(this.getRecordForFaction(factionId).hasWarTargetInSystem(false))
			return;

		String leadingFactionId = ExerelinData.getInstance().systemManager.stationManager.getFactionLeader();
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
					allianceFactionRecord.setFactionRelationship(faction.getFactionId(), warLevel);
					faction.setFactionRelationship(allianceFactionRecord.getFactionId(), warLevel*2);
				}
			}
			else
			{
				faction.setFactionRelationship(leadingFactionId, warLevel*2);
				leadingFaction.setFactionRelationship(faction.getFactionId(), warLevel);
				declareFactionWarOrPeace(faction, leadingFaction, -1);
			}
		}
	}

	private void printAllyMessage(String infactionId1, String infactionId2, String infactionId3, String initialMessage)
	{
		String[] factions = new String[3];
		factions[0] = infactionId1;
		factions[1] = infactionId2;
		factions[2] = infactionId3;

		// Check if player involved
		Boolean playerInvolved = false;
		if(infactionId1.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			playerInvolved = true;
		else if(infactionId2.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			playerInvolved = true;
		else if(infactionId3.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			playerInvolved = true;

		if(playerInvolved)
			sector.addMessage(String.format(initialMessage,  factions), Color.magenta);
		else
			sector.addMessage(String.format(initialMessage,  factions));
		System.out.println(String.format(initialMessage,  factions)) ;
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
			factionRecords[i].setFactionRelationship(factionId,  0);
			factionRecords[i].getFactionAPI().setRelationship(factionId,  0);
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
			iRec.setGameRelationship(rec.getFactionId(), allianceGameRelationship);
			rec.getFactionAPI().setRelationship(iRec.getFactionId(), allianceGameRelationship);
			iRec.getFactionAPI().setRelationship(rec.getFactionId(), allianceGameRelationship);
		}

		allianceRecord.addFactionToAlliance(factionId);
		rec.setAllianceId(allianceRecord.getAllianceId());

		System.out.println(factionId + " has joined " + allianceRecord.getAllianceNameAndFactions());
		if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			sector.addMessage(factionId + " has joined " + allianceRecord.getAllianceNameAndFactions(), Color.magenta);
		else
			sector.addMessage(factionId + " has joined " + allianceRecord.getAllianceNameAndFactions());
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

				System.out.println(factionId + " has left " + allianceRecord.getAllianceNameAndFactions());
				if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId))
					sector.addMessage(factionId + " has left " + allianceRecord.getAllianceNameAndFactions(), Color.magenta);
				else
					sector.addMessage(factionId + " has left " + allianceRecord.getAllianceNameAndFactions());
			}
		}
		else
		{
			// Remove faction from alliance and declare war with remaining faction in alliance
			DiplomacyRecord[] factionRecords = getDiplomacyRecordsForAlliance(allianceId);

			System.out.println(factionId + " has betrayed " + allianceRecord.getAllianceNameAndFactions() + "!");
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId))
				sector.addMessage(factionId + " has betrayed " + allianceRecord.getAllianceNameAndFactions() + "!", Color.magenta);
			else
				sector.addMessage(factionId + " has betrayed " + allianceRecord.getAllianceNameAndFactions() + "!");

			if(allianceRecord.getNumFactionsInAlliance() == 2)
			{
				dissolveAlliance(allianceRecord.getAllianceId(), factionRecords);

				for(int i = 0; i < factionRecords.length; i++)
				{
					if(factionRecords[i].getFactionId().equalsIgnoreCase(factionId))
						continue;

					declareFactionWarOrPeace(rec, factionRecords[i], -1);
				}
			}
			else
			{
				allianceRecord.removeFactionFromAlliance(factionId);
				declareWarOrPeaceBetweenFactionAndAlliance(factionId, allianceId, -1);
			}
		}
	}

	private void declareFactionWarOrPeace(DiplomacyRecord recordOne, DiplomacyRecord recordTwo, float value)
	{
		recordOne.setGameRelationship(recordTwo.getFactionId(), value);
		recordTwo.setGameRelationship(recordOne.getFactionId(), value);
		recordOne.getFactionAPI().setRelationship(recordTwo.getFactionId(), value);
		recordTwo.getFactionAPI().setRelationship(recordOne.getFactionId(), value);

		String innerMessage = "";
		if(value == -1)
			innerMessage = " has declared war with ";
		else if(value == 0)
			innerMessage = " has signed a peace treaty with ";


		System.out.println(recordOne.getFactionId() + innerMessage + recordTwo.getFactionId());
		if(recordOne.getFactionId().equalsIgnoreCase(playerRecord.getFactionId()) || recordTwo.getFactionId().equalsIgnoreCase(playerRecord.getFactionId()))
			sector.addMessage(recordOne.getFactionId() + innerMessage + recordTwo.getFactionId(), Color.magenta);
		else
			sector.addMessage(recordOne.getFactionId() + innerMessage + recordTwo.getFactionId());

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
				jRec.setGameRelationship(iRec.getFactionId(), value);
				iRec.getFactionAPI().setRelationship(jRec.getFactionId(), value);
				jRec.getFactionAPI().setRelationship(iRec.getFactionId(), value);
			}
		}

		String innerMessage = "";
		if(value == -1)
			innerMessage = " has declared war with ";
		else if(value == 0)
			innerMessage = " has signed a peace treaty with ";

		System.out.println(allianceOneRecord.getAllianceNameAndFactions() + innerMessage + allianceTwoRecord.getAllianceNameAndFactions());
		if(allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceOneId) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceTwoId))
			sector.addMessage(allianceOneRecord.getAllianceNameAndFactions() + innerMessage + allianceTwoRecord.getAllianceNameAndFactions(), Color.magenta);
		else
			sector.addMessage(allianceOneRecord.getAllianceNameAndFactions() + innerMessage + allianceTwoRecord.getAllianceNameAndFactions());
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
			rec.setGameRelationship(iRec.getFactionId(), value);
			iRec.getFactionAPI().setRelationship(rec.getFactionId(), value);
			rec.getFactionAPI().setRelationship(iRec.getFactionId(), value);
		}

		String innerMessage = "";
		if(value == -1)
			innerMessage = " has declared war with ";
		else if(value == 0)
			innerMessage = " has signed a peace treaty with ";

		System.out.println(factionId + innerMessage + allianceRecord.getAllianceNameAndFactions());
		if(factionId.equalsIgnoreCase(playerRecord.getFactionId()) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId))
			sector.addMessage(factionId + innerMessage + allianceRecord.getAllianceNameAndFactions(), Color.magenta);
		else
			sector.addMessage(factionId + innerMessage + allianceRecord.getAllianceNameAndFactions());
	}

	private void startAlliance(DiplomacyRecord rec1, DiplomacyRecord rec2)
	{
		String allianceId = allianceManager.createAlliance(rec1,  rec2);
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
			rec2.setGameRelationship(iRec.getFactionId(), allianceGameRelationship);
			iRec.setGameRelationship(rec1.getFactionId(), allianceGameRelationship);
			iRec.setGameRelationship(rec2.getFactionId(), allianceGameRelationship);
		}

		System.out.println("New alliance " + allianceRecord.getAllianceNameAndFactions() + " has been founded");
		if(rec1.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()) || rec2.getFactionId().equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			sector.addMessage("New alliance " + allianceRecord.getAllianceNameAndFactions() + " has been founded!", Color.magenta);
		else
			sector.addMessage("New alliance " + allianceRecord.getAllianceNameAndFactions() + " has been founded!");

	}

	private void dissolveAlliance(String allianceId, DiplomacyRecord[] records)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);

		System.out.println("Alliance " + allianceRecord.getAllianceNameAndFactions() + " has been dissolved");
		if(allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceId))
			sector.addMessage("Alliance " + allianceRecord.getAllianceNameAndFactions() + " has been dissolved.", Color.magenta);
		else
			sector.addMessage("Alliance " + allianceRecord.getAllianceNameAndFactions() + " has been dissolved.");

		allianceManager.dissolveAlliance(allianceId,  records);
	}

	private void joinAlliances(String allianceOneId, String allianceTwoId)
	{
		DiplomacyRecord[] allianceOneFactions = getDiplomacyRecordsForAlliance(allianceOneId);
		DiplomacyRecord[] allianceTwoFactions = getDiplomacyRecordsForAlliance(allianceTwoId);
		AllianceRecord allianceOneRecord = allianceManager.getAllianceRecord(allianceOneId);
		AllianceRecord allianceTwoRecord = allianceManager.getAllianceRecord(allianceTwoId);

		System.out.println("Alliances " + allianceOneRecord.getAllianceNameAndFactions() + " and " + allianceTwoRecord.getAllianceNameAndFactions() + " have joined together!");
		if(allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceOneId) || allianceManager.isFactionInAlliance(playerRecord.getFactionId(), allianceTwoId))
			sector.addMessage("Alliances " + allianceOneRecord.getAllianceNameAndFactions() + " and " + allianceTwoRecord.getAllianceNameAndFactions() + " have joined together!", Color.magenta);
		else
			sector.addMessage("Alliances " + allianceOneRecord.getAllianceNameAndFactions() + " and " + allianceTwoRecord.getAllianceNameAndFactions() + " have joined together!");

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
				iRec.setGameRelationship(rec.getFactionId(), allianceGameRelationship);
				rec.getFactionAPI().setRelationship(iRec.getFactionId(), allianceGameRelationship);
				iRec.getFactionAPI().setRelationship(rec.getFactionId(), allianceGameRelationship);
			}
		}

		System.out.println(combinedAlliance.getAllianceNameAndFactions() + " has been established as the new alliance!");
		if(allianceManager.isFactionInAlliance(playerRecord.getFactionId(), combinedAllianceId))
			sector.addMessage(combinedAlliance.getAllianceNameAndFactions() + " has been established as the new alliance!", Color.magenta);
		else
			sector.addMessage(combinedAlliance.getAllianceNameAndFactions() + " has been established as the new alliance!");
	}

	private float deriveAllianceSystemOwnership(String allianceId)
	{
		AllianceRecord allianceRecord = allianceManager.getAllianceRecord(allianceId);
		String[] allianceFactions = allianceRecord.getFactions();

		float ownership = 0;
		for(int i = 0; i < allianceFactions.length; i++)
			ownership = ownership + ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(allianceFactions[i]);

		return ownership;
	}
}
