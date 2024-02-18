package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.econ.RecentUnrest
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.RaidType
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import exerelin.utilities.NexUtilsMarket

class PoliceRaidFGI(params: GenericRaidParams?) : GenericRaidFGI(params)
{
    companion object {
        const val MEM_KEY_FLEET = "\$nex_HA_policeRaid_fleet"
        const val MEM_KEY_TARGET_MARKET = "\$nex_HA_policeRaid_targetMarket"
        const val MEM_KEY_TARGET_ON_OR_AT = "\$nex_HA_policeRaid_targetOnOrAt"
        const val CRIMINAL_DEF_PER_INDUSTRY = 100f
    }

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

    override fun hasCustomRaidAction(): Boolean {
        val market = params.raidParams.allowedTargets.first()
        val anyHostile = this.fleets.any { it.isHostileTo(market.primaryEntity) }
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

        val durMult = 1     // Global.getSettings().getFloat("punitiveExpeditionDisruptDurationMult")
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