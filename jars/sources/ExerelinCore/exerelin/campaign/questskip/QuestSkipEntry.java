package exerelin.campaign.questskip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import exerelin.utilities.NexUtils;
import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class QuestSkipEntry {

    public String id;
    public String name;
    public String tooltip;
    public QuestChainSkipEntry chain;
    public Set<String> idsToForceOnWhenEnabled = new HashSet<>();
    public Set<String> idsToForceOffWhenEnabled = new HashSet<>();
    public Set<String> idsToForceOnWhenDisabled = new HashSet<>();
    public Set<String> idsToForceOffWhenDisabled = new HashSet<>();
    public Map<String, Object> sectorMemflags = new HashMap<>();
    public Map<String, Object> playerMemflags = new HashMap<>();
    public Set<String> peopleToUnhide = new HashSet<>();

    @Getter protected String pluginClass;  // optional; custom script with various callins
    public String tooltipCreatorClass;  // optional

    public boolean isEnabled;

    public transient QuestSkipPlugin plugin;

    public QuestSkipEntry(String id, String name, QuestChainSkipEntry chain) {
        this.id = id;
        this.name = name;
        this.chain = chain;
    }

    public void setPluginClass(String pc) {
        this.pluginClass = pc;
        if (pc != null) {
            plugin = (QuestSkipPlugin) NexUtils.instantiateClassByName(pc);
            plugin.setQuest(this);
            plugin.setQuestChain(this.chain);
            plugin.init();
        }
    }

    public void applyMemKeys() {
        MemoryAPI global = Global.getSector().getMemoryWithoutUpdate();
        MemoryAPI player = Global.getSector().getCharacterData().getMemoryWithoutUpdate();
        for (String key : sectorMemflags.keySet()) {
            Object value = sectorMemflags.get(key);
            global.set(key, value);
        }
        //Global.getLogger(this.getClass()).info("wa wa wa " + playerMemflags);
        for (String key : playerMemflags.keySet()) {
            Object value = playerMemflags.get(key);
            player.set(key, value);
        }
    }

    public void unhidePeople() {
        for (String personId : peopleToUnhide) {
            PersonAPI pers = Global.getSector().getImportantPeople().getPerson(personId);
            if (pers == null || pers.getMarket() == null) return;
            pers.getMarket().getCommDirectory().getEntryForPerson(pers).setHidden(false);
        }
    }

    @Override
    public String toString() {
        return this.id;
    }
}
