package exerelin.campaign.intel.fleets;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.raid.RaidIntel;

public interface RaidListener {

    void reportRaidEnded(RaidIntel intel, FactionAPI attacker, FactionAPI defender, MarketAPI target, boolean success);
}
