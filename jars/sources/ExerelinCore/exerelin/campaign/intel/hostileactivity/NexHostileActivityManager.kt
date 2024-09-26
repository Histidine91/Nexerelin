package exerelin.campaign.intel.hostileactivity

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.impl.campaign.ids.Factions
import com.fs.starfarer.api.impl.campaign.intel.events.*
import exerelin.utilities.NexUtilsFaction
import exerelin.utilities.StringHelper

class NexHostileActivityManager : HostileActivityManager() {

    init {
        val ha = HostileActivityEventIntel.get()
        if (ha != null) {
            addCustomFactorsIfNeeded(ha)
            addDefenseFleetFactorsIfNeeded(ha)
            addCommissionFactorsIfNeeded(ha)
        }
    }

    override fun advance(amount: Float) {
        tracker.advance(amount)
        if (tracker.intervalElapsed()) {
            val playerHasColonies = !NexUtilsFaction.getPlayerMarkets(INCLUDE_AUTONOMOUS, false).isEmpty()
            if (HostileActivityEventIntel.get() == null && playerHasColonies) {
                val ha = NexHostileActivityEventIntel()
                purgeOldListeners()
                replaceVanillaOverrideActivities(ha)

            } else if (HostileActivityEventIntel.get() != null && !playerHasColonies) {
                HostileActivityEventIntel.get().endImmediately()
            }

            val ha = HostileActivityEventIntel.get()
            if (ha != null) {
                addCustomFactorsIfNeeded(ha)
                addDefenseFleetFactorsIfNeeded(ha)
                addCommissionFactorsIfNeeded(ha)
            }
        }
    }

    companion object {
        const val INCLUDE_AUTONOMOUS = true // might as well since they're counted in StandardPirateActivityCause2

        @JvmStatic
        fun getString(id : String) : String {
            return StringHelper.getString("nex_hostileActivity", id, false);
        }
        @JvmStatic
        fun getString(id : String, ucFirst : Boolean) : String {
            return StringHelper.getString("nex_hostileActivity", id, ucFirst);
        }

        /*
         runcode import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
         exerelin.campaign.intel.hostileactivity.NexHostileActivityManager.addDefenseFleetFactorsIfNeeded(HostileActivityEventIntel.get())
         */
        @JvmStatic
        fun addDefenseFleetFactorsIfNeeded(ha: HostileActivityEventIntel) {
            val alreadyHas = hasFactorOfClass(ha, DefenseFleetsFactor::class.java)
            if (alreadyHas) return
            var defFactor = DefenseFleetsFactor(ha);
            ha.addFactor(defFactor);
            ha.addActivity(defFactor, DefenseFleetActivityCause(ha))
            ha.addActivity(defFactor, SpecialTaskGroupActivityCause(ha))
            ha.addActivity(defFactor, MercPackageActivityCause(ha))
        }

        @JvmStatic
        fun addCommissionFactorsIfNeeded(ha: HostileActivityEventIntel) {
            //val alreadyHas = hasFactorOfClass(ha, DefenseFleetsFactor::class.java)

            val pirateFactor = ha.getActivityOfClass(PirateHostileActivityFactor::class.java)
            val wantAddPC = pirateFactor != null && pirateFactor?.getCauseOfClass(OutlawCommissionActivityCause::class.java) == null
            if (wantAddPC) {
                pirateFactor.addCause(OutlawCommissionActivityCause(ha, Factions.PIRATES, "pirate"))
            }

            val patherFactor = ha.getActivityOfClass(LuddicPathHostileActivityFactor::class.java)
            val wantAddLPC = patherFactor != null && patherFactor?.getCauseOfClass(OutlawCommissionActivityCause::class.java) == null
            if (wantAddLPC) {
                patherFactor.addCause(OutlawCommissionActivityCause(ha, Factions.LUDDIC_PATH, "lp"))
            }
        }

        @JvmStatic
        fun addCustomFactorsIfNeeded(ha: HostileActivityEventIntel) {
            //Global.getLogger(this.javaClass).info("Preparing to add custom factors")
            val alreadyHas = hasFactorOfClass(ha, PoliceHostileActivityFactor::class.java)
            if (alreadyHas) return
            ha.addActivity(PoliceHostileActivityFactor(ha), PoliceFreePortActivityCause(ha))

            val remnant = ha.getActivityOfClass(RemnantHostileActivityFactor::class.java)
            if (remnant != null) ha.addActivity(remnant, RemnantFriendlyCause(ha));
        }

        @JvmStatic
        fun replaceVanillaOverrideActivities(ha: HostileActivityEventIntel) {
            ha.removeActivityOfClass(PerseanLeagueHostileActivityFactor::class.java)
            ha.removeActivityOfClass(LuddicChurchHostileActivityFactor::class.java)
            ha.removeActivityOfClass(SindrianDiktatHostileActivityFactor::class.java)
            Global.getSector().listenerManager.removeListenerOfClass(
                PerseanLeagueHostileActivityFactor::class.java
            )
            Global.getSector().listenerManager.removeListenerOfClass(
                LuddicChurchHostileActivityFactor::class.java
            )
            Global.getSector().listenerManager.removeListenerOfClass(
                SindrianDiktatHostileActivityFactor::class.java
            )

            ha.addActivity(NexPerseanLeagueHostileActivityFactor(ha), StandardPerseanLeagueActivityCause(ha))
            ha.addActivity(NexLuddicChurchHostileActivityFactor(ha), LuddicChurchStandardActivityCause(ha))
            ha.addActivity(NexSindrianDiktatHostileActivityFactor(ha), NexSindrianDiktatStandardActivityCause(ha))
        }

        @JvmStatic
        fun purgeOldListeners() {
            Global.getSector().listenerManager.removeListenerOfClass(NexPerseanLeagueHostileActivityFactor::class.java)
            Global.getSector().listenerManager.removeListenerOfClass(NexLuddicChurchHostileActivityFactor::class.java)
            Global.getSector().listenerManager.removeListenerOfClass(NexSindrianDiktatHostileActivityFactor::class.java)
            Global.getSector().listenerManager.removeListenerOfClass(PoliceHostileActivityFactor::class.java)
        }

        @JvmStatic
        fun hasFactorOfClass(ha: HostileActivityEventIntel?, factorClass: Class<*>): Boolean {
            if (ha == null) return false
            for (factor in ha.factors) {
                if (factorClass.isInstance(factor)) return true
            }
            return false
        }
    }
}