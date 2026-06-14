package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;
import exerelin.campaign.alliances.Alliance;
import exerelin.campaign.alliances.AllianceEventListener;
import exerelin.campaign.intel.AllianceVoteIntel;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Because DiplomacyManager is already too damn long
public class VassalManager implements AllianceEventListener {

    public static final String DATA_KEY = "nex_vassalManager";

    protected Map<String, String> vassalages = new HashMap<>(); // vassal to overlord

    public static VassalManager getInstance() {
        return (VassalManager)Global.getSector().getPersistentData().get(DATA_KEY);
    }

    public static VassalManager create() {
        VassalManager manager = new VassalManager();
        Global.getSector().getPersistentData().put(DATA_KEY, manager);
        Global.getSector().getListenerManager().addListener(manager, false);
        return manager;
    }

    public void vassalize(String vassalId, String overlordId) {
        FactionAPI vassal = Global.getSector().getFaction(vassalId);
        FactionAPI overlord = Global.getSector().getFaction(overlordId);

        // remove vassal from their current alliance if they have one
        if (!AllianceManager.areFactionsAllied(vassalId, overlordId)) {
            AllianceManager.leaveAlliance(vassalId, false);
        }
        AllianceManager.createAlliance(vassalId, overlordId);

        this.vassalages.put(vassalId, overlordId);

        // TODO: intel event
    }

    public void devassalize(String vassalId, boolean leaveAlliance) {
        String overlordId = getOverlord(vassalId);
        if (overlordId == null) return;
        FactionAPI vassal = Global.getSector().getFaction(vassalId);

        if (leaveAlliance) AllianceManager.leaveAlliance(vassalId, false);
        vassalages.remove(vassalId);
    }

    @Nullable
    public String getOverlord(String vassalId) {
        return vassalages.get(vassalId);
    }

    // TODO: test to make sure it returns non-null
    public List<String> getVassals(String overlordId) {
        return vassalages.entrySet().stream().filter(entry -> entry.getValue().equals(overlordId)).map(it -> it.getKey()).toList();
    }

    public boolean isVassal(String factionId) {
        return vassalages.containsKey(factionId);
    }

    public void checkRelationsWithOverlords() {
        for (String vassalId : vassalages.keySet()) {
            checkRelationsWithOverlord(vassalId);
        }
    }

    public void checkRelationsWithOverlord(String vassalId) {
        String overlordId = vassalages.get(vassalId);
    }

    public void vassalJoinAlliance(Alliance alliance, String overlordId) {
        for (String vassalId : vassalages.keySet()) {
            String thisOverlordId = vassalages.get(vassalId);
            if (thisOverlordId.equals(overlordId)) {
                AllianceManager.getManager().joinAlliance(vassalId, alliance, true);
            }
        }
    }

    public void vassalLeaveAlliance(Alliance alliance, String overlordId) {
        for (String vassalId : vassalages.keySet()) {
            String thisOverlordId = vassalages.get(vassalId);
            if (thisOverlordId.equals(overlordId)) {
                AllianceManager.getManager().leaveAlliance(vassalId, alliance, true, true);
            }
        }
    }

    @Override
    public void reportAllianceFormed(Alliance alliance, FactionAPI faction1, FactionAPI faction2) {
        vassalJoinAlliance(alliance, faction1.getId());
        vassalJoinAlliance(alliance, faction2.getId());
    }

    @Override
    public void reportFactionJoinedAlliance(Alliance alliance, FactionAPI faction) {
        vassalJoinAlliance(alliance, faction.getId());
    }

    @Override
    public void reportFactionLeftAlliance(Alliance alliance, FactionAPI faction) {
        vassalLeaveAlliance(alliance, faction.getId());
    }

    @Override
    public void reportAlliancesMerged(Alliance into, Alliance other) {
        // handled by individual join calls
    }

    @Override
    public void reportAllianceDissolved(Alliance alliance) {
        // handled by individual leave calls
    }

    @Override
    public void reportAllianceVote(Alliance alliance, AllianceVoteIntel vote) {

    }
}
