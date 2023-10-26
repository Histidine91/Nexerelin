package com.fs.starfarer.api.impl.campaign.rulecmd

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.MonthlyReport
import com.fs.starfarer.api.campaign.econ.MonthlyReport.FDNode
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.*
import com.fs.starfarer.api.impl.campaign.shared.SharedData
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.backgrounds.CharacterBackgroundIntel
import exerelin.campaign.backgrounds.CharacterBackgroundUtils
import exerelin.campaign.backgrounds.scripts.HeavyDebtObligation
import exerelin.utilities.StringHelper
import java.util.*

class Nex_DebtBackgroundKantaCMD : BaseCommandPlugin() {
    override fun execute(ruleId: String,dialog: InteractionDialogAPI,  params: List<Misc.Token>, memoryMap: Map<String, MemoryAPI>): Boolean {

        var textPanel = dialog.textPanel
        var visualPanel = dialog.visualPanel
        var optionPanel = dialog.optionPanel

        val arg = params[0].getString(memoryMap)
        if (arg == "recreateOptions") {
            optionPanel.clearOptions()
            optionPanel.addOption(CharacterBackgroundUtils.getString("bgDebt_dialogOpt_discussDebt"), "nex_BGDebtKanta2")
            optionPanel.addOption(StringHelper.getString("leave", true), "kpLeaveProt2")
            dialog.makeStoryOption("nex_BGDebtKanta2", 1, 1f, Sounds.STORY_POINT_SPEND)
            optionPanel.addOptionConfirmation("nex_BGDebtKanta2", DebtBackgroundStoryDelegate(textPanel))
            optionPanel.setTooltip("nex_BGDebtKanta2", CharacterBackgroundUtils.getString("bgDebt_dialogOpt_discussDebtTooltip"))
        }

        if (arg == "continueConversation") {
            optionPanel.clearOptions()

            var fleet = createFleet()
            var commander = fleet.commander

            Global.getSector().memoryWithoutUpdate.set("\$nex_debtBackgroundTarget", fleet)

            textPanel.addPara(CharacterBackgroundUtils.getString("bgDebt_dialogOpt_discussDebt"), Misc.getStoryOptionColor(), Misc.getStoryOptionColor())

            textPanel.addPara(CharacterBackgroundUtils.getString("bgDebt_dialog_discussDebt1"))

            textPanel.addPara(String.format(CharacterBackgroundUtils.getString("bgDebt_dialog_discussDebt2"),
                commander.himOrHer, commander.heOrShe.uppercase(), commander.himOrHer))

            textPanel.addPara(CharacterBackgroundUtils.getString("bgDebt_dialog_discussDebt3"))

            textPanel.setFontSmallInsignia()
            textPanel.addPara(CharacterBackgroundUtils.getString("bgDebt_dialog_discussDebt4"), Misc.getStoryOptionColor(), Misc.getStoryOptionColor())
            textPanel.setFontInsignia()

            var intel = Global.getSector().intelManager.getIntel(CharacterBackgroundIntel::class.java).first() as CharacterBackgroundIntel
            intel.location = fleet.starSystem
            Global.getSector().intelManager.addIntelToTextPanel(intel, textPanel)

            visualPanel.showMapMarker(fleet, fleet.name, Global.getSector().getFaction(Factions.PIRATES).color, false, null, "", null)

            optionPanel.addOption("Leave", "kpLeaveProt2")
        }

        return false
    }

    fun createFleet() : CampaignFleetAPI {

        var system = Global.getSector().starSystems
            .filter { !it.hasTag(Tags.THEME_UNSAFE) && !it.hasTag(Tags.THEME_HIDDEN) && !it.hasTag(Tags.CORONAL_TAP) && !it.hasTag(Tags.THEME_CORE_POPULATED) && it.planets.any { planet -> !planet.isStar } }
            .random()

        var planet = system.planets.filter { !it.isStar }.random()


        val params = FleetParamsV3(null,
            planet.locationInHyperspace,
            Factions.PIRATES,
            3f,
            FleetTypes.SCAVENGER_LARGE,
            180f,  // combatPts
            40f,  // freighterPts
            30f,  // tankerPts
            0f,  // transportPts
            0f,  // linerPts
            0f,  // utilityPts
            0f // qualityMod
        )

        params.random = Random()
        params.withOfficers = true

        var fleet = FleetFactoryV3.createFleet(params)

        val location = system
        location.addEntity(fleet)

        fleet.clearAssignments()
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, planet, 9999999f)
        fleet.setLocation(planet.location.x, planet.location.y)
        fleet.facing = Random().nextFloat() * 360f

        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_NO_REP_IMPACT] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORES_OTHER_FLEETS] = true

        fleet.name = CharacterBackgroundUtils.getString("bgDebt_fleetName")
        fleet.addEventListener(DebtCollectorFleetListener())

        Misc.makeImportant(fleet, "nex_debtBackgroundTarget")

        return fleet

    }

    class DebtCollectorFleetListener() : FleetEventListener {
        override fun reportFleetDespawnedToListener(fleet: CampaignFleetAPI?, reason: CampaignEventListener.FleetDespawnReason?, param: Any?) {
            endObligation()
        }

        override fun reportBattleOccurred(fleet: CampaignFleetAPI?, primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {
            if (primaryWinner == Global.getSector().playerFleet) {
                endObligation()
            }
        }

        fun endObligation() {
            Global.getSector().memoryWithoutUpdate.set("\$nex_defeatedDebtBackgroundTarget", true)
            Global.getSector().listenerManager.removeListenerOfClass(HeavyDebtObligation::class.java)
            Global.getSector().campaignUI.addMessage(CharacterBackgroundUtils.getString("bgDebt_msg_win"))

            val report = SharedData.getData().currentReport

            val debt: Int = 0
            val fleetNode: FDNode = report.getNode(MonthlyReport.FLEET)

            val stipendNode: FDNode = report.getNode(fleetNode, "SpacerObligation")
            stipendNode.upkeep = debt.toFloat()
            stipendNode.name = CharacterBackgroundUtils.getString("bgDebt_reportNode_name")
            stipendNode.icon = Global.getSettings().getSpriteName("income_report", "generic_expense")

        }

    }

    class DebtBackgroundStoryDelegate(var panel: TextPanelAPI) : StoryPointActionDelegate {
        override fun getTitle(): String {
            return CharacterBackgroundUtils.getString("bgDebt_dialogOpt_discussDebt")
        }

        override fun withDescription(): Boolean {
            return true
        }

        override fun withSPInfo(): Boolean {
           return true
        }

        override fun createDescription(info: TooltipMakerAPI?) {
            info!!.addPara(CharacterBackgroundUtils.getString("bgDebt_dialogOpt_discussDebtTooltip"), 0f)
        }

        override fun getBonusXPFraction(): Float {
            return 0f
        }

        override fun getTextPanel(): TextPanelAPI {
           return panel
        }

        override fun preConfirm() {

        }

        override fun confirm() {

        }

        override fun getConfirmSoundId(): String {
            return Sounds.STORY_POINT_SPEND
        }

        override fun getRequiredStoryPoints(): Int {
            return 1
        }

        override fun getLogText(): String {
           return ""
        }

    }
}