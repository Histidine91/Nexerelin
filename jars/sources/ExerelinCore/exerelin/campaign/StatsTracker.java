package exerelin.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemQuantity;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyPlayerHostileActListener;
import com.fs.starfarer.api.campaign.listeners.SurveyPlanetListener;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.RuleBasedInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.TempData;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.Misc;
import exerelin.campaign.submarkets.PrismMarket;
import exerelin.utilities.NexUtilsMarket;
import exerelin.utilities.StringHelper;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.util.*;

/**
 *  Tracks lifetime stats: kills, losses, planets captured, etc.
 */
public class StatsTracker extends BaseCampaignEventListener implements ColonyPlayerHostileActListener, SurveyPlanetListener {
    protected static final String TRACKER_MAP_KEY = "exerelin_statsTracker";
    public static Logger log = Global.getLogger(StatsTracker.class);
    
    public static final Set<String> NO_ORPHANS_FACTIONS = new HashSet<>(Arrays.asList(new String[] {
        Factions.DERELICT, "nex_derelict", Factions.REMNANTS, "spire", "darkspire"
    }));
    
    protected static StatsTracker tracker;

    protected int shipsKilled = 0;
    protected int shipsLost = 0;
    protected float fpKilled = 0;
    protected float fpLost = 0;
    protected int marketsCaptured = 0;
    protected int marketsRaided = 0;
    protected int marketsTacBombarded = 0;
    protected int marketsSatBombarded = 0;
    protected int prisonersRepatriated = 0;
    protected int prisonersRansomed = 0;
    protected int agentActions = 0;
    @Deprecated protected int slavesSold = 0;
    protected int orphansMade = 0;  // hee hee
    protected int planetsSurveyed;
    
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

    public int getMarketsRaided() {
        return marketsRaided;
    }

    public int getMarketsTacBombarded() {
        return marketsTacBombarded;
    }

    public int getMarketsSatBombarded() {
        return marketsSatBombarded;
    }

    public int getNumAgentActions() {
        return agentActions;
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

    public int getPlanetsSurveyed() {
        return planetsSurveyed;
    }

    /**
     * @return The set of dead officers (not a copy, changes to the returned set will be reflected in the rest of the mod).
     */
    public Set<DeadOfficerEntry> getDeadOfficers() {
        return deadOfficers;
    }
    
    public List<DeadOfficerEntry> getDeadOfficersSorted() {
        List<DeadOfficerEntry> list = new ArrayList(deadOfficers);
        Collections.sort(list);
        return list;
    }
    
    public int getNumOfficersLost() {
        return deadOfficers.size();
    }
    
    public void modifyOrphansMade(int num) {
        orphansMade += num;
    }

    public void notifyAgentActions(int num)
    {
        agentActions += num;
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
    
    public DeadOfficerEntry addDeadOfficer(OfficerDataAPI officer, FleetMemberAPI member)
    {
        if (deadOfficers == null) deadOfficers = new HashSet<>();    // reverse compat
        DeadOfficerEntry entry = new DeadOfficerEntry(officer, member);
        deadOfficers.add(entry);
        return entry;
    }
    
    public void removeDeadOfficer(OfficerDataAPI officer)
    {
        if (deadOfficers == null) deadOfficers = new HashSet<>();    // reverse compat
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
        if (RevengeanceManager.getManager() != null)
        {
            //RevengeanceManager.getManager().reportBattle(winner, battle);
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
                if (!haveOrphans(factionId)) continue;
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
        return getOrCreateTracker();
    }
    
    public static boolean isTrackerLoaded()
    {
        return (tracker != null);
    }
    
    public static StatsTracker getOrCreateTracker()
    {
        Map<String, Object> data = Global.getSector().getPersistentData();
        tracker = (StatsTracker)data.get(TRACKER_MAP_KEY);
        if (tracker != null)
            return tracker;
        
        tracker = new StatsTracker();
        
        data.put(TRACKER_MAP_KEY, tracker);
        Global.getSector().getListenerManager().addListener(tracker);
        return tracker;
    }
    
    // all guesstimates until TempData becomes actually accessible
    public int addOrphansFromMilitaryAction(int power, float raidMult) {
        int orphans = (int)(MathUtils.getRandomNumberInRange(5, 20) * Math.pow(2, power));
        if (raidMult > 0) {
            float contestedFactor = 0.5f - Math.abs(0.5f - raidMult);
            orphans = orphans * 2 * Math.round(contestedFactor);
        }
        orphansMade += orphans;
        return orphans;
    }
    
    @Override
    public void reportRaidForValuablesFinishedBeforeCargoShown(
            InteractionDialogAPI dialog, MarketAPI market, TempData actionData, CargoAPI cargo) {
        if (haveOrphans(market.getFactionId())) {
            int orphans = addOrphansFromMilitaryAction(market.getSize() - 2, actionData.raidMult);
            log.info("Making " + orphans + " orphans from raid for valuables");
        }
        
        
        marketsRaided++;
    }

    @Override
    public void reportRaidToDisruptFinished(InteractionDialogAPI dialog, MarketAPI market, 
            TempData actionData, Industry industry) {
        if (haveOrphans(market.getFactionId())) {
            int orphans = addOrphansFromMilitaryAction(market.getSize() - 2, actionData.raidMult);
            log.info("Making " + orphans + " orphans from raid to disrupt");
        }
        
        marketsRaided++;
    }

    @Override
    public void reportTacticalBombardmentFinished(InteractionDialogAPI dialog, 
            MarketAPI market, TempData actionData) {
        if (haveOrphans(market.getFactionId())) {
            int orphans = addOrphansFromMilitaryAction(market.getSize() - 1, -1);
            log.info("Making " + orphans + " orphans from tactical bombardment");
        }
        
        marketsTacBombarded++;
    }

    @Override
    public void reportSaturationBombardmentFinished(InteractionDialogAPI dialog, 
            MarketAPI market, TempData actionData) {
        if (haveOrphans(market.getFactionId())) {
            int oldSize = market.getSize() + 1;
            float deaths = NexUtilsMarket.getPopulation(oldSize) - NexUtilsMarket.getPopulation(market.getSize());
            deaths *= MathUtils.getRandomNumberInRange(0.5f, 1.5f);
            int orphans = (int)Math.round(Math.sqrt(deaths));

            log.info("Making " + orphans + " orphans from saturation bombardment");
            orphansMade += orphans;
        }
        
        marketsSatBombarded++;
    }

    @Override
    public void reportPlayerSurveyedPlanet(PlanetAPI planet) {
        planetsSurveyed++;
    }
    
    public static boolean haveOrphans(String factionId) {
        return !NO_ORPHANS_FACTIONS.contains(factionId);
    }
    
    public static class DeadOfficerEntry implements Comparable<DeadOfficerEntry>
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

        @Override
        public int compareTo(DeadOfficerEntry other) {
            int result = Integer.compare(deadCycle, other.deadCycle);
            if (result != 0) return result;
            result = Integer.compare(deadMonth, other.deadMonth);
            if (result != 0) return result;
            result = Integer.compare(deadDay, other.deadDay);
            return result;
        }
    }
}
