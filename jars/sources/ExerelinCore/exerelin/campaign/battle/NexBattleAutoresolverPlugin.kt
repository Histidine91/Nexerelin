package exerelin.campaign.battle

import com.fs.starfarer.api.campaign.BattleAPI
import com.fs.starfarer.api.impl.campaign.BattleAutoresolverPluginImpl
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.BattleAutoresolverPluginImpl.FleetMemberAutoresolveData
import com.fs.starfarer.api.campaign.CampaignFleetAPI

open class NexBattleAutoresolverPlugin(battle: BattleAPI?) : BattleAutoresolverPluginImpl(battle) {

    companion object {
        const val MEM_KEY_STRENGTH_MULT = "\$nex_autoresolve_strMult";
    }

    override fun computeDataForMember(member: FleetMemberAPI): FleetMemberAutoresolveData {
        val data = super.computeDataForMember(member)

        // MODIFIED
        var fleetMult = member?.fleetData?.fleet?.memoryWithoutUpdate?.getFloat(MEM_KEY_STRENGTH_MULT) ?: 1f

        // MODIFIED
        data.strength *= fleetMult
        return data
    }
}