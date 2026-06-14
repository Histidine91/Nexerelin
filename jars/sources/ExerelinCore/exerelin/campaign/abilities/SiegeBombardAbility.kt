package exerelin.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.abilities.BaseToggleAbility
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.intel.groundbattle.GBConstants
import exerelin.campaign.intel.groundbattle.GroundBattleAI
import exerelin.campaign.intel.groundbattle.GroundBattleCampaignListener
import exerelin.campaign.intel.groundbattle.GroundBattleIntel
import exerelin.campaign.intel.groundbattle.plugins.AbilityPlugin
import exerelin.campaign.intel.groundbattle.plugins.FireSupportAbilityPlugin
import exerelin.utilities.StringHelper
import org.lazywizard.lazylib.MathUtils
import org.magiclib.util.MagicTxt

class SiegeBombardAbility : BaseToggleAbility(), GroundBattleCampaignListener {

    var battle : GroundBattleIntel? = null

    override fun getActivationText(): String? {
        return StringHelper.getString("exerelin_abilities", "siegeBombardmentFloatText")
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
        if (battle == null || !isUsable()) {
            deactivate()
            return
        }
        Misc.setFlagWithReason(
            battle!!.market.memoryWithoutUpdate, GBConstants.MEMKEY_BLOCK_ATTACKER_DEFEAT,
            getReasonKey(), true, 0.3f
        )
    }

    override fun deactivateImpl() {
        cleanupImpl()
    }

    override fun cleanupImpl() {
        if (battle != null) {
            Misc.setFlagWithReason(
                battle!!.market.memoryWithoutUpdate, GBConstants.MEMKEY_BLOCK_ATTACKER_DEFEAT,
                getReasonKey(), false, -1f
            )
        }
        battle = null
        Global.getSector().listenerManager.removeListener(this)
    }

    fun getReasonKey() : String {
        return "nex_siegeBombardAbility" + fleet?.id
    }

    fun getFireSupportAbility(battle : GroundBattleIntel) : AbilityPlugin {
        return battle.getSide(true).getAbilityById("fireSupport")
    }

    override fun hasTooltip(): Boolean {
        return true
    }

    override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean) {
        val bad = Misc.getNegativeHighlightColor()
        val gray = Misc.getGrayColor()
        val highlight = Misc.getHighlightColor()
        val text = Misc.getTextColor();

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
        var str = StringHelper.getString("exerelin_abilities", "siegeBombardmentTooltip1")
        MagicTxt.addPara(tooltip, str, pad, text, highlight)
        str = StringHelper.getString("exerelin_abilities", "siegeBombardmentTooltip2")
        tooltip.addPara(str, pad)

        if (Global.CODEX_TOOLTIP_MODE) return

        val notUsableReason = getNotUsableReason()
        var notUsableReasonHuman : String? = null
        when (notUsableReason) {
            "noBattle" -> notUsableReasonHuman = StringHelper.getString("exerelin_abilities", "siegeBombardmentTooltipNoBattle")
            "notEnoughFuel" -> notUsableReasonHuman = StringHelper.getString("exerelin_abilities", "siegeBombardmentTooltipNoFuel")
        }
        if (notUsableReasonHuman != null)
            tooltip.addPara(notUsableReasonHuman, pad, bad, battle?.market?.faction?.baseUIColor ?: gray, battle?.market?.name)

        addIncompatibleToTooltip(tooltip, expanded)
    }

    override fun isUsable(): Boolean {
        if (!super.isUsable()) return false
        if (fleet == null) return false

        battle = getNearestBattle()
        return getNotUsableReason() == null
    }

    fun getNotUsableReason(): String? {
        if (battle == null) return "noBattle"

        var fireSupportAbility = getFireSupportAbility(battle!!) as FireSupportAbilityPlugin? ?: return null
        if (fireSupportAbility.getFuelCost(fleet) > fleet.cargo.fuel) return "notEnoughFuel"

        return null
    }

    fun getNearestBattle(): GroundBattleIntel? {
        val fleet = fleet ?: return null

        val loc = fleet.containingLocation ?: return null
        val markets =
            Global.getSector().economy.getMarkets(loc)

        var best: GroundBattleIntel? = null
        var bestDist = Float.MAX_VALUE
        for (market in markets) {
            val battle = GroundBattleIntel.getOngoing(market) ?: continue
            if (battle.outcome != null) continue
            if (battle.isPlayerAttacker != true) continue
            val dist = MathUtils.getDistance(fleet, market.primaryEntity)
            if (dist > GBConstants.MAX_SUPPORT_DIST) continue
            if (dist < bestDist) {
                bestDist = dist
                best = battle
            }
        }
        return best
    }

    override fun reportBattleStarted(battle: GroundBattleIntel?) {

    }

    override fun reportPlayerJoinedBattle(battle: GroundBattleIntel?) {

    }

    override fun reportBattleBeforeTurn(battle: GroundBattleIntel?, turn: Int) {
        if (battle == null) return
        if (battle != this.battle) return
        if (fleet == null) return
        var ability = getFireSupportAbility(battle)
        if (ability.getDisabledReason(fleet.commander) != null) return
        val ai = GroundBattleAI(battle, true, true, false)
        ai.getInfo()
        ability?.aiExecute(ai, fleet?.commander)
    }

    override fun reportBattleAfterTurn(battle: GroundBattleIntel?, turn: Int) {

    }

    override fun reportBattleEnded(battle: GroundBattleIntel?) {

    }

    companion object {
        const val COMMODITY_ID: String = Commodities.FUEL
    }
}
