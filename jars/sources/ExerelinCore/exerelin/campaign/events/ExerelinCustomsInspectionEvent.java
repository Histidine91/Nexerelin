package exerelin.campaign.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.events.CustomsInspectionEvent;
import exerelin.campaign.PlayerFactionStore;
import exerelin.utilities.ExerelinConfig;

// stupid private variables in parent class
public class ExerelinCustomsInspectionEvent extends CustomsInspectionEvent {
	
    private boolean done = false;
    protected String starSystemId = null;
        
    @Override
    public void startEvent() {
        //String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        log.info("Customs inspection event starting");
        if (faction != null)    // I haven't been able to properly test this yet so this is for safety
        {
            String factionId = faction.getId();
            boolean blockOwnFaction = factionId.equals("player_npc");
            if (!ExerelinConfig.ownFactionCustomsInspections || factionId.equals(PlayerFactionStore.getPlayerFactionId())) 
                blockOwnFaction = true;

            if (blockOwnFaction)
            {
                log.info("Customs inspection by own faction; aborting");
                Global.getSector().getMemory().unset("$customsInspectionFactionId");
                done = true;
                return;
            }
        }
        super.startEvent();
    }
}










