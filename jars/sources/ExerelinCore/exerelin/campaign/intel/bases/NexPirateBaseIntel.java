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

    // used to determine which markets this base can target, and also who within a targeted star system can issue a bounty on this base
    @Override
    public boolean affectsMarket(MarketAPI market) {
        if (market == null) return false;
        if (!super.affectsMarket(market)) return false;

        if (!market.getFaction().isHostileTo(this.market.getFaction())) return false;

        return true;
    }
}
