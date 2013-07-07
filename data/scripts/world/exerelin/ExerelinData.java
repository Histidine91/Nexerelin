package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;

/* This class functions as a data transfer for the various Exerelin modules

   It should also function as a cache, but due to no hookable event on load/save
   some variables need to be reset each game advance.
 */

public final class ExerelinData
{
	private static ExerelinData instance = null;

	public boolean confirmedFaction = false;
	private String playerFaction = "independent";

	private String[] possibleFactions = new String[] {"hegemony", "tritachyon", "pirates", "independent", "shadowyards", "syndicateasp", "junkpirate", "nomad", "council", "blackrock", "antediluvian", "valkyrian", "lotusconglomerate", "gedune", "neutrino", "interstellarFederation", "relics", "nihil", "thulelegacy"};
	//private String[] possibleFactions = new String[] {"hegemony", "tritachyon", "pirates", "independent"};
	private String[] availableFactions = null;
	public boolean onlyVanillaFactions = false;
	public boolean confirmedAvailableFactions = false;

	public int numPlanets = 10;
	public int maxMoonsPerPlanet = 3;
	public int numStations = 3;
	public int numAsteroidBelts = 6;

	public Boolean playerOwnedStationFreeTransfer = false;
	public Boolean confirmedFreeTransfer = false;
	public boolean respawnFactions = true;
	public boolean onlyRespawnStartingFactions = true;
	public int respawnDelay = 1000000;
	public int numStartFactions = 3;
	public boolean omniFacPresent = false;
	public int maxFactionsInExerelinAtOnce = 3;
	public int maxSystemSize = 15000;

	public SystemManager systemManager;

	public Vector2f playerOffMapFleetSpawnLocation = null;

	private ExerelinData()
	{
		// Empty constructor
	}

	public static ExerelinData getInstance()
	{
		if(instance == null)
			instance = new ExerelinData();

		return instance;
	}

	public String getPlayerFaction()
	{
		return playerFaction;
	}

	public void setPlayerFaction(String factionId)
	{
		confirmedFaction = true;
		playerFaction = factionId;
	}

	public void resetPlayerFaction()
	{
		playerFaction = "independent"; // Set to default
	}

	public String[] getPossibleFacions()
	{
		if(onlyVanillaFactions)
			return new String[] {"hegemony", "tritachyon", "pirates", "independent",};
		else
			return possibleFactions;
	}

	public void setAvailableFactions(String[] array)
	{
		availableFactions = array;
	}

	public void resetAvailableFactions()
	{
		availableFactions = null;
	}

	public String[] getAvailableFactions(SectorAPI sector)
	{
		if (availableFactions == null)
		{
			String[] locPossibleFaction = this.getPossibleFacions();
			ArrayList confirmedFactions = new ArrayList(locPossibleFaction.length);

			if(!onlyRespawnStartingFactions)
			{
				for(int i = 0; i < locPossibleFaction.length; i = i + 1)
				{
					FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
					if(fac != null)
						confirmedFactions.add(fac.getId());
					else
						System.out.println("Couldn't determine faction for:" + locPossibleFaction[i]);
				}
				availableFactions = (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
			}
			else
			{
				ExerelinUtils.shuffleStringArray(locPossibleFaction);

				int i = 0;
				while(confirmedFactions.size() < Math.min(this.numStartFactions, locPossibleFaction.length))
				{
					if(locPossibleFaction[i].equalsIgnoreCase(this.playerFaction))
					{
						i = i + 1;
						continue;
					}

					FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
					if(fac != null)
					{
						confirmedFactions.add(fac.getId());
					}
					else
						System.out.println("Couldn't determine faction for:" + locPossibleFaction[i]);

					i = i + 1;
				}
				confirmedFactions.add(this.getPlayerFaction());

				availableFactions = (String[])confirmedFactions.toArray( new String[confirmedFactions.size()] );
			}
		}
		return availableFactions;
	}
}
