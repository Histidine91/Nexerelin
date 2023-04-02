package exerelin.campaign.ai.action.fleet;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathBaseIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCells;
import com.fs.starfarer.api.impl.campaign.intel.bases.LuddicPathCellsIntel;
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateActivity;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import exerelin.campaign.ai.concern.StrategicConcern;
import exerelin.campaign.fleets.InvasionFleetManager;
import exerelin.utilities.NexUtilsFaction;

public class BaseStrikeAction extends OffensiveFleetAction {
    @Override
    public InvasionFleetManager.EventType getEventType() {
        return InvasionFleetManager.EventType.BASE_STRIKE;
    }

    @Override
    public boolean generate() {
        WeightedRandomPicker<MarketAPI> targetPicker = new WeightedRandomPicker<>();

        for (MarketAPI market : NexUtilsFaction.getFactionMarkets(ai.getFactionId())) {
            if (market.hasCondition(Conditions.PIRATE_ACTIVITY) && market.getFaction().isHostileTo(Factions.PIRATES)) {
                MarketConditionAPI cond = market.getCondition(Conditions.PIRATE_ACTIVITY);
                PirateActivity plugin = (PirateActivity)cond.getPlugin();
                targetPicker.add(plugin.getIntel().getMarket());
            }
            if (market.hasCondition(Conditions.PATHER_CELLS) && market.getFaction().isHostileTo(Factions.LUDDIC_PATH)) {
                MarketConditionAPI cond = market.getCondition(Conditions.PATHER_CELLS);
                LuddicPathCells cellCond = (LuddicPathCells)(cond.getPlugin());
                LuddicPathCellsIntel cellIntel = cellCond.getIntel();
                LuddicPathBaseIntel base;

                boolean sleeper = cellIntel.getSleeperTimeout() > 0;
                if (sleeper) continue;
                base = LuddicPathCellsIntel.getClosestBase(market);
                if (base == null) continue;

                targetPicker.add(base.getMarket());
            }
        }

        MarketAPI target = targetPicker.pick();
        if (target == null) return false;
        delegate = InvasionFleetManager.getManager().spawnBaseStrikeFleet(ai.getFaction(), target);
        if (delegate == null) return false;

        return super.canUse(concern);
    }

    @Override
    public boolean canUse(StrategicConcern concern) {
        return concern.getDef().hasTag("canBaseStrike");
    }
}
