package exerelin.campaign.intel.missions.remnant

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.Script
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickMode
import com.fs.starfarer.api.campaign.FactionAPI.ShipPickParams
import com.fs.starfarer.api.campaign.JumpPointAPI.JumpDestination
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.BattleCreationContext
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictType
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.*
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.fleets.AutoDespawnScript
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.*
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel
import com.fs.starfarer.api.impl.campaign.intel.deciv.DecivTracker
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch.MarketRequirement
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode
import com.fs.starfarer.api.impl.campaign.plog.PlaythroughLog
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator.*
import com.fs.starfarer.api.impl.campaign.procgen.themes.MiscellaneousThemeGenerator
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.BaseSalvageSpecial
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.ShipRecoverySpecialData
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.DelayedActionScript
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import com.fs.starfarer.loading.specs.PlanetSpec
import exerelin.campaign.DiplomacyManager
import exerelin.campaign.PlayerFactionStore
import exerelin.campaign.SectorManager
import exerelin.campaign.battle.NexBattleAutoresolverPlugin
import exerelin.campaign.graphics.BombardmentAnimationV2
import exerelin.campaign.intel.merc.MercContractIntel
import exerelin.campaign.intel.missions.BuildStation.SystemUninhabitedReq
import exerelin.plugins.ExerelinCampaignPlugin
import exerelin.utilities.*
import lombok.Getter
import org.apache.log4j.Logger
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.VectorUtils
import java.awt.Color
import kotlin.math.abs


open class RemnantSalvation : HubMissionWithBarEvent(), FleetEventListener {
    enum class Stage {
        GO_TO_BASE, INVESTIGATE_LEADS, RETURN_TO_MIDNIGHT, DEFEND_PLANET, EPILOGUE, COMPLETED, FAILED, BAD_END
    }

    @Getter protected var base: MarketAPI? = null
    @Getter protected var remnantSystem: StarSystemAPI? = null
    @Getter protected var target: MarketAPI? = null
    @Getter protected var arroyoMarket: MarketAPI? = null
    @Getter protected var fleet1: CampaignFleetAPI? = null
    @Getter protected var fleet2: CampaignFleetAPI? = null
    @Getter protected var knightFleet: CampaignFleetAPI? = null

    @Getter protected var defeatedFleet1 = false
    @Getter protected var defeatedFleet2 = false
    protected var talkedToArroyo = false
    protected var talkedToTowering1 = false
    @Getter protected var targetPKed = false
    protected var hiredEndbringer = false

    @Deprecated("Unused") protected var warpFleet2Delay = 0.2f;
    @Getter protected var timerToPK : Float = 30f;

    companion object {
        @JvmField var SALVATION_ENABLED = true;
        @JvmField var DEBUG_MODE = false;
        const val STAT_MOD_ID = "nex_remSalvation_mod";

        @JvmField val log : Logger = Global.getLogger(RemnantSalvation::class.java)
        // runcode exerelin.campaign.intel.missions.remnant.RemnantSalvation.Companion.devAddTriggers()
        @JvmStatic fun devAddTriggers() {
            var mission = Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_ref"] as RemnantSalvation
            val argent = RemnantQuestUtils.getOrCreateArgent();
            argent.setPortraitSprite(Global.getSettings().getSpriteName("characters", "nex_argent"));
        }

        // runcode exerelin.campaign.intel.missions.remnant.RemnantSalvation.Companion.pk()
        @JvmStatic fun pk() {
            var mission = Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_ref"] as RemnantSalvation
            mission.deployPK()
        }

        // runcode exerelin.campaign.intel.missions.remnant.RemnantSalvation.Companion.killToweringFlagship()
        @JvmStatic fun killToweringFlagship() {
            var mission = Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_ref"] as RemnantSalvation
            mission.fleet2?.removeFleetMemberWithDestructionFlash(mission.fleet2?.flagship)
            mission.reportBattleOccurred(mission.fleet2, null, null)
        }
    }

    override fun create(createdAt: MarketAPI, barEvent: Boolean): Boolean {
        if (!SALVATION_ENABLED) {
            //log.info("Blocking Salvation")
            return false
        }

        if (!setGlobalReference("\$nex_remSalvation_ref")) {
            return false
        }
        val madeira = Global.getSector().economy.getMarket("madeira")
        base = if (madeira != null && madeira.factionId == Factions.PERSEAN) madeira else {
            requireMarketFaction(Factions.PERSEAN)
            requireMarketNotHidden()
            requireMarketNotInHyperspace()
            preferMarketSizeAtLeast(5)
            preferMarketSizeAtMost(6)
            preferMarketIsMilitary()
            pickMarket()
        }
        if (base == null) return false

        arroyoMarket = Global.getSector().importantPeople.getPerson(People.ARROYO)?.market;
        if (arroyoMarket == null) {
            requireMarketFaction(Factions.TRITACHYON)
            requireMarketNotHidden()
            requireMarketNotInHyperspace()
            preferMarketSizeAtLeast(5)
            arroyoMarket = pickMarket()
        }
        if (arroyoMarket == null) return false;

        val gilead = Global.getSector().economy.getMarket("gilead")
        target = if (gilead != null && NexUtilsFaction.isLuddicFaction(gilead.factionId)) gilead else {
            requireMarketFaction(Factions.LUDDIC_CHURCH)
            requireMarketNotHidden()
            requireMarketNotInHyperspace()
            requireMarketSizeAtLeast(6)
            preferMarketSizeAtLeast(7)
            requireMarketConditions(ReqMode.ALL, Conditions.HABITABLE)
            preferMarketConditions(ReqMode.ANY, Conditions.MILD_CLIMATE, Conditions.FARMLAND_ADEQUATE, Conditions.FARMLAND_RICH, Conditions.FARMLAND_BOUNTIFUL)
            // prefer non-military
            search.marketPrefs.add(MarketRequirement { market -> !Misc.isMilitary(market) })
            pickMarket()
        }
        if (target == null) return false

        // pick Remnant location
        requireSystemNotHasPulsar()
        requireSystemNotBlackHole()
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
        preferSystemTags(ReqMode.ANY, Tags.THEME_REMNANT, Tags.THEME_DERELICT, Tags.THEME_INTERESTING)
        requireSystemWithinRangeOf(base!!.locationInHyperspace, 15f)
        search.systemReqs.add(SystemUninhabitedReq())
        preferSystemOutsideRangeOf(base!!.locationInHyperspace, 7f)
        preferSystemUnexplored()
        remnantSystem = pickSystem()
        if (remnantSystem == null) return false
        setStartingStage(Stage.GO_TO_BASE)
        addSuccessStages(Stage.COMPLETED)
        addFailureStages(Stage.FAILED, Stage.BAD_END)
        setStoryMission()
        setupTriggers()

        //makeImportant(station, "$nex_remBrawl_target", RemnantBrawl.Stage.GO_TO_TARGET_SYSTEM, RemnantBrawl.Stage.BATTLE, RemnantBrawl.Stage.BATTLE_DEFECTED);
        setRepPersonChangesVeryHigh()
        setRepFactionChangesHigh()
        setCreditReward(CreditReward.VERY_HIGH)
        setCreditReward(creditReward * 5)
        return true
    }

    protected fun setupTriggers() {
        makeImportant(base, "\$nex_remSalvation_base_imp", Stage.GO_TO_BASE)
        makeImportant(remnantSystem!!.hyperspaceAnchor, "\$nex_remSalvation_remnantSystem_imp", Stage.INVESTIGATE_LEADS)
        makeImportant(person, "\$nex_remSalvation_returnStage_imp", Stage.RETURN_TO_MIDNIGHT)
        makeImportant(target, "\$nex_remSalvation_target_imp", Stage.DEFEND_PLANET)
        makeImportant(RemnantQuestUtils.getOrCreateArgent(), "\$nex_remSalvation_epilogue", Stage.EPILOGUE)

        // just sets up a memory value we'll use later
        beginStageTrigger(Stage.GO_TO_BASE)
        currTrigger.id = "beginBaseStage"
        triggerSetMemoryValue(base, "\$nex_remSalvation_seenBaseIntro", false)
        endTrigger()

        // Approach base: add some wreckage to the attacked base and disrupt its industries
        beginWithinHyperspaceRangeTrigger(base, 2f, false, Stage.GO_TO_BASE)
        currTrigger.id = "approachBase"
        var loc = LocData(base!!.primaryEntity, true)
        triggerSpawnDebrisField(DEBRIS_MEDIUM, DEBRIS_DENSE, loc)
        loc = LocData(base!!.primaryEntity, true)
        triggerSpawnShipGraveyard(Factions.REMNANTS, 2, 2, loc)
        loc = LocData(base!!.primaryEntity, true)
        triggerSpawnShipGraveyard(Factions.PERSEAN, 4, 5, loc)
        triggerRunScriptAfterDelay(0.01f, GenericMissionScript(this, "trashBase"))
        endTrigger()

        // Approach Remnant system: add a salvagable station and the first fleet
        beginWithinHyperspaceRangeTrigger(remnantSystem, 2f, false, Stage.INVESTIGATE_LEADS)
        currTrigger.id = "approachRemnantSystem"
        triggerRunScriptAfterDelay(0.01f, GenericMissionScript(this, "setupFleet1"))
        endTrigger()

        // Approach target planet: add the Knight fleet
        beginWithinHyperspaceRangeTrigger(target, 2f, false, Stage.DEFEND_PLANET)
        currTrigger.id = "approachTarget"
        triggerRunScriptAfterDelay(0.01f, GenericMissionScript(this, "setupKnightFleet"))
        endTrigger()
    }

    // adds the ship type so it doesn't crash (and because we do want ship type)
    override fun spawnShipGraveyard(factionId: String?, minShips: Int, maxShips: Int, data: LocData) {
        val sizes = listOf(DerelictType.SMALL, DerelictType.MEDIUM, DerelictType.SMALL, DerelictType.LARGE)

        val focus = spawnEntityToken(data)
        val numShips = minShips + genRandom.nextInt(maxShips - minShips + 1)
        val bands = WeightedRandomPicker<Float>(genRandom)
        for (i in 0 until numShips + 5) {
            bands.add(120f + i * 20f, (i + 1f) * (i + 1f))
        }
        for (i in 0 until numShips) {
            val type = sizes[i%sizes.size]
            val r = bands.pickAndRemove()
            val loc = EntityLocation()
            loc.type = LocationType.OUTER_SYSTEM
            val orbitDays = r / (5f + genRandom.nextFloat() * 10f)
            loc.orbit = Global.getFactory().createCircularOrbit(focus, genRandom.nextFloat() * 360f, r, orbitDays)
            val curr = LocData(loc, data.system, data.removeOnMissionOver)
            spawnDerelict(factionId, type, curr)
        }
    }

    protected fun addAdditionalTriggersDev() {

    }

    protected fun checkPK() {
        if (fleet2 != null) {
            // if not in actual contact with the target, try again
            if (MathUtils.getDistance(fleet2, target!!.primaryEntity) > 100)
                return

            // engage any nearby fleets before attempting PK
            for (otherFleet in target!!.containingLocation.fleets) {
                if (otherFleet === fleet2) continue;
                if (!otherFleet.isHostileTo(fleet2)) continue;
                if (MathUtils.getDistance(otherFleet, target!!.primaryEntity) > 350f) continue;

                // engage station if there's one
                // as for patrols, fuck 'em, if they can't catch us we get a free PK
                if (otherFleet === Misc.getStationFleet(target) || otherFleet === knightFleet) {
                    Global.getFactory().createBattle(fleet2, otherFleet)
                    return
                }
            }
        }

        deployPK()
    }

    protected fun convertPlanet(planet : PlanetAPI, newType : String) {
        val allSpecs: Iterator<*> = Global.getSettings().allPlanetSpecs.iterator()
        var myspec = planet.spec
        while (allSpecs.hasNext()) {
            val spec = allSpecs.next() as PlanetSpecAPI
            if (spec.planetType == newType) {
                myspec.atmosphereColor = spec.atmosphereColor
                myspec.atmosphereThickness = spec.atmosphereThickness
                myspec.atmosphereThicknessMin = spec.atmosphereThicknessMin
                myspec.cloudColor = spec.cloudColor
                myspec.cloudRotation = spec.cloudRotation
                myspec.cloudTexture = spec.cloudTexture
                myspec.glowColor = spec.glowColor
                myspec.glowTexture = spec.glowTexture
                myspec.iconColor = spec.iconColor
                myspec.planetColor = spec.planetColor
                myspec.starscapeIcon = spec.starscapeIcon
                myspec.texture = spec.texture
                myspec.isUseReverseLightForGlow = spec.isUseReverseLightForGlow
                (myspec as PlanetSpec).planetType = newType
                myspec.name = spec.name
                myspec.descriptionId = (spec as PlanetSpec).descriptionId
                break
            }
        }
        planet.applySpecChanges()
        val market = planet.market;
        market.removeCondition(Conditions.MILD_CLIMATE)
        market.removeCondition(Conditions.FARMLAND_POOR)
        market.removeCondition(Conditions.FARMLAND_ADEQUATE)
        market.removeCondition(Conditions.FARMLAND_RICH)
        market.removeCondition(Conditions.FARMLAND_BOUNTIFUL)
        market.removeCondition(Conditions.WATER_SURFACE)
        market.addCondition(Conditions.HOT)
        market.addCondition(Conditions.EXTREME_WEATHER)
        market.addCondition(Conditions.DECIVILIZED)
        // keep the pre-existing ruins
        market.removeCondition(Conditions.RUINS_VAST)
        market.removeCondition(Conditions.RUINS_EXTENSIVE)
        market.addCondition(Conditions.RUINS_WIDESPREAD)
    }

    protected fun deployPK() {
        if (targetPKed) return; // already done the deed

        val planet = target!!.planetEntity
        BombardmentAnimationV2.addBombardVisual(planet, 6f, Color(255, 128, 96, 255))
        DecivTracker.decivilize(target, true, true)
        target!!.addCondition(Conditions.POLLUTION)

        targetPKed = true

        // relationship effects
        lowerRep(Factions.LUDDIC_CHURCH, Factions.PERSEAN, false)
        lowerRep(Factions.LUDDIC_CHURCH, Factions.TRITACHYON, true)
        lowerRep(Factions.HEGEMONY, Factions.PERSEAN, false)
        lowerRep(Factions.HEGEMONY, Factions.TRITACHYON, false)

        fleet2?.memoryWithoutUpdate?.unset("\$genericHail")
        knightFleet?.memoryWithoutUpdate?.unset("\$genericHail")

        Global.getSector().addScript(object: DelayedActionScript(0.1f) {
            override fun doAction() {
                convertPlanet(planet, "barren-desert")
                planet.customDescriptionId = "nex_planet_gilead_pked"
            }
        })

        makeUnimportant(target, Stage.DEFEND_PLANET)
        sendUpdateIfPlayerHasIntel(null, false)

        if (fleet2 != null) {
            RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(fleet2, true)
            fleet2?.addScript(AutoDespawnScript(fleet2));
        }

        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_endTimestamp"] = Global.getSector().clock.timestamp
    }

    /**
     * Lowers relations between the two factions, creating a Declare War diplomacy event if needed.
     */
    protected fun lowerRep(factionId1 : String, factionId2 : String, severe: Boolean) {
        // TODO: maybe set relationship cap between Church and TT? Meh probably not needed, don't they already have a cap
        var faction1 = Global.getSector().getFaction(factionId1)
        var faction2 = Global.getSector().getFaction(factionId2)
        var loss = if (severe) .6f else .3f
        var curr = faction1.getRelationship(factionId2)
        var isHostile = faction1.isHostileTo(factionId2)
        if (!isHostile && curr - loss < -RepLevel.HOSTILE.min) {
            // declare war event
            DiplomacyManager.createDiplomacyEventV2(
                faction1, faction2,
                "declare_war", null
            )
        } else {
            // apply rep ding and show in campaign UI
            var atBest = if (severe) RepLevel.INHOSPITABLE else RepLevel.NEUTRAL
            var repResult = DiplomacyManager.adjustRelations(faction1, faction2, -loss, atBest, null, null)

            val relation = faction1.getRelationship(factionId2)
            val relationStr = NexUtilsReputation.getRelationStr(relation)
            val relColor = NexUtilsReputation.getRelColor(relation)
            val delta = abs(repResult.delta * 100).toInt()
            var str = StringHelper.getString("exerelin_diplomacy", "intelRepResultNegative")
            str = StringHelper.substituteToken(str, "\$faction1", faction1.displayName)
            str = StringHelper.substituteToken(str, "\$faction2", faction2.displayName)
            str = StringHelper.substituteToken(str, "\$deltaAbs", "" + delta)
            str = StringHelper.substituteToken(str, "\$newRelationStr", relationStr)
            val nhl = Misc.getNegativeHighlightColor()
            Global.getSector().campaignUI.addMessage(str, Misc.getTextColor(), "" + delta, relationStr, nhl, relColor)
        }
    }

    /**
     * Generates the station that Towering's first fleet will orbit.
     */
    protected fun setupFleet1Station() : SectorEntityToken {
        //log.info("Running station setup")
        val loc = this.generateLocation(null, EntityLocationType.ORBITING_PLANET_OR_STAR, null, remnantSystem)
        val stationDefId = when {
            Global.getSettings().modManager.isModEnabled("IndEvo") -> "IndEvo_arsenalStation"
            //Global.getSettings().modManager.isModEnabled("assortment_of_things") -> "rat_refurbishment_station"   // can't be salvaged without special tagging
            else -> "station_mining_remnant"
        }

        var station = remnantSystem!!.addCustomEntity("nex_remSalvation_fleet1_station", null, stationDefId, Factions.REMNANTS)
        station.isDiscoverable = true
        station.orbit = loc?.orbit?.makeCopy()
        station.setSensorProfile(2500f)
        //log.info(String.format("Wololo, orbit period %s, target %s", station.orbit.orbitalPeriod, station.orbit.focus.name))
        return station
    }

    /**
     * Generates Towering's first fleet.
     */
    protected fun setupFleet1(toOrbit : SectorEntityToken): CampaignFleetAPI {
        val playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().playerFleet).toFloat()
        val capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus())
        var fp = playerStr/4 * 0.8f + capBonus - 40
        log.info(String.format("Estimating needed FP at %.1f based on player strength %.1f, capBonus %s", fp, playerStr, capBonus))
        fp = fp.coerceAtMost(350f)
        fp = fp.coerceAtLeast(10f)

        var params = FleetParamsV3(remnantSystem!!.location, Factions.REMNANTS, 1.2f, FleetTypes.TASK_FORCE,
                fp, // combat
                0f, 0f, 0f, 0f, 0f,  // freighter, tanker, transport, liner, utility
                0f
        )
        params.aiCores = OfficerQuality.AI_BETA_OR_GAMMA
        params.averageSMods = 1
        //params.commander = RemnantQuestUtils.getOrCreateTowering()
        //params.flagshipVariantId = "nex_silverlight_restrained_Standard"
        var fleet = FleetFactoryV3.createFleet(params)
        fleet1 = fleet

        fleet.memoryWithoutUpdate.set(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS, true)
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_PURSUE_PLAYER] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_HOSTILE] = true
        //fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_LOW_REP_IMPACT] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_NO_REP_IMPACT] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN] = EnemyFIDConfigGen()
        fleet.memoryWithoutUpdate["\$nex_remSalvation_fleet1"] = true
        fleet.memoryWithoutUpdate.set(NexBattleAutoresolverPlugin.MEM_KEY_STRENGTH_MULT, 1.1f);

        fleet.inflateIfNeeded()

        var flagship = fleet.flagship
        flagship.isFlagship = false

        flagship = fleet.fleetData.addFleetMember("nex_silverlight_restrained_Standard")
        var commander = RemnantQuestUtils.getOrCreateTowering()
        fleet.fleetData.setFlagship(flagship)
        flagship.setCaptain(commander)
        flagship.repairTracker.cr = flagship.repairTracker.maxCR
        setupSpecialVariant(flagship, false)
        flagship.shipName = RemnantQuestUtils.getString("salvation_shipName1")
        fleet.commander = commander

        fleet.fleetData.sort()
        fleet.fleetData.setSyncNeeded()
        fleet.fleetData.syncIfNeeded()

        makeImportant(fleet, "\$nex_remSalvation_fleet1_imp", Stage.INVESTIGATE_LEADS)
        //Misc.addDefeatTrigger(fleet, "Nex_RemSalvation_Fleet1Defeated")
        fleet.addEventListener(this)

        remnantSystem!!.addEntity(fleet)
        fleet.setLocation(toOrbit.location.x, toOrbit.location.y)
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, toOrbit, 99999f)

        return fleet
    }

    /**
     * Generates Towering's second fleet.
     */
    protected fun setupFleet2(): CampaignFleetAPI? {
        if (fleet2 != null) return null

        RemnantQuestUtils.enhanceTowering()

        val playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().playerFleet, false).toFloat()
        val capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus())
        var fp = (playerStr / 4 * 0.55f)
        fp += capBonus - 50
        //if (hiredEndbringer) fp -= 20;  // don't scale to Endbringer's Facet
        fp = fp.coerceAtMost(300f)
        fp += 120f

        var params = FleetParamsV3(target!!.locationInHyperspace, Factions.REMNANTS, 1.5f, FleetTypes.TASK_FORCE,
            fp, // combat
            0f, 0f, 0f, 0f, 0f,  // freighter, tanker, transport, liner, utility
            0f
        )
        params.aiCores = OfficerQuality.AI_MIXED
        params.averageSMods = 2
        //params.commander = RemnantQuestUtils.getOrCreateTowering()
        //params.flagshipVariantId = "nex_silverlight_Ascendant"
        var fleet = FleetFactoryV3.createFleet(params)
        fleet2 = fleet

        // add two Facets
        //genRandom = Misc.random;
        val spParams = ShipPickParams(ShipPickMode.PRIORITY_THEN_ALL)
        var picks = Global.getSector().getFaction(Factions.OMEGA).pickShip(ShipRoles.COMBAT_MEDIUM, spParams)
        if (picks.isNotEmpty()) {
            val plugin = Misc.getAICoreOfficerPlugin(Commodities.ALPHA_CORE)

            var i = 0;
            while (i < 2) {
                var name = Global.getSector().getFaction(Factions.OMEGA).pickRandomShipName(genRandom)
                var member = fleet.fleetData.addFleetMember(picks.random().variantId)
                var person = plugin.createPerson(Commodities.ALPHA_CORE, Factions.REMNANTS, genRandom)
                member.captain = person
                member.repairTracker.cr = member.repairTracker.maxCR
                member.shipName = name
                setupSpecialVariant(member, true)
                i++
            }
        }

        fleet.name = RemnantQuestUtils.getString("salvation_fleetName")
        fleet.isNoFactionInName = true

        //fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE] = true
        fleet.memoryWithoutUpdate["\$genericHail"] = true
        fleet.memoryWithoutUpdate["\$genericHail_openComms"] = "Nex_RemSalvationHail_Towering"
        fleet.memoryWithoutUpdate.set(MemFlags.MEMORY_KEY_PURSUE_PLAYER, true, 1f)
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_HOSTILE] = true
        //fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_LOW_REP_IMPACT] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_NO_REP_IMPACT] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN] = EnemyFIDConfigGen()
        fleet.memoryWithoutUpdate["\$nex_remSalvation_fleet2"] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORES_OTHER_FLEETS] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_DO_NOT_IGNORE_PLAYER] = true
        fleet.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true, 1f)
        fleet.memoryWithoutUpdate.set(NexBattleAutoresolverPlugin.MEM_KEY_STRENGTH_MULT, 1.25f);

        fleet.inflateIfNeeded()

        // manual flagship replacement because specifying it in params breaks if flagship is modular
        var flagship = fleet.flagship
        flagship.isFlagship = false

        flagship = fleet.fleetData.addFleetMember("nex_silverlight_Ascendant")
        var commander = RemnantQuestUtils.getOrCreateTowering()
        fleet.fleetData.setFlagship(flagship)
        flagship.setCaptain(commander)
        flagship.repairTracker.cr = flagship.repairTracker.maxCR
        setupSpecialVariant(flagship, true)
        flagship.shipName = RemnantQuestUtils.getString("salvation_shipName2")
        fleet.commander = commander

        fleet.fleetData.sort()
        fleet.fleetData.setSyncNeeded()
        fleet.fleetData.syncIfNeeded()

        makeImportant(fleet, "\$nex_remSalvation_fleet2_imp", Stage.DEFEND_PLANET)
        //Misc.addDefeatTrigger(fleet, "Nex_RemSalvation_Fleet1Defeated")
        fleet.addEventListener(this)

        fleet.memoryWithoutUpdate[ExerelinCampaignPlugin.MEM_KEY_BATTLE_PLUGIN] =
            SalvationBattleCreationPlugin::class.java.name

        //Global.getSector().hyperspace.addEntity(fleet)
        //fleet.setLocation(target!!.locationInHyperspace.x, target!!.locationInHyperspace.y)
        insertFleet2(fleet)

        knightFleet!!.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 0.5f)
        knightFleet!!.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true, 0.5f)

        return fleet
    }

    /**
     * Triggers the transverse jump for Towering's second fleet.
     */
    protected fun insertFleet2(fleet: CampaignFleetAPI) {
        var planet = target!!.primaryEntity

        target!!.containingLocation.addEntity(fleet)
        fleet.setLocation(99999f, 99999f)
        //Global.getSector().hyperspace.addEntity(fleet)
        //fleet.setLocation(target!!.locationInHyperspace.x, target!!.locationInHyperspace.y)

        // Transverse jump code adapted from FractureJumpAbility
        // the only way I've found to do what I want reliably is to do actually do the jump from in-system
        var loc = Misc.getPointAtRadius(planet.location, planet.radius + 200f + fleet.radius)
        if (DEBUG_MODE) {
            loc = Misc.getStationFleet(target).location;
        }

        val token = planet.containingLocation.createToken(loc.x, loc.y)
        val dest = JumpDestination(token, null)
        Global.getSector().doHyperspaceTransition(fleet, null, dest)
        //fleet.abilities[Abilities.TRANSVERSE_JUMP]!!.activate()   // needs to be specifically on the gravity well

        // chase player if player is near target, else chase the knight fleet
        var player = Global.getSector().playerFleet
        if (planet.containingLocation == player.containingLocation && Misc.getDistance(player, planet) <= 800) {
            fleet.addAssignment(FleetAssignment.INTERCEPT, player, 2f)
            knightFleet?.addAssignmentAtStart(FleetAssignment.ORBIT_PASSIVE, player, 0.5f, null)   // so NexFIDPI will pull it in
        }
        else if (knightFleet != null && knightFleet!!.isAlive) {
            fleet.addAssignment(FleetAssignment.INTERCEPT, knightFleet, 2f)
        }
        fleet.addAssignment(FleetAssignment.DELIVER_FUEL, planet, 60f,
            StringHelper.getFleetAssignmentString("movingInToAttack", planet.name))
        fleet.addAssignment(FleetAssignment.HOLD, planet, 0.25f, StringHelper.getFleetAssignmentString("attacking", planet.name),
            GenericMissionScript(this, "pk"))

        // don't issue return order until we've actually PK'd the target or died trying
        //Misc.giveStandardReturnToSourceAssignments(fleet, false)
    }

    /**
     * Generates the Knights of Ludd fleet.
     */
    protected fun setupKnightFleet(): CampaignFleetAPI {
        var fp = 100f * 2.5f;   // approximate fleet size mult of Gilead

        var params = FleetParamsV3(target!!, target!!.locationInHyperspace, Factions.LUDDIC_CHURCH, null, FleetTypes.TASK_FORCE,
            fp, // combat
            fp/10, 0f, 0f, 0f, 5f,  // freighter, tanker, transport, liner, utility
            .5f
        )
        params.averageSMods = 1
        params.ignoreMarketFleetSizeMult = true
        params.commander = RemnantQuestUtils.getOrCreateArgent()
        params.officerNumberMult = 1.2f
        params.officerLevelBonus = 1

        var fleet = FleetFactoryV3.createFleet(params)
        knightFleet = fleet

        fleet.memoryWithoutUpdate["\$genericHail"] = true
        fleet.memoryWithoutUpdate["\$genericHail_openComms"] = "Nex_RemSalvationHail_Knight"
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE] = true
        // try to make sure it joins the battle
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_PATROL_FLEET] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_PATROL_ALLOW_TOFF] = true
        fleet.memoryWithoutUpdate["\$nex_remSalvation_knightFleet"] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_WAR_FLEET] = true
        // make it busy so patrol assignments don't pull it elsewhere
        Misc.setFlagWithReason(fleet.memoryWithoutUpdate, MemFlags.FLEET_BUSY, "nex_remSalvation", true, -1f)
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_IGNORES_OTHER_FLEETS] = true

        makeImportant(fleet, "\$nex_remSalvation_knightFleet_imp", Stage.DEFEND_PLANET)
        makeImportant(fleet, "\$nex_remSalvation_knightFleet_epilogue_imp", Stage.EPILOGUE)
        makeImportant(params.commander, "\$nex_remSalvation_knightPreBattle2", Stage.DEFEND_PLANET)
        //Misc.addDefeatTrigger(fleet, "Nex_RemSalvation_Fleet1Defeated")
        fleet.addEventListener(this)

        target!!.containingLocation.addEntity(fleet)
        fleet.setLocation(target!!.primaryEntity.location.x, target!!.primaryEntity.location.y)
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, target!!.primaryEntity, 99999f)

        return fleet
    }

    protected fun setupSpecialVariant(flagship : FleetMemberAPI, consistentWeapons : Boolean) {
        NexUtilsFleet.setClonedVariant(flagship, true)
        flagship.variant.addTag(Tags.SHIP_LIMITED_TOOLTIP)  // might be lost if done before inflating
        if (consistentWeapons) flagship.variant.addTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS)
        flagship.variant.addTag(Tags.VARIANT_UNBOARDABLE)
        flagship.variant.addTag(Tags.TAG_NO_AUTOFIT_UNLESS_PLAYER)
        flagship.variant.addTag(Tags.VARIANT_DO_NOT_DROP_AI_CORE_FROM_CAPTAIN)
    }

    protected fun reportWonBattle1(dialog: InteractionDialogAPI?, memoryMap: Map<String, MemoryAPI>?) {
        if (defeatedFleet1) return
        defeatedFleet1 = true
        spawnFlagship1Wreck()
        // send the fleet home if it's still alive
        Misc.giveStandardReturnToSourceAssignments(fleet1, true)
    }

    protected fun reportWonBattle2(dialog: InteractionDialogAPI?, memoryMap: Map<String, MemoryAPI>?) {
        if (defeatedFleet2) return
        defeatedFleet2 = true
        spawnFlagship2Wreck()
        if (targetPKed) completeMissionBadEnd(dialog, memoryMap)
        else {
            setCurrentStage(Stage.EPILOGUE, dialog, memoryMap)
            knightFleet!!.memoryWithoutUpdate["\$genericHail"] = true
            knightFleet!!.memoryWithoutUpdate.unset(MemFlags.MEMORY_KEY_IGNORE_PLAYER_COMMS)
        }

        // unfuck station's malfunction rate
        unapplyStationMalfunction()
        // send the fleet home if it's still alive
        RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(fleet2, true)

        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_endTimestamp"] = Global.getSector().clock.timestamp
    }

    protected fun spawnFlagship1Wreck() {
        val variantId = "nex_silverlight_restrained_Standard"
        val params = DerelictShipData(
            PerShipData(
                Global.getSettings().getVariant(variantId), ShipRecoverySpecial.ShipCondition.WRECKED,
                RemnantQuestUtils.getString("salvation_shipName1"), Factions.REMNANTS, 0f
            ), false
        )
        var flagship1 = BaseThemeGenerator.addSalvageEntity(
            remnantSystem, Entities.WRECK, Factions.NEUTRAL, params
        )
        flagship1.isDiscoverable = true
        flagship1.setLocation(fleet1!!.location.x, fleet1!!.location.y)
        makeImportant(flagship1, "\$nex_remSalvation_flagship1_impFlag", Stage.INVESTIGATE_LEADS)
        flagship1.memoryWithoutUpdate.set("\$nex_remSalvation_flagship1", true)
    }

    protected fun spawnFlagship2Wreck() {
        val variantId = "nex_silverlight_Hull"  // no weapons, since they'll already have dropped in post-battle salvage
        val params = DerelictShipData(
            PerShipData(
                Global.getSettings().getVariant(variantId), ShipRecoverySpecial.ShipCondition.WRECKED,
                RemnantQuestUtils.getString("salvation_shipName2"), Factions.REMNANTS, 0f
            ), false
        )

        var flagship2 = BaseThemeGenerator.addSalvageEntity(
            target!!.containingLocation, Entities.WRECK, Factions.NEUTRAL, params
        )
        val data = ShipRecoverySpecialData(null)
        data.notNowOptionExits = true
        data.noDescriptionText = true
        val copy = (flagship2.customPlugin as DerelictShipEntityPlugin).data.ship.clone()
        copy.variant.source = VariantSource.REFIT
        copy.variant.stationModules.clear()
        copy.variant.addTag(Tags.SHIP_CAN_NOT_SCUTTLE)
        copy.variant.addTag(Tags.SHIP_UNIQUE_SIGNATURE)
        data.addShip(copy)

        Misc.setSalvageSpecial(flagship2, data)
        if (!targetPKed) {
            val haveBoggled = Global.getSettings().modManager.isModEnabled("Terraforming & Station Construction")
            val salvage = Global.getFactory().createCargo(true)
            salvage.addSpecial(SpecialItemData(if (haveBoggled) "boggled_planetkiller" else Items.PLANETKILLER, null), 1f)
            BaseSalvageSpecial.addExtraSalvage(flagship2, salvage)
        }

        flagship2.isDiscoverable = true
        flagship2.setLocation(fleet2!!.location.x, fleet2!!.location.y)
        Misc.makeImportant(flagship2, "\$nex_remSalvation_flagship2_imp")
        flagship2.memoryWithoutUpdate.set("\$nex_remSalvation_flagship2", true)
    }

    protected fun checkFlagshipKilled(fleet : CampaignFleetAPI, function: (dialog: InteractionDialogAPI?, memoryMap : Map<String, MemoryAPI>?) -> Unit)
    {
        if (fleet!!.flagship == null || fleet!!.flagship.captain != RemnantQuestUtils.getOrCreateTowering()) {
            function(null, null)
        }
    }

    override fun acceptImpl(dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>) {
        Misc.makeStoryCritical(base, "nex_remSalvation")
        Misc.makeStoryCritical(arroyoMarket, "nex_remSalvation")
        Misc.makeStoryCritical(target, "nex_remSalvation")
        Global.getSector().memoryWithoutUpdate.set("\$nex_remSalvation_targetNamePermanent", target!!.name)
    }

    /**
     * Disrupts the defense buildings on the League base.
     */
    protected fun trashBase() {
        for (ind in base!!.industries) {
            val spec = ind.spec
            if (spec.hasTag(Industries.TAG_TACTICAL_BOMBARDMENT) || spec.hasTag(Industries.TAG_STATION)) {
                ind.setDisrupted(ind.disruptedDays + NexUtilsMarket.getIndustryDisruptTime(ind), true)
            }
        }
    }

    /**
     * Opens a conversation with a random League ensign in the base visit section of the dialog.
     */
    protected fun setupConvoWithRandomOperator(dialog : InteractionDialogAPI) {
        var person : PersonAPI = OfficerManagerEvent.createOfficer(Global.getSector().getFaction(Factions.PERSEAN), 1)
        person.setPersonality(Personalities.STEADY);    // just to fit the convo, even though no-one will see it lol
        person.rankId = Ranks.SPACE_ENSIGN

        dialog.getInteractionTarget().setActivePerson(person)
        (dialog.getPlugin() as RuleBasedDialog).notifyActivePersonChanged()
        dialog.getVisualPanel().showPersonInfo(person, false, true)
    }

    protected fun markBaseOfficialAsImportant() {
        var best: PersonAPI? = null
        var bestScore = 0
        for (entry in base!!.commDirectory.entriesCopy) {
            if (entry.isHidden) continue
            if (entry.entryData == null || entry.entryData !is PersonAPI) continue
            val pers = entry.entryData as PersonAPI
            if (pers.faction != base!!.faction) continue

            val score = getMilitaryPostScore(pers.postId);
            if (score > bestScore) {
                bestScore = score;
                best = pers;
            }
        }
        makeImportant(best, "\$nex_remSalvation_seniorMil_imp", Stage.GO_TO_BASE)
    }

    protected fun getMilitaryPostScore(postId: String?): Int {
        when (postId) {
            Ranks.POST_BASE_COMMANDER -> return 4
            Ranks.POST_STATION_COMMANDER -> return 3
            Ranks.POST_FLEET_COMMANDER -> return 2
            Ranks.POST_ADMINISTRATOR -> return 1
        }
        return 0
    }

    protected fun beginLeadsStage(dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>) {
        setArroyoImportantIfAvailable();
        setCurrentStage(Stage.INVESTIGATE_LEADS, dialog, memoryMap)
    }

    protected fun cleanup() {
        if (fleet1?.isAlive == true) RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(fleet1, true)
        if (fleet2?.isAlive == true) RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(fleet2, true)
        if (knightFleet?.isAlive == true) Misc.giveStandardReturnToSourceAssignments(knightFleet, true)

        Misc.makeNonStoryCritical(base, "nex_remSalvation")
        Misc.makeNonStoryCritical(arroyoMarket, "nex_remSalvation")
        Misc.makeNonStoryCritical(target, "nex_remSalvation")
    }

    protected fun setupArroyoIfNeeded() {
        var arroyo = Global.getSector().importantPeople.getPerson(People.ARROYO);
        if (arroyo == null) {
            arroyo = Global.getFactory().createPerson()
            arroyo.id = People.ARROYO
            arroyo.setFaction(Factions.TRITACHYON)
            arroyo.gender = FullName.Gender.MALE
            arroyo.rankId = Ranks.CITIZEN
            arroyo.postId = Ranks.POST_SENIOR_EXECUTIVE
            arroyo.importance = PersonImportance.HIGH
            arroyo.name.first = StringHelper.getString("exerelin_misc", "arroyoName1")
            arroyo.name.last = StringHelper.getString("exerelin_misc", "arroyoName2")
            arroyo.portraitSprite = Global.getSettings().getSpriteName("characters", arroyo.id)
            arroyo.stats.setSkillLevel(Skills.BULK_TRANSPORT, 1f)
            arroyo.stats.setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1f)
            arroyo.addTag(Tags.CONTACT_TRADE)
            arroyo.addTag(Tags.CONTACT_MILITARY)
            arroyo.voice = Voices.BUSINESS

            arroyoMarket!!.getCommDirectory().addPerson(arroyo, 0)

            arroyoMarket!!.addPerson(arroyo)
            Global.getSector().importantPeople.addPerson(arroyo)
        } else {
            if (arroyo.market == null) arroyo.market = arroyoMarket
            var directory = arroyoMarket!!.commDirectory.getEntryForPerson(arroyo);
            if (directory == null) arroyoMarket!!.commDirectory.addPerson(arroyo)
            else directory.setHidden(false)
        }
        makeImportant(arroyo, "\$nex_remSalvation_arroyo_imp", Stage.INVESTIGATE_LEADS)
    }

    protected fun setArroyoImportantIfAvailable() {
        makeImportant(Global.getSector().importantPeople.getPerson(People.ARROYO) ?: return, "\$nex_remSalvation_arroyo_imp", Stage.INVESTIGATE_LEADS)
    }

    protected fun metArroyoBefore(): Boolean {
        if (Global.getSector().memoryWithoutUpdate.getBoolean("\$gaka_completed")) return true;
        val arroyo = Global.getSector().importantPeople.getPerson(People.ARROYO) ?: return false

        if (Global.getSector().intelManager.getIntel(ContactIntel::class.java).firstOrNull() {i -> (i as ContactIntel).person == arroyo } != null) {
            return true;
        }

        return false;
    }

    protected fun haveArroyoComms(): Boolean {
        val arroyo = Global.getSector().importantPeople.getPerson(People.ARROYO) ?: return false
        return !arroyoMarket!!.commDirectory.getEntryForPerson(arroyo).isHidden;
    }

    protected fun getCombatSkillLevel(): Float {
        var level = 0f
        for (skill : SkillLevelAPI in Global.getSector().playerStats.skillsCopy) {
            // count tactical drills too
            if (skill.skill.governingAptitudeId != Skills.APT_COMBAT && skill.skill.id != Skills.TACTICAL_DRILLS) continue;
            if (skill.level >= 2) level += 2
            else if (skill.level >= 1) level++
        }
        return level;
    }

    protected fun checkInvestigateStageCompletion(dialog : InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>) {
        if (talkedToArroyo && talkedToTowering1) {
            this.setCurrentStage(Stage.RETURN_TO_MIDNIGHT, dialog, memoryMap)
        } else {
            Global.getSector().intelManager.addIntelToTextPanel(this, dialog.textPanel)
        }
    }

    protected fun failMission(dialog : InteractionDialogAPI?, memoryMap: Map<String, MemoryAPI>?) {
        setCurrentStage(Stage.FAILED, dialog, memoryMap)
        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_missionDone"] = true
        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_missionFailed"] = true
    }

    protected fun completeMission(dialog : InteractionDialogAPI?, memoryMap: Map<String, MemoryAPI>?) {
        setCurrentStage(Stage.COMPLETED, dialog, memoryMap)
        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_missionDone"] = true
        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_missionCompleted"] = true

        var logString = RemnantQuestUtils.getString("salvation_playthroughLog_line")
        logString = StringHelper.substituteToken(logString, "\$target", target!!.name)
        logString = StringHelper.substituteToken(logString, "\$towering", RemnantQuestUtils.getOrCreateTowering().nameString)
        PlaythroughLog.getInstance().addEntry(logString, true)
    }

    protected fun completeMissionBadEnd(dialog : InteractionDialogAPI?, memoryMap: Map<String, MemoryAPI>?) {
        setCreditReward(0)
        setRepFactionChangesNone()
        setRepPersonChangesNone()
        setCurrentStage(Stage.BAD_END, dialog, memoryMap)
        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_missionDone"] = true
        Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_badEnd"] = true
    }

    protected fun setFleetsNonHostile(market : MarketAPI, timer : Float) {
        for (fleet : CampaignFleetAPI in market.containingLocation.fleets) {
            if (fleet.isPlayerFleet) continue
            if (fleet.faction != market.faction) continue;
            Misc.setFlagWithReason(
                fleet.memoryWithoutUpdate,
                MemFlags.MEMORY_KEY_MAKE_NON_HOSTILE,
                "nex_remSalvation_def",
                true,
                timer
            )
        }
    }

    protected fun applyStationMalfunction() {
        val desc = RemnantQuestUtils.getString("salvation_statDescSabotage")
        val fleet = Misc.getStationFleet(target)
        if (fleet != null) {
            for (member in fleet.fleetData.membersListCopy) {
                //fleet.removeFleetMemberWithDestructionFlash(member)
            }
            fleet.memoryWithoutUpdate.set(NexBattleAutoresolverPlugin.MEM_KEY_STRENGTH_MULT, 0.25f, 20f);
            //fleet.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORES_OTHER_FLEETS, true, 2f)  // not needed
            fleet.memoryWithoutUpdate.set(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS, true, 2f)
        }
    }

    protected fun unapplyStationMalfunction() {
        val fleet = Misc.getStationFleet(target)
        if (fleet != null) {
            for (member in fleet.fleetData.membersListCopy) {
                //stats.weaponMalfunctionChance.unmodify(STAT_MOD_ID)
            }
            fleet.memoryWithoutUpdate.unset(NexBattleAutoresolverPlugin.MEM_KEY_STRENGTH_MULT);
            //fleet.memoryWithoutUpdate.unset(MemFlags.FLEET_IGNORES_OTHER_FLEETS)
            fleet.memoryWithoutUpdate.unset(MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS)
        }

        /*
        for (ind in target!!.industries) {
            if (ind.spec.hasTag(Industries.TAG_STATION)) {
                ind.setDisrupted(0.01f)
            }
        }
        */
    }

    protected fun fleet2AILoop() {
        if (fleet2 == null) return;
        if (fleet2!!.currentAssignment != null) {
            return;
        }

        if (targetPKed) {
            RemnantQuestUtils.giveReturnToNearestRemnantBaseAssignments(fleet2, false)
            return;
        }

        val planet = target!!.primaryEntity
        fleet2!!.addAssignment(FleetAssignment.DELIVER_FUEL, planet, 60f,
            StringHelper.getFleetAssignmentString("movingInToAttack", planet.name))
        fleet2!!.addAssignment(FleetAssignment.HOLD, planet, 0.25f, StringHelper.getFleetAssignmentString("attacking", planet.name),
            GenericMissionScript(this, "pk"))
    }

    override fun advanceImpl(amount: Float) {
        super.advanceImpl(amount)
        val days = Global.getSector().clock.convertToDays(amount)
        if (currentStage == Stage.INVESTIGATE_LEADS) {
            setFleetsNonHostile(base!!, 30f)
        }
        else if (currentStage == Stage.DEFEND_PLANET) {

            if (fleet2 != null) {
                /*
                if (warpFleet2Delay > 0) {
                    warpFleet2Delay -= days
                    if (warpFleet2Delay <= 0) insertFleet2(fleet2!!)
                }
                */
                fleet2AILoop()
            } else {
                setFleetsNonHostile(target!!, 1f)
            }

            if (!targetPKed && fleet2 == null) {
                this.timerToPK -= days;
                if (timerToPK > 0) return

                if (!Misc.isNear(Global.getSector().playerFleet, target!!.locationInHyperspace)) {
                    //setupFleet2()
                    deployPK()
                    failMission(null, null)
                } else {
                    setupFleet2()
                    applyStationMalfunction()
                }
            }
        }
    }

    override fun callAction(
        action: String,
        ruleId: String,
        dialog: InteractionDialogAPI,
        params: List<Misc.Token>,
        memoryMap: Map<String, MemoryAPI>
    ): Boolean {
        //log.info("wololo $action")
        when (action) {
            "makeOfficialImportant" -> { markBaseOfficialAsImportant(); return true}
            "setupConvoWithOperator" -> {setupConvoWithRandomOperator(dialog); return true}
            "beginLeadsStage" -> { beginLeadsStage(dialog, memoryMap); return true}
            "reportTalkedToArroyo" -> {
                talkedToArroyo = true;
                makeUnimportant(Global.getSector().importantPeople.getPerson(People.ARROYO), Stage.INVESTIGATE_LEADS)
                Global.getSector().characterData.memoryWithoutUpdate.set("\$nex_witnessed_arroyo_attempted_assassination", true)
                checkInvestigateStageCompletion(dialog, memoryMap)
                return true;
            }
            "reportTalkedToTowering1" -> {
                talkedToTowering1 = true;
                makeUnimportant(remnantSystem!!.hyperspaceAnchor, Stage.INVESTIGATE_LEADS)
                checkInvestigateStageCompletion(dialog, memoryMap)
                return true;
            }
            "setupArroyo" -> { setupArroyoIfNeeded(); return true}
            "explosionSound" -> {
                Global.getSoundPlayer().playUISound("nex_sfx_explosion", 1f, 1f)
                return true
            }
            "showPather" -> {
                var pather = Global.getSector().getFaction(Factions.LUDDIC_PATH).createRandomPerson(FullName.Gender.MALE, genRandom)
                dialog.visualPanel.showSecondPerson(pather)
                return true
            }
            "hireMerc" -> {
                var merc = MercContractIntel("endbringer")
                merc.init(dialog.interactionTarget.market, true);
                merc.accept(dialog.interactionTarget.market, dialog.textPanel)
                hiredEndbringer = true
                return true
            }
            "startDefendStage" -> {
                setCurrentStage(Stage.DEFEND_PLANET, dialog, memoryMap)
                return true
            }
            "floorChurchRep" -> {
                var customRepImpact = CoreReputationPlugin.CustomRepImpact()
                customRepImpact.delta = 0f
                customRepImpact.ensureAtWorst = RepLevel.INHOSPITABLE
                var envelope = RepActionEnvelope(
                    RepActions.CUSTOM, customRepImpact,
                    null, dialog.textPanel, true)
                Global.getSector().adjustPlayerReputation(envelope, Factions.LUDDIC_CHURCH)
                return true
            }
            "reportTalkedToKnight" -> {
                setupFleet2()
                //val currCR = Misc.getStationFleet(target).flagship.repairTracker.cr
                // can't change CR directly, it's auto-set
                // actually we can't change it at all
                applyStationMalfunction()
                makeUnimportant(knightFleet, Stage.DEFEND_PLANET)
                return true
            }
            "complete" -> {completeMission(dialog, memoryMap); return true}
        }
        return false
    }

    override fun updateInteractionDataImpl() {

        set("\$nex_remSalvation_baseName", base!!.name);
        set("\$nex_remSalvation_baseNameAllCaps", base!!.name.uppercase());
        set("\$nex_remSalvation_baseOnOrAt", base!!.onOrAt);
        set("\$nex_remSalvation_arroyoMarketName", arroyoMarket?.name);
        set("\$nex_remSalvation_arroyoMarketOnOrAt", arroyoMarket?.onOrAt);
        set("\$nex_remSalvation_remnantSystem", remnantSystem!!.baseName);
        set("\$nex_remSalvation_targetName", target!!.name);
        set("\$nex_remSalvation_targetId", target!!.id);
        set("\$nex_remSalvation_leagueColor", Global.getSector().getFaction(Factions.PERSEAN).baseUIColor);
        set("\$nex_remSalvation_ttColor", Global.getSector().getFaction(Factions.TRITACHYON).baseUIColor);
        set("\$nex_remSalvation_churchColor", Global.getSector().getFaction(Factions.LUDDIC_CHURCH).baseUIColor);
        set("\$nex_remSalvation_remnantColor", factionForUIColors.baseUIColor);

        //val clock = Global.getSector().clock.createClock(Global.getSector().clock.timestamp - 1000000);
        //set("\$nex_remSalvation_attackDate", "" + clock.getDay() + " " + Global.getSector().clock.shortMonthString);

        val faction = PlayerFactionStore.getPlayerFaction();
        val factionId = faction.id

        set("\$nex_remSalvation_playerFactionId", factionId)
        set("\$nex_remSalvation_isRepresentingState", SectorManager.isFactionAlive(factionId) && !Misc.isDecentralized(faction))
        set("\$nex_remSalvation_playerFaction", faction.displayName)
        set("\$nex_remSalvation_thePlayerFaction", faction.displayNameWithArticle)
        set("\$nex_remSalvation_playerFactionLeaderRank", faction.getRank(Ranks.FACTION_LEADER))
        set("\$nex_remSalvation_haveOwnFaction", faction.isPlayerFaction && SectorManager.isFactionAlive(Factions.PLAYER))

        val metSiyavong = Global.getSector().importantPeople.getPerson(People.SIYAVONG)?.memoryWithoutUpdate?.getBoolean("\$metAlready")
        set("\$nex_remSalvation_metSiyavongBefore", metSiyavong)
        set("\$nex_remSalvation_metArroyoBefore", metArroyoBefore())
        set("\$nex_remSalvation_haveArroyoComms", haveArroyoComms())
        set("\$nex_remSalvation_talkedToArroyo", talkedToArroyo)
        set("\$nex_remSalvation_targetPKed", targetPKed)
        set("\$nex_remSalvation_defeatedFleet1", defeatedFleet1)
        set("\$nex_remSalvation_defeatedFleet2", defeatedFleet2)

        set("\$nex_remSalvation_combatSkillLevel", getCombatSkillLevel())

        //var baseConvoType : String
        //if (factionId.equals(Factions.PERSEAN)) baseConvoType = ""
    }

    override fun addDescriptionForNonEndStage(info: TooltipMakerAPI, width: Float, height: Float) {
        val hl = Misc.getHighlightColor()
        val opad = 10f
        var str = RemnantQuestUtils.getString("salvation_boilerplateDesc")
        str = StringHelper.substituteToken(str, "\$name", person.name.fullName)
        str = StringHelper.substituteToken(str, "\$base", base!!.name)
        info.addPara(str, opad, base!!.faction.baseUIColor, base!!.name)

        if (currentStage == Stage.GO_TO_BASE) {
            info.addPara(RemnantQuestUtils.getString("salvation_startDesc"), opad, base!!.faction.baseUIColor, base!!.name)
        } else if (currentStage == Stage.INVESTIGATE_LEADS) {
            str = RemnantQuestUtils.getString("salvation_investigateLeadsDesc");
            str = StringHelper.substituteToken(str, "\$agentName", Global.getSector().importantPeople.getPerson(People.SIYAVONG).name.last)
            info.addPara(str, opad)
            var arroyo : PersonAPI? = Global.getSector().importantPeople.getPerson(People.ARROYO);

            bullet(info)
            val tt = Global.getSector().getFaction(Factions.TRITACHYON)
            if (!defeatedFleet1)
                info.addPara(RemnantQuestUtils.getString("salvation_investigateLeadsDesc1"), 0f, hl, remnantSystem!!.nameWithLowercaseTypeShort)
            if (!talkedToArroyo) {
                if (metArroyoBefore() || arroyo?.memoryWithoutUpdate?.getBoolean("\$nex_remSalvation_arroyo_imp") == true) info.addPara(RemnantQuestUtils.getString("salvation_investigateLeadsDesc2Known"), 0f, tt.baseUIColor,
                    tt.displayName, arroyo?.nameString, arroyoMarket?.name)
                else info.addPara(RemnantQuestUtils.getString("salvation_investigateLeadsDesc2"), 0f, tt.baseUIColor, tt.displayName)
            }

            unindent(info)
        } else if (currentStage == Stage.RETURN_TO_MIDNIGHT) {
            info.addPara(RemnantQuestUtils.getString("salvation_returnDesc"), opad, person.faction.baseUIColor, person.name.first)
        } else if (currentStage == Stage.DEFEND_PLANET) {
            var key = "salvation_defendPlanetDesc"
            if (targetPKed) key += "PKed"
            info.addPara(RemnantQuestUtils.getString(key), opad, target!!.faction.baseUIColor, target!!.name)
            if (Global.getSettings().isDevMode) {
                info.addPara("[debug] Time left: %s days", opad, hl, String.format("%.1f", this.timerToPK))
            }
        } else if (currentStage == Stage.EPILOGUE) {
            var knight = RemnantQuestUtils.getOrCreateArgent()
            var str = RemnantQuestUtils.getString("salvation_epilogueDesc")
            str = StringHelper.substituteToken(str, "\$system", target!!.containingLocation.name)
            info.addPara(str, opad, knight.faction.baseUIColor, knight.nameString)
        }
    }

    override fun getStageDescriptionText(): String? {
        var key = "salvation_endText"
        if (currentStage == Stage.FAILED) {
            key += "Fail"
        } else if (currentStage == Stage.BAD_END) {
            key += "BadEnd"
        } else if (currentStage == Stage.COMPLETED) {
            key += "Success"
        } else return null

        var str = RemnantQuestUtils.getString(key)
        str = StringHelper.substituteToken(str, "\$nex_remSalvation_targetName", target!!.name)
        str = StringHelper.substituteToken(str, "\$playerFirstName", Global.getSector().playerPerson.name.first)
        return str
    }

    override fun addNextStepText(info: TooltipMakerAPI, tc: Color?, pad: Float): Boolean {
        super.addNextStepText(info, tc, pad)
        val hl = Misc.getHighlightColor()
        //val col: Color = station.getStarSystem().getStar().getSpec().getIconColor()
        //val sysName: String = station.getContainingLocation().getNameWithLowercaseTypeShort()

        //info.addPara("[debug] Current stage: " + currentStage, tc, pad);
        if (currentStage == Stage.GO_TO_BASE) {
            info.addPara(
                RemnantQuestUtils.getString("salvation_startNextStep"), pad, tc, base!!.faction.baseUIColor, base!!.name
            )
        } else if (currentStage == Stage.INVESTIGATE_LEADS) {
            val tt = Global.getSector().getFaction(Factions.TRITACHYON)
            if (!defeatedFleet1)
                info.addPara(RemnantQuestUtils.getString("salvation_investigateLeadsNextStep1"), pad, tc, hl, remnantSystem!!.nameWithLowercaseTypeShort)
            if (!talkedToArroyo)
                info.addPara(RemnantQuestUtils.getString("salvation_investigateLeadsNextStep2"), 0f, tc, tt.baseUIColor, tt.displayName)
        } else if (currentStage == Stage.RETURN_TO_MIDNIGHT) {
            info.addPara(RemnantQuestUtils.getString("salvation_returnNextStep"), pad, tc, person.faction.baseUIColor, person.name.first)
        } else if (currentStage == Stage.DEFEND_PLANET) {
            var key = "salvation_defendPlanetNextStep"
            if (targetPKed) key += "PKed"
            info.addPara(RemnantQuestUtils.getString(key), pad, tc, target!!.faction.baseUIColor, target!!.name)
        } else if (currentStage == Stage.EPILOGUE) {
            var knight = RemnantQuestUtils.getOrCreateArgent()
            info.addPara(RemnantQuestUtils.getString("salvation_epilogueNextStep"), pad, tc, knight.faction.baseUIColor, knight.name.last)
        }
        return false
    }

    override fun getBaseName(): String? {
        return RemnantQuestUtils.getString("salvation_name")
    }

    override fun notifyEnding() {
        cleanup()
    }

    class EnemyFIDConfigGen : FIDConfigGen {
        override fun createConfig(): FIDConfig {
            val config = FIDConfig()
            config.delegate = object : BaseFIDDelegate() {
                override fun postPlayerSalvageGeneration(
                    dialog: InteractionDialogAPI,
                    context: FleetEncounterContext,
                    salvage: CargoAPI
                ) {
                }

                override fun notifyLeave(dialog: InteractionDialogAPI) {
                    // unreliable and won't work if fleet is killed with campaign console; just use the EveryFrameScript
                    /*
					dialog.setInteractionTarget(Global.getSector().getPlayerFleet());
					RuleBasedInteractionDialogPluginImpl plugin = new RuleBasedInteractionDialogPluginImpl();
					dialog.setPlugin(plugin);
					plugin.init(dialog);
					plugin.fireBest("Sunrider_Mission4_PostEncounterDialogStart");
					*/
                }

                override fun battleContextCreated(dialog: InteractionDialogAPI, bcc: BattleCreationContext) {
                    bcc.aiRetreatAllowed = false
                    bcc.fightToTheLast = true
                }
            }
            return config
        }
    }

    override fun reportFleetDespawnedToListener(
        fleet: CampaignFleetAPI?,
        reason: CampaignEventListener.FleetDespawnReason?,
        param: Any?
    ) {
        // fleet 1 gone
        if (fleet == this.fleet1) {
            reportWonBattle1(null, null)
        }
        // knight fleet gone
        else if (fleet == this.knightFleet) {
            var knight = RemnantQuestUtils.getOrCreateArgent()
            target!!.addPerson(knight)
            target!!.commDirectory.addPerson(knight)
        }
        // fleet 2 killed, OR did its thing and escaped
        else if (fleet == this.fleet2) {
            val reasonIsLose = reason == CampaignEventListener.FleetDespawnReason.REACHED_DESTINATION
                    || reason == CampaignEventListener.FleetDespawnReason.PLAYER_FAR_AWAY
            if (reasonIsLose && targetPKed) {
                failMission(null, null)
            } else reportWonBattle2(null, null)
        }
    }

    override fun reportBattleOccurred(fleet: CampaignFleetAPI?, primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {
        if (fleet == fleet1) {
            checkFlagshipKilled(fleet!!) { _: InteractionDialogAPI?, _: Map<String, MemoryAPI>? -> reportWonBattle1(null, null) }
        }

        if (fleet == fleet2) {
            checkFlagshipKilled(fleet!!) { _: InteractionDialogAPI?, _: Map<String, MemoryAPI>? -> reportWonBattle2(null, null) }
        }
    }

    class GenericMissionScript(var intel : RemnantSalvation, val param : String) : Script {
        override fun run() {
            when (param) {
                "trashBase" -> intel.trashBase()
                "setupFleet1" -> {
                    val station = intel.setupFleet1Station()
                    intel.setupFleet1(station)
                }
                "setupKnightFleet" -> intel.setupKnightFleet()
                "pk" -> intel.checkPK()
                "setupFleet2" -> intel.setupFleet2()
            }
        }
    }


}