package exerelin.campaign.fleets;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.ai.FleetAssignmentDataAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import org.lazywizard.lazylib.MathUtils;

/**
 * Cleans up scavenger fleets that are failing to return to their home markets
 */
public class ScavengerCleaner extends BaseCampaignEventListener {

	public ScavengerCleaner() {
		super(false);
	}
	
	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) 
	{
		for (StarSystemAPI system : Global.getSector().getStarSystems())
		{
			for (CampaignFleetAPI fleet : system.getFleets())
			{
				if (!fleet.getFaction().getId().equals(Factions.INDEPENDENT))
					continue;
				boolean isScav = fleet.getMemoryWithoutUpdate().contains(MemFlags.MEMORY_KEY_SCAVENGER) && fleet.getMemoryWithoutUpdate().getBoolean(MemFlags.MEMORY_KEY_SCAVENGER);
				if (!isScav) continue;
				
				FleetAssignmentDataAPI assign = fleet.getCurrentAssignment();
				if (assign == null) continue;
				if (assign.getAssignment() != FleetAssignment.GO_TO_LOCATION 
						&& assign.getAssignment() != FleetAssignment.GO_TO_LOCATION_AND_DESPAWN)
					continue;
				
				SectorEntityToken target = assign.getTarget();
				if (target == null || target.getMarket() == null) continue;
				String sourceMarketId = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_SOURCE_MARKET);
				if (sourceMarketId == null || !sourceMarketId.equals(target.getMarket().getId()))
					continue;
				if (MathUtils.isWithinRange(fleet, target, 100))
				{
					if (Global.getSettings().isDevMode())
						Global.getSector().getCampaignUI().addMessage("Cleaning up " + fleet.getName());
					fleet.despawn(FleetDespawnReason.REACHED_DESTINATION, target);
				}
			}
		}
	}
}
