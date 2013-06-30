package data.shipsystems.scripts.ai;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import data.shipsystems.scripts.CommandRelay;
import org.lazywizard.lazylib.combat.AIUtils;
import org.lwjgl.util.vector.Vector2f;

public class CommandRelayAI implements ShipSystemAIScript
{
    private static final float MIN_HULL_LEVEL_TO_USE_SYSTEM = .25f;
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
        // Can use system, not terribly injured, and allies are nearby
        if (!system.isActive() && ship.getHullLevel() >= MIN_HULL_LEVEL_TO_USE_SYSTEM
                && AIUtils.canUseSystemThisFrame(ship) && !AIUtils.getNearbyAllies(ship,
                CommandRelay.getBuffRange(ship)).isEmpty())
        {
            ship.useSystem();
        }
    }
}
