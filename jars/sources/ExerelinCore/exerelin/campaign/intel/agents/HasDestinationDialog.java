package exerelin.campaign.intel.agents;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

public interface HasDestinationDialog {
    void setDestination(MarketAPI market);
    MarketAPI getDestination();
    MarketAPI getMarket();
}
