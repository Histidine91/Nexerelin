package data.shipsystems.scripts;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;

public class ECMJammer implements com.fs.starfarer.api.plugins.ShipSystemStatsScript
{
    private static final String DEBUFF_ID = "valk_ecm_jammer";
    public static final float DEBUFF_RANGE = 2000f;
    private static final float DEBUFF_RANGE_SQUARED = DEBUFF_RANGE * DEBUFF_RANGE;
    private static final float DEBUFF_STRENGTH = .80f;
    private static final Map debuffed = new WeakHashMap();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel)
    {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null)
        {
            return;
        }

        ShipAPI victim;
        for (Iterator enemies = AIUtils.getEnemiesOnMap(ship).iterator(); enemies.hasNext();)
        {
            victim = (ShipAPI) enemies.next();

            // Only debuff ships that aren't already being debuffed by another ship
            if (debuffed.containsKey(victim) && debuffed.get(victim) != ship)
            {
                continue;
            }

            // Debuff all enemies in range
            if (MathUtils.getDistanceSquared(victim, ship) <= DEBUFF_RANGE_SQUARED)
            {
                applyDebuff(victim, effectLevel);
                debuffed.put(victim, ship);
            }
            // Remove debuffs from enemies that moved out of our range
            else if (debuffed.containsKey(victim))
            {
                unapplyDebuff(victim);
                debuffed.remove(victim);
            }
        }
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id)
    {
        ShipAPI ship = (ShipAPI) stats.getEntity();
        if (ship == null)
        {
            return;
        }

        // Remove all debuffs created by this ship
        Map.Entry debuff;
        for (Iterator allDebuffs = debuffed.entrySet().iterator(); allDebuffs.hasNext();)
        {
            debuff = (Map.Entry) allDebuffs.next();
            if (debuff.getValue() == ship)
            {
                unapplyDebuff((ShipAPI) debuff.getKey());
                allDebuffs.remove();
            }
        }
    }

    public void applyDebuff(ShipAPI victim, float effectLevel)
    {
        victim.getMutableStats().getSightRadiusMod().modifyMult(DEBUFF_ID,
                1.0f - (DEBUFF_STRENGTH * effectLevel));
    }

    public void unapplyDebuff(ShipAPI victim)
    {
        victim.getMutableStats().getSightRadiusMod().unmodifyMult(DEBUFF_ID);
    }

    @Override
    public StatusData getStatusData(int index, State state, float effectLevel)
    {
        if (index == 0)
        {
            return new StatusData("Jamming enemy radar at " + (int) (DEBUFF_STRENGTH
                    * effectLevel * 100) + "% effectiveness", false);
        }

        return null;
    }
}
