package data.scripts.plugins;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.FluxTrackerAPI;

public class isHulkCheck implements EveryFrameWeaponEffectPlugin
{

 public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon)	
  { 
   if (engine.isPaused()) return;

   AnimationAPI animation = weapon.getAnimation();
 
   if (weapon.getShip().isHulk()) 
    {
    animation.pause();
    }

   else
    {
     animation.play(); 
    }  

  }

}


