package exerelin.campaign.intel;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionIntel;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager.PunExGoal;
import com.fs.starfarer.api.impl.campaign.intel.punitive.PunitiveExpeditionManager.PunExReason;
import com.fs.starfarer.api.impl.campaign.intel.raid.BaseRaidStage;

public class Nex_PunitiveExpeditionIntel extends PunitiveExpeditionIntel {

	public Nex_PunitiveExpeditionIntel(FactionAPI faction, MarketAPI from, MarketAPI target, 
			float fp, float orgDur, PunExGoal goal, Industry industry, PunExReason bestReason) {
		super(faction, from, target, fp, orgDur, goal, industry, bestReason);
	}
	
	public void terminateEvent(PunExOutcome outcome)
	{
		setOutcome(outcome);
		forceFail(true);
	}
	
	// check if market should still be punished
	@Override
	protected void advanceImpl(float amount) {
		if (outcome == null)
		{
			// source captured before launch
			if (getCurrentStage() <= 0 && from.getFaction() != faction) {
				terminateEvent(PunExOutcome.AVERTED);
			}
			else if (!target.isInEconomy()) {
				terminateEvent(PunExOutcome.COLONY_NO_LONGER_EXISTS);
			}
		}
		super.advanceImpl(amount);
	}
	
	// send fleets home
	@Override
	protected void failedAtStage(RaidStage stage) {
		BaseRaidStage stage2 = (BaseRaidStage)stage;
		stage2.giveReturnOrdersToStragglers(stage2.getRoutes());
	}
	

	@Override
	public CampaignFleetAPI spawnFleet(RouteManager.RouteData route) {
		// Fix bug if source market is captured after fleet leaves, by enforcing the faction
		CampaignFleetAPI fleet = super.spawnFleet(route);
		if (fleet != null) fleet.setFaction(this.faction.getId());
		return fleet;
	}
}
