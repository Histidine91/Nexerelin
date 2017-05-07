package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.CoreScript;
import com.fs.starfarer.api.impl.campaign.fleets.LionsGuardFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import exerelin.campaign.fleets.ExerelinLionsGuardFleetManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import exerelin.campaign.fleets.ExerelinPatrolFleetManager;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;

// same as vanilla one except uses our own patrol fleet manager + some special handling
public class ExerelinCoreScript extends CoreScript {
	protected Set<String> marketsWithAssignedPatrolScripts = new HashSet<String>();
	public static Logger log = Global.getLogger(ExerelinCoreScript.class);
	
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
			
			SectorEntityToken entity = market.getPrimaryEntity();
			
			if (id.equals("sindria")) {
				entity.removeScriptsOfClass(LionsGuardFleetManager.class);
				LionsGuardFleetManager script = new ExerelinLionsGuardFleetManager(market);
				entity.addScript(script);
			}
			else if (id.equals("tem_ascalon"))
				continue;
			
			ExerelinPatrolFleetManager script = new ExerelinPatrolFleetManager(market);
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
