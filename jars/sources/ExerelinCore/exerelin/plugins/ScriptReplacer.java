package exerelin.plugins;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEventListener;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager;
import com.fs.starfarer.api.impl.campaign.intel.GenericMissionManager.GenericMissionCreator;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager.GenericBarEventCreator;

import java.util.ArrayList;
import java.util.List;

import static exerelin.plugins.ExerelinModPlugin.log;

public class ScriptReplacer {
    public static <T extends EveryFrameScript> boolean replaceScript(SectorAPI sector, Class toRemove, T toAdd)
    {
        boolean removedAny = false;
        for (EveryFrameScript script : sector.getScripts())
        {
            if (toRemove.isInstance(script))
            {
                // if toAdd is non-null, check if the current script is an instance of toAdd
                // so we don't keep replacing the already-correct script with new instances
                if (toAdd != null && toAdd.getClass().isInstance(script))
                    continue;
                
                log.info("Removing EveryFrameScript " + script.toString() + " | " + toRemove.getCanonicalName());
                
                sector.removeScript(script);
                if (script instanceof CampaignEventListener)
                    sector.removeListener((CampaignEventListener)script);
                
                if (toAdd != null)
                    sector.addScript(toAdd);
                
                removedAny = true;
                break;
            }
        }
        return removedAny;
    }
	
	public static <T extends GenericMissionCreator> boolean replaceMissionCreator(Class toRemove, T toAdd) {
		boolean removedAny = false;
		GenericMissionManager manager = GenericMissionManager.getInstance();
		if (!manager.hasMissionCreator(toRemove))
			return false;
        for (GenericMissionCreator creator : new ArrayList<>(manager.getCreators()))
        {
            if (toRemove.isInstance(creator))
            {
                if (toAdd != null && toAdd.getClass().isInstance(creator))
                    continue;
                
                log.info("Removing mission creator " + creator.toString() + " | " + toRemove.getCanonicalName());
                
                manager.getCreators().remove(creator);
                
                if (toAdd != null)
                    manager.addMissionCreator(toAdd);
                
                removedAny = true;
                break;
            }
        }
        return removedAny;
	}
	
	public static <T extends GenericBarEventCreator> boolean replaceBarEventCreator(Class toRemove, T toAdd) {
		boolean removedAny = false;
		BarEventManager bar = BarEventManager.getInstance();
		if (!bar.hasEventCreator(toRemove))
			return false;
		for (GenericBarEventCreator creator : new ArrayList<>(bar.getCreators()))
        {
            if (toRemove.isInstance(creator))
            {
                if (toAdd != null && toAdd.getClass().isInstance(creator))
                    continue;
                
                log.info("Removing bar event creator " + creator.toString() + " | " + toRemove.getCanonicalName());
                
                bar.getCreators().remove(creator);
                
                if (toAdd != null)
                    bar.addEventCreator(toAdd);
                
                removedAny = true;
                break;
            }
        }
        return removedAny;
	}
	
	public static void replaceIndustry(String industry) {
		for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) 
		{
			if (!market.hasIndustry(industry))
				continue;
			
			Industry old = market.getIndustry(industry);
			String aiCore = old.getAICoreId();
			List<SpecialItemData> installedItems = old.getVisibleInstalledItems();
			market.removeIndustry(industry, null, false);
			market.addIndustry(industry);
			Industry novus = market.getIndustry(industry);
			novus.setAICoreId(aiCore);
			if (!installedItems.isEmpty())
				novus.setSpecialItem(installedItems.get(0));
		}
	}
}
