package exerelin.campaign.intel.fleets

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.fleets.RouteLocationCalculator
import com.fs.starfarer.api.impl.campaign.intel.group.BlockadeFGI
import com.fs.starfarer.api.impl.campaign.intel.group.FGBlockadeAction.FGBlockadeParams
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.PlayerFactionStore
import exerelin.campaign.ai.action.StrategicAction
import exerelin.campaign.ai.action.StrategicActionDelegate.ActionStatus
import exerelin.utilities.NexConfig
import lombok.Getter
import lombok.Setter
import java.util.*
import kotlin.math.roundToInt

/**
 * Wrapper for a base game {@code BlockadeFGI}, for places where an {@code OffensiveFleetIntel} is expected.
 * Should not be itself added to intel manager or the sector.
 */
class BlockadeWrapperIntel(attacker: FactionAPI?, from: MarketAPI?, target: MarketAPI?, fp: Float, orgDur: Float) :
    OffensiveFleetIntel(attacker, from, target, fp, orgDur), FleetGroupIntel.FGIEventListener {

    @Getter @Setter protected lateinit var fgi: BlockadeFGI

    override fun init() {
        log.info("Creating blockade intel")
        val raidJump: SectorEntityToken? =
            RouteLocationCalculator.findJumpPointToUse(factionForUIColors, target.primaryEntity)
        if (raidJump == null) {
            endImmediately()
            return
        }

        val random = Random()
        val params = GenericRaidParams(Random(random.nextLong()), target.faction.isPlayerFaction)
        params.factionId = faction.id
        params.source = from
        params.prepDays = this.orgDur
        params.payloadDays = 120f
        params.makeFleetsHostile = false
        addFleetsToParams(params)

        val bParams = FGBlockadeParams()
        bParams.where = target.starSystem
        bParams.targetFaction = target.factionId

        params.style = FleetCreatorMission.FleetStyle.STANDARD

        fgi = NexBlockadeFGI(params, bParams)
        fgi.listener = this

        when (NexConfig.nexIntelQueued) {
            0 -> addIntelIfNeeded()
            1 -> if (isPlayerTargeted || playerSpawned || targetFaction === Misc.getCommissionFaction()) //TODO all intel has the problem of not updating without active comm relays and not queueing the update
                addIntelIfNeeded() else if (shouldDisplayIntel()) queueIntelIfNeeded()
            2 -> if (playerSpawned) addIntelIfNeeded() else if (shouldDisplayIntel()) {
                Global.getSector().intelManager.queueIntel(fgi)
                intelQueuedOrAdded = true
            }
            else -> {
                addIntelIfNeeded()
                Global.getSector().campaignUI.addMessage(
                    "Switch statement within init(), in BlockadeWrapperIntel, " +
                            "defaulted. This is not supposed to happen. If your nexIntelQueued setting within ExerelinConfig " +
                            "is below 0 or above 2, that is the likely cause. Otherwise, please contact the mod author!"
                )
            }
        }
    }

    /**
     * Converts {@code fp} to fleet counts and sizes.
     */
    fun addFleetsToParams(params: GenericRaidParams) {
        val maxFPPerFleet = faction.getApproximateMaxFPPerFleet(FactionAPI.ShipPickMode.PRIORITY_THEN_ALL) * 0.9f

        var totalDifficulty: Int = (this.fp / maxFPPerFleet * 10 * BLOCKADE_FP_BONUS_MULT).roundToInt().coerceAtLeast(1)

        log.info(String.format("  Blockade initial FP: %s, max FP per fleet %s, total int points: %s", this.fp, maxFPPerFleet, totalDifficulty))

        while (totalDifficulty > 0) {
            var thisDiff = 5
            // every sixth fleet can be maximum size
            if (params.fleetSizes.size % 6 == 0) thisDiff = totalDifficulty

            thisDiff = thisDiff.coerceAtMost(9)
            log.info(String.format("  Number of fleets previously %s, size int for this one %s",  params.fleetSizes.size, thisDiff))
            totalDifficulty -= thisDiff
            log.info("  Size points remaining: " + totalDifficulty)

            params.fleetSizes.add(totalDifficulty)
        }
    }

    override fun queueIntelIfNeeded() {
        if (intelQueuedOrAdded) return
        if (faction.isPlayerFaction) Global.getSector().intelManager.addIntel(fgi) else Global.getSector().intelManager.queueIntel(
            fgi
        )
        intelQueuedOrAdded = true
    }

    override fun addIntelIfNeeded() {
        if (intelQueuedOrAdded) return
        if (shouldMakeImportantIfTargetingPlayer()
            && (targetFaction.isPlayerFaction || targetFaction === PlayerFactionStore.getPlayerFaction())
        ) fgi.isImportant = true
        Global.getSector().intelManager.addIntel(fgi)
        intelQueuedOrAdded = true
    }

    override fun reportFGIAborted(intel: FleetGroupIntel?) {

    }

    override fun getType(): String {
        return "blockade"
    }

    override fun getName(): String {
        return fgi?.name ?: "blockade wrapper name"
    }

    override fun getIcon(): String? {
        return fgi?.icon ?: null
    }

    // because Kotlin cannot into Lombok
    override fun getStrategicAction(): StrategicAction {
        return strategicAction
    }
    override fun setStrategicAction(strategicAction: StrategicAction?) {
        this.strategicAction = strategicAction
        if (fgi is NexBlockadeFGI) (fgi as NexBlockadeFGI).strategicAction = strategicAction;
    }

    override fun getIntelTags(map: SectorMapAPI): Set<String> {
        val tags = super.getIntelTags(map)
        fgi?.getIntelTags(map)?.let { tags.addAll(it) }
        return tags
    }

    override fun getStrategicActionStatus(): ActionStatus {
        if (fgi == null || fgi.isAborted) return ActionStatus.CANCELLED
        if (fgi.isSucceeded) return ActionStatus.SUCCESS
        if (fgi.isFailed) return ActionStatus.FAILURE
        if (fgi.isInPreLaunchDelay) return ActionStatus.STARTING

        if (fgi.isEnding || fgi.isEnded) return ActionStatus.CANCELLED

        return ActionStatus.IN_PROGRESS
    }

    override fun addStrategicActionInfo(info: TooltipMakerAPI?, width: Float) {

    }

    override fun getStrategicActionDaysRemaining(): Float {
        val untilDeparture: Float = fgi.getETAUntil(GenericRaidFGI.TRAVEL_ACTION, true)
        val untilRaid: Float = fgi.getETAUntil(GenericRaidFGI.PAYLOAD_ACTION, true)
        return untilDeparture + untilRaid
    }

    override fun abortStrategicAction() {
        fgi.abort()
    }

    override fun getStrategicActionName(): String {
        return fgi?.name ?: super.getStrategicActionName()
    }

    companion object {
        @JvmField val BLOCKADE_FP_BONUS_MULT = 1.5f
    }
}