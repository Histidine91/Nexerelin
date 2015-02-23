package exerelin.diplomacy;

/* This class manages the alliances currently in the game.
 */

import exerelin.utilities.ExerelinUtils;

public class AllianceManager
{
	private AllianceRecord[] allianceRecords;

	private final String[] alliancePrefixes = {"northern", "southern", "eastern", "western", "red", "yellow", "blue", "prime", "interstellar", "celestial", "galactic", "outer", "void", "core", "inner"};
	private final String[] allianceSuffixes = {"coalition", "pax", "amalgam", "bloc", "combine", "confederacy", "confederation", "consolidation", "federation", "league", "unification", "union"};

	private final String DELIMETER = "_";

	public AllianceManager()
	{
		allianceRecords = new AllianceRecord[0];
	}



	public String createAlliance(DiplomacyRecord factionOne, DiplomacyRecord factionTwo)
	{
		String newId = createAllianceId();
		AllianceRecord allianceRecord = new AllianceRecord(newId);

		allianceRecord.addFactionToAlliance(factionOne.getFactionId());
		allianceRecord.addFactionToAlliance(factionTwo.getFactionId());
		factionOne.setAllianceId(allianceRecord.getAllianceId());
		factionTwo.setAllianceId(allianceRecord.getAllianceId());

		addAllianceToCollection(allianceRecord);
		return newId;
	}

	public void dissolveAlliance(String allianceId, DiplomacyRecord[] diplomacyRecords)
	{
		AllianceRecord allianceRecord = getAllianceRecord(allianceId);

		if(allianceRecord != null)
		{
			for(int i = 0; i < diplomacyRecords.length; i++)
			{
				allianceRecord.removeFactionFromAlliance(diplomacyRecords[i].getFactionId());
				diplomacyRecords[i].setAllianceId("");
			}

			removeAllianceFromCollection(allianceRecord);
		}
	}

	public AllianceRecord getAllianceRecord(String allianceId)
	{
		for(int i = 0; i < allianceRecords.length; i++)
		{
			if(allianceRecords[i].getAllianceId().equalsIgnoreCase(allianceId))
				return allianceRecords[i];
		}
		return null;
	}

	public boolean isFactionInAlliance(String factionId, String allianceId)
	{
		AllianceRecord allianceRecord = getAllianceRecord(allianceId);
		return allianceRecord.isFactionInAlliance(factionId);
	}



	private void addAllianceToCollection(AllianceRecord record)
	{
		AllianceRecord[] newAllianceRecords = new AllianceRecord[allianceRecords.length + 1];
        System.arraycopy(allianceRecords, 0, newAllianceRecords, 0, allianceRecords.length);

		newAllianceRecords[allianceRecords.length] = record;
		allianceRecords = newAllianceRecords;
	}

	private void removeAllianceFromCollection(AllianceRecord record)
	{
		AllianceRecord[] newAllianceRecords = new AllianceRecord[allianceRecords.length - 1];
		int j = 0;
		for(int i = 0; i < allianceRecords.length; i++)
		{
			if(!allianceRecords[i].getAllianceId().equalsIgnoreCase(record.getAllianceId()))
			{
				newAllianceRecords[j] = allianceRecords[i];
				j++;
			}
		}

		allianceRecords = newAllianceRecords;
	}

	private String createAllianceId()
	{
		String id = "";
		while(id.equalsIgnoreCase(""))
		{
			id = alliancePrefixes[ExerelinUtils.getRandomInRange(0, alliancePrefixes.length - 1)] + DELIMETER + allianceSuffixes[ExerelinUtils.getRandomInRange(0, allianceSuffixes.length - 1)];
			if(!isAllianceIdAvailable(id))
				id = "";
		}

		return id;
	}

	private boolean isAllianceIdAvailable(String allianceId)
	{
		for(int i = 0; i < allianceRecords.length; i++)
		{
			if(allianceRecords[i].getAllianceId().equalsIgnoreCase(allianceId))
				return false;
		}
		return true;
	}
}
