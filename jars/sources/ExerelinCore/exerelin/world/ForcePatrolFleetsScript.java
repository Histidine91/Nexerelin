package exerelin.world;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.PatrolFleetManager;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import com.fs.starfarer.api.util.IntervalUtil;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinFactionConfig;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;

/**
 * Spawns patrols for factions that normally don't have them (as set in .faction file)
 */
@Deprecated
public class ForcePatrolFleetsScript extends BaseCampaignEventListener implements EveryFrameScript
{
	public static Logger log = Global.getLogger(ForcePatrolFleetsScript.class);
	
	private final IntervalUtil timer = new IntervalUtil(0.5f, 1.5f);
	private final Set<String> marketsWithAssignedPatrolScripts = new HashSet<String>();
	private boolean firstFrame = true;
	
	public ForcePatrolFleetsScript()
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
				assignPatrolSpawningScripts();
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
			if (SharedData.getData().getMarketsWithoutPatrolSpawn().contains(market.getId())) continue;

			// corescript already took care of the ones where this is true
			if (!market.getFaction().getCustom().optBoolean(Factions.CUSTOM_NO_PATROLS)) continue;
			ExerelinFactionConfig factionConfig = ExerelinConfig.getExerelinFactionConfig(market.getFactionId());
			if (factionConfig == null) continue;
			
			String id = market.getId();
			if (marketsWithAssignedPatrolScripts.contains(id)) continue;
			marketsWithAssignedPatrolScripts.add(id);

			SectorEntityToken entity = market.getPrimaryEntity();
			if (factionConfig.spawnPatrols)
			{
				//log.info("Faction " + market.getFactionId() + " can spawn patrols");
				PatrolFleetManager script = new PatrolFleetManager(market);
				entity.addScript(script);
				log.info("Added patrol fleet spawning script to market [" + market.getName() + "]");
			}
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