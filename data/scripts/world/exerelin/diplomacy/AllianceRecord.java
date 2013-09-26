package data.scripts.world.exerelin.diplomacy;

import com.fs.starfarer.api.Global;
import data.scripts.world.exerelin.ExerelinUtils;

import java.util.HashMap;
import java.util.Iterator;

/* This class holds the details of a multi-faction alliance.
 */

public class AllianceRecord
{
	private String allianceId;
	private HashMap factions;

	private final String DELIMETER = "_";

	public AllianceRecord(String id)
	{
		factions = new HashMap();
		if(id.equalsIgnoreCase(""))
			allianceId = Integer.toString(ExerelinUtils.getRandomInRange(0, 10000000));
		else
			allianceId = id;
	}



	public void addFactionToAlliance(String factionId)
	{
		if(!factions.containsKey(factionId) && !factions.containsValue(factionId))
			factions.put(factionId, factionId);
	}

	public void removeFactionFromAlliance(String factionId)
	{
		if(factions.containsKey(factionId) || factions.containsValue(factionId))
			factions.remove(factionId);
	}

	public String[] getFactions()
	{
		String [] factionsInAlliance = new String[factions.size()];

		Iterator it = factions.values().iterator();
		int index = 0;
		while(it.hasNext())
		{
			factionsInAlliance[index] = it.next().toString();
			index = index+1;
		}

		return factionsInAlliance;
	}

	public boolean isFactionInAlliance(String factionId)
	{
		return (factions.containsKey(factionId) || factions.containsValue(factionId));
	}

	public String getAllianceId()
	{
		return allianceId;
	}

	public int getNumFactionsInAlliance()
	{
		return factions.size();
	}

	public String getAllianceName()
	{
		String[] split = allianceId.split(DELIMETER);
		split[0] = Character.toUpperCase(split[0].charAt(0)) + split[0].substring(1);
		split[1] = Character.toUpperCase(split[1].charAt(0)) + split[1].substring(1);
		return split[0] + " " + split[1];
	}

	public String getAllianceNameAndFactions()
	{
		String name = getAllianceName();
		String allianceFactions = "";
		String[] factions = getFactions();

		for(int i = 0; i < factions.length; i++)
		{
			allianceFactions = allianceFactions + Global.getSector().getFaction(factions[i]).getDisplayName() + ", ";
		}
		allianceFactions = allianceFactions.substring(0, allianceFactions.length() - 2);

		return name + " (" + allianceFactions + ")";
	}
}

