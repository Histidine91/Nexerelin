package exerelin.campaign.alliances;

import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.intel.AllianceVoteIntel;

public interface AllianceEventListener {

    void reportAllianceFormed(Alliance alliance, FactionAPI faction1, FactionAPI faction2);
    void reportFactionJoinedAlliance(Alliance alliance, FactionAPI faction);
    void reportFactionLeftAlliance(Alliance alliance, FactionAPI faction);
    void reportAlliancesMerged(Alliance into, Alliance other);
    void reportAllianceDissolved(Alliance alliance);
    void reportAllianceVote(Alliance alliance, AllianceVoteIntel vote);
}
