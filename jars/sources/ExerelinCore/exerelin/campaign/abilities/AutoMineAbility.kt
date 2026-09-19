package exerelin.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.MiningHelperLegacy
import exerelin.campaign.MiningHelperLegacy.*
import exerelin.campaign.intel.groundbattle.GBConstants
import exerelin.utilities.StringHelper
import org.lazywizard.lazylib.MathUtils
import org.magiclib.util.MagicTxt
import java.awt.Color

class AutoMineAbility : BaseToggleAbility() {

    fun getNearestMineable(): SectorEntityToken? {
        val fleet = fleet ?: return null

        val loc = fleet.containingLocation ?: return null
        val entitiesToCheck = ArrayList<SectorEntityToken>()
        entitiesToCheck.addAll(loc.planets)
        entitiesToCheck.addAll(loc.asteroids)

        var best: SectorEntityToken? = null
        var bestDist = Float.MAX_VALUE
        for (entity in entitiesToCheck) {
            if (!MiningHelperLegacy.canMine(entity)) continue
            val dist = MathUtils.getDistance(fleet, entity)
            if (dist > GBConstants.MAX_SUPPORT_DIST) continue
            if (dist < bestDist) {
                bestDist = dist
                best = entity
            }
        }
        return best
    }

    override fun getActivationText(): String? {
        return StringHelper.getString("exerelin_abilities", "autoMineFloatText")
    }

    override fun getDeactivationText(): String? {
        return null
    }

    override fun showProgressIndicator(): Boolean {
        return false
    }

    override fun showActiveIndicator(): Boolean {
        return isActive
    }

    override fun activateImpl() {
        Global.getSector().listenerManager.addListener(this)
    }

    override fun applyEffect(amount: Float, level: Float) {
        val mem = Global.getSector().characterData.memoryWithoutUpdate
        if (mem.contains("\$nex_miningCooldown")) return
        if (MiningHelperLegacy.getFleetMiningStrength(fleet) <= 0) return

        var result = MiningHelperLegacy.getMiningResults(this.fleet, getNearestMineable() ?: return, 1f, true)
        reportMiningResult(result);
        mem.set("\$nex_miningCooldown", true, 1f)
    }

    override fun deactivateImpl() {

    }

    override fun cleanupImpl() {

    }

    fun reportMiningResult(result : MiningResult) : IntelInfoPlugin {
        val message = MessageIntel()
        val hl = Misc.getHighlightColor()
        val textCol = Misc.getTextColor()
        val bad = Misc.getNegativeHighlightColor()

        var commodities = ArrayList<String>()
        var commoditiesHl = ArrayList<String>()
        var highlightColors = ArrayList<Color>()
        for (commodEntry in result.report.totalOutput) {
            val num = Misc.getWithDGS(commodEntry.value)
            val commodName = Global.getSettings().getCommoditySpec(commodEntry.key).name
            commodities.add(String.format("%s %s", num, commodName))
            commoditiesHl.add(num)
            highlightColors.add(hl)
            if (message.icon == null) message.icon = Global.getSettings().getCommoditySpec(commodEntry.key).iconName
        }

        message.addLine(commodities.toString(), textCol, commoditiesHl.toTypedArray(), *highlightColors.toTypedArray())

        var caches = ArrayList<String>()
        var cachesHl = ArrayList<String>()
        highlightColors.clear()

        for (cache in result.cachesFound) {
            var displayStr = cache.name
            cachesHl.add(displayStr)
            highlightColors.add(hl)
            if (cache.def.type == CacheType.COMMODITY || cache.def.type == CacheType.WEAPON) {
                val count = Misc.getWithDGS(cache.numItems.toFloat())
                displayStr += (" x $count")
                cachesHl.add(count)
                highlightColors.add(hl)
            }
            caches.add(displayStr)
        }

        if (!caches.isEmpty()) message.addLine(String.format(getString("abilityBulletCache"), caches.toString()), textCol, cachesHl.toTypedArray(), *highlightColors.toTypedArray())

        if (result.accidents != null) {
            var accidents = ArrayList<String>()
            var accidentsHl = ArrayList<String>()
            highlightColors.clear()

            var crewLost = result.accidents.crewLost
            if (crewLost > 0) {
                accidents.add(String.format(getString("abilityBulletCrewLost"), crewLost))
                accidentsHl.add(crewLost.toString())
                highlightColors.add(bad)
            }
            var machineryLost = result.accidents.machineryLost
            if (machineryLost > 0) {
                accidents.add(String.format(getString("abilityBulletMachineryLost"), machineryLost))
                accidentsHl.add(machineryLost.toString())
                highlightColors.add(bad)
            }
            var numCR = result.accidents.crLost.size
            if (numCR > 0) {
                accidents.add(String.format(getString("abilityBulletShipsLostCR"), numCR))
                accidentsHl.add(numCR.toString())
                highlightColors.add(bad)
            }
            var numDam = result.accidents.damage.size
            if (numDam > 0) {
                accidents.add(String.format(getString("abilityBulletShipsDamaged"), numDam))
                accidentsHl.add(numDam.toString())
                highlightColors.add(bad)
            }
            var numLost = result.accidents.shipsDestroyed.size
            if (numLost > 0) {
                accidents.add(String.format(getString("abilityBulletShipsDestroyed"), numLost))
                accidentsHl.add(numLost.toString())
                highlightColors.add(bad)
            }

            message.addLine(String.format(getString("abilityBulletAccidents"), accidents.toString()), textCol, accidentsHl.toTypedArray(), *highlightColors.toTypedArray())
        }

        Global.getSector().intelManager.addIntel(message)
        return message
    }

    override fun hasTooltip(): Boolean {
        return true
    }

    override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean) {
        val gray = Misc.getGrayColor()

        var statusKey = "statusOff"
        if (turnedOn) {
            statusKey = "statusOn"
        }

        if (!Global.CODEX_TOOLTIP_MODE) {
            var status = StringHelper.getString("exerelin_abilities", statusKey)
            val title = tooltip.addTitle(String.format("%s (%s)", spec.name, status))
            title.highlightLast(status)
            title.setHighlightColor(gray)
        } else {
            tooltip.addSpacer(-10f)
        }

        val pad = 10f
        var str = StringHelper.getString("exerelin_abilities", "autoMineTooltip")
        tooltip.addPara(str, pad)

        if (Global.CODEX_TOOLTIP_MODE) return

        addIncompatibleToTooltip(tooltip, expanded)
    }

    override fun isUsable(): Boolean {
        return true
    }

    companion object {
        @JvmStatic
        fun getString(id : String) : String {
            return StringHelper.getString("exerelin_mining", id)
        }
    }
}
