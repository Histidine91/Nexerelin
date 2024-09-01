package exerelin.campaign.intel.diplomacy;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import lombok.Getter;

public class InterventionIntel extends TimedDiplomacyIntel {

    @Getter protected String recipientFactionId;
    @Getter protected String friendId;

    public InterventionIntel(String factionId, String recipientFactionId, String friendId) {
        this.factionId = factionId;
        this.recipientFactionId = recipientFactionId;
        this.friendId = friendId;
    }

    public void init() {
        // if recipient is not a player-ruled faction, an AI method handles response
        // else, add this intel to intel manager and let player decide

    }

    public boolean pickAIResponse() {
        // TODO
        return false;
    }

    @Override
    public void onExpire() {

    }

    @Override
    protected void acceptImpl() {

    }

    @Override
    protected void rejectImpl() {

    }

    @Override
    public void createGeneralDescription(TooltipMakerAPI info, float width, float opad) {

    }

    @Override
    public void createPendingDescription(TooltipMakerAPI info, float width, float opad) {

    }

    @Override
    public void createOutcomeDescription(TooltipMakerAPI info, float width, float opad) {

    }

    @Override
    public String getStrategicActionName() {
        return this.getName();
    }
}
