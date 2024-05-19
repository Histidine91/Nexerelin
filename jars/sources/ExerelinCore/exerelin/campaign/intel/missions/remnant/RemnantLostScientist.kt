package exerelin.campaign.intel.missions.remnant

import com.fs.starfarer.api.EveryFrameScript
import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.ai.CampaignFleetAIAPI.ActionType
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript
import com.fs.starfarer.api.impl.campaign.MilitaryResponseScript.MilitaryResponseParams
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.fleets.misc.MiscAcademyFleetCreator
import com.fs.starfarer.api.impl.campaign.ids.*
import com.fs.starfarer.api.impl.campaign.intel.bases.PirateBaseIntel
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.StarSystemData
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator
import com.fs.starfarer.api.impl.campaign.procgen.themes.SalvageSpecialAssigner
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_DecivEvent
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipCondition
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import exerelin.campaign.SectorManager
import exerelin.campaign.colony.ColonyTargetValuator
import exerelin.campaign.intel.bases.NexPirateBaseIntel
import exerelin.campaign.intel.bases.Nex_PirateBaseManager
import exerelin.campaign.intel.missions.BuildStation.SystemUninhabitedReq
import exerelin.utilities.NexUtilsAstro
import exerelin.utilities.NexUtilsFleet
import exerelin.utilities.StringHelper
import org.apache.log4j.Logger
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.ext.logging.i
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.*


open class RemnantLostScientist : HubMissionWithBarEvent() {

    companion object {
        const val REWARD = 45000
        const val REWARD2 = 45000
        const val MEMORY_KEY_ASKED_ABOUT_LIU = "\$nex_remLostSci_askedAboutLiu"
        const val MEMORY_KEY_TALK_TO_SPACER = "\$nex_remLostSci_wantTalkToSpacer"

        @JvmField
        val log: Logger = Global.getLogger(RemnantLostScientist::class.java)

        @JvmStatic
        fun reverseCompatibilityStatic() {
            val mission : RemnantLostScientist? = Global.getSector().memoryWithoutUpdate.get("\$nex_remLostSci_ref") as RemnantLostScientist?
            mission?.reverseCompatibility()
        }
    }

    enum class Stage {
        GO_TO_ACADEMY, GO_TO_PLANET, RETURN, COMPLETED, FAILED
    }

    var academy: SectorEntityToken? = null
    var planet: PlanetAPI? = null
    var scientist: PersonAPI = RemnantQuestUtils.getOrCreateLostScientist()
    var pirateBase: PirateBaseIntel? = null
    var bribe = MathUtils.getRandomNumberInRange(10, 12) * 1000
    var bribeSmall = 2000
    var wantCheckOutFleet = false
    var toweringFleet: CampaignFleetAPI? = null

    /*
     store in player or global memory:
        want to talk to spacer
        got paper
        returning scientist
        attacked Mauve early
        killed Mauve early
     */

    override fun create(createdAt: MarketAPI?, barEvent: Boolean): Boolean {
        log.info("Starting lost sci mission gen")
        if (!setGlobalReference("\$nex_remLostSci_ref")) {
            return false
        }

        academy = findAcademy()
        if (academy == null) {
            log.info("  No academy found...?")
            return false
        }

        requireSystemNotBlackHole()
        requireSystemNotHasPulsar()
        search.systemReqs.add(SystemUninhabitedReq())
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
        preferSystemWithinRangeOf(academy!!.locationInHyperspace, 15f, 25f)
        requirePlanetConditions(ReqMode.ANY, Conditions.DECIVILIZED)
        requirePlanetUnpopulated()
        preferPlanetConditions(ReqMode.ANY, Conditions.HABITABLE)
        planet = pickPlanet()
        if (planet == null) {
            log.info("  No valid planet found")
            return false
        }

        setRepPersonChangesMedium()
        setRepFactionChangesLow()

        setStartingStage(Stage.GO_TO_ACADEMY)
        setSuccessStage(Stage.COMPLETED)
        setFailureStage(Stage.FAILED)
        setCreditReward(REWARD)

        setStoryMission()

        return true
    }

    fun setupTriggersOnAccept() {
        makeImportant(academy, "\$nex_remLostSci_academy_imp", Stage.GO_TO_ACADEMY)
        makeImportant(planet, "\$nex_remLostSci_planet_imp", Stage.GO_TO_PLANET)
        makeImportant(this.person, "\$nex_remLostSci_return", Stage.RETURN)

        beginStageTrigger(Stage.GO_TO_ACADEMY)
        currTrigger.id = "goToAcademy_stage"
        // set memory values here so they'll be cleared on mission end
        triggerSetMemoryValue(planet, ColonyTargetValuator.MEM_KEY_NO_COLONIZE, false)
        triggerSetMemoryValue(academy, MEMORY_KEY_ASKED_ABOUT_LIU, false)
        triggerSetMemoryValue(academy, MEMORY_KEY_TALK_TO_SPACER, false)
        endTrigger()

        beginStageTrigger(Stage.GO_TO_PLANET)
        currTrigger.id = "goToPlanet_stage"
        triggerSetMemoryValue(planet, Nex_DecivEvent.MEM_KEY_HAS_EVENT, false)
        triggerSetMemoryValue(planet, "\$nex_remLostSci_planetFirstVisit", false)
        endTrigger()

        beginWithinHyperspaceRangeTrigger(planet, 3f, false, Stage.GO_TO_PLANET)
        currTrigger.id = "goToPlanet_approach"
        triggerRunScriptAfterDelay(0f) {
            setupPirateBaseAndFleet()
        }
        endTrigger()

    }

    fun reverseCompatibility() {
        if (Global.getSector().memoryWithoutUpdate.getBoolean(MEMORY_KEY_TALK_TO_SPACER)) {
            Global.getSector().memoryWithoutUpdate.unset(MEMORY_KEY_TALK_TO_SPACER)
            academy?.memoryWithoutUpdate?.set(MEMORY_KEY_TALK_TO_SPACER, true)
        }
        val sebestyen = Global.getSector().importantPeople.getPerson(People.SEBESTYEN) ?: return
        if (sebestyen.memoryWithoutUpdate.getBoolean(MEMORY_KEY_ASKED_ABOUT_LIU)) {
            sebestyen.memoryWithoutUpdate.unset(MEMORY_KEY_ASKED_ABOUT_LIU)
            academy?.memoryWithoutUpdate?.set(MEMORY_KEY_TALK_TO_SPACER, true)
        }
    }

    open fun <T : EveryFrameScript?> getScriptsOfClass(clazz: Class<T>): List<T>? {
        return Global.getSector().scripts.filterIsInstance(clazz)
    }

    override fun acceptImpl(dialog: InteractionDialogAPI?, memoryMap: MutableMap<String, MemoryAPI>?) {
        setupTriggersOnAccept()
    }

    fun findAcademy(): SectorEntityToken {
        return if (SectorManager.getManager().isCorvusMode) MiscAcademyFleetCreator.getAcademy() else
            Global.getSector().memoryWithoutUpdate["\$nex_randomSector_galatiaAcademy"] as SectorEntityToken
    }

    protected fun setupPirateBaseAndFleet() {
        // first see if there's already a pirate base around the planet that we can use
        for (info in Global.getSector().intelManager.getIntel(PirateBaseIntel::class.java)) {
            val pbi = info as PirateBaseIntel
            if (pbi.isEnding || pbi.isEnded) continue;
            if (pbi.entity?.orbit?.focus == planet) {
                pirateBase = pbi
                break
            }
        }

        if (pirateBase == null) {
            pirateBase =
                NexPirateBaseIntel(planet!!.starSystem, Factions.PIRATES, PirateBaseIntel.PirateBaseTier.TIER_3_2MODULE)
            Nex_PirateBaseManager.getInstance().addActive(pirateBase)
        }

        if (pirateBase == null) return;

        var orbitRadius = BaseThemeGenerator.getOrbitalRadius(planet)
        pirateBase!!.entity.setCircularOrbitPointingDown(
            planet, genRandom.nextFloat() * 360, orbitRadius,
            NexUtilsAstro.getOrbitalPeriod(planet, orbitRadius)
        )

        pirateBase!!.market.memoryWithoutUpdate.set("\$nex_remLostSci_pirateBase", true)

        //makeImportant(pirateBase!!.market, "\$nex_lostSci_pirateBase", Stage.GO_TO_PLANET)

        var params = FleetParamsV3(
            pirateBase!!.market, FleetTypes.PATROL_MEDIUM,
            35f, // combat
            0f, 0f, 0f, 0f, 2f,  // freighter, tanker, transport, liner, utility
            0f
        )
        params.ignoreMarketFleetSizeMult = true
        val pirateFleet = FleetFactoryV3.createFleet(params)
        pirateFleet.memoryWithoutUpdate.set(MemFlags.MEMORY_KEY_PIRATE, true)
        pirateFleet.memoryWithoutUpdate.set("\$nex_remLostSci_piratePatrol", true)
        Misc.setFlagWithReason(pirateFleet.memoryWithoutUpdate, MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
            "nex_remLostSci_dontFear",true, -1f
        )
        /*
        Misc.setFlagWithReason(pirateFleet.memoryWithoutUpdate, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE,
            "nex_remLostSci_dontFear",true, -1f
        )
        Misc.setFlagWithReason(pirateFleet.memoryWithoutUpdate, MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE,
            "nex_remLostSci_dontFear",true, -1f
        )
        */
        planet!!.containingLocation.addEntity(pirateFleet);
        pirateFleet.setCircularOrbit(pirateBase!!.entity, 0f, 100f, 1f)
        pirateFleet.addAssignment(FleetAssignment.ORBIT_PASSIVE, pirateBase!!.entity, 99999f)
    }

    protected fun setupTowering() {
        val fp = 80f;
        val system = planet!!.starSystem
        var params = FleetParamsV3(
            planet!!.locationInHyperspace, Factions.REMNANTS, 1.2f, FleetTypes.TASK_FORCE,
            fp, // combat
            0f, 0f, 0f, 0f, 0f,  // freighter, tanker, transport, liner, utility
            0f
        )
        params.aiCores = OfficerQuality.AI_BETA_OR_GAMMA
        params.averageSMods = 1
        params.commander = RemnantQuestUtils.getOrCreateTowering()
        var fleet = FleetFactoryV3.createFleet(params)
        toweringFleet = fleet

        fleet.memoryWithoutUpdate["\$genericHail"] = true
        fleet.memoryWithoutUpdate["\$genericHail_openComms"] = "Nex_RemLostSciToweringHail"
        fleet.memoryWithoutUpdate["\$clearCommands_no_remove"] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORES_OTHER_FLEETS] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_DO_NOT_IGNORE_PLAYER] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS] = true
        Misc.setFlagWithReason(
            fleet.memoryWithoutUpdate,
            MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
            "nex_remLostSci_towering",
            true,
            -1f
        )
        makeImportant(fleet, "\$nex_remLostSci_towering", Stage.RETURN)

        var flagship = fleet.flagship
        flagship.isFlagship = false

        flagship = fleet.fleetData.addFleetMember("radiant_Assault")
        var commander = RemnantQuestUtils.getOrCreateTowering()
        fleet.fleetData.setFlagship(flagship)
        flagship.captain = commander
        flagship.repairTracker.cr = flagship.repairTracker.maxCR
        fleet.commander = commander
        NexUtilsFleet.setClonedVariant(flagship, true)
        flagship.variant.addTag(Tags.VARIANT_DO_NOT_DROP_AI_CORE_FROM_CAPTAIN)
        fleet.fleetData.sort()
        fleet.forceSync()

        val player = Global.getSector().playerFleet
        val dist = player.getMaxSensorRangeToDetect(fleet)
        dist.coerceAtLeast(1000f)
        dist.coerceAtMost(3000f)
        system.addEntity(fleet)

        var pos: Vector2f
        var tries = 0
        do {
            pos = MathUtils.getPointOnCircumference(player.location, dist, genRandom.nextFloat() * 360)
            tries++
        } while (isNearCorona(system, pos) && tries < 15)

        fleet.setLocation(pos.x, pos.y)

        val token = system.createToken(fleet.location)
        fleet.addAssignment(FleetAssignment.HOLD, token, 999999f)
        fleet.setCircularOrbit(token, 0f, 1f, 99999f)
        fleet.abilities[Abilities.GO_DARK]?.activate();

        fleet.addEventListener(RemnantLostScientistToweringListener(this))

        // debris
        // should also act as a destination market for player, test this
        val loc = LocData(token, false);
        val debris = spawnDebrisField(DEBRIS_MEDIUM, DEBRIS_AVERAGE, loc)
        if (wantCheckOutFleet) makeImportant(debris, "\$nex_remLostSci_investigate", Stage.RETURN)
        //debris.isDiscoverable = false
        debris.sensorProfile = 5000f;

        // dead ships
        spawnShipGraveyard(Factions.LIONS_GUARD, 2, 3, loc);
    }

    protected fun militaryResponse() {
        val market = pirateBase?.market ?: return
        val params = MilitaryResponseParams(
            ActionType.HOSTILE,
            "nex_remLostSci_angerStationKing",
            market.getFaction(),
            market.getPrimaryEntity(),
            0.75f,
            30f
        )
        market.containingLocation.addScript(MilitaryResponseScript(params))

        var fleets: List<CampaignFleetAPI> = market.containingLocation.fleets
        for (other: CampaignFleetAPI? in fleets) {
            if (other?.faction == market.faction) {
                val mem: MemoryAPI = other!!.memoryWithoutUpdate
                Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_HOSTILE_WHILE_TOFF, "raidAlarm", true, 10f)
                Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE, "raidAlarm", true, 10f)
                Misc.setFlagWithReason(mem, MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY, "raidAlarm", true, 10f)
                Misc.setFlagWithReason(
                    other.memoryWithoutUpdate,
                    MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
                    "nex_remLostSci_dontFear",
                    false,
                    -1f
                )
                Misc.setFlagWithReason(
                    other.memoryWithoutUpdate,
                    MemFlags.MEMORY_KEY_MAKE_ALLOW_DISENGAGE,
                    "nex_remLostSci_dontFear",
                    false,
                    -1f
                )
                //other.memoryWithoutUpdate.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS);
                //other.addAssignmentAtStart(FleetAssignment.INTERCEPT, Global.getSector().playerFleet, 10f, null, null)
            }
        }
        //log.info("Mil response triggered")
    }

    protected fun generateSpacer(): PersonAPI {
        return OfficerManagerEvent.createOfficer(
            Global.getSector().getFaction(Factions.INDEPENDENT),
            2,
            OfficerManagerEvent.SkillPickPreference.ANY,
            genRandom
        )
    }

    protected fun sendToweringHome() {
        if (toweringFleet != null) {
            RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(toweringFleet, true);
            toweringFleet!!.abilities[Abilities.GO_DARK]?.deactivate();
        }
    }

    override fun notifyEnding() {
        sendToweringHome()
    }

    override fun updateInteractionDataImpl() {
        //set("\$nex_remLostSci_ref", this)
        set("\$nex_remLostSci_scientist", scientist)
        set("\$nex_remLostSci_planet", planet!!.name)
        set("\$nex_remLostSci_system", planet!!.starSystem.nameWithLowercaseTypeShort)
        set("\$nex_remLostSci_isBaseAlive", pirateBase?.entity?.isAlive)
        set("\$nex_remLostSci_stage", getCurrentStage())
        set("\$nex_remLostSci_bribe", bribe)
        set("\$nex_remLostSci_bribeSmall", bribeSmall)
        set("\$nex_remLostSci_bribeStr", Misc.getWithDGS(bribe.toFloat()))
        set("\$nex_remLostSci_bribeSmallStr", Misc.getWithDGS(bribeSmall.toFloat()))
        set("\$nex_remLostSci_hasSKDeal", pirateBase?.playerHasDealWithBaseCommander())
        set(
            "\$nex_remLostSci_playerDP",
            Global.getSector().playerFleet.fleetData.membersListCopy.filter { !it.isCivilian }
                .sumOf { it -> it.deploymentPointsCost.toDouble() }.toInt()
        )
        set("\$nex_remLostSci_reward1", REWARD)
        set("\$nex_remLostSci_reward1Str", Misc.getDGSCredits(REWARD.toFloat()))
        set("\$nex_remLostSci_reward2", REWARD2)
        set("\$nex_remLostSci_reward2Str", Misc.getDGSCredits(REWARD2.toFloat()))
        set("\$nex_remLostSci_rewardTotal", REWARD + REWARD2)
    }

    override fun callAction(
        action: String,
        ruleId: String,
        dialog: InteractionDialogAPI,
        params: List<Misc.Token>,
        memoryMap: Map<String, MemoryAPI>
    ): Boolean {
        when (action) {
            "beginSpacerConvo" -> {
                val person: PersonAPI = generateSpacer()
                dialog.interactionTarget.activePerson = person
                (dialog.plugin as RuleBasedDialog).notifyActivePersonChanged()
                dialog.visualPanel.showPersonInfo(person, true, false)

                return true
            }
            "beginPlanetStage" -> {
                setCurrentStage(Stage.GO_TO_PLANET, dialog, memoryMap)
                return true
            }
            "addMilitaryResponse" -> {
                militaryResponse()
                return true
            }
            "sendToweringHome" -> {
                sendToweringHome()
                return true
            }
            "completeMission" -> {
                setCurrentStage(Stage.COMPLETED, dialog, memoryMap)
                Global.getSector().memoryWithoutUpdate["\$nex_remLostSci_missionCompleted"] = true
                return true
            }
            "investigateFleet" -> {
                this.wantCheckOutFleet = true
                setupTowering()
                setCurrentStage(Stage.RETURN, dialog, memoryMap)
                return true
            }
            "noInvestigateFleet" -> {
                this.wantCheckOutFleet = false
                setupTowering()
                setCurrentStage(Stage.RETURN, dialog, memoryMap)
                return true
            }
            "setReward1" -> {
                setCreditReward(REWARD)
                setRepPersonChangesMedium()
                setRepFactionChangesLow()
                return true
            }
            "setReward2" -> {
                setCreditReward(REWARD + REWARD2)
                setRepPersonChangesHigh()
                setRepFactionChangesMedium()
                return true
            }
            "cancelInvestigate" -> {
                this.wantCheckOutFleet = false
                sendToweringHome()
                return true
            }
        }
        return false
    }

    override fun addDescriptionForNonEndStage(info: TooltipMakerAPI, width: Float, height: Float) {
        val hl = Misc.getHighlightColor()
        val opad = 10f

        if (currentStage == Stage.GO_TO_ACADEMY) {
            var label = info.addPara(
                RemnantQuestUtils.getString("lostScientist_goToAcademyDesc"),
                opad,
                hl,
                academy!!.name,
                scientist.nameString
            )
            label.setHighlightColors(academy!!.faction.baseUIColor, hl)
        } else if (currentStage == Stage.GO_TO_PLANET) {
            var label = info.addPara(
                RemnantQuestUtils.getString("lostScientist_goToPlanetDesc"), opad, hl,
                planet!!.name, planet!!.containingLocation.nameWithLowercaseTypeShort, scientist.nameString
            )
            label.setHighlightColors(
                planet!!.market.textColorForFactionOrPlanet,
                planet!!.starSystem?.star?.lightColor ?: hl,
                hl
            )
        } else if (currentStage == Stage.RETURN) {
            if (this.wantCheckOutFleet) {
                info.addPara(
                    RemnantQuestUtils.getString("lostScientist_checkFleetDesc"),
                    opad,
                    planet!!.starSystem?.star?.lightColor ?: hl,
                    planet!!.containingLocation.nameWithLowercaseTypeShort
                )
            } else {
                var str = StringHelper.substituteToken(
                    RemnantQuestUtils.getString("lostScientist_returnMidnightDesc"),
                    "\$name",
                    person.nameString
                )
                info.addPara(str, opad, person.market.textColorForFactionOrPlanet, person.market.name)
            }
        }
    }

    override fun addNextStepText(info: TooltipMakerAPI, tc: Color?, pad: Float): Boolean {
        super.addNextStepText(info, tc, pad)
        val hl = Misc.getHighlightColor()
        //val col: Color = station.getStarSystem().getStar().getSpec().getIconColor()
        //val sysName: String = station.getContainingLocation().getNameWithLowercaseTypeShort()

        //info.addPara("[debug] Current stage: " + currentStage, tc, pad);
        when (currentStage) {
            Stage.GO_TO_ACADEMY -> {
                info.addPara(
                    RemnantQuestUtils.getString("lostScientist_goToAcademyNextStep"),
                    pad,
                    tc,
                    academy!!.faction.baseUIColor,
                    academy!!.name
                )
                return true
            }
            Stage.GO_TO_PLANET -> {
                val label = info.addPara(
                    RemnantQuestUtils.getString("lostScientist_goToPlanetNextStep"),
                    pad, tc, hl, planet!!.name, planet!!.containingLocation.nameWithNoType
                )
                label.setHighlightColors(
                    planet!!.market.textColorForFactionOrPlanet,
                    planet!!.starSystem?.star?.lightColor ?: hl
                )
                return true
            }
            Stage.RETURN -> {
                if (this.wantCheckOutFleet) {
                    info.addPara(
                        RemnantQuestUtils.getString("lostScientist_checkFleetNextStep"),
                        pad, tc, planet!!.starSystem?.star?.lightColor ?: hl, planet!!.containingLocation.nameWithNoType
                    )
                } else {
                    info.addPara(
                        getReturnTextShort(person.market),
                        pad,
                        tc,
                        person.market.textColorForFactionOrPlanet,
                        person.market.name
                    )
                }
                return true
            }
            else -> return false
        }
    }

    override fun getBaseName(): String? {
        return RemnantQuestUtils.getString("lostScientist_name")
    }

    class RemnantLostScientistToweringListener(var mission: RemnantLostScientist) : FleetEventListener {

        override fun reportFleetDespawnedToListener(
            fleet: CampaignFleetAPI?,
            reason: CampaignEventListener.FleetDespawnReason?,
            param: Any?
        ) {

        }

        override fun reportBattleOccurred(
            fleet: CampaignFleetAPI?,
            primaryWinner: CampaignFleetAPI?,
            battle: BattleAPI?
        ) {
            if (battle?.isPlayerInvolved == true && battle?.playerSide != battle?.getSideFor(fleet)) {
                mission.setCurrentStage(Stage.FAILED, Global.getSector().campaignUI.currentInteractionDialog, null)
            }
        }

    }
}
