package exerelin.campaign.intel.merc;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyInteractionListener;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.ImportantPeopleAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.intel.merc.MercDataManager.MercCompanyDef;
import exerelin.utilities.NexUtils;
import org.apache.log4j.Logger;

import java.util.*;

public class MercSectorManager implements ColonyInteractionListener, EconomyTickListener {
	
	public static Logger log = Global.getLogger(MercSectorManager.class);
	
	public static final boolean DEBUG_MODE = false;
	public static final String DATA_KEY = "nex_mercSectorManager";
	public static final String MEMORY_KEY_NO_REP = "$nex_no_AIM_representative";
	public static final int STAY_AT_MARKET_TIME = 10;	// in economyTicks
	
	protected Map<MarketAPI, PersonAPI> aimReps = new HashMap<>();
	// Once a merc is shown at a particular location, they'll stay there for a while
	// so the player won't magically meet them again on the other end of the core worlds
	protected Map<String, Pair<MarketAPI, Integer>> currentLocations = new HashMap<>();
	
	protected Map<String, Integer> numTimesHired = new HashMap<>();
	
	public void init() {
		Global.getSector().getListenerManager().addListener(this);
		Global.getSector().getPersistentData().put(DATA_KEY, this);
	}
	
	public static MercSectorManager getInstance() {
		return (MercSectorManager)Global.getSector().getPersistentData().get(DATA_KEY);
	}
	
	public int getNumTimesHired(String id) {
		Integer i = numTimesHired.get(id);
		if (i == null) return 0;
		return i;
	}
	
	public void updateCurrentLocation(String id, MarketAPI market, int time) 
	{
		Pair<MarketAPI, Integer> current = currentLocations.get(id);
		if (current == null) {
			currentLocations.put(id, new Pair<>(market, time));
			return;
		}
		current.one = market;
		current.two = time;
	}
	
	public void reportMercShown(String id, MarketAPI market) 
	{
		log.info(String.format("Showing merc %s at %s", id, market.getName()));
		updateCurrentLocation(id, market, STAY_AT_MARKET_TIME);
	}
	
	public void reportMercHired(String id, MarketAPI market) {
		//currentLocations.put(id, new Pair<>((MarketAPI)null, Global.getSettings().getInt("economyIterPerMonth") * 10));
		NexUtils.modifyMapEntry(numTimesHired, id, 1);
	}
	
	public void reportMercLeft(String id, MarketAPI market) {
		updateCurrentLocation(id, null, STAY_AT_MARKET_TIME);
	}
	
	public List<MercContractIntel> getAvailableHires(MarketAPI market) {
		
		WeightedRandomPicker<MercContractIntel> picker = new WeightedRandomPicker<>();
		
		List<MercContractIntel> results = new ArrayList<>();
		for (MercCompanyDef def : MercDataManager.getAllDefs()) {
			String id = def.id;
			
			// check whether the merc company is somewhere else
			if (!DEBUG_MODE && currentLocations.containsKey(id) && currentLocations.get(id).one != market) 
			{
				log.info(String.format("Merc %s was already shown elsewhere, skipping", id));
				continue;
			}
			//Global.getLogger(this.getClass()).info("Testing merc company " + def.id);
			MercContractIntel intel = new MercContractIntel(id);
			intel.init(market);
			if (intel.getOfferedFleet() == null) continue;	// not valid at this location
			
			picker.add(intel, def.pickChance);
		}
		
		int max = MercDataManager.companiesForHire;
		int bonusCount = picker.getItems().size() - MercDataManager.companiesForHire;
		int bonus = (int)Math.floor(bonusCount * MercDataManager.companiesForHireMult);
		if (bonus > 0) max += bonus;		
		if (Global.getSettings().isDevMode() || DEBUG_MODE) max = Math.max(12, max);
		
		while (!picker.isEmpty()) {
			MercContractIntel intel = picker.pickAndRemove();
			results.add(intel);
			reportMercShown(intel.getDef().id, market);
			
			if (results.size() >= max) break;
		}
		
		return results;
	}

	public MercContractIntel pickPatrolCompany(Random random) {

		WeightedRandomPicker<MercContractIntel> picker = new WeightedRandomPicker<>(random);
		MercContractIntel ongoing = MercContractIntel.getOngoing();

		for (MercCompanyDef def : MercDataManager.getAllDefs()) {
			if (!def.canPatrol) continue;
			String id = def.id;

			// check whether the merc company is somewhere else
			if (false && !DEBUG_MODE && currentLocations.containsKey(id))
			{
				log.info(String.format("Merc %s was already shown elsewhere, skipping", id));
				continue;
			}
			if (ongoing != null && id.equals(ongoing.companyId)) continue;

			// relationship check
			if (def.minRep != null) {
				if (!Global.getSector().getPlayerFaction().isAtWorst(def.factionId, def.minRep)) {
					continue;
				}
			}

			//Global.getLogger(this.getClass()).info("Testing merc company " + def.id);
			MercContractIntel intel = new MercContractIntel(id);
			picker.add(intel, def.pickChance);
		}

		return picker.pick();
	}
	
	public boolean addRepresentativeToMarket(MarketAPI market)
	{
		if (aimReps.containsKey(market))
		{
			//log.debug("Market " + market.getId() + " already has AIM rep, not adding");
			return false;
		}
		
		SectorAPI sector = Global.getSector();
		ImportantPeopleAPI ip = sector.getImportantPeople();
		FactionAPI independent = sector.getFaction(Factions.INDEPENDENT);
		PersonAPI rep = independent.createRandomPerson();
		rep.setRankId(Ranks.CITIZEN);
		rep.setPostId("nex_AIM_representative");
		rep.getMemory().set("$nex_isAIMRep", true);

		market.getCommDirectory().addPerson(rep);
		market.addPerson(rep);
		ip.addPerson(rep);
		ip.getData(rep).getLocation().setMarket(market);
		
		aimReps.put(market, rep);
		return true;
	}
	
	public boolean removeRepresentativeFromMarket(MarketAPI market)
	{
		if (!aimReps.containsKey(market))
		{
			//log.debug("Market " + market.getId() + " does not have an AIM rep to remove");
			return false;
		}
		PersonAPI rep = aimReps.get(market);
		ImportantPeopleAPI ip = Global.getSector().getImportantPeople();
		market.getCommDirectory().removePerson(rep);
		market.removePerson(rep);
		ip.removePerson(rep);
		
		aimReps.remove(market);
		return true;
	}
	
	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {
		int reqSize = 6;
		if (market.getFactionId().equals(Factions.INDEPENDENT))
			reqSize--;
		
		boolean enabled = MercDataManager.allowAtFaction(market.getFactionId())
				&& (market.getSize() >= reqSize || Misc.isMilitary(market));
		enabled = enabled && !market.isHidden();
		enabled = enabled && !market.getMemoryWithoutUpdate().getBoolean(MEMORY_KEY_NO_REP);
		
		if (enabled) {
			addRepresentativeToMarket(market);
		} else {
			removeRepresentativeFromMarket(market);
		}
	}

	@Override
	public void reportPlayerClosedMarket(MarketAPI market) {}

	@Override
	public void reportPlayerOpenedMarketAndCargoUpdated(MarketAPI market) {}

	@Override
	public void reportPlayerMarketTransaction(PlayerMarketTransaction transact) {}

	@Override
	public void reportEconomyTick(int numIter) {
		List<String> toRemove = new ArrayList<>();
		for (String companyId : currentLocations.keySet()) {
			Pair<MarketAPI, Integer> entry = currentLocations.get(companyId);
			entry.two = entry.two - 1;
			if (entry.two <= 0) {
				toRemove.add(companyId);
			}
		}
		for (String companyId : toRemove) {
			currentLocations.remove(companyId);
		}
	}

	@Override
	public void reportEconomyMonthEnd() {}
	
}
