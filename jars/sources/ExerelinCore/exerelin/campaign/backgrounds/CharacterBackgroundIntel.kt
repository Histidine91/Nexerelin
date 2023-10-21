package exerelin.campaign.backgrounds

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.FactionSpecAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin
import com.fs.starfarer.api.ui.SectorMapAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import exerelin.utilities.NexConfig
import exerelin.utilities.NexFactionConfig

class CharacterBackgroundIntel(var factionId: String) : BaseIntelPlugin() {

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
        var name = getPlugin()?.getTitle(getFactionSpec(), getFactionConfig()) ?: "Unknown"
        return "Background: $name"
    }

    override fun createSmallDescription(info: TooltipMakerAPI, width: Float, height: Float) {
        info.addSpacer(10f)
        var plugin = getPlugin()

        if (plugin == null) {
            info.addPara("You have no known background", 0f)
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
        tags.add("Personal")
        return tags
    }


}