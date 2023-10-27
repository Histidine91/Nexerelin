package exerelin.campaign.backgrounds

import com.fs.starfarer.api.Global
import exerelin.utilities.StringHelper

object CharacterBackgroundUtils {

    @JvmStatic
    fun isBackgroundActive(id: String) : Boolean {
        var spec: CharacterBackgroundSpec = getCurrentBackgroundSpec() ?: return false
        return spec.id == id
    }

    @JvmStatic
    fun getCurrentBackgroundSpec() : CharacterBackgroundSpec? {
        var specId = Global.getSector().memoryWithoutUpdate.getString("\$nex_selected_background")
        var spec = CharacterBackgroundLoader.specs.find { it.id == specId }
        return spec
    }

    @JvmStatic
    fun getBackgroundSpecByID(id: String) : CharacterBackgroundSpec? {
        var spec = CharacterBackgroundLoader.specs.find { it.id == id }
        return spec
    }

    @JvmStatic
    fun getBackgroundPluginByID(id: String) : BaseCharacterBackground? {
        var spec = getCurrentBackgroundSpec() ?: return null
        try {
            var plugin = Global.getSettings().scriptClassLoader.loadClass(spec.pluginPath).newInstance() as BaseCharacterBackground
            plugin.spec = spec
            return plugin
        } catch (e: Throwable) {
            return null
        }
    }

    @JvmStatic
    fun getPluginForSpec(spec: CharacterBackgroundSpec): BaseCharacterBackground? {
        try {
            var plugin = Global.getSettings().scriptClassLoader.loadClass(spec.pluginPath).newInstance() as BaseCharacterBackground
            plugin.spec = spec
            return plugin
        } catch (e: Throwable) {
            return null
        }
    }

    @JvmStatic
    fun getString(id: String): String {
        return getString(id, false)
    }

    @JvmStatic
    fun getString(id: String, ucFirst: Boolean): String {
        return StringHelper.getString("nex_backgrounds", id, ucFirst)
    }
}