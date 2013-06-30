package data.scripts.world.exerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;

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

		// Build off map initial station attack fleets
		this.initStationAttackFleets(sector, system);

		// Add trader spawns
		this.initTraderSpawns(sector, system);
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

	private void initStationAttackFleets(SectorAPI sector, StarSystemAPI system)
	{
		String[] factions = ExerelinData.getInstance().getAvailableFactions(sector);
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
			OutSystemStationAttackFleet offMapSpawn = new OutSystemStationAttackFleet(sector, system, factionId, false);
			offMapSpawn.spawnFleet(null, null);
		}

		// Spawn player fleet
		OutSystemStationAttackFleet offMapSpawn = new OutSystemStationAttackFleet(sector, system, ExerelinData.getInstance().getPlayerFaction(), false);
		offMapSpawn.spawnFleet(null, null);

		// Save player off map fleet spawn location
		ExerelinData.getInstance().playerOffMapFleetSpawnLocation = offMapSpawn.spawnPoint.getLocation();
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

	private void initTraderSpawns(SectorAPI sector, StarSystemAPI system)
	{
		for(int i = 0; i < Math.max(1, system.getOrbitalStations().size()/5); i++)
		{
			TradeGuildTraderSpawnPoint tgtsp = new TradeGuildTraderSpawnPoint(sector,  system,  ExerelinUtils.getRandomInRange(8,12), 1, system.createToken(0,0));
			system.addSpawnPoint(tgtsp);
		}
	}
}
