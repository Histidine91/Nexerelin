package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.List;

@SuppressWarnings("unchecked")
public class Exerelin implements SectorGeneratorPlugin
{
	public void generate(SectorAPI sector)
	{
		ClassLoader cl = Global.getSettings().getScriptClassLoader();
		StarSystemAPI system = sector.getStarSystem("Exerelin");

		// Check that player picked faction is available
		this.checkPlayerFactionPick(sector);

		// Set abandoned as enemy of every faction
		this.initFactionRelationships(sector);

		// Build off map initial station attack fleets in random systems
		this.initStationAttackFleets(sector);

		// Add trader spawns
		this.initTraderSpawns(sector);
	}

	private void checkPlayerFactionPick(SectorAPI sector)
	{
		String[] availableFactions = ExerelinData.getInstance().getAvailableFactions(sector);
		Boolean pickOK = false;
		for(int i = 0; i < availableFactions.length; i = i + 1)
		{
			if(ExerelinData.getInstance().getPlayerFaction().equalsIgnoreCase(availableFactions[i]))
			pickOK = true;
		}

		if(!pickOK)
			ExerelinData.getInstance().resetPlayerFaction();
	}

	private void initStationAttackFleets(SectorAPI sector)
	{
		String[] factions = SectorManager.getCurrentSectorManager().getFactionsPossibleInSector();
		ExerelinUtils.shuffleStringArray(factions); // Randomise order

		int numFactionsInitialStart = Math.min(factions.length - 1, ExerelinData.getInstance().numStartFactions);
		for(int i = 0; i < numFactionsInitialStart; i = i + 1)
		{
			String factionId = factions[i];
			if(factionId.equalsIgnoreCase(ExerelinData.getInstance().getPlayerFaction()))
			{
				numFactionsInitialStart = numFactionsInitialStart + 1;
				continue;
			}

			int systemChosen = ExerelinUtils.getRandomInRange(0, SectorManager.getCurrentSectorManager().getSystemManagers().length - 1);
			StarSystemAPI systemAPI = SectorManager.getCurrentSectorManager().getSystemManagers()[systemChosen].getStarSystemAPI();
			OutSystemStationAttackFleet offMapSpawn = new OutSystemStationAttackFleet(sector, systemAPI, factionId, false);
			offMapSpawn.spawnFleet(null, null);
		}
	}

	private void initFactionRelationships(SectorAPI sector)
	{
		String[] factions = ExerelinData.getInstance().getAvailableFactions(sector);
		for(int i = 0; i < factions.length; i = i + 1)
		{
			sector.getFaction(factions[i]).setRelationship("abandoned", -1);
		}

		// Set the tradeguild and rebels to hate each other
		FactionAPI rebel = sector.getFaction("rebel");
		FactionAPI tradeGuild = sector.getFaction("tradeguild");
		rebel.setRelationship(tradeGuild.getId(), -1);
		tradeGuild.setRelationship(rebel.getId(), -1);
	}

	private void initTraderSpawns(SectorAPI sector)
	{
		for(int j = 0; j < SectorManager.getCurrentSectorManager().getSystemManagers().length; j++)
		{
			StarSystemAPI systemAPI = SectorManager.getCurrentSectorManager().getSystemManagers()[j].getStarSystemAPI();
			for(int i = 0; i < Math.max(1, systemAPI.getOrbitalStations().size()/5); i++)
			{
				TradeGuildTraderSpawnPoint tgtsp = new TradeGuildTraderSpawnPoint(sector,  systemAPI,  ExerelinUtils.getRandomInRange(8,12), 1, systemAPI.createToken(0,0));
				systemAPI.addSpawnPoint(tgtsp);
			}
		}
	}
}
