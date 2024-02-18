package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.intel.hostileactivity.PoliceHostileActivityFactor.Companion.getFreePortScore
import exerelin.campaign.intel.hostileactivity.PoliceHostileActivityFactor.Companion.isDefeatedExpedition
import kotlin.math.roundToInt

class PoliceFreePortActivityCause(intel: HostileActivityEventIntel?) : BaseHostileActivityCause2(intel) {

    companion object {
        const val MAX_MAG = 0.45f

        @JvmStatic
        fun getProgressDiminishingReturns(progress : Int, unit : Float, mult : Float) : Int {
            var rem: Int = progress
            var adjusted = 0f
            while (rem > unit) {
                adjusted += unit
                rem -= unit.toInt()
                rem = (rem * mult).toInt()
            }
            adjusted += rem.toFloat()

            var reduced = adjusted.roundToInt()
            if (progress > 0 && reduced < 1) reduced = 1
            return reduced
        }
    }

    override fun getTooltip(): TooltipCreator {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any) {
                val f = Global.getSector().getFaction(Factions.PIRATES)
                val h = Misc.getHighlightColor()
                val label = tooltip.addPara(PoliceHostileActivityFactor.getString("activityCauseTooltip"), 0f)
                label.setHighlight(*PoliceHostileActivityFactor.getString("activityCauseTooltipHighlight").split(", ").toTypedArray())
                label.setHighlightColors(f.baseUIColor, h, h)
            }
        }
    }

    override fun getProgress(): Int {
        if (isDefeatedExpedition()) return 0
        var score = 0
        for (market in Misc.getPlayerMarkets(false)) {
            score += getFreePortScore(market!!).toInt()
        }

        score = getProgressDiminishingReturns(score, Global.getSettings().getFloat("patherProgressUnit"),
            Global.getSettings().getFloat("patherProgressMult"))

        return score
    }

    override fun getDesc(): String {
        return PoliceHostileActivityFactor.getString("activityCauseDesc")
    }

    override fun getMagnitudeContribution(system: StarSystemAPI): Float {
        //if (KantaCMD.playerHasProtection()) return 0f;
        if (progress <= 0) return 0f
        var total = 0f
        for (market in Misc.getMarketsInLocation(system, Factions.PLAYER)) {
            total += getFreePortScore(market!!)
        }
        var f = total / 6f
        if (f > 1f) f = 1f
        return f * MAX_MAG
    }
}