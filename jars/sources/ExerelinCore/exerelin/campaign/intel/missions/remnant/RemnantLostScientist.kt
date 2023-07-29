package exerelin.campaign.intel.missions.remnant

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.fleets.misc.MiscAcademyFleetCreator
import com.fs.starfarer.api.impl.campaign.ids.*
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_DecivEvent
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.SectorManager
import exerelin.campaign.colony.ColonyTargetValuator
import exerelin.utilities.NexUtilsAstro
import exerelin.utilities.StringHelper
import java.awt.Color


open class RemnantLostScientist : HubMissionWithBarEvent() {

    companion object {
        const val REWARD = 30000f;
        const val REWARD2 = 30000f;
    }

    enum class Stage {
        GO_TO_ACADEMY, GO_TO_PLANET, CHECK_OUT_FLEET, RETURN_TO_MIDNIGHT, SUCCESS
    }

    var academy : SectorEntityToken? = null
    var planet : PlanetAPI? = null
    var scientist : PersonAPI = RemnantQuestUtils.getOrCreateLostScientist()
    var pirateBase : PirateBaseIntel? = null

    /*
     store in player or global memory:
        got paper
        returning scientist
        attacked Mauve early
        killed Mauve early
     */

    override fun create(createdAt: MarketAPI?, barEvent: Boolean): Boolean {

        if (!setGlobalReference("\$nex_remLostScientist_ref")) {
            return false
        }

        academy = findAcademy()
        if (academy == null) return false;

        requireSystemNotBlackHole()
        requireSystemNotHasPulsar()
        requireSystemTags(
            ReqMode.NOT_ANY,
            Tags.THEME_UNSAFE,
            Tags.THEME_CORE,
            Tags.THEME_REMNANT_RESURGENT,
            Tags.THEME_REMNANT_SECONDARY,
            Tags.TRANSIENT,
            Tags.SYSTEM_CUT_OFF_FROM_HYPER,
            Tags.THEME_HIDDEN
        )
        preferSystemUnexplored()
        requirePlanetConditions(ReqMode.ANY, Conditions.DECIVILIZED)
        requirePlanetUnpopulated()
        preferPlanetConditions(ReqMode.ANY, Conditions.HABITABLE)
        planet = pickPlanet()
        if (planet == null) return false

        setRepPersonChangesMedium()
        setRepFactionChangesLow()

        setStartingStage(Stage.GO_TO_ACADEMY)
        setSuccessStage(Stage.SUCCESS)

        //setupTriggersOnAccept()

        return false;
    }

    fun setupTriggersOnAccept() {
        makeImportant(academy, "\$nex_remLostScientist_imp", Stage.GO_TO_ACADEMY)
        makeImportant(planet, "\$nex_remLostScientist_imp", Stage.GO_TO_PLANET)

        beginStageTrigger(Stage.GO_TO_ACADEMY)
        currTrigger.id = "goToAcademy_stage"
        triggerSetMemoryValue(planet, ColonyTargetValuator.MEM_KEY_NO_COLONIZE, false)
        endTrigger()

        beginStageTrigger(Stage.GO_TO_PLANET)
        currTrigger.id = "goToPlanet_stage"
        triggerSetMemoryValue(planet, Nex_DecivEvent.MEM_KEY_HAS_EVENT, false)
        endTrigger()

        beginWithinHyperspaceRangeTrigger(planet, 3f, false, Stage.GO_TO_PLANET)
        currTrigger.id = "goToPlanet"
        triggerRunScriptAfterDelay(0f) {
            setupPirateBaseAndFleet()
        }

        endTrigger()
    }

    open fun <T : EveryFrameScript?> getScriptsOfClass(clazz: Class<T>): List<T>? {
        return Global.getSector().scripts.filterIsInstance(clazz)
    }

    override fun acceptImpl(dialog: InteractionDialogAPI?, memoryMap: MutableMap<String, MemoryAPI>?) {
        setupTriggersOnAccept()
    }

    fun findAcademy() : SectorEntityToken {
        return if (SectorManager.getManager().isCorvusMode) MiscAcademyFleetCreator.getAcademy() else
            Global.getSector().memoryWithoutUpdate["\$nex_randomSector_galatiaAcademy"] as SectorEntityToken
    }

    protected fun setupPirateBaseAndFleet() {
        pirateBase = PirateBaseIntel(planet!!.starSystem, Factions.PIRATES, PirateBaseIntel.PirateBaseTier.TIER_5_3MODULE)
        if (pirateBase == null) return;

        var orbitRadius = BaseThemeGenerator.getOrbitalRadius(planet)
        pirateBase!!.entity.setCircularOrbitPointingDown(planet, genRandom.nextFloat() * 360, orbitRadius,
            NexUtilsAstro.getOrbitalPeriod(planet, orbitRadius))

        pirateBase!!.market.memoryWithoutUpdate.set("\$nex_remLostScientist_pirateBase", true)

        //makeImportant(pirateBase!!.market, "\$nex_lostScientist_pirateBase", Stage.GO_TO_PLANET)

        var params = FleetParamsV3(pirateBase!!.market, FleetTypes.PATROL_MEDIUM,
            30f, // combat
            0f, 0f, 0f, 0f, 2f,  // freighter, tanker, transport, liner, utility
            0f
        )
        val pirateFleet = FleetFactoryV3.createFleet(params)
        pirateFleet.memoryWithoutUpdate.set(MemFlags.MEMORY_KEY_PIRATE, true)
        pirateFleet.memoryWithoutUpdate.set("\$nex_remLostScientist_piratePatrol", true)
        //pirateFleet.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true)
        planet!!.containingLocation.addEntity(pirateFleet);
        pirateFleet.setCircularOrbit(pirateBase!!.entity, 0f, 100f, 1f)
        pirateFleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, planet!!, 99999f)
    }

    protected fun setupTowering() {

    }

    override fun addDescriptionForNonEndStage(info: TooltipMakerAPI, width: Float, height: Float) {
        val hl = Misc.getHighlightColor()
        val opad = 10f

        if (currentStage == Stage.GO_TO_ACADEMY) {
            var label = info.addPara(RemnantQuestUtils.getString("lostScientist_goToAcademyDesc"), opad, hl, academy!!.name, scientist.nameString)
            label.setHighlightColors(academy!!.faction.baseUIColor, hl)
        } else if (currentStage == Stage.GO_TO_PLANET) {
            var label = info.addPara(RemnantQuestUtils.getString("lostScientist_goToPlanetDesc"), opad, hl,
                planet!!.name, planet!!.containingLocation.nameWithLowercaseTypeShort, scientist.nameString)
            label.setHighlightColors(planet!!.market.textColorForFactionOrPlanet, planet!!.starSystem?.star?.indicatorColor ?: hl, hl)
        } else if (currentStage == Stage.CHECK_OUT_FLEET) {
            info.addPara(RemnantQuestUtils.getString("lostScientist_checkOutFleetDesc"), opad,
                planet!!.starSystem?.star?.indicatorColor ?: hl, planet!!.containingLocation.nameWithLowercaseTypeShort)
        } else if (currentStage == Stage.RETURN_TO_MIDNIGHT) {
            var str = StringHelper.substituteToken(RemnantQuestUtils.getString("lostScientist_returnMidnightDesc"), "\$name", person.nameString)
            info.addPara(str, opad, person.market.textColorForFactionOrPlanet, person.market.name)
        }
    }

    override fun addNextStepText(info: TooltipMakerAPI, tc: Color?, pad: Float): Boolean {
        super.addNextStepText(info, tc, pad)
        val hl = Misc.getHighlightColor()
        //val col: Color = station.getStarSystem().getStar().getSpec().getIconColor()
        //val sysName: String = station.getContainingLocation().getNameWithLowercaseTypeShort()

        //info.addPara("[debug] Current stage: " + currentStage, tc, pad);
        if (currentStage == Stage.GO_TO_ACADEMY) {
            info.addPara(RemnantQuestUtils.getString("lostScientist_goToAcademyNextStep"), pad, tc, academy!!.faction.baseUIColor, academy!!.name)
            return true
        } else if (currentStage == Stage.GO_TO_PLANET) {
            val label = info.addPara(RemnantQuestUtils.getString("lostScientist_goToAcademyNextStep"),
                pad, tc, hl, planet!!.name, planet!!.containingLocation.nameWithNoType)
            label.setHighlightColors(planet!!.market.textColorForFactionOrPlanet, planet!!.starSystem?.star?.indicatorColor ?: hl)
            return true
        } else if (currentStage == Stage.CHECK_OUT_FLEET) {
            info.addPara(RemnantQuestUtils.getString("lostScientist_checkOutBattleNextStep"),
                pad, tc, planet!!.starSystem?.star?.indicatorColor ?: hl, planet!!.name, planet!!.containingLocation.nameWithNoType)
            return true
        } else if (currentStage == Stage.RETURN_TO_MIDNIGHT) {
            info.addPara(getReturnTextShort(person.market), pad, tc, person.market.textColorForFactionOrPlanet, person.market.name)
            return true
        }
        return false
    }

    override fun getBaseName(): String? {
        return RemnantQuestUtils.getString("lostScientist_name")
    }

    class RemnantLostScientistToweringListener : FleetEventListener {
        override fun reportFleetDespawnedToListener(fleet: CampaignFleetAPI?, reason: CampaignEventListener.FleetDespawnReason?, param: Any?)
        {

        }

        override fun reportBattleOccurred(fleet: CampaignFleetAPI?, primaryWinner: CampaignFleetAPI?, battle: BattleAPI?)
        {

        }

    }
}