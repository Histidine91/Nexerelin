package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import java.util.*;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;


public class ms_panChargeFX implements EveryFrameWeaponEffectPlugin {
    //particle spawn radius
    public static final float chargeRadius = 1f;
    //particle opacity
    public static final float chargeOp = .5f;
    //particle life span
    public static final float life = .1f;
    //particle and emp arc color
    public static final Color effectColor = new Color(165,215,145,150);
    //and the color of the smoke particles emitted from the vents
    public static final Color smokeColor = new Color(165,215,145,150);
    //weapon chargeup and cooldown
    public static final float charging = 4f;
    public static final float cooling = 2f;
    //and mapping out the weapons in use
    
    
    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon)
    {
        if (engine.isPaused()) return;
        
        ShipAPI ship = weapon.getShip();
        float remaining = 0;
        
        // Fade away when about to expire
        float glowStrength = (remaining > life ? 1f : remaining / life);

        // How far from the edge the particle should spawn
        float fraction = .5f + ((float) Math.random() / 2f);
        
        if (weapon.isFiring() && weapon.getChargeLevel() <= 1.0f)
        {
            Vector2f chargeCenter = weapon.getLocation();
            Vector2f shipVelocity = ship.getVelocity();
            Vector2f particlePos, particleVel;
                    
            //Creates a glowy effect right on top of the weapon
            engine.addSmoothParticle(chargeCenter, shipVelocity, 2f, 1f, .1f, effectColor);
            engine.addHitParticle(chargeCenter, shipVelocity, 0.25f, 1f, .1f, effectColor);
                
            //Creates tiny particles that are drawn into the center
            particlePos = MathUtils.getRandomPointOnCircumference(chargeCenter,
                chargeRadius * fraction);
            particleVel = Vector2f.sub(chargeCenter, particlePos, null);
            engine.addHitParticle(particlePos, particleVel, .1f,
                chargeOp * glowStrength,
                fraction, effectColor);
        }
    }
}
