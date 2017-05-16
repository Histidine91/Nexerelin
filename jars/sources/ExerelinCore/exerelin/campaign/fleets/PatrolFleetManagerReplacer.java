package exerelin.campaign.fleets;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.LionsGuardFleetManager;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;

public class PatrolFleetManagerReplacer extends BaseCampaignEventListener implements EveryFrameScript
{
	public static Logger log = Global.getLogger(PatrolFleetManagerReplacer.class);
	
	protected final IntervalUtil timer = new IntervalUtil(0.5f, 1.5f);
	protected final Set<String> marketsWithAssignedPatrolScripts = new HashSet<>();
	protected boolean firstFrame = true;
	
	public PatrolFleetManagerReplacer()
	{
		super(true);
	}
	
	@Override
	public void advance(float amount) {
		SectorAPI sector = Global.getSector();

		if (sector.isPaused()) {
				return;
		}

		if (firstFrame) {
				//assignPatrolSpawningScripts();	// wait for the vanilla one; make sure we do the replacing and not them
				firstFrame = false;
		}

		float days = sector.getClock().convertToDays(amount);

		timer.advance(days);
		if (timer.intervalElapsed()) {
				assignPatrolSpawningScripts();
		}
	}
	
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
				ExerelinLionsGuardFleetManager script = new ExerelinLionsGuardFleetManager(market);
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

	@Override
	public boolean isDone() {
			return false;
	}

	@Override
	public boolean runWhilePaused() {
			return true;
	}
}