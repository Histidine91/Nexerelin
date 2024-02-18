package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode
import com.fs.starfarer.api.campaign.econ.Industry
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.NPCHassler
import com.fs.starfarer.api.impl.campaign.ids.Commodities
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes
import com.fs.starfarer.api.impl.campaign.ids.Industries
import com.fs.starfarer.api.impl.campaign.intel.events.*
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.HAERandomEventData
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel.FGIEventListener
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers
import com.fs.starfarer.api.impl.campaign.rulecmd.Nex_CargoScan
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.WeightedRandomPicker
import exerelin.campaign.SectorManager
import exerelin.campaign.diplomacy.DiplomacyTraits
import exerelin.utilities.NexUtils
import exerelin.utilities.NexUtilsFaction
import exerelin.utilities.NexUtilsReputation
import exerelin.utilities.StringHelper
import java.awt.Color
import java.util.*

open class PoliceHostileActivityFactor(intel: HostileActivityEventIntel?) : BaseHostileActivityFactor(intel), FGIEventListener
{
    companion object {
        const val MEM_KEY_DEFEATED_EXPEDITION : String = "\$nex_HA_defeatedPoliceExpedition"
        const val MIN_ACTIVITY_PER_COLONY = 5
        @JvmStatic val CRIMINAL_INDUSTRIES = HashSet<String>()
        const val HASSLE_REASON = "nex_police"
        const val REP_GAIN_ON_VICTORY = 0.3f

        init {
            CRIMINAL_INDUSTRIES.add("IndEvo_pirateHaven")
            CRIMINAL_INDUSTRIES.add("HMI_market")
            CRIMINAL_INDUSTRIES.add("prv_rb_syndicate_network")
            CRIMINAL_INDUSTRIES.add("yrex_crime")
            CRIMINAL_INDUSTRIES.add("yrex_privateers")
        }

        @JvmStatic
        fun isDefeatedExpedition(): Boolean {
            //if (true) return true;
            return Global.getSector().playerMemoryWithoutUpdate.getBoolean(MEM_KEY_DEFEATED_EXPEDITION)
        }

        @JvmStatic
        fun setDefeatedExpedition(value: Boolean) {
            Global.getSector().playerMemoryWithoutUpdate[MEM_KEY_DEFEATED_EXPEDITION] = value
        }

        @JvmStatic
        fun getFreePortScore(market : MarketAPI) : Float {
            if (!market.isFreePort) return 0f;
            var score = market.size.toFloat()
            var drugs = market.getCommodityData(Commodities.DRUGS).maxSupply
            drugs = drugs.coerceAtLeast(market.getCommodityData(Commodities.DRUGS).maxDemand)
            if (drugs > 1) score += drugs
            var guns = market.getCommodityData(Commodities.HAND_WEAPONS).maxSupply
            if (guns > 1) score += guns

            if (score < MIN_ACTIVITY_PER_COLONY) return 0f;

            return score
        }

        @JvmStatic
        fun isRaidableCriminalIndustry(industry : Industry) : Boolean {
            if (industry.spec.hasTag(Industries.TAG_UNRAIDABLE)) return false
            if (industry.spec.hasTag(Industries.TAG_SPACEPORT)) return true
            if (CRIMINAL_INDUSTRIES.contains(industry.id)) return true

            var drugs = industry.getSupply(Commodities.DRUGS).quantity.modifiedInt
            drugs = drugs.coerceAtLeast(industry.getDemand(Commodities.DRUGS).quantity.modifiedInt)
            if (drugs > 0) return true

            return false
        }

        @JvmStatic
        fun getString(subId : String) : String {
            return StringHelper.getString("nex_hostileActivity", "police_$subId")
        }

        @JvmStatic
        fun getEligibleFactions() : List<String> {
            val list = ArrayList<String>()
            list.addAll(SectorManager.getLiveFactionIdsCopy().filter { DiplomacyTraits.hasTrait(it, DiplomacyTraits.TraitIds.LAW_AND_ORDER) })
            return list
        }
    }

    protected var raid : FleetGroupIntel? = null

    override fun getProgress(intel: BaseEventIntel?): Int {
        return if (getEligibleFactions().isEmpty()) {
            0
        } else super.getProgress(intel)
    }

    override fun getDesc(intel: BaseEventIntel?): String? {
        return getString("factorName")
    }

    override fun getNameForThreatList(first: Boolean): String? {
        return getString("factorName")
    }

    override fun getDescColor(intel: BaseEventIntel?): Color? {
        return if (getProgress(intel) <= 0) {
            Misc.getGrayColor()
        } else Global.getSector().getFaction(Factions.INDEPENDENT).baseUIColor
    }

    override fun getMainRowTooltip(intel: BaseEventIntel?): TooltipCreator? {
        return object : BaseFactorTooltip() {
            override fun createTooltip(tooltip: TooltipMakerAPI, expanded: Boolean, tooltipParam: Any) {
                val opad = 10f
                val str = getString("factorTooltip")
                tooltip.addPara(str, 0f
                )
            }
        }
    }

    override fun getMaxNumFleets(system: StarSystemAPI?): Int {
        return Global.getSettings().getInt("nex_ha_policeMaxFleets")
    }

    override fun createFleet(system: StarSystemAPI, random: Random): CampaignFleetAPI? {

        //float f = intel.getMarketPresenceFactor(system);
        var maxSize = 0
        for (curr: MarketAPI in Misc.getMarketsInLocation(system, Factions.PLAYER)) {
            maxSize = Math.max(curr.size, maxSize)
        }

        //int difficulty = 0 + (int) Math.max(1f, Math.round(f * 6f));
        var difficulty = maxSize
        difficulty += random.nextInt(3)
        if (difficulty > 8) difficulty = 8

        val m = FleetCreatorMission(random)
        m.beginFleet()
        val loc = system.location
        var factionId = Factions.MERCENARY
        if (Math.random() > 0.35f) factionId = NexUtils.getRandomListElement(getEligibleFactions())
        m.createStandardFleet(difficulty, factionId, loc)
        m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.SMOD_1)
        m.triggerSetFleetQuality(HubMissionWithTriggers.FleetQuality.HIGHER)
        m.triggerSetFleetType(FleetTypes.INVESTIGATORS)
        m.triggerFleetSetName(getString("fleetName"))
        m.triggerSetPatrol()
        m.triggerSetFleetHasslePlayer(HASSLE_REASON)
        m.triggerSetFleetFlag("\$nex_HA_police")
        m.triggerFleetAllowLongPursuit()
        m.triggerMakeNoRepImpact()
        m.triggerSetFleetMaxShipSize(3)
        m.triggerSetFleetMemoryValue(Nex_CargoScan.MEM_KEY_FACTION_ID_FOR_ILLEGAL, Factions.HEGEMONY)

        val fleet = m.createFleet()
        if (fleet != null) {
            fleet.setFaction(Factions.INDEPENDENT, true)
            val hassle = NPCHassler(fleet, system)
            hassle.params.crDamageMult = 0f
            fleet.addScript(hassle)
        }
        return fleet
    }


    override fun addBulletPointForEvent(
        intel: HostileActivityEventIntel?, stage: EventStageData?, info: TooltipMakerAPI,
        mode: ListInfoMode?, isUpdate: Boolean, tc: Color?, initPad: Float
    ) {
        val c = Global.getSector().getFaction(Factions.INDEPENDENT).baseUIColor
        info.addPara(getString("eventBullet"), initPad, tc, c, getString("eventBulletHighlight"))
    }

    override fun addBulletPointForEventReset(
        intel: HostileActivityEventIntel?, stage: EventStageData?, info: TooltipMakerAPI,
        mode: ListInfoMode?, isUpdate: Boolean, tc: Color?, initPad: Float
    ) {
        info.addPara(getString("eventAvertedBullet"), tc, initPad)
    }

    override fun addStageDescriptionForEvent(
        intel: HostileActivityEventIntel?,
        stage: EventStageData,
        info: TooltipMakerAPI
    ) {
        var small = 8f
        val opad = 10f
        val h = Misc.getHighlightColor()
        val bad = Misc.getNegativeHighlightColor()
        val pirate = Global.getSector().getFaction(Factions.PIRATES).baseUIColor
        var label = info.addPara(getString("eventDesc1"), small)
        label.setHighlight(*getString("eventDesc1Highlight").split(", ").toTypedArray())
        label.setHighlightColors(bad, pirate)
        label = info.addPara(getString("eventDesc2"), opad)
        label.setHighlight(*getString("eventDesc2Highlight").split(", ").toTypedArray())
        label.setHighlightColors(bad, h, h)
        label = info.addPara(
            getString("eventDesc3"),
            opad, pirate, getString("eventDesc3Highlight")
        )

        stage.beginResetReqList(info, true, StringHelper.getString("crisis"), opad)
        info.addPara(getString("eventResetReq"), 0f)
        stage.endResetReqList(info, false, StringHelper.getString("crisis"), -1, -1)
        addBorder(info, Global.getSector().getFaction(Factions.INDEPENDENT).baseUIColor)
    }

    override fun getEventStageIcon(intel: HostileActivityEventIntel?, stage: EventStageData?): String? {
        return Global.getSector().getFaction(Factions.INDEPENDENT).crest
    }

    override fun getStageTooltipImpl(intel: HostileActivityEventIntel?, stage: EventStageData): TooltipCreator? {
        return if (stage.id === HostileActivityEventIntel.Stage.HA_EVENT) {
            getDefaultEventTooltip("[temp] Sectorpol expedition", intel, stage)
        } else null
    }

    override fun getEventFrequency(intel: HostileActivityEventIntel?, stage: EventStageData): Float {
        if (stage.id === HostileActivityEventIntel.Stage.HA_EVENT) {
            if (isDefeatedExpedition()) {
                return 0f
            }
            if (raid != null) {
                return 0f
            }
            val target = pickTargetMarket()
            val source = pickSourceMarket()
            if (target != null && source != null) {
                return 7.5f
            }
        }
        return 0f
    }

    override fun rollEvent(intel: HostileActivityEventIntel, stage: EventStageData) {
        val data = HAERandomEventData(this, stage)
        stage.rollData = data
        intel.sendUpdateIfPlayerHasIntel(data, false)
    }

    override fun fireEvent(intel: HostileActivityEventIntel?, stage: EventStageData): Boolean {
        val target = pickTargetMarket()
        val source = pickSourceMarket()
        if (source == null || target == null) {
            return false
        }
        stage.rollData = null
        return startExpedition(source, target, stage, getRandomizedStageRandom(3))
    }


    fun pickTargetMarket(): MarketAPI? {
        val picker = WeightedRandomPicker<MarketAPI>(randomizedStageRandom)
        for (market: MarketAPI in Misc.getPlayerMarkets(false)) {
            if (market.starSystem == null) continue
            var w = getFreePortScore(market)
            if (w <= 0) continue
            w = w * w * w
            picker.add(market, w)
        }
        return picker.pick()
    }

    fun pickSourceMarket(): MarketAPI? {
        val validFactions = HashSet(getEligibleFactions())
        val picker = WeightedRandomPicker<MarketAPI>(getRandomizedStageRandom(7))
        for (market in Global.getSector().economy.marketsCopy) {
            if (!validFactions.contains(market.factionId)) continue

            // look for a working base
            var b = market.getIndustry(Industries.MILITARYBASE)
            if (b == null) b = market.getIndustry(Industries.HIGHCOMMAND)
            if (b == null || b.isDisrupted || !b.isFunctional) {
                continue
            }

            picker.add(market, market.size.toFloat())
        }
        return picker.pick()
    }

    fun startExpedition(source: MarketAPI, target: MarketAPI, stage: EventStageData?, random: Random): Boolean {
        val params = GenericRaidParams(Random(random.nextLong()), true)
        params.factionId = Factions.INDEPENDENT
        params.source = source
        params.prepDays = 7f + random.nextFloat() * 14f
        params.payloadDays = 180f
        params.makeFleetsHostile = false
        params.repImpact = HubMissionWithTriggers.ComplicationRepImpact.NONE

        params.raidParams.where = target.starSystem
        //params.raidParams.type = FGRaidAction.FGRaidType.SEQUENTIAL // only one target so maybe not needed?
        params.raidParams.raidsPerColony = 1
        params.raidParams.allowedTargets.add(target)
        params.raidParams.allowNonHostileTargets = true // lets the raid happen even if independents are not hostile
        for (industry in target.industries) {
            if (isRaidableCriminalIndustry(industry)) {
                params.raidParams.disrupt.add(industry.spec.id)
            }
        }

        params.noun = getString("raidNoun")
        params.forcesNoun = getString("raidForcesNoun")
        params.style = FleetCreatorMission.FleetStyle.QUALITY
        params.fleetSizes.add(8)

        // and a few smaller picket forces
        params.fleetSizes.add(5)
        params.fleetSizes.add(4)
        params.fleetSizes.add(3)
        params.fleetSizes.add(3)
        val raid = PoliceRaidFGI(params)
        raid.listener = this
        Global.getSector().intelManager.addIntel(raid)
        return true
    }

    override fun reportFGIAborted(intel: FleetGroupIntel?) {
        setDefeatedExpedition(true)
        for (factionId in SectorManager.getLiveFactionIdsCopy().filter { NexUtilsFaction.isPirateFaction(it) }) {
            NexUtilsReputation.adjustPlayerReputation(Global.getSector().getFaction(factionId), REP_GAIN_ON_VICTORY, null, null)
        }
    }
}