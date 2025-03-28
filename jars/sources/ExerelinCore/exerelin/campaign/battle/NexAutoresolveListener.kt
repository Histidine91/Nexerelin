package exerelin.campaign.battle

import com.fs.starfarer.api.campaign.listeners.CoreAutoresolveListener
import com.fs.starfarer.api.impl.campaign.BattleAutoresolverPluginImpl

class NexAutoresolveListener : CoreAutoresolveListener {

    companion object {
        const val MEM_KEY_STRENGTH_MULT = "\$nex_autoresolve_strMult";
    }

    override fun modifyDataForFleet(data: BattleAutoresolverPluginImpl.FleetAutoresolveData?) {
        var fleet = data!!.fleet
        if (!fleet.memoryWithoutUpdate.contains(MEM_KEY_STRENGTH_MULT)) return
        var strMult = fleet.memoryWithoutUpdate.getFloat(MEM_KEY_STRENGTH_MULT)

        for (member in data!!.members) {
            member.strength *= strMult
        }
    }
}