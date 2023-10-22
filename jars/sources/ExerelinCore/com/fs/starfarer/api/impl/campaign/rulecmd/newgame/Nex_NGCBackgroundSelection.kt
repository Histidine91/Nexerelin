package com.fs.starfarer.api.impl.campaign.rulecmd.newgame

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.OptionPanelAPI
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.campaign.VisualPanelAPI
import com.fs.starfarer.api.campaign.rules.MemKeys
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.CharacterCreationData
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.ExerelinSetupData
import exerelin.campaign.backgrounds.BaseCharacterBackground
import exerelin.campaign.backgrounds.CharacterBackgroundIntel
import exerelin.campaign.backgrounds.CharacterBackgroundLoader
import exerelin.campaign.backgrounds.UnknownCharacterBackground
import exerelin.utilities.NexConfig
import exerelin.campaign.ui.NexLunaCheckbox
import exerelin.campaign.ui.NexLunaElement
import org.lwjgl.input.Keyboard

class Nex_NGCBackgroundSelection : BaseCommandPlugin() {

    lateinit var optionPanel: OptionPanelAPI
    lateinit var textPanel: TextPanelAPI
    lateinit var visualPanel: VisualPanelAPI

    var selectedPlugin: BaseCharacterBackground? = null

    override fun execute(ruleId: String?, dialog: InteractionDialogAPI, params: MutableList<Misc.Token>?, memoryMap: MutableMap<String, MemoryAPI>): Boolean {

        this.optionPanel = dialog.optionPanel
        this.visualPanel = dialog.visualPanel
        this.textPanel = dialog.textPanel

        val data = memoryMap.get(MemKeys.LOCAL)!!.get("\$characterData") as CharacterCreationData

        val factionId = memoryMap.get(MemKeys.LOCAL)!!.get("\$playerFaction") as String
        var factionSpec = Global.getSettings().getFactionSpec(factionId)
        val factionConfig = NexConfig.getFactionConfig(factionId)

        optionPanel.clearOptions()
        textPanel.addPara("Choose your Background", Misc.getBasePlayerColor(), Misc.getBasePlayerColor())
        textPanel.addPara("A background can determine an assortment of different things, be that starting cargo, skills or future interactions with other characters. You can also choose to begin as a nobody who has yet to make their name known.")
        textPanel.addPara("Hover over a background to get more information.")

        optionPanel.addOption("Done", "nex_NGCDone")
        optionPanel.setShortcut("nex_NGCDone", Keyboard.KEY_RETURN, false, false, false, false)

        var width = 600f
        var height = 400f

        var panel = visualPanel.showCustomPanel(width, height, null)
        var element = panel.createUIElement(width, height, true)

        var backgrounds = ArrayList<BaseCharacterBackground>()
        for (spec in CharacterBackgroundLoader.specs) {
            var plugin = Global.getSettings().scriptClassLoader.loadClass(spec.pluginPath).newInstance()

            if (plugin is BaseCharacterBackground) {
                plugin.spec = spec
                backgrounds.add(plugin)
            }
        }

        var first = true
        var checkboxes = HashMap<NexLunaElement, NexLunaCheckbox>()
        element.addPara("", 0f).position.inTL(10f, 0f)
        for (background in backgrounds.sortedBy { it.order }) {
            if (first) {
                element.addSpacer(10f)
            }
            else {
                element.addSpacer(20f)
            }

            var title = background.getTitle(factionSpec, factionConfig)
            var description = background.getShortDescription(factionSpec, factionConfig)
            var imagePath = background.getIcon(factionSpec, factionConfig)

            var subelement = NexLunaElement(element, width - 50, 50f).apply {
                renderBackground = false
                renderBorder = false
            }
            subelement.innerElement.addSpacer(10f)

            var checkbox = NexLunaCheckbox(first, subelement.innerElement, 20f, 20f)
            if (first) {
                selectedPlugin = background
            }
            first = false
            checkboxes.put(subelement, checkbox)

            var image = subelement.innerElement.beginImageWithText(imagePath, 50f)

            image.addPara(title, 0f, Misc.getHighlightColor(), Misc.getHighlightColor())
            image.addPara(description, 0f, Misc.getTextColor(), Misc.getTextColor())

            subelement.innerElement.addImageWithText(0f)
            var prev = subelement.innerElement.prev
            prev.position.rightOfMid(checkbox.elementPanel, 10f)


            subelement.onClick {
                checkbox.value = true
                selectedPlugin = background
                ExerelinSetupData.getInstance().backgroundId = background.spec.id
            }

            checkbox.onClick {
                selectedPlugin = background
                ExerelinSetupData.getInstance().backgroundId = background.spec.id
            }


            element.addTooltipTo(object  : TooltipCreator {
                override fun isTooltipExpandable(tooltipParam: Any?): Boolean {
                    return background.spec.id != "nex_none"
                }

                override fun getTooltipWidth(tooltipParam: Any?): Float {
                    return 450f
                }

                override fun createTooltip(tooltip: TooltipMakerAPI?, expanded: Boolean, tooltipParam: Any?) {
                    background.addTooltipForSelection(tooltip, factionSpec, factionConfig, expanded)

                    if (expanded && background.spec.id != "nex_none") {
                        tooltip!!.addSpacer(10f)
                        tooltip.addPara("Added by ${background.spec.modName}", 0f, Misc.getTextColor(), Misc.getHighlightColor(), "${background.spec.modName}")
                    }
                }

            },subelement.elementPanel, TooltipMakerAPI.TooltipLocation.BELOW)
        }

        for ((element, checkbox) in checkboxes) {

            element.onClick {
                element.playClickSound()
                checkbox.value = true

                for (other in checkboxes) {
                    if (other.value == checkbox) continue
                    other.value.value = false
                }
            }


            checkbox.onClick {
                checkbox.value = true

                for (other in checkboxes) {
                    if (other.value == checkbox) continue
                    other.value.value = false
                }
            }
        }


        panel.addUIElement(element)

        data.addScript {
            var plugin = selectedPlugin
            plugin!!.executeAfterGameCreation(factionSpec, factionConfig)
            Global.getSector().memoryWithoutUpdate.set("\$nex_selected_background", plugin.spec.id)

            var intel = CharacterBackgroundIntel(factionId)
            Global.getSector().intelManager.addIntel(intel)
        }

        return true
    }

}