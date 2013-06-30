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
					sector.addMessage(ExerelinData.getInstance().getPlayerFaction() + " has betrayed " + f2API.getId() + " and war has broken out!", Color.magenta);
				}
				else if(previousGameRelationship >= 0)
				{
					System.out.println(ExerelinData.getInstance().getPlayerFaction() + " has broken treaty with " + f2API.getId());
					sector.addMessage(ExerelinData.getInstance().getPlayerFaction() + " has broken treaty with " + f2API.getId() + " and war has broken out!", Color.magenta);
				}
				factionRecords[j].setFactionRelationship(f1API.getId(), warLevel + warLevel);
				playerRecord.setFactionRelationship(factionRecords[j].getFactionId(), peaceTreatyLevel);

				currentRelationship = -1;
				changeGameRelationship(playerRecord, factionRecords[j], -1, previousGameRelationship, false);

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

		// Update all relationship levels with all other factions
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
				factionRelationship = factionRelationship - 4;
			else if (factionRecords[j].getFactionId().equalsIgnoreCase(factionIdLast))
				factionRelationship = factionRelationship + 2;

			// Are they beating us or are we beating them
			Float ourPercentage = ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(recordToUpdate.getFactionId());
			Float theirPercentage = ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(factionRecords[j].getFactionId());
			factionRelationship = factionRelationship + (int)((ourPercentage - theirPercentage)*5);

			// Add whatever the current bias is
			int f1API_about_f2API = recordToUpdate.getFactionRelationship(factionRecords[j].getFactionId());
			int bias = 0;
			if(f1API_about_f2API < -10 && f1API_about_f2API > -60)
				bias = -1;
			else if (f1API_about_f2API > 10 && f1API_about_f2API < 60)
				bias = 1;

			// Only add warweariness if we are actually at war with this faction
			if(recordToUpdate.getGameRelationship(factionRecords[j].getFactionId()) < 0)
				factionRelationship = factionRelationship + bias + warweariness;
			else
				factionRelationship = factionRelationship + bias;

			// If no war target, decrease relationships with allies a lot and neutral slightly
			if(!hasWarTarget && recordToUpdate.getGameRelationship(factionRecords[j].getFactionId()) >= 1)
				factionRelationship = factionRelationship - 4;
			else if(!hasWarTarget && recordToUpdate.getGameRelationship(factionRecords[j].getFactionId()) > 0)
				factionRelationship = factionRelationship - 2;

			// Add a bit of random
			int relationshipRandom = ExerelinUtils.getRandomInRange(-2, 2);
			factionRelationship = factionRelationship + relationshipRandom;

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

		// Process any diplomacy changes
		this.updateFactionDiplomacy(recordToUpdate);
	}

	// Apply any changes that need to be made
	private void updateFactionDiplomacy(DiplomacyRecord diplomacyRecord)
	{
		// Work out alliance system ownership
		float allySystemOwnership = 0f;
		String[] allies = diplomacyRecord.getAlliedFactions();
		for(int j = 0; j< allies.length; j++)
		{
			allySystemOwnership = allySystemOwnership + ExerelinData.getInstance().systemManager.stationManager.getStationOwnershipPercent(allies[j]);
		}

		for(int i = 0; i < this.factionRecords.length; i++)
		{
			DiplomacyRecord otherDiplomacyRecord = this.factionRecords[i];

			if(diplomacyRecord.getFactionId().equalsIgnoreCase(otherDiplomacyRecord.getFactionId()))
				continue;

			if(diplomacyRecord.getFactionRelationship(otherDiplomacyRecord.getFactionId()) + otherDiplomacyRecord.getFactionRelationship(diplomacyRecord.getFactionId()) < warLevel + warLevel && diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()) >= 0)
			{
				// Reset war weariness for factions if they dont have a war
				if(!diplomacyRecord.hasWarTagetInSystem(false))
					diplomacyRecord.setWarweariness(0);
				if(!otherDiplomacyRecord.hasWarTagetInSystem(false))
					otherDiplomacyRecord.setWarweariness(0);

				// Declare war
				changeGameRelationship(diplomacyRecord, otherDiplomacyRecord, -1, diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()), true);

				// Reduce likelihood of war with other factions slightly
				diplomacyRecord.bulkAddToFactionRelationships(otherDiplomacyRecord.getFactionId(), ExerelinUtils.getRandomInRange(2, 3));
				break;
			}
			else if(diplomacyRecord.getFactionRelationship(otherDiplomacyRecord.getFactionId()) + otherDiplomacyRecord.getFactionRelationship(diplomacyRecord.getFactionId()) > allyLevel + allyLevel && diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()) < 1)
			{
				// Start alliance
				changeGameRelationship(diplomacyRecord, otherDiplomacyRecord, 1, diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()), true);

				// Increase likelihood of war with other factions slightly
				diplomacyRecord.bulkAddToFactionRelationships(otherDiplomacyRecord.getFactionId(), ExerelinUtils.getRandomInRange(-2, -3));
				break;
			}
			else if(diplomacyRecord.getFactionRelationship(otherDiplomacyRecord.getFactionId()) + otherDiplomacyRecord.getFactionRelationship(diplomacyRecord.getFactionId()) > peaceTreatyLevel + peaceTreatyLevel && diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()) < 0)
			{
				// End war
				changeGameRelationship(diplomacyRecord, otherDiplomacyRecord, 0, diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()), true);
				break;
			}
			else if(diplomacyRecord.getFactionRelationship(otherDiplomacyRecord.getFactionId()) + otherDiplomacyRecord.getFactionRelationship(diplomacyRecord.getFactionId()) < endAllianceLevel + endAllianceLevel && diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()) > 0)
			{
				// End alliance
				changeGameRelationship(diplomacyRecord, otherDiplomacyRecord, 0, diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()), true);
				break;
			}
			else if(diplomacyRecord.getGameRelationship(otherDiplomacyRecord.getFactionId()) >= 1 && ExerelinUtils.getRandomInRange(0,9)*allySystemOwnership > 2)
			{
				// Betray an ally
				diplomacyRecord.setFactionRelationship(otherDiplomacyRecord.getFactionId(), warLevel*3);
				otherDiplomacyRecord.setFactionRelationship(diplomacyRecord.getFactionId(), warLevel*3);
				changeGameRelationship(diplomacyRecord, otherDiplomacyRecord, -1, 1, true);
				break;
			}
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
			// ????
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

	public void setGameAndFactionRelationships(DiplomacyRecord fdr1, DiplomacyRecord fdr2, int factionRelationshipValue, float gameRelationshipValue)
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
	}
}
