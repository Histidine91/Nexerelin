package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.TextPanelAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.intel.group.BaseFGAction
import com.fs.starfarer.api.impl.campaign.intel.group.FGAction
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD
import com.fs.starfarer.api.ui.ButtonAPI
import com.fs.starfarer.api.ui.IntelUIAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import exerelin.utilities.NexUtilsGUI
import exerelin.utilities.NexUtilsMarket

open class PoliceRaidFGI(params: GenericRaidParams?) : GenericRaidFGI(params)
{
    companion object {
        const val MEM_KEY_FLEET = "\$nex_HA_policeRaid_fleet"
        const val MEM_KEY_TARGET_MARKET = "\$nex_HA_policeRaid_targetMarket"
        const val MEM_KEY_TARGET_ON_OR_AT = "\$nex_HA_policeRaid_targetOnOrAt"
        const val CRIMINAL_DEF_PER_INDUSTRY = 100f
        const val BUTTON_TOGGLE_RESIST = "btnToggleResist"
    }

    var resist = false;
    @Transient var resistCheckbox : ButtonAPI? = null;

    val factionPicker = WeightedRandomPicker<String>(this.random)

    override fun getFleetCreationFactionOverride(size: Int): String? {
        if (size >= 8) return Factions.MERCENARY

        if (factionPicker.isEmpty) factionPicker.addAll(PoliceHostileActivityFactor.getEligibleFactions())
        if (factionPicker.isEmpty) return Factions.MERCENARY
        return factionPicker.pickAndRemove()
    }

    override fun configureFleet(size: Int, m: FleetCreatorMission?) {
        m!!.triggerSetFleetFlag(MEM_KEY_FLEET)
        val target = params.raidParams.allowedTargets.first()
        m.triggerSetFleetMemoryValue(MEM_KEY_TARGET_MARKET, target.name)
        m.triggerSetFleetMemoryValue(MEM_KEY_TARGET_ON_OR_AT, target.onOrAt)

        if (size >= 8) {
            //m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_2)
            m.triggerFleetSetName(PoliceHostileActivityFactor.getString("fleetRaidLarge"))
        } else {
            m.triggerFleetSetName(PoliceHostileActivityFactor.getString("fleetRaid"))
        }
        var tugs = 1
        if (size >= 8) {
            tugs = 2
        }
        val lightDetachment = size <= 5
        if (lightDetachment) {
            //m.triggerSetFleetMaxShipSize(3)
        }
        m.triggerFleetMakeFaster(true, tugs, true)
    }

    override fun configureFleet(size: Int, fleet: CampaignFleetAPI?) {
        super.configureFleet(size, fleet)

        if (shouldMakeHostile()) {
            makeFleetHostile(fleet ?: return)
        }
    }

    override fun hasCustomRaidAction(): Boolean {
        if (resist) return true;

        val market = params.raidParams.allowedTargets.first()
        val anyHostile = this.fleets.any { it.isHostileTo(market.primaryEntity) || it.isHostileTo(Global.getSector().playerFleet) }
        return !anyHostile  // if any of the fleets are hostile, do the full-up raid against defenses
    }

    override fun doCustomRaidAction(fleet: CampaignFleetAPI?, market: MarketAPI?, raidStr: Float) {
        raidVsCriminals(fleet, market, raidStr)
    }

    fun raidVsCriminals(fleet: CampaignFleetAPI?, market: MarketAPI?, raidStr: Float) {
        //val mcmd = Nex_MarketCMD(market!!.primaryEntity)
        Global.getLogger(this.javaClass).info("Attempting special raid vs ${market!!.name}")
        val temp = Nex_MarketCMD.NexTempData(market!!.primaryEntity) //mcmd.tempData

        temp.raidType = RaidType.CUSTOM_ONLY
        temp.targetFaction = market.faction

        temp.attackerStr = raidStr
        temp.defenderStr = CRIMINAL_DEF_PER_INDUSTRY * market.industries.filter { PoliceHostileActivityFactor.isRaidableCriminalIndustry(it) }.count()

        var canDisrupt = true
        temp.raidMult = temp.attackerStr / Math.max(1f, temp.attackerStr + temp.defenderStr)
        temp.raidMult = Math.round(temp.raidMult * 100f) / 100f

        if (temp.raidMult < MarketCMD.DISRUPTION_THRESHOLD) {
            canDisrupt = false
        }
        if (!canDisrupt) return

        val random = getRandom()

        var reason = PoliceHostileActivityFactor.getString("raidUnrestReason")
        RecentUnrest.get(market).add(2, reason)
        Misc.setFlagWithReason(
            market.memoryWithoutUpdate, MemFlags.RECENTLY_RAIDED,
            "PoliceRaidFGI", true, 30f
        )
        Misc.setRaidedTimestamp(market)

        val durMult = 0.75f     // Global.getSettings().getFloat("punitiveExpeditionDisruptDurationMult")
        for (industryId in params.raidParams.disrupt) {
            val ind = market.getIndustry(industryId) ?: continue
            temp.target = ind

            var dur: Float = ind.spec.disruptDanger.disruptionDays - ind.disruptedDays;
            dur *= StarSystemGenerator.getNormalRandom(random, 1f, 1.25f)
            dur *= durMult
            if (dur < 2) dur = 2f
            val already: Float = ind.disruptedDays
            ind.setDisrupted(already + dur)
            NexUtilsMarket.reportNPCIndustryRaid(market, temp, ind)
        }

        market.isFreePort = false
    }

    override fun createSmallDescription(info: TooltipMakerAPI?, width: Float, height: Float) {
        super.createSmallDescription(info, width, height)

        val pad = 3f
        val opad = 10f
        val pf = Global.getSector().playerFaction
        val currentlyRaiding = currentAction == raidAction
        val hostile = faction.isHostileTo(params.raidParams.allowedTargets[0].faction)
        if (!hostile && !isEnding && !isSucceeded) {
            // checkbox to toggle
            resistCheckbox = info?.addAreaCheckbox(PoliceHostileActivityFactor.getString("intelButtonTextResist"), BUTTON_TOGGLE_RESIST, pf.baseUIColor,
                pf.darkUIColor, pf.brightUIColor, width, 24f, opad)
            resistCheckbox?.isChecked = resist

            // don't allow taking back the resist order
            if (currentlyRaiding && resist) {
                resistCheckbox?.isEnabled = false
                val tooltip : String = PoliceHostileActivityFactor.getString("intelDescNoChange")
                info?.addTooltipTo(NexUtilsGUI.createSimpleTextTooltip(tooltip, 300f), resistCheckbox, TooltipMakerAPI.TooltipLocation.BELOW)
            }
        }
        if (!isEnding && !isSucceeded) {
            if (resist || hostile) {
                info?.addPara(PoliceHostileActivityFactor.getString("intelDescResist"), pad)
            } else {
                info?.addPara(PoliceHostileActivityFactor.getString("intelDescNoResist"), pad)
            }
        }
    }

    protected fun shouldMakeHostile() : Boolean {
        val hostile = faction.isHostileTo(params.raidParams.allowedTargets[0].faction)
        return currentAction == raidAction && (resist || hostile)
    }

    protected fun makeFleetsHostile() {
        //Global.getLogger(this.javaClass).info("Making fleets hostile to player")
        for (fleet in fleets) {
            makeFleetHostile(fleet)
        }
    }

    protected fun makeFleetHostile(fleet : CampaignFleetAPI) {
        Misc.setFlagWithReason(fleet.memory, MemFlags.MEMORY_KEY_MAKE_HOSTILE + "_" + Factions.PLAYER,
            "nex_resistPoliceRaid", true, -1f)
        Misc.setFlagWithReason(fleet.memory, MemFlags.MEMORY_KEY_MAKE_HOSTILE, "nex_resistPoliceRaid", true, -1f)
    }

    override fun notifyActionFinished(action: FGAction?) {
        super.notifyActionFinished(action)
        // on completing travel action, mark police fleets as hostile if we're resisting them
        if (shouldMakeHostile()) {
            makeFleetsHostile()
        }
    }

    override fun buttonPressConfirmed(buttonId: Any?, ui: IntelUIAPI?) {
        if (buttonId == BUTTON_TOGGLE_RESIST) {
            resist = resistCheckbox?.isChecked ?: false
            ui?.updateUIForItem(this)
            //Global.getLogger(this.javaClass).info("Current action: " + currentAction + "; " + (currentAction == raidAction))
            if (shouldMakeHostile()) makeFleetsHostile()
            return
        }
        super.buttonPressConfirmed(buttonId, ui)
    }

    /*
    override fun createPayloadAction(): GenericPayloadAction? {
        return FGPoliceRaidAction(params.raidParams, params.payloadDays)
    }

    open class FGPoliceRaidAction(params: FGRaidParams?, raidDays: Float) : FGRaidAction(params, raidDays) {
        override fun performRaid(fleet: CampaignFleetAPI?, market: MarketAPI?) {
            super.performRaid(fleet, market)
            market?.isFreePort = false
        }
    }
     */
}