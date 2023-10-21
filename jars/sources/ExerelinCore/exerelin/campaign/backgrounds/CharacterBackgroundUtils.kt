package exerelin.campaign.backgrounds

import com.fs.starfarer.api.Global

object CharacterBackgroundUtils {

    @JvmStatic
    fun isBackgroundActive(id: String) : Boolean {
        return getCurrentBackgroundSpec()!!.id == id
    }

    @JvmStatic
    fun getCurrentBackgroundSpec() : CharacterBackgroundSpec? {
        var specId = Global.getSector().memoryWithoutUpdate.getString("\$nex_selected_background")
        var spec = CharacterBackgroundLoader.specs.find { it.id == specId }
        return spec
    }

    @JvmStatic
    fun getPluginForSpec(spec: CharacterBackgroundSpec): BaseCharacterBackground? {
        return Global.getSettings().scriptClassLoader.loadClass(spec.pluginPath).newInstance() as BaseCharacterBackground?
    }

}