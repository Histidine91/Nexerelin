package com.fs.starfarer.api.impl.campaign.rulecmd.salvage

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.rules.MemKeys
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_VisualCustomPanel
import com.fs.starfarer.api.loading.FighterWingSpecAPI
import com.fs.starfarer.api.loading.WeaponSpecAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.UIComponentAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.Pair
import exerelin.campaign.MiningHelperLegacy
import exerelin.campaign.ui.CustomPanelPluginWithInput
import exerelin.campaign.ui.CustomPanelPluginWithInput.RadioButtonEntry
import exerelin.campaign.ui.FramedCustomPanelPlugin
import exerelin.utilities.NexUtilsGUI
import exerelin.utilities.StringHelper
import org.apache.log4j.Logger
import java.awt.Color
import java.text.MessageFormat
import java.util.*
import kotlin.collections.ArrayList

open class Nex_PrintMiningInfoV2 : BaseCommandPlugin() {

    companion object {
        @JvmField val log: Logger = Global.getLogger(Nex_PrintMiningInfoV2.javaClass)

        const val STRING_CATEGORY = "exerelin_mining"
        @JvmField val WING = Misc.ucFirst(StringHelper.getString("fighterWingShort"))
        const val MEMORY_KEY_MINING_HULLS = "\$nex_miningHulls"
        const val MEMORY_KEY_MINING_WINGS = "\$nex_miningWings"
        const val MEMORY_KEY_MINING_WEAPONS = "\$nex_miningWeapons"
        const val COST_HEIGHT = 67f
        const val SHIP_PANEL_HEIGHT = 48f
        const val SHIP_PANEL_HEIGHT_SMALL = 36f
        const val TEXT_HEIGHT = 24f
        @JvmField val SHIP_WING_COLUMN_WIDTHS = arrayOf(160f, 48f, 72f, 160f, 48f)
        @JvmField val WEAPON_COLUMN_WIDTHS = arrayOf(160f, 48f, 72f, 60f, 100f, 48f)

        @JvmField var miningToolTab = MiningToolTab.SHIP
        @JvmField var sortColumn = SortColumn.NAME
        @JvmField var sortAscending = true
        @JvmField var needResort = true

        @JvmStatic fun getString(id: String): String {
            return getString(id, false)
        }
        @JvmStatic fun getString(id: String, ucFirst : Boolean): String {
            return StringHelper.getString(STRING_CATEGORY, id, ucFirst)
        }

        // Formats to 1 decimal place if necessary, integers will be printed with zero decimal places
        @JvmStatic protected fun getFormattedStrengthString(strength: Float): String {
            return if (strength % 1 == 0f) String.format("%.0f", strength) else String.format("%.1f", strength)
        }

        @JvmStatic fun getMiningHulls(dialog: InteractionDialogAPI) : MutableList<Pair<FleetMemberAPI, Float>> {
            val miningHulls: MutableList<Pair<FleetMemberAPI, Float>> = ArrayList()
            for (variantId in Global.getSettings().allVariantIds) {
                if (!variantId.endsWith("_Hull")) continue
                val variant = Global.getSettings().getVariant(variantId)
                if (variant.isFighter) continue  // we'll deal fighters separately
                if (variant.hints.contains(ShipTypeHints.UNBOARDABLE) && variant.hints.contains(ShipTypeHints.HIDE_IN_CODEX)) continue
                if (MiningHelperLegacy.isHidden(variant.hullSpec.hullId) || MiningHelperLegacy.isHidden(variant.hullSpec.baseHullId)) continue
                try {
                    val temp = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant)
                    val strength = MiningHelperLegacy.getShipMiningStrength(temp, false)
                    if (strength == 0f) continue
                    miningHulls.add(Pair(temp, strength))
                } catch (ex: Exception) {
                    var err = String.format(StringHelper.getString(STRING_CATEGORY, "miningToolsError"), variantId)
                    dialog.textPanel.addPara(
                        err,
                        Misc.getNegativeHighlightColor(),
                        Misc.getHighlightColor(),
                        variantId
                    )
                    Global.getLogger(this.javaClass).error(err, ex)
                }
            }

            return miningHulls
        }

        @JvmStatic fun getMiningWings() : MutableList<Pair<FleetMemberAPI, Float>> {
            val miningWings: MutableList<Pair<FleetMemberAPI, Float>> = ArrayList()
            for (wingSpec in Global.getSettings().allFighterWingSpecs) {
                if (MiningHelperLegacy.isHidden(wingSpec.variant.hullSpec.hullId)
                    || MiningHelperLegacy.isHidden(wingSpec.variant.hullSpec.baseHullId)
                ) continue

                val temp = Global.getFactory().createFleetMember(FleetMemberType.FIGHTER_WING, wingSpec.id)
                val strength = MiningHelperLegacy.getWingMiningStrength(wingSpec)
                if (strength == 0f) continue

                miningWings.add(Pair(temp, strength))
            }

            return miningWings
        }

        @JvmStatic fun getMiningWeapons() : MutableList<Pair<WeaponSpecAPI, Float>> {
            val miningWeapons: MutableList<Pair<WeaponSpecAPI, Float>> = ArrayList()
            for ((weaponId, strength) in MiningHelperLegacy.getMiningWeaponsCopy()) {
                if (strength == 0f) continue
                var weapon: WeaponSpecAPI = try {
                    Global.getSettings().getWeaponSpec(weaponId)
                } catch (rex: RuntimeException) {
                    continue  // doesn't exist, skip
                }
                miningWeapons.add(Pair(weapon, strength))
            }

            return miningWeapons
        }


    }

    override fun execute(
        ruleId: String?,
        dialog: InteractionDialogAPI?,
        params: List<Misc.Token>,
        memoryMap: Map<String?, MemoryAPI?>?
    ): Boolean {
        if (dialog == null) return false
        val target = dialog.interactionTarget
        if (!MiningHelperLegacy.canMine(target)) return false
        val arg = params[0].getString(memoryMap)
        when (arg) {
            "fleet" -> printFleetInfo(dialog)
            "tools" -> {
                printMiningTools(dialog, memoryMap)
                val text = dialog.textPanel
                text.setFontSmallInsignia()
                text.addParagraph(StringHelper.getString(STRING_CATEGORY, "miningToolsListAddendum"))
                text.setFontInsignia()
            }
            "planet" -> printPlanetInfo(dialog.interactionTarget, dialog.textPanel, memoryMap)
            "cargo" -> dialog.visualPanel.showCore(
                CoreUITabId.CARGO, null
            ) { dialog.dismiss() }
            else -> printPlanetInfo(dialog.interactionTarget, dialog.textPanel, memoryMap)
        }
        return true
    }

    protected fun printPlanetInfo(target: SectorEntityToken, text: TextPanelAPI, memoryMap: Map<String?, MemoryAPI?>?) {
        val hl = Misc.getHighlightColor()
        val red = Misc.getNegativeHighlightColor()
        val playerFleet = Global.getSector().playerFleet
        val miningStrength = MiningHelperLegacy.getFleetMiningStrength(playerFleet)
        val miningStrengthStr = String.format("%.1f", miningStrength)
        val report = MiningHelperLegacy.getMiningReport(playerFleet, target, 1f)
        val danger = report.danger
        val dangerStr = MessageFormat.format("{0,number,#%}", danger)
        val exhaustion = report.exhaustion
        val exhaustionStr = String.format("%.1f", exhaustion * 100) + "%"
        var planetType = target.name
        if (target is PlanetAPI) {
            planetType = target.spec.name
        }
        text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "planetType")) + ": " + planetType)
        text.highlightInLastPara(hl, planetType)
        text.addParagraph(StringHelper.getString(STRING_CATEGORY,"miningStrength", true) + ": " + miningStrengthStr
        )
        text.highlightInLastPara(hl, miningStrengthStr)
        text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "danger")) + ": " + dangerStr)
        if (danger > 0.5) text.highlightInLastPara(red, dangerStr) else text.highlightInLastPara(hl, dangerStr)
        text.addParagraph(Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "exhaustion")) + ": " + exhaustionStr)
        text.highlightInLastPara(hl, exhaustionStr)
        text.setFontSmallInsignia()
        text.addParagraph(StringHelper.HR)
        for ((res, amount) in report.totalOutput) {
            val amountStr = String.format("%.2f", amount)
            val resName = StringHelper.getCommodityName(res)
            text.addParagraph("$resName: $amountStr")
            text.highlightInLastPara(hl, resName)
        }
        text.addParagraph(StringHelper.HR)
        val playerFaction = Global.getSector().playerFaction
        val color = playerFaction.color
        val cost = text.addCostPanel(
            Misc.ucFirst(StringHelper.getString(STRING_CATEGORY, "machineryAvailable")),
            COST_HEIGHT, color, playerFaction.darkUIColor
        )
        cost.isNumberOnlyMode = true
        cost.isWithBorder = false
        cost.alignment = Alignment.LMID
        val usable = Math.ceil(MiningHelperLegacy.getRequiredMachinery(miningStrength).toDouble()).toInt()
        val available = playerFleet.cargo.getCommodityQuantity(Commodities.HEAVY_MACHINERY).toInt()
        var curr = color
        if (usable > available) {
            curr = Misc.getNegativeHighlightColor()
            val warning = StringHelper.getStringAndSubstituteToken(
                STRING_CATEGORY, "insufficientMachineryWarning",
                "\$shipOrFleet", StringHelper.getShipOrFleet(playerFleet)
            )
            text.addParagraph(warning)
        }
        cost.addCost(Commodities.HEAVY_MACHINERY, "$usable ($available)", curr)
        cost.update()
        text.addParagraph(StringHelper.HR)
        text.setFontInsignia()
        val memory = memoryMap?.get(MemKeys.PLAYER)
        memory!!["\$miningStrength", miningStrength] = 0f
    }

    protected fun printFleetInfo(dialog: InteractionDialogAPI) {
        val hl = Misc.getHighlightColor()
        val bad = Misc.getNegativeHighlightColor()

        Nex_VisualCustomPanel.createPanel(dialog, true)

        val tt = Nex_VisualCustomPanel.getTooltip()
        val pad = 3f
        val opad = 10f

        tt.setParaSmallInsignia()
        tt.addPara(getString("fleetHeader"), opad)
        tt.setParaFontDefault()
        var thisPad = opad

        // don't need header for fleet info ig
        // should we sort members by mining strength? Nah, leave it with player's sort order
        val playerFleet = Global.getSector().playerFleet
        for (member in playerFleet.fleetData.membersInPriorityOrder) {
            val strength = MiningHelperLegacy.getShipMiningStrength(member, true)
            val crMult = MiningHelperLegacy.getMiningCRMult(member)
            if (strength <= 0 && crMult > 0) continue
            val strengthStr = String.format("%.1f", strength)
            val crMultStr = String.format("%.2f", crMult)

            var shipName = if (member.isFighterWing) member.variant.fullDesignationWithHullName else member.shipName + " (" + member.hullSpec.hullNameWithDashClass + ")"

            val imgWidth = SHIP_PANEL_HEIGHT
            val textWidth = Nex_VisualCustomPanel.PANEL_WIDTH - SHIP_PANEL_HEIGHT - pad
            val shipPanel = NexUtilsGUI.addPanelWithFixedWidthImage(Nex_VisualCustomPanel.getPanel(), null, Nex_VisualCustomPanel.PANEL_WIDTH,
                    SHIP_PANEL_HEIGHT, null, textWidth, pad, null, imgWidth, pad, null, false, null)
            NexUtilsGUI.addSingleShipList(shipPanel.elements[0] as TooltipMakerAPI, SHIP_PANEL_HEIGHT, member, 0f)
            val textTT = shipPanel.elements[1] as TooltipMakerAPI
            textTT.addPara(shipName, Misc.getBasePlayerColor(), 0f)
            val hlColor = if (crMult < 0.7) bad else hl
            textTT.addPara(getString("miningStrengthWithCR", true), pad, hlColor, strengthStr, crMultStr)

            tt.addCustom(shipPanel.panel, thisPad)
            thisPad = pad
        }

        Nex_VisualCustomPanel.addTooltipToPanel()
    }

    protected fun addShipOrWingHeaderRow(outer : CustomPanelAPI, tooltip : TooltipMakerAPI, isWing : Boolean,
                                         dialog: InteractionDialogAPI, memoryMap: Map<String?, MemoryAPI?>?) {
        val pad = 3f
        val opad = 10f

        var textWidth = SHIP_WING_COLUMN_WIDTHS[0]
        val headerPanel = NexUtilsGUI.addPanelWithFixedWidthImage(outer, null, Nex_VisualCustomPanel.PANEL_WIDTH,
            TEXT_HEIGHT, null, textWidth, pad, null, SHIP_PANEL_HEIGHT_SMALL, pad, null, false, null)
        val row = headerPanel.panel

        val nameTT = headerPanel.elements[1] as TooltipMakerAPI
        var rbeList = ArrayList<RadioButtonEntry>()
        addSortButton(row, nameTT, SortColumn.NAME, rbeList, textWidth, null, pad, dialog, memoryMap)

        textWidth = SHIP_WING_COLUMN_WIDTHS[1]
        val strengthTT = addSortButton(row, null, SortColumn.STRENGTH, rbeList, textWidth, nameTT, pad, dialog, memoryMap)

        textWidth = SHIP_WING_COLUMN_WIDTHS[2]
        val sizeTT = addSortButton(row, null, SortColumn.SIZE, rbeList, textWidth, strengthTT, pad, dialog, memoryMap)

        textWidth = SHIP_WING_COLUMN_WIDTHS[3]
        val designTypeTT = addSortButton(row, null, SortColumn.DESIGN_TYPE, rbeList, textWidth, sizeTT, pad, dialog, memoryMap)

        textWidth = SHIP_WING_COLUMN_WIDTHS[4]
        val costTT = addSortButton(row, null, if (isWing) SortColumn.OP_COST else SortColumn.SUPPLY_COST, rbeList,
            textWidth, designTypeTT, pad, dialog, memoryMap)

        tooltip.addCustom(row, opad)
    }

    protected fun addWeaponHeaderRow(outer : CustomPanelAPI, tooltip : TooltipMakerAPI,
                                         dialog: InteractionDialogAPI, memoryMap: Map<String?, MemoryAPI?>?) {
        val pad = 3f
        val opad = 10f

        var textWidth = WEAPON_COLUMN_WIDTHS[0]
        val headerPanel = NexUtilsGUI.addPanelWithFixedWidthImage(outer, null, Nex_VisualCustomPanel.PANEL_WIDTH,
            TEXT_HEIGHT, null, textWidth, pad, null, SHIP_PANEL_HEIGHT_SMALL, pad, null, false, null)
        val row = headerPanel.panel

        val nameTT = headerPanel.elements[1] as TooltipMakerAPI
        var rbeList = ArrayList<RadioButtonEntry>()
        addSortButton(row, nameTT, SortColumn.NAME, rbeList, textWidth, null, pad, dialog, memoryMap)

        textWidth = WEAPON_COLUMN_WIDTHS[1]
        val strengthTT = addSortButton(row, null, SortColumn.STRENGTH, rbeList, textWidth, nameTT, pad, dialog, memoryMap)

        textWidth = WEAPON_COLUMN_WIDTHS[2]
        val sizeTT = addSortButton(row, null, SortColumn.SIZE, rbeList, textWidth, strengthTT, pad, dialog, memoryMap)

        textWidth = WEAPON_COLUMN_WIDTHS[3]
        val typeTT = addSortButton(row, null, SortColumn.WEAPON_TYPE, rbeList, textWidth, sizeTT, pad, dialog, memoryMap)

        textWidth = WEAPON_COLUMN_WIDTHS[4]
        val designTypeTT = addSortButton(row, null, SortColumn.DESIGN_TYPE, rbeList, textWidth, typeTT, pad, dialog, memoryMap)

        textWidth = WEAPON_COLUMN_WIDTHS[5]
        val costTT = addSortButton(row, null, SortColumn.OP_COST, rbeList, textWidth, designTypeTT, pad, dialog, memoryMap)

        tooltip.addCustom(row, opad)
    }

    protected fun updateSort(sortColumn: SortColumn) {
        if (Companion.sortColumn == sortColumn) {
            sortAscending = !sortAscending
        } else {
            sortAscending = sortColumn.defaultAscending
            Companion.sortColumn = sortColumn
        }
        needResort = true
    }

    /**
     * Generates a sort button for the header row in the tools list.
     * Returns the TooltipMakerAPI holding the button, for the next button holder to anchor on.
     */
    protected fun addSortButton(row : CustomPanelAPI, existingHolder : TooltipMakerAPI?, sortColumn : SortColumn,
                                rbeList : MutableList<RadioButtonEntry>, textWidth : Float, prev: UIComponentAPI?, pad : Float,
                                dialog: InteractionDialogAPI, memoryMap: Map<String?, MemoryAPI?>?) : TooltipMakerAPI
    {
        var btnHolder = existingHolder ?: row.createUIElement(textWidth, TEXT_HEIGHT, false)

        val tabIdStr = sortColumn.name

        val base = Misc.getBasePlayerColor()
        val bright = Misc.getBrightPlayerColor()
        val dark = Misc.getDarkPlayerColor()
        val plugin = Nex_VisualCustomPanel.getPlugin()

        val btn = btnHolder.addAreaCheckbox(sortColumn.getHumanName(), Misc.ucFirst(tabIdStr), base, dark, bright, textWidth, TEXT_HEIGHT, 0f)
        val rbe = object : RadioButtonEntry(btn, tabIdStr, rbeList) {
            override fun onToggleImpl() {
                //log.info("Sort button pressed")
                updateSort(sortColumn)
                printMiningTools(dialog, memoryMap)
                //btn.isChecked = false
            }
        }
        rbeList.add(rbe)
        plugin.addButton(rbe)

        if (prev != null) row.addUIElement(btnHolder).rightOfTop(prev, pad)

        return btnHolder
    }

    protected fun printMiningToolsWeapon(weapons : MutableList<Pair<WeaponSpecAPI, Float>>,
                                             outer : CustomPanelAPI, tooltip : TooltipMakerAPI,
                                             dialog: InteractionDialogAPI, memoryMap: Map<String?, MemoryAPI?>?)
    {
        if (weapons.isEmpty()) return
        val pad = 3f
        val hl = Misc.getHighlightColor()

        if (needResort) {
            val sort = Nex_PrintMiningInfoSort.getComparator(sortColumn)
            Collections.sort(weapons, sort)
            //log.info("Sorting column $sortColumn with comparator $sort, ascending $sortAscending")

            if (!sortAscending) weapons.reverse()
            needResort = false
        }

        addWeaponHeaderRow(outer, tooltip, dialog, memoryMap)

        for (entry in weapons) {
            val weapon : WeaponSpecAPI = entry.one
            val strength = entry.two

            val imgWidth = SHIP_PANEL_HEIGHT_SMALL
            var textWidth = WEAPON_COLUMN_WIDTHS[0]
            val shipPanel = NexUtilsGUI.addPanelWithFixedWidthImage(outer, null, Nex_VisualCustomPanel.PANEL_WIDTH,
                SHIP_PANEL_HEIGHT_SMALL, null, textWidth, pad, null, imgWidth, pad, null, false, null)
            val row = shipPanel.panel

            val imageTT = shipPanel.elements[0] as TooltipMakerAPI;
            val cargo = Global.getFactory().createCargo(true)
            cargo.addWeapons(weapon.weaponId, 1)
            imageTT.showCargo(cargo, 1, false, 0f)
            val nameTT = shipPanel.elements[1] as TooltipMakerAPI
            nameTT.addPara(weapon.weaponName, Misc.getBasePlayerColor(), 0f).setAlignment(Alignment.MID)
            //nameTT.addPara(weapon.primaryRoleStr, pad).setAlignment(Alignment.MID)

            // mining strength
            textWidth = WEAPON_COLUMN_WIDTHS[1]
            val strengthStr = String.format("%.1f", strength)
            val strengthTT = row.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            strengthTT.addPara(strengthStr, hl, 0f).setAlignment(Alignment.MID)
            row.addUIElement(strengthTT).rightOfTop(nameTT, pad)

            // weapon size
            textWidth = WEAPON_COLUMN_WIDTHS[2]
            val sizeStr = weapon.size.displayName
            val sizeTT = row.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            sizeTT.addPara(sizeStr, 0f).setAlignment(Alignment.MID)
            row.addUIElement(sizeTT).rightOfTop(strengthTT, pad)

            // weapon type
            textWidth = WEAPON_COLUMN_WIDTHS[3]
            val typeStr = weapon.mountType.displayName
            val typeTT = row.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            typeTT.addPara(typeStr, getWeaponColor(weapon.mountType),0f).setAlignment(Alignment.MID)
            row.addUIElement(typeTT).rightOfTop(sizeTT, pad)

            // design type
            textWidth = WEAPON_COLUMN_WIDTHS[4]
            val designTypeStr = weapon.manufacturer
            val designTypeTT = row.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            designTypeTT.addPara(designTypeStr, Global.getSettings().getDesignTypeColor(designTypeStr), 0f).setAlignment(Alignment.MID)
            row.addUIElement(designTypeTT).rightOfTop(typeTT, pad)

            // OP cost
            textWidth = WEAPON_COLUMN_WIDTHS[5]
            val suppliesOrOpStr = String.format("%.0f", weapon.getOrdnancePointCost(null))
            val suppliesOrOPTT = shipPanel.panel.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            suppliesOrOPTT.addPara(suppliesOrOpStr, 0f).setAlignment(Alignment.MID)
            row.addUIElement(suppliesOrOPTT).rightOfTop(designTypeTT, pad)

            tooltip.addCustom(row, pad)
        }
    }

    protected fun printMiningToolsShipOrWing(shipsOrWings : MutableList<Pair<FleetMemberAPI, Float>>,
                                             outer : CustomPanelAPI, tooltip : TooltipMakerAPI,
                                             dialog: InteractionDialogAPI, memoryMap: Map<String?, MemoryAPI?>?)
    {
        if (shipsOrWings.isEmpty()) return
        val pad = 3f
        val hl = Misc.getHighlightColor()
        val isWing = shipsOrWings[0].one.isFighterWing

        if (needResort) {
            val sort = Nex_PrintMiningInfoSort.getComparator(sortColumn)
            Collections.sort(shipsOrWings, sort)
            //log.info("Sorting column $sortColumn with comparator $sort, ascending $sortAscending")

            if (!sortAscending) shipsOrWings.reverse()
            needResort = false
        }

        addShipOrWingHeaderRow(outer, tooltip, isWing, dialog, memoryMap)

        for (entry in shipsOrWings) {
            val member : FleetMemberAPI = entry.one
            val strength = entry.two
            val wingSpec : FighterWingSpecAPI? = if (isWing) Global.getSettings().getFighterWingSpec(member.specId) else null

            val shipName = if (isWing) wingSpec!!.wingName else member.hullSpec.hullName

            val imgWidth = SHIP_PANEL_HEIGHT_SMALL
            var textWidth = SHIP_WING_COLUMN_WIDTHS[0]
            val shipPanel = NexUtilsGUI.addPanelWithFixedWidthImage(outer, null, Nex_VisualCustomPanel.PANEL_WIDTH,
                SHIP_PANEL_HEIGHT_SMALL, null, textWidth, pad, null, imgWidth, pad, null, false, null)
            val row = shipPanel.panel

            NexUtilsGUI.addSingleShipList(shipPanel.elements[0] as TooltipMakerAPI, SHIP_PANEL_HEIGHT_SMALL, member, 0f)
            val nameTT = shipPanel.elements[1] as TooltipMakerAPI
            nameTT.addPara(shipName, Misc.getBasePlayerColor(), 0f).setAlignment(Alignment.MID)
            if (isWing) {
                nameTT.addPara(wingSpec?.roleDesc, pad).setAlignment(Alignment.MID)
            } else {
                nameTT.addPara(member.hullSpec.designation, pad).setAlignment(Alignment.MID)
            }

            // mining strength
            textWidth = SHIP_WING_COLUMN_WIDTHS[1]
            val strengthStr = String.format("%.1f", strength)
            val strenghTT = row.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            strenghTT.addPara(strengthStr, hl, 0f).setAlignment(Alignment.MID)
            row.addUIElement(strenghTT).rightOfTop(nameTT, pad)

            // ship size
            textWidth = SHIP_WING_COLUMN_WIDTHS[2]
            var hullSize = member.hullSpec.hullSize
            val key = if (hullSize == ShipAPI.HullSize.CAPITAL_SHIP) "capital_ship_short" else hullSize.toString().lowercase()
            val sizeStr = Misc.ucFirst(StringHelper.getString(key))
            val sizeTT = row.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            sizeTT.addPara(sizeStr, 0f).setAlignment(Alignment.MID)
            row.addUIElement(sizeTT).rightOfTop(strenghTT, pad)

            // design type
            textWidth = SHIP_WING_COLUMN_WIDTHS[3]
            val designTypeStr = member.hullSpec.manufacturer
            val designTypeTT = row.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            designTypeTT.addPara(designTypeStr, Global.getSettings().getDesignTypeColor(designTypeStr), 0f).setAlignment(Alignment.MID)
            row.addUIElement(designTypeTT).rightOfTop(sizeTT, pad)

            // supplies/OP cost
            textWidth = SHIP_WING_COLUMN_WIDTHS[4]
            val suppliesOrOpStr = String.format("%.0f", (if (isWing) wingSpec?.getOpCost(null) else member.hullSpec.suppliesPerMonth))
            val suppliesOrOPTT = shipPanel.panel.createUIElement(textWidth, SHIP_PANEL_HEIGHT_SMALL, false)
            suppliesOrOPTT.addPara(suppliesOrOpStr, 0f).setAlignment(Alignment.MID)
            row.addUIElement(suppliesOrOPTT).rightOfTop(designTypeTT, pad)

            tooltip.addCustom(row, pad)
        }
    }

    protected fun printMiningTools(dialog : InteractionDialogAPI, memoryMap: Map<String?, MemoryAPI?>?) {
        Nex_VisualCustomPanel.createPanel(dialog, true)
        addTabButtons(Nex_VisualCustomPanel.getPanel(), Nex_VisualCustomPanel.getTooltip(), dialog, memoryMap)

        when (miningToolTab) {
            MiningToolTab.SHIP -> {
                val mem = memoryMap?.get(MemKeys.LOCAL)
                var miningShips = mem?.get(MEMORY_KEY_MINING_HULLS) as? MutableList<Pair<FleetMemberAPI, Float>>
                if (miningShips == null) {
                    miningShips = getMiningHulls(dialog)
                    mem?.set(MEMORY_KEY_MINING_HULLS, miningShips, 0f)
                }
                printMiningToolsShipOrWing(miningShips, Nex_VisualCustomPanel.getPanel(), Nex_VisualCustomPanel.getTooltip(), dialog, memoryMap)
            }
            MiningToolTab.WING -> {
                val mem = memoryMap?.get(MemKeys.LOCAL)
                var miningWings = mem?.get(MEMORY_KEY_MINING_WINGS) as? MutableList<Pair<FleetMemberAPI, Float>>
                if (miningWings == null) {
                    miningWings = getMiningWings()
                    mem?.set(MEMORY_KEY_MINING_WINGS, miningWings, 0f)
                }
                printMiningToolsShipOrWing(miningWings, Nex_VisualCustomPanel.getPanel(), Nex_VisualCustomPanel.getTooltip(), dialog, memoryMap)
            }
            MiningToolTab.WEAPON -> {
                val mem = memoryMap?.get(MemKeys.LOCAL)
                var miningWeapons = mem?.get(MEMORY_KEY_MINING_WEAPONS) as? MutableList<Pair<WeaponSpecAPI, Float>>
                if (miningWeapons == null) {
                    miningWeapons = getMiningWeapons()
                    mem?.set(MEMORY_KEY_MINING_WEAPONS, miningWeapons, 0f)
                }
                printMiningToolsWeapon(miningWeapons, Nex_VisualCustomPanel.getPanel(), Nex_VisualCustomPanel.getTooltip(), dialog, memoryMap)
            }
        }

        Nex_VisualCustomPanel.addTooltipToPanel()
    }

    protected fun addTabButtons(outer : CustomPanelAPI, tooltip: TooltipMakerAPI, dialog : InteractionDialogAPI, memoryMap: Map<String?, MemoryAPI?>?) {
        val pad = 3f
        val opad = 10f
        val base = Misc.getBasePlayerColor()
        val bright = Misc.getBrightPlayerColor()
        val dark = Misc.getDarkPlayerColor()
        val plugin = Nex_VisualCustomPanel.getPlugin()

        val buttonRow = outer.createCustomPanel(Nex_VisualCustomPanel.PANEL_WIDTH - opad * 2, TEXT_HEIGHT + pad * 2,
            FramedCustomPanelPlugin(0f, Misc.getBasePlayerColor(), false)
        )
        val rbeList = ArrayList<RadioButtonEntry>()
        var btnHolderList = ArrayList<TooltipMakerAPI>()

        for (tabId in MiningToolTab.values()) {
            val tabIdStr = tabId.name
            val btnHolder = buttonRow.createUIElement(96f, TEXT_HEIGHT, false)
            btnHolder.setForceProcessInput(true)
            val btn = btnHolder.addAreaCheckbox(Misc.ucFirst(tabId.getHumanName()), tabIdStr, base, dark, bright, 96f, TEXT_HEIGHT, 0f)
            btn.isChecked = tabId == miningToolTab
            val rbe = object : RadioButtonEntry(btn, tabIdStr, rbeList) {
                override fun onToggleImpl() {
                    miningToolTab = tabId
                    needResort = true
                    printMiningTools(dialog, memoryMap)
                }
            }
            rbeList.add(rbe)
            plugin.addButton(rbe)
            if (btnHolderList.isEmpty()) {
                buttonRow.addUIElement(btnHolder).inTL(pad, pad)
            }
            else {
                buttonRow.addUIElement(btnHolder).rightOfTop(btnHolderList[btnHolderList.size - 1], pad)
            }
            btnHolderList.add(btnHolder)
        }
        tooltip.addCustom(buttonRow, 10f)
    }

    protected fun getWeaponColor(type : WeaponAPI.WeaponType): Color {
        return when(type) {
            WeaponAPI.WeaponType.BALLISTIC -> Misc.MOUNT_BALLISTIC
            WeaponAPI.WeaponType.ENERGY -> Misc.MOUNT_ENERGY
            WeaponAPI.WeaponType.MISSILE -> Misc.MOUNT_MISSILE
            WeaponAPI.WeaponType.HYBRID -> Misc.MOUNT_HYBRID
            WeaponAPI.WeaponType.COMPOSITE -> Misc.MOUNT_COMPOSITE
            WeaponAPI.WeaponType.SYNERGY -> Misc.MOUNT_SYNERGY
            else -> Misc.getTextColor()
        }
    }

    enum class MiningToolTab {
        SHIP, WEAPON, WING;

        fun getHumanName() : String {
            when (this) {
                SHIP -> return StringHelper.getString("ship")
                WEAPON -> return StringHelper.getString("weapon")
                WING -> return StringHelper.getString("fighterWingShort")
            }
        }
    }

    enum class SortColumn(val stringId : String, val defaultAscending : Boolean) {
        NAME("name", true),
        STRENGTH("strength", false),
        SIZE("size", false),
        WEAPON_TYPE("type", false),
        DESIGN_TYPE("manufacturer", true),
        OP_COST("opCost", false),
        SUPPLY_COST("supplyCost", false);

        fun getHumanName() : String {
            return getString("miningToolsHeader_$stringId")
        }
    }
}