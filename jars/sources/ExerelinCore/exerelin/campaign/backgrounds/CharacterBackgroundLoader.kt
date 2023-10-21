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
            val order = row.getDouble("order").toFloat()
            val iconPath = row.getString("iconPath")
            val pluginPath = row.getString("plugin")

            Global.getSettings().loadTexture(iconPath)

            var spec = CharacterBackgroundSpec()

            spec.id = id
            spec.title = title
            spec.shortDescription = shortDescription
            spec.order = order
            spec.iconPath = iconPath
            spec.pluginPath = pluginPath

            specs.add(spec)
        }
    }

}