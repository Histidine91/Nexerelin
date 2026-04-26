package exerelin.campaign.fleets

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.campaign.CampaignEventListener.FleetDespawnReason
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.SectorEntityToken.VisibilityLevel
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.impl.campaign.fleets.RouteManager
import com.fs.starfarer.api.util.Misc
import exerelin.campaign.fleets.utils.NexRouteManagerListener
import exerelin.utilities.NexUtilsFleet
import exerelin.utilities.ReflectionUtils

open class NexRouteManager : RouteManager() {

    companion object {
        const val DEBUG_MODE = true
        const val MEM_KEY_LOCATION_FORCE_SPAWN = "\$nex_forceRouteSpawn"
        const val DATA_KEY_NO_PROCESS_DAMAGE = "\$nex_noProcessDamage"
        const val USE_FORCE_SPAWN = true

        @JvmStatic val log = Global.getLogger(NexRouteManager.javaClass)

        @JvmStatic fun reportRouteAdded(route : RouteData) {
            for (x in Global.getSector().listenerManager.getListeners(NexRouteManagerListener::class.java))
            {
                x.reportRouteAdded(route)
            }
        }

        @JvmStatic fun reportRouteRemoved(route : RouteData) {
            for (x in Global.getSector().listenerManager.getListeners(NexRouteManagerListener::class.java))
            {
                x.reportRouteRemoved(route)
            }
        }

        @JvmStatic fun reportRouteFleetSpawned(fleet: CampaignFleetAPI, route : RouteData) {
            for (x in Global.getSector().listenerManager.getListeners(NexRouteManagerListener::class.java))
            {
                x.reportRouteFleetSpawned(fleet, route)
            }
        }

        @JvmStatic fun reportRouteFleetDespawned(fleet: CampaignFleetAPI, route : RouteData) {
            for (x in Global.getSector().listenerManager.getListeners(NexRouteManagerListener::class.java))
            {
                x.reportRouteFleetDespawned(fleet, route)
            }
        }

        @JvmStatic fun replaceExistingRouteManager() {
            val nexMan = NexRouteManager()
            val existing = getInstance()
            if (existing is NexRouteManager) return
            log.info("Swapping out route manager")
            var existingRoutes = ReflectionUtils.get(existing, "routes", null, true) as List<RouteManager.RouteData>
            for (existingRoute : RouteData in existingRoutes) {
                val newRoute = NexRouteData(existingRoute)
                nexMan.routes.add(newRoute)
                nexMan.addToMap(newRoute)
                if (existingRoute.activeFleet != null) {
                    nexMan.fleetToRoute[existingRoute.activeFleet] = newRoute
                    existingRoute.activeFleet.removeEventListener(existing)
                    existingRoute.activeFleet.addEventListener(nexMan)
                }
            }
            for (route in nexMan.routes) {
                nexMan.addToMap(route)
            }
            Global.getSector().memoryWithoutUpdate[KEY] = nexMan
        }
    }

    var fleetToRoute = HashMap<CampaignFleetAPI, RouteData?>()
        protected set

    open class NexRouteData : RouteData {
        var dataStore : Map<String, Object?> = HashMap()
        var forceSpawn = false
            set(force) {
                this.forceSpawn = force
                if (force && activeFleet != null) {
                    activeFleet.isNoAutoDespawn = true
                }
            }

        fun setDaysSinceSeenByPlayer(days : Float) {
            daysSinceSeenByPlayer = days
        }
        fun setActiveFleet(fleet : CampaignFleetAPI?) {
            activeFleet = fleet
        }
        fun setSpawner(spawner : RouteFleetSpawner) {
            this.spawner = spawner
        }

        constructor(source: String?, market: MarketAPI?, seed: Long?, extra: OptionalFleetData?) : super(
            source,
            market,
            seed,
            extra
        )

        constructor(existing : RouteData) : super(existing.source, existing.market, existing.seed, existing.extra) {
            this.delay = existing.delay
            this.timestamp = existing.timestamp
            this.segments = existing.segments
            this.activeFleet = existing.activeFleet
            this.daysSinceSeenByPlayer = existing.daysSinceSeenByPlayer
            this.elapsed = existing.elapsed
            this.custom = existing.custom
            this.current = existing.current
            this.spawner = existing.spawner
        }
    }

    protected fun readResolve(): Any {
        sourceToRoute = LinkedHashMap()
        for (route in routes) {
            addToMap(route)
        }
        return this
    }

    override fun addRoute(
        source: String?,
        market: MarketAPI?,
        seed: Long?,
        extra: OptionalFleetData?,
        spawner: RouteFleetSpawner?,
        custom: Any?
    ): NexRouteData {
        return addRoute(source, market, seed, extra, spawner, custom, null)
    }

    fun addRoute(
        source: String?,
        market: MarketAPI?,
        seed: Long?,
        extra: OptionalFleetData?,
        spawner: RouteFleetSpawner?,
        custom: Any?,
        dataStore: Map<String, Object?>? = null
    ): NexRouteData {
        routesByLocation = null

        val route = NexRouteData(source, market, seed, extra)
        route.spawner = spawner
        route.custom = custom
        route.timestamp = Global.getSector().clock.timestamp
        if (dataStore != null) route.dataStore = dataStore
        routes.add(route)
        addToMap(route)
        //routes.clear();

        reportRouteAdded(route)
        return route
    }

    override fun spawnAndDespawn() {
        val player = Global.getSector().playerFleet ?: return

        //System.out.println("Num routes: " + routes.size());
        var add = 0
        var sub = 0
        for (data in ArrayList(routes)) {
            if (data.activeFleet != null && data.activeFleet.containingLocation === player.containingLocation) {
                val level = data.activeFleet.visibilityLevelToPlayerFleet
                if ((level == VisibilityLevel.COMPOSITION_AND_FACTION_DETAILS ||
                            level == VisibilityLevel.COMPOSITION_DETAILS) &&
                    data.activeFleet.wasMousedOverByPlayer()
                ) {
                    ReflectionUtils.set(data, "daysSinceSeenByPlayer", 0f, true)
                }
            }

            if (shouldDespawn(data)) {
                despawnRoute(data)
                sub++
                continue
            }

            if (shouldSpawn(data)) {
                if (spawnRoute(data))
                    add++
                continue
            }
        }
    }

    fun spawnRoute(data: RouteData) : Boolean {
        ReflectionUtils.set(data, "activeFleet", data.spawner.spawnFleet(data), true)
        if (data.activeFleet != null) {
            data.activeFleet.addEventListener(this)
            fleetToRoute[data.activeFleet] = data
            if (data is NexRouteData && data.forceSpawn) data.activeFleet.isNoAutoDespawn = true
            reportRouteFleetSpawned(data.activeFleet, data)
            return true
        } else {
            data.expire()
            return false
        }
    }

    fun despawnRoute(data: RouteData) {
        var fleet = data.activeFleet
        data.spawner.reportAboutToBeDespawnedByRouteManager(data)
        data.activeFleet.despawn(FleetDespawnReason.PLAYER_FAR_AWAY, null)
        if (data.activeFleet.containingLocation != null) {
            data.activeFleet.containingLocation.removeEntity(data.activeFleet)
        }
        ReflectionUtils.set(data, "activeFleet", null, true)
        fleetToRoute.remove(data.activeFleet)
        reportRouteFleetDespawned(fleet, data)
    }

    override fun removeFromMap(data: RouteData?) {
        super.removeFromMap(data)
        reportRouteRemoved(data!!)
    }

    override fun shouldSpawn(data: RouteData): Boolean {
        if (data.delay > 0) return false
        if (data.activeFleet != null) return false
        if (data is NexRouteData && data.forceSpawn) return true
        if (data.current?.isInSystem == true && data.current.from.containingLocation.memoryWithoutUpdate.getBoolean(MEM_KEY_LOCATION_FORCE_SPAWN))
            return true

        return super.shouldSpawn(data)
    }

    override fun shouldDespawn(data: RouteData): Boolean {
        if (data.activeFleet == null) return false
        if (data.activeFleet.containingLocation.memoryWithoutUpdate.getBoolean(MEM_KEY_LOCATION_FORCE_SPAWN)) return false
        return super.shouldDespawn(data)
    }

    override fun reportBattleOccurred(fleet: CampaignFleetAPI?, primaryWinner: CampaignFleetAPI?, battle: BattleAPI?) {
        if (fleet == null) return
        var route = fleetToRoute[fleet] ?: return
        if (route is NexRouteData && true.equals(route.dataStore.get(DATA_KEY_NO_PROCESS_DAMAGE))) return

        var baseFP = NexUtilsFleet.getStartingFP(fleet)
        if (baseFP <= 0) return

        var dam = Misc.getSnapshotFPLost(fleet)
        if (route.extra.damage == null) route.extra.damage = 0f
        route.extra.damage += dam/baseFP
        route.extra.damage.coerceAtMost(1f)
        if (DEBUG_MODE) log.info(String.format("Fleet %s took damage %s vs. base fp %s (%s), now at %s damage",
            fleet.nameWithFaction, dam, baseFP, Math.round(100*dam/baseFP), String.format("%.1f", route.extra.damage * 100)))
    }

    override fun reportFleetDespawnedToListener(fleet: CampaignFleetAPI?, reason: FleetDespawnReason?, param: Any?) {
        super.reportFleetDespawnedToListener(fleet, reason, param)
        fleetToRoute.remove(fleet)
    }
}