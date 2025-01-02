package exerelin.campaign.questskip

import com.fs.starfarer.api.Global
import exerelin.ExerelinConstants
import exerelin.utilities.NexUtils
import org.apache.log4j.Logger
import org.json.JSONArray
import org.json.JSONObject
import org.magiclib.util.MagicSettings
import java.util.*

/**
 * Replaces the old single-switch "skip story" system.
 */
// converting this class to Kotlin was a mistake
open class QuestChainSkipEntry(@JvmField var id: String?, @JvmField var name: String, @JvmField var image: String, @JvmField var sortKey: String) :
    Comparable<QuestChainSkipEntry> {

    @JvmField
    var tooltip: String? = null

    @JvmField
    var pluginClass // optional; custom script with various callins
            : String? = null
    @JvmField
    var tooltipCreatorClass // optional
            : String? = null

    @JvmField
    @Transient
    var plugin: QuestSkipPlugin? = null
    @JvmField
    var quests: MutableList<QuestSkipEntry> = ArrayList()
    fun setPluginClass(pc: String?) {
        pluginClass = pc
        plugin = NexUtils.instantiateClassByName(pc ?: return) as QuestSkipPlugin
        plugin!!.questChain = this
        plugin!!.init()
    }

    fun addQuestsById(vararg questIds: String?) {
        var id: String? = null
        val emptyArray = JSONArray()
        val emptyMap = JSONObject()

        try {
            val fileJson = Global.getSettings().getMergedJSONForMod(SETTINGS_FILE_PATH, ExerelinConstants.MOD_ID)
            val allQuestsJson = fileJson.getJSONObject(QUEST_SETTINGS_KEY)
            for (questId in questIds) {
                id = questId
                val questJson = allQuestsJson.getJSONObject(questId)
                val name = questJson.getString("name")
                val entry = QuestSkipEntry(questId, name, this)
                entry.tooltip = questJson.optString("tooltip", null)


                entry.idsToForceOnWhenEnabled.addAll(NexUtils.jsonToList(questJson.optJSONArray("idsToForceOnWhenEnabled") ?: emptyArray).filterIsInstance<String>())
                entry.idsToForceOffWhenEnabled.addAll(NexUtils.jsonToList(questJson.optJSONArray("idsToForceOffWhenEnabled") ?: emptyArray).filterIsInstance<String>())
                entry.idsToForceOnWhenDisabled.addAll(NexUtils.jsonToList(questJson.optJSONArray("idsToForceOnWhenDisabled") ?: emptyArray).filterIsInstance<String>())
                entry.idsToForceOffWhenDisabled.addAll(NexUtils.jsonToList(questJson.optJSONArray("idsToForceOffWhenDisabled") ?: emptyArray).filterIsInstance<String>())
                entry.playerMemflags.putAll(NexUtils.jsonToMap(questJson.optJSONObject("playerMemFlags") ?: emptyMap))
                entry.sectorMemflags.putAll(NexUtils.jsonToMap(questJson.optJSONObject("sectorMemFlags") ?: emptyMap))
                entry.peopleToUnhide.addAll(NexUtils.jsonToList(questJson.optJSONArray("peopleToUnhide") ?: emptyArray).filterIsInstance<String>())
                entry.setPluginClass(questJson.optString("plugin", null))
                entry.tooltipCreatorClass = questJson.optString("tooltipCreator", null)
                quests.add(entry)
            }
        } catch (ex: Exception) {
            log.error("Failed to load quest skip entries, last quest ID: $id", ex)
        }
    }

    fun getEnabledQuestMap(): Map<String, Boolean> {
        val map = LinkedHashMap<String, Boolean>()

        for (quest in quests) {
            map[quest.id] = quest.isEnabled
        }
        return map
    }

    fun isQuestEnabled(id : String) : Boolean {
        return isQuestEnabled(id, getEnabledQuestMap())
    }

    fun isQuestEnabled(id : String, map : Map<String, Boolean>) : Boolean {
        return map[id] == true;
    }

    fun onNewGame() {
        this.plugin?.onNewGame();
        for (quest in quests) {
            if (!quest.isEnabled) continue;
            quest.plugin?.onNewGame()
        }
    }

    fun onNewGameAfterProcGen() {
        this.plugin?.onNewGameAfterProcGen();
        for (quest in quests) {
            if (!quest.isEnabled) continue;
            quest.plugin?.onNewGameAfterProcGen()
        }
    }

    fun onNewGameAfterEconomyLoad() {
        this.plugin?.onNewGameAfterEconomyLoad()
        this.plugin?.applyMemKeys()
        for (quest in quests) {
            if (!quest.isEnabled) continue
            quest.plugin?.applyMemKeys()
            quest.plugin?.onNewGameAfterEconomyLoad()
        }
    }

    fun onNewGameAfterTimePass() {
        this.plugin?.onNewGameAfterTimePass()
        for (quest in quests) {
            if (!quest.isEnabled) continue
            quest.unhidePeople()
            quest.plugin?.onNewGameAfterTimePass()
        }
    }

    override fun compareTo(o: QuestChainSkipEntry): Int {
        return this.sortKey.compareTo(o.sortKey)
    }

    override fun toString(): String {
        return this.id + " " + this.quests
    }

    companion object {
        @JvmField val log: Logger = Global.getLogger(QuestChainSkipEntry.javaClass)
        const val CHAIN_SETTINGS_KEY = "questChains"
        const val QUEST_SETTINGS_KEY = "quests"
        const val SETTINGS_FILE_PATH = "data/config/exerelin/questSkip.json"

        @JvmField var entries: List<QuestChainSkipEntry>? = null

        @JvmStatic
        fun getEntries(): List<QuestChainSkipEntry>? {
            return entries
        }

        // runcode exerelin.campaign.questskip.QuestChainSkipEntry.initEntries()
        @JvmStatic
        fun initEntries() {
            entries = ArrayList<QuestChainSkipEntry>()
            var id: String? = null
            try {
                val fileJson = Global.getSettings().getMergedJSONForMod(SETTINGS_FILE_PATH, ExerelinConstants.MOD_ID)
                val allChainsJson = fileJson.getJSONObject(CHAIN_SETTINGS_KEY)
                val keys: MutableIterator<Any?> = allChainsJson.keys()
                while (keys.hasNext()) {
                    id = keys.next() as String
                    val chainJson = allChainsJson.getJSONObject(id)
                    val name = chainJson.getString("name")
                    val image = chainJson.getString("image")
                    val sortKey = chainJson.optString("sortKey", name)
                    val chain = QuestChainSkipEntry(id, name, image, sortKey)
                    chain.tooltip = chainJson.optString("tooltip", null)
                    chain.tooltipCreatorClass = chainJson.optString("tooltipCreator", null)
                    chain.addQuestsById(*NexUtils.JSONArrayToArrayList(chainJson.getJSONArray("quests")).toTypedArray())
                    chain.setPluginClass(chainJson.optString("plugin", null))

                    (entries as ArrayList<QuestChainSkipEntry>).add(chain)
                }

                Collections.sort(entries)
            } catch (ex: Exception) {
                log.error("Failed to load quest skip entries, last chain ID: $id", ex)
            }
        }
    }
}