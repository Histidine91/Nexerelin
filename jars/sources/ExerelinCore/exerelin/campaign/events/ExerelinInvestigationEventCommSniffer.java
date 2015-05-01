package exerelin.campaign.events;

import com.fs.starfarer.api.impl.campaign.events.InvestigationEventCommSniffer;

public class ExerelinInvestigationEventCommSniffer extends InvestigationEventCommSniffer {
    
    protected String factionId = "neutral";
    
    @Override
    public void startEvent() {
        //String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        if (market != null) {
            String marketFactionId = market.getFactionId();
            if (marketFactionId.equals("player_npc"))
            {
                log.info("Investigation by own faction; aborting");
                endEvent();
                return;
            }
        }
        super.startEvent();
        factionId = entity.getFaction().getId();
    }
    
    @Override
    public void advance(float amount) {
        if (!isEventStarted()) return;
        if (isDone()) return;

        // relay has changed hands since investigation started; kill it
        if (!entity.getFaction().getId().equals(factionId))
        {
            log.info("Relay changed hands; cancelling investigation");
            super.endEvent();
            return;
        }
        
        super.advance(amount);
    }
}