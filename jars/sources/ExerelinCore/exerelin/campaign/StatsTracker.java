package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.List;
import java.util.Map;

/**
 *  Tracks lifetime stats: kills, losses, planets captured, etc.
 */
public class StatsTracker extends BaseCampaignEventListener{
    protected static final String TRACKER_MAP_KEY = "exerelin_statsTracker";
    
    protected static StatsTracker tracker;
    
    protected int shipsKilled = 0;
    protected int shipsLost = 0;
    protected float fpKilled = 0;
    protected float fpLost = 0;
    protected int marketsCaptured = 0;
    protected int agentsUsed = 0;
    protected int saboteursUsed = 0;
    protected int prisonersRepatriated = 0;
    protected int prisonersRansomed = 0;
    protected int slavesSold = 0;
    protected int orphansMade = 0;  // TODO hee hee
    
    public StatsTracker() {
        super(true);
    }
    
    public float getFpKilled() {
        return fpKilled;
    }
    
    public float getFpLost() {
        return fpLost;
    }

    public int getMarketsCaptured() {
        return marketsCaptured;
    }
    
    public int getAgentsUsed() {
        return agentsUsed;
    }
    
    public int getSaboteursUsed() {
        return saboteursUsed;
    }

    public int getPrisonersRepatriated() {
        return prisonersRepatriated;
    }

    public int getPrisonersRansomed() {
        return prisonersRansomed;
    }

    public int getSlavesSold() {
        return slavesSold;
    }

    public void notifyAgentsUsed(int num)
    {
        agentsUsed += num;
    }
    
    public void notifySaboteursUsed(int num)
    {
        saboteursUsed += num;
    }
    
    public void notifyPrisonersRepatriated(int num) {
        prisonersRepatriated += num;
    }
    
    public void notifyPrisonersRansomed(int num) {
        prisonersRansomed += num;
    }
    
    public void notifySlavesSold(int num) {
        slavesSold += num;
    }
    
    public void notifyMarketCaptured(MarketAPI market) {
        marketsCaptured++;
    }
    
    @Override
    public void reportBattleOccurred(CampaignFleetAPI winner, CampaignFleetAPI loser)
    {
        FactionAPI winFaction = winner.getFaction();
        FactionAPI loseFaction = loser.getFaction();
        String winFactionId = winFaction.getId();
        String loseFactionId = loseFaction.getId();
        
        String playerAlignedFactionId = PlayerFactionStore.getPlayerFactionId();
        FactionAPI playerAlignedFaction = Global.getSector().getFaction(playerAlignedFactionId);
        CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
        
        CampaignFleetAPI killFleet;
        CampaignFleetAPI lossFleet;
        
        //Global.getLogger(StatsTracker.class).info("Tracker tracking battle");
        
        if (winner == playerFleet)
        {
            killFleet = loser;
            lossFleet = winner;
        }
        else if (loser == playerFleet)
        {
            killFleet = winner;
            lossFleet = loser;
        }
        else return; 

        List<FleetMemberAPI> killCurrent = killFleet.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : killFleet.getFleetData().getSnapshot()) {
            if (!killCurrent.contains(member)) {
                fpKilled += member.getFleetPointCost();
                shipsKilled++;
            }
        }
        
        List<FleetMemberAPI> lossCurrent = lossFleet.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : lossFleet.getFleetData().getSnapshot()) {
            if (!lossCurrent.contains(member)) {
                fpLost += member.getFleetPointCost();
                shipsLost++;
            }
        }
    }
    
    public static StatsTracker getStatsTracker()
    {
        if (tracker == null) return create();
        return tracker;
    }
    
    public static boolean isTrackerLoaded()
    {
        return (tracker != null);
    }
    
    public static StatsTracker create()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        tracker = (StatsTracker)data.get(TRACKER_MAP_KEY);
        if (tracker != null)
            return tracker;
        
        tracker = new StatsTracker();
        
        data.put(TRACKER_MAP_KEY, tracker);
        return tracker;
    }
}
