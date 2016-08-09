package exerelin.campaign.events;

import org.apache.log4j.Logger;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventPlugin;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.impl.campaign.events.BaseEventPlugin;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.world.exigency.Tasserus;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.utilities.ExerelinUtilsFleet;
import exerelin.utilities.StringHelper;
import exerelin.world.InvasionFleetManager.InvasionFleetData;


public class ExigencyRespawnFleetEvent extends BaseEventPlugin {

	public static Logger log = Global.getLogger(ExigencyRespawnFleetEvent.class);
	protected MarketAPI fakeMarket;
	protected IntervalUtil interval;
		
	protected void giveInitialAssignment(InvasionFleetData data)
    {
        float daysToOrbit = ExerelinUtilsFleet.getDaysToOrbit(data.fleet) * 0.25f;
        if (daysToOrbit < 0.2f)
        {
            daysToOrbit = 0.2f;
        }
        data.fleet.getAI().addAssignment(FleetAssignment.ORBIT_PASSIVE, data.source, daysToOrbit, 
				StringHelper.getFleetAssignmentString("preparingFor", data.source.getName(), "missionInvasion"), null);
    }
	
	@Override
	public void init(String type, CampaignEventTarget eventTarget) {
		super.init(type, eventTarget);
		interval = new IntervalUtil(ExerelinConfig.factionRespawnInterval * 1.6f, ExerelinConfig.factionRespawnInterval * 2.4f);
		//interval = new IntervalUtil(3, 3);
		fakeMarket = Global.getFactory().createMarket("tasserus_fake_market", "Tasserus", 6);
		fakeMarket.setFactionId("exigency");
		fakeMarket.setPrimaryEntity(Global.getSector().getEntityById("exigency_anomaly"));
	}
	
	@Override
	public void advance(float amount) {
		if (SectorManager.isFactionAlive("exigency")) return;
		float days = Global.getSector().getClock().convertToDays(amount);
		interval.advance(days);
		if (interval.intervalElapsed())
		{
			InvasionFleetData fleetData = SectorManager.spawnRespawnFleet(fakeMarket.getFaction(), fakeMarket, true);
			if (fleetData == null) return;
			Tasserus.getAnomalyPlugin().createBigPulse(Math.min(1f, fleetData.fleet.getFleetSizeCount() / 10f));
			giveInitialAssignment(fleetData);
			
			interval.setInterval(ExerelinConfig.factionRespawnInterval * 1.6f, ExerelinConfig.factionRespawnInterval * 2.4f);
		}
	}

	@Override
	public String getEventName() {
		return StringHelper.getString("exerelin_events", "exigencyRespawn");
	}
	
	@Override
	public CampaignEventPlugin.CampaignEventCategory getEventCategory() {
		return CampaignEventPlugin.CampaignEventCategory.DO_NOT_SHOW_IN_MESSAGE_FILTER;
	}
	
	@Override
	public String getCurrentImage() {
		FactionAPI faction = Global.getSector().getFaction("exigency");
		if (faction != null) return faction.getLogo();
		return null;
	}
	
	@Override
	public boolean isDone() {
		return false;
	}
	
	@Override
	public boolean showAllMessagesIfOngoing() {
		return false;
	}
}