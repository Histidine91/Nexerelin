package data.hullmods;

import com.fs.starfarer.api.combat.HullModEffect;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class RuneStats implements HullModEffect
{
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id)
    {
        stats.getCombatWeaponRepairTimeMult().modifyMult(id, 0.01f);// the number followed by f is the multiplier, edit to change value.
        stats.getCombatEngineRepairTimeMult().modifyMult(id, 0.01f);
        stats.getDamageToTargetWeaponsMult().modifyMult(id, 0.01f);
        stats.getDamageToTargetEnginesMult().modifyMult(id, 0.01f);
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id)
    {
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize)
    {
        return null;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship)
    {
        return (ship.getHullSpec().getHullId().startsWith("thule_"));
    }
}
