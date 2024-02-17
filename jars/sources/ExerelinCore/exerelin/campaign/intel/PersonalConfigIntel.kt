package exerelin.campaign.intel

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.impl.campaign.Tuning
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseManager
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.Pair
import exerelin.campaign.PlayerFactionStore
import exerelin.campaign.StatsTracker
import exerelin.campaign.intel.diplomacy.DiplomacyProfileIntel
import exerelin.campaign.ui.CustomPanelPluginWithInput
import exerelin.campaign.ui.FramedCustomPanelPlugin
import exerelin.campaign.ui.MusicPickerDialogDelegate
import exerelin.campaign.ui.VictoryScreenScript
import exerelin.campaign.ui.VictoryScreenScript.VictoryDialog
import exerelin.utilities.ModPluginEventListener
import exerelin.utilities.NexConfig
import exerelin.utilities.NexUtils
import exerelin.utilities.StringHelper
import org.lazywizard.lazylib.ext.logging.i
import java.awt.Color
import java.lang.NullPointerException

open class PersonalConfigIntel : BaseIntelPlugin(), ModPluginEventListener {

    companion object {

        @JvmField val log = Global.getLogger(PersonalConfigIntel.javaClass);

        @JvmField val BUTTON_CHANGE_HONORIFIC = Any()
        const val PERSISTENT_DATA_KEY = "nex_personalConfigIntel"
        const val MEMORY_KEY_PREFIX_MUSIC = "\$nex_savedMusic_"
        const val MUSIC_ROW_HEIGHT = 24f
        const val MUSIC_TITLE_WIDTH = 160f
        const val MUSIC_TRACKNAME_WIDTH = 360f
        const val MUSIC_BUTTON_WIDTH = 96f
		const val TEXT_INPUT_WIDTH = 742f   // just enough to not fit the full PAGSM Andrada title

        @JvmField val MUSIC_MAP_KEYS = listOf("encounter_friendly", "market_friendly",
            "encounter_neutral", "market_neutral", "encounter_hostile", "market_hostile")

        @JvmField val ETHOSES = listOf("AntiAI", "Cocky", "Freedom", "Generous", "Honorable", "Humanitarian", "Idealistic",
            "Knowledge", "Liar", "Mercenary", "Mercy", "Order", "ProAI", "Ruthless", "Sarcastic", "Truth", "UseAI")

        @JvmField val LUDDIC_ATTITUDES = listOf("Agnostic", "Atheistic", "Cynical", "Faithful", "Pather")

        @JvmStatic
        fun getString(id: String) : String {
            return StringHelper.getString("nex_personal", id);
        }

        // runcode exerelin.campaign.intel.PersonalConfigIntel.create()
        @JvmStatic
        fun create() : PersonalConfigIntel {
            val intel = PersonalConfigIntel()
            Global.getSector().persistentData.put(PERSISTENT_DATA_KEY, intel)
            Global.getSector().intelManager.addIntel(intel, true)
            return intel
        }

        // runcode exerelin.campaign.intel.PersonalConfigIntel.get()
        @JvmStatic
        fun get() : PersonalConfigIntel? {
            return Global.getSector().persistentData[PERSISTENT_DATA_KEY] as PersonalConfigIntel?
        }

        @JvmStatic
        fun playMusic(musicSetId : String) {
            try {
                Global.getSoundPlayer().playCustomMusic(1, 1, musicSetId, false)
            } catch (ex : NullPointerException) {}
        }

        @JvmStatic
        fun reapplyMusicOnLoad() {
            val faction = Global.getSector().playerFaction
            for (musicType in MUSIC_MAP_KEYS) {
                var saved : String? = faction.memoryWithoutUpdate.getString(MEMORY_KEY_PREFIX_MUSIC + musicType)
                //log.info("Saved music for $musicType is $saved")
                if (saved != null) faction.musicMap[musicType] = saved
            }
        }

        @JvmStatic
        fun saveMusic(musicType : String, musicSetId: String?) {
            Global.getSector().playerFaction.musicMap[musicType] = musicSetId
            Global.getSector().playerFaction.memoryWithoutUpdate[MEMORY_KEY_PREFIX_MUSIC + musicType] = musicSetId
        }

        @JvmStatic
        fun addMusicButtonRow(id: String, title: String, trackName : String?, titleColor : Color?, trackNameColor : Color?,
                              outerPanel: CustomPanelAPI, tooltip : TooltipMakerAPI, width : Float,
                              dialog : MusicPickerDialogDelegate?) {
            val pad = 3f
            val row : CustomPanelAPI = outerPanel.createCustomPanel(width, MUSIC_ROW_HEIGHT, null)

            val rowTT : TooltipMakerAPI = row.createUIElement(width, MUSIC_ROW_HEIGHT, false)
            rowTT.setForceProcessInput(true)
            //log.info("Adding row with id $id, title $title, track ")
            val titleLabel = rowTT.addPara(title, titleColor ?: Misc.getTextColor(), 0f)
            var x = MUSIC_TITLE_WIDTH + pad

            val trackLabel = rowTT.addPara(trackName, trackNameColor ?: Misc.getTextColor(), 0f)
            trackLabel.position.inTL(x, 0f)
            x += MUSIC_TRACKNAME_WIDTH + pad

            val playBtn = rowTT.addButton(getString("btnPlayMusic"), "preview_" + id, MUSIC_BUTTON_WIDTH, MUSIC_ROW_HEIGHT, 0f)
            playBtn.position.inTL(x, 0f)
            x += MUSIC_BUTTON_WIDTH + pad
            dialog?.customPanelPlugin?.addButton(object :
                CustomPanelPluginWithInput.ButtonEntry(playBtn, "preview_" + id) {
                override fun onToggle() {
                    playMusic(trackName!!)
                }
            })

            val changeBtn = rowTT.addButton(getString("btnChangeMusic"), "change_" + id, MUSIC_BUTTON_WIDTH, MUSIC_ROW_HEIGHT, 0f)
            changeBtn.position.inTL(x, 0f)
            x += MUSIC_BUTTON_WIDTH + pad
            dialog?.customPanelPlugin?.addButton(object :
                CustomPanelPluginWithInput.ButtonEntry(changeBtn, "change_" + id) {
                override fun onToggle() {
                    //log.info("wololo changing music to $id")
                    saveMusic(dialog.musicType, trackName)
                    dialog.callback?.dismissCustomDialog(0)
                    dialog.interactionDialog?.dismiss()
                    dialog.intelUIAPI.updateUIForItem(dialog.intel)
                }
            })

            tooltip.addCustom(row, pad)

            row.addUIElement(rowTT).inTL(0f, 0f)
        }
    }

    @Transient protected var textFieldHonorific : TextFieldAPI? = null

    fun addPortraitFlagAndHonorificPicker(outerPanel: CustomPanelAPI, tooltip: TooltipMakerAPI, ttWidth: Float) {
        val player = Global.getSector().playerPerson
        val opad = 10f;
        val pad = 3f;
        var width = 128 + pad + 410/2;
        var height = 128f;
        val imageHolder = outerPanel.createCustomPanel(width, height, null)
        val imageTT = imageHolder.createUIElement(width, height, false)
        imageTT.addImages(width, height, 0f, pad, player.portraitSprite, factionForUIColors.logo)
        imageHolder.addUIElement(imageTT).inTL(0f, 0f)

        tooltip.addCustom(imageHolder, opad)

        // honorific picker
        width = ttWidth - width - pad
        val textHolder = outerPanel.createCustomPanel(width, height, null)
        val textTT = textHolder.createUIElement(width, height, false)
        textTT.addPara(player.nameString, Misc.getHighlightColor(), 0f)
        textFieldHonorific = textTT.addTextField(TEXT_INPUT_WIDTH.coerceAtMost(width), Fonts.DEFAULT_SMALL, pad)
        textFieldHonorific!!.text = Global.getSector().characterData.honorific
        textTT.addButton(getString("btnChangeHonorific"), BUTTON_CHANGE_HONORIFIC, 240f, 24f, pad)
        textHolder.addUIElement(textTT).inTL(0f, 0f)

        tooltip.addCustomDoNotSetPosition(textHolder)
        textHolder.position.rightOfTop(imageHolder, 3f)
        //textHolder.position.inTR(pad, 0f)
    }



    fun createMusicSelectorWindow(musicType : String, intelUI : IntelUIAPI) {
        val width = (Global.getSettings().screenWidth * 0.6f).coerceAtLeast(800f)
        val height = (Global.getSettings().screenHeight * 0.8f).coerceAtLeast(640f)

        val delegate = MusicPickerDialogDelegate(musicType, width, height, intelUI, this)
        intelUI.showDialog(Global.getSector().playerFleet, MusicPickerDialogDelegate.MusicPickerDialogPlugin(delegate))
    }

    fun addMusicSelector(outerPanel: CustomPanelAPI, tooltip: TooltipMakerAPI, ttWidth: Float) {
        val pad = 3f
        val opad = 10f
        val playerFaction = Global.getSector().playerFaction

        tooltip.addSectionHeading(getString("intelHeaderMusic"), factionForUIColors.baseUIColor, factionForUIColors.darkUIColor, Alignment.MID, opad)
        val height = (MUSIC_ROW_HEIGHT + pad) * 6
        val width = ttWidth - opad
        val rowsHolder = outerPanel.createCustomPanel(width, height, FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false))
        val rowsTT = rowsHolder.createUIElement(ttWidth - pad - pad, height, false)

        for (musicMapKey in MUSIC_MAP_KEYS) {
            val curr = playerFaction.musicMap.get(musicMapKey)
            val title = getString("musicMap_name_$musicMapKey")
            val color : Color? = if (musicMapKey == "encounter_friendly" || musicMapKey == "market_friendly") Misc.getHighlightColor() else null
            addMusicButtonRow(musicMapKey, title, curr, color, null, rowsHolder, rowsTT, width, null)
        }
        rowsHolder.addUIElement(rowsTT).inTL(0f, 0f)
        tooltip.addCustom(rowsHolder, pad)
    }

    protected fun addStatsTable(outerPanel: CustomPanelAPI, tooltip: TooltipMakerAPI, tableWidth : Float, xPos : Float) {
        val opad = 10f
        val tc = Misc.getTextColor()
        val h = Misc.getHighlightColor()
        val good = Misc.getPositiveHighlightColor()
        val bad = Misc.getNegativeHighlightColor()
        var track = StatsTracker.getOrCreateTracker();

        var statsTable = tooltip.beginTable(
            factionForUIColors, 20f,
            getString("tableHeaderStat"), tableWidth/3*2,
            getString("tableHeaderValue"), tableWidth/3
        )

        // level
        tooltip.addRow(tc, VictoryDialog.getString("statsLevel"), h,
            Global.getSector().playerPerson.stats.level.toString() + "")
        // days elapsed
        tooltip.addRow(tc, VictoryDialog.getString("statsDaysElapsed"), h,
            Misc.getWithDGS(NexUtils.getTrueDaysSinceStart()))
        // ships killed
        tooltip.addRow(tc, VictoryDialog.getString("statsShipsKilled"), good,
            track.shipsKilled.toString())
        // ships lost
        tooltip.addRow(tc, VictoryDialog.getString("statsShipsLost"), bad,
            track.shipsLost.toString())
        // FP killed
        tooltip.addRow(tc, VictoryDialog.getString("statsFpKilled"), good,
            Misc.getWithDGS(track.fpKilled))
        // FP lost
        tooltip.addRow(tc, VictoryDialog.getString("statsFpLost"), bad,
            Misc.getWithDGS(track.fpLost))
        // officer deaths
        if (VictoryScreenScript.haveOfficerDeaths()) tooltip.addRow(tc, VictoryDialog.getString("statsOfficersLost"), bad,
            track.numOfficersLost.toString())
        // orphans made
        tooltip.addRow(tc, VictoryDialog.getString("statsOrphansMade"), h,
            Misc.getWithDGS(track.orphansMade.toFloat()))
        // planets surveyed
        tooltip.addRow(tc, VictoryDialog.getString("statsPlanetsSurveyed"), h,
            Misc.getWithDGS(track.planetsSurveyed.toFloat()))
        // markets captured
        tooltip.addRow(tc, VictoryDialog.getString("statsMarketsCaptured"), h,
            track.marketsCaptured.toString())
        // markets raided
        tooltip.addRow(tc, VictoryDialog.getString("statsMarketsRaided"), h,
            track.marketsRaided.toString())
        // tactical bombardments
        tooltip.addRow(tc, VictoryDialog.getString("statsTacticalBombardments"), h,
            track.marketsTacBombarded.toString())
        // saturation bombardments
        tooltip.addRow(tc, VictoryDialog.getString("statsSaturationBombardments"), bad,
            track.marketsSatBombarded.toString())
        // prisoners repatriated
        tooltip.addRow(tc, VictoryDialog.getString("statsPrisonersRepatriated"), h,
            track.prisonersRepatriated.toString())
        // prisoners ransomed
        tooltip.addRow(tc, VictoryDialog.getString("statsPrisonersRansomed"), h,
            track.prisonersRansomed.toString())

        tooltip.addTable("", 0, opad)
        statsTable.position.inTL(xPos, 0f)
    }

    protected fun addEthosTable(outerPanel: CustomPanelAPI, tooltip: TooltipMakerAPI, tableWidth : Float, xPos : Float) {
        val opad = 10f
        val tc = Misc.getTextColor()
        val h = Misc.getHighlightColor()
        val memory = Global.getSector().characterData.memoryWithoutUpdate;

        var statsTable = tooltip.beginTable(
            factionForUIColors, 20f,
            getString("tableHeaderEthos"), tableWidth/3*2,
            getString("tableHeaderValue"), tableWidth/3
        )
        for (ethos : String in ETHOSES) {
            var key = "\$ethos$ethos"
            var score = if (memory.contains(key)) memory.getFloat(key).toInt() else 0
            tooltip.addRow(tc, getString("ethosName_$ethos"), h, score.toString())
        }

        tooltip.addTable("", 0, opad)
        statsTable.position.inTL(xPos, 0f)
    }

    protected fun addLuddicAttitudeTable(outerPanel: CustomPanelAPI, tooltip: TooltipMakerAPI, tableWidth : Float, xPos : Float) {
        val opad = 10f
        val tc = Misc.getTextColor()
        val h = Misc.getHighlightColor()
        val memory = Global.getSector().characterData.memoryWithoutUpdate;
        val faction = Global.getSector().getFaction(Factions.LUDDIC_CHURCH)

        var scoreMap = HashMap<String, Int>()
        var highest : String? = null;
        var highestScore : Int = 0;
        for (attitude : String in LUDDIC_ATTITUDES) {
            var key = "\$luddicAttitude$attitude"
            var score = if (memory.contains(key)) memory.getFloat(key).toInt() else 0
            scoreMap[attitude] = score
            if (score > highestScore && score > 3) {
                highestScore = score
                highest = attitude
            }
        }

        var statsTable = tooltip.beginTable(
            faction, 20f,
            getString("tableHeaderLuddicAttitude"), tableWidth/3*2,
            getString("tableHeaderValue"), tableWidth/3
        )
        for (attitude : String in LUDDIC_ATTITUDES) {
            var score = scoreMap[attitude]
            tooltip.addRow(tc, getString("luddicAttitudeName_$attitude"),
                if (attitude == highest) faction.baseUIColor else h, score.toString())
        }

        tooltip.addTable("", 0, opad)
        statsTable.position.inTL(xPos, 0f)
    }

    protected fun addPersonalStats(outerPanel: CustomPanelAPI, tooltip: TooltipMakerAPI, ttWidth: Float) {
        val pad = 3f
        val opad = 10f

        var availableWidth = ttWidth - opad * 2;

        val tableWidth = availableWidth/3 - opad;

        val row = outerPanel.createCustomPanel(availableWidth, 400f, null)
        val tableTT = row.createUIElement(availableWidth, 400f, false)

        // first table: stats
        var xPos = opad
        addStatsTable(row, tableTT, tableWidth, xPos)
        xPos += tableWidth + opad
        // second table: ethos
        addEthosTable(row, tableTT, tableWidth, xPos)
        xPos += tableWidth + opad
        // third table: Luddic attitude
        addLuddicAttitudeTable(row, tableTT, tableWidth, xPos)

        row.addUIElement(tableTT).inTL(0f, 0f)
        tooltip.addCustom(row, opad)
    }

    override fun createLargeDescription(panel: CustomPanelAPI, width: Float, height: Float) {
        try {

            val player = Global.getSector().playerPerson
            val pad = 3f
            val opad = 10f

            // TooltipMakerAPI that holds all other elements
            val tooltip = panel.createUIElement(width, height, true)

            // title
            tooltip.addSectionHeading(
                smallDescriptionTitle + ": " + player.nameString, factionForUIColors.baseUIColor,
                factionForUIColors.darkUIColor, Alignment.MID, opad
            )

            // portrait, flag, honorific picker
            addPortraitFlagAndHonorificPicker(panel, tooltip, width)

            // music selector
            addMusicSelector(panel, tooltip, width)

            // stats, ethos, Luddic attitude
            addPersonalStats(panel, tooltip, width)

            // diplo alignment selector
            //DiplomacyProfileIntel.createAlignmentButtons(factionForUIColors, panel, tooltip, width, pad)

            panel.addUIElement(tooltip).inTL(0f, 0f)

        } catch (ex : Exception) {
            log.error("Intel display error", ex)
        }
    }

    override fun buttonPressConfirmed(buttonId: Any?, ui: IntelUIAPI?) {
        if (buttonId === BUTTON_CHANGE_HONORIFIC) {
            var txt : String? = textFieldHonorific!!.text;
            if (txt?.isBlank() == true) txt = null
            Global.getSector().characterData.honorific = txt
            return
        }

        if (buttonId is Pair<*, *>) {
            DiplomacyProfileIntel.updateDisposition(buttonId, Global.getSector().playerFaction)
            ui!!.updateUIForItem(this)
            return
        }

        if (buttonId is String && (buttonId as String).startsWith("preview_")) {
            val type = (buttonId as String).substring("preview_".length)
            val musicSetId = Global.getSector().playerFaction.musicMap[type] ?: return
            playMusic(musicSetId)
            return
        }

        if (buttonId is String && (buttonId as String).startsWith("change_")) {
            val type = (buttonId as String).substring("change_".length)
            createMusicSelectorWindow(type, ui!!)
            return
        }
    }

    override fun getIcon(): String {
        return Global.getSector().playerPerson.portraitSprite
    }

    override fun getIntelTags(map: SectorMapAPI?): MutableSet<String> {
        val tags = super.getIntelTags(map)
        tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"))
        return tags
    }

    override fun getSortTier(): IntelInfoPlugin.IntelSortTier {
        return IntelInfoPlugin.IntelSortTier.TIER_1
    }

    override fun hasSmallDescription(): Boolean {
        return false
    }

    override fun hasLargeDescription(): Boolean {
        return true
    }

    override fun getName(): String {
        return getString("intelTitle")
    }

    override fun getFactionForUIColors(): FactionAPI {
        return PlayerFactionStore.getPlayerFaction();
    }

    override fun onGameLoad(newGame: Boolean) {
        reapplyMusicOnLoad()
    }

    override fun beforeGameSave() {}

    override fun afterGameSave() {}

    override fun onGameSaveFailed(){}

    override fun onNewGameAfterProcGen() {}

    override fun onNewGameAfterEconomyLoad() {}

    override fun onNewGameAfterTimePass() {}
}