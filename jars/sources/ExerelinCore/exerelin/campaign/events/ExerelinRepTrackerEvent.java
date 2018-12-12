package exerelin.campaign.events;

import com.fs.starfarer.api.impl.campaign.events.RepTrackerEvent;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import exerelin.campaign.PlayerFactionStore;
import java.util.List;

// TODO: still needed?
public class ExerelinRepTrackerEvent extends RepTrackerEvent {
    
    @Override
    public void causeNegativeRepChangeWithEnemies(List<MarketTradeInfo> info) {
        
        String alignedFactionId = PlayerFactionStore.getPlayerFactionId();
        MarketTradeInfo info1 = info.get(0);
        String marketFactionId = info1.market.getFactionId();
        if ( marketFactionId.equals(alignedFactionId) || (marketFactionId.equals(Factions.PLAYER) ))
            return;   // no rep penalty for trading with own faction
        
        super.causeNegativeRepChangeWithEnemies(info);
    }
}