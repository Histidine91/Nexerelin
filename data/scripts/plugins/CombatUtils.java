package data.scripts.plugins;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.Iterator;
import java.util.List;
import org.lwjgl.util.vector.Vector2f;

public class CombatUtils implements EveryFrameCombatPlugin
{
    private static CombatEngineAPI engine;
    private static float combatTime;

    public static CombatEngineAPI getCombatEngine()
    {
        return engine;
    }

    public static float getElapsedCombatTime()
    {
        return combatTime;
    }

    public static ShipAPI getOwner(MutableShipStatsAPI stats)
    {
        if (engine == null)
        {
            return null;
        }

        ShipAPI tmp;
        for (Iterator allShips = engine.getShips().iterator(); allShips.hasNext();)
        {
            tmp = (ShipAPI) allShips.next();

            if (tmp.getMutableStats() == stats)
            {
                return tmp;
            }
        }

        return null;
    }

    public static float getDistance(CombatEntityAPI obj1, CombatEntityAPI obj2)
    {
        return getDistance(obj1.getLocation(), obj2.getLocation());
    }

    public static float getDistance(CombatEntityAPI entity, Vector2f vector)
    {
        return getDistance(entity.getLocation(), vector);
    }

    public static float getDistance(Vector2f vector1, Vector2f vector2)
    {
        float a = vector1.x - vector2.x;
        float b = vector1.y - vector2.y;
        return (float) Math.hypot(a, b);
    }

    @Override
    public void advance(float amount, List events)
    {
        combatTime += amount;
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
        CombatUtils.engine = engine;
        combatTime = 0f;
    }
}
