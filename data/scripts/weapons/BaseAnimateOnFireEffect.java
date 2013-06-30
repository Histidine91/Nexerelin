package data.scripts.weapons;

import com.fs.starfarer.api.AnimationAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import java.util.*;

public class BaseAnimateOnFireEffect implements EveryFrameWeaponEffectPlugin
{
    // Default to 15 frames per second
    private float timeSinceLastFrame, timeBetweenFrames = 1.0f / 15f;
    private Map pauseFrames = new HashMap();
    private int curFrame = 0, pausedFor = 0;
    private boolean isFiring = false;

    protected void setFramesPerSecond(float fps)
    {
        timeBetweenFrames = 1.0f / fps;
    }

    protected void pauseOnFrame(int frame, int pauseFor)
    {
        pauseFrames.put(frame, pauseFor);
    }

    private void incFrame(AnimationAPI anim)
    {
        if (pauseFrames.containsKey(curFrame))
        {
            if (pausedFor < (Integer) pauseFrames.get(curFrame))
            {
                pausedFor++;
                return;
            }
            else
            {
                pausedFor = 0;
            }
        }

        curFrame = Math.min(curFrame + 1, anim.getNumFrames() - 1);
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon)
    {
        if (engine.isPaused())
        {
            return;
        }

        AnimationAPI anim = weapon.getAnimation();
        anim.setFrame(curFrame);

        if (isFiring)
        {
            timeSinceLastFrame += amount;

            if (timeSinceLastFrame >= timeBetweenFrames)
            {
                timeSinceLastFrame = 0f;
                incFrame(anim);
                anim.setFrame(curFrame);

                if (curFrame == anim.getNumFrames() - 1)
                {
                    isFiring = false;
                }
            }
        }
        else
        {
            if (weapon.isFiring() && weapon.getChargeLevel() == 1.0f)
            {
                isFiring = true;
                incFrame(anim);
                anim.setFrame(curFrame);
            }
            else
            {
                curFrame = 0;
                anim.setFrame(curFrame);
            }
        }
    }
}