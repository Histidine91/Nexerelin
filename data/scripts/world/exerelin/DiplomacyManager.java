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
	private DiplomacyRecord[] factionRecords;
	private SectorAPI sector;

	private final int warLevel = -20;
	private final int allyLevel = 20;
	private final int peaceTreatyLevel = -10;
	private final int endAllianceLevel = 10;

	public DiplomacyRecord playerRecord;

	// Variables used to manage single update per day
	public int nextRecordToUpdate = 0;
	String factionIdFirst;
	String factionIdLast;

	public DiplomacyManager(SectorAPI sectorValue)
	{
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

			float gameRelationship = f1API.getRelationship(f2API.getId());

			// Check what we had stored for game relationship
			if(factionRecords[j].checkForBetray(f1API.getId(), gameRelationship))
			{
				if(factionRecords[j].getGameRelationship(f1API.getId()) >= 1)
				{
					System.out.println(ExerelinData.getInstance().getPlayerFaction() + " has betrayed " + f2API.getId());
					sector.addMessage(ExerelinData.getInstance().getPlayerFaction() + " has betrayed " + f2API.getId() + " and war has broken out!", Color.magenta);
				}
				else if(factionRecords[j].getGameRelationship(f1API.getId()) >= 0)
				{
					System.out.println(ExerelinData.getInstance().getPlayerFaction() + " has broken treaty with " + f2API.getId());
					sector.addMessage(ExerelinData.getInstance().getPlayerFaction() + " has broken treaty with " + f2API.getId() + " and war has broken out!", Color.magenta);
				}
				factionRecords[j].setFactionRelationship(f1API.getId(), warLevel + warLevel);
				playerRecord.setFactionRelationship(factionRecords[j].getFactionId(), peaceTreatyLevel);

				gameRelationship = -1;
				changeGameRelationship(playerRecord, factionRecords[j], (int)gameRelationship, f1API.getRelationship(f2API.getId()), false);

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

		boolean hasWarTarget = recordToUpdate.hasWarTagetInSystem(false);

		// Update all relationships for this record
		for(int j = 0; j < factionRecords.length; j = j + 1)
		{
			FactionAPI f2API = sector.getFaction(factionRecords[j].getFactionId());

			if(!ExerelinData.getInstance().systemManager.stationManager.doesFactionOwnStation(f2API.getId()))
				continue;

			if(f2API.getId().equalsIgnoreCase(f1API.getId()))
				continue;

			int factionRelationship = recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId());

			// If last, like them, if first, don't like them
			if(factionRecords[j].getFactionId().equalsIgnoreCase(factionIdFirst))
				factionRelationship = factionRelationship - 2;
			else if (factionRecords[j].getFactionId().equalsIgnoreCase(factionIdLast))
				factionRelationship = factionRelationship + 2;

			// Are they beating us or are we beating them
			Float ourPercentage = ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(recordToUpdate.getFactionId());
			Float theirPercentage = ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(factionRecords[j].getFactionId());
			factionRelationship = factionRelationship + (int)((ourPercentage - theirPercentage)*10);

			// Add whatever the current bias is
			int f1API_about_f2API = recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId());
			int bias = 0;
			if(f1API_about_f2API < -5 && f1API_about_f2API > -40)
				bias = -1;
			else if (f1API_about_f2API > 5 && f1API_about_f2API < 40)
				bias = 1;

			// Only add warweariness if we are actually at war with this faction
			if(recordToUpdate.getGameRelationship(factionRecords[j].getFactionId()) < 0)
				factionRelationship = factionRelationship + bias + warweariness;
			else
				factionRelationship = factionRelationship + bias;

			// If no war target, decrease relationships with allies a lot and neutral slightly
			if(!hasWarTarget && recordToUpdate.getGameRelationship(factionRecords[j].getFactionId()) >= 1)
				factionRelationship = factionRelationship - 34;
			else if(!hasWarTarget && recordToUpdate.getGameRelationship(factionRecords[j].getFactionId()) > 0)
				factionRelationship = factionRelationship - 2;

			// Add a bit of random
			int relationshipRandom = ExerelinUtils.getRandomInRange(-2, 2);
			factionRelationship = factionRelationship + relationshipRandom;

			// 2% chance to reset relationship
			if(ExerelinUtils.getRandomInRange(0,49) == 0)
				factionRelationship = 0;

			// Update the relationship setting
			recordToUpdate.setFactionRelationship(factionRecords[j].getFactionId(), factionRelationship);
			//System.out.println(recordToUpdate.getFactionId() + " relationship to " + factionRecords[j].getFactionId() + " = " + recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId()));

			if(recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId()) + factionRecords[j].getFactionRelationship(recordToUpdate.getFactionId()) < warLevel + warLevel && f1API.getRelationship(factionRecords[j].getFactionId()) >= 0)
			{
				// Reset war weariness for factions if they dont have a war
				if(!recordToUpdate.hasWarTagetInSystem(false))
					recordToUpdate.setWarweariness(0);
				if(!factionRecords[j].hasWarTagetInSystem(false))
					factionRecords[j].setWarweariness(0);

				// Declare war
				changeGameRelationship(recordToUpdate, factionRecords[j], -1, f1API.getRelationship(factionRecords[j].getFactionId()), true);

				// Reduce likelihood of war with other factions slightly
				recordToUpdate.bulkAddToFactionRelationships(factionRecords[j].getFactionId(), ExerelinUtils.getRandomInRange(2, 3));
				break;
			}
			else if(recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId()) + factionRecords[j].getFactionRelationship(recordToUpdate.getFactionId()) > allyLevel + allyLevel && f1API.getRelationship(factionRecords[j].getFactionId()) < 1)
			{
				// Start alliance
				changeGameRelationship(recordToUpdate, factionRecords[j], 1, f1API.getRelationship(factionRecords[j].getFactionId()), true);

				// Increase likelihood of war with other factions slightly
				recordToUpdate.bulkAddToFactionRelationships(factionRecords[j].getFactionId(), ExerelinUtils.getRandomInRange(-2, -3));
				break;
			}
			else if(recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId()) + factionRecords[j].getFactionRelationship(recordToUpdate.getFactionId()) > peaceTreatyLevel + peaceTreatyLevel && f1API.getRelationship(factionRecords[j].getFactionId()) < 0)
			{
				// End war
				changeGameRelationship(recordToUpdate, factionRecords[j], 0, f1API.getRelationship(factionRecords[j].getFactionId()), true);
				break;
			}
			else if(recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId()) + factionRecords[j].getFactionRelationship(recordToUpdate.getFactionId()) < endAllianceLevel + endAllianceLevel && f1API.getRelationship(factionRecords[j].getFactionId()) > 0)
			{
				// End alliance
				changeGameRelationship(recordToUpdate, factionRecords[j], 0, f1API.getRelationship(factionRecords[j].getFactionId()), true);
				break;
			}
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

		// 0.5% Chance to betray an ally
		if(allies.length > 0 && ExerelinUtils.getRandomInRange(0,199) == 0)
		{
			ExerelinUtils.shuffleStringArray(allies);
			DiplomacyRecord betrayedAlly = this.getRecordForFaction(allies[0]);
			recordToUpdate.setFactionRelationship(betrayedAlly.getFactionId(), warLevel*3);
			betrayedAlly.setFactionRelationship(recordToUpdate.getFactionId(), warLevel*3);
			changeGameRelationship(recordToUpdate, betrayedAlly, -1, 1, true);
		}
	}

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

	private void updateOtherRelationshipsOnChange(DiplomacyRecord fdr1, DiplomacyRecord fdr2, String change)
	{
		if(change.equalsIgnoreCase("War declared between "))
		{
			for(int i = 0; i < factionRecords.length; i = i + 1)
			{
				if(factionRecords[i].getFactionId().equalsIgnoreCase(fdr1.getFactionId()) || factionRecords[i].getFactionId().equalsIgnoreCase(fdr2.getFactionId()))
					continue;

				// Check if ally of fdr1, and 50% chance and not at war with fdr2 or ally with fdr2 already
				if(factionRecords[i].getGameRelationship(fdr1.getFactionId()) >= 1)
				{
					if(ExerelinUtils.getRandomInRange(0,1) == 0 && fdr2.getGameRelationship(factionRecords[i].getFactionId()) < 1 && fdr2.getGameRelationship(factionRecords[i].getFactionId()) >= 0)
					{
						factionRecords[i].setGameRelationship(fdr2.getFactionId(), -1);
						fdr2.setGameRelationship(factionRecords[i].getFactionId(), -1);
						factionRecords[i].setFactionRelationship(fdr2.getFactionId(), warLevel + warLevel);
						fdr2.setFactionRelationship(factionRecords[i].getFactionId(), warLevel);
						sector.getFaction(factionRecords[i].getFactionId()).setRelationship(fdr2.getFactionId(), -1);
						sector.getFaction(fdr2.getFactionId()).setRelationship(factionRecords[i].getFactionId(), -1);
						printJoinAllyMessage(factionRecords[i].getFactionId(), fdr1.getFactionId(), fdr2.getFactionId());
					}
				}
				else if(factionRecords[i].getGameRelationship(fdr2.getFactionId()) >= 1)
				{
					if(ExerelinUtils.getRandomInRange(0,1) == 0 && fdr1.getGameRelationship(factionRecords[i].getFactionId()) < 1 && fdr1.getGameRelationship(factionRecords[i].getFactionId()) >= 0)
					{
						factionRecords[i].setGameRelationship(fdr1.getFactionId(), -1);
						fdr1.setGameRelationship(factionRecords[i].getFactionId(), -1);
						factionRecords[i].setFactionRelationship(fdr1.getFactionId(), warLevel + warLevel);
						fdr1.setFactionRelationship(factionRecords[i].getFactionId(), warLevel);
						sector.getFaction(factionRecords[i].getFactionId()).setRelationship(fdr1.getFactionId(), -1);
						sector.getFaction(fdr1.getFactionId()).setRelationship(factionRecords[i].getFactionId(), -1);
						printJoinAllyMessage(factionRecords[i].getFactionId(), fdr2.getFactionId(), fdr1.getFactionId());
					}
				}
			}
		}
		else if(change.equalsIgnoreCase("Peace treaty signed between "))
		{
			// Allies upset at peace treaty
			updateAllies(fdr1, -10);
			updateAllies(fdr2, -10);
		}
		else if(change.equalsIgnoreCase("Alliance initiated between "))
		{
			// Other factions jealous of alliance
			fdr1.bulkAddToFactionRelationships(fdr2.getFactionId(), -10);
			fdr2.bulkAddToFactionRelationships(fdr1.getFactionId(), -10);
		}
		else if(change.equalsIgnoreCase("Alliance ended between "))
		{
			// enemies happy at alliance end
			updateEnemies(fdr1, 10) ;
			updateEnemies(fdr2, 10);
		}
		else if(change.equalsIgnoreCase("Alliance betrayal between "))
		{
			// Other allies upset at betraying faction
			updateAllies(fdr1, -30);
		}
		else
		{
			System.out.println("EXERELIN ERROR: Invalid relationship change reason: " + change);
		}
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
		if(this.getRecordForFaction(factionId).hasWarTagetInSystem(false))
			return;

		String leadingFactionId = ExerelinData.getInstance().systemManager.stationManager.getFactionLeader();
		if(leadingFactionId == null || leadingFactionId.equalsIgnoreCase(factionId))
			return;

		DiplomacyRecord faction = this.getRecordForFaction(factionId);
		DiplomacyRecord leadingFaction = this.getRecordForFaction(leadingFactionId);

		if(leadingFaction != null && faction != null)
		{
			faction.setFactionRelationship(leadingFactionId, warLevel + warLevel);
			leadingFaction.setFactionRelationship(faction.getFactionId(), warLevel);
			changeGameRelationship(faction, leadingFaction, -1, faction.getGameRelationship(leadingFactionId), true);
		}
	}

	private void printJoinAllyMessage(String infactionId1, String infactionId2, String infactionId3)
	{
		String factionId1 = infactionId1;
		String factionId2 = infactionId2;
		String factionId3 = infactionId3;
		Boolean playerInvolved = false;

		if(infactionId1.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
		{
			factionId1 = ExerelinData.getInstance().getPlayerFaction();
			playerInvolved = true;
		}
		if(infactionId2.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
		{
			factionId2 = ExerelinData.getInstance().getPlayerFaction();
			playerInvolved = true;
		}
		if(infactionId3.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
		{
			factionId3 = ExerelinData.getInstance().getPlayerFaction();
			playerInvolved = true;
		}

		if(playerInvolved)
			sector.addMessage("   " + factionId1 + " has joined ally " + factionId2 + " in war on " + factionId3 + "!", Color.magenta);
		else
			sector.addMessage("   " + factionId1 + " has joined ally " + factionId2 + " in war on " + factionId3 + "!");
		System.out.println("   " + factionId1 + " has joined ally " + factionId2 + " in war on " + factionId3+ "!") ;
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
	}
}
