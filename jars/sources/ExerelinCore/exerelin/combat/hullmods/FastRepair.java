package exerelin.combat.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

public class FastRepair extends BaseHullMod {

    public static final float REPAIR_RATE_PERCENT = 200;
    public static final float FREE_REPAIR_PERCENT = 25;

    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize,
                                               MutableShipStatsAPI stats, String id) {
        stats.getRepairRatePercentPerDay().modifyPercent(id, 200);
        stats.getDynamic().getMod(Stats.INSTA_REPAIR_FRACTION).modifyFlat(id, FREE_REPAIR_PERCENT * 0.01f);
        stats.getDynamic().getMod(Stats.DMOD_ACQUIRE_PROB_MOD).modifyMult(id, 0);
        stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, 1000f);
    }

    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        if (index == 0) return "" + (int)REPAIR_RATE_PERCENT;
        if (index == 1) return "" + (int)FREE_REPAIR_PERCENT;
        return null;
    }

    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {

    }
}
