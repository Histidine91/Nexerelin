package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.BattleObjectiveAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import java.util.*;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class ms_pmineAI implements ShipSystemAIScript
{
    private static final String MINE_ID = "ms_pmine";
    private static final float MINE_WITHIN_RANGE = 500f;
    private static final int MAX_MINES_PER_OBJECTIVE = 2;
    private static CombatEngineAPI engine;
    private ShipSystemAPI system;
    private ShipAPI ship;

    private static int getMinesAroundObjective(BattleObjectiveAPI objective, int owner)
    {
        int totalMines = 0;

        DamagingProjectileAPI tmp;
        for (Iterator iter = CombatUtils.getProjectilesWithinRange(
                objective.getLocation(), MINE_WITHIN_RANGE).iterator();
                iter.hasNext();)
        {
            tmp = (DamagingProjectileAPI) iter.next();

            if (tmp.getOwner() == owner && MINE_ID.equals(tmp.getProjectileSpecId()))
            {
                totalMines++;
            }
        }

        return totalMines;
    }

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine)
    {
        this.ship = ship;
        this.system = system;
        ms_pmineAI.engine = engine;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target)
    {
        if (!AIUtils.canUseSystemThisFrame(ship))
        {
            return;
        }

        BattleObjectiveAPI nearestObjective = AIUtils.getNearestObjective(ship);

        // No objectives on the map
        if (nearestObjective == null)
        {
            return;
        }

        // Ship is within range of an objective
        if (MathUtils.getDistance(ship.getLocation(),
                nearestObjective.getLocation()) < MINE_WITHIN_RANGE)
        {
            // The objective isn't already saturated with this side's mines
            if (getMinesAroundObjective(nearestObjective, ship.getOwner())
                    < MAX_MINES_PER_OBJECTIVE)
            {
                ship.useSystem();
            }
        }
    }
}
