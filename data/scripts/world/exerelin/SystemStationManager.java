package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import org.lazywizard.lazylib.MathUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SystemStationManager
{
	private StationRecord[] stationRecords;

	private HashMap stationCount = new HashMap();
	private String leadingFaction;
	private int leadingFactionStationCount;
	private String losingFaction;
	private int losingFactionStationCount;
	private float averageStationDistance;

	private int nextStationRecord = 0;

	public SystemStationManager(SectorAPI sector, StarSystemAPI system)
	{
		List stations = system.getOrbitalStations();
		stationRecords = new StationRecord[stations.size()];
		for(int i = 0; i < stations.size(); i++)
		{
			stationRecords[i] = new StationRecord(sector, system, this, (SectorEntityToken)stations.get(i));
		}
	}

	public StationRecord[] getStationRecords()
	{
		return stationRecords;
	}

	public StationRecord getStationRecordForToken(SectorEntityToken stationToken)
	{
		for(int i = 0; i < stationRecords.length; i++)
		{
			if(stationToken.getFullName().equalsIgnoreCase(stationRecords[i].getStationToken().getFullName()))
			{
				return stationRecords[i];
			}
		}
		return null;
	}

	// Each call will update a station and move the counter to the next station
	public void updateStations()
	{
        if(stationRecords.length == 0)
            return;

		if(nextStationRecord == stationRecords.length)
		{
			setFactionStationCount();
			nextStationRecord = 0;
		}

		if(stationRecords[nextStationRecord].getOwner() != null)
		{
			stationRecords[nextStationRecord].increaseResources();
			stationRecords[nextStationRecord].spawnFleets();
			stationRecords[nextStationRecord].checkForPlayerItems();
		}

		nextStationRecord++;
	}

	public float getStationOwnershipPercent(String factionId)
	{
		if(stationCount != null && doesFactionOwnStation(factionId) && stationCount.containsKey(factionId))
		{
			Integer count = Integer.parseInt((String)stationCount.get(factionId));
			return (float)count / (float)stationRecords.length;
		}
		else
			return 0f;
	}

	public int getNumStationsOwnedByFaction(String factionId)
	{
		if(stationCount != null && doesFactionOwnStation(factionId) && stationCount.containsKey(factionId))
		{
			Integer count = Integer.parseInt((String)stationCount.get(factionId));
			return count;
		}
		else
			return 0;
	}

	public float getStationOwnershipDifferenceFromLeader(String factionId)
	{
		if(stationCount != null && doesFactionOwnStation(factionId))
		{
			Integer count = Integer.parseInt((String)stationCount.get(factionId));
			return count / leadingFactionStationCount;
		}
		else
			return 0f;
	}

	public float getStationOwnershipDifferenceFromLoser(String factionId)
	{
		if(stationCount != null && doesFactionOwnStation(factionId))
		{
			Integer count = Integer.parseInt((String)stationCount.get(factionId));
			return count / losingFactionStationCount;
		}
		else
			return 0f;
	}

	public float getDistanceBetweenFactionsRelativeToAverage (String factionId1, String factionId2)
	{
		if(stationCount != null) //averageStationDistance got calculated
		{
			float totalStationsDistance = 0.f;
			int numDistances = 0;

			for(int i = 0; i < stationRecords.length; i++)
			{
				StationRecord record = stationRecords[i];

				if(record.getOwner() == null || !record.getOwner().getFactionId().equalsIgnoreCase(factionId1))
					continue;

				for(int j = 0; j < stationRecords.length; j++) {
					StationRecord record2 = stationRecords[j];

					if (record2.getOwner() == null || !record2.getOwner().getFactionId().equalsIgnoreCase(factionId2))
						continue;

					totalStationsDistance += MathUtils.getDistance(record.getStationToken(), record2.getStationToken());
					numDistances++;
				}
			}

			if (numDistances == 0)
				return 0f;

			float factionDistance = totalStationsDistance / numDistances;

			return (factionDistance - averageStationDistance) / averageStationDistance;
		}
		else
			return 0f;
	}

	public Boolean doesFactionOwnStation(String factionId)
	{
		for(int i = 0; i < stationRecords.length; i = i + 1)
		{
			StationRecord record = stationRecords[i];

			if(record.getOwner() == null)
				continue;

			if(record.getOwner().getFactionId().equalsIgnoreCase(factionId))
				return true;
		}
		return false;
	}

	public String getFactionLeader()
	{
		return leadingFaction;
	}

	public String getFactionLoser()
	{
		return losingFaction;
	}

	public int getNumFactionsInSystem()
	{
		return stationCount.size();
	}

	private void setFactionStationCount()
	{
		HashMap map = new HashMap();
		String firstFaction = "";
		int firstNumStations = 0;
		String lastFaction = "";
		int lastNumStations = 600;
		float totalStationsDistance = 0.f;
		int numDistances = 0;

		for(int i = 0; i < stationRecords.length; i = i + 1)
		{
			StationRecord record = stationRecords[i];

			if(record.getOwner() == null)
				continue;

			String owner = record.getOwner().getFactionId();

			if(map.containsKey(owner))
			{
				Integer count = Integer.parseInt((String)map.get(owner));
				count = count + 1;
				if(count > firstNumStations)
				{
					firstNumStations = count;
					firstFaction = owner;
				}
				map.remove(owner);
				map.put(owner, count.toString());
			}
			else
			{
				map.put(owner, "1");
				if(1 > firstNumStations)
				{
					firstNumStations = 1;
					firstFaction = owner;
				}
			}

			//calculate distances
			for(int j = i + 1; j < stationRecords.length; j++)
			{
				StationRecord record2 = stationRecords[j];

				if (record2.getOwner() == null || record2.getOwner().getFactionId().equalsIgnoreCase(owner))
					continue;

				totalStationsDistance += MathUtils.getDistance(record.getStationToken(), record2.getStationToken());
				numDistances++;
			}
		}

		stationCount = map;

		for(int j = 0; j < stationRecords.length; j = j + 1)
		{
			StationRecord record = stationRecords[j];

			if(record.getOwner() == null)
				continue;

			int stationCount = this.getNumStationsOwnedByFaction(record.getOwner().getFactionId());

			if(stationCount < lastNumStations)
			{
				lastFaction = record.getOwner().getFactionId();
				lastNumStations = stationCount;
			}

		}

		leadingFaction = firstFaction;
		leadingFactionStationCount = firstNumStations;
		losingFaction = lastFaction;
		losingFactionStationCount = lastNumStations;
		averageStationDistance = ((numDistances > 0) ? totalStationsDistance / numDistances : 1.f);
	}

	public String[] getFactionsOwningStations()
	{
		ArrayList foundFactions = new ArrayList(stationRecords.length);

		for(int i = 0; i < stationRecords.length; i = i + 1)
		{
			if(stationRecords[i].getOwner() == null)
				continue;

			String stationFactionId = stationRecords[i].getOwner().getFactionId();

			boolean alreadyFound = false;
			for(int j = 0; j < foundFactions.size(); j = j + 1)
			{
				if(((String)foundFactions.get(j)).equalsIgnoreCase(stationFactionId))
					alreadyFound = true;
			}
			if(!alreadyFound)
				foundFactions.add(stationFactionId);

		}

		return (String[])foundFactions.toArray( new String[foundFactions.size()] );
	}

    // Check if x,y coordinates correspond to a station managed by this station manager and return the station record
    public StationRecord getStationRecordForXY(float x, float y, float maxOffset)
    {
        float xMax = x + maxOffset;
        float xMin = x - maxOffset;
        float yMax = y + maxOffset;
        float yMin = y - maxOffset;

        for(int i = 0; i < stationRecords.length; i++)
        {
            float stationX = stationRecords[i].getStationToken().getLocation().getX();
            float stationY = stationRecords[i].getStationToken().getLocation().getY();

            if(stationX > xMin
                    && stationX < xMax
                    && stationY > yMin
                    && stationY < yMax)
            {
                return stationRecords[i];
            }
        }

        return null;
    }

    // Return the faction id of a station at a location
    public SectorEntityToken getStationTokenForXY(float x, float y, float maxOffset)
    {
        StationRecord stationRecord = this.getStationRecordForXY(x, y, maxOffset);

        if(stationRecord == null)
            return null;
        else
        {
            return stationRecord.getStationToken();
        }
    }

    public void setStationOwner(SectorEntityToken station, String newOwnerFactionId, Boolean displayMessage, Boolean updateRelationship)
    {
        this.getStationRecordForToken(station).setOwner(newOwnerFactionId, displayMessage, updateRelationship);
    }
}
