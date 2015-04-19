package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseOnMessageDeliveryScript;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.campaign.comm.CommMessageAPI;
import com.fs.starfarer.api.campaign.comm.MessagePriority;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.events.CustomsInspectionEvent;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.shared.SharedData;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;

// stupid private variables in parent class
public class ExerelinCustomsInspectionEvent extends CustomsInspectionEvent {
	
    private CampaignFleetAPI fleet;

    private boolean done = false;
    protected String starSystemId = null;
        
    @Override
    public void startEvent() {
        //String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (market != null) {
            String marketFactionId = market.getFactionId();
            boolean ownFaction = marketFactionId.equals("player_npc");
            if (ExerelinConfig.ownFactionCustomsInspections && marketFactionId.equals(PlayerFactionStore.getPlayerFactionId())) 
                ownFaction = true;
            
            if (ownFaction)
            {
                log.info("Customs inspection by own faction; aborting");
                endEvent(InspectionOutcome.ENDED_PEACEFULLY);
                return;
            }
        }
        super.startEvent();
    }

    // same as vanilla
    private void endEvent(InspectionOutcome outcome) {
        fleet.getAI().removeFirstAssignmentIfItIs(FleetAssignment.FOLLOW);
        fleet.getAI().removeFirstAssignmentIfItIs(FleetAssignment.INTERCEPT);
        fleet.getAI().removeFirstAssignmentIfItIs(FleetAssignment.HOLD);

        fleet.getMemoryWithoutUpdate().unset("$doingCustomsInspection");
        fleet.getMemoryWithoutUpdate().unset(MemFlags.FLEET_BUSY);

        Global.getSector().getMemory().unset("$customsInspectionFactionId");

        done = true;

        String factionId = fleet.getFaction().getId();

        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        switch (outcome) {
        case PLAYER_EVASION_NOTICED:
        case PLAYER_FOUGHT_OR_RAN_AFTER_SCAN:
        case PLAYER_FOUGHT_OR_RAN_BEFORE_SCAN:
            Global.getSector().reportEventStage(this, "player_evaded", playerFleet, MessagePriority.ENSURE_DELIVERY, new BaseOnMessageDeliveryScript() {
                public void beforeDelivery(CommMessageAPI message) {
                    Global.getSector().adjustPlayerReputation(
                        new RepActionEnvelope(RepActions.CUSTOMS_NOTICED_EVADING, null, message, true), 
                        fleet.getFaction().getId());
                }
            });
            if (starSystemId != null) {
                SharedData.getData().getStarSystemCustomsTimeout(factionId).add(starSystemId, 2f);
            }			
            break;
        case PLAYER_LEFT_SYSTEM:
        case INTERRUPTED_BY_THIRD_PARTY:
        case PLAYER_EVASION_SUCCESS:
        case EVENT_TOOK_TOO_LONG:
            if (starSystemId != null) {
                SharedData.getData().getStarSystemCustomsTimeout(factionId).add(starSystemId, 2f);
            }
            break;
        case ENDED_PEACEFULLY:
            if (starSystemId != null) {
                SharedData.getData().getStarSystemCustomsTimeout(factionId).add(starSystemId, 15f);
            }
            break;
        }
    }
}










