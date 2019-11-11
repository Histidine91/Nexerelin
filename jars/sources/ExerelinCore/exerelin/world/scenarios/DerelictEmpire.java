package exerelin.world.scenarios;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinUtilsFaction;

public class DerelictEmpire extends Scenario {
	
	@Override
	public void afterEconomyLoad(SectorAPI sector) {
		FactionAPI derelict = sector.getFaction(Factions.DERELICT);
		for (MarketAPI market : sector.getEconomy().getMarketsCopy()) 
		{
			if (market.getMemoryWithoutUpdate().getBoolean("$nex_randomMarket"))
			{
				if (market.getMemoryWithoutUpdate().getBoolean("$nex_procgen_hq")) continue;
				if (ExerelinUtilsFaction.isPirateFaction(market.getFactionId())) continue;
				if (market.getFactionId().equals(Factions.INDEPENDENT))
					continue;
				
				SectorManager.transferMarket(market, derelict, market.getFaction(), 
						false, false, null, 0, true);
				market.setAdmin(null);
			}
		}
		
		for (String factionId : SectorManager.getLiveFactionIdsCopy()) {
			if (factionId.equals(Factions.DERELICT)) continue;
			derelict.setRelationship(factionId, DiplomacyManager.STARTING_RELATIONSHIP_HOSTILE);
		}
		derelict.setRelationship(Factions.PLAYER, DiplomacyManager.STARTING_RELATIONSHIP_HOSTILE);
	}
}
