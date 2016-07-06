package exerelin.campaign.events;

import com.fs.starfarer.api.impl.campaign.events.RepTrackerEvent;
import exerelin.ExerelinConstants;
import exerelin.campaign.PlayerFactionStore;
import java.util.List;

public class ExerelinRepTrackerEvent extends RepTrackerEvent {
    
    @Override
    public void causeNegativeRepChangeWithEnemies(List<MarketTradeInfo> info) {
        
        String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        MarketTradeInfo info1 = info.get(0);
        String marketFactionId = info1.market.getFactionId();
        if ( marketFactionId.equals(alignedFactionId) || (marketFactionId.equals(ExerelinConstants.PLAYER_NPC_ID) ))
            return;   // no rep penalty for trading with own faction
        
        super.causeNegativeRepChangeWithEnemies(info);
    }
}