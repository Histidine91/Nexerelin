package exerelin.campaign.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexUtils;
import org.apache.log4j.Logger;
import org.lazywizard.console.Console;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MilitaryInfoHelper {
	
	public static Logger log = Global.getLogger(MilitaryInfoHelper.class);
	public static boolean loggingMode = false;

	protected static MilitaryInfoHelper currInstance;
	
	protected Set<String> haveHeavyIndustry = new HashSet<>();

	protected Map<LocationAPI, PatrolStrengthEntry> patrolStrength = new HashMap<>();
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.createInstance()
	/**
	 * Creates and stores an instance of the military info helper. Should be called on every game load.
	 * @param replace Replaces the existing instance of the helper if true. Should only be false if called from {@code getInstance},
	 *                   to avoid the helper being retained between sectors.
	 * @return 
	 */
	public static MilitaryInfoHelper createInstance(boolean replace) {
		if (currInstance != null) {
			if (replace) Global.getSector().getListenerManager().removeListener(currInstance);
			else return currInstance;
		}
		currInstance = new MilitaryInfoHelper();
		Global.getSector().getListenerManager().addListener(currInstance, true);
		
		currInstance.collectMilitaryData(true);
		return currInstance;
	}

	public static MilitaryInfoHelper getInstance() {
		return getInstance(true);
	}
	
	public static MilitaryInfoHelper getInstance(boolean createIfNeeded) {
		if (currInstance == null && createIfNeeded) return createInstance(false);
		return currInstance;
	}
	
	public static void logInfo(String str) {
		if (!loggingMode) return;
		log.info(str);
		Console.showMessage(str);
	}
	
	public static void setLoggingMode(boolean mode) {
		loggingMode = mode;
	}
	
	// runcode exerelin.campaign.econ.EconomyInfoHelper.getInstance().collectMilitaryData()
	public void collectMilitaryData(boolean firstRun)
	{
		Set<LocationAPI> populatedLocs = new HashSet<>();
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
			populatedLocs.add(market.getContainingLocation());
		}

		for (LocationAPI loc : populatedLocs) {
			PatrolStrengthEntry entry = new PatrolStrengthEntry(loc);
			for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
				if (market.isHidden()) continue;
				entry.addMarketStrength(market);
			}
			patrolStrength.put(loc, entry);
		}
	}

	public PatrolStrengthEntry getPatrolStrength(LocationAPI loc) {
		return patrolStrength.get(loc);
	}

	
	public static class PatrolStrengthEntry {
		public LocationAPI loc;
		public float total;
		public final Map<MarketAPI, Float> strByMarket = new HashMap<>();
		public final Map<String, Float> strByFaction = new HashMap<>();

		public PatrolStrengthEntry(LocationAPI loc) {
			this.loc = loc;
		}

		public void addMarketStrength(MarketAPI market) {
			float str = InvasionFleetManager.estimatePatrolStrength(market, 0);
			NexUtils.modifyMapEntry(strByMarket, market, str);
			NexUtils.modifyMapEntry(strByFaction, market.getFactionId(), str);
		}
	}
}
