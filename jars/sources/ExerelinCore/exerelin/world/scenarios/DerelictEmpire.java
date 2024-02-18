package exerelin.world.scenarios;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.ColonyManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexConfig;
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
				if (NexUtilsFaction.isPirateFaction(market.getFactionId()) && !NexConfig.allowPirateInvasions) continue;
				if (market.getFactionId().equals(Factions.INDEPENDENT))
					continue;
				if (corvus && corvusSpawnPoints.contains(market.getPrimaryEntity().getId()))
					continue;
				
				SectorManager.transferMarket(market, derelict, market.getFaction(), 
						false, false, null, 0, true);
				PersonAPI currAdmin = market.getAdmin();
				market.setAdmin(null);
				ColonyManager.replaceDisappearedAdmin(market, currAdmin);
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
	
	@Override
	public void afterTimePass(SectorAPI sector) {
		InvasionFleetManager man = InvasionFleetManager.getManager();
		if (man == null) return;
		
		float points = NexConfig.pointsRequiredForInvasionFleet * 0.8f;
		
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			man.modifySpawnCounter(factionId, points);
		}
	}
}
