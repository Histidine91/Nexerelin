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
import exerelin.campaign.intel.defensefleet.DefenseFleetIntel
import kotlin.math.roundToInt

class DefenseFleetActivityCause(intel: HostileActivityEventIntel?) : BaseHostileActivityCause2(intel) {

    companion object {
        const val PROGRESS_PER_RAID_STRENGTH = 0.025f
    }

    override fun getTooltip(): TooltipCreator? {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any) {
                tooltip.addPara(
                    NexHostileActivityManager.getString("defenseFleetDesc"), 0f
                )
            }
        }
    }

    override fun getProgress(): Int {
        var strongest = 0f
        for (dfi : DefenseFleetIntel in getActiveDefenseFleets(null)) {
            var fp = dfi.raidStr
            strongest = fp.coerceAtLeast(strongest);
        }

        var mag = getMagnitudeForRaidStr(strongest)
        //Global.getLogger(this.javaClass).info("Applying progress $mag for fleet strength $strongest")
        return mag.roundToInt()
    }

    override fun getDesc(): String? {
        return NexHostileActivityManager.getString("defenseFleetName")
    }

    override fun getMagnitudeContribution(system: StarSystemAPI?): Float {
        return 0f
    }

    protected fun getMagnitudeForRaidStr(raidStr : Float) : Float {
        return -raidStr * PROGRESS_PER_RAID_STRENGTH
    }

    protected fun getActiveDefenseFleets(system : StarSystemAPI?) : List<DefenseFleetIntel> {
        val results = ArrayList<DefenseFleetIntel>();
        for (intel : IntelInfoPlugin in Global.getSector().intelManager.getIntel(DefenseFleetIntel :: class.java)) {
            val dfi = intel as DefenseFleetIntel
            if (dfi.isEnding || dfi.isEnded) continue
            if (system != null && dfi.target.starSystem != system) continue
            if (dfi.currentStage != dfi.getStageIndex(dfi.actionStage)) continue;   // not yet reached action stage
            if (dfi.faction.isAtBest(Factions.PLAYER, RepLevel.FAVORABLE)) continue;

            results.add(dfi)
        }
        return results
    }
}