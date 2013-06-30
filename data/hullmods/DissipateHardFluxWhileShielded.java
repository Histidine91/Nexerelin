package data.hullmods;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import java.util.*;

public class DissipateHardFluxWhileShielded extends BaseHullMod
{
    private static final List allowedIds = new ArrayList();

    static
    {
        allowedIds.add("excalibur_corv_wing");
        allowedIds.add("insert another ship id that can have this hullmod here");
        // etc
    }

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id)
    {
        stats.getHardFluxDissipationFraction().modifyFlat(id, 1f);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship)
    {
        if (allowedIds.contains(ship.getHullSpec().getHullId()))
        {
            return true;
        }

        return false;
    }
}