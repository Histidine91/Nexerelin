package exerelin.campaign.ui

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.IntelUIAPI
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.intel.PersonalConfigIntel
import java.awt.Color

class MusicPickerDialogDelegate(val musicType: String, val width : Float, val height : Float, val intelUIAPI: IntelUIAPI, val intel: PersonalConfigIntel?) :
    BaseCustomDialogDelegate() {

    val customPanelPlugin = FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false)
    var mainPanel : CustomPanelAPI? = null
    var callback : CustomDialogDelegate.CustomDialogCallback? = null
    var interactionDialog : InteractionDialogAPI? = null

    fun getPlugin() : CustomPanelPluginWithInput {
        return customPanelPlugin
    }

    fun getMusicSetsAndColors(): Pair<Set<String>, Map<String, Color>> {
        val tracks = LinkedHashSet<String>()
        val colors = HashMap<String, Color>()
        for (faction in Global.getSector().allFactions) {
            if (faction.isPlayerFaction || !faction.isShowInIntelTab) continue
            tracks.addAll(faction.musicMap.values)
            for (musicSet : String in faction.musicMap.values) {
                if (colors.containsKey(musicSet)) continue
                colors[musicSet] = faction.baseUIColor
            }
        }
        return Pair(tracks, colors)
    }

    override fun createCustomDialog(panel: CustomPanelAPI, callback: CustomDialogDelegate.CustomDialogCallback?) {
        mainPanel = panel
        this.callback = callback
        val tooltip = panel.createUIElement(width, height, true)
        val musicSetsAndColors = getMusicSetsAndColors()

        for (musicId : String in musicSetsAndColors.first) {
            val color = musicSetsAndColors.second.get(musicId)
            PersonalConfigIntel.addMusicButtonRow(musicId, "", musicId, null, color, panel, tooltip, width, this)
        }
        panel.addUIElement(tooltip).inTL(0f, 0f)
    }

    override fun getCustomPanelPlugin(): CustomUIPanelPlugin? {
        return customPanelPlugin
    }

    override fun customDialogConfirm() {
        interactionDialog?.dismiss()
    }

    override fun customDialogCancel() {
        customDialogConfirm()
    }

    class MusicPickerDialogPlugin(val delegate: MusicPickerDialogDelegate) : InteractionDialogPlugin {
        override fun init(dialog: InteractionDialogAPI?) {
            delegate.interactionDialog = dialog
            dialog!!.showCustomDialog(delegate.width, delegate.height, delegate)
        }

        override fun optionSelected(optionText: String?, optionData: Any?) {

        }

        override fun optionMousedOver(optionText: String?, optionData: Any?) {

        }

        override fun advance(amount: Float) {

        }

        override fun backFromEngagement(battleResult: EngagementResultAPI?) {

        }

        override fun getContext(): Any? {
            return null
        }

        override fun getMemoryMap(): MutableMap<String, MemoryAPI>? {
            return null
        }

    }
}