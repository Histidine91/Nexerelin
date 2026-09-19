package exerelin.campaign.ui

import com.fs.starfarer.api.campaign.BaseCustomDialogDelegate
import com.fs.starfarer.api.campaign.CustomDialogDelegate.CustomDialogCallback
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.InteractionDialogPlugin
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.util.Misc
import lombok.Getter
import lombok.Setter

open class NexBaseCustomDialogDelegate(val width: Float, val height: Float) : BaseCustomDialogDelegate() {

    var interactionDialog : InteractionDialogAPI? = null
    var plugin: CustomPanelPluginWithInput = FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false)
    var mainPanel: CustomPanelAPI? = null
    var callback: CustomDialogCallback? = null

    override fun getCustomPanelPlugin(): CustomUIPanelPlugin {
        return plugin
    }

    class SimpleDialogWithDelegate(protected var delegate: NexBaseCustomDialogDelegate) : InteractionDialogPlugin {
        override fun init(dialog: InteractionDialogAPI) {
            delegate.interactionDialog = dialog
            dialog.showCustomDialog(delegate.width, delegate.height, delegate)
        }

        override fun optionSelected(optionText: String, optionData: Any) {
        }

        override fun optionMousedOver(optionText: String, optionData: Any) {
        }

        override fun advance(amount: Float) {
        }

        override fun backFromEngagement(battleResult: EngagementResultAPI) {
        }

        override fun getContext(): Any? {
            return null
        }

        override fun getMemoryMap(): Map<String, MemoryAPI>? {
            return null
        }
    }
}
