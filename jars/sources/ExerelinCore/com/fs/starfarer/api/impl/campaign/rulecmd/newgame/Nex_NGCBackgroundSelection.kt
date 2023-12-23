package com.fs.starfarer.api.impl.campaign.rulecmd.newgame

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.InteractionDialogAPI
import com.fs.starfarer.api.campaign.OptionPanelAPI
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.campaign.VisualPanelAPI
import com.fs.starfarer.api.campaign.rules.MemKeys
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.CharacterCreationData
import com.fs.starfarer.api.impl.campaign.ids.Factions
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

    override fun execute(ruleId: String?, dialog: InteractionDialogAPI, params: MutableList<Misc.Token>?, memoryMap: MutableMap<String, MemoryAPI>): Boolean {

        this.optionPanel = dialog.optionPanel
        this.visualPanel = dialog.visualPanel
        this.textPanel = dialog.textPanel

        var arg = params!!.get(0).getString(memoryMap)

        if (arg == "confirmSelection") {
            var backgroundID = memoryMap.get(MemKeys.LOCAL)!!.getString("\$nex_selected_background")
            var factionID  = memoryMap.get(MemKeys.LOCAL)!!.getString("\$nex_selected_faction_for_background")
            ExerelinSetupData.getInstance().backgroundId = backgroundID
            ExerelinSetupData.getInstance().selectedFactionForBackground = factionID
        }

        val data = memoryMap.get(MemKeys.LOCAL)!!.get("\$characterData") as CharacterCreationData

        if (arg != "selectBackground") return false

        val factionId = memoryMap[MemKeys.LOCAL]!!.getString("\$playerFaction") ?: Factions.PLAYER
        var factionSpec = Global.getSettings().getFactionSpec(factionId)
        val factionConfig = NexConfig.getFactionConfig(factionId)
        memoryMap.get(MemKeys.LOCAL)!!.set("\$nex_selected_faction_for_background", factionId)

        optionPanel.clearOptions()
        textPanel.addPara("Choose your Background", Misc.getBasePlayerColor(), Misc.getBasePlayerColor())
        textPanel.addPara("A background can determine an assortment of different things, be that starting cargo, skills or future interactions with other characters. You can also choose to begin as a nobody who has yet to make their name known.")
        textPanel.addPara("Hover over a background to get more information.")

        optionPanel.addOption("Done", "nex_NGCDoneWithBackground")
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
            if (!background.shouldShowInSelection(factionSpec, factionConfig)) continue
            if (first) {
                element.addSpacer(10f)
            }
            else {
                element.addSpacer(20f)
            }


            var canBeSelected = background.canBeSelected(factionSpec, factionConfig)


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
                memoryMap.get(MemKeys.LOCAL)!!.set("\$nex_selected_background", background.spec.id)
            }
            first = false
            checkboxes.put(subelement, checkbox)
            subelement.setCustomData("plugin", background)

            var image = subelement.innerElement.beginImageWithText(imagePath, 50f)

            var titleColor = Misc.getHighlightColor()
            if (!canBeSelected) titleColor = Misc.getGrayColor()
            image.addPara(title, 0f, titleColor, titleColor)
            image.addPara(description, 0f, Misc.getTextColor(), Misc.getTextColor())

            subelement.innerElement.addImageWithText(0f)
            var prev = subelement.innerElement.prev
            prev.position.rightOfMid(checkbox.elementPanel, 10f)


            subelement.onClick {
                if (background.canBeSelected(factionSpec, factionConfig)) {
                    checkbox.value = true
                    memoryMap.get(MemKeys.LOCAL)!!.set("\$nex_selected_background", background.spec.id)
                }
            }

            checkbox.onClick {
                if (background.canBeSelected(factionSpec, factionConfig)) {
                    memoryMap.get(MemKeys.LOCAL)!!.set("\$nex_selected_background", background.spec.id)
                }
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

                    if (!canBeSelected) {
                        tooltip!!.addSpacer(10f)
                        background.canNotBeSelectedReason(tooltip, factionSpec, factionConfig)
                    }

                    if (expanded && background.spec.id != "nex_none") {
                        tooltip!!.addSpacer(10f)
                        tooltip.addPara("Added by ${background.spec.modName}", 0f, Misc.getTextColor(), Misc.getHighlightColor(), "${background.spec.modName}")
                    }


                }

            },subelement.elementPanel, TooltipMakerAPI.TooltipLocation.BELOW)
        }

        for ((element, checkbox) in checkboxes) {

            var background = element.getCustomData("plugin") as BaseCharacterBackground


            if (!background.canBeSelected(factionSpec, factionConfig)) {
                element.onClick {
                    element.playSound("ui_button_disabled_pressed", 1f, 1f)
                }
                checkbox.onClick {
                    checkbox.playSound("ui_button_disabled_pressed", 1f, 1f)
                }
                continue
            }

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

                checkbox.playClickSound()

                for (other in checkboxes) {
                    if (other.value == checkbox) continue
                    other.value.value = false
                }
            }


        }


        panel.addUIElement(element)

       /* data.addScript {
            var plugin = selectedPlugin
            plugin!!.executeAfterGameCreation(factionSpec, factionConfig)
            Global.getSector().memoryWithoutUpdate.set("\$nex_selected_background", plugin.spec.id)

            var intel = CharacterBackgroundIntel(factionId)
            Global.getSector().intelManager.addIntel(intel)
        }*/

        return true
    }

}