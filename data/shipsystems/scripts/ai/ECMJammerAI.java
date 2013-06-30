package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import data.shipsystems.scripts.ECMJammer;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

public class ECMJammerAI implements ShipSystemAIScript
{
    private ShipAPI ship;
    private ShipSystemAPI system;

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine)
    {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target)
    {
        if (!system.isActive() && AIUtils.canUseSystemThisFrame(ship))
        {
            if (!AIUtils.getNearbyEnemies(ship, ECMJammer.DEBUFF_RANGE).isEmpty())
            {
                ship.useSystem();
            }
        }
    }
}
