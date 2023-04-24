package exerelin.campaign.intel.missions.remnant

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.campaign.listeners.FleetEventListener
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.characters.FullName
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI
import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.combat.BattleCreationContext
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin.DerelictShipData
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl.*
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.*
import com.fs.starfarer.api.impl.campaign.intel.contacts.ContactIntel
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithBarEvent
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithSearch.MarketRequirement
import com.fs.starfarer.api.impl.campaign.missions.hub.ReqMode
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial.PerShipData
import com.fs.starfarer.api.loading.VariantSource
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.PlayerFactionStore
import exerelin.campaign.SectorManager
import exerelin.campaign.intel.missions.BuildStation.SystemUninhabitedReq
import exerelin.utilities.NexUtilsFaction
import exerelin.utilities.NexUtilsFleet
import exerelin.utilities.NexUtilsMarket
import exerelin.utilities.StringHelper
import lombok.Getter
import org.apache.log4j.Logger
import java.awt.Color


open class RemnantSalvation : HubMissionWithBarEvent(), FleetEventListener {
    enum class Stage {
        GO_TO_BASE, INVESTIGATE_LEADS, RETURN_TO_MIDNIGHT, DEFEND_PLANET, EPILOGUE, COMPLETED, FAILED, BAD_END
    }

    @Getter protected var base: MarketAPI? = null
    @Getter protected var remnantSystem: StarSystemAPI? = null
    @Getter protected var target: MarketAPI? = null
    protected var arroyoMarket: MarketAPI? = null
    protected var fleet1: CampaignFleetAPI? = null
    protected var fleet2: CampaignFleetAPI? = null
    protected var knightFleet: CampaignFleetAPI? = null

    protected var defeatedFleet1 = false
    protected var defeatedFleet2 = false
    protected var talkedToArroyo = false
    protected var talkedToTowering1 = false
    protected var toldKnightsAboutMidnight = false
    protected var targetPKed = false

    // runcode exerelin.campaign.intel.missions.remnant.RemnantSalvation.Companion.devAddTriggers()
    companion object {
        @JvmStatic val log : Logger = Global.getLogger(ContactIntel::class.java)
        @JvmStatic fun devAddTriggers() {
            var mission = Global.getSector().memoryWithoutUpdate["\$nex_remSalvation_ref"] as RemnantSalvation
            mission.addAdditionalTriggersDev()
        }
    }

    override fun create(createdAt: MarketAPI, barEvent: Boolean): Boolean {
        if (!setGlobalReference("\$nex_remSalvation_ref")) {
            return false
        }
        val madeira = Global.getSector().economy.getMarket("madeira")
        base = if (madeira != null && madeira.factionId == Factions.PERSEAN) madeira else {
            requireMarketFaction(Factions.PERSEAN)
            requireMarketNotHidden()
            requireMarketNotInHyperspace()
            preferMarketSizeAtLeast(5)
            preferMarketIsMilitary()
            pickMarket()
        }
        if (base == null) return false

        arroyoMarket = Global.getSector().importantPeople.getPerson(People.ARROYO).market;
        if (arroyoMarket == null) {
            requireMarketFaction(Factions.TRITACHYON)
            requireMarketNotHidden()
            requireMarketNotInHyperspace()
            preferMarketSizeAtLeast(5)
            arroyoMarket = pickMarket()
            if (arroyoMarket != null) setupArroyoIfNeeded();
        }
        if (arroyoMarket == null) return false;

        val gilead = Global.getSector().economy.getMarket("gilead")
        target = if (gilead != null && NexUtilsFaction.isLuddicFaction(gilead.factionId)) gilead else {
            requireMarketFaction(Factions.LUDDIC_CHURCH)
            requireMarketNotHidden()
            requireMarketNotInHyperspace()
            requireMarketSizeAtLeast(6)
            preferMarketSizeAtLeast(7)
            // prefer non-military
            search.marketPrefs.add(MarketRequirement { market -> !Misc.isMilitary(market) })
            pickMarket()
        }
        if (target == null) return false

        // pick Remnant location
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
        preferSystemTags(ReqMode.ANY, Tags.THEME_REMNANT, Tags.THEME_DERELICT, Tags.THEME_INTERESTING)
        requireSystemWithinRangeOf(base!!.locationInHyperspace, 15f)
        search.systemReqs.add(SystemUninhabitedReq())
        preferSystemOutsideRangeOf(base!!.locationInHyperspace, 7f)
        preferSystemUnexplored()
        remnantSystem = pickSystem()
        if (remnantSystem == null) return false
        setStartingStage(Stage.GO_TO_BASE)
        addSuccessStages(Stage.COMPLETED, Stage.BAD_END)
        addFailureStages(Stage.FAILED)
        setStoryMission()
        setupTriggers()

        //makeImportant(station, "$nex_remBrawl_target", RemnantBrawl.Stage.GO_TO_TARGET_SYSTEM, RemnantBrawl.Stage.BATTLE, RemnantBrawl.Stage.BATTLE_DEFECTED);
        setRepPersonChangesVeryHigh()
        setRepFactionChangesHigh()
        setCreditReward(CreditReward.VERY_HIGH)
        setCreditReward(creditReward * 4)
        return true
    }

    protected fun setupTriggers() {
        makeImportant(base, "\$nex_remSalvation_base_imp", Stage.GO_TO_BASE)
        makeImportant(remnantSystem!!.hyperspaceAnchor, "\$nex_remSalvation_remnantSystem_imp", Stage.INVESTIGATE_LEADS)
        makeImportant(person, "\$nex_remSalvation_returnStage_imp", Stage.RETURN_TO_MIDNIGHT)
        makeImportant(target, "\$nex_remSalvation_target_imp", Stage.DEFEND_PLANET)

        // just sets up a memory value we'll use later
        beginStageTrigger(Stage.GO_TO_BASE)
        triggerSetMemoryValue(base, "\$nex_remSalvation_seenBaseIntro", false)
        endTrigger()

        // Approach base: add some wreckage to the attacked base and disrupt its industries
        beginWithinHyperspaceRangeTrigger(base, 2f, false, Stage.GO_TO_BASE)
        val loc = LocData(base!!.primaryEntity, true)
        triggerSpawnDebrisField(DEBRIS_MEDIUM, DEBRIS_DENSE, loc)
        triggerSpawnShipGraveyard(Factions.REMNANTS, 2, 2, loc)
        triggerSpawnShipGraveyard(Factions.PERSEAN, 4, 6, loc)
        triggerRunScriptAfterDelay(0.1f) { trashBase() }
        endTrigger()

        // Approach Remnant system: add a salvagable station and the fleet
        beginWithinHyperspaceRangeTrigger(remnantSystem, 2f, false, Stage.INVESTIGATE_LEADS)
        triggerRunScriptAfterDelay(0.1f) { var station = setupFleet1Station(); setupFleet1(station) }
        endTrigger()
    }

    protected fun addAdditionalTriggersDev() {
        //log.info("blabla " + fleet1!!.flagship.variant.hasTag(Tags.SHIP_LIMITED_TOOLTIP))
        //if (true) return;

        beginWithinHyperspaceRangeTrigger(remnantSystem, 2f, false, Stage.INVESTIGATE_LEADS)
        triggerRunScriptAfterDelay(0.1f) { var station = setupFleet1Station(); setupFleet1(station) }
        endTrigger()
    }

    protected fun setupFleet1Station() : SectorEntityToken {
        //log.info("Running station setup")
        val loc = this.generateLocation(null, EntityLocationType.ORBITING_PLANET_OR_STAR, null, remnantSystem)
        val stationDefId = if (Global.getSettings().modManager.isModEnabled("IndEvo")) "IndEvo_arsenalStation" else "station_mining_remnant"
        var station = remnantSystem!!.addCustomEntity("nex_remSalvation_fleet1_station", null, stationDefId, Factions.REMNANTS)
        station.orbit = loc.orbit.makeCopy()
        //log.info(String.format("Wololo, orbit period %s, target %s", station.orbit.orbitalPeriod, station.orbit.focus.name))
        return station
    }

    protected fun setupFleet1(toOrbit : SectorEntityToken): CampaignFleetAPI {
        val playerStr = NexUtilsFleet.calculatePowerLevel(Global.getSector().playerFleet).toFloat()
        val capBonus = Math.round(NexUtilsFleet.getPlayerLevelFPBonus())
        var fp = (playerStr + capBonus) * 0.6f// - 40
        fp = fp.coerceAtLeast(10f)

        var params = FleetParamsV3(remnantSystem!!.location, Factions.REMNANTS, 1.2f, FleetTypes.TASK_FORCE,
                fp, // combat
                0f, 0f, 0f, 0f, 0f,  // freighter, tanker, transport, liner, utility
                0f
        )
        params.aiCores = OfficerQuality.AI_BETA_OR_GAMMA
        params.averageSMods = 1
        params.commander = RemnantQuestUtils.getOrCreateTowering()
        params.flagshipVariantId = "nex_silverlight_restrained_Standard"
        var fleet = FleetFactoryV3.createFleet(params)
        fleet1 = fleet

        fleet.memoryWithoutUpdate.set("\$ignorePlayerCommRequests", true)
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_ALWAYS_PURSUE] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_PURSUE_PLAYER] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_HOSTILE] = true
        //fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_MAKE_AGGRESSIVE_ONE_BATTLE_ONLY] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_LOW_REP_IMPACT] = true
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_NO_REP_IMPACT] = true
        fleet.memoryWithoutUpdate[MemFlags.FLEET_INTERACTION_DIALOG_CONFIG_OVERRIDE_GEN] = EnemyFIDConfigGen()
        fleet.memoryWithoutUpdate["\$nex_remSalvation_fleet1"] = true


        var flagship = fleet.flagship
        flagship.captain = params.commander

        fleet.inflateIfNeeded()
        setupFlagshipVariant(flagship)

        makeImportant(fleet, "\$nex_remSalvation_fleet1", Stage.INVESTIGATE_LEADS)
        //Misc.addDefeatTrigger(fleet, "Nex_RemSalvation_Fleet1Defeated")
        fleet.addEventListener(this)

        remnantSystem!!.addEntity(fleet)
        fleet.setLocation(toOrbit.location.x, toOrbit.location.y)
        fleet.addAssignment(FleetAssignment.ORBIT_AGGRESSIVE, toOrbit, 99999f)

        return fleet
    }

    protected fun setupFlagshipVariant(flagship : FleetMemberAPI) {
        flagship.setVariant(flagship.variant.clone(), false, false)
        flagship.variant.source = VariantSource.REFIT
        flagship.variant.addTag(Tags.SHIP_LIMITED_TOOLTIP)  // might be lost if done before inflating
        //flagship.variant.addTag(Tags.VARIANT_CONSISTENT_WEAPON_DROPS)
        flagship.variant.addTag(Tags.UNRECOVERABLE)
    }

    protected fun reportWonBattle1() {
        if (defeatedFleet1) return
        defeatedFleet1 = true
        spawnFlagship1Wreck()
    }

    protected fun spawnFlagship1Wreck() {
        val variantId = "nex_silverlight_restrained_Standard"
        val params = DerelictShipData(
            PerShipData(
                variantId,
                ShipRecoverySpecial.ShipCondition.WRECKED, 0f
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

    override fun acceptImpl(dialog: InteractionDialogAPI, memoryMap: Map<String, MemoryAPI>) {
        Misc.makeStoryCritical(base, "nex_remSalvation")
        Misc.makeStoryCritical(arroyoMarket, "nex_remSalvation")
        Misc.makeStoryCritical(target, "nex_remSalvation")
    }

    protected fun trashBase() {
        for (ind in base!!.industries) {
            val spec = ind.spec
            if (spec.hasTag(Industries.TAG_TACTICAL_BOMBARDMENT) || spec.hasTag(Industries.TAG_STATION)) {
                ind.setDisrupted(ind.disruptedDays + NexUtilsMarket.getIndustryDisruptTime(ind), true)
            }
        }
    }

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
        setCurrentStage(Stage.INVESTIGATE_LEADS, dialog, memoryMap)
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
            arroyo.name.first = "Rayan"
            arroyo.name.last = "Arroyo"
            arroyo.portraitSprite = Global.getSettings().getSpriteName("characters", arroyo.id)
            arroyo.stats.setSkillLevel(Skills.BULK_TRANSPORT, 1f)
            arroyo.stats.setSkillLevel(Skills.INDUSTRIAL_PLANNING, 1f)
            arroyo.addTag(Tags.CONTACT_TRADE)
            arroyo.addTag(Tags.CONTACT_MILITARY)
            arroyo.voice = Voices.BUSINESS

            arroyoMarket!!.getCommDirectory().addPerson(arroyo, 1) // second after Sun

            //arroyoMarket!!.getCommDirectory().getEntryForPerson(arroyo).setHidden(true)
            arroyoMarket!!.addPerson(arroyo)
            Global.getSector().importantPeople.addPerson(arroyo)
        } else {
            arroyo.market = arroyoMarket
            arroyoMarket!!.getCommDirectory().getEntryForPerson(arroyo).setHidden(false)
        }
        makeImportant(arroyo, "\$nex_remSalvation_arroyo_imp", Stage.INVESTIGATE_LEADS)
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
            if (skill.skill.governingAptitudeId != Skills.APT_COMBAT) continue;
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
        }
        return false
    }

    override fun updateInteractionDataImpl() {

        set("\$nex_remSalvation_baseName", base!!.name);
        set("\$nex_remSalvation_baseNameAllCaps", base!!.name.uppercase());
        set("\$nex_remSalvation_baseOnOrAt", base!!.onOrAt);
        set("\$nex_remSalvation_arroyoMarketName", arroyoMarket!!.name);
        set("\$nex_remSalvation_arroyoMarketOnOrAt", arroyoMarket!!.onOrAt);
        set("\$nex_remSalvation_remnantSystem", remnantSystem!!.baseName);
        set("\$nex_remSalvation_leagueColor", Global.getSector().getFaction(Factions.PERSEAN).baseUIColor);
        set("\$nex_remSalvation_ttColor", Global.getSector().getFaction(Factions.TRITACHYON).baseUIColor);
        set("\$nex_remSalvation_remnantColor", factionForUIColors.baseUIColor);

        //val clock = Global.getSector().clock.createClock(Global.getSector().clock.timestamp - 1000000);
        //set("\$nex_remSalvation_attackDate", "" + clock.getDay() + " " + Global.getSector().clock.shortMonthString);

        val factionId = PlayerFactionStore.getPlayerFactionId()

        set("\$nex_remSalvation_playerFactionId", factionId)
        set("\$nex_remSalvation_isRepresentingState", SectorManager.isFactionAlive(factionId))
        set("\$nex_remSalvation_playerFaction", PlayerFactionStore.getPlayerFaction().displayName)
        set("\$nex_remSalvation_thePlayerFaction", PlayerFactionStore.getPlayerFaction().displayNameWithArticle)
        set("\$nex_remSalvation_playerFactionLeaderRank", PlayerFactionStore.getPlayerFaction().getRank(Ranks.FACTION_LEADER))
        set("\$nex_remSalvation_haveOwnFaction", PlayerFactionStore.getPlayerFaction().isPlayerFaction && SectorManager.isFactionAlive(Factions.PLAYER))

        val metSiyavong = Global.getSector().importantPeople.getPerson(People.SIYAVONG)?.memoryWithoutUpdate?.getBoolean("\$metAlready")
        set("\$nex_remSalvation_metSiyavongBefore", metSiyavong)
        set("\$nex_remSalvation_metArroyoBefore", metArroyoBefore())
        set("\$nex_remSalvation_haveArroyoComms", haveArroyoComms())
        set("\$nex_remSalvation_talkedToArroyo", talkedToArroyo)
        set("\$nex_remSalvation_defeatedFleet1", defeatedFleet1)

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

            bullet(info)
            val tt = Global.getSector().getFaction(Factions.TRITACHYON)
            if (!defeatedFleet1)
                info.addPara(RemnantQuestUtils.getString("salvation_investigateLeadsDesc1"), 0f, hl, remnantSystem!!.nameWithLowercaseTypeShort)
            if (!talkedToArroyo)
                info.addPara(RemnantQuestUtils.getString("salvation_investigateLeadsDesc2"), 0f, tt.baseUIColor, tt.displayName)
            unindent(info)
        } else if (currentStage == Stage.RETURN_TO_MIDNIGHT) {
            info.addPara(RemnantQuestUtils.getString("salvation_returnDesc"), opad, person.faction.baseUIColor, person.name.first)
        }

    }

    override fun addNextStepText(info: TooltipMakerAPI, tc: Color?, pad: Float): Boolean {
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
        }
        return false
    }

    override fun getBaseName(): String? {
        return RemnantQuestUtils.getString("salvation_name")
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
        if (fleet == this.fleet1) {
            reportWonBattle1()
        }
    }

    override fun reportBattleOccurred(fleet: CampaignFleetAPI?, primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {

    }
}