package exerelin.campaign

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.LocationAPI
import com.fs.starfarer.api.campaign.listeners.CurrentLocationChangedListener
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_IsFactionRuler
import com.fs.starfarer.api.util.Misc
import exerelin.utilities.NexUtilsFaction

class TransponderCheckBlockScript : EveryFrameScript, CurrentLocationChangedListener {

    var location : LocationAPI? = null
    var isOwner = false

    companion object {
        @JvmStatic
        fun create(): TransponderCheckBlockScript? {
            val script = TransponderCheckBlockScript()
            Global.getSector().addTransientScript(script)
            Global.getSector().listenerManager.addListener(script, true)
            script.reportCurrentLocationChanged(null, Global.getSector().currentLocation)
            return script
        }

        const val IGNORE_TRANSPONDER_CHECK_BLOCK = "\$nex_ignoreTransponderBlockCheck"
    }

    fun setNoTransponderCheckIfNeeded(loc: LocationAPI?) {
        if (!isOwner) return
        for (fleet in loc!!.fleets) {

            if (fleet.faction.isPlayerFaction) continue
            if (!fleet.memoryWithoutUpdate.getBoolean(MemFlags.MEMORY_KEY_PATROL_FLEET)) continue
            if (fleet.memoryWithoutUpdate.getBoolean(IGNORE_TRANSPONDER_CHECK_BLOCK)) continue
            Misc.setFlagWithReason(
                fleet.memoryWithoutUpdate,
                MemFlags.MEMORY_KEY_PATROL_ALLOW_TOFF,
                "player_system_owner",
                true,
                1f
            )
        }
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun runWhilePaused(): Boolean {
        return false
    }

    override fun advance(amount: Float) {
        setNoTransponderCheckIfNeeded(location)
    }

    override fun reportCurrentLocationChanged(prev: LocationAPI?, curr: LocationAPI?) {
        location = curr
        var owner = if (location != null && !location!!.isHyperspace) NexUtilsFaction.getSystemOwner(location) else null
        isOwner = owner != null && Nex_IsFactionRuler.isRuler(owner)
    }
}
