package data.scripts.world.exerelin.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import data.scripts.world.exerelin.utilities.ExerelinConfig;
import data.scripts.world.exerelin.ExerelinUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/*	This class is used to store a finer grained faction relationship
	with other factions.
 */

@SuppressWarnings("unchecked")
public class DiplomacyRecord
{
	private String factionId;
	private HashMap otherFactionRelationships;
	private HashMap gameFactionRelationships;
	private int warWeariness = 0;
	private String[] availableFactions;
	private String allianceId;

	public DiplomacyRecord(String FactionIdValue, String[] InAvailableFactions)
	{
		factionId = FactionIdValue;

		otherFactionRelationships = new HashMap();
		gameFactionRelationships = new HashMap();
		availableFactions = InAvailableFactions;

		allianceId = "";

		// Initialise each other faction level to random
		for(int i = 0; i < availableFactions.length; i = i + 1)
		{
			if(!availableFactions[i].equalsIgnoreCase(factionId))
			{
				otherFactionRelationships.put(availableFactions[i], Integer.toString(ExerelinUtils.getRandomInRange(-50, 50)));
				gameFactionRelationships.put(availableFactions[i], "0");
			}
		}
		warWeariness = ExerelinUtils.getRandomInRange(-5, 0);
	}

	public void setFactionRelationship(String otherFactionId, int relationshipValue)
	{
		otherFactionRelationships.remove(otherFactionId);
		otherFactionRelationships.put(otherFactionId, Integer.toString(relationshipValue));
	}

	public void addToFactionRelationship(String otherFactionId, int relationshipValue)
	{
		int currentRelationship = this.getFactionRelationship(otherFactionId);
		otherFactionRelationships.remove(otherFactionId);
		otherFactionRelationships.put(otherFactionId, Integer.toString(currentRelationship + relationshipValue));
	}

	public void bulkAddToFactionRelationships(String skipFactionId, int relationshipValue)
	{
		for(int i = 0; i < availableFactions.length; i = i + 1)
		{
			if((skipFactionId != null && availableFactions[i].equalsIgnoreCase(skipFactionId)) || availableFactions[i].equalsIgnoreCase(this.getFactionId()))
				continue;

			int currentValue = Integer.parseInt((String)otherFactionRelationships.get(availableFactions[i]));
			otherFactionRelationships.remove(availableFactions[i]);
			otherFactionRelationships.put(availableFactions[i], Integer.toString(currentValue + relationshipValue));
		}
	}

	public void bulkSetFactionRelationships(String skipFactionId, int relationshipValue)
	{
		for(int i = 0; i < availableFactions.length; i = i + 1)
		{
			if((skipFactionId != null && availableFactions[i].equalsIgnoreCase(skipFactionId)) || availableFactions[i].equalsIgnoreCase(this.getFactionId()))
				continue;

			otherFactionRelationships.remove(availableFactions[i]);
			otherFactionRelationships.put(availableFactions[i], Integer.toString(relationshipValue));
		}
	}

	public int getFactionRelationship(String otherFactionId)
	{
		return Integer.parseInt((String) otherFactionRelationships.get(otherFactionId));
	}

	public boolean checkForBetray(String otherFactionId, float value)
	{
        float relationship = Float.parseFloat((String)gameFactionRelationships.get(otherFactionId));
		return (relationship > value && relationship >= 0);
	}

	public void setGameRelationship(String otherFactionId, float value)
	{
		gameFactionRelationships.remove(otherFactionId);
		gameFactionRelationships.put(otherFactionId, Float.toString(value));
	}

	public float getGameRelationship(String otherFactionId)
	{
		if(gameFactionRelationships.containsKey(otherFactionId))
			return Float.parseFloat((String) gameFactionRelationships.get(otherFactionId));
		else
			return 0;
	}

	public void bulkSetGameRelationships(String skipFactionId, float relationshipValue)
	{
		for(int i = 0; i < availableFactions.length; i = i + 1)
		{
			if((skipFactionId != null && availableFactions[i].equalsIgnoreCase(skipFactionId)) || availableFactions[i].equalsIgnoreCase(this.getFactionId()))
				continue;

			gameFactionRelationships.remove(availableFactions[i]);
			gameFactionRelationships.put(availableFactions[i], Float.toString(relationshipValue));
			Global.getSector().getFaction(this.factionId).setRelationship(availableFactions[i], 0);
		}
	}

	public String getFactionId()
	{
		return factionId;
	}

	public Boolean hasWarTargetInSystem(StarSystemAPI starSystemAPI, Boolean includeAbandoned)
	{
        List stations = starSystemAPI.getOrbitalStations();
        for(int i = 0; i < stations.size(); i = i + 1)
        {
            SectorEntityToken station = (SectorEntityToken)stations.get(i);

            if(station.getFaction().getId().equalsIgnoreCase(this.factionId)
                || (station.getFaction().getId().equalsIgnoreCase("abandoned") && !includeAbandoned)
                || ExerelinUtils.doesStringArrayContainValue(station.getFaction().getId(), ExerelinConfig.neutralFactions, false))
                continue;

            if(Global.getSector().getFaction(this.getFactionId()).getRelationship(station.getFaction().getId()) < 0)
                return true;
        }

        return false;
	}

	public int updateWarWeariness(Boolean atWar)
	{
		if(atWar)
			warWeariness = warWeariness + 1;
		else
			warWeariness = 0;
		return warWeariness;
	}

	public void setWarweariness(int value)
	{
		warWeariness = value;
	}

	public FactionAPI getFactionAPI()
	{
        return Global.getSector().getFaction(this.factionId);
	}

	public String[] getAlliedFactions()
	{
		ArrayList confirmedFactions = new ArrayList(availableFactions.length);

		for(int i = 0; i < availableFactions.length; i++)
		{
			if(availableFactions[i].equalsIgnoreCase(this.getFactionId()))
				continue;

			if(Float.parseFloat((String)gameFactionRelationships.get(availableFactions[i])) >= 1)
			{
				confirmedFactions.add(availableFactions[i]);
			}
		}

		return (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
	}

	public String[] getEnemyFactions()
	{
		ArrayList confirmedFactions = new ArrayList(availableFactions.length);

		for(int i = 0; i < availableFactions.length; i++)
		{
			if(availableFactions[i].equalsIgnoreCase(this.getFactionId()))
				continue;

			if(Float.parseFloat((String)gameFactionRelationships.get(availableFactions[i])) < 0)
			{
				confirmedFactions.add(availableFactions[i]);
			}
		}

		return (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
	}

	public String[] getNeutralFactions()
	{
		ArrayList confirmedFactions = new ArrayList(availableFactions.length);

		for(int i = 0; i < availableFactions.length; i++)
		{
			if(availableFactions[i].equalsIgnoreCase(this.getFactionId()))
				continue;

			if(Float.parseFloat((String)gameFactionRelationships.get(availableFactions[i])) < 1 && Float.parseFloat((String)gameFactionRelationships.get(availableFactions[i])) >= 0)
			{
				confirmedFactions.add(availableFactions[i]);
			}
		}

		return (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
	}

	public Boolean isInAlliance()
	{
		return (allianceId.equalsIgnoreCase(""));
	}

	public String getAllianceId()
	{
		return allianceId;
	}

	public void setAllianceId(String inAllianceId)
	{
		allianceId = inAllianceId;
	}
}
