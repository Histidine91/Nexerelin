package exerelin.campaign.backgrounds

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionSpecAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exerelin.utilities.NexConfig
import exerelin.utilities.NexFactionConfig
import exerelin.utilities.StringHelper

class CharacterBackgroundIntel(var factionId: String) : BaseIntelPlugin() {

    var location: StarSystemAPI? = null

    fun getSpec() : CharacterBackgroundSpec? {
        return CharacterBackgroundUtils.getCurrentBackgroundSpec()
    }

    fun getPlugin() : BaseCharacterBackground? {
        var spec = getSpec() ?: return null
        return CharacterBackgroundUtils.getPluginForSpec(spec)
    }

    fun getFactionConfig() : NexFactionConfig {
       return NexConfig.getFactionConfig(factionId)
    }

    fun getFactionSpec() : FactionSpecAPI {
        return Global.getSettings().getFactionSpec(factionId)
    }

    init {
        this.isImportant = true
    }

    override fun getName(): String? {
        var name = getPlugin()?.getTitle(getFactionSpec(), getFactionConfig()) ?: StringHelper.getString("unknown", true)
        return String.format(StringHelper.getString("nex_backgrounds", "intelTitle"), name);
    }

    override fun createSmallDescription(info: TooltipMakerAPI, width: Float, height: Float) {
        info.addSpacer(10f)
        var plugin = getPlugin()

        if (plugin == null) {
            info.addPara(StringHelper.getString("nex_backgrounds", "intelDescNoBackground"), 0f)
            return
        }

        plugin.addTooltipForIntel(info, getFactionSpec(), getFactionConfig())

    }

    override fun getIcon(): String? {
        var iconPath = getPlugin()?.getIcon(getFactionSpec(), getFactionConfig()) ?: "graphics/factions/crest_player_flag.png"
        return iconPath
    }

    override fun getIntelTags(map: SectorMapAPI?): Set<String>? {
        val tags: MutableSet<String> = LinkedHashSet()
        tags.add(StringHelper.getString("exerelin_misc", "intelTagPersonal"))
        return tags
    }

    override fun getMapLocation(map: SectorMapAPI?): SectorEntityToken? {
        return location?.hyperspaceAnchor
    }
}