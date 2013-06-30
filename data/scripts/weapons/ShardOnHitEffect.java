package data.scripts.weapons;
import com.fs.starfarer.api.Global;
import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatEntityAPI;
import com.fs.starfarer.api.combat.DamageType;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.OnHitEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import java.lang.Math;

public class ShardOnHitEffect implements OnHitEffectPlugin {
    
        public Vector2f particleVelocity1;
        public Vector2f particleVelocity2;
        public float damageAmount = 125f; //set this to whatever the damage should be
        public float empAmount = 0f; //set this to whatever the EMP damage should be
        public Color particleColor = new Color(130,205,217,255);
        public float particleSize = 3f;
        public float particleBrightness = 0.98f;
        public float particleDuration = 2.0f;
		public float explosionSize = 179f;
		public float explosionSize2 = 61f;
		public float explosionDuration = 0.37f;
		public float explosionDuration2 = 1.3f;
        public float pitch = 1f; //sound pitch. Default seems to be 1
        public float volume = 0.5f; //volume, scale from 0-1
        public String soundName = "shardsplode"; //assign the sound we want to play
        public boolean dealsSoftFlux = false;
        
        @Override
	public void onHit(DamagingProjectileAPI projectile, CombatEntityAPI target, Vector2f point, boolean shieldHit, CombatEngineAPI engine) {
            if ((float) Math.random() > 0.80f && !shieldHit && target instanceof ShipAPI) {
                particleVelocity1 = projectile.getVelocity();
                particleVelocity2 = projectile.getVelocity();
                particleVelocity1.scale(0.02f);
                particleVelocity2.scale(0.06f);

				damageAmount += (75f * Math.random());
                engine.applyDamage(target, point, damageAmount, DamageType.ENERGY, empAmount, false, dealsSoftFlux, engine);
                engine.addHitParticle(point, particleVelocity1, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity2, particleSize, particleBrightness, particleDuration, particleColor);	
                engine.addHitParticle(point, particleVelocity1, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity2, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity1, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity2, particleSize, particleBrightness, particleDuration, particleColor);	
                engine.addHitParticle(point, particleVelocity1, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity2, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity2, particleSize, particleBrightness, particleDuration, particleColor);	
                engine.addHitParticle(point, particleVelocity1, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity2, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity1, particleSize, particleBrightness, particleDuration, particleColor);
                engine.addHitParticle(point, particleVelocity2, particleSize, particleBrightness, particleDuration, particleColor);					
                engine.spawnExplosion(point, particleVelocity1, particleColor, explosionSize, explosionDuration);
                engine.spawnExplosion(point, particleVelocity2, particleColor, explosionSize2, explosionDuration2);					
                Global.getSoundPlayer().playSound(soundName, pitch, volume, point, projectile.getVelocity());
            }
	}
}