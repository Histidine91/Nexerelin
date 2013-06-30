package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.Random;
import org.lwjgl.util.vector.Vector2f;

public class ExtraDamageOnHitEffectBarbarossa implements OnHitEffectPlugin
{
    private static final Random rng = new Random();
    private static final String SOUND_ID = "thule_explosion_barbarossa";
    private static final float EXTRA_DAMAGE_CHANCE = 1f;
    private static final float MIN_EXTRA_DAMAGE = 50f;
    private static final float MAX_EXTRA_DAMAGE = 150f;
    private static final DamageType DAMAGE_TYPE = DamageType.ENERGY;

    private static float getRandomNumberInRange(float min, float max)
    {
        return rng.nextFloat() * (max - min) + min;
    }

    @Override
    public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, CombatEngineAPI engine)
    {
        // Check if we hit a ship (not its shield)
        if (target instanceof ShipAPI && !shieldHit
                && Math.random() <= EXTRA_DAMAGE_CHANCE)
        {
            // Apply extra damage
            engine.applyDamage(target, point,
                    getRandomNumberInRange(MIN_EXTRA_DAMAGE,
                    MAX_EXTRA_DAMAGE), DAMAGE_TYPE, 0f, false,
                    true, projectile.getSource());

            // Sound follows enemy that was hit
            Global.getSoundPlayer().playSound(SOUND_ID, 1f,1f,
                    target.getLocation(), target.getVelocity());
        }
    }
}