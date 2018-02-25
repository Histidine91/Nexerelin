package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemQuantity;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.events.RevengeanceManagerEvent;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.StringHelper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    protected Set<DeadOfficerEntry> deadOfficers = new HashSet<>();
    
    public StatsTracker() {
        super(true);
    }
    
    public int getShipsKilled() {
        return shipsKilled;
    }

    public int getShipsLost() {
        return shipsLost;
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
    
    public Set<DeadOfficerEntry> getDeadOfficers() {
        return new HashSet<>(deadOfficers);
    }
    
    public int getNumOfficersLost() {
        return deadOfficers.size();
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
    
    public void addDeadOfficer(OfficerDataAPI officer, FleetMemberAPI member)
    {
        DeadOfficerEntry entry = new DeadOfficerEntry(officer, member);
        deadOfficers.add(entry);
    }
    
    public void removeDeadOfficer(OfficerDataAPI officer)
    {
        DeadOfficerEntry toRemove = null;
        for (DeadOfficerEntry dead : deadOfficers)
        {
            if (dead.officer == officer)
            {
                toRemove = dead;
                break;
            }
        }
        if (toRemove != null)
            deadOfficers.remove(toRemove);
    }
    
    @Override
    public void reportBattleFinished(CampaignFleetAPI winner, BattleAPI battle)
    {
        if (RevengeanceManagerEvent.getOngoingEvent() != null)
        {
            RevengeanceManagerEvent.getOngoingEvent().reportBattle(winner, battle);
        }
        
        if (!battle.isPlayerInvolved()) return;
        
        List<CampaignFleetAPI> killedFleets = battle.getNonPlayerSide();
        //List<CampaignFleetAPI> lossesFleets = battle.getPlayerSide();
        CampaignFleetAPI myFleet = Global.getSector().getPlayerFleet();
        
        Global.getLogger(StatsTracker.class).info("Tracker tracking battle");

        float involvedFraction = battle.getPlayerInvolvementFraction();

        float recentFpKilled = 0;
        int recentShipsKilled = 0;
        
        for (CampaignFleetAPI killedFleet : killedFleets)
        {
            for (FleetMemberAPI member : Misc.getSnapshotMembersLost(killedFleet)) {
                recentFpKilled += member.getFleetPointCost();
                recentShipsKilled++;

                // orphans
                String factionId = member.getCaptain().getFaction().getId();
                if (factionId.equals("spire") || factionId.equals("darkspire")) continue; // Spire biology is different
                modifyOrphansMadeByCrewCount((int)(member.getMinCrew()*involvedFraction), factionId);
            }
        }
        fpKilled += recentFpKilled * involvedFraction;
        shipsKilled += recentShipsKilled * involvedFraction;
        
        List<FleetMemberAPI> myCurrent = myFleet.getFleetData().getMembersListCopy();
        List<FleetMemberAPI> mySnapshot = myFleet.getFleetData().getSnapshot();
        for (FleetMemberAPI member : mySnapshot) {
            if (!myCurrent.contains(member)) {
                fpLost += member.getFleetPointCost();
                shipsLost++;
            }
        }
        // report captured ships to Prism market
        for (FleetMemberAPI member : myCurrent) {
            if (!mySnapshot.contains(member)) {
                PrismMarket.notifyShipAcquired(member);
            }
        }
    }
    
    // use this to record IBB ships for already-bought list 
    // should catch debris field salvage
    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        InteractionDialogPlugin plugin = dialog.getPlugin();
        if (plugin instanceof RuleBasedInteractionDialogPluginImpl)
        {
            PrismMarket.recordShipsOwned(Global.getSector().getPlayerFleet().getMembersWithFightersCopy());
            CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();
            for (CargoItemQuantity<String> fighterStack : cargo.getFighters())
            {
                FighterWingSpecAPI bla = Global.getSettings().getFighterWingSpec(fighterStack.getItem());
                PrismMarket.notifyShipAcquired(bla.getId());
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
    
    public static class DeadOfficerEntry
    {
		public static final int NUM_CAUSES_OF_DEATH = 12;
		
        public OfficerDataAPI officer;
        public int deadCycle;
        public int deadMonth;
        public int deadDay;
        public String shipName;
        public String shipClass;
        public String shipDesignation;
		public String causeOfDeath;
        
        public DeadOfficerEntry(OfficerDataAPI officer, FleetMemberAPI member)
        {
            this.officer = officer;
            this.shipName = member.getShipName();
            this.shipClass = member.getHullSpec().getHullNameWithDashClass();
            this.shipDesignation = member.getHullSpec().getDesignation();
            CampaignClockAPI clock = Global.getSector().getClock();
            this.deadCycle = clock.getCycle();
            this.deadMonth = clock.getMonth();
            this.deadDay = clock.getDay();
			this.causeOfDeath = StringHelper.getString("exerelin_officers", 
					"causeOfDeath" + MathUtils.getRandomNumberInRange(1, NUM_CAUSES_OF_DEATH));
        }
        
        public String getDeathDate()
        {
            return deadCycle + "." + deadMonth + "." + deadDay;
        }
    }
}
