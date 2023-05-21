package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import kotlin.math.roundToInt

class MercPackageActivityCause(intel: HostileActivityEventIntel?) : BaseHostileActivityCause2(intel) {

    companion object {
        // for comparison, a military base on a size 6 has a base upkeep of 20k and applies 2*size progress (12 at max size)
        // a high command costs 28k at size 6 and applies 3*size progress (18 at max size)
        const val PROGRESS_PER_MONTH = 16f
        const val PROGRESS_MULT_PER_MONTH = 0.15f
        const val MONTHLY_FEE = 100000f
        const val MONTHS = 3;

        @JvmStatic fun getTooltipStatic() : BaseFactorTooltip {
            return object : BaseFactorTooltip() {
                override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any) {
                    tooltip.addPara(
                        NexHostileActivityManager.getString("mercPackageDesc"), 0f
                    )
                }
            }
        }
    }

    override fun getTooltip(): TooltipCreator? {
        return getTooltipStatic()
    }

    override fun getProgress(): Int {
        var intel = MercPackageIntel.getInstance();
        if (intel == null || intel.isEnding || intel.isEnded) return 0
        var mag = -PROGRESS_MULT_PER_MONTH * HostileActivityEventIntel.get().progress
        return mag.roundToInt()
    }

    override fun getDesc(): String? {
        return NexHostileActivityManager.getString("mercPackageName")
    }

    override fun getMagnitudeContribution(system: StarSystemAPI?): Float {
        return 0f
    }
}