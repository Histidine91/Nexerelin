package exerelin.plugins;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventManagerAPI;
import com.fs.starfarer.api.campaign.events.CampaignEventTarget;
import com.fs.starfarer.api.campaign.events.EventProbabilityAPI;
import com.fs.starfarer.api.impl.campaign.CoreRuleTokenReplacementGeneratorImpl;
import com.fs.starfarer.api.impl.campaign.fleets.CustomFleets;
import com.fs.starfarer.api.impl.campaign.ids.Events;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.CovertOpsManager;
import exerelin.campaign.DiplomacyManager;
import exerelin.campaign.SectorManager;
import exerelin.utilities.ExerelinConfig;
import exerelin.world.ResponseFleetManager;
import java.util.List;

public class ExerelinLifecyclePlugin extends BaseModPlugin {

    @Override
    public void onGameLoad() {
        // the token replacement generators don't get saved
        // add them on every game load
        Global.getSector().getRules().addTokenReplacementGenerator(new CoreRuleTokenReplacementGeneratorImpl());
        
        ExerelinConfig.loadSettings();
        Global.getSector().addScript(SectorManager.create());
        Global.getSector().addScript(DiplomacyManager.create());
        Global.getSector().addScript(ResponseFleetManager.create());
        Global.getSector().addScript(CovertOpsManager.create());
        
        if (!Global.getSector().getEventManager().isOngoing(null, "exerelin_faction_salary")) {
            Global.getSector().getEventManager().startEvent(null, "exerelin_faction_salary", null);
        }
		if (!Global.getSector().getEventManager().isOngoing(null, "exerelin_faction_insurance")) {
            Global.getSector().getEventManager().startEvent(null, "exerelin_faction_insurance", null);
        }
    }

    @Override
    public void onNewGame() {

    }

    @Override
    public void onNewGameAfterTimePass() {
        new CustomFleets().spawn();


        CampaignEventManagerAPI eventManager = Global.getSector().getEventManager();
        //MarketAPI jangala = Global.getSector().getEconomy().getMarket("jangala");
        //eventManager.startEvent(new CampaignEventTarget(jangala), Events.SYSTEM_BOUNTY, null);

        List<String> pirateFactions = DiplomacyManager.getPirateFactionsCopy();
        WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<>();
        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            if (pirateFactions.contains( market.getFactionId() )) {
                continue;
            }
            EventProbabilityAPI ep = eventManager.getProbability(Events.FOOD_SHORTAGE, market);
            if (eventManager.isOngoing(ep)) continue;
            if (ep.getProbability() < 0.05f) continue;

            picker.add(market, ep.getProbability());
        }

        MarketAPI pick = picker.pick();
        if (pick != null) {
            eventManager.startEvent(new CampaignEventTarget(pick), Events.FOOD_SHORTAGE, null);
        }
    }

    @Override
    public void onNewGameAfterEconomyLoad() {

        for (MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            SectorEntityToken entity = market.getPrimaryEntity();
            if (entity == null) continue;
            LocationAPI location = entity.getContainingLocation();
            if (location == null) continue;

            int numJunk = 5 + market.getSize() * 10;
            if (market.getSize() < 5) {
                numJunk = (int) Math.max(1, numJunk * 0.5f);
            }
            float radius = entity.getRadius() + 100f;
            float minOrbitDays = radius / 20;
            float maxOrbitDays = minOrbitDays + 10f;

            location.addOrbitalJunk(entity,
                    "orbital_junk", // from custom_entities.json
                    numJunk, // num of junk
                    12, 20, // min/max sprite size (assumes square)
                    radius, // orbit radius
                    //70, // orbit width
                    110, // orbit width
                    minOrbitDays, // min orbit days
                    maxOrbitDays, // max orbit days
                    60f, // min spin (degress/day)
                    360f); // max spin (degrees/day)
        }

    }



}
