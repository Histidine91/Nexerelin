package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.fleets.LionsGuardFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.world.ExerelinPatrolFleetManager;
import java.util.HashSet;
import java.util.Set;

// same as vanilla one except uses our own patrol fleet manager + some special handling
public class ExerelinCoreScript extends CoreScript {
	protected Set<String> marketsWithAssignedPatrolScripts = new HashSet<String>();
	
	@Override
	public void assignPatrolSpawningScripts() {
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			String id = market.getId();
			
			if (SharedData.getData().getMarketsWithoutPatrolSpawn().contains(id)) continue;
			
			//if (market.getFactionId().equals(Factions.PIRATES)) continue;
			
			if (market.getFaction().getCustom().optBoolean(Factions.CUSTOM_NO_PATROLS)) 
			{
				ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
				if (factionConfig == null || !factionConfig.spawnPatrols) continue;
			}	
			
			if (marketsWithAssignedPatrolScripts.contains(id)) continue;
			marketsWithAssignedPatrolScripts.add(id);
			
			if (id.equals("sindria")) {
				LionsGuardFleetManager script = new LionsGuardFleetManager(market);
				SectorEntityToken entity = market.getPrimaryEntity();
				entity.addScript(script);
			}
			else if (id.equals("tem_ascalon"))
				continue;
			
			ExerelinPatrolFleetManager script = new ExerelinPatrolFleetManager(market);
			SectorEntityToken entity = market.getPrimaryEntity();
			// remove any existing patrol scripts just to be safe
			entity.removeScriptsOfClass(PatrolFleetManager.class);
			entity.removeScriptsOfClass(ExerelinPatrolFleetManager.class);
			entity.addScript(script);
			log.info("Added patrol fleet spawning script to market [" + market.getName() + "]");
			
//			MercAndPirateFleetManager pirateScript = new MercAndPirateFleetManager(market);
//			entity.addScript(pirateScript);
//			log.info("Added pirate fleet spawning script to market [" + market.getName() + "]");
			
		}
	}
}
