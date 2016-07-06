package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.events.CustomsInspectionEvent;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;

// stupid private variables in parent class
public class ExerelinCustomsInspectionEvent extends CustomsInspectionEvent {
            
    @Override
    public void startEvent() {
        //String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        
        if (faction != null)    // I haven't been able to properly test this yet so this is for safety
        {
            String factionId = faction.getId();
            log.info("Customs inspection event starting: faction " + factionId);
            
            boolean blockOwnFaction = factionId.equals(ExerelinConstants.PLAYER_NPC_ID);
            if (!ExerelinConfig.ownFactionCustomsInspections)
            {
                blockOwnFaction = blockOwnFaction || factionId.equals(PlayerFactionStore.getPlayerFactionId());
            }

            if (blockOwnFaction)
            {
                log.info("Customs inspection by own faction (" + faction.getDisplayName() + "); aborting");
                Global.getSector().getMemory().unset("$customsInspectionFactionId");
                return;
            }
        }
        super.startEvent();
    }
}










