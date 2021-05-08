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
import exerelin.campaign.intel.merc.MercDataManager.MercCompanyDef;
import exerelin.utilities.NexUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MercSectorManager implements ColonyInteractionListener, EconomyTickListener {
	
	public static final String DATA_KEY = "nex_mercSectorManager";
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
	
	public void reportMercShown(String id, MarketAPI market) 
	{
		Pair<MarketAPI, Integer> current = currentLocations.get(id);
		if (current == null) {
			currentLocations.put(id, new Pair<>(market, STAY_AT_MARKET_TIME));
			return;
		}
		current.one = market;
		current.two = STAY_AT_MARKET_TIME;
	}
	
	public void reportMercHired(String id, MarketAPI market) {
		//currentLocations.put(id, new Pair<>((MarketAPI)null, Global.getSettings().getInt("economyIterPerMonth") * 10));
		NexUtils.modifyMapEntry(numTimesHired, id, 1);
	}
	
	public List<MercContractIntel> getAvailableHires(MarketAPI market) {
		
		// TODO: check availability
		
		List<MercContractIntel> results = new ArrayList<>();
		for (MercCompanyDef def : MercDataManager.getAllDefs()) {
			Global.getLogger(this.getClass()).info("Testing merc company " + def.id);
			MercContractIntel intel = new MercContractIntel(def.id);
			intel.init(market);
			results.add(intel);
			reportMercShown(def.id, market);
			if (results.size() > MercDataManager.companiesForHire) break;
		}
		return results;
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
		boolean enabled = MercDataManager.allowAtFaction(market.getFactionId())
				&& (market.getSize() >= 6 || Misc.isMilitary(market));
		
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
	public void reportPlayerMarketTransaction(PlayerMarketTransaction arg0) {}

	@Override
	public void reportEconomyTick(int numIter) {
		// TODO: increment merc status here
	}

	@Override
	public void reportEconomyMonthEnd() {}
	
}
