package exerelin.campaign.events;

import com.fs.starfarer.api.impl.campaign.events.RepTrackerEvent;
import exerelin.campaign.PlayerFactionStore;
import java.util.List;

public class ExerelinRepTrackerEvent extends RepTrackerEvent {
    
    @Override
    public void causeNegativeRepChangeWithEnemies(List<MarketTradeInfo> info) {
        
        String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        MarketTradeInfo info1 = info.get(0);
        String marketFactionId = info1.market.getFactionId();
        if ( marketFactionId.equals(alignedFactionId) || (marketFactionId.equals("player_npc") ))
            return;   // no rep penalty for trading with own faction
        
        super.causeNegativeRepChangeWithEnemies(info);
    }
}