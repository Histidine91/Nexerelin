package exerelin.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.EconomyTickListener;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import exerelin.utilities.ExerelinUtilsMarket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// I have no idea what to do with this right now
public class ColonyManager extends BaseCampaignEventListener implements EveryFrameScript, EconomyTickListener {
	
	public static final String PERSISTENT_KEY = "nex_colonyManager";
	public static final Set<String> NEEDED_OFFICIALS = new HashSet<>(Arrays.asList(
			Ranks.POST_ADMINISTRATOR, Ranks.POST_BASE_COMMANDER, 
			Ranks.POST_STATION_COMMANDER, Ranks.POST_PORTMASTER
	));
	
	public ColonyManager() {
		super(true);
	}
	
	protected boolean hasBaseOfficial(MarketAPI market)
	{
		for (CommDirectoryEntryAPI dir : market.getCommDirectory().getEntriesCopy())
		{
			if (dir.getType() != CommDirectoryEntryAPI.EntryType.PERSON) continue;
			PersonAPI person = (PersonAPI)dir.getEntryData();
			if (NEEDED_OFFICIALS.contains(person.getPostId()))
				return true;
		}
		return false;
	}
	
	// this exists because else it'd be a leak in constructor
	public void init() {
		Global.getSector().getPersistentData().put(PERSISTENT_KEY, this);
	}
	
	// add admin to player faction if needed
	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {
		if (market.getFaction().isPlayerFaction())
		{
			if (hasBaseOfficial(market)) return;
			ExerelinUtilsMarket.addPerson(Global.getSector().getImportantPeople(), 
					market, Ranks.CITIZEN, Ranks.POST_ADMINISTRATOR, true);
		}
	}
	
	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		
	}

	@Override
	public void reportEconomyTick(int iterIndex) {
		
	}

	@Override
	public void reportEconomyMonthEnd() {
		
	}
	
	public static ColonyManager getManager()
	{
		return (ColonyManager)Global.getSector().getPersistentData().get(PERSISTENT_KEY);
	}
}
