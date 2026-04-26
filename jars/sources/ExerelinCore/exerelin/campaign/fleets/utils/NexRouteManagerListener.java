package exerelin.campaign.fleets.utils;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager;

public interface NexRouteManagerListener {

    void reportRouteAdded(RouteManager.RouteData route);

    void reportRouteRemoved(RouteManager.RouteData route);

    void reportRouteFleetSpawned(CampaignFleetAPI fleet, RouteManager.RouteData route);

    void reportRouteFleetDespawned(CampaignFleetAPI fleet, RouteManager.RouteData route);
}
