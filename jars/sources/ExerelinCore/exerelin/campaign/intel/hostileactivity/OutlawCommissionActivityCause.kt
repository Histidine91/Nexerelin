package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.RepLevel
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.ids.People
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityCause2
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.KantasWrathPirateActivityCause2
import com.fs.starfarer.api.impl.campaign.rulecmd.KantaCMD
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.Range
import kotlin.math.roundToInt

class OutlawCommissionActivityCause(intel: HostileActivityEventIntel?, var factionId : String, var idForText : String) : BaseHostileActivityCause2(intel) {

    override fun getTooltip(): TooltipMakerAPI.TooltipCreator? {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any) {
                tooltip.addPara(
                    NexHostileActivityManager.getString(idForText + "CommissionDesc"), 0f
                )
            }
        }
    }

    override fun getProgress(): Int {
        var mag = getMagnitudeContribution(null)
        if (mag >= 0) return 0
        return (mag * 20).roundToInt();
    }

    override fun getDesc(): String? {
        return NexHostileActivityManager.getString(idForText + "CommissionName")
    }

    override fun getMagnitudeContribution(system: StarSystemAPI?): Float {
        if (factionId != Misc.getCommissionFactionId()) return 0f
        val rel = Global.getSector().playerFaction.getRelationshipLevel(factionId);
        var mag = when(rel) {
            RepLevel.SUSPICIOUS -> 0.05f
            RepLevel.NEUTRAL -> 0.1f
            RepLevel.FAVORABLE -> 0.15f
            RepLevel.WELCOMING -> 0.2f
            RepLevel.FRIENDLY -> 0.25f
            RepLevel.COOPERATIVE -> 0.3f
            else -> 0f
        }

        return mag * -2;
    }
}