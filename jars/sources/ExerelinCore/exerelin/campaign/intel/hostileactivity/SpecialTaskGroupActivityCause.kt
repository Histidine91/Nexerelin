package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.RepLevel
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.intel.specialforces.SpecialForcesIntel
import exerelin.utilities.NexUtilsFleet
import org.lwjgl.util.vector.Vector2f
import kotlin.math.roundToInt
import kotlin.math.sqrt

class SpecialTaskGroupActivityCause(intel: HostileActivityEventIntel?) : BaseHostileActivityCause2(intel) {

    companion object {
        const val PROGRESS_MULT = 0.5f;
    }

    override fun getTooltip(): TooltipCreator? {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any) {
                tooltip.addPara(
                    NexHostileActivityManager.getString("specialForcesDesc"), 0f
                )
            }
        }
    }

    override fun getProgress(): Int {
        var totalStr = 0f;
        for (sf : SpecialForcesIntel in getActiveSFFleets(null, true)) {
            var str : Float = if (sf.route.activeFleet != null) NexUtilsFleet.calculatePowerLevel(sf.route.activeFleet)/4f else sf.route.extra.strength;
            totalStr += str;
        }

        var mag = getMagnitudeForStr(totalStr)
        return mag.roundToInt()
    }

    override fun getDesc(): String? {
        return NexHostileActivityManager.getString("specialForcesName")
    }

    override fun getMagnitudeContribution(system: StarSystemAPI?): Float {
        return 0f
    }

    protected fun getMagnitudeForStr(totalStrength : Float) : Float {
        return -sqrt(totalStrength) * PROGRESS_MULT
    }

    protected fun getActiveSFFleets(system : StarSystemAPI?, playerOnly : Boolean) : List<SpecialForcesIntel> {
        val results = ArrayList<SpecialForcesIntel>();
        for (intel : IntelInfoPlugin in Global.getSector().intelManager.getIntel(SpecialForcesIntel :: class.java)) {
            val sf = intel as SpecialForcesIntel
            if (sf.isEnding || sf.isEnded) continue
            if (system != null && !isNear(sf.route.interpolatedHyperLocation, system.location)) continue
            if (playerOnly && !sf.isPlayer) continue;
            else if (sf.faction.isAtBest(Factions.PLAYER, RepLevel.FAVORABLE)) continue;

            results.add(sf)
        }
        return results
    }

    fun isNear(ourLocInHyper: Vector2f, hyperLoc: Vector2f?): Boolean {
        val maxRange = Global.getSettings().getFloat("commRelayRangeAroundSystem")
        val dist = Misc.getDistanceLY(ourLocInHyper, hyperLoc)
        return dist <= maxRange
    }
}