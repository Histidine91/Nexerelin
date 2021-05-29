package exerelin.utilities;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;

public class TemporaryFleetAdvanceScript implements EveryFrameScript {
	
	protected CampaignFleetAPI fleet;
	protected float ttl;
	
	public TemporaryFleetAdvanceScript(CampaignFleetAPI fleet, float ttl) {
		this.fleet = fleet;
		this.ttl = ttl;
	}
	
	public void advance(float amount) {
		fleet.advance(amount);
		float days = Global.getSector().getClock().convertToDays(amount);
		
		ttl -= days;
		if (ttl <= 0) {
			if (fleet.isAlive()) {
				Global.getLogger(this.getClass()).info("Despawning response fleet " + fleet.getNameWithFaction());
				fleet.despawn(CampaignEventListener.FleetDespawnReason.OTHER, null);
			}
		}
	}

	public boolean isDone() {
		return ttl <= 0;
	}

	public boolean runWhilePaused() {
		return false;
	}
}
