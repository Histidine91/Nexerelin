package exerelin.campaign.intel.bases;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel;

public class NexPirateBaseIntel extends PirateBaseIntel {

    public NexPirateBaseIntel(StarSystemAPI system, String factionId, PirateBaseTier tier) {
        super(system, factionId, tier);
    }

    public BaseBountyData getBountyData() {
        return this.bountyData;
    }

    @Override
    public boolean affectsMarket(MarketAPI market) {
        // TODO: make it not affect stuff we don't want
        return super.affectsMarket(market);
    }
}
