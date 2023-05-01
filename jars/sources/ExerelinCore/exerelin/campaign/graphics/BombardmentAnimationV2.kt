package exerelin.campaign.graphics

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin
import com.fs.starfarer.api.util.Misc
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.sqrt

/**
 * Copy of MarketCMD.BombardmentAnimation with new args.
 */
open class BombardmentAnimationV2(var num: Int, var target: SectorEntityToken, var sizeMult: Float, var color: Color) : EveryFrameScript {
    var added = 0
    var elapsed = 0f

    companion object {
        @JvmStatic fun addBombardVisual(target: SectorEntityToken?, sizeMult: Float) {
            addBombardVisual(target, sizeMult, DEFAULT_COLOR)
        }

        @JvmStatic fun addBombardVisual(target: SectorEntityToken?, sizeMult: Float, color: Color) {
            if (target != null && target.isInCurrentLocation) {
                var num = (target.radius * target.radius / 300f).toInt()
                num *= 2
                if (num > 150) num = 150
                if (num < 10) num = 10
                target.addScript(BombardmentAnimationV2(num, target, sizeMult, color))
            }
        }

        @JvmStatic val DEFAULT_COLOR = Color(255, 165, 100, 255)
    }

    override fun runWhilePaused(): Boolean {
        return false
    }

    override fun isDone(): Boolean {
        return added >= num
    }

    override fun advance(amount: Float) {
        elapsed += amount * Math.random().toFloat()
        if (elapsed < 0.03f) return
        elapsed = 0f
        var curr = Math.round(Math.random() * 4).toInt()
        if (curr < 1) curr = 0
        var vel = Vector2f()
        if (target.orbit != null && target.circularOrbitRadius > 0 && target.circularOrbitPeriod > 0 && target.orbitFocus != null) {
            val circumference = 2f * Math.PI.toFloat() * target.circularOrbitRadius
            val speed = circumference / target.circularOrbitPeriod
            val dir = Misc.getAngleInDegrees(target.location, target.orbitFocus.location) + 90f
            vel = Misc.getUnitVectorAtDegreeAngle(dir)
            vel.scale(speed / Global.getSector().clock.secondsPerDay)
        }
        for (i in 0 until curr) {
            var glowSize = 50f + 50f * Math.random().toFloat()
            glowSize *= sizeMult
            val angle = Math.random().toFloat() * 360f
            var dist = Math.sqrt(Math.random()).toFloat() * target.radius
            val factor = 0.5f + 0.5f * (1f - Math.sqrt((dist / target.radius).toDouble())
                .toFloat())
            glowSize *= factor
            val loc = Misc.getUnitVectorAtDegreeAngle(angle)
            loc.scale(dist)
            Vector2f.add(loc, target.location, loc)
            val c2 = Misc.scaleColor(color, factor)
            //c2 = color;
            Misc.addHitGlow(target.containingLocation, loc, vel, glowSize, c2)
            added++
            if (i == 0) {
                dist = Misc.getDistance(loc, Global.getSector().playerFleet.location)
                if (dist < HyperspaceTerrainPlugin.STORM_STRIKE_SOUND_RANGE) {
                    var volumeMult = 1f - dist / HyperspaceTerrainPlugin.STORM_STRIKE_SOUND_RANGE
                    volumeMult *= sqrt(sizeMult).toFloat()
                    volumeMult = sqrt(volumeMult.toDouble()).toFloat()
                    volumeMult *= 0.1f * factor
                    if (volumeMult > 0) {
                        Global.getSoundPlayer().playSound("mine_explosion", 1f, 1f * volumeMult, loc, Misc.ZERO)
                    }
                }
            }
        }
    }

    init {
        this.num = num
        this.target = target
    }
}