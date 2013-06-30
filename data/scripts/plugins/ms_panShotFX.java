package data.scripts.plugins;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import java.awt.Color;
import java.util.*;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;


public class ms_panShotFX implements EveryFrameCombatPlugin {
    //How many frames between each explosion
    public static float explosionNum = 0.3f;
    public static float explosionInterval = 1f;
    private static final float explosionDur = .5f;
    //Effects color (should match the projectile color)
    public static final Color effectColor = new Color(165,215,145,150);
    private static final Map shots = new HashMap();
    
    
    static
        {
            shots.put("ms_pandora_wave", false);
        }
    
    @Override
    public void advance(float amount, List events)
    {
        CombatEngineAPI engine = CombatUtils.getCombatEngine();
        DamagingProjectileAPI proj;
        
        for (Iterator iter = engine.getProjectiles().iterator(); iter.hasNext();)
        {
            proj = (DamagingProjectileAPI) iter.next();
            
            Vector2f spawn = proj.getLocation();
            Vector2f explosionVelocity = (Vector2f) new Vector2f(proj.getVelocity()).scale(0.9f);
            
            if (!shots.containsKey(proj.getProjectileSpecId()))
            {
                continue;
            }
            
            for (int x = 0; x < explosionNum; x++) {
                engine.spawnExplosion(spawn, explosionVelocity, effectColor, 2f, explosionDur);
            }
        }
    }

    @Override
    public void init(CombatEngineAPI engine)
    {
    }
}
