package exerelin.campaign.ui

import com.fs.starfarer.api.campaign.CustomDialogDelegate
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.IntelUIAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exerelin.campaign.diplomacy.DiplomacyTraits
import exerelin.campaign.diplomacy.DiplomacyTraits.TraitDef
import exerelin.campaign.intel.PersonalConfigIntel
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel

/**
 * Handles the diplomacy trait picker from [DiplomacyProfileIntel].
 */
class DiploTraitPickerDialogDelegate(width : Float, height : Float, val intelUIAPI: IntelUIAPI, val intel: DiplomacyProfileIntel?) :
    NexBaseCustomDialogDelegate(width, height) {

    companion object {
        const val BUTTON_WIDTH = 40f
    }

    val selectedTraits : MutableSet<String> = HashSet(DiplomacyTraits.getFactionTraits(Factions.PLAYER))

    override fun createCustomDialog(panel: CustomPanelAPI, callback: CustomDialogDelegate.CustomDialogCallback?) {
        mainPanel = panel
        this.callback = callback
        val tooltip = panel.createUIElement(width, height, true)

        for (traitDef : TraitDef in DiplomacyTraits.getTraits()) {
            addTraitPickerRow(traitDef.id, panel, tooltip, width)
        }
        tooltip.addSpacer(10f);
        panel.addUIElement(tooltip).inTL(0f, 0f)
    }

    override fun customDialogConfirm() {
        // Add the selected traits
        // Note: currently does not handle unselecting traits specified in faction config, nor retaining traits that were added elsewhere
        DiplomacyTraits.getAddedTraits(Factions.PLAYER).clear()
        //DiplomacyTraits.getAddedTraits(Factions.PLAYER).addAll(selectedTraits)    // can't add this way since getAddedTraits returns a throwaway table if not already saved to faction memory
        for (trait in selectedTraits) {
            DiplomacyTraits.addAddedTrait(Factions.PLAYER, trait)
        }
        intelUIAPI.updateUIForItem(intel)
        interactionDialog?.dismiss()
    }

    override fun customDialogCancel() {
        interactionDialog?.dismiss()
    }

    fun addTraitPickerRow(
        traitId: String,
        outerPanel: CustomPanelAPI,
        tooltip: TooltipMakerAPI,
        width: Float
    ) {
        val pad = 3f
        val row = outerPanel.createCustomPanel(width, DiplomacyProfileIntel.TRAIT_ROW_HEIGHT, null)
        val trait = DiplomacyTraits.getTrait(traitId)
        val plugin = this.customPanelPlugin as CustomPanelPluginWithInput

        val traitInfoOuter = row.createUIElement(
            width - DiplomacyProfileIntel.TRAIT_ROW_HEIGHT - pad*2 - BUTTON_WIDTH,
            DiplomacyProfileIntel.TRAIT_ROW_HEIGHT,
            false
        )

        val traitInfo = traitInfoOuter.beginImageWithText(trait.icon, DiplomacyProfileIntel.TRAIT_ROW_HEIGHT)
        traitInfo.addPara(trait.name, trait.color, 0f)
        traitInfo.addPara(trait.desc, 3f)
        traitInfoOuter.addImageWithText(0f)

        val buttonTT = row.createUIElement(BUTTON_WIDTH, DiplomacyProfileIntel.TRAIT_ROW_HEIGHT, false)
        buttonTT.setForceProcessInput(true)

        val button = buttonTT.addCheckbox(
            DiplomacyProfileIntel.TRAIT_ROW_HEIGHT, DiplomacyProfileIntel.TRAIT_ROW_HEIGHT, "",
            "chk_$traitId", ButtonAPI.UICheckboxSize.LARGE, 0f
        )
        if (selectedTraits.contains(traitId)) button.isChecked = true

        plugin.addButton(object : CustomPanelPluginWithInput.ButtonEntry(button, "toggle_$traitId") {
            override fun onToggle() {
                if (this.state) selectedTraits.add(traitId)
                else selectedTraits.remove(traitId)
            }
        })

        row.addUIElement(buttonTT).inTL(0f, 0f)
        row.addUIElement(traitInfoOuter).rightOfTop(buttonTT, pad)

        tooltip.addCustom(row, pad)
    }
}