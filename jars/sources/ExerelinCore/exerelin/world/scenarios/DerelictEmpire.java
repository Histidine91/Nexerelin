package exerelin.world.scenarios;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.NexUtilsFaction;
import exerelin.world.ExerelinCorvusLocations;
import java.util.HashSet;
import java.util.Set;

public class DerelictEmpire extends Scenario {
	
	@Override
	public void afterEconomyLoad(SectorAPI sector) {
		FactionAPI derelict = sector.getFaction("nex_derelict");
		boolean corvus = SectorManager.getManager().isCorvusMode();		
		Set<String> corvusSpawnPoints = new HashSet<>();
		for (ExerelinCorvusLocations.SpawnPointEntry entry : ExerelinCorvusLocations.getFactionSpawnPointsCopy().values())
		{
			corvusSpawnPoints.add(entry.entityId);
		}
		
		for (MarketAPI market : sector.getEconomy().getMarketsCopy()) 
		{
			if (corvus || market.getMemoryWithoutUpdate().getBoolean("$nex_randomMarket"))
			{
				if (market.getMemoryWithoutUpdate().getBoolean("$nex_procgen_hq")) continue;
				if (NexUtilsFaction.isPirateFaction(market.getFactionId())) continue;
				if (market.getFactionId().equals(Factions.INDEPENDENT))
					continue;
				Global.getLogger(this.getClass()).info(String.format(
						"my ID: %s; my spawn point: %s", 
						market.getPrimaryEntity().getId(), 
						ExerelinCorvusLocations.
						getFactionSpawnPoint(market.getFactionId())));
				if (corvus && corvusSpawnPoints.contains(market.getPrimaryEntity().getId()))
					continue;
				
				SectorManager.transferMarket(market, derelict, market.getFaction(), 
						false, false, null, 0, true);
				market.setAdmin(null);
			}
		}
		
		for (FactionAPI faction : Global.getSector().getAllFactions()) {
			String factionId = faction.getId();
			if (factionId.equals(Factions.DERELICT)) continue;
			if (factionId.equals("nex_derelict")) continue;
			if (factionId.equals(Factions.REMNANTS)) continue;
			derelict.setRelationship(factionId, DiplomacyManager.STARTING_RELATIONSHIP_HOSTILE);
		}
	}
}
