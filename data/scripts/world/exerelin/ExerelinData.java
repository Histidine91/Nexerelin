package data.scripts.world.exerelin;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import org.lwjgl.util.vector.Vector2f;

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
			String confirmedFactionsString = "";
			String delimter = "1111"; // weird but '|' didn't work
			String[] locPossibleFaction = this.getPossibleFacions();

			if(!onlyRespawnStartingFactions)
			{
				for(int i = 0; i < locPossibleFaction.length; i = i + 1)
				{
					FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
					if(fac != null)
						confirmedFactionsString = confirmedFactionsString + fac.getId() + delimter;
					else
						System.out.println("Couldn't determine faction for:" + locPossibleFaction[i]);
				}
				confirmedFactionsString = confirmedFactionsString.substring(0, confirmedFactionsString.length() - delimter.length());
				availableFactions = confirmedFactionsString.split(delimter);
			}
			else
			{
				ExerelinUtils.shuffleStringArray(locPossibleFaction);

				int i = 0;
				int added = 0;
				while(added < Math.min(this.numStartFactions, locPossibleFaction.length))
				{
					if(locPossibleFaction[i].equalsIgnoreCase(this.playerFaction))
					{
						i = i + 1;
						continue;
					}

					FactionAPI fac = sector.getFaction(locPossibleFaction[i]);
					if(fac != null)
					{
						confirmedFactionsString = confirmedFactionsString + fac.getId() + delimter;
						added = added + 1;
					}
					else
						System.out.println("Couldn't determine faction for:" + locPossibleFaction[i]);

					i = i + 1;
				}
				confirmedFactionsString = confirmedFactionsString + this.getPlayerFaction() + delimter;

				confirmedFactionsString = confirmedFactionsString.substring(0, confirmedFactionsString.length() - delimter.length());
				availableFactions = confirmedFactionsString.split(delimter);
			}
		}
		return availableFactions;
	}
}
