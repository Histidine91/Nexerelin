package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;

public class CommandRelay implements ShipSystemStatsScript
{
    private static final String BUFF_ID = "valk_command_relay";
    private static final float BUFF_RANGE_PAST_RADIUS = 2000f;
    private static final float PERCENT_PENALTY_FOR_BUFFER = -15f;
    private static final float PERCENT_BONUS_FOR_CAPITALS = 5f;
    private static final float PERCENT_BONUS_FOR_CRUISERS = 10f;
    private static final float PERCENT_BONUS_FOR_DESTROYERS = 15f;
    private static final float PERCENT_BONUS_FOR_FRIGATES = 25f;
    private static final float PERCENT_BONUS_FOR_FIGHTERS = 25f;
    private static final Map buffed = new WeakHashMap();

    public static float getBuffRange(ShipAPI ship)
    {
        return BUFF_RANGE_PAST_RADIUS + ship.getCollisionRadius();
    }

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)
    {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null)
        {
            // This should never occur, but just in case...
            return;
        }

        // Place any penalties for the buffing ship here
        ship.getMutableStats().getMaxSpeed().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);
        ship.getMutableStats().getAcceleration().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);
        ship.getMutableStats().getTurnAcceleration().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);

        ship.getMutableStats().getFluxCapacity().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);
        ship.getMutableStats().getFluxDissipation().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);

        ship.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);
        ship.getMutableStats().getBallisticWeaponDamageMult().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);
        ship.getMutableStats().getMissileWeaponDamageMult().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);

        ship.getMutableStats().getAutofireAimAccuracy().modifyPercent(id,
                PERCENT_PENALTY_FOR_BUFFER * effectLevel);

        ShipAPI toBuff;
        float actualRangeSquared = (float) Math.pow(getBuffRange(ship), 2);
        for (Iterator allies = AIUtils.getAlliesOnMap(ship).iterator(); allies.hasNext();)
        {
            toBuff = (ShipAPI) allies.next();

            // Only buff ships that aren't already being buffed by another ship
            if (buffed.containsKey(toBuff) && buffed.get(toBuff) != ship)
            {
                continue;
            }

            // Buff all allies in range
            if (MathUtils.getDistanceSquared(toBuff, ship) <= actualRangeSquared)
            {
                applyBuff(toBuff, effectLevel);
                buffed.put(toBuff, ship);
            }
            // Remove buffs from allies that moved out of our range
            else if (buffed.containsKey(toBuff))
            {
                unapplyBuff(toBuff);
                buffed.remove(toBuff);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id)
    {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null)
        {
            // This should never occur, but just in case...
            return;
        }

        // Remove any penalties given to the buffing ship here
        ship.getMutableStats().getMaxSpeed().unmodify(id);
        ship.getMutableStats().getAcceleration().unmodify(id);
        ship.getMutableStats().getTurnAcceleration().unmodify(id);

        ship.getMutableStats().getFluxCapacity().unmodify(id);
        ship.getMutableStats().getFluxDissipation().unmodify(id);

        ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(id);
        ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(id);
        ship.getMutableStats().getMissileWeaponDamageMult().unmodify(id);

        ship.getMutableStats().getAutofireAimAccuracy().unmodify(id);

        // Remove all buffs created by this ship
        Map.Entry toDebuff;
        for (Iterator allBuffs = buffed.entrySet().iterator(); allBuffs.hasNext();)
        {
            toDebuff = (Map.Entry) allBuffs.next();
            if (toDebuff.getValue() == ship)
            {
                unapplyBuff((ShipAPI) toDebuff.getKey());
                allBuffs.remove();
            }
        }
    }

    public void applyBuff(ShipAPI ship, float effectLevel)
    {
        float bonusPercent;
        if (ship.isCapital())
        {
            bonusPercent = PERCENT_BONUS_FOR_CAPITALS;
        }
        else if (ship.isCruiser())
        {
            bonusPercent = PERCENT_BONUS_FOR_CRUISERS;
        }
        else if (ship.isDestroyer())
        {
            bonusPercent = PERCENT_BONUS_FOR_DESTROYERS;
        }
        else if (ship.isFrigate())
        {
            bonusPercent = PERCENT_BONUS_FOR_FRIGATES;
        }
        else if (ship.isFighter() || ship.isDrone())
        {
            bonusPercent = PERCENT_BONUS_FOR_FIGHTERS;
        }
        else
        {
            bonusPercent = .2f;
        }

        ship.getMutableStats().getMaxSpeed().modifyPercent(BUFF_ID, bonusPercent * effectLevel);
        ship.getMutableStats().getAcceleration().modifyPercent(BUFF_ID, bonusPercent * effectLevel);
        ship.getMutableStats().getTurnAcceleration().modifyPercent(BUFF_ID, bonusPercent * effectLevel);

        ship.getMutableStats().getFluxCapacity().modifyPercent(BUFF_ID, bonusPercent * effectLevel);
        ship.getMutableStats().getFluxDissipation().modifyPercent(BUFF_ID, bonusPercent * effectLevel);

        ship.getMutableStats().getEnergyWeaponDamageMult().modifyPercent(BUFF_ID, bonusPercent * effectLevel);
        ship.getMutableStats().getBallisticWeaponDamageMult().modifyPercent(BUFF_ID, bonusPercent * effectLevel);
        ship.getMutableStats().getMissileWeaponDamageMult().modifyPercent(BUFF_ID, bonusPercent * effectLevel);

        ship.getMutableStats().getAutofireAimAccuracy().modifyPercent(BUFF_ID, bonusPercent * effectLevel);
    }

    public void unapplyBuff(ShipAPI ship)
    {
        ship.getMutableStats().getMaxSpeed().unmodify(BUFF_ID);
        ship.getMutableStats().getAcceleration().unmodify(BUFF_ID);
        ship.getMutableStats().getTurnAcceleration().unmodify(BUFF_ID);

        ship.getMutableStats().getFluxCapacity().unmodify(BUFF_ID);
        ship.getMutableStats().getFluxDissipation().unmodify(BUFF_ID);

        ship.getMutableStats().getEnergyWeaponDamageMult().unmodify(BUFF_ID);
        ship.getMutableStats().getBallisticWeaponDamageMult().unmodify(BUFF_ID);
        ship.getMutableStats().getMissileWeaponDamageMult().unmodify(BUFF_ID);

        ship.getMutableStats().getAutofireAimAccuracy().unmodify(BUFF_ID);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel)
    {
        if (index == 0)
        {
            return new StatusData("Buffing nearby ships", false);
        }
        else if (index == 1)
        {
            return new StatusData("Diverting power from systems", true);
        }

        return null;
    }
}
