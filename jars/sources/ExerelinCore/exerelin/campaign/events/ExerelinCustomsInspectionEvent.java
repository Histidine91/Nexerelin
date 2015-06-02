package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.events.CustomsInspectionEvent;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;

// stupid private variables in parent class
public class ExerelinCustomsInspectionEvent extends CustomsInspectionEvent {
	        
    @Override
    public void startEvent() {
        //String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        log.info("Customs inspection event starting");
        if (faction != null)    // I haven't been able to properly test this yet so this is for safety
        {
            String factionId = faction.getId();
            boolean blockOwnFaction = factionId.equals("player_npc");
            if (ExerelinConfig.ownFactionCustomsInspections)
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










