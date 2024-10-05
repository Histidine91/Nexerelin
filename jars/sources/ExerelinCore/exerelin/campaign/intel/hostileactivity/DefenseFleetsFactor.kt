package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.FactionAPI
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.battle.NexBattleAutoresolverPlugin
import exerelin.campaign.intel.merc.MercContractIntel
import exerelin.campaign.intel.merc.MercSectorManager
import exerelin.utilities.NexUtilsFaction
import org.lazywizard.lazylib.MathUtils
import java.awt.Color
import java.util.*

// factors: def fleet request (use largest), PSF (use sqrt(total)), AIM package
// add a Remnant factor elsewhere?
class DefenseFleetsFactor(intel: HostileActivityEventIntel?) : BaseHostileActivityFactor(intel) {

    companion object {
        const val DEBUG_MODE = false
    }

    override fun getDesc(intel: BaseEventIntel?): String {
        return NexHostileActivityManager.getString("defensiveFleetName");
    }

    override fun getProgressStr(intel: BaseEventIntel?): String? {
        return ""
    }

    override fun getDescColor(intel: BaseEventIntel?): Color {
        return if (getProgress(intel) >= 0) {
            Misc.getGrayColor()
        } else Global.getSector().playerFaction.baseUIColor
    }

    override fun getMainRowTooltip(): TooltipCreator {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any) {
                val opad = 10f
                tooltip.addPara(NexHostileActivityManager.getString("defensiveFleetDesc"), 0f)
            }
        }
    }

    override fun shouldShow(intel: BaseEventIntel): Boolean {
        return getProgress(intel) != 0
    }

    override fun createFleet(system: StarSystemAPI, random: Random): CampaignFleetAPI? {
        //Global.getLogger(this.javaClass).info("Trying to spawn AIM fleet in/near ${system.nameWithLowercaseTypeShort}")

        var intel : MercContractIntel? = MercSectorManager.getInstance().pickPatrolCompany(random) ?: return null
        var market = NexUtilsFaction.getPlayerMarkets(true, false).firstOrNull()
        intel!!.init(market, true)

        var fleet = intel.offeredFleet
        fleet.name = intel.def.name
        fleet.isNoFactionInName = true
        for (member in fleet.fleetData.membersListCopy) {
            member.repairTracker.cr = member.repairTracker.maxCR
        }

        // add extra ships with 75-100 FP
        val strength = random.nextInt(25).toFloat() + 75
        val params = FleetParamsV3(system.location, intel.def.factionIdForShipPick, null, FleetTypes.PATROL_LARGE,
        strength, strength/10, strength/10, 0f, 0f, 5f, 0.25f)
        //if (intel.def.averageSMods != null) params.averageSMods = intel.def.averageSMods - 1  // don't want S-mods on the generics
        params.noCommanderSkills = true
        params.ignoreMarketFleetSizeMult = true
        params.modeOverride = FactionAPI.ShipPickMode.PRIORITY_THEN_ALL

        var tempFleet : CampaignFleetAPI? = FleetFactoryV3.createFleet(params);
        tempFleet?.inflateIfNeeded()
        tempFleet?.fleetData?.sort()
        tempFleet?.flagship?.isFlagship = false

        for (member : FleetMemberAPI in tempFleet?.fleetData?.membersListCopy!!) {
            fleet.fleetData.addFleetMember(member)
            if (member.captain?.isDefault == false) fleet.fleetData.addOfficer(member.captain)
        }
        fleet.fleetData.setSyncNeeded()
        fleet.fleetData.syncIfNeeded()
        fleet.setFaction(Factions.PLAYER, false)
        fleet.memoryWithoutUpdate[MemFlags.MEMORY_KEY_PATROL_FLEET] = true
        fleet.memoryWithoutUpdate["\$nex_HAmercFleet"] = true
        fleet.memoryWithoutUpdate["\$nex_HAmerc_companyId"] = intel.def.id
        fleet.memoryWithoutUpdate.set(NexBattleAutoresolverPlugin.MEM_KEY_STRENGTH_MULT, 1.25f);
        //Global.getLogger(this.javaClass).info("Spawned AIM fleet ${fleet.nameWithFaction} in/near ${system.nameWithLowercaseTypeShort}")

        return fleet;
    }

    override fun getSpawnFrequency(system: StarSystemAPI?): Float {
        var pack : MercPackageIntel? = MercPackageIntel.getInstance();
        if (pack == null || pack.isEnding || pack.isEnded) return 0f;
        //Global.getLogger(this.javaClass).info("Checking spawn frequency")

        var sizeSum = 0f;
        for (market : MarketAPI in Global.getSector().economy.getMarkets(system)) {
            if (!market.faction.isPlayerFaction && !market.isPlayerOwned) continue;
            sizeSum += market.size;
        }
        if (DEBUG_MODE) sizeSum *= 1000

        return sizeSum/4;
    }

    override fun getSpawnInHyperProbability(system: StarSystemAPI?): Float {
        return 0.25f
    }


    override fun getStayInHyperProbability(system: StarSystemAPI?): Float {
        return 0.25f
    }

    override fun getMaxNumFleets(system: StarSystemAPI?): Int {
        return 1
    }
}