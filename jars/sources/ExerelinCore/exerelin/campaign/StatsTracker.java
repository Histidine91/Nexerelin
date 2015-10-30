package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.List;
import java.util.Map;
import org.lazywizard.lazylib.MathUtils;

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
    protected int orphansMade = 0;  // hee hee
    
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
    
    public int getOrphansMade() {
        return orphansMade;
    }
    
    public void modifyOrphansMade(int num) {
        orphansMade += num;
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
    
    public void modifyOrphansMadeByCrewCount(int crew, String faction)
    {
        float numAvgKids = MathUtils.getRandomNumberInRange(0f, 1.5f) + MathUtils.getRandomNumberInRange(0f, 1.5f);
        if (faction.equals("templars"))   // High-ranking Templars (including those who'd get to serve on a ship) have large (adopted) families
            numAvgKids = MathUtils.getRandomNumberInRange(0f, 5f) + MathUtils.getRandomNumberInRange(0f, 5f);
        orphansMade += crew * numAvgKids;
    }
    
    @Override
    public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
    {
        CampaignFleetAPI loser = battle.getOtherSideFor(winner).get(0);    // should probably work
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
        int crewKilled = 0;
        
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
        
        String killedFaction = killFleet.getFaction().getId();
        if (killedFaction.equals("spire") || killedFaction.equals("darkspire")) return; // Spire biology is different

        List<FleetMemberAPI> killCurrent = killFleet.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : killFleet.getFleetData().getSnapshot()) {
            if (!killCurrent.contains(member)) {
                fpKilled += member.getFleetPointCost();
                shipsKilled++;
                crewKilled += member.getMinCrew();
            }
        }
        
        List<FleetMemberAPI> lossCurrent = lossFleet.getFleetData().getMembersListCopy();
        for (FleetMemberAPI member : lossFleet.getFleetData().getSnapshot()) {
            if (!lossCurrent.contains(member)) {
                fpLost += member.getFleetPointCost();
                shipsLost++;
            }
        }
        
        modifyOrphansMadeByCrewCount(crewKilled, killedFaction);
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
