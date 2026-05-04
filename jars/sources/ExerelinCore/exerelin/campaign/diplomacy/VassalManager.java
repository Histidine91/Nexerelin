package exerelin.campaign.diplomacy;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import exerelin.campaign.AllianceManager;

import java.util.HashMap;
import java.util.Map;

// Because DiplomacyManager is already too damn long
public class VassalManager {

    protected Map<String, String> vassalages = new HashMap<>();

    public static VassalManager getInstance() {
        return null;
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

    public String getOverlord(String vassalId) {
        return vassalages.get(vassalId);
    }

    public boolean isVassal(String factionId) {
        return vassalages.containsKey(factionId);
    }

    public void checkRelationsWithOverlords() {

    }

    public void checkRelationsWithOverlord(String vassalId) {

    }
}
