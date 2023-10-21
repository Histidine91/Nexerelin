package exerelin.campaign.backgrounds

import com.fs.starfarer.api.Global

object CharacterBackgroundLoader {

    @JvmStatic
    var specs = ArrayList<CharacterBackgroundSpec>()

    @JvmStatic
    fun load() {
        var CSV = Global.getSettings().getMergedSpreadsheetDataForMod("id", "data/config/exerelin/character_backgrounds.csv", "nexerelin")

        for (index in 0 until  CSV.length())
        {
            val row = CSV.getJSONObject(index)

            val id = row.getString("id")
            if (id.startsWith("#") || id == "") continue
            val title = row.getString("name")
            val shortDescription = row.getString("shortDescription")
            val longDescription = row.getString("longDescription")
            val order = row.getDouble("order").toFloat()
            val iconPath = row.getString("iconPath")
            val pluginPath = row.getString("plugin")

            Global.getSettings().loadTexture(iconPath)

            var spec = CharacterBackgroundSpec()

            spec.id = id
            spec.title = title
            spec.shortDescription = shortDescription
            spec.longDescription = longDescription
            spec.order = order
            spec.iconPath = iconPath
            spec.pluginPath = pluginPath
            spec.modName = filterModPath(row.getString("fs_rowSource"))

            specs.add(spec)
        }
    }

    //From Console Commands by LazyWizard
    private fun filterModPath(fullPath: String): String? {
        var modPath = fullPath.replace("/", "\\")
        modPath = modPath.substring(modPath.lastIndexOf("\\mods\\"))
        modPath = modPath.substring(0, modPath.indexOf('\\', 6)) + "\\"
        modPath = modPath.replace("\\mods\\", "")
        modPath = modPath.replace("\\", "")
        return modPath
    }

}